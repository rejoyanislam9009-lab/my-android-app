package com.globalcall.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.CallSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.BroadcastEvent
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import java.net.URL

@Composable
fun CallScreen(session: CallSession, repository: GlobalCallRepository?, onFinish: (Boolean) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val instant = session.callId.startsWith("instant-") || repository == null
    val roomCode = remember(session.token) { session.token.removePrefix("GlobalCall-") }
    var launched by remember(session.callId) { mutableStateOf(false) }
    var joined by remember(session.callId) { mutableStateOf(false) }
    var callStatus by remember(session.callId) {
        mutableStateOf(if (instant || !session.outgoing) "accepted" else "ringing")
    }
    var launchError by remember(session.callId) { mutableStateOf<String?>(null) }
    var finished by remember(session.callId) { mutableStateOf(false) }
    var permissionsGranted by remember(session.callId) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!session.video || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        )
    }

    fun finish(updateServer: Boolean) {
        if (!finished) {
            finished = true
            onFinish(updateServer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val camera = !session.video || (result[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
        permissionsGranted = mic && camera
        if (!permissionsGranted) launchError = "Camera and microphone permission are required for this call."
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

    // Give the caller audible feedback while the remote user is actually ringing.
    // The player is disposed immediately when the call is accepted, declined or cancelled.
    DisposableEffect(session.callId, callStatus, session.outgoing) {
        val ringback = if (!instant && session.outgoing && callStatus == "ringing" && !finished) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                MediaPlayer.create(context, uri)?.apply {
                    isLooping = true
                    setVolume(0.28f, 0.28f)
                    start()
                }
            }.getOrNull()
        } else null

        onDispose {
            runCatching { ringback?.stop() }
            runCatching { ringback?.release() }
        }
    }

    DisposableEffect(session.callId) {
        val filter = IntentFilter().apply {
            addAction(BroadcastEvent.Type.CONFERENCE_WILL_JOIN.action)
            addAction(BroadcastEvent.Type.CONFERENCE_JOINED.action)
            addAction(BroadcastEvent.Type.CONFERENCE_TERMINATED.action)
            addAction(BroadcastEvent.Type.READY_TO_CLOSE.action)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent == null) return
                when (BroadcastEvent(intent).type) {
                    BroadcastEvent.Type.CONFERENCE_JOINED -> joined = true
                    BroadcastEvent.Type.CONFERENCE_TERMINATED, BroadcastEvent.Type.READY_TO_CLOSE -> finish(true)
                    else -> Unit
                }
            }
        }
        val manager = LocalBroadcastManager.getInstance(context)
        manager.registerReceiver(receiver, filter)
        onDispose { manager.unregisterReceiver(receiver) }
    }

    fun launchNativeConference() {
        if (launched || finished || !permissionsGranted) return
        if (!instant && session.outgoing && callStatus != "accepted") return
        runCatching {
            val options = JitsiMeetConferenceOptions.Builder()
                .setServerURL(URL(session.serverUrl))
                .setRoom(session.token)
                .setAudioMuted(false)
                .setVideoMuted(false)
                .setAudioOnly(!session.video)
                .setFeatureFlag("prejoinpage.enabled", false)
                .setFeatureFlag("welcomepage.enabled", false)
                .setFeatureFlag("pip.enabled", true)
                .setFeatureFlag("invite.enabled", false)
                .setFeatureFlag("calendar.enabled", false)
                .setFeatureFlag("recording.enabled", false)
                .setFeatureFlag("live-streaming.enabled", false)
                .setFeatureFlag("video-mute.enabled", true)
                .setFeatureFlag("camera-facing-mode", "user")
                .build()
            launched = true
            JitsiMeetActivity.launch(context, options)
        }.onFailure {
            launched = false
            launchError = it.message ?: "Unable to start secure call"
        }
    }

    LaunchedEffect(session.callId, permissionsGranted, callStatus) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (session.video) add(Manifest.permission.CAMERA)
                }.toTypedArray()
            )
        } else if (instant || !session.outgoing || callStatus == "accepted") {
            launchNativeConference()
        }
    }

    LaunchedEffect(session.callId, callStatus) {
        if (!instant && session.outgoing && callStatus == "ringing" && repository != null) {
            delay(45_000)
            runCatching { repository.endCall(session.callId) }
            finish(false)
        }
    }

    val heading = when {
        joined -> if (session.video) "Video call connected" else "Voice call connected"
        !permissionsGranted -> "Permission required"
        !instant && session.outgoing && callStatus == "ringing" -> "Calling ${session.peerName.ifBlank { "contact" }}…"
        callStatus == "accepted" -> "Connecting secure call…"
        else -> "Starting secure call…"
    }

    val subheading = when {
        !instant && session.outgoing && callStatus == "ringing" -> "Ringing • waiting for answer"
        !instant && callStatus == "accepted" && !joined -> "Answered • opening secure media"
        instant -> "Private room $roomCode"
        else -> session.peerName.ifBlank { "GlobalCall contact" }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF070A10), Color(0xFF101827), Color(0xFF06080D)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(104.dp), CircleShape, color = Color(0xFF1D2940)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (session.video) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(heading, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                subheading,
                color = Color.White.copy(alpha = .68f),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(18.dp))

            if (launchError == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171C26))) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(launchError ?: "Unable to start call", color = Color(0xFFFFB4AB))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            launchError = null
                            if (!permissionsGranted) {
                                permissionLauncher.launch(
                                    buildList {
                                        add(Manifest.permission.RECORD_AUDIO)
                                        if (session.video) add(Manifest.permission.CAMERA)
                                    }.toTypedArray()
                                )
                            } else {
                                launchNativeConference()
                            }
                        }) { Text("Allow / Try again") }
                    }
                }
            }

            if (instant) {
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Share room code", color = Color.White.copy(alpha = .65f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(roomCode)) }) {
                        Icon(Icons.Default.ContentCopy, "Copy room code", tint = Color.White)
                    }
                }
            }

            if (!instant && session.outgoing && callStatus == "ringing") {
                Spacer(Modifier.height(28.dp))
                FilledIconButton(
                    onClick = {
                        scope.launch {
                            repository?.endCall(session.callId)
                            finish(false)
                        }
                    },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF3B30))
                ) {
                    Icon(Icons.Default.CallEnd, "Cancel call", modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
