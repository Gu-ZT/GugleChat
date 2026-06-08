<script setup lang="ts">
import { onUnmounted } from 'vue'
import { useRtcStore } from '@/stores/rtc'
import { useWebSocketStore } from '@/stores/websocket'
import { useAuthStore } from '@/stores/auth'
import { IconNotification, IconClose, IconVideoCamera, IconPhone, IconUser } from '@arco-design/web-vue/es/icon'

const rtcStore = useRtcStore()
const wsStore = useWebSocketStore()
const authStore = useAuthStore()

// Wire up RTC signaling
rtcStore.setSendSignaling((dest, payload) => wsStore.sendSignaling(dest, payload))

wsStore.onRtcMessage(async (body: Record<string, unknown>) => {
  const type = body.type as string
  if (type === 'room-users') {
    const users = (body.users as number[]) || []
    // Send offer to each existing user, but only if we have their username
    for (const uid of users) {
      await createOffer(uid, 'User' + uid)
    }
  } else if (type === 'user-joined') {
    const uid = body.userId as number
    const uname = (body.username as string) || 'User' + uid
    await createOffer(uid, uname)
  } else if (type === 'user-left') {
    rtcStore.removeRemotePeer(body.userId as number)
  } else if (type === 'offer') {
    await handleOffer(body)
  } else if (type === 'answer') {
    await handleAnswer(body)
  } else if (type === 'ice-candidate') {
    await handleIceCandidate(body)
  }
})

async function createOffer(targetId: number, username: string) {
  const pc = rtcStore.createPeerConnection(targetId, username)
  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)
  wsStore.sendSignaling('rtc.offer', { target: targetId, sdp: offer })
}

async function handleOffer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const pc = rtcStore.createPeerConnection(senderId, 'User' + senderId)
  await pc.setRemoteDescription(new RTCSessionDescription(body.sdp as RTCSessionDescriptionInit))
  const answer = await pc.createAnswer()
  await pc.setLocalDescription(answer)
  wsStore.sendSignaling('rtc.answer', { target: senderId, sdp: answer })
}

async function handleAnswer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const peer = rtcStore.remotePeers[senderId]
  if (peer) {
    await peer.pc.setRemoteDescription(new RTCSessionDescription(body.sdp as RTCSessionDescriptionInit))
  }
}

async function handleIceCandidate(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const peer = rtcStore.remotePeers[senderId]
  if (peer && body.candidate) {
    await peer.pc.addIceCandidate(new RTCIceCandidate(body.candidate as RTCIceCandidateInit))
  }
}

onUnmounted(() => {
  if (rtcStore.activeRoomId) rtcStore.endCall()
})
</script>

<template>
  <div v-if="rtcStore.activeRoomId" class="voice-panel">
    <!-- Connected users list -->
    <div class="voice-users">
      <div class="voice-user" v-for="(peer, uid) in rtcStore.remotePeers" :key="uid">
        <IconUser class="user-icon" />
        <span>{{ peer.username }}</span>
        <div v-if="peer.stream" class="mini-video">
          <video autoplay playsinline :srcObject="peer.stream" />
        </div>
      </div>
      <!-- Self -->
      <div class="voice-user self">
        <IconUser class="user-icon" />
        <span>{{ authStore.user?.username }} (me)</span>
      </div>
    </div>

    <!-- Controls -->
    <div class="voice-controls">
      <a-button shape="circle" size="small"
                :type="rtcStore.audioEnabled ? 'primary' : 'outline'"
                :status="rtcStore.audioEnabled ? undefined : 'danger'"
                @click="rtcStore.toggleAudio">
        <template #icon><IconNotification v-if="rtcStore.audioEnabled" /><IconClose v-else /></template>
      </a-button>
      <a-button shape="circle" size="small"
                :type="rtcStore.videoEnabled ? 'primary' : 'outline'"
                @click="rtcStore.toggleVideo">
        <template #icon><IconVideoCamera /></template>
      </a-button>
      <a-button shape="circle" size="small" type="primary" status="danger" @click="rtcStore.endCall">
        <template #icon><IconPhone /></template>
      </a-button>
    </div>
  </div>
</template>

<style scoped>
.voice-panel {
  position: fixed; bottom: 0; left: 268px; z-index: 500;
  background: var(--color-bg-2); border-top: 2px solid #22c55e;
  border-right: 1px solid var(--color-border-2);
  border-radius: 0 8px 0 0;
  padding: 8px 12px;
  display: flex; gap: 12px; align-items: center;
  min-width: 280px; box-shadow: 0 -2px 12px rgba(0,0,0,0.3);
}
.voice-users { display: flex; gap: 8px; flex-wrap: wrap; }
.voice-user { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #22c55e; }
.voice-user .user-icon { font-size: 14px; }
.mini-video { width: 60px; height: 45px; border-radius: 4px; overflow: hidden; }
.mini-video video { width: 100%; height: 100%; object-fit: cover; }
.voice-controls { display: flex; gap: 6px; margin-left: auto; }
</style>
