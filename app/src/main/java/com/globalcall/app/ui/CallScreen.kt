package com.globalcall.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.media.WebRtcCallEngine
import com.globalcall.app.model.CallSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    session: CallSession,
    repository: GlobalCallRepository?,
    onFinish: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val instant = session.callId.startsWith("instant-") || repository == null

    var callStatus by remember(session.callId) {
        mutableStateOf(if (instant || !session.outgoing) "accepted" else "ringing")
    }
    var mediaState by remember(session.callId) { mutableStateOf("Preparing secure media…") }
    var mediaError by remember(session.callId) { mutableStateOf<String?>(null) }
    var connected by remember(session.callId) { mutableStateOf(false) }
    var elapsedSeconds by remember(session.callId) { mutableIntStateOf(0) }
    var finished by remember(session.callId) { mutableStateOf(false) }
    var engine by remember(session.callId) { mutableStateOf<WebRtcCallEngine?>(null) }
    var muted by remember(session.callId) { mutableStateOf(false) }
    var cameraOn by remember(session.callId) { mutableStateOf(session.video) }
    var audioRoute by remember(session.callId) { mutableStateOf(if (session.video) "speaker" else "earpiece") }

    var permissionsGranted by remember(session.callId) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!session.video || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        )
    }

    fun finish(updateServer: Boolean) {
        if (!finished) {
            finished = true
            engine?.close()
            engine = null
            onFinish(updateServer)
        }
    }

    DisposableEffect(activity, session.callId) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            engine?.close()
        }
    }

    // Audio calls behave like a normal phone call: while this wake lock is held,
    // Android's proximity sensor turns the screen off near the ear and back on
    // when the phone is moved away.
    DisposableEffect(callStatus, session.video, finished) {
        val wakeLock = if (!session.video && callStatus == "accepted" && !finished) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                runCatching {
                    power.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "GlobalCall:audioProximity"
                    ).apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }.getOrNull()
            } else null
        } else null
        onDispose {
            if (wakeLock?.isHeld == true) runCatching { wakeLock.release() }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val camera = !session.video || (result[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
        permissionsGranted = mic && camera
        if (!permissionsGranted) {
            mediaError = if (session.video) {
                "Camera and microphone permission are required for a video call."
            } else {
                "Microphone permission is required for a voice call."
            }
        }
    }

    DisposableEffect(session.callId, repository) {
        if (instant || repository == null) {
            onDispose { }
        } else {
            val registration = repository.observeCallStatus(session.callId) { status ->
                callStatus = status
                if (status == "declined" || status == "ended") finish(false)
            }
            onDispose { registration.remove() }
        }
    }

    DisposableEffect(session.callId, callStatus, session.outgoing) {
        val ringback = if (!instant && session.outgoing && callStatus == "ringing" && !finished) {
            runCatching {
                MediaPlayer.create(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                    isLooping = true
                    setVolume(0.20f, 0.20f)
                    start()
                }
            }.getOrNull()
        } else null
        onDispose {
            runCatching { ringback?.stop() }
            runCatching { ringback?.release() }
        }
    }

    LaunchedEffect(session.callId, permissionsGranted, callStatus) {
        if (instant) return@LaunchedEffect

        if (!permissionsGranted) {
            permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (session.video) add(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                }.toTypedArray()
            )
            return@LaunchedEffect
        }

        if (callStatus == "accepted" && engine == null && repository != null) {
            val uid = repository.currentUid
            if (uid.isNullOrBlank()) {
                mediaError = "Your account session expired. Sign in again."
                return@LaunchedEffect
            }

            mediaError = null
            engine = WebRtcCallEngine(
                context = context,
                callId = session.callId,
                uid = uid,
                outgoing = session.outgoing,
                video = session.video,
                onState = { mediaState = it },
                onConnected = { connected = true; mediaState = "Connected" },
                onError = { mediaError = it; mediaState = "Media connection failed" }
            ).also {
                it.start()
                muted = false
                cameraOn = session.video
                audioRoute = it.currentAudioRoute()
            }
        }
    }

    LaunchedEffect(engine, finished) {
        while (engine != null && !finished) {
            audioRoute = engine?.currentAudioRoute() ?: audioRoute
            delay(600)
        }
    }

    LaunchedEffect(session.callId, callStatus) {
        if (!instant && session.outgoing && callStatus == "ringing" && repository != null) {
            delay(45_000)
            if (callStatus == "ringing" && !finished) {
                runCatching { repository.endCall(session.callId) }
                finish(false)
            }
        }
    }

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        elapsedSeconds = 0
        while (connected && !finished) {
            delay(1_000)
            elapsedSeconds++
        }
    }

    if (instant) {
        UnsupportedInstantCallScreen(onFinish = { finish(false) })
        return
    }

    val accepted = callStatus == "accepted"
    val duration = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    val bg = Brush.verticalGradient(listOf(Color(0xFF03060B), Color(0xFF111A2A), Color(0xFF05070C)))

    Box(Modifier.fillMaxSize().background(bg)) {
        if (accepted && session.video) {
            engine?.let { currentEngine ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> SurfaceViewRenderer(ctx).also { currentEngine.attachRemoteRenderer(it) } }
                )

                if (cameraOn) {
                    AndroidView(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp, end = 18.dp)
                            .size(width = 116.dp, height = 158.dp)
                            .clip(RoundedCornerShape(22.dp)),
                        factory = { ctx -> SurfaceViewRenderer(ctx).also { currentEngine.attachLocalRenderer(it) } }
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp, end = 18.dp)
                            .size(width = 116.dp, height = 158.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xDD151B26)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.VideocamOff, null, tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(34.dp))
                        }
                    }
                }
            }
        }

        if (!accepted || !session.video) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(modifier = Modifier.size(126.dp), shape = CircleShape, color = Color(0xFF1A2A45)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (session.video) Icons.Default.Videocam else Icons.Default.Call,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    when {
                        callStatus == "ringing" -> "Calling"
                        connected -> if (session.video) "Video call" else "Voice call"
                        else -> "Connecting"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    session.peerName.ifBlank { "GlobalCall contact" },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        callStatus == "ringing" -> "Ringing • waiting for answer"
                        connected -> duration
                        else -> mediaState
                    },
                    color = Color.White.copy(alpha = .68f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                if (accepted && !session.video) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (audioRoute) {
                            "bluetooth" -> "Bluetooth audio"
                            "speaker" -> "Speaker on"
                            else -> "Earpiece • proximity sensor active"
                        },
                        color = Color.White.copy(alpha = .48f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (accepted && session.video) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 58.dp, start = 20.dp, end = 150.dp)
            ) {
                Text(
                    session.peerName.ifBlank { "GlobalCall contact" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    if (connected) "$duration • ${audioRoute.replaceFirstChar { it.uppercase() }}" else mediaState,
                    color = Color.White.copy(alpha = .74f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        mediaError?.let { error ->
            Card(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2171D2A)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Call connection problem", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color(0xFFFFC7C2), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        engine?.close()
                        engine = null
                        mediaError = null
                    }) { Text("Retry media") }
                }
            }
        }

        if (accepted) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 22.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xE61A1F29),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlButton(
                        selected = muted,
                        icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (muted) "Unmute" else "Mute"
                    ) {
                        muted = !(muted)
                        engine?.setMuted(muted)
                    }

                    CallControlButton(
                        selected = audioRoute == "speaker",
                        icon = if (audioRoute == "speaker") Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        label = when (audioRoute) {
                            "bluetooth" -> "Bluetooth"
                            "speaker" -> "Speaker"
                            else -> "Audio"
                        }
                    ) {
                        audioRoute = engine?.setSpeakerEnabled(audioRoute != "speaker") ?: audioRoute
                    }

                    if (session.video) {
                        CallControlButton(
                            selected = !cameraOn,
                            icon = if (cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = if (cameraOn) "Camera" else "Camera off"
                        ) {
                            cameraOn = engine?.setCameraEnabled(!cameraOn) ?: cameraOn
                        }
                        CallControlButton(
                            selected = false,
                            icon = Icons.Default.Cameraswitch,
                            label = "Flip",
                            enabled = cameraOn
                        ) { engine?.switchCamera() }
                    }

                    FilledIconButton(
                        onClick = {
                            scope.launch {
                                engine?.close()
                                engine = null
                                repository?.endCall(session.callId)
                                finish(false)
                            }
                        },
                        modifier = Modifier.size(58.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF3B30))
                    ) {
                        Icon(Icons.Default.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        } else {
            FilledIconButton(
                onClick = {
                    scope.launch {
                        repository?.endCall(session.callId)
                        finish(false)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp).size(74.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF3B30))
            ) {
                Icon(Icons.Default.CallEnd, "End call", modifier = Modifier.size(34.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun CallControlButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (selected) Color.White else Color(0xFF303744),
                contentColor = if (selected) Color(0xFF0A0D12) else Color.White,
                disabledContainerColor = Color(0xFF242A34),
                disabledContentColor = Color.White.copy(alpha = .35f)
            )
        ) { Icon(icon, label, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = if (enabled) .75f else .35f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun UnsupportedInstantCallScreen(onFinish: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080C14)) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(20.dp))
            Text("Sign in for real calling", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "GlobalCall uses account-to-account native WebRTC for reliable voice and video calls.",
                color = Color.White.copy(alpha = .7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onFinish) { Text("Back") }
        }
    }
}
