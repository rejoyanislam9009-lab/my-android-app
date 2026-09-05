package com.globalcall.app.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebRtcCallEngine(
    context: Context,
    private val callId: String,
    private val uid: String,
    private val outgoing: Boolean,
    private val video: Boolean,
    private val onState: (String) -> Unit,
    private val onConnected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val db = FirebaseFirestore.getInstance()
    private val callRef = db.collection("calls").document(callId)
    private val sessionRef = callRef.collection("webrtc").document("session")
    private val iceRef = callRef.collection("iceCandidates")
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val eglBase = EglBase.create()
    private val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(true)
        .createAudioDeviceModule()
    private val factory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var sessionRegistration: ListenerRegistration? = null
    private var iceRegistration: ListenerRegistration? = null
    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private val seenRemoteIce = mutableSetOf<String>()
    private var started = false
    private var closed = false
    private var remoteDescriptionSet = false
    private var localOfferSent = false
    private var localAnswerSent = false
    private var cameraCaptureActive = false
    private var cameraEnabled = video
    private var usingFrontCamera = true
    private var userForcedSpeaker = false
    private var audioRoute = "earpiece"

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (userForcedSpeaker) return
            when {
                hasWiredOutput() -> routeToWired()
                hasBluetoothOutput() -> routeToBluetooth()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val routeGone = when (audioRoute) {
                "bluetooth" -> !hasBluetoothOutput()
                "wired" -> !hasWiredOutput()
                else -> false
            }
            if (routeGone) applyDefaultRoute()
        }
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (closed) return
        localRenderer?.let { old -> if (old !== renderer) runCatching { old.release() } }
        localRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(usingFrontCamera)
        renderer.setEnableHardwareScaler(true)
        videoTrack?.addSink(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (closed) return
        remoteRenderer?.let { old -> if (old !== renderer) runCatching { old.release() } }
        remoteRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(false)
        renderer.setEnableHardwareScaler(true)
        remoteVideoTrack?.addSink(renderer)
    }

    fun start() {
        if (started || closed) return
        started = true
        postState("Preparing microphone${if (video) " and camera" else ""}…")
        configureAudioRoute()
        runCatching { audioManager.registerAudioDeviceCallback(audioDeviceCallback, main) }

        runCatching {
            createLocalMedia()
            createPeerConnection()
            listenForSession()
            listenForIce()
            if (outgoing) createOffer()
        }.onFailure {
            postError(it.message ?: "Could not start the media connection")
        }
    }

    fun setMuted(muted: Boolean): Boolean {
        audioTrack?.setEnabled(!muted)
        return muted
    }

    fun setCameraEnabled(enabled: Boolean): Boolean {
        if (!video) return false
        cameraEnabled = enabled
        videoTrack?.setEnabled(enabled)
        if (enabled && !cameraCaptureActive) {
            runCatching {
                capturer?.startCapture(640, 480, 24)
                cameraCaptureActive = true
            }.onFailure { postError("Could not restart the camera") }
        } else if (!enabled && cameraCaptureActive) {
            runCatching {
                capturer?.stopCapture()
                cameraCaptureActive = false
            }
        }
        return cameraEnabled
    }

    fun switchCamera() {
        if (!video || !cameraEnabled) return
        capturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                usingFrontCamera = isFrontCamera
                main.post { localRenderer?.setMirror(isFrontCamera) }
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                postError(errorDescription ?: "Could not switch camera")
            }
        })
    }

    fun currentAudioRoute(): String = audioRoute

    fun availableAudioRoutes(): List<String> = buildList {
        if (hasEarpiece()) add("earpiece")
        add("speaker")
        if (hasBluetoothOutput()) add("bluetooth")
        if (hasWiredOutput()) add("wired")
    }.distinct()

    fun selectAudioRoute(route: String): String {
        userForcedSpeaker = route == "speaker"
        return when (route) {
            "speaker" -> routeToSpeaker()
            "bluetooth" -> if (hasBluetoothOutput()) routeToBluetooth() else applyDefaultRoute()
            "wired" -> if (hasWiredOutput()) routeToWired() else applyDefaultRoute()
            else -> routeToEarpiece()
        }
    }

    fun setSpeakerEnabled(enabled: Boolean): String {
        return if (enabled) selectAudioRoute("speaker") else {
            userForcedSpeaker = false
            applyDefaultRoute()
        }
    }

    private fun configureAudioRoute() {
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        userForcedSpeaker = false
        applyDefaultRoute()
    }

    private fun applyDefaultRoute(): String = when {
        hasWiredOutput() -> routeToWired()
        hasBluetoothOutput() -> routeToBluetooth()
        video -> routeToSpeaker()
        else -> routeToEarpiece()
    }

    private fun hasEarpiece(): Boolean = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
    }.getOrDefault(true)

    private fun hasBluetoothOutput(): Boolean = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }.getOrDefault(false)

    private fun hasWiredOutput(): Boolean = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }.getOrDefault(false)

    private fun routeToBluetooth(): String {
        val routed = runCatching {
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= 31) {
                val device = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                device != null && audioManager.setCommunicationDevice(device)
            } else {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                true
            }
        }.getOrDefault(false)
        audioRoute = if (routed) "bluetooth" else when {
            hasWiredOutput() -> routeToWired()
            video -> routeToSpeaker()
            else -> routeToEarpiece()
        }
        return audioRoute
    }

    private fun routeToWired(): String {
        val routed = runCatching {
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= 31) {
                val device = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                device != null && audioManager.setCommunicationDevice(device)
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                run { audioManager.isBluetoothScoOn = false }
                true
            }
        }.getOrDefault(false)
        audioRoute = if (routed && hasWiredOutput()) "wired" else if (video) routeToSpeaker() else routeToEarpiece()
        return audioRoute
    }

    private fun routeToSpeaker(): String {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) audioManager.setCommunicationDevice(speaker)
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                run { audioManager.isBluetoothScoOn = false }
            }
            audioManager.isSpeakerphoneOn = true
        }
        audioRoute = "speaker"
        return audioRoute
    }

    private fun routeToEarpiece(): String {
        runCatching {
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= 31) {
                val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                if (earpiece != null) audioManager.setCommunicationDevice(earpiece)
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                run { audioManager.isBluetoothScoOn = false }
            }
        }
        audioRoute = "earpiece"
        return audioRoute
    }

    private fun createLocalMedia() {
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("GC_AUDIO_$uid", audioSource).apply { setEnabled(true) }

        if (video) {
            val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(appContext)) {
                Camera2Enumerator(appContext)
            } else {
                Camera1Enumerator(true)
            }
            val device = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: error("No camera is available on this device")
            usingFrontCamera = enumerator.isFrontFacing(device)
            capturer = enumerator.createCapturer(device, null)
                ?: error("Could not open the camera")
            textureHelper = SurfaceTextureHelper.create("GlobalCallCamera", eglBase.eglBaseContext)
            videoSource = factory.createVideoSource(false)
            capturer?.initialize(textureHelper, appContext, videoSource?.capturerObserver)
            capturer?.startCapture(640, 480, 24)
            cameraCaptureActive = true
            videoTrack = factory.createVideoTrack("GC_VIDEO_$uid", videoSource).apply { setEnabled(true) }
            localRenderer?.let { videoTrack?.addSink(it) }
        }
    }

    private fun createPeerConnection() {
        val config = PeerConnection.RTCConfiguration(buildIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        peerConnection = requireNotNull(factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                when (newState) {
                    PeerConnection.IceConnectionState.CHECKING -> postState("Connecting secure media…")
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        postState("Connected")
                        main.post(onConnected)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> postState("Network changed • reconnecting…")
                    PeerConnection.IceConnectionState.FAILED -> postError("Could not establish the audio/video path. Check both phones' internet and retry.")
                    PeerConnection.IceConnectionState.CLOSED -> postState("Call ended")
                    else -> Unit
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        postState("Connected")
                        main.post(onConnected)
                    }
                    PeerConnection.PeerConnectionState.FAILED -> postError("Media connection failed. Please retry the call.")
                    PeerConnection.PeerConnectionState.DISCONNECTED -> postState("Reconnecting media…")
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                if (closed) return
                iceRef.add(
                    mapOf(
                        "uid" to uid,
                        "sdpMid" to candidate.sdpMid.orEmpty(),
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "candidate" to candidate.sdp,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(dataChannel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                attachRemoteTrack(receiver.track())
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                attachRemoteTrack(transceiver.receiver.track())
            }
        })) { "Could not create a WebRTC peer connection" }

        peerConnection?.setAudioPlayout(true)
        peerConnection?.setAudioRecording(true)
        audioTrack?.let { peerConnection?.addTrack(it, listOf("globalcall")) }
        videoTrack?.let { peerConnection?.addTrack(it, listOf("globalcall")) }
    }

    private fun attachRemoteTrack(track: MediaStreamTrack?) {
        if (track is VideoTrack) {
            remoteVideoTrack = track
            track.setEnabled(true)
            remoteRenderer?.let { track.addSink(it) }
        }
        if (track is AudioTrack) track.setEnabled(true)
    }

    private fun createOffer() {
        if (localOfferSent || closed) return
        localOfferSent = true
        postState("Creating secure media offer…")
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sessionRef.set(
                            mapOf(
                                "offerSdp" to description.description,
                                "offerBy" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        postState("Waiting for the other phone's media…")
                    }
                    override fun onSetFailure(error: String?) {
                        postError(error ?: "Could not activate microphone/camera")
                    }
                }, description)
            }
            override fun onCreateFailure(error: String?) {
                localOfferSent = false
                postError(error ?: "Could not create the call media offer")
            }
        }, MediaConstraints())
    }

    private fun createAnswer() {
        if (localAnswerSent || closed) return
        localAnswerSent = true
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sessionRef.set(
                            mapOf(
                                "answerSdp" to description.description,
                                "answerBy" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        postState("Opening secure media…")
                    }
                    override fun onSetFailure(error: String?) {
                        postError(error ?: "Could not answer the media connection")
                    }
                }, description)
            }
            override fun onCreateFailure(error: String?) {
                localAnswerSent = false
                postError(error ?: "Could not create the media answer")
            }
        }, MediaConstraints())
    }

    private fun listenForSession() {
        sessionRegistration = sessionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                postError("Media signaling is unavailable. Check Firestore rules and internet.")
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists() || closed) return@addSnapshotListener

            if (outgoing) {
                val answer = snapshot.getString("answerSdp").orEmpty()
                val answerBy = snapshot.getString("answerBy").orEmpty()
                if (answer.isNotBlank() && answerBy != uid && !remoteDescriptionSet) {
                    setRemoteDescription(SessionDescription.Type.ANSWER, answer) { }
                }
            } else {
                val offer = snapshot.getString("offerSdp").orEmpty()
                val offerBy = snapshot.getString("offerBy").orEmpty()
                if (offer.isNotBlank() && offerBy != uid && !remoteDescriptionSet) {
                    setRemoteDescription(SessionDescription.Type.OFFER, offer) { createAnswer() }
                }
            }
        }
    }

    private fun setRemoteDescription(type: SessionDescription.Type, sdp: String, after: () -> Unit) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                val queued = synchronized(pendingRemoteIce) {
                    pendingRemoteIce.toList().also { pendingRemoteIce.clear() }
                }
                queued.forEach { peerConnection?.addIceCandidate(it) }
                after()
            }
            override fun onSetFailure(error: String?) {
                postError(error ?: "Could not negotiate the remote media")
            }
        }, SessionDescription(type, sdp))
    }

    private fun listenForIce() {
        iceRegistration = iceRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || closed) return@addSnapshotListener
            snapshot.documentChanges.forEach { change ->
                val doc = change.document
                val fromUid = doc.getString("uid").orEmpty()
                if (fromUid.isBlank() || fromUid == uid || !seenRemoteIce.add(doc.id)) return@forEach
                val candidate = doc.getString("candidate").orEmpty()
                if (candidate.isBlank()) return@forEach
                val ice = IceCandidate(
                    doc.getString("sdpMid"),
                    (doc.getLong("sdpMLineIndex") ?: 0L).toInt(),
                    candidate
                )
                if (remoteDescriptionSet) peerConnection?.addIceCandidate(ice)
                else synchronized(pendingRemoteIce) { pendingRemoteIce += ice }
            }
        }
    }

    private fun buildIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        runCatching {
            val username = ((System.currentTimeMillis() / 1000L) + 86_400L).toString()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(OPEN_RELAY_SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
            val credential = Base64.encodeToString(
                mac.doFinal(username.toByteArray(StandardCharsets.UTF_8)),
                Base64.NO_WRAP
            )
            servers += PeerConnection.IceServer.builder(
                listOf(
                    "turn:staticauth.openrelay.metered.ca:80?transport=udp",
                    "turn:staticauth.openrelay.metered.ca:80?transport=tcp",
                    "turns:staticauth.openrelay.metered.ca:443?transport=tcp"
                )
            ).setUsername(username).setPassword(credential).createIceServer()
        }
        return servers
    }

    private fun postState(text: String) {
        if (!closed) main.post { if (!closed) onState(text) }
    }

    private fun postError(text: String) {
        if (!closed) main.post { if (!closed) onError(text) }
    }

    fun close() {
        if (closed) return
        closed = true
        sessionRegistration?.remove()
        iceRegistration?.remove()
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching { if (cameraCaptureActive) capturer?.stopCapture() }
        videoTrack?.let { track -> localRenderer?.let { runCatching { track.removeSink(it) } } }
        remoteVideoTrack?.let { track -> remoteRenderer?.let { runCatching { track.removeSink(it) } } }
        runCatching { capturer?.dispose() }
        runCatching { textureHelper?.dispose() }
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        runCatching { localRenderer?.release() }
        runCatching { remoteRenderer?.release() }
        runCatching { factory.dispose() }
        runCatching { audioDeviceModule.release() }
        runCatching { eglBase.release() }
        runCatching {
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
            else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                run { audioManager.isBluetoothScoOn = false }
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private const val OPEN_RELAY_SECRET = "openrelayprojectsecret"
    }
}
