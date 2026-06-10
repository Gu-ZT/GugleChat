import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Client, type StompSubscription } from '@stomp/stompjs'
import { useMessageStore } from './message'
import { useRtcStore } from './rtc'
import type { Message } from '@/types'

export type RtcHandler = (body: Record<string, unknown>) => void

export const useWebSocketStore = defineStore('websocket', () => {
  const connected = ref(false)
  let client: Client | null = null
  let rtcHandler: RtcHandler | null = null
  const serverLatency = ref(-1)
  let pingTimer: ReturnType<typeof setInterval> | null = null
  // Track active channel subscriptions to prevent duplicates and enable cleanup
  const channelSubs = new Map<number, StompSubscription>()

  function startPing() {
    stopPing()
    pingTimer = setInterval(() => {
      if (!client?.connected) return
      client.publish({
        destination: '/app/heartbeat',
        body: JSON.stringify({ pingTs: Date.now() }),
      })
    }, 5000)
  }

  function stopPing() {
    if (pingTimer) { clearInterval(pingTimer); pingTimer = null }
  }

  function connect() {
    const token = localStorage.getItem('token')
    if (!token) return
    // Prevent double-connect: deactivate existing client first
    if (client) { client.deactivate(); client = null; connected.value = false }
    const backend = localStorage.getItem('guglechat_backend_url') || ''
    const wsBase = backend ? backend.replace(/^http/, 'ws') : `ws://${window.location.host}`
    client = new Client({
      brokerURL: `${wsBase}/ws?token=${token}`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        connected.value = true
        startPing()
        // Re-subscribe to all active channels after reconnect
        const activeChannels = [...channelSubs.keys()]
        channelSubs.clear()
        for (const id of activeChannels) subscribeToChannel(id)
        // Global voice users — no per-channel subscription needed
        client?.subscribe('/topic/voice-users', (msg) => {
          const body = JSON.parse(msg.body) as Record<string, unknown>
          if (body.type === 'voice-users') {
            const rtc = useRtcStore()
            rtc.setVoiceUsers(body.roomId as number, (body.users as any[]) || [])
            if (body.hostId && rtc.activeRoomId === body.roomId) rtc.setHostId(body.hostId as number)
            rtc.setForcedHostId((body.forcedHostId as number) || 0)
          }
        })
        // Heartbeat — handle server ping and pong echo
        client?.subscribe('/topic/heartbeat', (msg) => {
          const body = JSON.parse(msg.body) as Record<string, unknown>
          if (body.type === 'ping') {
            client?.publish({ destination: '/app/heartbeat', body: '{}' })
          }
        })
        // Pong response for RTT measurement
        client?.subscribe('/user/queue/heartbeat', (msg) => {
          const body = JSON.parse(msg.body) as Record<string, unknown>
          if (body.type === 'pong' && body.pingTs) {
            serverLatency.value = Date.now() - (body.pingTs as number)
          }
        })
        // RTC signaling
        client?.subscribe('/user/queue/rtc', (msg) => {
          const body = JSON.parse(msg.body) as Record<string, unknown>
          rtcHandler?.(body)
        })
      },
      onDisconnect: () => { connected.value = false; stopPing() },
      onStompError: (frame) => console.error('[WS]', frame.headers['message']),
    })
    client.activate()
    return client
  }

  function onRtcMessage(handler: RtcHandler) {
    rtcHandler = handler
  }

  function sendSignaling(destination: string, payload: Record<string, unknown>) {
    if (!client?.connected) return
    try {
      client.publish({
        destination: `/app/${destination}`,
        body: JSON.stringify(payload),
      })
    } catch { /* connection dropped between check and publish */ }
  }

  function subscribeToChannel(channelId: number) {
    if (!client?.connected) return
    // Prevent duplicate subscriptions for the same channel
    if (channelSubs.has(channelId)) return
    const msgStore = useMessageStore()
    const sub = client.subscribe(`/topic/channel.${channelId}`, (message) => {
      const body = JSON.parse(message.body)
      if (body.type === 'DELETE') msgStore.removeMessage(body.messageId)
      else if (body.type === 'voice-users') {
        console.log('[WS] voice-users for channel', channelId, body.users)
        const rtc = useRtcStore()
        rtc.setVoiceUsers(channelId, (body.users as { userId: number; username: string; quality: number }[]) || [])
        if (body.hostId && rtc.activeRoomId === channelId) rtc.setHostId(body.hostId as number)
      }
      else msgStore.addMessage(body as Message)
    })
    channelSubs.set(channelId, sub)
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
    channelSubs.clear()
    client?.deactivate(); client = null; connected.value = false
  }

  return { connected, serverLatency, connect, subscribeToChannel, sendMessage, disconnect, isConnected, onRtcMessage, sendSignaling }
})
