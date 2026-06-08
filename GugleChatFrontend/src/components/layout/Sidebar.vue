<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChannelStore } from '@/stores/channel'
import { useWebSocketStore } from '@/stores/websocket'
import { useRtcStore, connStateLabel, connStateColor } from '@/stores/rtc'
import { IconPlus, IconSound, IconVoice, IconMute, IconSettings, IconClose, IconUser, IconMessage, IconVideoCamera, IconCloseCircle } from '@arco-design/web-vue/es/icon'
import type { ChannelType } from '@/types'

const router = useRouter()
const authStore = useAuthStore()
const channelStore = useChannelStore()
const wsStore = useWebSocketStore()
const rtcStore = useRtcStore()

const showCreate = ref(false)
const contextMenu = ref({ show: false, x: 0, y: 0 })
const newName = ref('')
const newType = ref<ChannelType>('TEXT')
const creating = ref(false)
let clickTimer: ReturnType<typeof setTimeout> | null = null

function onContextMenu(e: MouseEvent) {
  contextMenu.value = { show: true, x: e.clientX, y: e.clientY }
}
function closeContextMenu() {
  contextMenu.value.show = false
}
onMounted(() => document.addEventListener('click', closeContextMenu))
onUnmounted(() => document.removeEventListener('click', closeContextMenu))

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
    if (clickTimer) {
      clearTimeout(clickTimer); clickTimer = null
      wsStore.subscribeToChannel(c.id)
      rtcStore.startCall(c.id)
      rtcStore.showVoiceChat = false
      return
    }
    clickTimer = setTimeout(() => { clickTimer = null }, 300)
  }
  selectTextChannel(c.id)
}

function selectTextChannel(id: number) {
  channelStore.selectChannel(id)
  wsStore.subscribeToChannel(id)
  if (rtcStore.activeRoomId) rtcStore.showVoiceChat = true
}

function openVoiceChat(c: { id: number; type: ChannelType }) {
  selectTextChannel(c.id)
}

function handleLogout() { wsStore.disconnect(); authStore.logout(); router.push('/login') }
</script>

<template>
  <aside class="sidebar">
    <!-- Server header (clickable for home) -->
    <div class="server-header">
      <h3 class="server-name">GugleChat</h3>
    </div>

    <!-- Channel list -->
    <div class="channel-section" @contextmenu.prevent="onContextMenu">
      <template v-for="c in channelStore.channels" :key="c.id">
        <div class="channel-item"
             :class="{
               active: c.id === channelStore.currentChannelId,
               'voice-joined': rtcStore.activeRoomId === c.id
             }"
             @click="handleChannelClick(c)">
          <IconVoice v-if="c.type === 'VOICE'" class="ch-icon voice-icon" />
          <IconMessage v-else class="ch-icon" />
          <span class="ch-name">{{ c.name }}</span>
          <a-button v-if="c.type === 'VOICE' && rtcStore.activeRoomId === c.id"
                    class="ch-chat-btn" type="text" size="mini"
                    @click.stop="openVoiceChat(c)" title="Open text chat">
            <template #icon><IconMessage /></template>
          </a-button>
          <span v-if="c.type === 'VOICE' && rtcStore.voiceUsers.length > 0 && rtcStore.activeRoomId === c.id"
                class="voice-count">
            <span class="count-dot" />{{ rtcStore.voiceUsers.length }}
          </span>
        </div>
        <!-- Connected voice users -->
        <div v-if="c.type === 'VOICE' && rtcStore.activeRoomId === c.id" class="voice-users-list">
          <div v-for="u in rtcStore.voiceUsers" :key="u.userId" class="voice-user-item">
            <span class="vu-dot" :class="{ 'vu-speaking': rtcStore.remoteSpeaking[u.userId] }"
                  :style="{ background: connStateColor(u.userId === authStore.user?.id ? 'completed' : (rtcStore.remotePeers[u.userId]?.iceState || 'new')) }" />
            <span class="vu-name">{{ u.username }}{{ u.userId === authStore.user?.id ? ' (you)' : '' }}{{ u.userId === rtcStore.hostId ? ' 👑' : '' }}</span>
            <span v-if="u.userId !== authStore.user?.id && rtcStore.remotePeers[u.userId]" class="vu-state">
              {{ connStateLabel(rtcStore.remotePeers[u.userId].iceState) }}
            </span>
          </div>
        </div>
      </template>
    </div>

    <!-- Voice controls (above user panel) -->
    <div v-if="rtcStore.activeRoomId" class="voice-controls-panel">
      <div class="vcp-left">
        <IconSound class="vcp-signal" />
        <span class="vcp-text">Voice Connected</span>
      </div>
      <div class="vcp-actions">
        <a-checkbox :model-value="rtcStore.monitoring"
                    @change="rtcStore.setMonitoring(!rtcStore.monitoring)" title="Hear yourself" />
        <a-popover trigger="hover" position="top">
          <a-button type="text" size="mini"
                    :status="!rtcStore.audioEnabled ? 'danger' : undefined"
                    @click="rtcStore.toggleAudio">
            <template #icon><IconSound v-if="rtcStore.audioEnabled" /><IconMute v-else /></template>
          </a-button>
          <template #content>
            <div class="device-list">
              <div class="device-title">Audio Input</div>
              <div v-for="d in rtcStore.audioInputs" :key="d.deviceId"
                   class="device-item"
                   :class="{ active: d.deviceId === rtcStore.currentAudioDevice }"
                   @click="rtcStore.switchAudioDevice(d.deviceId)">{{ d.label }}</div>
            </div>
          </template>
        </a-popover>
        <a-button type="text" size="mini"
                  :class="{ active: rtcStore.videoEnabled }"
                  @click="rtcStore.toggleVideo">
          <template #icon><IconVideoCamera /></template>
        </a-button>
        <a-button type="text" size="mini" status="danger" @click="rtcStore.endCall()">
          <template #icon><IconCloseCircle /></template>
        </a-button>
      </div>
    </div>

    <!-- Bottom user panel (Discord-style) -->
    <div class="user-panel">
      <div class="user-avatar">{{ authStore.user?.username?.charAt(0).toUpperCase() }}</div>
      <div class="user-info">
        <div class="user-name">{{ authStore.user?.username }}</div>
        <div class="user-status">
          <span class="status-dot online" />
          Online
        </div>
      </div>
      <div class="user-actions">
        <a-button type="text" size="mini" @click="router.push('/settings')">
          <template #icon><IconSettings /></template>
        </a-button>
      </div>
    </div>

    <!-- Context menu -->
    <div v-if="contextMenu.show" class="context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
      <div class="ctx-item" @click="closeContextMenu(); showCreate = true">
        <IconPlus class="ctx-icon" />
        <span>Create Channel</span>
      </div>
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
.sidebar {
  width: 240px; min-width: 240px;
  background: #1e1f22;
  display: flex; flex-direction: column;
}
.server-header {
  padding: 12px 16px;
  border-bottom: 1px solid #2b2d31;
  cursor: pointer;
}
.server-header:hover { background: #35373c; }
.server-name { color: #fff; font-size: 15px; font-weight: 600; margin: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.channel-section { flex: 1; overflow-y: auto; padding: 8px 8px 0; }
.section-header {
  display: flex; align-items: center; gap: 4px;
  padding: 16px 8px 6px 8px;
  font-size: 11px; color: #949ba4; font-weight: 600; letter-spacing: 0.5px;
  text-transform: uppercase; cursor: pointer;
}
.section-header:hover { color: #dbdee1; }
.section-arrow { font-size: 10px; }
.add-icon { margin-left: auto; font-size: 14px; opacity: 0; }
.section-header:hover .add-icon { opacity: 1; }

.channel-item {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px; margin: 1px 0;
  border-radius: 4px; cursor: pointer;
  color: #949ba4; font-size: 14px;
  transition: background .1s;
}
.channel-item:hover { background: #35373c; color: #dbdee1; }
.channel-item.active { background: #404249; color: #f2f3f5; }
.channel-item.voice-joined { color: #22c55e; }
.ch-icon { font-size: 16px; width: 20px; text-align: center; flex-shrink: 0; }
.ch-hash { font-weight: 600; color: #949ba4; }
.ch-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.voice-count { display: flex; align-items: center; gap: 3px; font-size: 11px; color: #22c55e; }
.count-dot { width: 6px; height: 6px; border-radius: 50%; background: #22c55e; }

.voice-users-list { padding: 2px 0 4px 28px; }
.voice-user-item { display: flex; align-items: center; gap: 6px; padding: 3px 0; font-size: 13px; color: #949ba4; }
.vu-icon { font-size: 14px; }
.vu-icon.speaking { color: #22c55e; }
.vu-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; transition: background .15s; }
.vu-dot.vu-speaking { background: #22c55e !important; box-shadow: 0 0 6px #22c55e; }
.vu-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vu-state { font-size: 10px; color: #949ba4; margin-left: auto; }

.ch-chat-btn { opacity: 0; margin-left: auto; }
.channel-item:hover .ch-chat-btn { opacity: 1; }
.ch-chat-btn :deep(.arco-btn) { color: #b5bac1; }

/* Voice controls panel */
.voice-controls-panel {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 8px; background: #232428; border-top: 1px solid #2b2d31;
  border-bottom: 1px solid #2b2d31; gap: 4px;
}
.vcp-left { display: flex; align-items: center; gap: 6px; min-width: 0; }
.vcp-signal { color: #22c55e; font-size: 16px; flex-shrink: 0; }
.vcp-text { font-size: 12px; color: #22c55e; font-weight: 600; white-space: nowrap; }
.vcp-actions { display: flex; gap: 2px; }
.vcp-actions :deep(.arco-btn-text) { color: #b5bac1; }
.vcp-actions :deep(.arco-btn-text:hover) { color: #dbdee1; background: #35373c; }
.vcp-actions :deep(.arco-btn-text.active) { color: #22c55e; }
.vcp-actions :deep(.arco-checkbox) { margin-top: 2px; }

.device-list { min-width: 180px; }
.device-title { font-size: 11px; color: #949ba4; font-weight: 600; text-transform: uppercase; padding: 4px 0 8px; }
.device-item { font-size: 13px; padding: 6px 8px; border-radius: 4px; cursor: pointer; color: #dbdee1; }
.device-item:hover { background: #5865f2; color: #fff; }
.device-item.active { color: #22c55e; }

.context-menu {
  position: fixed; z-index: 1000;
  background: #1e1f22; border: 1px solid #2b2d31; border-radius: 4px;
  padding: 4px 0; min-width: 160px; box-shadow: 0 8px 16px rgba(0,0,0,0.4);
}
.ctx-item {
  display: flex; align-items: center; gap: 8px; padding: 6px 12px;
  font-size: 13px; color: #b5bac1; cursor: pointer;
}
.ctx-item:hover { background: #5865f2; color: #fff; border-radius: 2px; }
.ctx-icon { font-size: 14px; }

/* Bottom user panel */
.user-panel {
  display: flex; align-items: center; gap: 8px;
  padding: 0 8px; height: 52px;
  background: #232428; margin-top: auto;
}
.user-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  background: #5865f2; display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 600; color: #fff; flex-shrink: 0;
}
.user-info { flex: 1; min-width: 0; }
.user-name { font-size: 13px; color: #f2f3f5; font-weight: 600; }
.user-status { font-size: 11px; color: #949ba4; display: flex; align-items: center; gap: 4px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.status-dot.online { background: #22c55e; }
.user-actions { display: flex; gap: 2px; }
.user-actions :deep(.arco-btn-text) { color: #b5bac1; }
.user-actions :deep(.arco-btn-text:hover) { color: #dbdee1; background: #35373c; }
</style>
