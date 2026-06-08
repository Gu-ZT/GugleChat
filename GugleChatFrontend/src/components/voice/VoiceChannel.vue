<script setup lang="ts">
import { onUnmounted } from 'vue'
import { useRtcStore } from '@/stores/rtc'
import { useWebSocketStore } from '@/stores/websocket'
import { useAuthStore } from '@/stores/auth'
import { IconNotification, IconClose, IconVideoCamera, IconPhone } from '@arco-design/web-vue/es/icon'

const rtcStore = useRtcStore()
const wsStore = useWebSocketStore()
const authStore = useAuthStore()
// Unused: import { useChannelStore } from '@/stores/channel'; const channelStore = useChannelStore()

// Wire up RTC signaling
rtcStore.setSendSignaling((dest, payload) => wsStore.sendSignaling(dest, payload))

wsStore.onRtcMessage(async (body: Record<string, unknown>) => {
  const type = body.type as string
  if (type === 'room-users') {
    // New user: just note existing users, WAIT for them to send offers
    // (user-joined already notifies them to send offers to us)
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
  const myId = authStore.user?.id || 0
  const myName = authStore.user?.username || 'Me'
  wsStore.sendSignaling('rtc.offer', { target: targetId, sdp: offer, username: myName })
}

async function handleOffer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const senderName = (body.username as string) || 'User' + senderId
  // Create bare PC — don't add tracks yet, let setRemoteDescription determine m-lines
  const pc = new RTCPeerConnection({ iceServers: rtcStore.getIceServers?.() || [{ urls: 'stun:stun.l.google.com:19302' }] })
  rtcStore.addRemotePeer(senderId, senderName, pc)
  pc.onicecandidate = (event) => {
    if (event.candidate) {
      wsStore.sendSignaling('rtc.ice-candidate', { target: senderId, candidate: event.candidate })
    }
  }
  pc.ontrack = (event) => { rtcStore.setRemoteStream(senderId, event.streams[0]) }

  // Set remote description first to match offer's m-line order
  await pc.setRemoteDescription(new RTCSessionDescription(body.sdp as RTCSessionDescriptionInit))
  // Now add local tracks — they'll be matched to existing transceivers
  if (rtcStore.localStream) {
    rtcStore.localStream.getTracks().forEach(track => pc.addTrack(track, rtcStore.localStream!))
  }
  const answer = await pc.createAnswer()
  await pc.setLocalDescription(answer)
  const myName = authStore.user?.username || 'Me'
  wsStore.sendSignaling('rtc.answer', { target: senderId, sdp: answer, username: myName })
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
    <div class="vp-left">
      <IconNotification class="vp-signal" />
      <span class="vp-text">Voice Connected</span>
      <span class="vp-divider">·</span>
      <span class="vp-channel">Voice</span>
    </div>
    <div class="vp-center">
      <!-- Self -->
      <div class="vp-user">
        <div class="vp-avatar" :class="{ speaking: rtcStore.audioEnabled }">
          {{ authStore.user?.username?.charAt(0).toUpperCase() }}
        </div>
      </div>
      <!-- Remote peers -->
      <div class="vp-user" v-for="(peer, uid) in rtcStore.remotePeers" :key="uid">
        <div class="vp-avatar">{{ peer.username?.charAt(0).toUpperCase() }}</div>
        <div v-if="peer.stream" class="vp-mini-video">
          <video autoplay playsinline :srcObject="peer.stream" />
        </div>
      </div>
    </div>
    <div class="vp-right">
      <a-button type="text" size="mini"
                :status="!rtcStore.audioEnabled ? 'danger' : undefined"
                @click="rtcStore.toggleAudio">
        <template #icon><IconNotification v-if="rtcStore.audioEnabled" /><IconClose v-else /></template>
      </a-button>
      <a-button type="text" size="mini"
                :class="{ active: rtcStore.videoEnabled }"
                @click="rtcStore.toggleVideo">
        <template #icon><IconVideoCamera /></template>
      </a-button>
      <a-button type="text" size="mini" status="danger" @click="rtcStore.endCall">
        <template #icon><IconPhone /></template>
      </a-button>
    </div>
  </div>
</template>

<style scoped>
.voice-panel {
  position: fixed; bottom: 0; left: 240px; right: 0; z-index: 500;
  background: #232428; border-bottom: 3px solid #22c55e;
  padding: 8px 16px;
  display: flex; align-items: center; gap: 16px;
  height: 48px; box-shadow: 0 -1px 0 #1e1f22;
}
.vp-left { display: flex; align-items: center; gap: 6px; min-width: 200px; }
.vp-signal { color: #22c55e; font-size: 16px; }
.vp-text { color: #22c55e; font-size: 13px; font-weight: 600; }
.vp-divider { color: #4e5058; font-size: 16px; font-weight: 700; }
.vp-channel { color: #dbdee1; font-size: 13px; }

.vp-center { display: flex; align-items: center; gap: 8px; flex: 1; }
.vp-user { position: relative; }
.vp-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  background: #313338; display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; color: #dbdee1;
}
.vp-avatar.speaking { box-shadow: 0 0 0 2px #22c55e; }
.vp-mini-video {
  position: absolute; top: -60px; left: -4px;
  width: 80px; height: 56px; border-radius: 4px; overflow: hidden;
}
.vp-mini-video video { width: 100%; height: 100%; object-fit: cover; }

.vp-right { display: flex; gap: 2px; }
.vp-right :deep(.arco-btn-text) { color: #b5bac1; }
.vp-right :deep(.arco-btn-text:hover) { color: #dbdee1; background: #35373c; }
.vp-right :deep(.arco-btn-text.active) { color: #22c55e; }
</style>
