<script setup lang="ts">
import { useRtcStore, connStateLabel, connStateColor } from '@/stores/rtc'
import { useAuthStore } from '@/stores/auth'
import { IconUser, IconVoice } from '@arco-design/web-vue/es/icon'

const rtcStore = useRtcStore()
const authStore = useAuthStore()
</script>

<template>
  <div class="voice-call-view">
    <div class="vc-header">
      <IconVoice class="vc-header-icon" />
      <span>Voice Connected</span>
    </div>

    <div class="vc-participants">
      <!-- Self -->
      <div class="vc-card self">
        <div class="vc-avatar" :class="{ speaking: rtcStore.speaking }">
          {{ authStore.user?.username?.charAt(0).toUpperCase() }}
        </div>
        <div class="vc-name">{{ authStore.user?.username }}</div>
        <div class="vc-status">You</div>
      </div>

      <!-- Remote peers -->
      <div v-for="(peer, uid) in rtcStore.remotePeers" :key="uid" class="vc-card">
        <div class="vc-avatar" :class="{ speaking: rtcStore.remoteSpeaking[uid] }"
             :style="{ background: '#' + ((uid * 2654435761) >>> 0).toString(16).slice(0, 6) }">
          {{ peer.username?.charAt(0).toUpperCase() }}
        </div>
        <div class="vc-name">{{ peer.username }}</div>
        <div class="vc-status">
          <span class="vc-dot" :style="{ background: connStateColor(peer.iceState) }" />
          {{ connStateLabel(peer.iceState) }}
        </div>
        <div class="vc-quality">
          ⭐ {{ peer.quality.toFixed(1) }}
        </div>
      </div>

      <!-- Waiting for others -->
      <div v-if="Object.keys(rtcStore.remotePeers).length === 0" class="vc-empty">
        <IconUser class="vc-empty-icon" />
        <p>Waiting for others to join...</p>
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
  width: 120px; padding: 16px 8px; border-radius: 8px;
  background: #2b2d31; gap: 6px;
}
.vc-card:hover { background: #35373c; }
.vc-avatar {
  width: 56px; height: 56px; border-radius: 50%;
  background: #5865f2; display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 600; color: #fff;
}
.vc-avatar.speaking { box-shadow: 0 0 0 3px #22c55e; }
.vc-name { font-size: 14px; color: #dbdee1; font-weight: 600; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.vc-status { font-size: 12px; color: #949ba4; display: flex; align-items: center; gap: 4px; }
.vc-dot { width: 6px; height: 6px; border-radius: 50%; background: #22c55e; }
.vc-quality { font-size: 11px; color: #fbbf24; margin-top: 2px; }
.vc-empty {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: #949ba4; gap: 12px;
}
.vc-empty-icon { font-size: 40px; opacity: 0.5; }
</style>
