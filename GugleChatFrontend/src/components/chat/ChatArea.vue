<script setup lang="ts">
import { watch, ref, nextTick, computed } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { useMessageStore } from '@/stores/message'
import { useWebSocketStore } from '@/stores/websocket'
import { useAuthStore } from '@/stores/auth'
import { useContextMenu } from '@/composables/useContextMenu'
import { IconVoice, IconMessage } from '@arco-design/web-vue/es/icon'
import MessageBubble from './MessageBubble.vue'
import ChatInput from './ChatInput.vue'
import type { Message } from '@/types'

const channelStore = useChannelStore()
const messageStore = useMessageStore()
const wsStore = useWebSocketStore()
const authStore = useAuthStore()
const container = ref<HTMLElement | null>(null)
const { ctxMenu, openMessageMenu, closeMenu } = useContextMenu()
const isLoadingMore = ref(false)

watch(() => channelStore.currentChannelId, async (id) => {
  if (id) { await messageStore.loadHistory(id); wsStore.subscribeToChannel(id); scrollBottom() }
}, { immediate: true })

// Only auto-scroll on new messages if already near the bottom
watch(() => messageStore.getMessages(channelStore.currentChannelId || 0).length, () => {
  if (isNearBottom()) scrollBottom()
})

function isNearBottom(): boolean {
  if (!container.value) return true
  const { scrollTop, scrollHeight, clientHeight } = container.value
  return scrollHeight - scrollTop - clientHeight < 100
}

function scrollBottom() {
  nextTick(() => { if (container.value) container.value.scrollTop = container.value.scrollHeight })
}

let lastChannelId = 0

// Lazy load older messages on scroll
async function onScroll() {
  if (!container.value) return
  const id = channelStore.currentChannelId
  if (!id) return

  // Reset scroll state when switching channels
  if (id !== lastChannelId) {
    lastChannelId = id
    return
  }

  const { scrollTop } = container.value

  // When within 200px of the top, load older messages
  if (scrollTop < 200 && messageStore.hasMore(id) && !isLoadingMore.value) {
    const msgs = messageStore.getMessages(id)
    if (msgs.length === 0) return
    const oldestId = msgs[0].id
    const prevScrollHeight = container.value.scrollHeight

    isLoadingMore.value = true
    await messageStore.loadHistory(id, oldestId)
    isLoadingMore.value = false

    // Restore scroll position so view doesn't jump
    await nextTick()
    if (container.value) {
      container.value.scrollTop = container.value.scrollHeight - prevScrollHeight
    }
  }
}

const isChannelAdmin = computed(() => {
  const ch = channelStore.currentChannel
  if (!ch || !authStore.user) return false
  return ch.createdBy === authStore.user.id
})

const canDeleteCtxMsg = computed(() => {
  if (!ctxMenu.value.message || !authStore.user) return false
  return ctxMenu.value.message.userId === authStore.user.id || isChannelAdmin.value
})

function onMessageCtxMenu(e: MouseEvent, msg: Message) {
  openMessageMenu(e, msg)
}

function handleDeleteMessage() {
  if (!ctxMenu.value.message) return
  wsStore.deleteMessage(ctxMenu.value.message.id)
  closeMenu()
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

    <div ref="container" class="messages-container" @scroll="onScroll">
      <!-- Loading indicator -->
      <div v-if="isLoadingMore" class="load-more">加载更多消息...</div>

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

    <!-- Global context menu (message items) -->
    <div v-if="ctxMenu.show && ctxMenu.type === 'message' && canDeleteCtxMsg" class="msg-ctx-menu"
         :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }" @click.stop>
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
.ch-icon { font-size: 18px; color: var(--color-text-3); }
.chat-type { font-size: 12px; color: var(--color-text-3); }
.messages-container { flex: 1; overflow-y: auto; padding: 16px 20px; }
.empty-messages { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--color-text-3); }
.chat-input-wrapper { border-top: 1px solid var(--color-border-2); padding: 12px 20px; }

.load-more {
  text-align: center;
  padding: 8px 0;
  font-size: 12px;
  color: var(--color-text-3);
}

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
  display: flex; align-items: center; gap: 8px;
  padding: 6px 12px; font-size: 13px;
  color: var(--color-text-2); cursor: pointer;
}
.msg-ctx-item:hover { background: rgb(var(--primary-6)); color: #fff; border-radius: 2px; }
.msg-ctx-item-danger { color: rgb(var(--red-6)); }
.msg-ctx-item-danger:hover { background: rgb(var(--red-6)); color: #fff; }
</style>
