package com.globalcall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.AppUser
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PrivacySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }
        setContent {
            GlobalCallTheme {
                PrivacySettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySettingsScreen(onBack: () -> Unit) {
    val repository = remember { GlobalCallRepository() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    var blockedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mutedUids by remember { mutableStateOf(repository.getMutedContactUids()) }
    var profiles by remember { mutableStateOf<Map<String, AppUser>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uid) {
        val registration = if (uid.isBlank()) null else repository.observeBlockedUsers(uid) {
            blockedUids = it
        }
        onDispose { registration?.remove() }
    }

    LaunchedEffect(blockedUids, mutedUids) {
        val ids = (blockedUids + mutedUids).filter { it.isNotBlank() }.toSet()
        val next = mutableMapOf<String, AppUser>()
        ids.forEach { peerUid ->
            runCatching { repository.loadUser(peerUid) }.getOrNull()?.let { next[peerUid] = it }
        }
        profiles = next
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & contacts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "Blocked users",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Blocked accounts cannot start new calls or messages with you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (blockedUids.isEmpty()) {
                    item { PrivacyEmptyCard(Icons.Default.Block, "No blocked users") }
                } else {
                    items(blockedUids.toList(), key = { "blocked-$it" }) { peerUid ->
                        val person = profiles[peerUid]
                        PrivacyContactRow(
                            person = person,
                            fallbackUid = peerUid,
                            actionLabel = "Unblock",
                            onAction = {
                                scope.launch {
                                    runCatching { repository.unblockUser(peerUid) }
                                        .onSuccess { message = "${person?.displayName ?: "Contact"} unblocked" }
                                        .onFailure { message = it.message ?: "Could not unblock contact" }
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Muted message notifications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Messages still arrive in Chats; only their notification is muted on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (mutedUids.isEmpty()) {
                    item { PrivacyEmptyCard(Icons.Default.NotificationsOff, "No muted contacts") }
                } else {
                    items(mutedUids.toList(), key = { "muted-$it" }) { peerUid ->
                        val person = profiles[peerUid]
                        PrivacyContactRow(
                            person = person,
                            fallbackUid = peerUid,
                            actionLabel = "Unmute",
                            onAction = {
                                repository.setContactMuted(peerUid, false)
                                mutedUids = repository.getMutedContactUids()
                                message = "${person?.displayName ?: "Contact"} unmuted"
                            }
                        )
                    }
                }
            }

            message?.let { text ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { message = null }) { Text("OK") } }
                ) { Text(text) }
            }
        }
    }
}

@Composable
private fun PrivacyContactRow(
    person: AppUser?,
    fallbackUid: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val name = person?.displayName?.ifBlank { null }
        ?: person?.email?.substringBefore('@')?.ifBlank { null }
        ?: "GlobalCall contact"
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        name.take(1).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(
                    person?.callCode?.ifBlank { null } ?: fallbackUid.take(12),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PrivacyEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null)
            Spacer(Modifier.width(10.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
