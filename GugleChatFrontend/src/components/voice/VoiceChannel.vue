<script setup lang="ts">
import { onUnmounted } from 'vue'
import { useRtcStore } from '@/stores/rtc'
import { useWebSocketStore } from '@/stores/websocket'
import { useAuthStore } from '@/stores/auth'

const rtcStore = useRtcStore()
const wsStore = useWebSocketStore()
const authStore = useAuthStore()
// Unused: import { useChannelStore } from '@/stores/channel'; const channelStore = useChannelStore()

// Wire up RTC signaling
rtcStore.setSendSignaling((dest, payload) => wsStore.sendSignaling(dest, payload))

wsStore.onRtcMessage(async (body: Record<string, unknown>) => {
  const type = body.type as string
  const myId = authStore.user?.id || 0
  if (type === 'room-users') {
    // New user joined: set hostId, then connect to host only (or everyone if I'm host)
    if (body.hostId) rtcStore.hostId = body.hostId as number
    const users = (body.users as number[]) || []
    const host = body.hostId as number
    if (host === myId) {
      // I'm the host: send offers to everyone already in the room
      for (const uid of users) {
        await createOffer(uid, 'User' + uid)
      }
    } else if (host && host !== myId) {
      // I'm not the host: only connect to the host
      await createOffer(host, 'Host')
    }
  } else if (type === 'user-joined') {
    const uid = body.userId as number
    const uname = (body.username as string) || 'User' + uid
    if (body.hostId) rtcStore.hostId = body.hostId as number
    // Only host sends offers to new users
    if (body.hostId === myId) {
      await createOffer(uid, uname)
    }
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
  // Host: when receiving a track, forward to all other peers
  const myId = authStore.user?.id || 0
  pc.addEventListener('track', (event: RTCTrackEvent) => {
    if (rtcStore.hostId !== myId) return
    const stream = event.streams[0]
    if (!stream) return
    stream.getTracks().forEach(track => {
      Object.entries(rtcStore.remotePeers).forEach(([uid, p]) => {
        if (Number(uid) !== targetId && p.pc.connectionState === 'connected') {
          const sender = p.pc.getSenders().find(s => s.track?.kind === track.kind)
          if (sender) sender.replaceTrack(track)
          else p.pc.addTrack(track, stream)
        }
      })
    })
  })
  const offer = await pc.createOffer({ offerToReceiveAudio: true, offerToReceiveVideo: true })
  await pc.setLocalDescription(offer)
  const myName = authStore.user?.username || 'Me'
  wsStore.sendSignaling('rtc.offer', { target: targetId, sdp: offer, username: myName })
}

async function handleOffer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const senderName = (body.username as string) || 'User' + senderId
  try {
    const pc = rtcStore.createPeerConnection(senderId, senderName)
    await pc.setRemoteDescription(new RTCSessionDescription(body.sdp as RTCSessionDescriptionInit))
    // Flush buffered ICE after setting remote description
    const peer = rtcStore.remotePeers[senderId]
    if (peer) {
      for (const c of peer.iceBuffer) {
        await pc.addIceCandidate(new RTCIceCandidate(c))
      }
      peer.iceBuffer = []
    }
    const answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    const myName = authStore.user?.username || 'Me'
    wsStore.sendSignaling('rtc.answer', { target: senderId, sdp: answer, username: myName })
  } catch (e: any) {
    if (e.name === 'InvalidStateError') return // ignore state errors
    throw e
  }
}

async function handleAnswer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const peer = rtcStore.remotePeers[senderId]
  if (!peer) return
  try {
    await peer.pc.setRemoteDescription(new RTCSessionDescription(body.sdp as RTCSessionDescriptionInit))
  } catch (e) {
    // Ignore if already in stable state (duplicate answer)
    if (peer.pc.signalingState !== 'stable') throw e
  }
  // Flush buffered ICE candidates
  for (const c of peer.iceBuffer) {
    await peer.pc.addIceCandidate(new RTCIceCandidate(c))
  }
  peer.iceBuffer = []
}

async function handleIceCandidate(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const peer = rtcStore.remotePeers[senderId]
  if (!peer || !body.candidate) return
  const candidate = body.candidate as RTCIceCandidateInit
  // If remote description not set yet, buffer the candidate
  if (!peer.pc.remoteDescription || !peer.pc.remoteDescription.type) {
    peer.iceBuffer.push(candidate)
  } else {
    await peer.pc.addIceCandidate(new RTCIceCandidate(candidate))
  }
}

onUnmounted(() => {
  if (rtcStore.activeRoomId) rtcStore.endCall()
})
</script>

<template>
  <div v-if="false" />
</template>

