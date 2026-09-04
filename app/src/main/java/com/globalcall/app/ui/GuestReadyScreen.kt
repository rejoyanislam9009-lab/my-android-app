package com.globalcall.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.globalcall.app.BuildConfig
import com.globalcall.app.model.CallSession
import java.util.UUID

@Composable
fun GuestReadyScreen(
    onJoinCall: (CallSession) -> Unit,
    onSignIn: () -> Unit
) {
    var roomCode by remember { mutableStateOf("") }

    fun openRoom(code: String, video: Boolean) {
        val clean = code.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "").take(32)
        val finalCode = clean.ifBlank { UUID.randomUUID().toString().replace("-", "").take(10).uppercase() }
        onJoinCall(
            CallSession(
                callId = "instant-$finalCode",
                peerUid = "",
                peerName = "Room $finalCode",
                serverUrl = BuildConfig.MEETING_BASE_URL,
                token = "GlobalCall-$finalCode",
                video = video,
                outgoing = true
            )
        )
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Videocam,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text("GlobalCall", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Instant voice & video calling", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Create a new room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { openRoom("", true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Videocam, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Video")
                        }
                        OutlinedButton(onClick = { openRoom("", false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Call, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Voice")
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.uppercase().take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Enter shared room code") },
                        leadingIcon = { Icon(Icons.Default.Tag, null) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            enabled = roomCode.trim().length >= 4,
                            onClick = { openRoom(roomCode, true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Join video") }
                        FilledTonalButton(
                            enabled = roomCode.trim().length >= 4,
                            onClick = { openRoom(roomCode, false) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Join voice") }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Share the room code with the other phone and join the same room.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            TextButton(onClick = onSignIn) {
                Icon(Icons.Default.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign in / Create account")
            }
        }
    }
}
