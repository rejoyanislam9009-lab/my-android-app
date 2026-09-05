package com.globalcall.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
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
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
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
        val ringback: MediaPlayer? = if (!instant && session.outgoing && callStatus == "ringing" && !finished) {
            runCatching {
                MediaPlayer.create(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                    isLooping = true
                    setVolume(0.22f, 0.22f)
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
            ).also { it.start() }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050810), Color(0xFF101827), Color(0xFF05070C))))
    ) {
        if (accepted && session.video) {
            engine?.let { currentEngine ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also { currentEngine.attachRemoteRenderer(it) }
                    }
                )

                AndroidView(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 30.dp, end = 18.dp)
                        .size(width = 112.dp, height = 154.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also { currentEngine.attachLocalRenderer(it) }
                    }
                )
            }
        }

        if (!accepted || !session.video) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(122.dp),
                    shape = CircleShape,
                    color = Color(0xFF1B2942)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (session.video) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
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
            }
        }

        if (accepted && session.video) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 34.dp, start = 20.dp, end = 145.dp)
            ) {
                Text(
                    session.peerName.ifBlank { "GlobalCall contact" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    if (connected) duration else mediaState,
                    color = Color.White.copy(alpha = .74f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        mediaError?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE171D2A)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Call connection problem", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color(0xFFFFC7C2), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        engine?.close()
                        engine = null
                        mediaError = null
                        if (accepted && permissionsGranted) {
                            val uid = repository?.currentUid
                            if (!uid.isNullOrBlank()) {
                                engine = WebRtcCallEngine(
                                    context = context,
                                    callId = session.callId,
                                    uid = uid,
                                    outgoing = session.outgoing,
                                    video = session.video,
                                    onState = { mediaState = it },
                                    onConnected = { connected = true; mediaState = "Connected" },
                                    onError = { mediaError = it }
                                ).also { it.start() }
                            }
                        }
                    }) { Text("Retry media") }
                }
            }
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 46.dp)
                .size(74.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF3B30))
        ) {
            Icon(Icons.Default.CallEnd, "End call", modifier = Modifier.size(34.dp), tint = Color.White)
        }

        if (accepted && !session.video) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 54.dp)
                    .size(58.dp),
                shape = CircleShape,
                color = Color(0xFF202B3D)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mic, null, tint = Color.White)
                }
            }
        }
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
                "GlobalCall now uses account-to-account native WebRTC so calls can ring, reconnect and identify the correct person. Instant anonymous rooms are disabled in this build.",
                color = Color.White.copy(alpha = .7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onFinish) { Text("Back") }
        }
    }
}
