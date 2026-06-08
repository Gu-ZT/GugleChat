import {defineStore} from 'pinia'
import {ref} from 'vue'

export interface RemotePeer {
    userId: number
    username: string
    stream: MediaStream | null
    pc: RTCPeerConnection
    iceBuffer: RTCIceCandidateInit[]
    iceState: RTCIceConnectionState
    connState: RTCPeerConnectionState
}

export function connStateLabel(state: string): string {
    switch (state) {
        case 'connected': case 'completed': return 'P2P connected'
        case 'checking': return 'Connecting...'
        case 'new': case 'connecting': return 'Signaling...'
        case 'failed': return 'Connection failed'
        case 'disconnected': return 'Disconnected'
        default: return state
    }
}

export function connStateColor(state: string): string {
    switch (state) {
        case 'connected': case 'completed': return '#22c55e'
        case 'checking': case 'new': case 'connecting': return '#fbbf24'
        case 'failed': case 'disconnected': return '#ef4444'
        default: return '#888'
    }
}

export const useRtcStore = defineStore('rtc', () => {
    const localStream = ref<MediaStream | null>(null)
    const remotePeers = ref<Record<number, RemotePeer>>({})
    const activeRoomId = ref<number | null>(null)
    const videoEnabled = ref(false)
    const audioEnabled = ref(true)

    interface VoiceUser {
        userId: number;
        username: string
    }

    const voiceUsers = ref<VoiceUser[]>([])
    const showVoiceChat = ref(false)

    function getIceServers(): RTCIceServer[] {
        const servers: RTCIceServer[] = [
            {urls: 'stun:stun.l.google.com:19302'},
            {urls: 'stun:stun1.l.google.com:19302'},
            {urls: 'stun:stun2.l.google.com:19302'},
            {urls: 'stun:stun3.l.google.com:19302'},
            {urls: 'stun:stun4.l.google.com:19302'},
            {urls: 'stun:stun.cloudflare.com:3478'},
            {urls: 'stun:stun.voipbuster.com:3478'},
            {urls: 'stun:stun.sipnet.net:3478'},
            {urls: 'stun:stun.ippi.fr:3478'},
            {urls: 'stun:stun.voipstunt.com:3478'},
            {urls: 'stun:stun.counterpath.net:3478'},
            {urls: 'stun:stun.ekiga.net:3478'},
            {urls: 'stun:stun.ideasip.com:3478'},
            {urls: 'stun:stun.schlund.de:3478'},
            {urls: 'stun:stun.voiparound.com:3478'},
            {urls: 'stun:stun.voipbuster.com:3478'},
            {urls: 'stun:stun.voipstunt.com:3478'},
            {urls: 'stun:stun1.voiceeclipse.net:3478'},
        ]
        const turnUrl = localStorage.getItem('guglechat_turn_url')
        const turnUser = localStorage.getItem('guglechat_turn_user')
        const turnPass = localStorage.getItem('guglechat_turn_pass')
        if (turnUrl && turnUser && turnPass) {
            servers.push({urls: turnUrl, username: turnUser, credential: turnPass})
        }
        return servers
    }

    function setVoiceUsers(users: VoiceUser[]) {
        voiceUsers.value = users || []
    }

    function addRemotePeer(userId: number, username: string, pc: RTCPeerConnection) {
        remotePeers.value = {...remotePeers.value, [userId]: {
            userId, username, stream: null, pc, iceBuffer: [],
            iceState: 'new', connState: 'new',
        }}
    }

    function setRemoteStream(userId: number, stream: MediaStream | null) {
        const peer = remotePeers.value[userId]
        if (peer) {
            peer.stream = stream;
            remotePeers.value = {...remotePeers.value}
        }
    }

    function removeRemotePeer(userId: number) {
        const peer = remotePeers.value[userId]
        if (peer) {
            peer.pc.close()
            const next = {...remotePeers.value};
            delete next[userId];
            remotePeers.value = next
        }
    }

    function createPeerConnection(targetId: number, username: string): RTCPeerConnection {
        const pc = new RTCPeerConnection({iceServers: getIceServers()})
        addRemotePeer(targetId, username, pc)

        pc.onicecandidate = (event) => {
            if (event.candidate) {
                sendSignaling('rtc.ice-candidate', {target: targetId, candidate: event.candidate})
            }
        }
        pc.ontrack = (event) => {
            console.log(`[RTC] ontrack from ${username}:`, event.track.kind)
            setRemoteStream(targetId, event.streams[0])
            // Play audio directly
            const stream = event.streams[0]
            if (stream) {
                const audio = new Audio()
                audio.srcObject = stream
                audio.play().catch(() => {})
            }
        }
        pc.oniceconnectionstatechange = () => {
            const peer = remotePeers.value[targetId]
            if (peer) {
                peer.iceState = pc.iceConnectionState
                remotePeers.value = {...remotePeers.value}
                console.log(`[RTC] P2P ${username}: ${pc.iceConnectionState}`)
            }
        }
        pc.onconnectionstatechange = () => {
            const peer = remotePeers.value[targetId]
            if (peer) {
                peer.connState = pc.connectionState
                remotePeers.value = {...remotePeers.value}
            }
        }

        // Add transceivers BEFORE creating offer to ensure consistent m-line order
        pc.addTransceiver('audio', {direction: 'sendrecv'})
        pc.addTransceiver('video', {direction: 'sendrecv'})

        // Replace placeholder tracks with actual local tracks
        if (localStream.value) {
            const audioTrack = localStream.value.getAudioTracks()[0]
            console.log(`[RTC] local audio track: ${audioTrack?.kind || 'none'}, enabled: ${audioTrack?.enabled}`)
            if (audioTrack) {
                const sender = pc.getSenders().find(s => s.track?.kind === 'audio')
                if (sender) sender.replaceTrack(audioTrack)
            }
            const videoTrack = localStream.value.getVideoTracks()[0]
            if (videoTrack) {
                const sender = pc.getSenders().find(s => s.track?.kind === 'video')
                if (sender) sender.replaceTrack(videoTrack)
            }
        } else {
            console.log('[RTC] no local stream — cannot send audio')
        }
        return pc
    }

    async function startCall(roomId: number) {
        // Already in this room — don't rejoin
        if (activeRoomId.value === roomId) return
        if (activeRoomId.value) endCall()
        activeRoomId.value = roomId
        // Try to get audio (may fail without HTTPS/localhost)
        if (navigator.mediaDevices) {
            try {
                localStream.value = await navigator.mediaDevices.getUserMedia({video: false, audio: true})
                console.log('[RTC] microphone OK')
            } catch (e: any) {
                console.error('[RTC] microphone denied:', e.name, e.message)
            }
        }
        videoEnabled.value = false
        audioEnabled.value = true
        sendSignaling('rtc.join/' + roomId, {})
    }

    function endCall() {
        Object.values(remotePeers.value).forEach(p => p.pc.close())
        remotePeers.value = {}
        voiceUsers.value = []
        if (localStream.value) {
            localStream.value.getTracks().forEach(t => t.stop())
            localStream.value = null
        }
        if (activeRoomId.value) {
            sendSignaling('rtc.leave/' + activeRoomId.value, {})
        }
        activeRoomId.value = null
        videoEnabled.value = false
    }

    async function toggleVideo() {
        if (!videoEnabled.value) {
            if (!localStream.value) return
            try {
                const newStream = await navigator.mediaDevices.getUserMedia({video: true, audio: true})
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
            } catch { /* camera denied */
            }
        } else {
            localStream.value?.getVideoTracks().forEach(t => t.stop())
            videoEnabled.value = false
        }
    }

    function toggleAudio() {
        audioEnabled.value = !audioEnabled.value
        localStream.value?.getAudioTracks().forEach(t => t.enabled = audioEnabled.value)
    }

    let sendSignaling: (type: string, payload: Record<string, unknown>) => void = () => {
    }

    return {
        localStream, remotePeers, activeRoomId, videoEnabled, audioEnabled, voiceUsers, showVoiceChat,
        setVoiceUsers,
        addRemotePeer, setRemoteStream, removeRemotePeer, createPeerConnection,
        startCall, endCall, toggleVideo, toggleAudio,
        setSendSignaling: (fn: typeof sendSignaling) => {
            sendSignaling = fn
        },
        getIceServers,
    }
})
