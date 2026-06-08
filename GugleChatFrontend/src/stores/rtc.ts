import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface RemotePeer {
  userId: number
  stream: MediaStream | null
  pc: RTCPeerConnection
}

export const useRtcStore = defineStore('rtc', () => {
  const localStream = ref<MediaStream | null>(null)
  const remotePeers = ref<Record<number, RemotePeer>>({})
  const inCall = ref(false)
  const currentRoomId = ref<number | null>(null)
  const videoEnabled = ref(true)
  const audioEnabled = ref(true)

  const iceServers: RTCIceServer[] = [
    { urls: 'stun:stun.l.google.com:19302' },
  ]

  function setLocalStream(stream: MediaStream | null) {
    localStream.value = stream
  }

  function addRemotePeer(userId: number, pc: RTCPeerConnection) {
    remotePeers.value = { ...remotePeers.value, [userId]: { userId, stream: null, pc } }
  }

  function setRemoteStream(userId: number, stream: MediaStream | null) {
    const peer = remotePeers.value[userId]
    if (peer) {
      peer.stream = stream
      remotePeers.value = { ...remotePeers.value } // trigger reactivity
    }
  }

  function removeRemotePeer(userId: number) {
    const peer = remotePeers.value[userId]
    if (peer) {
      peer.pc.close()
      const next = { ...remotePeers.value }
      delete next[userId]
      remotePeers.value = next
    }
  }

  function createPeerConnection(userId: number): RTCPeerConnection {
    const pc = new RTCPeerConnection({ iceServers })
    addRemotePeer(userId, pc)

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignaling('rtc.ice-candidate', {
          target: userId,
          candidate: event.candidate,
        })
      }
    }

    pc.ontrack = (event) => {
      setRemoteStream(userId, event.streams[0])
    }

    // Add local tracks
    if (localStream.value) {
      localStream.value.getTracks().forEach(track => {
        pc.addTrack(track, localStream.value!)
      })
    }

    return pc
  }

  function toggleVideo() {
    if (localStream.value) {
      videoEnabled.value = !videoEnabled.value
      localStream.value.getVideoTracks().forEach(t => t.enabled = videoEnabled.value)
    }
  }

  function toggleAudio() {
    if (localStream.value) {
      audioEnabled.value = !audioEnabled.value
      localStream.value.getAudioTracks().forEach(t => t.enabled = audioEnabled.value)
    }
  }

  async function startCall(roomId: number) {
    currentRoomId.value = roomId
    inCall.value = true
    try {
      localStream.value = await navigator.mediaDevices.getUserMedia({
        video: true, audio: true,
      })
    } catch {
      localStream.value = await navigator.mediaDevices.getUserMedia({ audio: true })
    }
    sendSignaling('rtc.join', { roomId })
  }

  function endCall() {
    Object.values(remotePeers.value).forEach(p => p.pc.close())
    remotePeers.value = {}
    if (localStream.value) {
      localStream.value.getTracks().forEach(t => t.stop())
      localStream.value = null
    }
    if (currentRoomId.value) {
      sendSignaling('rtc.leave', { roomId: currentRoomId.value })
    }
    inCall.value = false
    currentRoomId.value = null
  }

  // helper: send signaling via STOMP (injected after store creation)
  let sendSignaling: (type: string, payload: Record<string, unknown>) => void = () => {}

  function setSendSignaling(fn: typeof sendSignaling) {
    sendSignaling = fn
  }

  return {
    localStream, remotePeers, inCall, currentRoomId,
    videoEnabled, audioEnabled,
    setLocalStream, addRemotePeer, setRemoteStream, removeRemotePeer,
    createPeerConnection,
    toggleVideo, toggleAudio, startCall, endCall,
    setSendSignaling, iceServers,
  }
})
