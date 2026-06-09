<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChannelStore } from '@/stores/channel'
import { useRtcStore } from '@/stores/rtc'
import { useWebSocketStore } from '@/stores/websocket'
import Sidebar from '@/components/layout/Sidebar.vue'
import ChatArea from '@/components/chat/ChatArea.vue'
import VoiceCallView from '@/components/voice/VoiceCallView.vue'
import VoiceChannel from '@/components/voice/VoiceChannel.vue'
import SettingsModal from '@/components/common/SettingsModal.vue'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const channelStore = useChannelStore()
const rtcStore = useRtcStore()
const wsStore = useWebSocketStore()
const router = useRouter()
const showSettings = ref(false)

// Show VoiceCallView when in voice call and NOT viewing text chat
const showVoiceView = computed(() =>
  rtcStore.activeRoomId && !rtcStore.showVoiceChat &&
  (!channelStore.currentChannel || channelStore.currentChannel.type === 'VOICE')
)

// Show ChatArea when we have a current channel AND either it's a text channel or voice with chat mode
const showChat = computed(() =>
  channelStore.currentChannel &&
  (channelStore.currentChannel.type === 'TEXT' || rtcStore.showVoiceChat)
)

onMounted(async () => {
  await authStore.fetchMe()
  await channelStore.fetchChannels()
  // Populate voice users from channel list response (now includes voiceUsers)
  for (const ch of channelStore.channels) {
    if (ch.type === 'VOICE' && (ch as any).voiceUsers?.length) {
      rtcStore.setVoiceUsers(ch.id, (ch as any).voiceUsers)
      if ((ch as any).hostId) rtcStore.hostId = (ch as any).hostId
    }
  }
  wsStore.connect()
})

onUnmounted(() => wsStore.disconnect())
</script>

<template>
  <div class="main-layout">
    <Sidebar @open-settings="showSettings = true" />
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
    <SettingsModal v-model:visible="showSettings" @logout="router.push('/login')" />
  </div>
</template>

<style scoped>
.main-layout { display: flex; height: 100vh; }
.main-content {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
  background: var(--color-bg-1);
}
.empty-state {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  justify-content: center; color: var(--color-text-3);
}
.empty-state h2 { font-size: 24px; margin-bottom: 8px; }
</style>
