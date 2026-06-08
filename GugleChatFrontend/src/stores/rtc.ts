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
    quality: number
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
        username: string;
        quality: number;
    }

    interface AudioDevice {
        deviceId: string
        label: string
    }
    const voiceUsers = ref<VoiceUser[]>([])
    const showVoiceChat = ref(false)
    const hostId = ref<number | null>(null)
    const speaking = ref(false)
    const remoteSpeaking = ref<Record<number, boolean>>({})
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
        for (const u of users) {
            const peer = remotePeers.value[u.userId]
            if (peer) { peer.quality = u.quality ?? 0; remotePeers.value = {...remotePeers.value} }
        }
    }

    function addRemotePeer(userId: number, username: string, pc: RTCPeerConnection) {
        remotePeers.value = {...remotePeers.value, [userId]: {
            userId, username, stream: null, pc, iceBuffer: [], audioEl: null, quality: 0,
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
            stopRemoteVad(userId)
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
                    peer.audioEl?.pause(); peer.audioEl?.remove()
                }
                const audio = document.createElement('audio')
                audio.srcObject = stream
                audio.autoplay = true
                audio.controls = false
                audio.style.display = 'none'
                document.body.appendChild(audio)
                audio.play().catch(e => console.warn('[RTC] audio play blocked:', e))
                if (peer) { peer.audioEl = audio; remotePeers.value = {...remotePeers.value} }
                // Remote VAD: detect when this peer is speaking
                startRemoteVad(targetId, stream)
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
        // NAT type detection + bandwidth → composite quality score
        const natScore = await detectNatType()
        const bwScore = Math.min(((navigator as any).connection?.downlink as number) || 1.0, 20) / 20
        const quality = natScore * 5 + bwScore  // NAT weighted higher than bandwidth
        console.log(`[RTC] NAT score=${natScore}, BW=${((navigator as any).connection?.downlink || 1)}Mbps, quality=${quality.toFixed(1)}`)
        sendSignaling('rtc.join/' + roomId, { quality })
    }

    function endCall() {
        Object.values(remotePeers.value).forEach(p => p.pc.close())
        Object.keys(remoteVadTimers).forEach(uid => stopRemoteVad(Number(uid)))
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

    // Remote VAD: track each remote stream separately
    const remoteVadTimers: Record<number, number> = {}

    function startRemoteVad(userId: number, stream: MediaStream) {
        stopRemoteVad(userId)
        const ctx = new AudioContext()
        const analyser = ctx.createAnalyser()
        analyser.fftSize = 256
        ctx.createMediaStreamSource(stream).connect(analyser)
        const data = new Uint8Array(analyser.frequencyBinCount)
        const tick = () => {
            analyser.getByteFrequencyData(data)
            const avg = data.reduce((a, b) => a + b, 0) / data.length
            const speaking = avg > 30
            if (remoteSpeaking.value[userId] !== speaking) {
                remoteSpeaking.value = { ...remoteSpeaking.value, [userId]: speaking }
            }
            remoteVadTimers[userId] = requestAnimationFrame(tick)
        }
        tick()
    }

    function stopRemoteVad(userId: number) {
        if (remoteVadTimers[userId]) { cancelAnimationFrame(remoteVadTimers[userId]); delete remoteVadTimers[userId] }
        if (remoteSpeaking.value[userId]) {
            remoteSpeaking.value = { ...remoteSpeaking.value, [userId]: false }
        }
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

    /** Detect NAT type: 1=open(NAT1), 0.66=cone(NAT2-3), 0.33=symmetric(NAT4) */
    async function detectNatType(): Promise<number> {
        return new Promise((resolve) => {
            const pc = new RTCPeerConnection({
                iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
            })
            const candidates: RTCIceCandidate[] = []
            const timer = setTimeout(() => {
                pc.close()
                const types = candidates.map(c => c.type || '')
                const hasHost = types.includes('host') && !candidates.every(c => c.address?.startsWith('192.') || c.address?.startsWith('10.') || c.address?.startsWith('172.'))
                const hasSrflx = types.includes('srflx')
                const hasRelay = types.includes('relay')
                if (hasHost) resolve(1.0)       // NAT1: open, can be host directly
                else if (hasSrflx && !hasRelay) resolve(0.66)  // NAT2-3: cone NAT
                else resolve(0.33)               // NAT4: symmetric, needs relay
            }, 3000)
            pc.onicecandidate = (e) => {
                if (e.candidate) candidates.push(e.candidate)
                else { clearTimeout(timer); resolve(1.0) }
            }
            pc.createDataChannel('nat-check')
            pc.createOffer().then(o => pc.setLocalDescription(o))
        })
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
        hostId, startCall, endCall, toggleVideo, toggleAudio, speaking, remoteSpeaking, monitoring, setMonitoring,
        audioInputs, currentAudioDevice, enumerateAudioDevices, switchAudioDevice,
        setSendSignaling: (fn: typeof sendSignaling) => {
            sendSignaling = fn
        },
        getIceServers,
    }
})
