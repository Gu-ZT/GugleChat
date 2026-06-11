import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Channel, ChannelType } from '@/types'
import { channelService } from '@/services/channelService'

export const useChannelStore = defineStore('channel', () => {
  const channels = ref<Channel[]>([])
  const currentChannelId = ref<number | null>(null)
  const currentChannel = computed(() => channels.value.find(c => c.id === currentChannelId.value) || null)

  async function fetchChannels() {
    const res = await channelService.list()
    channels.value = res.data as unknown as Channel[]
    if (!currentChannelId.value && channels.value.length > 0)
      currentChannelId.value = channels.value[0].id
  }

  async function createChannel(name: string, type: ChannelType, description?: string) {
    const res = await channelService.create(name, type, description)
    channels.value.push(res.data as unknown as Channel)
    return res.data as unknown as Channel
  }

  function selectChannel(id: number) { currentChannelId.value = id }

  async function deleteChannel(id: number) {
    await channelService.deleteChannel(id)
    channels.value = channels.value.filter(c => c.id !== id)
    if (currentChannelId.value === id) {
      currentChannelId.value = channels.value.length > 0 ? channels.value[0].id : null
    }
  }

  return { channels, currentChannelId, currentChannel, fetchChannels, createChannel, selectChannel, deleteChannel }
})
