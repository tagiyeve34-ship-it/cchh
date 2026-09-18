package com.hesabat.twopersonmessenger

import android.content.Context
import org.webrtc.*

class WebRtcClient(
    private val context: Context,
    private val video: Boolean,
    iceDtos: List<IceServerDto>,
    private val onIce: (IceCandidate) -> Unit,
    private val onState: (String) -> Unit
) {
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    val eglContext get() = egl.eglBaseContext

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()

        val iceServers = iceDtos.flatMap { dto ->
            if (dto.urls.isEmpty()) emptyList() else listOf(
                PeerConnection.IceServer.builder(dto.urls)
                    .setUsername(dto.username ?: "")
                    .setPassword(dto.credential ?: "")
                    .createIceServer()
            )
        }.ifEmpty { listOf(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()) }

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) = onIce(c)
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) = onState(s.name)
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) = onState(s.name)
            override fun onTrack(t: RtpTransceiver) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(v: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, streams: Array<out MediaStream>) {}
        })
        createLocalTracks()
    }

    private fun createLocalTracks() {
        val audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("audio0", audioSource).also { peer?.addTrack(it, listOf("stream0")) }
        if (video) {
            val enumerator = Camera2Enumerator(context)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
            if (name != null) {
                capturer = enumerator.createCapturer(name, null)
                val source = factory.createVideoSource(false)
                surfaceHelper = SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext)
                capturer?.initialize(surfaceHelper, context, source.capturerObserver)
                capturer?.startCapture(640, 480, 24)
                videoTrack = factory.createVideoTrack("video0", source).also { peer?.addTrack(it, listOf("stream0")) }
            }
        }
    }

    fun attachLocal(renderer: SurfaceViewRenderer) {
        renderer.init(egl.eglBaseContext, null); renderer.setMirror(true); renderer.setEnableHardwareScaler(true)
        videoTrack?.addSink(renderer)
    }
    fun attachRemote(renderer: SurfaceViewRenderer) {
        renderer.init(egl.eglBaseContext, null); renderer.setMirror(false); renderer.setEnableHardwareScaler(true)
        peer?.transceivers?.firstOrNull { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }?.receiver?.track()?.let { (it as? VideoTrack)?.addSink(renderer) }
    }
    fun refreshRemote(renderer: SurfaceViewRenderer) {
        peer?.receivers?.mapNotNull { it.track() as? VideoTrack }?.firstOrNull()?.addSink(renderer)
    }

    fun createOffer(cb:(SessionDescription)->Unit) {
        peer?.createOffer(object: SimpleSdpObserver(){ override fun onCreateSuccess(s:SessionDescription){ peer?.setLocalDescription(SimpleSdpObserver(),s); cb(s)}}, MediaConstraints())
    }
    fun createAnswer(cb:(SessionDescription)->Unit) {
        peer?.createAnswer(object: SimpleSdpObserver(){ override fun onCreateSuccess(s:SessionDescription){ peer?.setLocalDescription(SimpleSdpObserver(),s); cb(s)}}, MediaConstraints())
    }
    fun setRemote(type:String, sdp:String, done:()->Unit={}) {
        val t=if(type=="offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        peer?.setRemoteDescription(object:SimpleSdpObserver(){override fun onSetSuccess(){done()}}, SessionDescription(t,sdp))
    }
    fun addIce(mid:String?, index:Int, sdp:String){ peer?.addIceCandidate(IceCandidate(mid,index,sdp)) }
    fun mute(muted:Boolean){ audioTrack?.setEnabled(!muted) }
    fun camera(enabled:Boolean){ videoTrack?.setEnabled(enabled) }
    fun switchCamera(){ (capturer as? CameraVideoCapturer)?.switchCamera(null) }
    fun close(){ runCatching{capturer?.stopCapture()}; capturer?.dispose(); surfaceHelper?.dispose(); peer?.close(); peer?.dispose(); factory.dispose(); egl.release() }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(s: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String) {}
    override fun onSetFailure(e: String) {}
}
