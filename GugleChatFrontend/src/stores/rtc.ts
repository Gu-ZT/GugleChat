import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface RemotePeer {
  userId: number
  username: string
  stream: MediaStream | null
  pc: RTCPeerConnection
}

export const useRtcStore = defineStore('rtc', () => {
  const localStream = ref<MediaStream | null>(null)
  const remotePeers = ref<Record<number, RemotePeer>>({})
  const activeRoomId = ref<number | null>(null)
  const videoEnabled = ref(false)
  const audioEnabled = ref(true)
  const voiceUsers = ref<Set<number>>(new Set())
  const showVoiceChat = ref(false)

  const iceServers: RTCIceServer[] = [
    { urls: 'stun:stun.l.google.com:19302' },
  ]

  function setVoiceUsers(ids: number[]) {
    voiceUsers.value = new Set(ids)
  }

  function addRemotePeer(userId: number, username: string, pc: RTCPeerConnection) {
    remotePeers.value = { ...remotePeers.value, [userId]: { userId, username, stream: null, pc } }
  }

  function setRemoteStream(userId: number, stream: MediaStream | null) {
    const peer = remotePeers.value[userId]
    if (peer) { peer.stream = stream; remotePeers.value = { ...remotePeers.value } }
  }

  function removeRemotePeer(userId: number) {
    const peer = remotePeers.value[userId]
    if (peer) {
      peer.pc.close()
      const next = { ...remotePeers.value }; delete next[userId]; remotePeers.value = next
    }
  }

  function createPeerConnection(targetId: number, username: string): RTCPeerConnection {
    const pc = new RTCPeerConnection({ iceServers })
    addRemotePeer(targetId, username, pc)

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignaling('rtc.ice-candidate', { target: targetId, candidate: event.candidate })
      }
    }
    pc.ontrack = (event) => { setRemoteStream(targetId, event.streams[0]) }

    if (localStream.value) {
      localStream.value.getTracks().forEach(track => pc.addTrack(track, localStream.value!))
    }
    return pc
  }

  async function startCall(roomId: number) {
    if (activeRoomId.value) endCall()
    activeRoomId.value = roomId
    try {
      localStream.value = await navigator.mediaDevices.getUserMedia({ video: false, audio: true })
    } catch {
      localStream.value = await navigator.mediaDevices.getUserMedia({ audio: true })
    }
    videoEnabled.value = false
    audioEnabled.value = true
    sendSignaling('rtc.join', { roomId })
  }

  function endCall() {
    Object.values(remotePeers.value).forEach(p => p.pc.close())
    remotePeers.value = {}
    voiceUsers.value = new Set()
    if (localStream.value) {
      localStream.value.getTracks().forEach(t => t.stop())
      localStream.value = null
    }
    if (activeRoomId.value) {
      sendSignaling('rtc.leave', { roomId: activeRoomId.value })
    }
    activeRoomId.value = null
    videoEnabled.value = false
  }

  async function toggleVideo() {
    if (!videoEnabled.value) {
      if (!localStream.value) return
      try {
        const newStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
        localStream.value.getTracks().forEach(t => t.stop())
        localStream.value = newStream
        videoEnabled.value = true
        // Replace tracks on existing connections
        const videoTrack = newStream.getVideoTracks()[0]
        Object.values(remotePeers.value).forEach(peer => {
          const sender = peer.pc.getSenders().find(s => s.track?.kind === 'video')
          if (sender && videoTrack) sender.replaceTrack(videoTrack)
          else if (videoTrack) peer.pc.addTrack(videoTrack, newStream)
        })
      } catch { /* camera denied */ }
    } else {
      localStream.value?.getVideoTracks().forEach(t => t.stop())
      videoEnabled.value = false
    }
  }

  function toggleAudio() {
    audioEnabled.value = !audioEnabled.value
    localStream.value?.getAudioTracks().forEach(t => t.enabled = audioEnabled.value)
  }

  let sendSignaling: (type: string, payload: Record<string, unknown>) => void = () => {}

  return {
    localStream, remotePeers, activeRoomId, videoEnabled, audioEnabled, voiceUsers, showVoiceChat,
    setVoiceUsers,
    addRemotePeer, setRemoteStream, removeRemotePeer, createPeerConnection,
    startCall, endCall, toggleVideo, toggleAudio,
    setSendSignaling: (fn: typeof sendSignaling) => { sendSignaling = fn },
  }
})
