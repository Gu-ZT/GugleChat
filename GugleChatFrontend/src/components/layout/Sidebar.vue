<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChannelStore } from '@/stores/channel'
import { useWebSocketStore } from '@/stores/websocket'
import { useRtcStore } from '@/stores/rtc'
import { IconPlus, IconNotification, IconSettings, IconPoweroff, IconUser } from '@arco-design/web-vue/es/icon'
import type { ChannelType } from '@/types'

const router = useRouter()
const authStore = useAuthStore()
const channelStore = useChannelStore()
const wsStore = useWebSocketStore()
const rtcStore = useRtcStore()

const showCreate = ref(false)
const newName = ref('')
const newType = ref<ChannelType>('TEXT')
const creating = ref(false)
let clickTimer: ReturnType<typeof setTimeout> | null = null

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

function handleChannelClick(c: { id: number; type: ChannelType }) {
  if (c.type === 'VOICE') {
    // Double-click detection for voice channels
    if (clickTimer) {
      clearTimeout(clickTimer)
      clickTimer = null
      // Double-click: join voice call
      rtcStore.startCall(c.id)
      wsStore.subscribeToChannel(c.id)
      return
    }
    clickTimer = setTimeout(() => { clickTimer = null }, 300)
  }
  // Single-click: switch to channel (text view)
  channelStore.selectChannel(c.id)
  wsStore.subscribeToChannel(c.id)
}

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
      <div v-for="c in channelStore.channels" :key="c.id">
        <div class="channel-item"
             :class="{
               active: c.id === channelStore.currentChannelId,
               'voice-active': rtcStore.activeRoomId === c.id
             }"
             @click="handleChannelClick(c)">
          <IconNotification v-if="c.type === 'VOICE'" class="ch-icon" />
          <span v-else class="ch-icon ch-hash">#</span>
          <span class="ch-name">{{ c.name }}</span>
          <span v-if="rtcStore.activeRoomId === c.id" class="voice-dot" title="Connected" />
          <a-tag size="small" :color="c.type === 'VOICE' ? 'green' : 'arcoblue'">{{ c.type }}</a-tag>
        </div>
        <!-- Voice users under voice channel -->
        <div v-if="c.type === 'VOICE' && rtcStore.activeRoomId === c.id && rtcStore.voiceUsers.size > 0"
             class="voice-users-list">
          <div v-for="uid in rtcStore.voiceUsers" :key="uid" class="voice-user-item">
            <IconUser class="vu-icon" />
            <span>{{ uid === authStore.user?.id ? 'You' : 'User ' + uid }}</span>
          </div>
        </div>
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
.ch-hash { font-weight: 700; }
.ch-name { flex: 1; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #ccc; }
.voice-active { border-left: 3px solid #22c55e; }
.voice-dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; flex-shrink: 0; }
.voice-users-list { padding: 2px 16px 4px 36px; }
.voice-user-item { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #22c55e; padding: 2px 0; }
.vu-icon { font-size: 12px; }
.sidebar-footer { padding: 12px 16px; border-top: 1px solid #2a2a4a; display: flex; justify-content: space-between; }
</style>
