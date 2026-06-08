<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Message } from '@arco-design/web-vue'

const router = useRouter()
const authStore = useAuthStore()
const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)

async function handleRegister() {
  if (password.value !== confirmPassword.value) {
    Message.error('Passwords do not match'); return
  }
  loading.value = true
  try {
    await authStore.register(username.value, email.value, password.value)
    router.push('/')
  } catch (e: any) {
    Message.error(e.response?.data?.message || e.message || 'Registration failed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-container">
    <a-card class="auth-card" :bordered="false">
      <template #title><h1 class="auth-title">Create Account</h1></template>
      <a-space direction="vertical" size="large" fill>
        <a-input v-model="username" placeholder="Username" size="large" allow-clear />
        <a-input v-model="email" placeholder="Email" size="large" allow-clear />
        <a-input v-model="password" type="password" placeholder="Password" size="large" allow-clear />
        <a-input v-model="confirmPassword" type="password" placeholder="Confirm Password" size="large" allow-clear
          @keyup.enter="handleRegister" />
        <a-button type="primary" size="large" long :loading="loading" @click="handleRegister">Register</a-button>
        <a-button type="text" long @click="router.push('/login')">Already have an account? Sign In</a-button>
      </a-space>
    </a-card>
  </div>
</template>

<style scoped>
.auth-container { display: flex; align-items: center; justify-content: center; height: 100vh; background: #313338; }
.auth-card { width: 400px; max-width: 90vw; }
.auth-title { font-size: 28px; margin: 0; }
</style>
