import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import { useMessageStore } from './message'
import type { Message } from '@/types'

export const useWebSocketStore = defineStore('websocket', () => {
  const connected = ref(false)
  let client: Client | null = null

  function connect() {
    const token = localStorage.getItem('token')
    if (!token) return
    client = new Client({
      brokerURL: `ws://${window.location.host}/ws?token=${token}`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => { connected.value = true },
      onDisconnect: () => { connected.value = false },
      onStompError: (frame) => console.error('[WS]', frame.headers['message']),
    })
    client.activate()
    return client
  }

  function subscribeToChannel(channelId: number) {
    if (!client?.connected) return
    const msgStore = useMessageStore()
    client.subscribe(`/topic/channel.${channelId}`, (message) => {
      const body = JSON.parse(message.body)
      if (body.type === 'DELETE') msgStore.removeMessage(body.messageId)
      else msgStore.addMessage(body as Message)
    })
  }

  function sendMessage(channelId: number, content: string) {
    if (!client?.connected) return
    client.publish({
      destination: `/app/chat.send/${channelId}`,
      body: JSON.stringify({ content, type: 'TEXT' }),
    })
  }

  function disconnect() {
    client?.deactivate(); client = null; connected.value = false
  }

  return { connected, connect, subscribeToChannel, sendMessage, disconnect }
})
