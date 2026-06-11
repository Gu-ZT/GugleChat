import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Message } from '@/types'
import { messageService } from '@/services/messageService'

const PAGE_SIZE = 15

export const useMessageStore = defineStore('message', () => {
  const messagesByChannel = ref<Record<number, Message[]>>({})
  const loadingByChannel = ref<Record<number, boolean>>({})
  const hasMoreByChannel = ref<Record<number, boolean>>({})

  function getMessages(channelId: number): Message[] {
    return messagesByChannel.value[channelId] || []
  }

  function isLoading(channelId: number): boolean {
    return loadingByChannel.value[channelId] || false
  }

  function hasMore(channelId: number): boolean {
    return hasMoreByChannel.value[channelId] ?? true
  }

  async function loadHistory(channelId: number, beforeId?: number) {
    if (loadingByChannel.value[channelId]) return []
    loadingByChannel.value[channelId] = true
    try {
      const res = await messageService.getHistory(channelId, beforeId)
      const msgs = (res.data as unknown as Message[]) || []
      const existing = messagesByChannel.value[channelId] || []
      const existingIds = new Set(existing.map(m => m.id))
      const newMsgs = msgs.filter(m => !existingIds.has(m.id)).reverse()
      if (newMsgs.length > 0) {
        messagesByChannel.value[channelId] = [...newMsgs, ...existing]
      }
      // If returned fewer than PAGE_SIZE, there's no more history
      if (msgs.length < PAGE_SIZE) {
        hasMoreByChannel.value[channelId] = false
      }
      return newMsgs
    } finally {
      loadingByChannel.value[channelId] = false
    }
  }

  function addMessage(message: Message) {
    if (!messagesByChannel.value[message.channelId]) messagesByChannel.value[message.channelId] = []
    if (!messagesByChannel.value[message.channelId].some(m => m.id === message.id))
      messagesByChannel.value[message.channelId].push(message)
  }

  function updateMessage(messageId: number, content: string) {
    for (const msgs of Object.values(messagesByChannel.value)) {
      const idx = msgs.findIndex(m => m.id === messageId)
      if (idx !== -1) { msgs[idx].content = content; msgs[idx].editedAt = new Date().toISOString(); break }
    }
  }

  function removeMessage(messageId: number) {
    for (const msgs of Object.values(messagesByChannel.value)) {
      const idx = msgs.findIndex(m => m.id === messageId)
      if (idx !== -1) { msgs.splice(idx, 1); break }
    }
  }

  return { messagesByChannel, getMessages, isLoading, hasMore, loadHistory, addMessage, updateMessage, removeMessage }
})
