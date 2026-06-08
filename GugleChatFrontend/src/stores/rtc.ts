import {defineStore} from 'pinia'
import {ref} from 'vue'

export interface RemotePeer {
    userId: number
    username: string
    stream: MediaStream | null
    pc: RTCPeerConnection
    iceBuffer: RTCIceCandidateInit[]
    audioEl: HTMLAudioElement | null
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

    interface AudioDevice {
        deviceId: string
        label: string
    }
    const voiceUsers = ref<VoiceUser[]>([])
    const showVoiceChat = ref(false)
    const speaking = ref(false)
    const monitoring = ref(false)
    const audioInputs = ref<AudioDevice[]>([])
    const currentAudioDevice = ref('')
    let audioCtx: AudioContext | null = null
    let vadTimer: number | null = null
    let monitorGain: GainNode | null = null

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
            userId, username, stream: null, pc, iceBuffer: [], audioEl: null,
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
            peer.audioEl?.pause()
            peer.audioEl?.remove()
            peer.audioEl = null
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
            const stream = event.streams[0]
            if (stream) {
                const peer = remotePeers.value[targetId]
                if (peer) {
                    // Stop old audio if exists
                    peer.audioEl?.pause()
                    peer.audioEl?.remove()
                }
                const audio = document.createElement('audio')
                audio.srcObject = stream
                audio.autoplay = true
                audio.controls = false
                audio.style.display = 'none'
                document.body.appendChild(audio)
                audio.play().catch(e => console.warn('[RTC] audio play blocked:', e))
                if (peer) {
                    peer.audioEl = audio
                    remotePeers.value = {...remotePeers.value}
                }
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

        // Add local tracks directly (standard WebRTC approach)
        if (localStream.value) {
            localStream.value.getTracks().forEach(track => {
                console.log(`[RTC] adding track: ${track.kind}, enabled: ${track.enabled}`)
                pc.addTrack(track, localStream.value!)
            })
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
        await enumerateAudioDevices()
        // Try to get audio (may fail without HTTPS/localhost)
        if (navigator.mediaDevices) {
            try {
                const audioOpt = currentAudioDevice.value
                    ? { deviceId: { exact: currentAudioDevice.value } } as MediaTrackConstraints
                    : true
                localStream.value = await navigator.mediaDevices.getUserMedia({video: false, audio: audioOpt})
                console.log('[RTC] microphone OK')
                startVad()
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
        stopVad()
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

    async function enumerateAudioDevices() {
        if (!navigator.mediaDevices) return
        try {
            const devices = await navigator.mediaDevices.enumerateDevices()
            audioInputs.value = devices
                .filter(d => d.kind === 'audioinput' && d.deviceId)
                .map(d => ({ deviceId: d.deviceId, label: d.label || `Microphone ${d.deviceId.slice(0, 8)}` }))
            if (!currentAudioDevice.value && audioInputs.value.length > 0) {
                currentAudioDevice.value = audioInputs.value[0].deviceId
            }
        } catch {}
    }

    async function switchAudioDevice(deviceId: string) {
        currentAudioDevice.value = deviceId
        if (!activeRoomId.value) return
        try {
            const newStream = await navigator.mediaDevices.getUserMedia({
                audio: { deviceId: { exact: deviceId } },
            })
            const oldTrack = localStream.value?.getAudioTracks()[0]
            if (oldTrack) {
                oldTrack.stop()
                newStream.getAudioTracks().forEach(track => {
                    if (oldTrack) localStream.value?.removeTrack(oldTrack)
                    localStream.value?.addTrack(track)
                    Object.values(remotePeers.value).forEach(peer => {
                        const sender = peer.pc.getSenders().find(s => s.track?.kind === 'audio')
                        if (sender) sender.replaceTrack(track)
                    })
                })
            }
        } catch (e) {
            console.error('[RTC] switch audio device failed:', e)
        }
    }

    function setMonitoring(on: boolean) {
        monitoring.value = on
        if (on) {
            startMonitor()
        } else {
            stopMonitor()
        }
    }

    function startVad() {
        if (!localStream.value || audioCtx) return
        audioCtx = new AudioContext()
        const source = audioCtx.createMediaStreamSource(localStream.value)
        const analyser = audioCtx.createAnalyser()
        analyser.fftSize = 256
        source.connect(analyser)
        const data = new Uint8Array(analyser.frequencyBinCount)
        const tick = () => {
            if (!audioCtx) return
            analyser.getByteFrequencyData(data)
            const avg = data.reduce((a, b) => a + b, 0) / data.length
            speaking.value = avg > 30
            vadTimer = requestAnimationFrame(tick)
        }
        tick()
    }

    function stopVad() {
        if (vadTimer) { cancelAnimationFrame(vadTimer); vadTimer = null }
        if (audioCtx) { audioCtx.close(); audioCtx = null }
        monitorGain = null
        speaking.value = false
    }

    function startMonitor() {
        if (!audioCtx || !localStream.value) return
        stopMonitor()
        const source = audioCtx.createMediaStreamSource(localStream.value)
        monitorGain = audioCtx.createGain()
        monitorGain.gain.value = 1.0
        source.connect(monitorGain)
        monitorGain.connect(audioCtx.destination)
    }

    function stopMonitor() {
        monitorGain?.disconnect()
        monitorGain = null
    }

    let sendSignaling: (type: string, payload: Record<string, unknown>) => void = () => {
    }

    return {
        localStream, remotePeers, activeRoomId, videoEnabled, audioEnabled, voiceUsers, showVoiceChat,
        setVoiceUsers,
        addRemotePeer, setRemoteStream, removeRemotePeer, createPeerConnection,
        startCall, endCall, toggleVideo, toggleAudio, speaking, monitoring, setMonitoring,
        audioInputs, currentAudioDevice, enumerateAudioDevices, switchAudioDevice,
        setSendSignaling: (fn: typeof sendSignaling) => {
            sendSignaling = fn
        },
        getIceServers,
    }
})
