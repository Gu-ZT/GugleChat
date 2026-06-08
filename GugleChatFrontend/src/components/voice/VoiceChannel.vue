<script setup lang="ts">
import { watch, onUnmounted } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { useRtcStore } from '@/stores/rtc'
import { useWebSocketStore } from '@/stores/websocket'

const channelStore = useChannelStore()
const rtcStore = useRtcStore()
const wsStore = useWebSocketStore()

// Wire up signaling
wsStore.onRtcMessage(async (body: Record<string, unknown>) => {
  const type = body.type as string

  if (type === 'room-users') {
    // New user joined, existing users send offers to them
    const users = (body.users as number[]) || []
    for (const uid of users) {
      await createOffer(uid)
    }
  } else if (type === 'user-joined') {
    // Someone new joined, send them an offer
    const uid = body.userId as number
    await createOffer(uid)
  } else if (type === 'user-left') {
    const uid = body.userId as number
    rtcStore.removeRemotePeer(uid)
  } else if (type === 'offer') {
    await handleOffer(body)
  } else if (type === 'answer') {
    await handleAnswer(body)
  } else if (type === 'ice-candidate') {
    await handleIceCandidate(body)
  }
})

async function createOffer(targetId: number) {
  const pc = rtcStore.createPeerConnection(targetId)
  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)
  wsStore.sendSignaling('rtc.offer', { target: targetId, sdp: offer })
}

async function handleOffer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const pc = rtcStore.createPeerConnection(senderId)
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

// Join voice channel when entering a VOICE channel
watch(() => channelStore.currentChannel, (ch) => {
  if (ch?.type === 'VOICE') {
    rtcStore.startCall(ch.id)
  } else {
    if (rtcStore.inCall) rtcStore.endCall()
  }
}, { immediate: true })

onUnmounted(() => {
  if (rtcStore.inCall) rtcStore.endCall()
})
</script>

<template>
  <div v-if="rtcStore.inCall" class="voice-overlay">
    <!-- Local video -->
    <div class="local-video">
      <video autoplay muted playsinline :srcObject="rtcStore.localStream" />
    </div>

    <!-- Remote videos -->
    <div v-for="peer in rtcStore.remotePeers" :key="peer.userId" class="remote-video">
      <video autoplay playsinline :srcObject="peer.stream" />
    </div>

    <!-- Controls -->
    <div class="controls">
      <a-button :type="rtcStore.audioEnabled ? 'primary' : 'outline'" shape="circle" @click="rtcStore.toggleAudio">
        {{ rtcStore.audioEnabled ? '🎤' : '🔇' }}
      </a-button>
      <a-button :type="rtcStore.videoEnabled ? 'primary' : 'outline'" shape="circle" @click="rtcStore.toggleVideo">
        {{ rtcStore.videoEnabled ? '📹' : '📷' }}
      </a-button>
      <a-button type="primary" status="danger" shape="circle" @click="rtcStore.endCall">
        📞
      </a-button>
    </div>
  </div>
</template>

<style scoped>
.voice-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.9); z-index: 1000;
  display: flex; flex-wrap: wrap; gap: 12px; padding: 16px;
  align-items: center; justify-content: center;
}
.local-video {
  position: absolute; top: 12px; right: 12px; width: 200px; z-index: 10;
  border-radius: 8px; overflow: hidden; border: 2px solid #80b4ff;
}
.local-video video, .remote-video video {
  width: 100%; display: block; border-radius: 8px;
}
.remote-video { width: 45%; max-width: 640px; }
.controls {
  position: absolute; bottom: 24px; display: flex; gap: 16px;
}
</style>
