<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Message } from '@arco-design/web-vue'

const router = useRouter()
const authStore = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

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
        <h1 class="auth-title">GugleChat</h1>
      </template>
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
  background: linear-gradient(135deg, #1a1a2e, #16213e, #0f3460);
}
.auth-card { width: 400px; max-width: 90vw; }
.auth-title { font-size: 28px; margin: 0; }
</style>
