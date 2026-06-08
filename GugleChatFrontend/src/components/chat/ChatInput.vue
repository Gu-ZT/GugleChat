<script setup lang="ts">
import { ref } from 'vue'
import { useWebSocketStore } from '@/stores/websocket'

const props = defineProps<{ channelId: number }>()
const wsStore = useWebSocketStore()
const text = ref('')

function handleSend() {
  const content = text.value.trim()
  if (!content) return
  wsStore.sendMessage(props.channelId, content)
  text.value = ''
}
</script>

<template>
  <div class="chat-input">
    <a-textarea v-model="text" placeholder="Type a message... (supports Markdown)"
                :auto-size="{ minRows: 1, maxRows: 5 }" allow-clear
                @keyup.enter.exact="handleSend" />
    <a-button type="primary" :disabled="!text.trim()" @click="handleSend">Send</a-button>
  </div>
</template>

<style scoped>
.chat-input { display: flex; gap: 12px; align-items: flex-end; }
.chat-input :deep(.arco-textarea-wrapper) { flex: 1; }
</style>
