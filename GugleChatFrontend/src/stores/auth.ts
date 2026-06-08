import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'
import { authService } from '@/services/authService'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))
  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const res = await authService.login(username, password)
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('token', res.data.token)
    return res.data
  }

  async function register(username: string, email: string, password: string) {
    const res = await authService.register(username, email, password)
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('token', res.data.token)
    return res.data
  }

  async function fetchMe() {
    if (!token.value) return
    try { user.value = (await authService.getMe()).data }
    catch { logout() }
  }

  function logout() {
    token.value = null; user.value = null; localStorage.removeItem('token')
  }

  return { user, token, isLoggedIn, login, register, fetchMe, logout }
})
