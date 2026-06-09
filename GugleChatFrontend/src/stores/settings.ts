import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'guglechat_backend_url'
const NAT_KEY = 'guglechat_nat_override'

export const useSettingsStore = defineStore('settings', () => {
  const backendUrl = ref(localStorage.getItem(STORAGE_KEY) || '')
  const natOverride = ref(localStorage.getItem(NAT_KEY) || '')

  function setBackendUrl(url: string) {
    backendUrl.value = url.replace(/\/+$/, '')
    localStorage.setItem(STORAGE_KEY, backendUrl.value)
  }

  function setNatOverride(value: string) {
    natOverride.value = value
    localStorage.setItem(NAT_KEY, value)
  }

  function getApiUrl(): string {
    return backendUrl.value || ''
  }

  function getWsUrl(): string {
    const url = backendUrl.value || window.location.origin
    return url.replace(/^http/, 'ws')
  }

  return { backendUrl, natOverride, setBackendUrl, setNatOverride, getApiUrl, getWsUrl }
})
