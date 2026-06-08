import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import { useMessageStore } from './message'
import type { Message } from '@/types'

export type RtcHandler = (body: Record<string, unknown>) => void

export const useWebSocketStore = defineStore('websocket', () => {
  const connected = ref(false)
  let client: Client | null = null
  let rtcHandler: RtcHandler | null = null

  function connect() {
    const token = localStorage.getItem('token')
    if (!token) return
    const backend = localStorage.getItem('guglechat_backend_url') || ''
    const wsBase = backend ? backend.replace(/^http/, 'ws') : `ws://${window.location.host}`
    client = new Client({
      brokerURL: `${wsBase}/ws?token=${token}`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        connected.value = true
        // Subscribe to RTC signaling (user-specific queue)
        client?.subscribe('/user/queue/rtc', (msg) => {
          const body = JSON.parse(msg.body) as Record<string, unknown>
          rtcHandler?.(body)
        })
      },
      onDisconnect: () => { connected.value = false },
      onStompError: (frame) => console.error('[WS]', frame.headers['message']),
    })
    client.activate()
    return client
  }

  function onRtcMessage(handler: RtcHandler) {
    rtcHandler = handler
  }

  function sendSignaling(destination: string, payload: Record<string, unknown>) {
    if (!client) return
    client.publish({
      destination: `/app/${destination}`,
      body: JSON.stringify(payload),
    })
  }

  function subscribeToChannel(channelId: number) {
    if (!client) return
    const msgStore = useMessageStore()
    client.subscribe(`/topic/channel.${channelId}`, (message) => {
      const body = JSON.parse(message.body)
      if (body.type === 'DELETE') msgStore.removeMessage(body.messageId)
      else if (body.type === 'voice-users') {
        // Handled by RTC store via rtcHandler or direct store update
        import('./rtc').then(m => {
          m.useRtcStore().setVoiceUsers((body.users as number[]) || [])
        })
      }
      else msgStore.addMessage(body as Message)
    })
  }

  function sendMessage(channelId: number, content: string) {
    if (!client) return
    client.publish({
      destination: `/app/chat.send/${channelId}`,
      body: JSON.stringify({ content, type: 'TEXT' }),
    })
  }

  function isConnected(): boolean {
    return client?.connected ?? false
  }

  function disconnect() {
    client?.deactivate(); client = null; connected.value = false
  }

  return { connected, connect, subscribeToChannel, sendMessage, disconnect, isConnected, onRtcMessage, sendSignaling }
})
