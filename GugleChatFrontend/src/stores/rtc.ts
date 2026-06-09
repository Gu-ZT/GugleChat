import {defineStore} from 'pinia'
import {ref, watch} from 'vue'
import {useSettingsStore} from './settings'
import {useAuthStore} from './auth'

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
    latency: number
    pingChannel: RTCDataChannel | null
    /** Forwarded video streams from other peers (via host relay), keyed by original sender userId */
    forwardedVideos: Map<number, MediaStream>
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
        case 'connected': case 'completed': return 'rgb(var(--green-6))'
        case 'checking': case 'new': case 'connecting': return 'rgb(var(--orange-6))'
        case 'failed': case 'disconnected': return 'rgb(var(--red-6))'
        default: return 'var(--color-text-3)'
    }
}

export const useRtcStore = defineStore('rtc', () => {
    const localStream = ref<MediaStream | null>(null)
    const remotePeers = ref<Record<number, RemotePeer>>({})
    const relayLatencies = ref<Record<number, number>>({})
    const peerConnStates = ref<Record<number, string>>({})
    const broadcastSpeaking = ref<Record<number, boolean>>({})
    const mutedPeers = ref<Record<number, boolean>>({})
    const activeRoomId = ref<number | null>(null)
    const videoEnabled = ref(false)
    const screenSharing = ref(false)
    const screenShareVersion = ref(0)
    const audioEnabled = ref(true)
    const speakerEnabled = ref(true)
    const echoCancellation = ref(localStorage.getItem('guglechat_echo_cancel') !== 'false')
    const noiseSuppression = ref(localStorage.getItem('guglechat_noise_suppress') !== 'false')
    const noiseFxEnabled = ref(localStorage.getItem('guglechat_noise_fx') === 'true')
    const rnnoiseEnabled = ref(localStorage.getItem('guglechat_rnnoise') === 'true')

    interface VoiceUser {
        userId: number;
        username: string;
        quality: number;
    }

    interface AudioDevice {
        deviceId: string
        label: string
    }
    const voiceUsersByChannel = ref<Record<number, VoiceUser[]>>({})
    const showVoiceChat = ref(false)
    const hostId = ref<number | null>(null)
    const forcedHostId = ref<number>(0)
    watch(hostId, (newHost, oldHost) => {
        relayLatencies.value = {}
        peerConnStates.value = {}
        broadcastSpeaking.value = {}
        // If I was the old host and no longer am, clean up my peer connections
        const myId = useAuthStore().user?.id
        if (oldHost === myId && newHost !== myId) {
            Object.values(remotePeers.value).forEach(p => p.pc.close())
            remotePeers.value = {}
        }
    })
    const speaking = ref(false)
    const remoteSpeaking = ref<Record<number, boolean>>({})
    const monitoring = ref(false)
    const audioInputs = ref<AudioDevice[]>([])
    const currentAudioDevice = ref('')
    const micVolume = ref(Number(localStorage.getItem('guglechat_mic_volume') || 100))
    const speakerVolume = ref(Number(localStorage.getItem('guglechat_speaker_volume') || 100))
    const audioOutputs = ref<AudioDevice[]>([])
    const currentOutputDevice = ref('')
    let audioCtx: AudioContext | null = null
    let vadTimer: number | null = null
    let monitorGain: GainNode | null = null
    let micGainNode: GainNode | null = null
    let micProcessedTrack: MediaStreamTrack | null = null
    let rnnoiseNode: AudioWorkletNode | null = null
    let rnnoiseDest: MediaStreamAudioDestinationNode | null = null
    // Shared AudioContext for remote VAD (avoids per-peer AudioContext limit)
    let remoteVadCtx: AudioContext | null = null
    const remoteVadAnalysers = new Map<number, { analyser: AnalyserNode, source: MediaStreamAudioSourceNode }>()
    // Video forwarding: streamId-based mapping (handles out-of-order track vs DataChannel arrival)
    const pendingFwdStreams = new Map<string, MediaStream>()   // streamId → stream (track arrived first)
    const pendingFwdUsers = new Map<string, number>()          // streamId → fromUserId (msg arrived first)

    function setMicVolume(v: number) {
        micVolume.value = v
        localStorage.setItem('guglechat_mic_volume', String(v))
        if (micGainNode) micGainNode.gain.value = v / 100
        applyMicGain()
    }

    function setSpeakerVolume(v: number) {
        speakerVolume.value = v
        localStorage.setItem('guglechat_speaker_volume', String(v))
        Object.values(remotePeers.value).forEach(p => {
            if (p.audioEl) p.audioEl.volume = Math.min(v / 100, 1)
        })
    }

    async function enumerateAudioOutputs() {
        if (!navigator.mediaDevices?.enumerateDevices) return
        try {
            const devices = await navigator.mediaDevices.enumerateDevices()
            audioOutputs.value = devices
                .filter(d => d.kind === 'audiooutput' && d.deviceId)
                .map(d => ({ deviceId: d.deviceId, label: d.label || `Speaker ${d.deviceId.slice(0, 8)}` }))
            const saved = localStorage.getItem('guglechat_output_device')
            if (saved && audioOutputs.value.some(d => d.deviceId === saved)) {
                currentOutputDevice.value = saved
            } else if (audioOutputs.value.length > 0) {
                currentOutputDevice.value = audioOutputs.value[0].deviceId
            }
        } catch {}
    }

    function switchAudioOutput(deviceId: string) {
        currentOutputDevice.value = deviceId
        localStorage.setItem('guglechat_output_device', deviceId)
        Object.values(remotePeers.value).forEach(p => {
            if (p.audioEl && typeof (p.audioEl as any).setSinkId === 'function') {
                ;(p.audioEl as any).setSinkId(deviceId).catch((e: any) =>
                    console.warn('[RTC] setSinkId failed:', e.message))
            } else if (p.audioEl) {
                console.warn('[RTC] setSinkId not supported in this browser')
            }
        })
    }

    function applyMicGain() {
        const srcStream = rnnoiseDest ? rnnoiseDest.stream : localStream.value
        if (!srcStream) return
        const origTrack = srcStream.getAudioTracks()[0]
        if (!origTrack) return
        if (micVolume.value === 100) {
            // Restore original track
            if (micProcessedTrack) {
                Object.values(remotePeers.value).forEach(p => {
                    const s = p.pc.getSenders().find(s => s.track?.kind === 'audio')
                    if (s) s.replaceTrack(origTrack)
                })
                micProcessedTrack.stop(); micProcessedTrack = null
            }
            return
        }
        const ctx = audioCtx || new AudioContext()
        if (!micGainNode) {
            const source = ctx.createMediaStreamSource(srcStream)
            micGainNode = ctx.createGain()
            micGainNode.gain.value = micVolume.value / 100
            const dest = ctx.createMediaStreamDestination()
            source.connect(micGainNode).connect(dest)
            micProcessedTrack = dest.stream.getAudioTracks()[0]
        }
        Object.values(remotePeers.value).forEach(p => {
            const s = p.pc.getSenders().find(s => s.track?.kind === 'audio')
            if (s) s.replaceTrack(micProcessedTrack!)
        })
    }

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

    function setVoiceUsers(channelId: number, users: VoiceUser[]) {
        console.log('[RTC] setVoiceUsers channel', channelId, users)
        voiceUsersByChannel.value = { ...voiceUsersByChannel.value, [channelId]: users || [] }
        for (const u of users) {
            const peer = remotePeers.value[u.userId]
            if (peer) { peer.quality = u.quality ?? 0; remotePeers.value = {...remotePeers.value} }
        }
    }

    function getVoiceUsers(channelId: number): VoiceUser[] {
        return voiceUsersByChannel.value[channelId] || []
    }

    function clearVoiceUsers(channelId: number) {
        voiceUsersByChannel.value = { ...voiceUsersByChannel.value, [channelId]: [] }
    }

    function addRemotePeer(userId: number, username: string, pc: RTCPeerConnection) {
        remotePeers.value = {...remotePeers.value, [userId]: {
            userId, username, stream: null, pc, iceBuffer: [], audioEl: null, quality: 0,
            iceState: 'new', connState: 'new', latency: -1, pingChannel: null,
            forwardedVideos: new Map(),
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
        const rl = {...relayLatencies.value}; delete rl[userId]; relayLatencies.value = rl
    }

    function clearAllPeers() {
        Object.values(remotePeers.value).forEach(p => {
            stopRemoteVad(p.userId)
            p.audioEl?.pause()
            p.audioEl?.remove()
            p.audioEl = null
            p.pc.close()
        })
        remotePeers.value = {}
        relayLatencies.value = {}
        broadcastSpeaking.value = {}
        peerConnStates.value = {}
    }

    function setForwardedVideo(senderId: number, fromUserId: number, stream: MediaStream) {
        const peer = remotePeers.value[senderId]
        if (!peer) return
        const updated = new Map(peer.forwardedVideos)
        updated.set(fromUserId, stream)
        peer.forwardedVideos = updated
        remotePeers.value = {...remotePeers.value}
        // Auto-cleanup when the forwarding track ends
        const videoTrack = stream.getVideoTracks()[0]
        if (videoTrack) {
            videoTrack.onended = () => removeForwardedVideo(senderId, fromUserId)
        }
    }

    function removeForwardedVideo(senderId: number, fromUserId: number) {
        const peer = remotePeers.value[senderId]
        if (!peer || !peer.forwardedVideos.has(fromUserId)) return
        const updated = new Map(peer.forwardedVideos)
        updated.delete(fromUserId)
        peer.forwardedVideos = updated
        remotePeers.value = {...remotePeers.value}
    }

    function createPeerConnection(targetId: number, username: string): RTCPeerConnection {
        const pc = Object.assign(new RTCPeerConnection({iceServers: getIceServers()}), {_gugleInitPing: undefined as any})
        addRemotePeer(targetId, username, pc)

        pc.onicecandidate = (event) => {
            if (event.candidate) {
                sendSignaling('rtc.ice-candidate', {target: targetId, candidate: event.candidate})
            }
        }
        pc.ontrack = (event) => {
            console.log(`[RTC] ontrack from ${username}:`, event.track.kind)
            const stream = event.streams[0]
            if (!stream) return

            // Check if this is a forwarded video track (has streamId-based mapping)
            if (event.track.kind === 'video' && stream) {
                const streamId = stream.id
                const pendingUser = pendingFwdUsers.get(streamId)
                if (pendingUser) {
                    // DataChannel message arrived first: pair up now
                    setForwardedVideo(targetId, pendingUser, stream)
                    pendingFwdUsers.delete(streamId)
                    return
                }
                // Track arrived first: store for later pairing with video-fwd message
                pendingFwdStreams.set(streamId, stream)
                // Fallback: if no video-fwd arrives within 2s, treat as sender's own video
                setTimeout(() => {
                    if (pendingFwdStreams.has(streamId)) {
                        pendingFwdStreams.delete(streamId)
                        setRemoteStream(targetId, stream)
                        console.log(`[RTC] video track from ${username} not paired — treating as direct`)
                    }
                }, 2000)
                return
            }

            // Audio track or sender's own video: standard handling
            setRemoteStream(targetId, stream)
            if (stream) {
                const peer = remotePeers.value[targetId]
                if (peer) {
                    peer.audioEl?.pause(); peer.audioEl?.remove()
                }
                const audio = document.createElement('audio')
                audio.srcObject = stream
                audio.autoplay = true
                audio.controls = false
                audio.muted = !speakerEnabled.value
                audio.volume = Math.min(speakerVolume.value / 100, 1)
                if (currentOutputDevice.value && 'setSinkId' in audio) {
                    ;(audio as any).setSinkId(currentOutputDevice.value).catch(() => {})
                }
                audio.style.display = 'none'
                document.body.appendChild(audio)
                audio.play().catch(() => { /* autoplay policy — audio resumes on user interaction */ })
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

        // P2P latency measurement via data channel (received from offerer)
        const LATENCY_LABEL = 'gugle-ping'
        let pingChannel: RTCDataChannel | null = null
        let p2pTimer: ReturnType<typeof setInterval> | null = null
        let speakTimer: ReturnType<typeof setInterval> | null = null

        function stopTimers() {
          if (p2pTimer) { clearInterval(p2pTimer); p2pTimer = null }
          if (speakTimer) { clearInterval(speakTimer); speakTimer = null }
        }

        function sendRelayPings() {
          const myId = useAuthStore().user?.id || 0
          const roomId = activeRoomId.value
          if (!roomId || hostId.value === myId) return
          if (!pingChannel || pingChannel.readyState !== 'open') return
          const users = getVoiceUsers(roomId)
          for (const u of users) {
            if (u.userId === myId || u.userId === hostId.value) continue
            if (remotePeers.value[u.userId]) continue
            pingChannel.send(JSON.stringify({ type: 'relay-ping', from: myId, target: u.userId, ts: performance.now() }))
          }
        }

        function broadcastPeerStates() {
          const myId = useAuthStore().user?.id || 0
          if (hostId.value !== myId) return // Only host broadcasts
          const states: Record<number, string> = {}
          // Host itself is always 'connected'
          states[myId] = 'connected'
          // Add all remote peers' states
          for (const [uid, p] of Object.entries(remotePeers.value)) {
            states[Number(uid)] = p.iceState
          }
          // Also add voice users without peer connections as 'new'
          const roomId = activeRoomId.value
          if (roomId) {
            for (const u of getVoiceUsers(roomId)) {
              if (!(u.userId in states)) states[u.userId] = 'new'
            }
          }
          // Add speaking & muted states
          const localSpeaking: Record<number, boolean> = {}
          for (const [uid, v] of Object.entries(remoteSpeaking.value)) {
            localSpeaking[Number(uid)] = v
          }
          localSpeaking[myId] = speaking.value
          const muted: Record<number, boolean> = {}
          for (const [uid, v] of Object.entries(mutedPeers.value)) {
            muted[Number(uid)] = v
          }
          muted[myId] = !audioEnabled.value
          // Broadcast to all connected peers
          for (const p of Object.values(remotePeers.value)) {
            if (p.pingChannel && p.pingChannel.readyState === 'open') {
              p.pingChannel.send(JSON.stringify({ type: 'peer-states', states, speaking: localSpeaking, muted }))
            }
          }
        }

        function startP2pPing() {
          p2pTimer = setInterval(() => {
            if (!pingChannel || pingChannel.readyState !== 'open') return
            pingChannel.send(JSON.stringify({ type: 'ping', ts: performance.now(), muted: !audioEnabled.value }))
            sendRelayPings()
          }, 3000)
          speakTimer = setInterval(() => {
            broadcastPeerStates()
          }, 500)
        }

        function setupChannel(ch: RTCDataChannel) {
          pingChannel = ch
          // Store on peer for relay lookup
          const peer = remotePeers.value[targetId]
          if (peer) { peer.pingChannel = ch; remotePeers.value = {...remotePeers.value} }
          ch.onmessage = (e) => {
            try {
              const data = JSON.parse(e.data)
              if (data.type === 'ping') {
                if (data.muted !== undefined) {
                  mutedPeers.value = {...mutedPeers.value, [targetId]: data.muted as boolean}
                }
                ch.send(JSON.stringify({ type: 'pong', ts: data.ts, muted: !audioEnabled.value }))
              } else if (data.type === 'pong') {
                if (data.muted !== undefined) {
                  mutedPeers.value = {...mutedPeers.value, [targetId]: data.muted as boolean}
                }
                const rtt = performance.now() - (data.ts as number)
                const p = remotePeers.value[targetId]
                if (p) { p.latency = Math.round(rtt); remotePeers.value = {...remotePeers.value} }
              } else if (data.type === 'relay-ping') {
                const myId = useAuthStore().user?.id
                // If I'm the target, respond with relay-pong back to sender
                if (data.target === myId) {
                  ch.send(JSON.stringify({ type: 'relay-pong', from: myId, target: data.from, ts: data.ts }))
                } else {
                  // I'm the Host: forward to target peer
                  const tp = remotePeers.value[data.target]
                  if (tp?.pingChannel && tp.pingChannel.readyState === 'open') {
                    tp.pingChannel.send(JSON.stringify({ type: 'relay-ping', from: data.from, target: data.target, ts: data.ts }))
                  }
                }
              } else if (data.type === 'peer-states') {
                peerConnStates.value = data.states as Record<number, string>
                if (data.speaking) broadcastSpeaking.value = data.speaking as Record<number, boolean>
                if (data.muted) mutedPeers.value = data.muted as Record<number, boolean>
              } else if (data.type === 'relay-pong') {
                const myId = useAuthStore().user?.id
                if (data.target === myId) {
                  // I'm the original sender: this is the result
                  const rtt = performance.now() - (data.ts as number)
                  relayLatencies.value = {...relayLatencies.value, [data.from]: Math.round(rtt)}
                } else {
                  // I'm the Host: forward pong back to original sender
                  const tp = remotePeers.value[data.target]
                  if (tp?.pingChannel && tp.pingChannel.readyState === 'open') {
                    tp.pingChannel.send(JSON.stringify({ type: 'relay-pong', from: data.from, target: data.target, ts: data.ts }))
                  }
                }
              } else if (data.type === 'video-fwd') {
                // Host is forwarding a video track: map streamId → fromUserId
                const streamId = data.streamId as string
                const fromUserId = data.fromUserId as number
                const pending = pendingFwdStreams.get(streamId)
                if (pending) {
                  // ontrack already fired: pair up now
                  setForwardedVideo(targetId, fromUserId, pending)
                  pendingFwdStreams.delete(streamId)
                } else {
                  // ontrack hasn't fired yet: store for later
                  pendingFwdUsers.set(streamId, fromUserId)
                }
              } else if (data.type === 'video-fwd-end') {
                // Host stopped forwarding a video
                const fromUserId = data.fromUserId as number
                removeForwardedVideo(targetId, fromUserId)
              } else if (data.type === 'video-off') {
                // Peer turned off video — clean up forwarded videos from this peer on all others
                const offUserId = targetId
                for (const p of Object.values(remotePeers.value)) {
                  removeForwardedVideo(p.userId, offUserId)
                  // Also notify other peers to clean up via their ping channels
                  if (p.pingChannel && p.pingChannel.readyState === 'open') {
                    p.pingChannel.send(JSON.stringify({ type: 'video-fwd-end', fromUserId: offUserId }))
                  }
                }
              }
            } catch {}
          }
          ch.onopen = () => startP2pPing()
          ch.onclose = () => stopTimers()
          if (ch.readyState === 'open') startP2pPing()
        }

        // Answerer side: receive data channel from offerer
        pc.ondatachannel = (event) => {
          if (event.channel.label === LATENCY_LABEL) setupChannel(event.channel)
        }

        // Expose for offerer to create data channel before creating offer
        pc._gugleInitPing = () => {
          if (pingChannel) return
          setupChannel(pc.createDataChannel(LATENCY_LABEL))
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
                const ac: MediaTrackConstraints = getAudioConstraints()
                if (currentAudioDevice.value) {
                    (ac as any).deviceId = { exact: currentAudioDevice.value }
                }
                localStream.value = await navigator.mediaDevices.getUserMedia({video: false, audio: ac})
                console.log('[RTC] microphone OK')
                startVad()
                if (rnnoiseEnabled.value) await initRnnoise()
            } catch (e: any) {
                console.error('[RTC] microphone denied:', e.name, e.message)
            }
        }
        videoEnabled.value = false
        audioEnabled.value = true
        // Re-enumerate after mic permission granted (outputs need it)
        enumerateAudioOutputs()
        // NAT type detection + bandwidth → composite quality score
        let natScore: number
        if ((window as any).__TAURI__) {
            // Tauri desktop: use Rust RFC 5780 STUN detection
            try {
                const result = await (window as any).__TAURI__.invoke('check_nat_type')
                natScore = result.score
                console.log(`[RTC] Tauri NAT: ${result.nat_type} score=${natScore}`, result.mappings)
            } catch { natScore = await detectNatType() }
        } else {
            natScore = await detectNatType()
        }
        // Apply manual NAT override: use the worse (lower score) between manual and detected
        const natOverride = useSettingsStore().natOverride
        if (natOverride) {
            const natScoreMap: Record<string, number> = { '1': 1.0, '2': 0.8, '3': 0.6, '4': 0.25 }
            const manualScore = natScoreMap[natOverride] ?? 1.0
            if (manualScore < natScore) {
                console.log(`[RTC] NAT overridden: detected=${natScore} → manual=${manualScore} (worse)`)
                natScore = manualScore
            }
        }
        const bwScore = Math.min(((navigator as any).connection?.downlink as number) || 1.0, 20) / 20
        const quality = natScore * 5 + bwScore
        console.log(`[RTC] NAT score=${natScore}, BW=${((navigator as any).connection?.downlink || 1)}Mbps, quality=${quality.toFixed(1)}`)
        sendSignaling('rtc.join/' + roomId, { quality })
    }

    function endCall() {
        Object.values(remotePeers.value).forEach(p => p.pc.close())
        Object.keys(remoteVadTimers).forEach(uid => stopRemoteVad(Number(uid)))
        remotePeers.value = {}
        relayLatencies.value = {}
        broadcastSpeaking.value = {}
        if (activeRoomId.value) clearVoiceUsers(activeRoomId.value)
        stopVad()
        if (localStream.value) {
            localStream.value.getTracks().forEach(t => t.stop())
            localStream.value = null
        }
        if (activeRoomId.value) {
            sendSignaling('rtc.leave/' + activeRoomId.value, {})
        }
        activeRoomId.value = null
        forcedHostId.value = 0
        videoEnabled.value = false
    }

    function setVideoBitrate(sender: RTCRtpSender, maxKbps: number) {
        const params = sender.getParameters()
        if (params.encodings?.length) {
            params.encodings[0].maxBitrate = maxKbps * 1000
            sender.setParameters(params).catch(() => {})
        }
    }

    async function toggleVideo() {
        if (videoEnabled.value) {
            // Close: stop video tracks, remove from local stream & all senders
            localStream.value?.getVideoTracks().forEach(t => {
                t.stop()
                localStream.value?.removeTrack(t)
            })
            for (const peer of Object.values(remotePeers.value)) {
                const sender = peer.pc.getSenders().find(s => s.track?.kind === 'video')
                if (sender) {
                    peer.pc.removeTrack(sender)
                    // Renegotiate so remote side knows track was removed
                    try {
                        const offer = await peer.pc.createOffer()
                        await peer.pc.setLocalDescription(offer)
                        sendSignaling('rtc.offer', { target: peer.userId, sdp: offer })
                    } catch (e: any) {
                        if (e.name !== 'InvalidStateError') console.warn('[RTC] video remove renegotiation failed:', e.message)
                    }
                    // Notify remote side that video stopped (for forwarding cleanup)
                    if (peer.pingChannel && peer.pingChannel.readyState === 'open') {
                        peer.pingChannel.send(JSON.stringify({ type: 'video-off' }))
                    }
                }
            }
            videoEnabled.value = false
            return
        }
        // Video and screen share mutually exclusive
        if (screenSharing.value) stopScreenShare()
        try {
            // Get video-only stream — never touch audio track
            const videoStream = await navigator.mediaDevices.getUserMedia({video: true, audio: false})
            const videoTrack = videoStream.getVideoTracks()[0]
            if (!videoTrack) return
            // Attach video track to existing local stream (or create one)
            if (!localStream.value) {
                localStream.value = videoStream
            } else {
                // Stop any old video tracks before replacing
                localStream.value.getVideoTracks().forEach(t => {
                    t.stop()
                    localStream.value!.removeTrack(t)
                })
                localStream.value.addTrack(videoTrack)
            }
            // Add video track on all peer connections and renegotiate
            for (const peer of Object.values(remotePeers.value)) {
                const s = peer.pc.addTrack(videoTrack, localStream.value!)
                setVideoBitrate(s, 1500)
                // Send new offer so remote side gets ontrack event
                try {
                    const offer = await peer.pc.createOffer()
                    await peer.pc.setLocalDescription(offer)
                    sendSignaling('rtc.offer', { target: peer.userId, sdp: offer })
                } catch (e: any) {
                    if (e.name !== 'InvalidStateError') console.warn('[RTC] video renegotiation failed:', e.message)
                }
            }
            // Set flag AFTER addTrack so v-if in VoiceCallView re-evaluates with track present
            videoEnabled.value = true
        } catch { /* camera denied */ }
    }

    function toggleAudio() {
        audioEnabled.value = !audioEnabled.value
        localStream.value?.getAudioTracks().forEach(t => t.enabled = audioEnabled.value)
    }

    function toggleSpeaker() {
        speakerEnabled.value = !speakerEnabled.value
        Object.values(remotePeers.value).forEach(p => {
            if (p.audioEl) p.audioEl.muted = !speakerEnabled.value
        })
    }

    async function toggleNoiseFx() {
        noiseFxEnabled.value = !noiseFxEnabled.value
        localStorage.setItem('guglechat_noise_fx', String(noiseFxEnabled.value))
        if (noiseFxEnabled.value && rnnoiseEnabled.value) {
            await initRnnoise()
            if (monitoring.value) { stopMonitor(); await startMonitor() }
        } else if (!noiseFxEnabled.value) {
            destroyRnnoise()
            if (monitoring.value) { stopMonitor(); await startMonitor() }
        }
        refreshAudioStream()
    }

    async function refreshAudioStream() {
        if (!activeRoomId.value && !monitoring.value) return
        const ac: MediaTrackConstraints = getAudioConstraints()
        if (currentAudioDevice.value) {
            (ac as any).deviceId = { exact: currentAudioDevice.value }
        }
        const newStream = await navigator.mediaDevices.getUserMedia({ video: false, audio: ac })
        const oldTrack = localStream.value?.getAudioTracks()[0]
        const newTrack = newStream.getAudioTracks()[0]
        if (!newTrack) return
        // Preserve enabled state from old track
        if (oldTrack) {
            newTrack.enabled = oldTrack.enabled
            localStream.value?.removeTrack(oldTrack)
            oldTrack.stop()
        } else {
            newTrack.enabled = audioEnabled.value
            localStream.value = new MediaStream()
        }
        localStream.value!.addTrack(newTrack)
        if (activeRoomId.value) {
            Object.values(remotePeers.value).forEach(peer => {
                const sender = peer.pc.getSenders().find(s => s.track?.kind === 'audio')
                if (sender) sender.replaceTrack(newTrack)
            })
        }
        // Restart VAD, RNNoise, monitor and mic gain with new stream
        const wasRnnoise = rnnoiseEnabled.value && rnnoiseNode
        stopVad()
        stopMonitor()
        if (wasRnnoise) destroyRnnoise()
        startVad()
        if (wasRnnoise) await initRnnoise()
        if (monitoring.value) await startMonitor()
        if (micGainNode) {
            micGainNode.disconnect(); micGainNode = null
            if (micProcessedTrack) { micProcessedTrack.stop(); micProcessedTrack = null }
        }
        applyMicGain()
    }

    function toggleEchoCancellation() {
        echoCancellation.value = !echoCancellation.value
        localStorage.setItem('guglechat_echo_cancel', String(echoCancellation.value))
        refreshAudioStream()
    }

    function toggleNoiseSuppression() {
        noiseSuppression.value = !noiseSuppression.value
        localStorage.setItem('guglechat_noise_suppress', String(noiseSuppression.value))
        refreshAudioStream()
    }

    async function toggleRnnoise() {
        rnnoiseEnabled.value = !rnnoiseEnabled.value
        localStorage.setItem('guglechat_rnnoise', String(rnnoiseEnabled.value))
        if (rnnoiseEnabled.value) {
            if (noiseSuppression.value) toggleNoiseSuppression()
            await initRnnoise()
        } else {
            destroyRnnoise()
        }
        // Restart monitoring with the new audio path
        if (monitoring.value) {
            stopMonitor()
            await startMonitor()
        }
    }

    async function initRnnoise() {
        if (!noiseFxEnabled.value || !rnnoiseEnabled.value) return
        if (!audioCtx || !localStream.value) return
        if (audioCtx.state === 'suspended') await audioCtx.resume()
        destroyRnnoise()
        try {
            const [{ NoiseSuppressorWorklet_Name }, { default: workletUrl }] = await Promise.all([
                import('@timephy/rnnoise-wasm'),
                // @ts-ignore Vite ?url suffix
                import('@timephy/rnnoise-wasm/NoiseSuppressorWorklet?url')
            ])
            try { await audioCtx.audioWorklet.addModule(workletUrl) } catch (e: any) {
                if (e.message && !e.message.includes('already')) throw e
            }
            const src = audioCtx.createMediaStreamSource(localStream.value)
            rnnoiseNode = new AudioWorkletNode(audioCtx, NoiseSuppressorWorklet_Name, { channelCount: 1, numberOfOutputs: 1 })
            rnnoiseDest = audioCtx.createMediaStreamDestination()
            rnnoiseDest.channelCount = 1
            src.connect(rnnoiseNode).connect(rnnoiseDest)

            // Replace outgoing audio track with RNNoise-processed track
            const processedTrack = rnnoiseDest.stream.getAudioTracks()[0]
            Object.values(remotePeers.value).forEach(peer => {
                const sender = peer.pc.getSenders().find(s => s.track?.kind === 'audio')
                if (sender) sender.replaceTrack(processedTrack)
            })
            console.log('[RTC] RNNoise enabled')
        } catch (e) {
            console.error('[RTC] RNNoise init failed:', e)
            rnnoiseEnabled.value = false
            destroyRnnoise()
        }
    }

    function destroyRnnoise() {
        if (rnnoiseNode) {
            rnnoiseNode.disconnect()
            rnnoiseNode = null
        }
        rnnoiseDest = null
        // Restore original track
        if (localStream.value) {
            const origTrack = localStream.value.getAudioTracks()[0]
            if (origTrack) {
                Object.values(remotePeers.value).forEach(peer => {
                    const sender = peer.pc.getSenders().find(s => s.track?.kind === 'audio')
                    if (sender) sender.replaceTrack(origTrack)
                })
            }
        }
    }

    function getAudioConstraints(): MediaTrackConstraints {
        const on = noiseFxEnabled.value
        return {
            echoCancellation: on && echoCancellation.value,
            noiseSuppression: on && noiseSuppression.value && !rnnoiseEnabled.value,
            autoGainControl: on,
        }
    }

    async function toggleScreenShare() {
        if (screenSharing.value) {
            await stopScreenShare()
            return
        }
        // Screen share and video are mutually exclusive
        if (videoEnabled.value) {
            localStream.value?.getVideoTracks().forEach(t => {
                t.stop()
                localStream.value?.removeTrack(t)
            })
            videoEnabled.value = false
        }
        try {
            const screenStream = await navigator.mediaDevices.getDisplayMedia({ video: true })
            // Keep audio from existing stream, replace video with screen
            const screenTrack = screenStream.getVideoTracks()[0]
            if (!screenTrack) return
            // Add screen track to local stream (create one if needed)
            if (localStream.value) {
                // Remove any old video tracks before adding new one
                localStream.value.getVideoTracks().forEach(t => {
                    t.stop()
                    localStream.value!.removeTrack(t)
                })
                localStream.value.addTrack(screenTrack)
            } else {
                localStream.value = screenStream
            }
            // Add video track on all peer connections and renegotiate
            for (const peer of Object.values(remotePeers.value)) {
                const s = peer.pc.addTrack(screenTrack, localStream.value!)
                setVideoBitrate(s, 4000)
                // Send new offer so remote side gets ontrack event
                try {
                    const offer = await peer.pc.createOffer()
                    await peer.pc.setLocalDescription(offer)
                    sendSignaling('rtc.offer', { target: peer.userId, sdp: offer })
                } catch (e: any) {
                    if (e.name !== 'InvalidStateError') console.warn('[RTC] screen renegotiation failed:', e.message)
                }
            }
            // Set flag AFTER addTrack so v-if in VoiceCallView re-evaluates with track present
            screenSharing.value = true
            screenShareVersion.value++
            // Stop sharing when user clicks browser's "Stop sharing" button
            screenTrack.onended = () => stopScreenShare()
        } catch (e) { /* user cancelled */ }
    }

    async function stopScreenShare() {
        screenSharing.value = false
        screenShareVersion.value++
        // Stop and remove old video tracks from local stream
        localStream.value?.getVideoTracks().forEach(t => {
            t.stop()
            localStream.value?.removeTrack(t)
        })
        // Remove video sender from all peer connections and renegotiate
        for (const peer of Object.values(remotePeers.value)) {
            const sender = peer.pc.getSenders().find(s => s.track?.kind === 'video')
            if (sender) {
                peer.pc.removeTrack(sender)
                // Renegotiate so remote side knows track was removed
                try {
                    const offer = await peer.pc.createOffer()
                    await peer.pc.setLocalDescription(offer)
                    sendSignaling('rtc.offer', { target: peer.userId, sdp: offer })
                } catch (e: any) {
                    if (e.name !== 'InvalidStateError') console.warn('[RTC] screen remove renegotiation failed:', e.message)
                }
                // Notify remote side that video stopped (for forwarding cleanup)
                if (peer.pingChannel && peer.pingChannel.readyState === 'open') {
                    peer.pingChannel.send(JSON.stringify({ type: 'video-off' }))
                }
            }
        }
    }

    async function enumerateAudioDevices() {
        if (!navigator.mediaDevices) return
        try {
            const devices = await navigator.mediaDevices.enumerateDevices()
            audioInputs.value = devices
                .filter(d => d.kind === 'audioinput' && d.deviceId)
                .map(d => ({ deviceId: d.deviceId, label: d.label || `Microphone ${d.deviceId.slice(0, 8)}` }))
            // Restore saved device, or fall back to default
            const saved = localStorage.getItem('guglechat_audio_device')
            if (saved && audioInputs.value.some(d => d.deviceId === saved)) {
                currentAudioDevice.value = saved
            } else if (audioInputs.value.length > 0) {
                currentAudioDevice.value = audioInputs.value[0].deviceId
            }
        } catch {}
    }

    async function switchAudioDevice(deviceId: string) {
        currentAudioDevice.value = deviceId
        localStorage.setItem('guglechat_audio_device', deviceId)
        await refreshAudioStream()
    }

    async function setMonitoring(on: boolean) {
        monitoring.value = on
        if (on) {
            if (!localStream.value) {
                const ac: MediaTrackConstraints = getAudioConstraints()
                if (currentAudioDevice.value) {
                    (ac as any).deviceId = { exact: currentAudioDevice.value }
                }
                try {
                    localStream.value = await navigator.mediaDevices.getUserMedia({ video: false, audio: ac })
                } catch (e) { monitoring.value = false; return }
            }
            if (!audioCtx) { audioCtx = new AudioContext() }
            if (rnnoiseEnabled.value) await initRnnoise()
            await startMonitor()
        } else {
            stopMonitor()
            if (!activeRoomId.value) {
                stopVad()
                localStream.value?.getTracks().forEach(t => t.stop())
                localStream.value = null
            }
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
            speaking.value = avg > 15
            vadTimer = requestAnimationFrame(tick)
        }
        tick()
    }

    // Remote VAD: track each remote stream separately
    const remoteVadTimers: Record<number, number> = {}

    function startRemoteVad(userId: number, stream: MediaStream) {
        stopRemoteVad(userId)
        // Lazy-init shared AudioContext for all remote VAD
        if (!remoteVadCtx) {
            remoteVadCtx = new AudioContext()
        }
        const ctx = remoteVadCtx!
        if (ctx.state === 'suspended') ctx.resume()
        const analyser = ctx.createAnalyser()
        analyser.fftSize = 256
        const source = ctx.createMediaStreamSource(stream)
        source.connect(analyser)
        remoteVadAnalysers.set(userId, { analyser, source })
        const data = new Uint8Array(analyser.frequencyBinCount)
        const tick = () => {
            if (!remoteVadAnalysers.has(userId)) return // stopped
            analyser.getByteFrequencyData(data)
            const avg = data.reduce((a, b) => a + b, 0) / data.length
            const speaking = avg > 15
            if (remoteSpeaking.value[userId] !== speaking) {
                remoteSpeaking.value = { ...remoteSpeaking.value, [userId]: speaking }
            }
            remoteVadTimers[userId] = requestAnimationFrame(tick)
        }
        tick()
    }

    function stopRemoteVad(userId: number) {
        if (remoteVadTimers[userId]) { cancelAnimationFrame(remoteVadTimers[userId]); delete remoteVadTimers[userId] }
        const entry = remoteVadAnalysers.get(userId)
        if (entry) {
            entry.source.disconnect()
            entry.analyser.disconnect()
            remoteVadAnalysers.delete(userId)
        }
        if (remoteSpeaking.value[userId]) {
            remoteSpeaking.value = { ...remoteSpeaking.value, [userId]: false }
        }
        // Close shared AudioContext when no more remote VADs are active
        if (remoteVadAnalysers.size === 0 && remoteVadCtx) {
            remoteVadCtx.close()
            remoteVadCtx = null
        }
    }

    function stopVad() {
        if (vadTimer) { cancelAnimationFrame(vadTimer); vadTimer = null }
        destroyRnnoise()
        if (audioCtx) { audioCtx.close(); audioCtx = null }
        monitorGain = null
        speaking.value = false
    }

    async function startMonitor() {
        if (!audioCtx) return
        const monitorStream = (rnnoiseDest && rnnoiseNode) ? rnnoiseDest.stream : localStream.value
        if (!monitorStream) return
        if (audioCtx.state === 'suspended') await audioCtx.resume()
        stopMonitor()
        const source = audioCtx.createMediaStreamSource(monitorStream)
        monitorGain = audioCtx.createGain()
        monitorGain.gain.value = 1.0
        source.connect(monitorGain)
        monitorGain.connect(audioCtx.destination)
    }

    /** Detect NAT type: 1=open(NAT1), 0.66=cone(NAT2-3), 0.33=symmetric(NAT4) */
    /**
     * RFC 5780 NAT Behavior Discovery (via ICE candidates + multi-STUN analysis)
     * Uses multiple STUN servers to compare mapped addresses.
     *
     * RFC 5780 classification:
     * - Endpoint-Independent Mapping (EIM): same IP:port regardless of destination
     * - Address-Dependent Mapping (ADM): different port per destination IP
     * - Address-and-Port-Dependent Mapping (APDM): different port per destination IP:port
     *
     * Returns score: NAT1=1.0, NAT2=0.8, NAT3=0.6, NAT4=0.25
     */
    async function detectNatType(): Promise<number> {
        return new Promise((resolve) => {
            const stunServers = [
                'stun:stun.l.google.com:19302',
                'stun:stun1.l.google.com:19302',
                'stun:stun2.l.google.com:19302',
                'stun:stun3.l.google.com:19302',
                'stun:stun.cloudflare.com:3478',
            ]
            const pc = new RTCPeerConnection({
                iceServers: stunServers.map(urls => ({ urls })),
            })
            const mappings: {ip: string, port: number}[] = []
            let hasHost = false
            const timer = setTimeout(() => { pc.close(); finish() }, 5000)

            const finish = () => {
                clearTimeout(timer)
                const ports = mappings.map(m => m.port)
                const uniquePorts = new Set(ports)
                const uniqueIPs = new Set(mappings.map(m => m.ip))
                console.log(`[NAT] Mappings from ${stunServers.length} STUN servers:`,
                    mappings.map(m => `${m.ip}:${m.port}`).join(', '))

                if (hasHost) {
                    console.log('[NAT] Type: Open Internet (NAT1) — public host candidate detected')
                    resolve(1.0)
                } else if (mappings.length === 0) {
                    console.log('[NAT] Type: UDP Blocked — no srflx candidates at all')
                    resolve(0.2)
                } else if (uniqueIPs.size === 0) {
                    resolve(0.25)
                } else if (uniquePorts.size === 1) {
                    // Same port mapped to all destinations = Endpoint-Independent Mapping
                    // Could be NAT1 (Full Cone) or NAT2 (Restricted Cone) or NAT3 (Port Restricted)
                    // Cannot distinguish without active test — assume NAT2 (Restricted Cone)
                    console.log('[NAT] Type: Cone NAT (NAT2) — consistent mapping (EIM)')
                    resolve(0.8)
                } else if (uniqueIPs.size > 1 && uniquePorts.size > 1) {
                    // Different IPs mapped to different ports = Address-Dependent Mapping
                    console.log('[NAT] Type: Symmetric NAT (NAT4) — different mapping per destination (ADM)')
                    resolve(0.25)
                } else {
                    // Same IP but different ports per destination = APDM
                    console.log('[NAT] Type: Port-Restricted NAT (NAT3) — ADM with port sensitivity')
                    resolve(0.6)
                }
            }

            pc.onicecandidate = (e) => {
                if (!e.candidate) { finish(); return }
                const c = e.candidate
                if (c.type === 'host' && c.address &&
                    !c.address.startsWith('192.') && !c.address.startsWith('10.') && !c.address.startsWith('172.')) {
                    hasHost = true
                }
                if (c.type === 'srflx' && c.address && c.port) {
                    mappings.push({ip: c.address, port: c.port})
                }
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
        localStream, remotePeers, relayLatencies, peerConnStates, broadcastSpeaking, mutedPeers, activeRoomId, videoEnabled, audioEnabled, voiceUsersByChannel, showVoiceChat,
        addRemotePeer, setRemoteStream, removeRemotePeer, clearAllPeers, createPeerConnection,
        hostId, forcedHostId, startCall, endCall, toggleVideo, toggleAudio, toggleScreenShare, screenSharing, screenShareVersion,
        speaking, remoteSpeaking, monitoring, setMonitoring,
        setVoiceUsers, getVoiceUsers, clearVoiceUsers,
        speakerEnabled, toggleSpeaker,
        echoCancellation, toggleEchoCancellation,
        noiseSuppression, toggleNoiseSuppression,
        noiseFxEnabled, toggleNoiseFx,
        rnnoiseEnabled, toggleRnnoise,
        micVolume, setMicVolume,
        speakerVolume, setSpeakerVolume,
        audioOutputs, currentOutputDevice, enumerateAudioOutputs, switchAudioOutput,
        audioInputs, currentAudioDevice, enumerateAudioDevices, switchAudioDevice,
        setSendSignaling: (fn: typeof sendSignaling) => {
            sendSignaling = fn
        },
        getIceServers,
    }
})
