<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {useAuthStore} from '@/stores/auth'
import {useChannelStore} from '@/stores/channel'
import {useWebSocketStore} from '@/stores/websocket'
import {connStateColor, connStateLabel, useRtcStore} from '@/stores/rtc'
import {useContextMenu} from '@/composables/useContextMenu'
import {
  IconCloseCircle,
  IconDesktop,
  IconMessage,
  IconMute,
  IconPhone,
  IconPlus,
  IconSettings,
  IconSound,
  IconThunderbolt,
  IconTrophy,
  IconVideoCamera,
  IconVoice
} from '@arco-design/web-vue/es/icon'
import type {ChannelType} from '@/types'

const router = useRouter()
const emit = defineEmits(['openSettings'])
const authStore = useAuthStore()
const channelStore = useChannelStore()
const wsStore = useWebSocketStore()
const rtcStore = useRtcStore()
const { ctxMenu, openChannelMenu, closeMenu } = useContextMenu()

// Resizable sidebar
const savedWidth = localStorage.getItem('guglechat_sidebar_width')
const sidebarWidth = ref(savedWidth ? Math.max(260, Math.min(800, Number(savedWidth))) : 260)
const dragging = ref(false)

function onDragStart(e: MouseEvent) {
  dragging.value = true;
  e.preventDefault()
}

function onDrag(e: MouseEvent) {
  if (!dragging.value) return
  const w = Math.max(260, Math.min(800, e.clientX))
  sidebarWidth.value = w
  localStorage.setItem('guglechat_sidebar_width', String(w))
}

function onDragEnd() {
  dragging.value = false
}

onMounted(() => { document.addEventListener('mousemove', onDrag); rtcStore.enumerateAudioDevices(); rtcStore.enumerateAudioOutputs() })
onMounted(() => document.addEventListener('mouseup', onDragEnd))
onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', onDragEnd)
})

const showCreate = ref(false)
const newName = ref('')
const newType = ref<ChannelType>('TEXT')
const creating = ref(false)
const deleting = ref(false)
let clickTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => document.addEventListener('click', closeMenu))
onUnmounted(() => document.removeEventListener('click', closeMenu))

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

async function handleDeleteChannel() {
  if (!ctxMenu.value.channelId) return
  deleting.value = true
  try {
    await channelStore.deleteChannel(ctxMenu.value.channelId)
  } finally { deleting.value = false; closeMenu() }
}

const channelToDelete = computed(() => {
  if (!ctxMenu.value.channelId) return null
  return channelStore.channels.find(c => c.id === ctxMenu.value.channelId) || null
})

const canDeleteChannel = computed(() => {
  if (!channelToDelete.value || !authStore.user) return false
  return channelToDelete.value.createdBy === authStore.user.id
})

function onChannelCtxMenu(e: MouseEvent, channelId: number) {
  openChannelMenu(e, channelId)
}

function onSectionCtxMenu(e: MouseEvent) {
  openChannelMenu(e, null)
}

function handleChannelClick(c: { id: number; type: ChannelType }) {
  if (c.type === 'VOICE') {
    if (clickTimer) {
      clearTimeout(clickTimer); clickTimer = null
      wsStore.subscribeToChannel(c.id)
      rtcStore.startCall(c.id)
      rtcStore.setShowVoiceChat(false)
      return
    }
    clickTimer = setTimeout(() => { clickTimer = null }, 300)
    if (rtcStore.activeRoomId === c.id) {
      rtcStore.setShowVoiceChat(false)
      channelStore.selectChannel(c.id)
      wsStore.subscribeToChannel(c.id)
      return
    }
  }
  selectTextChannel(c.id)
}

function selectTextChannel(id: number) {
  channelStore.selectChannel(id)
  wsStore.subscribeToChannel(id)
}

function openVoiceChat(c: { id: number; type: ChannelType }) {
  selectTextChannel(c.id)
  rtcStore.setShowVoiceChat(true)
}

function handleLogout() {
  wsStore.disconnect(); authStore.logout(); router.push('/login')
}
</script>

<template>
  <aside class="sidebar" :style="{ width: sidebarWidth + 'px' }">
    <div class="server-header">
      <h3 class="server-name">GugleChat</h3>
    </div>

    <div class="channel-section" @contextmenu.prevent="onSectionCtxMenu">
      <template v-for="c in channelStore.channels" :key="c.id">
        <div class="channel-item"
             :class="{ active: c.id === channelStore.currentChannelId, 'voice-joined': rtcStore.activeRoomId === c.id }"
             @click="handleChannelClick(c)"
             @contextmenu.stop.prevent="onChannelCtxMenu($event, c.id)">
          <IconVoice v-if="c.type === 'VOICE'" class="ch-icon voice-icon"/>
          <IconMessage v-else class="ch-icon"/>
          <span class="ch-name">{{ c.name }}</span>
          <a-button v-if="c.type === 'VOICE'" class="ch-chat-btn" type="text" size="mini"
                    @click.stop="openVoiceChat(c)" title="Open text chat">
            <template #icon><IconMessage/></template>
          </a-button>
          <span v-if="c.type === 'VOICE' && rtcStore.getVoiceUsers(c.id).length > 0" class="voice-count">
            <span class="count-dot"/>{{ rtcStore.getVoiceUsers(c.id).length }}
          </span>
        </div>
        <div v-if="c.type === 'VOICE' && rtcStore.getVoiceUsers(c.id).length > 0" class="voice-users-list">
          <div v-for="u in rtcStore.getVoiceUsers(c.id)" :key="u.userId" class="voice-user-item">
            <span class="vu-dot" :class="{ 'vu-speaking': rtcStore.remoteSpeaking[u.userId] || rtcStore.broadcastSpeaking[u.userId], 'vu-muted': u.userId === authStore.user?.id ? !rtcStore.audioEnabled : rtcStore.mutedPeers[u.userId] }"
                  :style="{ background: connStateColor(u.userId === authStore.user?.id ? 'completed' : (rtcStore.remotePeers[u.userId]?.iceState || rtcStore.peerConnStates[u.userId] || 'new')) }"/>
            <span class="vu-name">{{ u.username }}{{ u.userId === authStore.user?.id ? ' (you)' : '' }}{{ u.userId === rtcStore.hostId ? ' 👑' : '' }}</span>
            <span v-if="u.userId !== authStore.user?.id" class="vu-state">
              {{ rtcStore.remotePeers[u.userId] ? connStateLabel(rtcStore.remotePeers[u.userId].iceState) : rtcStore.peerConnStates[u.userId] ? connStateLabel(rtcStore.peerConnStates[u.userId]) : 'Waiting...' }}
            </span>
            <span class="vu-quality">⭐{{ u.quality?.toFixed(1) || '?' }}</span>
          </div>
        </div>
      </template>
    </div>

    <!-- Voice controls panel -->
    <div v-if="rtcStore.activeRoomId" class="voice-controls-panel">
      <div class="vcp-left">
        <IconSound class="vcp-signal"/>
        <span class="vcp-text">Voice Connected</span>
      </div>
      <div class="vcp-actions">
        <a-button type="text" size="mini" :class="{ 'fx-active': rtcStore.forcedHostId === authStore.user?.id }"
                  @click="wsStore.sendSignaling('rtc.force-host', {})" title="Force Host">
          <template #icon><IconTrophy/></template>
        </a-button>
        <a-button type="text" size="mini" :class="{ active: rtcStore.videoEnabled }"
                  @click="rtcStore.toggleVideo" title="Camera">
          <template #icon><IconVideoCamera/></template>
        </a-button>
        <a-button type="text" size="mini" :class="{ active: rtcStore.screenSharing }"
                  @click="rtcStore.toggleScreenShare" title="Screen Share">
          <template #icon><IconDesktop/></template>
        </a-button>
        <a-button type="text" size="mini" status="danger" @click="rtcStore.endCall()">
          <template #icon><IconCloseCircle/></template>
        </a-button>
      </div>
    </div>

    <!-- Bottom user panel -->
    <div class="user-panel">
      <div class="user-avatar">{{ authStore.user?.username?.charAt(0).toUpperCase() }}</div>
      <div class="user-info">
        <div class="user-name">{{ authStore.user?.username }}</div>
        <div class="user-status"><span class="status-dot online"/>Online</div>
      </div>
      <div class="user-actions">
        <a-popover trigger="hover" position="top">
          <a-button type="text" size="mini" :class="{ 'fx-active': rtcStore.noiseFxEnabled }"
                    @click="rtcStore.toggleNoiseFx()" title="Noise Suppression">
            <template #icon><IconThunderbolt/></template>
          </a-button>
          <template #content>
            <div class="device-list" style="min-width:180px">
              <div class="device-title">Audio Processing</div>
              <a-form-item label="AI Noise Suppression">
                <a-switch :model-value="rtcStore.rnnoiseEnabled" :disabled="!rtcStore.noiseFxEnabled" size="small"
                          @change="rtcStore.toggleRnnoise()" style="margin-left:auto" />
              </a-form-item>
              <a-form-item label="Noise Suppression">
                <a-switch :model-value="rtcStore.noiseSuppression" :disabled="!rtcStore.noiseFxEnabled || rtcStore.rnnoiseEnabled" size="small"
                          @change="rtcStore.toggleNoiseSuppression()" style="margin-left:auto" />
              </a-form-item>
              <a-form-item label="Echo Cancellation">
                <a-switch :model-value="rtcStore.echoCancellation" :disabled="!rtcStore.noiseFxEnabled" size="small"
                          @change="rtcStore.toggleEchoCancellation()" style="margin-left:auto" />
              </a-form-item>
            </div>
          </template>
        </a-popover>
        <a-button type="text" size="mini" :class="{ 'fx-active': rtcStore.monitoring }"
                  @click="rtcStore.setMonitoring(!rtcStore.monitoring)" title="Hear yourself">
          <template #icon><IconPhone/></template>
        </a-button>
        <a-popover trigger="hover" position="top">
          <a-button type="text" size="mini" :class="{ 'mic-muted': !rtcStore.audioEnabled }"
                    @click="rtcStore.toggleAudio" title="Mute / Unmute">
            <template #icon><IconVoice/></template>
          </a-button>
          <template #content>
            <div class="device-title">Audio Input</div>
            <div class="device-list">
              <div v-for="d in rtcStore.audioInputs" :key="d.deviceId" class="device-item"
                   :class="{ active: d.deviceId === rtcStore.currentAudioDevice }"
                   @click="rtcStore.switchAudioDevice(d.deviceId)">{{ d.label }}</div>
            </div>
            <div class="device-title" style="margin-top:8px">Mic Volume</div>
            <a-slider :model-value="rtcStore.micVolume" :min="0" :max="150" :step="1"
                      @change="rtcStore.setMicVolume($event as number)" style="width:100%"/>
          </template>
        </a-popover>
        <a-popover trigger="hover" position="top">
          <a-button type="text" size="mini" :class="{ 'mic-muted': !rtcStore.speakerEnabled }"
                    @click="rtcStore.toggleSpeaker()" title="Mute Speaker">
            <template #icon><IconSound v-if="rtcStore.speakerEnabled"/><IconMute v-else/></template>
          </a-button>
          <template #content>
            <div class="device-title">Audio Output</div>
            <div class="device-list">
              <div v-for="d in rtcStore.audioOutputs" :key="d.deviceId" class="device-item"
                   :class="{ active: d.deviceId === rtcStore.currentOutputDevice }"
                   @click="rtcStore.switchAudioOutput(d.deviceId)">{{ d.label }}</div>
            </div>
            <div class="device-title" style="margin-top:8px">Speaker Volume</div>
            <a-slider :model-value="rtcStore.speakerVolume" :min="0" :max="150" :step="1"
                      @change="rtcStore.setSpeakerVolume($event as number)" style="width:100%"/>
          </template>
        </a-popover>
        <a-button type="text" size="mini" @click="emit('openSettings')" title="Settings">
          <template #icon><IconSettings/></template>
        </a-button>
      </div>
    </div>

    <!-- Global context menu (sidebar items) -->
    <div v-if="ctxMenu.show && ctxMenu.type === 'channel'" class="context-menu"
         :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }" @click.stop>
      <div class="ctx-item" @click="closeMenu(); showCreate = true">
        <IconPlus class="ctx-icon"/>
        <span>Create Channel</span>
      </div>
      <div v-if="canDeleteChannel" class="ctx-item ctx-item-danger" @click="handleDeleteChannel">
        <span>Delete Channel</span>
      </div>
    </div>

    <a-modal v-model:visible="showCreate" title="Create Channel" @ok="handleCreate"
             :ok-loading="creating" :ok-button-props="{ disabled: !newName.trim() }">
      <a-space direction="vertical" fill size="medium">
        <a-input v-model="newName" placeholder="Channel name"/>
        <a-select v-model="newType" :options="[{label:'Text Channel',value:'TEXT'},{label:'Voice Channel',value:'VOICE'}]"/>
      </a-space>
    </a-modal>
    <div class="resize-handle" @mousedown="onDragStart"/>
  </aside>
</template>

<style scoped>
.sidebar { width: 260px; min-width: 260px; background: var(--color-bg-1); display: flex; flex-direction: column; position: relative; }
.resize-handle { position: absolute; top: 0; right: 0; bottom: 0; width: 4px; cursor: col-resize; z-index: 10; }
.resize-handle:hover, .resize-handle:active { background: rgb(var(--primary-6)); }
.server-header { padding: 12px 16px; border-bottom: 1px solid var(--color-border-2); cursor: pointer; }
.server-header:hover { background: var(--color-bg-3); }
.server-name { color: #fff; font-size: 15px; font-weight: 600; margin: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.channel-section { flex: 1; overflow-y: auto; padding: 8px 8px 0; }
.channel-item { display: flex; align-items: center; gap: 6px; padding: 6px 8px; margin: 1px 0; border-radius: 4px; cursor: pointer; color: var(--color-text-3); font-size: 14px; transition: background .1s; }
.channel-item:hover { background: var(--color-bg-3); color: var(--color-text-1); }
.channel-item.active { background: var(--color-bg-4); color: var(--color-text-1); }
.channel-item.voice-joined { color: rgb(var(--green-6)); }
.ch-icon { font-size: 16px; width: 20px; text-align: center; flex-shrink: 0; }
.ch-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.voice-count { display: flex; align-items: center; gap: 3px; font-size: 11px; color: rgb(var(--green-6)); }
.count-dot { width: 6px; height: 6px; border-radius: 50%; background: rgb(var(--green-6)); }
.voice-users-list { padding: 2px 0 4px 28px; }
.voice-user-item { display: flex; align-items: center; gap: 6px; padding: 3px 0; font-size: 13px; color: var(--color-text-3); }
.vu-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; transition: background .15s; }
.vu-dot.vu-speaking { background: rgb(var(--green-6)) !important; box-shadow: 0 0 6px rgb(var(--green-6)); }
.vu-dot.vu-muted { background: rgb(var(--red-6)) !important; box-shadow: 0 0 6px rgb(var(--red-6)); }
.vu-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vu-state { font-size: 10px; color: var(--color-text-3); margin-left: auto; }
.vu-quality { font-size: 10px; color: rgb(var(--orange-6)); margin-left: auto; }
.ch-chat-btn { opacity: 0; margin-left: auto; }
.channel-item:hover .ch-chat-btn { opacity: 1; }
.ch-chat-btn :deep(.arco-btn) { color: var(--color-text-2); }
.voice-controls-panel { display: flex; align-items: center; justify-content: space-between; padding: 8px 8px; background: var(--color-bg-2); border-top: 1px solid var(--color-border-2); border-bottom: 1px solid var(--color-border-2); gap: 4px; }
.vcp-left { display: flex; align-items: center; gap: 6px; min-width: 0; }
.vcp-signal { color: rgb(var(--green-6)); font-size: 16px; flex-shrink: 0; }
.vcp-text { font-size: 12px; color: rgb(var(--green-6)); font-weight: 600; white-space: nowrap; }
.vcp-actions { display: flex; gap: 2px; }
.vcp-actions :deep(.arco-btn-text) { color: var(--color-text-2); }
.vcp-actions :deep(.arco-btn-text:hover) { color: var(--color-text-1); background: var(--color-bg-3); }
.vcp-actions :deep(.arco-btn-text.active) { color: rgb(var(--green-6)); }
.device-list { min-width: 180px; max-height: 320px; overflow-y: auto; }
.device-title { font-size: 11px; color: var(--color-text-3); font-weight: 600; text-transform: uppercase; padding: 4px 0 8px; }
.device-item { font-size: 13px; padding: 6px 8px; border-radius: 4px; cursor: pointer; color: var(--color-text-1); }
.device-item:hover { background: rgb(var(--primary-6)); color: #fff; }
.device-item.active { color: rgb(var(--green-6)); }
.context-menu { position: fixed; z-index: 1000; background: var(--color-bg-1); border: 1px solid var(--color-border-2); border-radius: 4px; padding: 4px 0; min-width: 160px; box-shadow: 0 8px 16px rgba(0, 0, 0, 0.4); }
.ctx-item { display: flex; align-items: center; gap: 8px; padding: 6px 12px; font-size: 13px; color: var(--color-text-2); cursor: pointer; }
.ctx-item:hover { background: rgb(var(--primary-6)); color: #fff; border-radius: 2px; }
.ctx-icon { font-size: 14px; }
.ctx-item-danger { color: rgb(var(--red-6)); }
.ctx-item-danger:hover { background: rgb(var(--red-6)); color: #fff; }
.user-panel { display: flex; align-items: center; gap: 8px; padding: 0 8px; height: 52px; background: var(--color-bg-2); margin-top: auto; }
.user-avatar { width: 32px; height: 32px; border-radius: 50%; background: rgb(var(--primary-6)); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 600; color: #fff; flex-shrink: 0; }
.user-info { flex: 1; min-width: 0; }
.user-name { font-size: 13px; color: var(--color-text-1); font-weight: 600; }
.user-status { font-size: 11px; color: var(--color-text-3); display: flex; align-items: center; gap: 4px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.status-dot.online { background: rgb(var(--green-6)); }
.user-actions { display: flex; gap: 2px; }
.user-actions :deep(.arco-btn-text) { color: var(--color-text-2); }
.user-actions :deep(.arco-btn-text:hover) { color: var(--color-text-1); background: var(--color-bg-3); }
.mic-muted, .mic-muted :deep(*) { color: rgb(var(--red-6)) !important; }
.fx-active, .fx-active :deep(*) { color: rgb(var(--green-6)) !important; }
.fx-disabled { opacity: 0.4; pointer-events: none; }
</style>
