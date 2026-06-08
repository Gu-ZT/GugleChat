import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'guglechat_backend_url'

export const useSettingsStore = defineStore('settings', () => {
  const backendUrl = ref(localStorage.getItem(STORAGE_KEY) || '')

  function setBackendUrl(url: string) {
    // Normalize: trim trailing slash
    backendUrl.value = url.replace(/\/+$/, '')
    localStorage.setItem(STORAGE_KEY, backendUrl.value)
  }

  function getApiUrl(): string {
    return backendUrl.value || ''
  }

  function getWsUrl(): string {
    const url = backendUrl.value || window.location.origin
    return url.replace(/^http/, 'ws')
  }

  return { backendUrl, setBackendUrl, getApiUrl, getWsUrl }
})
