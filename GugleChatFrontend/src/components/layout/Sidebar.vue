<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChannelStore } from '@/stores/channel'
import { useWebSocketStore } from '@/stores/websocket'
import { IconPlus, IconNotification, IconSettings, IconPoweroff } from '@arco-design/web-vue/es/icon'
import type { ChannelType } from '@/types'

const router = useRouter()
const authStore = useAuthStore()
const channelStore = useChannelStore()
const wsStore = useWebSocketStore()

const showCreate = ref(false)
const newName = ref('')
const newType = ref<ChannelType>('TEXT')
const creating = ref(false)

async function handleCreate() {
  if (!newName.value.trim()) return
  creating.value = true
  try {
    const c = await channelStore.createChannel(newName.value.trim(), newType.value)
    channelStore.selectChannel(c.id)
    wsStore.subscribeToChannel(c.id)
    newName.value = ''; showCreate.value = false
  } finally { creating.value = false }
}

function selectChannel(id: number) { channelStore.selectChannel(id); wsStore.subscribeToChannel(id) }
function handleLogout() { wsStore.disconnect(); authStore.logout(); router.push('/login') }
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <h2 class="logo">GugleChat</h2>
      <span class="uname">{{ authStore.user?.username }}</span>
    </div>

    <div class="channel-section">
      <div class="section-header">
        <span>CHANNELS</span>
        <a-button size="mini" @click="showCreate = true"><IconPlus /></a-button>
      </div>
      <div v-for="c in channelStore.channels" :key="c.id"
           class="channel-item" :class="{ active: c.id === channelStore.currentChannelId }"
           @click="selectChannel(c.id)">
        <IconNotification v-if="c.type === 'VOICE'" class="ch-icon" />
        <span v-else class="ch-icon">#</span>
        <span class="ch-name">{{ c.name }}</span>
        <a-tag size="small" :color="c.type === 'VOICE' ? 'green' : 'arcoblue'">{{ c.type }}</a-tag>
      </div>
    </div>

    <div class="sidebar-footer">
      <a-button type="text" size="small" @click="router.push('/settings')"><template #icon><IconSettings /></template></a-button>
      <a-button type="text" size="small" status="danger" @click="handleLogout"><template #icon><IconPoweroff /></template></a-button>
    </div>

    <a-modal v-model:visible="showCreate" title="Create Channel" @ok="handleCreate"
             :ok-loading="creating" :ok-button-props="{ disabled: !newName.trim() }">
      <a-space direction="vertical" fill size="medium">
        <a-input v-model="newName" placeholder="Channel name" />
        <a-select v-model="newType" :options="[{label:'Text Channel',value:'TEXT'},{label:'Voice Channel',value:'VOICE'}]" />
      </a-space>
    </a-modal>
  </aside>
</template>

<style scoped>
.sidebar { width: 260px; min-width: 260px; background: #16213e; display: flex; flex-direction: column; border-right: 1px solid #2a2a4a; }
.sidebar-header { padding: 20px 16px 12px; border-bottom: 1px solid #2a2a4a; }
.logo { font-size: 20px; margin: 0; color: #e0e0e0; }
.uname { display: block; margin-top: 4px; font-size: 12px; color: #888; }
.channel-section { flex: 1; overflow-y: auto; padding: 8px 0; }
.section-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 16px 4px; font-size: 11px; color: #888; font-weight: 600; letter-spacing: 1px; }
.channel-item { display: flex; align-items: center; gap: 8px; padding: 8px 16px; cursor: pointer; border-radius: 4px; margin: 0 8px; transition: background .15s; }
.channel-item:hover { background: rgba(255,255,255,.05); }
.channel-item.active { background: rgba(255,255,255,.1); }
.ch-icon { font-size: 16px; }
.ch-name { flex: 1; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #ccc; }
.sidebar-footer { padding: 12px 16px; border-top: 1px solid #2a2a4a; display: flex; justify-content: space-between; }
</style>
