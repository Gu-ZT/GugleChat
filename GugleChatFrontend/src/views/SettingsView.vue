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
const turnUrl = ref('')
const turnUsername = ref('')
const turnPassword = ref('')
const showTurn = ref(false)

onMounted(() => {
  backendUrl.value = localStorage.getItem('guglechat_backend_url') || ''
  turnUrl.value = localStorage.getItem('guglechat_turn_url') || ''
  turnUsername.value = localStorage.getItem('guglechat_turn_user') || ''
  turnPassword.value = localStorage.getItem('guglechat_turn_pass') || ''
})

function saveBackendUrl() {
  const url = backendUrl.value.trim().replace(/\/+$/, '')
  localStorage.setItem('guglechat_backend_url', url)
  Message.success(url ? `Backend: ${url}` : 'Backend reset to default')
  wsStore.disconnect()
}

function saveTurn() {
  localStorage.setItem('guglechat_turn_url', turnUrl.value.trim())
  localStorage.setItem('guglechat_turn_user', turnUsername.value.trim())
  localStorage.setItem('guglechat_turn_pass', turnPassword.value.trim())
  Message.success('TURN settings saved. Restart voice call to apply.')
}

function handleLogout() {
  wsStore.disconnect()
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="settings-container">
    <a-card title="Settings" :bordered="false" style="width: 460px">
      <a-space direction="vertical" size="large" fill>
        <div>
          <p>Logged in as <strong>{{ authStore.user?.username }}</strong></p>
        </div>

        <a-form-item label="Theme">
          <a-button @click="themeStore.toggle">
            <template #icon>
              <IconSun v-if="themeStore.mode === 'light'" />
              <IconMoon v-else />
            </template>
            {{ themeStore.mode === 'dark' ? 'Dark' : 'Light' }}
          </a-button>
        </a-form-item>

        <a-form-item label="Backend URL" help="Leave empty for proxy/same-origin">
          <a-input v-model="backendUrl" placeholder="http://server:3250" allow-clear
                   @change="saveBackendUrl" />
        </a-form-item>

        <a-divider>Voice & Video</a-divider>

        <a-button type="text" long @click="showTurn = !showTurn">
          {{ showTurn ? '▼' : '▶' }} TURN Server Config
        </a-button>

        <template v-if="showTurn">
          <a-form-item label="TURN URL" help="e.g. turn:server.ztxy666.cn:3478">
            <a-input v-model="turnUrl" placeholder="turn:your-server:3478" allow-clear
                     @change="saveTurn" />
          </a-form-item>
          <a-form-item label="Username">
            <a-input v-model="turnUsername" placeholder="turn username" allow-clear
                     @change="saveTurn" />
          </a-form-item>
          <a-form-item label="Password">
            <a-input v-model="turnPassword" type="password" placeholder="turn password" allow-clear
                     @change="saveTurn" />
          </a-form-item>
        </template>

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
