<script setup lang="ts">
import { ref } from 'vue'
import { useRtcStore, connStateLabel, connStateColor } from '@/stores/rtc'
import { useWebSocketStore } from '@/stores/websocket'
import { useAuthStore } from '@/stores/auth'
import { IconVoice } from '@arco-design/web-vue/es/icon'

const rtcStore = useRtcStore()
const wsStore = useWebSocketStore()
const authStore = useAuthStore()
const focusedUserId = ref<number | null>(null)

function avatarColor(uid: number): string {
  return '#' + ((uid * 2654435761) >>> 0).toString(16).slice(0, 6)
}

function toggleFocus(uid: number) {
  focusedUserId.value = focusedUserId.value === uid ? null : uid
}

function hasVideo(uid: number): boolean {
  if (uid === authStore.user?.id) return (rtcStore.videoEnabled || rtcStore.screenSharing) && (rtcStore.localStream?.getVideoTracks().length ?? 0) > 0
  return (rtcStore.remotePeers[uid]?.stream?.getVideoTracks().length ?? 0) > 0
}
function latencyColor(ms: number): string {
  if (ms < 0) return 'var(--color-text-3)'
  if (ms < 100) return 'rgb(var(--green-6))'
  if (ms <= 250) return 'rgb(var(--orange-6))'
  return 'rgb(var(--red-6))'
}
function peerLatency(uid: number): number {
  const direct = rtcStore.remotePeers[uid]?.latency
  if (direct !== undefined && direct >= 0) return direct
  const relay = rtcStore.relayLatencies[uid]
  if (relay !== undefined && relay >= 0) return relay
  return -1
}
function getStream(uid: number): MediaStream | null {
  if (uid === authStore.user?.id) return (rtcStore.videoEnabled || rtcStore.screenSharing) ? rtcStore.localStream : null
  const s = rtcStore.remotePeers[uid]?.stream
  return s?.getVideoTracks().length ? s : null
}
</script>

<template>
  <div class="voice-call-view" @click.self="focusedUserId = null">
    <!-- Focused large video -->
    <div v-if="focusedUserId !== null && getStream(focusedUserId)" class="vc-focused">
      <video autoplay playsinline :muted="focusedUserId === authStore.user?.id"
             :srcObject="getStream(focusedUserId)" />
      <div class="vc-focused-name">
        {{ rtcStore.getVoiceUsers(rtcStore.activeRoomId || 0).find(u => u.userId === focusedUserId)?.username }}
      </div>
      <a-button class="vc-close-focus" type="text" @click="focusedUserId = null">✕</a-button>
    </div>

    <!-- Header -->
    <div v-if="focusedUserId === null || !getStream(focusedUserId)" class="vc-header">
      <IconVoice class="vc-header-icon" />
      <span>Voice Connected</span>
    </div>

    <!-- Cards grid -->
    <div v-if="focusedUserId === null" class="vc-participants">
      <div v-for="u in rtcStore.getVoiceUsers(rtcStore.activeRoomId || 0)" :key="u.userId"
           class="vc-card" :class="{ 'has-video': hasVideo(u.userId) }" @dblclick="hasVideo(u.userId) && toggleFocus(u.userId)">
        <div class="vc-latency"
             :style="{ color: u.userId === authStore.user?.id ? latencyColor(wsStore.serverLatency) : latencyColor(peerLatency(u.userId)) }">
          <template v-if="u.userId === authStore.user?.id">
            {{ wsStore.serverLatency >= 0 ? wsStore.serverLatency + 'ms' : '--' }}
          </template>
          <template v-else>
            {{ peerLatency(u.userId) >= 0 ? peerLatency(u.userId) + 'ms' : '--' }}
          </template>
        </div>
        <!-- Self video/screen preview -->
        <div v-if="u.userId === authStore.user?.id && (rtcStore.videoEnabled || rtcStore.screenSharing) && rtcStore.localStream?.getVideoTracks().length"
             class="vc-video-preview">
          <video autoplay muted playsinline :srcObject="rtcStore.localStream" :class="{ mirrored: !rtcStore.screenSharing }" />
        </div>
        <!-- Remote video preview -->
        <div v-else-if="u.userId !== authStore.user?.id && rtcStore.remotePeers[u.userId]?.stream?.getVideoTracks().length"
             class="vc-video-preview">
          <video autoplay playsinline :srcObject="rtcStore.remotePeers[u.userId].stream" />
        </div>
        <div v-else class="vc-avatar"
             :class="{ speaking: u.userId === authStore.user?.id ? rtcStore.speaking : rtcStore.remoteSpeaking[u.userId] }"
             :style="{ background: avatarColor(u.userId) }">
          {{ u.username?.charAt(0).toUpperCase() }}
        </div>
        <div class="vc-name">{{ u.username }}{{ u.userId === authStore.user?.id ? ' (You)' : '' }}</div>
        <div class="vc-badges">
          <span v-if="u.userId === rtcStore.hostId" class="vc-badge host">👑 Host</span>
        </div>
        <div v-if="rtcStore.remotePeers[u.userId]" class="vc-status">
          <span class="vc-dot" :style="{ background: connStateColor(rtcStore.remotePeers[u.userId].iceState) }" />
          {{ connStateLabel(rtcStore.remotePeers[u.userId].iceState) }}
        </div>
        <div v-else-if="u.userId !== authStore.user?.id" class="vc-status">
          <span class="vc-dot" style="background:#888" /> Waiting...
        </div>
        <div class="vc-quality">⭐ {{ u.quality?.toFixed(1) ?? '?' }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.voice-call-view {
  flex: 1; display: flex; flex-direction: column;
  background: transparent; position: relative;
}
.vc-header {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; color: rgb(var(--green-6)); font-weight: 600;
  padding: 24px 24px 20px; border-bottom: 1px solid var(--color-border-2);
}
.vc-header-icon { font-size: 18px; }
.vc-participants {
  flex: 1; display: flex; flex-wrap: wrap; gap: 16px;
  padding: 24px; align-content: flex-start;
}
.vc-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  width: 150px; padding: 12px 8px; border-radius: 8px;
  background: var(--color-bg-1); gap: 6px; cursor: pointer; position: relative;
  aspect-ratio: 1 / 1.59;
}
.vc-card:hover { background: var(--color-bg-3); }
.vc-latency {
  position: absolute; top: 4px; right: 6px;
  font-size: 10px; color: var(--color-text-3); font-family: monospace;
}
.vc-video-preview {
  width: 100%; height: 90px; border-radius: 6px; overflow: hidden;
  margin-bottom: 4px; background: var(--color-bg-3);
}
.vc-video-preview video {
  width: 100%; height: 100%; object-fit: cover;
}
.vc-video-preview video.mirrored { transform: scaleX(-1); }
.vc-avatar {
  width: 56px; height: 56px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 600; color: #fff;
}
.vc-avatar.speaking { box-shadow: 0 0 0 3px rgb(var(--green-6)); }
.vc-name { font-size: 13px; color: var(--color-text-1); font-weight: 600; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.vc-badges { display: flex; gap: 4px; }
.vc-badge { font-size: 10px; padding: 1px 6px; border-radius: 4px; }
.vc-badge.host { background: rgba(var(--orange-6), 0.2); color: rgb(var(--orange-6)); }
.vc-status { font-size: 11px; color: var(--color-text-3); display: flex; align-items: center; gap: 4px; }
.vc-dot { width: 6px; height: 6px; border-radius: 50%; }
.vc-quality { font-size: 11px; color: rgb(var(--orange-6)); }

.vc-focused {
  flex: 1; display: flex; align-items: center; justify-content: center;
  background: #000; position: relative;
}
.vc-focused video {
  max-width: 100%; max-height: 100%; border-radius: 4px;
}
.vc-focused video.mirrored { transform: scaleX(-1); }
.vc-focused-name {
  position: absolute; bottom: 16px; left: 16px;
  font-size: 14px; color: #fff; background: rgba(0,0,0,.6);
  padding: 4px 12px; border-radius: 4px;
}
.vc-close-focus {
  position: absolute; top: 16px; right: 16px; color: #fff !important;
  font-size: 20px; background: rgba(0,0,0,.5) !important;
}
</style>
