import api from './api'
import type { ChannelType } from '@/types'

export const channelService = {
  list() { return api.get('/channels') },
  create(name: string, type: ChannelType, description?: string) {
    return api.post('/channels', { name, type, description })
  },
  getMembers(channelId: number) { return api.get(`/channels/${channelId}/members`) },
  addMember(channelId: number, userId: number) { return api.post(`/channels/${channelId}/members`, { userId }) },
  removeMember(channelId: number, userId: number) { return api.delete(`/channels/${channelId}/members/${userId}`) },
  deleteChannel(id: number) { return api.delete(`/channels/${id}`) },
}
