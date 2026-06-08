import api from './api'

export const messageService = {
  getHistory(channelId: number, beforeId?: number) {
    return api.get(`/channels/${channelId}/messages`, { params: beforeId ? { before: beforeId } : {} })
  },
  editMessage(messageId: number, content: string) { return api.put(`/messages/${messageId}`, { content }) },
  deleteMessage(messageId: number) { return api.delete(`/messages/${messageId}`) },
}
