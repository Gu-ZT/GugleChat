<script setup lang="ts">
import { onUnmounted, watch } from 'vue'
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

// Video forwarding: host relays video from one peer to all others
// Key: "fromUserId→toUserId", Value: { sender, track, stream }
const videoForwardSenders = new Map<string, { sender: RTCRtpSender; track: MediaStreamTrack; stream: MediaStream }>()

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
}

function applyMixedTrack(uid: number) {
  const entry = peerMixes.get(uid)
  if (!entry) return
  const mixedTrack = entry.dest.stream.getAudioTracks()[0]
  const peers = rtcStore.remotePeers as Record<number, { pc: RTCPeerConnection }>
  const p = peers[uid]
  if (!p || p.pc.connectionState !== 'connected') return
  const sender = p.pc.getSenders().find(s => s.track?.kind === 'audio')
  if (sender && sender.track !== mixedTrack) {
    sender.replaceTrack(mixedTrack)
    console.log('[mix] replaced track for peer', uid)
  }
}

function applyAllMixedTracks() {
  const peers = rtcStore.remotePeers as Record<number, { pc: RTCPeerConnection }>
  for (const uid of Object.keys(peers)) {
    applyMixedTrack(Number(uid))
  }
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
  // Disconnect only the gain nodes that belong to this specific track
  // We need to find which gain nodes were created for this source.
  // Since we can't directly trace back, we rebuild each peer's mix without this source.
  // The simplest correct fix: remove nodes whose sourceUid matches info.sourceUid
  // and whose context matches mixCtx — but we don't have per-track node refs.
  // Instead, disconnect the source node itself (all its outputs), then reconnect remaining.
  info.node.disconnect()
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
    if (body.hostId) rtcStore.setHostId(body.hostId as number)
    rtcStore.setForcedHostId((body.forcedHostId as number) || 0)
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
    if (body.hostId) rtcStore.setHostId(body.hostId as number)
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
  // Host creates connections — mark as isHostConnection so ontrack skips setRemoteStream fallback
  const pc = rtcStore.createPeerConnection(targetId, username, true)
  ;(pc as any)._gugleInitPing?.()
  const myId = authStore.user?.id || 0
  // Auto-apply mixed track when connection becomes stable
  pc.addEventListener('connectionstatechange', async () => {
    if (rtcStore.hostId !== myId) return
    if (pc.connectionState === 'connected') {
      if (mixCtx) {
        ensurePeerMix(targetId)
        applyMixedTrack(targetId)
      }
      // Bug 3 fix: replay existing video forwards to newly joined peer
      const peer = rtcStore.remotePeers[targetId] as { pc: RTCPeerConnection; pingChannel: RTCDataChannel | null } | undefined
      if (!peer) return
      const existingForwards: { fromUserId: number; track: MediaStreamTrack }[] = []
      for (const [key, val] of videoForwardSenders) {
        // key format: "fromUserId→toUserId" — find all forwards not yet targeting this new peer
        const [fromStr] = key.split('→')
        const fromId = Number(fromStr)
        if (fromId !== targetId) {
          existingForwards.push({ fromUserId: fromId, track: val.track })
        }
      }
      for (const { fromUserId, track } of existingForwards) {
        const fwdKey = `${fromUserId}→${targetId}`
        if (videoForwardSenders.has(fwdKey)) continue // already forwarding to this peer
        const fwdStream = new MediaStream([track])
        const sender = peer.pc.addTrack(track, fwdStream)
        videoForwardSenders.set(fwdKey, { sender, track, stream: fwdStream })
        // Renegotiate
        try {
          const offer = await peer.pc.createOffer()
          await peer.pc.setLocalDescription(offer)
          wsStore.sendSignaling('rtc.offer', { target: targetId, sdp: offer, username: authStore.user?.username || 'Me' })
        } catch (e: any) {
          if (e.name !== 'InvalidStateError') console.warn('[fwd-replay] renegotiation failed:', e.message)
        }
        // Send video-fwd mapping — wait for DataChannel to be open
        const sendFwd = () => {
          if (peer.pingChannel?.readyState === 'open') {
            peer.pingChannel.send(JSON.stringify({ type: 'video-fwd', fromUserId, streamId: fwdStream.id }))
          }
        }
        if (peer.pingChannel?.readyState === 'open') {
          sendFwd()
        } else if (peer.pingChannel) {
          peer.pingChannel.addEventListener('open', sendFwd, { once: true })
        }
      }
    }
  })
  // Track event: mix remote audio, forward remote video
  pc.addEventListener('track', async (event: RTCTrackEvent) => {
    if (rtcStore.hostId !== myId) return
    const stream = event.streams[0]
    if (!stream) return
    ensureMixer()
    ensurePeerMix(targetId)
    for (const track of stream.getTracks()) {
      if (track.kind === 'audio') {
        addRemoteSource(targetId, track)
      } else if (track.kind === 'video') {
        // Forward this video to all other peers
        await forwardVideoToOthers(targetId, track)
      }
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

/** Host: forward a video track from one peer to all other peers */
async function forwardVideoToOthers(fromUserId: number, videoTrack: MediaStreamTrack) {
  const peers = rtcStore.remotePeers as Record<number, { pc: RTCPeerConnection; pingChannel: RTCDataChannel | null }>
  for (const [targetIdStr, peer] of Object.entries(peers)) {
    const targetId = Number(targetIdStr)
    if (targetId === fromUserId) continue // Don't send back to source
    const key = `${fromUserId}→${targetId}`
    const existing = videoForwardSenders.get(key)

    let fwdStream: MediaStream
    if (existing) {
      // Reuse the SAME stream instance — stream.id must stay stable so the remote
      // end can re-pair via the video-fwd DataChannel message.
      fwdStream = existing.stream
      existing.sender.replaceTrack(videoTrack)
      videoForwardSenders.set(key, { sender: existing.sender, track: videoTrack, stream: fwdStream })
      // replaceTrack doesn't trigger ontrack on the remote end, so we only need to
      // renegotiate if the codec params changed — skip renegotiation here and just
      // re-send the video-fwd message so the remote re-pairs with the same streamId.
    } else {
      fwdStream = new MediaStream([videoTrack])
      const sender = peer.pc.addTrack(videoTrack, fwdStream)
      videoForwardSenders.set(key, { sender, track: videoTrack, stream: fwdStream })
      // First-time: renegotiate so the remote side gets an ontrack event
      try {
        const offer = await peer.pc.createOffer()
        await peer.pc.setLocalDescription(offer)
        wsStore.sendSignaling('rtc.offer', { target: targetId, sdp: offer, username: authStore.user?.username || 'Me' })
      } catch (e: any) {
        if (e.name !== 'InvalidStateError') console.warn('[fwd] renegotiation failed:', e.message)
      }
    }

    // Always send video-fwd so the remote end (re-)pairs streamId → fromUserId
    if (peer.pingChannel && peer.pingChannel.readyState === 'open') {
      peer.pingChannel.send(JSON.stringify({
        type: 'video-fwd',
        fromUserId,
        streamId: fwdStream.id,
      }))
    }

    // Cleanup when source track ends
    videoTrack.addEventListener('ended', () => {
      const s = videoForwardSenders.get(key)
      if (s) {
        s.sender.replaceTrack(null)
        videoForwardSenders.delete(key)
        if (peer.pingChannel && peer.pingChannel.readyState === 'open') {
          peer.pingChannel.send(JSON.stringify({ type: 'video-fwd-end', fromUserId }))
        }
      }
    }, { once: true })
  }
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

// Handle host migration: recreate connections when becoming host, cleanup when losing host
watch(() => rtcStore.hostId, async (newHost) => {
  const myId = authStore.user?.id
  if (newHost === myId) {
    // I became the new host — close stale peer connections and create offers to everyone
    rtcStore.clearAllPeers()
    cleanupMixer()
    const roomId = rtcStore.activeRoomId
    if (roomId) {
      const users = rtcStore.getVoiceUsers(roomId)
      for (const u of users) {
        if (u.userId !== myId) {
          await createOffer(u.userId, u.username)
        }
      }
    }
  } else if (newHost !== myId) {
    cleanupMixer()
  }
})

onUnmounted(() => {
  cleanupMixer()
  if (rtcStore.activeRoomId) rtcStore.endCall()
})

function cleanupMixer() {
  // Clean up video forwarders
  videoForwardSenders.forEach(s => s.sender.replaceTrack(null))
  videoForwardSenders.clear()
  trackSources.clear()
  peerMixes.forEach(entry => entry.sourceNodes.forEach(n => n.disconnect()))
  peerMixes.clear()
  if (mixCtx) { mixCtx.close(); mixCtx = null }
}
</script>

<template>
  <div v-if="false" />
</template>

