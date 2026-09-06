package com.globalcall.app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    val scope = rememberCoroutineScope()
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
        callRepository = callRepository,
        onBack = onBack,
        onStartCall = { peer, video, onError ->
            scope.launch {
                runCatching { callRepository.startCall(peer, video) }
                    .onSuccess { session = it }
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
    callRepository: GlobalCallRepository,
    onBack: () -> Unit,
    onStartCall: (AppUser, Boolean, (String) -> Unit) -> Unit
) {
    val repository = remember { ChatRepository() }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember(peerUid) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var conversation by remember(peerUid) { mutableStateOf<ConversationState?>(null) }
    var peerOfficialName by remember(peerUid) { mutableStateOf(initialPeerName) }
    var peerName by remember(peerUid) {
        mutableStateOf(callRepository.getContactAlias(peerUid).ifBlank { initialPeerName })
    }
    var peerEmail by remember(peerUid) { mutableStateOf("") }
    var peerPhotoData by remember(peerUid) { mutableStateOf("") }
    var peerOnline by remember(peerUid) { mutableStateOf(false) }
    var peerLastSeen by remember(peerUid) { mutableStateOf<Timestamp?>(null) }
    var draft by remember(peerUid) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var renameDraft by remember(peerUid) { mutableStateOf(peerName) }
    var blockedByMe by remember(peerUid) { mutableStateOf(false) }
    var muted by remember(peerUid) { mutableStateOf(callRepository.isContactMuted(peerUid)) }

    DisposableEffect(peerUid, myUid) {
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
                    val email = snapshot.getString("email").orEmpty()
                    val officialName = snapshot.getString("displayName").orEmpty().trim()
                        .ifBlank { email.substringBefore('@').trim() }
                        .ifBlank { initialPeerName }
                    peerOfficialName = officialName
                    peerEmail = email
                    peerPhotoData = snapshot.getString("photoData").orEmpty()
                    peerOnline = snapshot.getBoolean("online") ?: false
                    peerLastSeen = snapshot.getTimestamp("lastSeen")
                    peerName = callRepository.getContactAlias(peerUid).ifBlank { officialName }
                }
            }
        val blockRegistration = if (myUid.isNotBlank()) {
            callRepository.observeBlockedUsers(myUid) { blocked ->
                blockedByMe = peerUid in blocked
            }
        } else null
        onDispose {
            messageRegistration.remove()
            conversationRegistration.remove()
            peerRegistration.remove()
            blockRegistration?.remove()
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

    LaunchedEffect(peerUid, draft.isBlank(), blockedByMe) {
        if (draft.isBlank() || blockedByMe) {
            runCatching { repository.setTyping(peerUid, false) }
        } else {
            while (draft.isNotBlank() && !blockedByMe) {
                runCatching { repository.setTyping(peerUid, true) }
                delay(2_500L)
            }
        }
    }

    val peerTyping = !blockedByMe && conversation?.let { state ->
        val age = state.typingAt?.let { (System.currentTimeMillis() / 1000L) - it.seconds } ?: Long.MAX_VALUE
        state.typingUid == peerUid && age in 0..10
    } == true

    val subtitle = when {
        blockedByMe -> "Blocked"
        peerTyping -> "typing…"
        peerOnline -> "Online"
        peerLastSeen != null -> "Last seen ${formatLastSeen(peerLastSeen!!)}"
        else -> "GlobalCall Messages"
    }

    fun startCall(video: Boolean) {
        if (blockedByMe) {
            error = "Unblock this contact before calling"
            return
        }
        val peer = AppUser(
            uid = peerUid,
            displayName = peerName,
            email = peerEmail,
            photoData = peerPhotoData,
            online = peerOnline
        )
        onStartCall(peer, video) { error = it }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename contact") },
            text = {
                Column {
                    Text(
                        "This name is private to you. Their GlobalCall profile remains unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it.take(50) },
                        label = { Text("Contact name") },
                        placeholder = { Text(peerOfficialName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (peerOfficialName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Account name: $peerOfficialName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { callRepository.setContactAlias(peerUid, renameDraft) }
                            .onSuccess {
                                peerName = callRepository.getContactAlias(peerUid).ifBlank { peerOfficialName }
                                info = if (callRepository.getContactAlias(peerUid).isBlank()) {
                                    "Using account name"
                                } else {
                                    "Contact renamed to $peerName"
                                }
                                error = null
                            }
                            .onFailure { error = it.message ?: "Could not rename contact" }
                        showRenameDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            icon = { Icon(Icons.Default.Block, null) },
            title = { Text("Block $peerName?") },
            text = { Text("They will not be able to start a new GlobalCall message or call with you while blocked.") },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    scope.launch {
                        runCatching { callRepository.blockUser(peerUid) }
                            .onSuccess {
                                blockedByMe = true
                                muted = true
                                draft = ""
                                info = "$peerName blocked"
                                error = null
                            }
                            .onFailure { error = it.message ?: "Could not block contact" }
                    }
                }) { Text("Block") }
            },
            dismissButton = { TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
            title = { Text("Delete contact?") },
            text = { Text("$peerName will be removed from your GlobalCall contacts. You can find and add the account again later.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        runCatching { callRepository.deleteContact(peerUid) }
                            .onSuccess { onBack() }
                            .onFailure { error = it.message ?: "Could not delete contact" }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PeerAvatar(peerPhotoData, peerName, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    blockedByMe -> MaterialTheme.colorScheme.error
                                    peerTyping || peerOnline -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(enabled = !blockedByMe, onClick = { startCall(false) }) {
                        Icon(Icons.Default.Call, "Voice call")
                    }
                    IconButton(enabled = !blockedByMe, onClick = { startCall(true) }) {
                        Icon(Icons.Default.Videocam, "Video call")
                    }
                    IconButton(
                        onClick = {
                            if (blockedByMe) {
                                scope.launch {
                                    runCatching { callRepository.unblockUser(peerUid) }
                                        .onSuccess { blockedByMe = false; info = "$peerName unblocked"; error = null }
                                        .onFailure { error = it.message ?: "Could not unblock contact" }
                                }
                            } else {
                                showBlockDialog = true
                            }
                        }
                    ) {
                        Icon(
                            if (blockedByMe) Icons.Default.LockOpen else Icons.Default.Block,
                            if (blockedByMe) "Unblock contact" else "Block contact"
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Contact options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                text = { Text("Rename contact") },
                                onClick = {
                                    renameDraft = peerName
                                    showMoreMenu = false
                                    showRenameDialog = true
                                }
                            )
                            if (callRepository.getContactAlias(peerUid).isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Use account name") },
                                    onClick = {
                                        callRepository.setContactAlias(peerUid, "")
                                        peerName = peerOfficialName
                                        info = "Using account name"
                                        showMoreMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                leadingIcon = { Icon(if (muted) Icons.Default.Notifications else Icons.Default.NotificationsOff, null) },
                                text = { Text(if (muted) "Unmute notifications" else "Mute notifications") },
                                onClick = {
                                    muted = !muted
                                    callRepository.setContactMuted(peerUid, muted)
                                    info = if (muted) "Message notifications muted" else "Message notifications unmuted"
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(if (blockedByMe) Icons.Default.LockOpen else Icons.Default.Block, null) },
                                text = { Text(if (blockedByMe) "Unblock contact" else "Block contact") },
                                onClick = {
                                    showMoreMenu = false
                                    if (blockedByMe) {
                                        scope.launch {
                                            runCatching { callRepository.unblockUser(peerUid) }
                                                .onSuccess {
                                                    blockedByMe = false
                                                    info = "$peerName unblocked"
                                                    error = null
                                                }
                                                .onFailure { error = it.message ?: "Could not unblock contact" }
                                        }
                                    } else {
                                        showBlockDialog = true
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                text = { Text("Delete contact", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
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
            if (!blockedByMe) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Contact controls",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            muted = !muted
                            callRepository.setContactMuted(peerUid, muted)
                            info = if (muted) "Message notifications muted" else "Message notifications unmuted"
                        }) {
                            Icon(
                                if (muted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (muted) "Unmute" else "Mute")
                        }
                        TextButton(onClick = { showBlockDialog = true }) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Block")
                        }
                    }
                }
            }
            if (blockedByMe) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "You blocked $peerName. Unblock to message or call.",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { callRepository.unblockUser(peerUid) }
                                    .onSuccess { blockedByMe = false; info = "$peerName unblocked" }
                                    .onFailure { error = it.message ?: "Could not unblock contact" }
                            }
                        }) { Text("Unblock") }
                    }
                }
            }

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
                            PeerAvatar(peerPhotoData, peerName, 78.dp)
                            Spacer(Modifier.height(14.dp))
                            Text(peerName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Start a private GlobalCall conversation",
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
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            info?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
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
                        enabled = !blockedByMe,
                        onValueChange = { if (it.length <= 2000) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (blockedByMe) "Unblock to send messages" else "Message $peerName") },
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        enabled = !blockedByMe && draft.isNotBlank() && !sending,
                        onClick = {
                            val text = draft
                            scope.launch {
                                sending = true
                                runCatching { repository.setTyping(peerUid, false) }
                                runCatching { repository.sendMessage(peerUid, text) }
                                    .onSuccess { draft = ""; error = null }
                                    .onFailure { error = friendlyChatError(it) }
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

private fun friendlyChatError(throwable: Throwable): String {
    val raw = throwable.message.orEmpty()
    return when {
        raw.contains("PERMISSION_DENIED", ignoreCase = true) ||
            raw.contains("insufficient permissions", ignoreCase = true) ->
            "Messaging access is not synced with Firebase yet. Publish the latest GlobalCall Firestore rules, then try again."
        raw.contains("network", ignoreCase = true) || raw.contains("unavailable", ignoreCase = true) ->
            "Message could not reach GlobalCall. Check internet and try again."
        raw.isNotBlank() -> raw
        else -> "Could not send message"
    }
}

@Composable
private fun PeerAvatar(photoData: String, name: String, size: Dp) {
    val image = remember(photoData) {
        if (photoData.isBlank()) null else runCatching {
            val bytes = Base64.decode(photoData, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "$name profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.ifBlank { "G" }.take(1).uppercase(),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
