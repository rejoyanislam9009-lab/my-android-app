package com.globalcall.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.globalcall.app.R
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.CallSession
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    session: CallSession,
    repository: GlobalCallRepository,
    onFinish: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = remember(session.callId) { LiveKit.create(context.applicationContext) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var localVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var micEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(session.video) }
    var speakerEnabled by remember { mutableStateOf(session.video) }
    var callStatus by remember { mutableStateOf(if (session.outgoing) "ringing" else "accepted") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    fun finish(updateServer: Boolean) {
        if (!finished) {
            finished = true
            room.disconnect()
            onFinish(updateServer)
        }
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var permissionsGranted by remember {
        mutableStateOf(
            hasPermission(Manifest.permission.RECORD_AUDIO) &&
                (!session.video || hasPermission(Manifest.permission.CAMERA))
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO] ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        val camera = !session.video ||
            (result[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA))
        permissionsGranted = mic && camera
    }

    DisposableEffect(session.callId) {
        val registration = repository.observeCallStatus(session.callId) { status ->
            callStatus = status
            if (status == "declined" || status == "ended") finish(false)
        }
        onDispose { registration.remove() }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            val permissions = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (session.video) add(Manifest.permission.CAMERA)
            }.toTypedArray()
            permissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(permissionsGranted, session.callId) {
        if (!permissionsGranted) return@LaunchedEffect
        try {
            launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> {
                            if (event.track is VideoTrack) remoteVideo = event.track as VideoTrack
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            if (event.track == remoteVideo) remoteVideo = null
                        }
                        else -> Unit
                    }
                }
            }
            room.connect(session.serverUrl, session.token)
            room.localParticipant.setMicrophoneEnabled(true)
            room.localParticipant.setCameraEnabled(session.video)
            if (session.video) {
                localVideo = room.localParticipant
                    .getTrackPublication(Track.Source.CAMERA)
                    ?.track as? VideoTrack
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerEnabled
            connected = true
        } catch (t: Throwable) {
            error = t.message ?: "Call connection failed"
        }
    }

    LaunchedEffect(callStatus) {
        if (callStatus == "accepted") {
            while (true) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }

    DisposableEffect(room) {
        onDispose {
            room.disconnect()
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF090B10))) {
        if (session.video && remoteVideo != null) {
            VideoSurface(room, remoteVideo, Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(session.peerName, 110)
                Spacer(Modifier.height(18.dp))
                Text(
                    session.peerName,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        error != null -> error!!
                        !connected -> stringResource(R.string.connecting)
                        callStatus == "ringing" -> stringResource(R.string.ringing)
                        else -> formatDuration(elapsedSeconds)
                    },
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        if (session.video && localVideo != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 14.dp)
                    .width(112.dp)
                    .height(160.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                VideoSurface(room, localVideo, Modifier.fillMaxSize())
            }
        }

        if (session.video && remoteVideo != null) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 28.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    session.peerName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (callStatus == "ringing") stringResource(R.string.ringing)
                    else formatDuration(elapsedSeconds),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            color = Color(0xCC1B1E26),
            shape = RoundedCornerShape(30.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    if (micEnabled) stringResource(R.string.mute) else stringResource(R.string.unmute)
                ) {
                    scope.launch {
                        micEnabled = !micEnabled
                        room.localParticipant.setMicrophoneEnabled(micEnabled)
                    }
                }

                CallControlButton(
                    if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    stringResource(R.string.speaker)
                ) {
                    speakerEnabled = !speakerEnabled
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = speakerEnabled
                }

                if (session.video) {
                    CallControlButton(
                        if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        stringResource(R.string.camera)
                    ) {
                        scope.launch {
                            cameraEnabled = !cameraEnabled
                            room.localParticipant.setCameraEnabled(cameraEnabled)
                            localVideo = if (cameraEnabled) {
                                room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                            } else null
                        }
                    }
                }

                FilledIconButton(
                    onClick = { finish(true) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        stringResource(R.string.end_call),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(icon, label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun VideoSurface(room: Room, track: VideoTrack?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember(room) {
        SurfaceViewRenderer(context).also { room.initVideoRenderer(it) }
    }

    DisposableEffect(track, renderer) {
        track?.addRenderer(renderer)
        onDispose { track?.removeRenderer(renderer) }
    }

    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }

    AndroidView(factory = { renderer }, modifier = modifier)
}

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
