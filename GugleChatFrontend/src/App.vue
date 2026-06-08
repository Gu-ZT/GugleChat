<script setup lang="ts">
</script>

<template>
  <router-view />
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
