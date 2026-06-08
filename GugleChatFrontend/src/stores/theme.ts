import { defineStore } from 'pinia'
import { ref } from 'vue'

const KEY = 'guglechat_theme'

export const useThemeStore = defineStore('theme', () => {
  const saved = localStorage.getItem(KEY) || 'dark'
  const mode = ref<'light' | 'dark'>(saved as 'light' | 'dark')

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem(KEY, mode.value)
  }

  return { mode, toggle }
})
