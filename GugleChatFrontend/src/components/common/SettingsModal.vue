<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useSettingsStore } from '@/stores/settings'
import { useWebSocketStore } from '@/stores/websocket'
import { Message } from '@arco-design/web-vue'
import { IconPoweroff, IconSun, IconMoon, IconClose } from '@arco-design/web-vue/es/icon'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits(['update:visible', 'logout'])

const authStore = useAuthStore()
const themeStore = useThemeStore()
const settingsStore = useSettingsStore()
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

function handleClose() { emit('update:visible', false) }
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
  Message.success('TURN settings saved')
}
function handleLogout() {
  wsStore.disconnect()
  authStore.logout()
  emit('logout')
}
</script>

<template>
  <a-modal :visible="visible" @cancel="handleClose" :footer="false" :width="460" title="Settings" :mask-closable="true">
    <a-form layout="vertical">
      <a-form-item><p>Logged in as <strong>{{ authStore.user?.username }}</strong></p></a-form-item>
      <a-form-item label="Theme">
        <a-button @click="themeStore.toggle">
          <template #icon><IconSun v-if="themeStore.mode === 'light'" /><IconMoon v-else /></template>
          {{ themeStore.mode === 'dark' ? 'Dark' : 'Light' }}
        </a-button>
      </a-form-item>
      <a-form-item label="Backend URL" help="Leave empty for proxy/same-origin">
        <a-input v-model="backendUrl" placeholder="http://server:3250" allow-clear @change="saveBackendUrl" />
      </a-form-item>
      <a-divider>Voice & Video</a-divider>
      <a-form-item label="NAT Type" help="Override auto-detection. Worse type between manual & detected is used.">
        <a-select :model-value="settingsStore.natOverride" placeholder="Auto" allow-clear
                  @update:model-value="settingsStore.setNatOverride" :style="{ width: '100%' }">
          <a-option value="">Auto (detect)</a-option>
          <a-option value="1">NAT1 — Open / Full Cone</a-option>
          <a-option value="2">NAT2 — Restricted Cone</a-option>
          <a-option value="3">NAT3 — Port Restricted Cone</a-option>
          <a-option value="4">NAT4 — Symmetric</a-option>
        </a-select>
      </a-form-item>
      <a-button type="text" long @click="showTurn = !showTurn">{{ showTurn ? '▼' : '▶' }} TURN Server Config</a-button>
      <template v-if="showTurn">
        <a-form-item label="TURN URL"><a-input v-model="turnUrl" placeholder="turn:server:3478" allow-clear @change="saveTurn" /></a-form-item>
        <a-form-item label="Username"><a-input v-model="turnUsername" placeholder="username" allow-clear @change="saveTurn" /></a-form-item>
        <a-form-item label="Password"><a-input v-model="turnPassword" type="password" placeholder="password" allow-clear @change="saveTurn" /></a-form-item>
      </template>
      <a-divider />
      <a-button type="primary" status="danger" long @click="handleLogout"><template #icon><IconPoweroff /></template>Logout</a-button>
    </a-form>
  </a-modal>
</template>
