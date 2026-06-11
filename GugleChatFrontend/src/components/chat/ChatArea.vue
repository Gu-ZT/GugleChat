<script setup lang="ts">
import { watch, ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { useMessageStore } from '@/stores/message'
import { useWebSocketStore } from '@/stores/websocket'
import { useAuthStore } from '@/stores/auth'
import { IconVoice, IconMessage } from '@arco-design/web-vue/es/icon'
import MessageBubble from './MessageBubble.vue'
import ChatInput from './ChatInput.vue'
import type { Message } from '@/types'

const channelStore = useChannelStore()
const messageStore = useMessageStore()
const wsStore = useWebSocketStore()
const authStore = useAuthStore()
const container = ref<HTMLElement | null>(null)

watch(() => channelStore.currentChannelId, async (id) => {
  if (id) { await messageStore.loadHistory(id); wsStore.subscribeToChannel(id); scrollBottom() }
}, { immediate: true })

watch(() => messageStore.getMessages(channelStore.currentChannelId || 0).length, () => scrollBottom())

function scrollBottom() {
  nextTick(() => { if (container.value) container.value.scrollTop = container.value.scrollHeight })
}

// --- Global message context menu ---
const msgCtx = ref<{ show: boolean; x: number; y: number; message: Message | null }>({
  show: false, x: 0, y: 0, message: null
})

const isChannelAdmin = computed(() => {
  const ch = channelStore.currentChannel
  if (!ch || !authStore.user) return false
  return ch.createdBy === authStore.user.id
})

const canDeleteCtxMsg = computed(() => {
  if (!msgCtx.value.message || !authStore.user) return false
  return msgCtx.value.message.userId === authStore.user.id || isChannelAdmin.value
})

function onMessageCtxMenu(e: MouseEvent, msg: Message) {
  msgCtx.value = { show: true, x: e.clientX, y: e.clientY, message: msg }
}

function closeMsgCtx() {
  msgCtx.value.show = false
}

onMounted(() => document.addEventListener('click', closeMsgCtx))
onUnmounted(() => document.removeEventListener('click', closeMsgCtx))

function handleDeleteMessage() {
  if (!msgCtx.value.message) return
  wsStore.deleteMessage(msgCtx.value.message.id)
  closeMsgCtx()
}
</script>

<template>
  <div class="chat-area">
    <div class="chat-header">
      <h3>
        <IconVoice v-if="channelStore.currentChannel?.type === 'VOICE'" class="ch-icon" />
        <IconMessage v-else class="ch-icon" />
        {{ channelStore.currentChannel?.name }}
      </h3>
      <span class="chat-type">{{ channelStore.currentChannel?.type === 'VOICE' ? 'Voice · Chat' : 'Text Channel' }}</span>
    </div>

    <div ref="container" class="messages-container">
      <MessageBubble
        v-for="msg in messageStore.getMessages(channelStore.currentChannelId || 0)"
        :key="msg.id"
        :message="msg"
        @ctxmenu="onMessageCtxMenu"
      />
      <div v-if="messageStore.getMessages(channelStore.currentChannelId || 0).length === 0" class="empty-messages">
        <p>No messages yet. Start the conversation!</p>
      </div>
    </div>

    <div class="chat-input-wrapper">
      <ChatInput :channel-id="channelStore.currentChannelId || 0" />
    </div>

    <!-- Global message context menu -->
    <div v-if="msgCtx.show && canDeleteCtxMsg" class="msg-ctx-menu" :style="{ left: msgCtx.x + 'px', top: msgCtx.y + 'px' }" @click.stop>
      <div class="msg-ctx-item msg-ctx-item-danger" @click="handleDeleteMessage">
        <span>删除消息</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-area { flex: 1; display: flex; flex-direction: column; height: 100%; }
.chat-header { padding: 16px 20px; border-bottom: 1px solid var(--color-border-2); display: flex; align-items: center; gap: 12px; }
.chat-header h3 { margin: 0; font-size: 16px; display: flex; align-items: center; gap: 6px; }
.ch-icon, .ch-hash { font-size: 18px; color: var(--color-text-3); }
.chat-type { font-size: 12px; color: var(--color-text-3); }
.messages-container { flex: 1; overflow-y: auto; padding: 16px 20px; }
.empty-messages { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--color-text-3); }
.chat-input-wrapper { border-top: 1px solid var(--color-border-2); padding: 12px 20px; }

.msg-ctx-menu {
  position: fixed;
  z-index: 1000;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  padding: 4px 0;
  min-width: 160px;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.4);
}

.msg-ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--color-text-2);
  cursor: pointer;
}

.msg-ctx-item:hover {
  background: rgb(var(--primary-6));
  color: #fff;
  border-radius: 2px;
}

.msg-ctx-item-danger { color: rgb(var(--red-6)); }
.msg-ctx-item-danger:hover { background: rgb(var(--red-6)); color: #fff; }
</style>
