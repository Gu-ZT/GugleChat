<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Message } from '@arco-design/web-vue'
import { IconSettings } from '@arco-design/web-vue/es/icon'

const router = useRouter()
const authStore = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)
const showServerConfig = ref(false)
const backendUrl = ref('')

onMounted(() => {
  backendUrl.value = localStorage.getItem('guglechat_backend_url') || ''
})

function saveBackendUrl() {
  const url = backendUrl.value.trim().replace(/\/+$/, '')
  localStorage.setItem('guglechat_backend_url', url)
  Message.success(url ? `Server: ${url}` : 'Using default connection')
}

async function handleLogin() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    await authStore.login(username.value, password.value)
    router.push('/')
  } catch (e: any) {
    Message.error(e.response?.data?.message || e.message || 'Login failed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-container">
    <a-card class="auth-card" :bordered="false">
      <template #title>
        <div class="auth-header">
          <h1 class="auth-title">GugleChat</h1>
          <a-button type="text" size="small" @click="showServerConfig = !showServerConfig"
                    :style="{ color: showServerConfig ? '#80b4ff' : '#888' }">
            <IconSettings />
          </a-button>
        </div>
      </template>

      <!-- Server config (collapsible) -->
      <div v-if="showServerConfig" class="server-config">
        <a-input v-model="backendUrl" placeholder="Backend URL (e.g. http://server:3250)"
                 size="small" allow-clear @change="saveBackendUrl">
          <template #prepend>Server</template>
        </a-input>
      </div>

      <a-space direction="vertical" size="large" fill>
        <a-input v-model="username" placeholder="Username" size="large" allow-clear @keyup.enter="handleLogin" />
        <a-input v-model="password" type="password" placeholder="Password" size="large" allow-clear @keyup.enter="handleLogin" />
        <a-button type="primary" size="large" long :loading="loading" @click="handleLogin">Sign In</a-button>
        <a-button type="text" long @click="router.push('/register')">Don't have an account? Register</a-button>
      </a-space>
    </a-card>
  </div>
</template>

<style scoped>
.auth-container {
  display: flex; align-items: center; justify-content: center;
  height: 100vh;
  background: #313338;
}
.auth-card { width: 400px; max-width: 90vw; }
.auth-header { display: flex; justify-content: space-between; align-items: center; }
.auth-title { font-size: 28px; margin: 0; }
.server-config { margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border-2); }
</style>
