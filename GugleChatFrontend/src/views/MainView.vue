<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChannelStore } from '@/stores/channel'
import { useWebSocketStore } from '@/stores/websocket'
import Sidebar from '@/components/layout/Sidebar.vue'
import ChatArea from '@/components/chat/ChatArea.vue'
import VoiceChannel from '@/components/voice/VoiceChannel.vue'

const authStore = useAuthStore()
const channelStore = useChannelStore()
const wsStore = useWebSocketStore()

onMounted(async () => {
  await authStore.fetchMe()
  await channelStore.fetchChannels()
  wsStore.connect()
})

onUnmounted(() => wsStore.disconnect())
</script>

<template>
  <div class="main-layout">
    <Sidebar />
    <main class="main-content">
      <ChatArea v-if="channelStore.currentChannel" />
      <div v-else class="empty-state">
        <h2>Welcome to GugleChat</h2>
        <p>Select a channel or create a new one to start chatting</p>
      </div>
    </main>
    <VoiceChannel />
  </div>
</template>

<style scoped>
.main-layout { display: flex; height: 100vh; }
.main-content { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: var(--color-bg-1); }
.empty-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--color-text-3); }
.empty-state h2 { font-size: 24px; margin-bottom: 8px; }
</style>
