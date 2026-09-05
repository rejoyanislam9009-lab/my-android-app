package com.globalcall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.globalcall.app.data.ChatMessage
import com.globalcall.app.data.ChatRepository
import com.globalcall.app.data.ConversationState
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallSession
import com.globalcall.app.ui.CallScreen
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val peerUid = intent.getStringExtra(EXTRA_PEER_UID).orEmpty()
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "GlobalCall contact" }
        if (peerUid.isBlank() || FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }
        setContent {
            GlobalCallTheme {
                ChatHost(peerUid = peerUid, initialPeerName = peerName, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PEER_UID = "globalcall_peer_uid"
        const val EXTRA_PEER_NAME = "globalcall_peer_name"
    }
}

@Composable
private fun ChatHost(peerUid: String, initialPeerName: String, onBack: () -> Unit) {
    val callRepository = remember { GlobalCallRepository() }
    var session by remember { mutableStateOf<CallSession?>(null) }

    session?.let { callSession ->
        CallScreen(
            session = callSession,
            repository = callRepository,
            onFinish = { session = null }
        )
        return
    }

    ChatScreen(
        peerUid = peerUid,
        initialPeerName = initialPeerName,
        onBack = onBack,
        onStartCall = { video, onError ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                runCatching {
                    callRepository.startCall(
                        AppUser(uid = peerUid, displayName = initialPeerName, online = true),
                        video
                    )
                }.onSuccess { session = it }
                    .onFailure { onError(it.message ?: "Could not start call") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    peerUid: String,
    initialPeerName: String,
    onBack: () -> Unit,
    onStartCall: (Boolean, (String) -> Unit) -> Unit
) {
    val repository = remember { ChatRepository() }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember(peerUid) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var conversation by remember(peerUid) { mutableStateOf<ConversationState?>(null) }
    var peerName by remember(peerUid) { mutableStateOf(initialPeerName) }
    var peerOnline by remember(peerUid) { mutableStateOf(false) }
    var peerLastSeen by remember(peerUid) { mutableStateOf<Timestamp?>(null) }
    var draft by remember(peerUid) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(peerUid) {
        val messageRegistration = repository.observeMessages(
            peerUid = peerUid,
            onChange = { messages = it; error = null },
            onError = { error = "Messages are syncing. Check your connection." }
        )
        val conversationRegistration = repository.observeConversation(
            peerUid = peerUid,
            onChange = { conversation = it },
            onError = { }
        )
        val peerRegistration = FirebaseFirestore.getInstance().collection("users").document(peerUid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    peerName = snapshot.getString("displayName").orEmpty().ifBlank { initialPeerName }
                    peerOnline = snapshot.getBoolean("online") ?: false
                    peerLastSeen = snapshot.getTimestamp("lastSeen")
                }
            }
        onDispose {
            messageRegistration.remove()
            conversationRegistration.remove()
            peerRegistration.remove()
            scope.launch { runCatching { repository.setTyping(peerUid, false) } }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.readAt) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            if (messages.any { it.receiverUid == myUid && it.readAt == null }) {
                runCatching { repository.markRead(peerUid) }
            }
        }
    }

    LaunchedEffect(draft) {
        if (draft.isBlank()) {
            runCatching { repository.setTyping(peerUid, false) }
        } else {
            delay(450)
            runCatching { repository.setTyping(peerUid, true) }
            delay(2_500)
            runCatching { repository.setTyping(peerUid, false) }
        }
    }

    val peerTyping = conversation?.let { state ->
        val age = state.typingAt?.let { (System.currentTimeMillis() / 1000L) - it.seconds } ?: Long.MAX_VALUE
        state.typingUid == peerUid && age in 0..8
    } == true

    val subtitle = when {
        peerTyping -> "typing…"
        peerOnline -> "Online"
        peerLastSeen != null -> "Last seen ${formatLastSeen(peerLastSeen!!)}"
        else -> "GlobalCall Messages"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                Text(peerName.take(1).uppercase(), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (peerTyping || peerOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { onStartCall(false) { error = it } }) {
                        Icon(Icons.Default.Call, "Voice call")
                    }
                    IconButton(onClick = { onStartCall(true) { error = it } }) {
                        Icon(Icons.Default.Videocam, "Video call")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillParentMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                                    Text("💬", style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("Start a conversation", fontWeight = FontWeight.Bold)
                            Text(
                                "Private GlobalCall internet messages. No carrier SMS charge.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, mine = message.senderUid == myUid)
                }
            }

            if (peerTyping) {
                Text(
                    "$peerName is typing…",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 2000) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message $peerName") },
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        enabled = draft.isNotBlank() && !sending,
                        onClick = {
                            val text = draft
                            scope.launch {
                                sending = true
                                runCatching { repository.setTyping(peerUid, false) }
                                runCatching { repository.sendMessage(peerUid, text) }
                                    .onSuccess { draft = ""; error = null }
                                    .onFailure { error = it.message ?: "Could not send message" }
                                sending = false
                            }
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (mine) 20.dp else 5.dp,
                bottomEnd = if (mine) 5.dp else 20.dp
            ),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp).widthIn(max = 300.dp)) {
                Text(
                    message.text,
                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val time = message.createdAt?.toDate()?.let {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it.time))
                }.orEmpty()
                if (time.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
                        )
                        if (mine) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (message.readAt != null) "✓✓ Read" else "✓ Sent",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.readAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatLastSeen(timestamp: Timestamp): String {
    val date = timestamp.toDate()
    val now = System.currentTimeMillis()
    val diff = now - date.time
    return when {
        diff < 60_000L -> "just now"
        diff < 60 * 60_000L -> "${(diff / 60_000L).coerceAtLeast(1)} min ago"
        diff < 24 * 60 * 60_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
    }
}
