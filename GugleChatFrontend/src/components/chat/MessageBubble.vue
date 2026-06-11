<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { Message } from '@/types'
import MarkdownRenderer from './MarkdownRenderer.vue'
import dayjs from 'dayjs'

const props = defineProps<{ message: Message }>()
const emit = defineEmits<{ (e: 'ctxmenu', ev: MouseEvent, msg: Message): void }>()

const auth = useAuthStore()
const isMine = computed(() => props.message.userId === auth.user?.id)
const time = computed(() => dayjs(props.message.createdAt).format('HH:mm'))
const avatarColors = ['#5865f2','#eb459e','#f2a23c','#22c55e','#ed4245','#fbbf24']
const avatarColor = computed(() => {
  let hash = 0
  for (const c of props.message.username) hash = c.charCodeAt(0) + ((hash << 5) - hash)
  return avatarColors[Math.abs(hash) % avatarColors.length]
})

function onContextMenu(e: MouseEvent) {
  emit('ctxmenu', e, props.message)
}
</script>

<template>
  <div class="msg" @contextmenu.prevent="onContextMenu">
    <div class="msg-avatar" :style="{ background: avatarColor }">{{ message.username.charAt(0).toUpperCase() }}</div>
    <div class="msg-body">
      <div class="msg-header">
        <span class="msg-user" :style="{ color: avatarColor }">{{ message.username }}</span>
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
.msg {
  display: flex; gap: 16px; padding: 4px 16px;
  margin-top: 1px; transition: background .05s;
}
.msg:hover { background: rgba(255,255,255,.02); }
.msg-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 16px; flex-shrink: 0; color: #fff; margin-top: 2px;
}
.msg-body { min-width: 0; flex: 1; }
.msg-header { display: flex; gap: 8px; align-items: baseline; }
.msg-user { font-weight: 600; font-size: 15px; }
.msg-time { font-size: 11px; color: var(--color-text-3); opacity: 0; transition: opacity .1s; }
.msg:hover .msg-time { opacity: 1; }
.msg-edited { font-size: 10px; color: var(--color-text-3); }
.msg-content { word-break: break-word; font-size: 15px; line-height: 1.5; color: var(--color-text-1); margin-top: 1px; }
</style>
