<script setup lang="ts">
import { useRtcStore, connStateLabel, connStateColor } from '@/stores/rtc'
import { useAuthStore } from '@/stores/auth'
import { IconVoice } from '@arco-design/web-vue/es/icon'

const rtcStore = useRtcStore()
const authStore = useAuthStore()

function avatarColor(uid: number): string {
  return '#' + ((uid * 2654435761) >>> 0).toString(16).slice(0, 6)
}
</script>

<template>
  <div class="voice-call-view">
    <div class="vc-header">
      <IconVoice class="vc-header-icon" />
      <span>Voice Connected</span>
    </div>

    <div class="vc-participants">
      <div v-for="u in rtcStore.voiceUsers" :key="u.userId" class="vc-card">
        <div class="vc-avatar"
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
  background: #313338; padding: 24px;
}
.vc-header {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; color: #22c55e; font-weight: 600;
  padding-bottom: 20px; border-bottom: 1px solid #3f4147;
}
.vc-header-icon { font-size: 18px; }
.vc-participants {
  flex: 1; display: flex; flex-wrap: wrap; gap: 16px;
  padding: 24px 0; align-content: flex-start;
}
.vc-card {
  display: flex; flex-direction: column; align-items: center;
  width: 130px; padding: 16px 8px; border-radius: 8px;
  background: #2b2d31; gap: 6px;
}
.vc-card:hover { background: #35373c; }
.vc-avatar {
  width: 56px; height: 56px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 600; color: #fff;
}
.vc-avatar.speaking { box-shadow: 0 0 0 3px #22c55e; }
.vc-name { font-size: 13px; color: #dbdee1; font-weight: 600; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.vc-badges { display: flex; gap: 4px; }
.vc-badge { font-size: 10px; padding: 1px 6px; border-radius: 4px; }
.vc-badge.host { background: rgba(251,191,36,.2); color: #fbbf24; }
.vc-status { font-size: 11px; color: #949ba4; display: flex; align-items: center; gap: 4px; }
.vc-dot { width: 6px; height: 6px; border-radius: 50%; }
.vc-quality { font-size: 11px; color: #fbbf24; }
</style>
