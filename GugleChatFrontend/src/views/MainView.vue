<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChannelStore } from '@/stores/channel'
import { useRtcStore } from '@/stores/rtc'
import { useWebSocketStore } from '@/stores/websocket'
import Sidebar from '@/components/layout/Sidebar.vue'
import ChatArea from '@/components/chat/ChatArea.vue'
import VoiceCallView from '@/components/voice/VoiceCallView.vue'
import VoiceChannel from '@/components/voice/VoiceChannel.vue'

const authStore = useAuthStore()
const channelStore = useChannelStore()
const rtcStore = useRtcStore()
const wsStore = useWebSocketStore()

// Show VoiceCallView when in voice call and NOT viewing text chat
const showVoiceView = computed(() =>
  rtcStore.activeRoomId && !rtcStore.showVoiceChat
)

// Show ChatArea when we have a current channel AND either it's a text channel or voice with chat mode
const showChat = computed(() =>
  channelStore.currentChannel &&
  (channelStore.currentChannel.type === 'TEXT' || rtcStore.showVoiceChat)
)

onMounted(async () => {
  await authStore.fetchMe()
  await channelStore.fetchChannels()
  wsStore.connect()
  // Wait for WebSocket to connect, then subscribe and sync
  await new Promise<void>(resolve => {
    const check = () => {
      if (wsStore.connected) {
        for (const ch of channelStore.channels) {
          wsStore.subscribeToChannel(ch.id)
        }
        syncVoiceUsers().then(resolve)
      } else {
        setTimeout(check, 200)
      }
    }
    check()
  })
})

async function syncVoiceUsers() {
  try {
    const res = await fetch('/api/channels/voice-users', {
      headers: { Authorization: `Bearer ${authStore.token}` },
    })
    const body = await res.json()
    if (body.code === 200 && body.data) {
      const data = body.data as Record<string, { users: any[]; hostId: number }>
      for (const roomId of Object.keys(data)) {
        rtcStore.setVoiceUsers(Number(roomId), data[roomId].users)
        if (data[roomId].hostId) rtcStore.hostId = data[roomId].hostId
      }
    }
  } catch { /* ignore if backend not ready */ }
}

onUnmounted(() => wsStore.disconnect())
</script>

<template>
  <div class="main-layout">
    <Sidebar />
    <main class="main-content">
      <VoiceCallView v-if="showVoiceView" />
      <ChatArea v-else-if="showChat" />
      <div v-else class="empty-state">
        <h2>Welcome to GugleChat</h2>
        <p>Select a channel or create a new one to start chatting</p>
      </div>
    </main>
    <!-- Hidden: handles RTC signaling -->
    <VoiceChannel v-show="false" />
  </div>
</template>

<style scoped>
.main-layout { display: flex; height: 100vh; }
.main-content {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
  background: #313338;
}
.empty-state {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  justify-content: center; color: #949ba4;
}
.empty-state h2 { font-size: 24px; margin-bottom: 8px; }
</style>
