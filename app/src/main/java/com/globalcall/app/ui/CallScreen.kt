package com.globalcall.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.CallSession
import org.jitsi.meet.sdk.BroadcastEvent
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import java.net.URL

@Composable
fun CallScreen(
    session: CallSession,
    repository: GlobalCallRepository?,
    onFinish: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val roomCode = remember(session.token) { session.token.removePrefix("GlobalCall-") }
    var launched by remember(session.callId) { mutableStateOf(false) }
    var joined by remember(session.callId) { mutableStateOf(false) }
    var launchError by remember(session.callId) { mutableStateOf<String?>(null) }
    var finished by remember(session.callId) { mutableStateOf(false) }

    fun finish(updateServer: Boolean) {
        if (!finished) {
            finished = true
            onFinish(updateServer)
        }
    }

    DisposableEffect(session.callId, repository) {
        if (session.callId.startsWith("instant-") || repository == null) {
            onDispose { }
        } else {
            val registration = repository.observeCallStatus(session.callId) { status ->
                if (status == "declined" || status == "ended") finish(false)
            }
            onDispose { registration.remove() }
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
                val event = BroadcastEvent(intent)
                when (event.type) {
                    BroadcastEvent.Type.CONFERENCE_JOINED -> joined = true
                    BroadcastEvent.Type.CONFERENCE_TERMINATED,
                    BroadcastEvent.Type.READY_TO_CLOSE -> finish(true)
                    else -> Unit
                }
            }
        }
        val manager = LocalBroadcastManager.getInstance(context)
        manager.registerReceiver(receiver, filter)
        onDispose { manager.unregisterReceiver(receiver) }
    }

    fun launchNativeConference() {
        if (launched || finished) return
        runCatching {
            val options = JitsiMeetConferenceOptions.Builder()
                .setServerURL(URL(session.serverUrl))
                .setRoom(session.token)
                .setAudioMuted(false)
                .setVideoMuted(!session.video)
                .setAudioOnly(!session.video)
                .setFeatureFlag("prejoinpage.enabled", false)
                .setFeatureFlag("welcomepage.enabled", false)
                .setFeatureFlag("pip.enabled", true)
                .setFeatureFlag("invite.enabled", false)
                .setFeatureFlag("calendar.enabled", false)
                .setFeatureFlag("recording.enabled", false)
                .setFeatureFlag("live-streaming.enabled", false)
                .build()
            launched = true
            JitsiMeetActivity.launch(context, options)
        }.onFailure {
            launched = false
            launchError = it.message ?: "Unable to start camera session"
        }
    }

    LaunchedEffect(session.callId) {
        launchNativeConference()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070A10), Color(0xFF101827), Color(0xFF06080D))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = Color(0xFF1D2940)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (session.video) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                if (joined) "Live call connected" else "Starting secure call…",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Room $roomCode",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(18.dp))

            if (launchError == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171C26))
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            launchError ?: "Unable to start call",
                            color = Color(0xFFFFB4AB)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            launchError = null
                            launchNativeConference()
                        }) { Text("Try again") }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Share room code", color = Color.White.copy(alpha = 0.65f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(roomCode)) }) {
                    Icon(Icons.Default.ContentCopy, "Copy room code", tint = Color.White)
                }
            }
        }
    }
}
