<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function handleLogout() { authStore.logout(); router.push('/login') }
</script>

<template>
  <div class="settings-container">
    <a-card title="Settings" :bordered="false" style="width: 400px">
      <a-space direction="vertical" fill>
        <p>Logged in as <strong>{{ authStore.user?.username }}</strong></p>
        <a-button type="primary" status="danger" long @click="handleLogout">Logout</a-button>
        <a-button long @click="router.push('/')">Back to Chat</a-button>
      </a-space>
    </a-card>
  </div>
</template>

<style scoped>
.settings-container { display: flex; align-items: center; justify-content: center; height: 100vh; background: #1a1a2e; }
</style>
