<script setup lang="ts">
import { watch, ref, nextTick } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { useMessageStore } from '@/stores/message'
import { useWebSocketStore } from '@/stores/websocket'
import MessageBubble from './MessageBubble.vue'
import ChatInput from './ChatInput.vue'

const channelStore = useChannelStore()
const messageStore = useMessageStore()
const wsStore = useWebSocketStore()
const container = ref<HTMLElement | null>(null)

watch(() => channelStore.currentChannelId, async (id) => {
  if (id) { await messageStore.loadHistory(id); wsStore.subscribeToChannel(id); scrollBottom() }
}, { immediate: true })

watch(() => messageStore.getMessages(channelStore.currentChannelId || 0).length, () => scrollBottom())

function scrollBottom() {
  nextTick(() => { if (container.value) container.value.scrollTop = container.value.scrollHeight })
}
</script>

<template>
  <div class="chat-area">
    <div class="chat-header">
      <h3>
        <span>{{ channelStore.currentChannel?.type === 'VOICE' ? '🔊' : '#' }}</span>
        {{ channelStore.currentChannel?.name }}
      </h3>
      <span class="chat-type">{{ channelStore.currentChannel?.type === 'VOICE' ? 'Voice Channel' : 'Text Channel' }}</span>
    </div>

    <div ref="container" class="messages-container">
      <MessageBubble v-for="msg in messageStore.getMessages(channelStore.currentChannelId || 0)" :key="msg.id" :message="msg" />
      <div v-if="messageStore.getMessages(channelStore.currentChannelId || 0).length === 0" class="empty-messages">
        <p>No messages yet. Start the conversation!</p>
      </div>
    </div>

    <div class="chat-input-wrapper">
      <ChatInput :channel-id="channelStore.currentChannelId || 0" />
    </div>
  </div>
</template>

<style scoped>
.chat-area { flex: 1; display: flex; flex-direction: column; height: 100%; }
.chat-header { padding: 16px 20px; border-bottom: 1px solid var(--color-border-2); display: flex; align-items: center; gap: 12px; }
.chat-header h3 { margin: 0; font-size: 16px; }
.chat-type { font-size: 12px; color: var(--color-text-3); }
.messages-container { flex: 1; overflow-y: auto; padding: 16px 20px; }
.empty-messages { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--color-text-3); }
.chat-input-wrapper { border-top: 1px solid var(--color-border-2); padding: 12px 20px; }
</style>
