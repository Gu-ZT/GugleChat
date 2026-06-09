<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
import { watch } from 'vue'

const themeStore = useThemeStore()

watch(() => themeStore.mode, (mode) => {
  document.body.setAttribute('arco-theme', mode)
}, { immediate: true })
</script>

<template>
  <a-config-provider>
    <router-view />
  </a-config-provider>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: var(--color-bg-1);
  color: var(--color-text-1);
}
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--color-border-2); border-radius: 3px; }
</style>
