import { defineStore } from 'pinia'
import { ref } from 'vue'

const KEY = 'guglechat_theme'
const VALID_MODES = ['light', 'dark'] as const

export const useThemeStore = defineStore('theme', () => {
  const saved = localStorage.getItem(KEY)
  const initial = VALID_MODES.includes(saved as 'light' | 'dark') ? saved as 'light' | 'dark' : 'dark'
  const mode = ref<'light' | 'dark'>(initial)

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem(KEY, mode.value)
  }

  return { mode, toggle }
})
