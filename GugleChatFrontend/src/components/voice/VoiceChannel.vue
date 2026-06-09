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
rtcStore.setSendSignaling((dest: string, payload: Record<string, unknown>) => wsStore.sendSignaling(dest, payload))

// Per-peer audio mixer — each peer gets host mic + all OTHER remote audio (excludes own)
let mixCtx: AudioContext | null = null
type MixEntry = { dest: MediaStreamAudioDestinationNode; sourceNodes: Set<AudioNode> }
const peerMixes = new Map<number, MixEntry>()
const trackSources = new Map<string, { node: AudioNode; sourceUid: number }>() // track.id -> source info

function ensureMixer() {
  if (mixCtx) return
  mixCtx = new AudioContext()
  // Create/update per-peer destinations
  const peers = rtcStore.remotePeers as Record<number, { pc: RTCPeerConnection }>
  for (const uid of Object.keys(peers)) {
    ensurePeerMix(Number(uid))
  }
}

function ensurePeerMix(uid: number) {
  if (!mixCtx || peerMixes.has(uid)) return
  const dest = mixCtx.createMediaStreamDestination()
  const entry: MixEntry = { dest, sourceNodes: new Set() }
  peerMixes.set(uid, entry)
  // Connect host mic to this peer's mix
  if (rtcStore.localStream) {
    const hostSrc = mixCtx.createMediaStreamSource(rtcStore.localStream)
    hostSrc.connect(dest)
    entry.sourceNodes.add(hostSrc)
  }
  // Connect all existing remote sources (except this peer's own)
  for (const [trackId, info] of trackSources) {
    if (info.sourceUid !== uid) {
      const gain = mixCtx.createGain()
      info.node.connect(gain).connect(dest)
      entry.sourceNodes.add(gain)
    }
  }
  // Apply this peer's mixed track to its PC
  applyMixedTrack(uid)
}

function applyMixedTrack(uid: number) {
  const entry = peerMixes.get(uid)
  if (!entry) return
  const mixedTrack = entry.dest.stream.getAudioTracks()[0]
  const peers = rtcStore.remotePeers as Record<number, { pc: RTCPeerConnection }>
  const sender = peers[uid]?.pc.getSenders().find(s => s.track?.kind === 'audio')
  if (sender) sender.replaceTrack(mixedTrack)
}

function addRemoteSource(uid: number, track: MediaStreamTrack) {
  if (!mixCtx || trackSources.has(track.id)) return
  const source = mixCtx.createMediaStreamSource(new MediaStream([track]))
  trackSources.set(track.id, { node: source, sourceUid: uid })
  // Connect this source to all peer mixes EXCEPT the source owner
  for (const [peerUid, entry] of peerMixes) {
    if (peerUid === uid) continue
    const gain = mixCtx.createGain()
    source.connect(gain).connect(entry.dest)
    entry.sourceNodes.add(gain)
  }
  track.onended = () => removeRemoteSource(track.id)
}

function removeRemoteSource(trackId: string) {
  const info = trackSources.get(trackId)
  if (!info) return
  trackSources.delete(trackId)
  // Disconnect from all peer mixes
  for (const [, entry] of peerMixes) {
    entry.sourceNodes.forEach(n => {
      if (n.context === mixCtx) n.disconnect()
    })
  }
}

function removePeerMix(uid: number) {
  const entry = peerMixes.get(uid)
  if (!entry) return
  entry.sourceNodes.forEach(n => n.disconnect())
  peerMixes.delete(uid)
}

wsStore.onRtcMessage(async (body: Record<string, unknown>) => {
  const type = body.type as string
  const myId = authStore.user?.id || 0
  if (type === 'room-users') {
    if (body.hostId) rtcStore.hostId = body.hostId as number
    rtcStore.forcedHostId = (body.forcedHostId as number) || 0
    const users = (body.users as { userId: number; username: string }[]) || []
    const host = body.hostId as number
    if (host === myId) {
      for (const u of users) {
        await createOffer(u.userId, u.username)
      }
    }
    // Non-host waits for host's offer (avoids glare)
  } else if (type === 'user-joined') {
    const uid = body.userId as number
    const uname = (body.username as string) || 'User' + uid
    if (body.hostId) rtcStore.hostId = body.hostId as number
    // Only host sends offers to new users
    if (body.hostId === myId) {
      await createOffer(uid, uname)
    }
  } else if (type === 'user-left') {
    const leftUid = body.userId as number
    removePeerMix(leftUid)
    rtcStore.removeRemotePeer(leftUid)
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
  ;(pc as any)._gugleInitPing?.()
  const myId = authStore.user?.id || 0
  // Track event: add remote audio to per-peer mixers
  pc.addEventListener('track', (event: RTCTrackEvent) => {
    if (rtcStore.hostId !== myId) return
    const stream = event.streams[0]
    if (!stream) return
    ensureMixer()
    ensurePeerMix(targetId) // ensure this peer has a mix
    for (const track of stream.getTracks()) {
      addRemoteSource(targetId, track)
    }
  })
  // If mixer already active, create mix for this new peer
  if (mixCtx) {
    ensurePeerMix(targetId)
  }
  const offer = await pc.createOffer({ offerToReceiveAudio: true, offerToReceiveVideo: true })
  await pc.setLocalDescription(offer)
  const myName = authStore.user?.username || 'Me'
  wsStore.sendSignaling('rtc.offer', { target: targetId, sdp: offer, username: myName })
}

async function handleOffer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const senderName = (body.username as string) || 'User' + senderId
  try {
    // Reuse existing PC for renegotiation, or create new one
    let pc = rtcStore.remotePeers[senderId]?.pc
    if (!pc || pc.signalingState === 'closed') {
      pc = rtcStore.createPeerConnection(senderId, senderName)
    }
    await pc.setRemoteDescription(new RTCSessionDescription(body.sdp as RTCSessionDescriptionInit))
    // Flush buffered ICE
    const peer = rtcStore.remotePeers[senderId]
    if (peer) {
      for (const c of peer.iceBuffer) {
        await pc.addIceCandidate(new RTCIceCandidate(c))
      }
      peer.iceBuffer = []
    }
    if (body.sdp && (body.sdp as RTCSessionDescriptionInit).type === 'offer') {
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      const myName = authStore.user?.username || 'Me'
      wsStore.sendSignaling('rtc.answer', { target: senderId, sdp: answer, username: myName })
    }
  } catch (e: any) {
    if (e.name === 'InvalidStateError') return
    throw e
  }
}

async function handleAnswer(body: Record<string, unknown>) {
  const senderId = body.userId as number
  const peer = rtcStore.remotePeers[senderId]
  if (!peer) return
  try {
    await peer.pc.setRemoteDescription(new RTCSessionDescription(body.sdp as RTCSessionDescriptionInit))
  } catch (e: any) {
    if (e.name === 'InvalidStateError') return
    throw e
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

function forceHost() {
  wsStore.sendSignaling('rtc.force-host', {})
}

onUnmounted(() => {
  cleanupMixer()
  if (rtcStore.activeRoomId) rtcStore.endCall()
})

function cleanupMixer() {
  trackSources.clear()
  peerMixes.forEach(entry => entry.sourceNodes.forEach(n => n.disconnect()))
  peerMixes.clear()
  if (mixCtx) { mixCtx.close(); mixCtx = null }
}
</script>

<template>
  <div v-if="false" />
</template>

