import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Message } from '@/types'
import { messageService } from '@/services/messageService'

export const useMessageStore = defineStore('message', () => {
  const messagesByChannel = ref<Record<number, Message[]>>({})

  function getMessages(channelId: number): Message[] { return messagesByChannel.value[channelId] || [] }

  async function loadHistory(channelId: number, beforeId?: number) {
    const res = await messageService.getHistory(channelId, beforeId)
    const msgs = (res.data as unknown as Message[]) || []
    const existing = messagesByChannel.value[channelId] || []
    messagesByChannel.value[channelId] = [...msgs.reverse(), ...existing]
    return msgs
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

  return { messagesByChannel, getMessages, loadHistory, addMessage, updateMessage, removeMessage }
})
