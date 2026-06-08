<script setup lang="ts">
import { ref } from 'vue'
import { useWebSocketStore } from '@/stores/websocket'
import { fileService } from '@/services/fileService'
import { IconSend, IconFolder } from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'

const props = defineProps<{ channelId: number }>()
const wsStore = useWebSocketStore()
const text = ref('')
const uploading = ref(false)

function handleSend() {
  const content = text.value.trim()
  if (!content) return
  wsStore.sendMessage(props.channelId, content)
  text.value = ''
}

function triggerUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = true
  input.onchange = async () => {
    const files = input.files
    if (!files) return
    uploading.value = true
    try {
      for (const file of Array.from(files)) {
        const info = await fileService.upload(file)
        const isImage = info.contentType?.startsWith('image/')
        const isVideo = info.contentType?.startsWith('video/')
        const isAudio = info.contentType?.startsWith('audio/')
        const markdown = isImage
          ? `![${info.originalName}](${fileService.getUrl(info.id)})`
          : isVideo
            ? `🎬 [${info.originalName}](${fileService.getUrl(info.id)})`
            : isAudio
              ? `🎵 [${info.originalName}](${fileService.getUrl(info.id)})`
              : `📎 [${info.originalName}](${fileService.getUrl(info.id)})`
        wsStore.sendMessage(props.channelId, markdown)
      }
    } catch (e: any) {
      Message.error('Upload failed: ' + (e.message || 'unknown error'))
    } finally {
      uploading.value = false
    }
  }
  input.click()
}
</script>

<template>
  <div class="chat-input">
    <a-button type="text" size="small" :loading="uploading" @click="triggerUpload" title="Upload file">
      <template #icon><IconFolder /></template>
    </a-button>
    <a-textarea v-model="text" placeholder="Type a message... (supports Markdown)"
                :auto-size="{ minRows: 1, maxRows: 5 }" allow-clear
                @keyup.enter.exact="handleSend" />
    <a-button type="primary" :disabled="!text.trim()" @click="handleSend"><template #icon><IconSend /></template></a-button>
  </div>
</template>

<style scoped>
.chat-input { display: flex; gap: 8px; align-items: flex-end; }
.chat-input :deep(.arco-textarea-wrapper) { flex: 1; }
</style>
