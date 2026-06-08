<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { Message } from '@/types'
import MarkdownRenderer from './MarkdownRenderer.vue'
import dayjs from 'dayjs'

const props = defineProps<{ message: Message }>()
const auth = useAuthStore()
const isMine = computed(() => props.message.userId === auth.user?.id)
const time = computed(() => dayjs(props.message.createdAt).format('HH:mm'))
</script>

<template>
  <div class="msg" :class="{ mine: isMine }">
    <div class="msg-avatar">{{ message.username.charAt(0).toUpperCase() }}</div>
    <div class="msg-body">
      <div class="msg-header">
        <span class="msg-user">{{ message.username }}</span>
        <span class="msg-time">{{ time }}</span>
        <span v-if="message.editedAt" class="msg-edited">(edited)</span>
      </div>
      <div class="msg-content">
        <MarkdownRenderer :content="message.content" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg { display: flex; gap: 12px; padding: 8px 0; max-width: 80%; }
.msg.mine { margin-left: auto; flex-direction: row-reverse; }
.msg-avatar { width: 36px; height: 36px; border-radius: 50%; background: #3a5a8c; display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px; flex-shrink: 0; color: #fff; }
.msg-body { min-width: 0; }
.msg-header { display: flex; gap: 8px; align-items: baseline; margin-bottom: 4px; }
.msg-user { font-weight: 600; font-size: 13px; color: #80b4ff; }
.msg-time { font-size: 11px; color: var(--color-text-3); }
.msg-edited { font-size: 10px; color: var(--color-text-4); font-style: italic; }
.msg-content { background: rgba(255,255,255,.05); border-radius: 8px; padding: 8px 12px; word-break: break-word; font-size: 14px; line-height: 1.5; }
.mine .msg-content { background: rgba(45,74,122,.5); }
</style>
