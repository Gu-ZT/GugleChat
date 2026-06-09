package dev.dubhe.gugle.chat.android.data

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class VoiceCallManager(
    private val ctx: Context,
    private val signal: (String, Map<String, Any>) -> Unit,
) {
    private var factory: PeerConnectionFactory? = null
    private var localStream: MediaStream? = null
    private val peers = mutableMapOf<Long, PeerConnection>()
    private var audioManager: AudioManager =
        ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var inCall = false
    private var roomId: Long? = null
    private var onRemoteStream: ((MediaStream?) -> Unit)? = null

    fun setOnRemoteStream(cb: (MediaStream?) -> Unit) {
        onRemoteStream = cb
    }

    fun startCall(roomId: Long) {
        if (inCall) endCall()
        this.roomId = roomId
        inCall = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val options = PeerConnectionFactory.InitializationOptions.builder(ctx)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(ctx).createAudioDeviceModule())
            .createPeerConnectionFactory()

        val audioSource = factory!!.createAudioSource(MediaConstraints())
        val audioTrack = factory!!.createAudioTrack("audio", audioSource)
        localStream = factory!!.createLocalMediaStream("local")
        localStream!!.addTrack(audioTrack)

        signal("rtc.join/$roomId", mapOf("quality" to 1.0))
    }

    fun onRtcMessage(type: String, data: Map<String, Any>) {
        when (type) {
            "room-users" -> {
                val users = data["users"] as? List<Map<String, Any>> ?: return
                val hostId = data["hostId"] as? Double ?: return
                if (hostId.toLong() == 0L) return // we're first, wait
                for (u in users) {
                    val uid = (u["userId"] as Double).toLong()
                    if (uid != 0L) createOffer(uid)
                }
            }

            "user-joined" -> {
                val uid = (data["userId"] as Double).toLong()
                createOffer(uid)
            }

            "user-left" -> {
                val uid = (data["userId"] as Double).toLong()
                peers[uid]?.close(); peers.remove(uid)
            }

            "offer" -> handleOffer(data)
            "answer" -> handleAnswer(data)
            "ice-candidate" -> handleIce(data)
        }
    }

    private fun createOffer(targetId: Long) {
        val pc = createPeer(targetId)
        pc.createOffer(object : SdpObserverBase() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SdpObserverBase(), sdp)
                signal("rtc.offer", mapOf("target" to targetId, "sdp" to sdpToMap(sdp)))
            }
        }, MediaConstraints())
    }

    private fun handleOffer(data: Map<String, Any>) {
        val senderId = (data["userId"] as Double).toLong()
        val sdpStr = data["sdp"] as? String ?: return
        val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
        val pc = createPeer(senderId)
        pc.setRemoteDescription(SdpObserverBase(), sdp)
        pc.createAnswer(object : SdpObserverBase() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SdpObserverBase(), sdp)
                signal("rtc.answer", mapOf("target" to senderId, "sdp" to sdpToMap(sdp)))
            }
        }, MediaConstraints())
    }

    private fun handleAnswer(data: Map<String, Any>) {
        val senderId = (data["userId"] as Double).toLong()
        val sdpStr = data["sdp"] as? String ?: return
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
        peers[senderId]?.setRemoteDescription(SdpObserverBase(), sdp)
    }

    private fun handleIce(data: Map<String, Any>) {
        val senderId = (data["userId"] as Double).toLong()
        val candMap = data["candidate"] as? Map<String, Any> ?: return
        val candidate = IceCandidate(
            candMap["sdpMid"] as? String ?: "",
            (candMap["sdpMLineIndex"] as? Double)?.toInt() ?: 0,
            candMap["candidate"] as? String ?: ""
        )
        peers[senderId]?.addIceCandidate(candidate)
    }

    private fun createPeer(targetId: Long): PeerConnection {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val pc = factory!!.createPeerConnection(iceServers, object : PeerConnectionObserverBase() {
            override fun onIceCandidate(candidate: IceCandidate) {
                signal(
                    "rtc.ice-candidate", mapOf(
                        "target" to targetId, "candidate" to mapOf(
                            "sdpMid" to candidate.sdpMid,
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "candidate" to candidate.sdp
                        )
                    )
                )
            }

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                if (streams.isNotEmpty()) onRemoteStream?.invoke(streams[0])
            }
        })!!
        localStream?.videoTracks?.forEach { pc.addTrack(it, listOf("local")) }
        localStream?.audioTracks?.forEach { pc.addTrack(it, listOf("local")) }
        peers[targetId] = pc
        return pc
    }

    fun endCall() {
        roomId?.let { signal("rtc.leave/$it", mapOf()) }
        peers.values.forEach { it.close() }; peers.clear()
        localStream?.dispose(); localStream = null
        factory?.dispose(); factory = null
        inCall = false; roomId = null
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun sdpToMap(sdp: SessionDescription) =
        "${sdp.type.canonicalForm()}\n${sdp.description}"

    open class SdpObserverBase : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String) {
            Log.e("RTC", "SDP fail: $p0")
        }

        override fun onSetFailure(p0: String) {
            Log.e("RTC", "SDP set fail: $p0")
        }
    }

    open class PeerConnectionObserverBase : PeerConnection.Observer {
        override fun onIceCandidate(p0: IceCandidate) {}
        override fun onAddStream(p0: MediaStream) {}
        override fun onRemoveStream(p0: MediaStream) {}
        override fun onDataChannel(p0: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState) {
            Log.d("RTC", "ICE: $p0")
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
        override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
        override fun onAddTrack(p0: RtpReceiver, p1: Array<out MediaStream>) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}
    }
}
