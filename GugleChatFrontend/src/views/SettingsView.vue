<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useWebSocketStore } from '@/stores/websocket'
import { Message } from '@arco-design/web-vue'
import { IconPoweroff, IconSun, IconMoon, IconLeft } from '@arco-design/web-vue/es/icon'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const wsStore = useWebSocketStore()

const backendUrl = ref('')

onMounted(() => {
  backendUrl.value = localStorage.getItem('guglechat_backend_url') || ''
})

function saveBackendUrl() {
  const url = backendUrl.value.trim().replace(/\/+$/, '')
  localStorage.setItem('guglechat_backend_url', url)
  Message.success(url ? `Backend set to ${url}` : 'Backend reset to default (proxy)')
  wsStore.disconnect()
}

function handleLogout() {
  wsStore.disconnect()
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="settings-container">
    <a-card title="Settings" :bordered="false" style="width: 440px">
      <a-space direction="vertical" size="large" fill>
        <div>
          <p>Logged in as <strong>{{ authStore.user?.username }}</strong></p>
        </div>

        <!-- Theme toggle -->
        <a-form-item label="Theme">
          <a-button @click="themeStore.toggle">
            <template #icon>
              <IconSun v-if="themeStore.mode === 'light'" />
              <IconMoon v-else />
            </template>
            {{ themeStore.mode === 'dark' ? 'Dark' : 'Light' }}
          </a-button>
        </a-form-item>

        <a-form-item label="Backend URL" help="Leave empty to use default proxy (dev) or same-origin (production)">
          <a-input v-model="backendUrl" placeholder="e.g. http://server.ztxy666.cn:3250" allow-clear
                   @change="saveBackendUrl" />
        </a-form-item>

        <a-divider />

        <a-button type="primary" status="danger" long @click="handleLogout">
          <template #icon><IconPoweroff /></template>
          Logout
        </a-button>
        <a-button long @click="router.push('/')">
          <template #icon><IconLeft /></template>
          Back to Chat
        </a-button>
      </a-space>
    </a-card>
  </div>
</template>

<style scoped>
.settings-container { display: flex; align-items: center; justify-content: center; height: 100vh; background: var(--color-bg-1); }
</style>
