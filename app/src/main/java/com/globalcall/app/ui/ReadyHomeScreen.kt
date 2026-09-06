package com.globalcall.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.globalcall.app.ChatActivity
import com.globalcall.app.ExternalCallRequest
import com.globalcall.app.MainActivity
import com.globalcall.app.PrivacySettingsActivity
import com.globalcall.app.BuildConfig
import com.globalcall.app.data.ChatRepository
import com.globalcall.app.data.ConversationState
import com.globalcall.app.data.DiscoveredUser
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.data.UserDiscoveryRepository
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallInvite
import com.globalcall.app.model.CallRecord
import com.globalcall.app.model.CallSession
import com.globalcall.app.ui.theme.ThemeMode
import com.globalcall.app.ui.theme.ThemePreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private suspend fun encodeProfilePhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open this image" }
        BitmapFactory.decodeStream(input, null, bounds)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Please choose a valid image" }

    var sample = 1
    while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open this image" }
        BitmapFactory.decodeStream(input, null, options)
    } ?: error("Could not decode this image")

    val maxSide = 320f
    val scale = minOf(1f, maxSide / maxOf(bitmap.width, bitmap.height).toFloat())
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else bitmap

    var quality = 80
    var bytes: ByteArray
    val output = ByteArrayOutputStream()
    do {
        output.reset()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        bytes = output.toByteArray()
        quality -= 8
    } while (bytes.size > 150 * 1024 && quality >= 48)

    if (scaled !== bitmap) bitmap.recycle()
    scaled.recycle()
    require(bytes.size <= 190 * 1024) { "This photo is too large. Please choose another image." }
    Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@Composable
private fun AvatarImage(
    photoData: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val image = remember(photoData) {
        if (photoData.isBlank()) null else runCatching {
            val bytes = Base64.decode(photoData, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Surface(
        modifier = modifier.size(size),
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
                    style = if (size >= 90.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun ReadyHomeScreen(
    auth: FirebaseAuth,
    repository: GlobalCallRepository,
    externalCallRequest: ExternalCallRequest?,
    onExternalCallHandled: () -> Unit,
    onJoinCall: (CallSession) -> Unit
) {
    val user = requireNotNull(auth.currentUser)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val chatRepository = remember(auth) { ChatRepository(auth = auth) }
    val discoveryRepository = remember(auth) { UserDiscoveryRepository(auth = auth) }
    var tab by remember { mutableIntStateOf(0) }
    var allUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var myProfile by remember { mutableStateOf<AppUser?>(null) }
    var connectionUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var conversations by remember { mutableStateOf<List<ConversationState>>(emptyList()) }
    var incoming by remember { mutableStateOf<CallInvite?>(null) }
    var myCallCode by remember { mutableStateOf("") }
    var myUsername by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val people = remember(allUsers, connectionUids) {
        allUsers.filter { it.uid in connectionUids }
            .sortedWith(compareByDescending<AppUser> { it.online }.thenBy { it.displayName.lowercase() })
    }
    val visibleConversations = remember(conversations, connectionUids, user.uid) {
        conversations.filter { it.peerUid(user.uid) in connectionUids }
    }
    val unreadChats = remember(visibleConversations, user.uid) {
        visibleConversations.count { it.isUnread(user.uid) }
    }

    fun openChat(peer: AppUser) {
        context.startActivity(
            Intent(context, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_PEER_UID, peer.uid)
                putExtra(ChatActivity.EXTRA_PEER_NAME, peer.displayName.ifBlank { "GlobalCall contact" })
            }
        )
    }

    LaunchedEffect(user.uid) {
        runCatching { repository.repairMyCallState() }
        runCatching { repository.ensureMyCallCode() }
            .onSuccess { myCallCode = it }
            .onFailure { message = it.message ?: "Could not activate your GlobalCall ID" }
        runCatching { discoveryRepository.ensureMyUsername() }
            .onSuccess { myUsername = it }
            .onFailure { message = it.message ?: "Could not activate your username" }
        runCatching { repository.publishPhoneDirectory() }
    }

    DisposableEffect(user.uid) {
        val myProfileReg = repository.observeMyProfile(user.uid, onChange = { myProfile = it }, onError = { })
        val peopleReg = repository.observePeople(onChange = { allUsers = it }, onError = { })
        val connectionReg = repository.observeConnectionUids(user.uid, onChange = { connectionUids = it }, onError = { })
        val callsReg = repository.observeCallHistory(user.uid, onChange = { calls = it }, onError = { })
        val chatReg = chatRepository.observeConversations(onChange = { conversations = it }, onError = { })

        val incomingReg = FirebaseFirestore.getInstance()
            .collection("calls")
            .whereArrayContains("participantUids", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    incoming = null
                    return@addSnapshotListener
                }
                val doc = snapshot?.documents.orEmpty()
                    .filter {
                        it.getString("calleeUid") == user.uid &&
                            it.getString("status") == "ringing"
                    }
                    .maxByOrNull { it.getTimestamp("createdAt")?.seconds ?: 0L }
                incoming = doc?.let {
                    CallInvite(
                        id = it.id,
                        callerUid = it.getString("callerUid").orEmpty(),
                        callerName = it.getString("callerName").orEmpty(),
                        calleeUid = it.getString("calleeUid").orEmpty(),
                        calleeName = it.getString("calleeName").orEmpty(),
                        status = it.getString("status").orEmpty(),
                        video = it.getBoolean("video") ?: true,
                        roomName = it.getString("roomName").orEmpty()
                    )
                }
            }

        onDispose {
            myProfileReg.remove()
            peopleReg.remove()
            connectionReg.remove()
            callsReg.remove()
            chatReg.remove()
            incomingReg.remove()
        }
    }

    DisposableEffect(lifecycleOwner, user.uid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                scope.launch { runCatching { repository.setOnline(true) } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            scope.launch { runCatching { repository.setOnline(true) } }
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(externalCallRequest?.callId) {
        val request = externalCallRequest ?: return@LaunchedEffect
        if (request.action == MainActivity.ACTION_ANSWER) {
            runCatching { repository.loadInvite(request.callId) }.getOrNull()?.let { invite ->
                runCatching { repository.acceptCall(invite) }
                    .onSuccess(onJoinCall)
                    .onFailure { message = it.message ?: "Could not answer this call" }
            }
        }
        onExternalCallHandled()
    }

    fun directCall(peer: AppUser, video: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            calls.filter {
                it.status == "ringing" &&
                    it.isOutgoing(user.uid) &&
                    it.peerUid(user.uid) == peer.uid
            }.forEach { oldCall ->
                runCatching { repository.endCall(oldCall.id) }
            }

            runCatching { repository.startCall(peer, video) }
                .onSuccess(onJoinCall)
                .onFailure {
                    message = when {
                        it.message?.contains("another call", ignoreCase = true) == true -> it.message
                        it.message?.contains("permission", ignoreCase = true) == true -> "Call access is syncing. Please try again in a moment."
                        else -> it.message ?: "Could not start call"
                    }
                }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlobalCallTopBar(
                    name = myProfile?.displayName?.takeIf { it.isNotBlank() }
                        ?: user.displayName
                        ?: user.phoneNumber
                        ?: user.email.orEmpty(),
                    onlineCount = people.count { it.online },
                    onSearch = { tab = 2 },
                    onChats = { tab = 1 },
                    unreadChats = unreadChats
                )
            },
            bottomBar = { GlobalCallBottomBar(tab, unreadChats) { tab = it } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> CallsTab(
                        calls = calls,
                        people = people,
                        currentUid = user.uid,
                        busy = busy,
                        onDirectCall = ::directCall,
                        onMessage = ::openChat
                    )
                    1 -> ChatsTab(
                        conversations = visibleConversations,
                        people = people,
                        currentUid = user.uid,
                        onMessage = ::openChat
                    )
                    2 -> PeopleTab(
                        people = people,
                        busy = busy,
                        repository = repository,
                        discoveryRepository = discoveryRepository,
                        onDirectCall = ::directCall,
                        onMessage = ::openChat
                    )
                    3 -> ProfileTab(
                        auth = auth,
                        repository = repository,
                        discoveryRepository = discoveryRepository,
                        profile = myProfile,
                        callCode = myCallCode,
                        username = myUsername,
                        onUsernameChanged = { myUsername = it },
                        onMessage = { message = it }
                    )
                    else -> SettingsTab(
                        auth = auth,
                        repository = repository,
                        onMessage = { message = it }
                    )
                }
                message?.let {
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        action = { TextButton(onClick = { message = null }) { Text("OK") } }
                    ) { Text(it) }
                }
            }
        }

        incoming?.let { invite ->
            IncomingOverlay(
                invite = invite,
                callerPhotoData = people.firstOrNull { it.uid == invite.callerUid }?.photoData.orEmpty(),
                onAnswer = {
                    scope.launch {
                        runCatching { repository.acceptCall(invite) }
                            .onSuccess { incoming = null; onJoinCall(it) }
                            .onFailure { message = it.message ?: "Could not answer this call" }
                    }
                },
                onDecline = {
                    scope.launch { runCatching { repository.declineCall(invite.id) } }
                    incoming = null
                }
            )
        }
    }
}

@Composable
private fun GlobalCallTopBar(
    name: String,
    onlineCount: Int,
    onSearch: () -> Unit,
    onChats: () -> Unit,
    unreadChats: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = .98f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "GlobalCall",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF35D39A)))
                }
                Text(
                    if (onlineCount > 0) "$onlineCount contact${if (onlineCount == 1) "" else "s"} online" else name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onChats) {
                BadgedBox(
                    badge = {
                        if (unreadChats > 0) Badge { Text(unreadChats.coerceAtMost(99).toString()) }
                    }
                ) { Icon(Icons.Default.Chat, "Chats") }
            }
            FilledIconButton(onClick = onSearch) { Icon(Icons.Default.PersonSearch, "Find people") }
        }
    }
}

@Composable
private fun GlobalCallBottomBar(selected: Int, unreadChats: Int, onSelected: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(selected == 0, { onSelected(0) }, { Icon(Icons.Default.PhoneInTalk, null) }, label = { Text("Calls") })
        NavigationBarItem(
            selected == 1,
            { onSelected(1) },
            {
                BadgedBox(
                    badge = { if (unreadChats > 0) Badge { Text(unreadChats.coerceAtMost(99).toString()) } }
                ) { Icon(Icons.Default.Chat, null) }
            },
            label = { Text("Chats") }
        )
        NavigationBarItem(selected == 2, { onSelected(2) }, { Icon(Icons.Default.Groups, null) }, label = { Text("People") })
        NavigationBarItem(selected == 3, { onSelected(3) }, { Icon(Icons.Default.AccountCircle, null) }, label = { Text("Profile") })
        NavigationBarItem(selected == 4, { onSelected(4) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
    }
}

@Composable
private fun CallsTab(
    calls: List<CallRecord>,
    people: List<AppUser>,
    currentUid: String,
    busy: Boolean,
    onDirectCall: (AppUser, Boolean) -> Unit,
    onMessage: (AppUser) -> Unit
) {
    val onlinePeople = remember(people) { people.filter { it.online } }
    val recentCalls = remember(calls, currentUid) {
        calls.filter { it.status != "ringing" }
            .distinctBy { it.peerUid(currentUid) }
            .take(30)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))
                    ).padding(22.dp)
                ) {
                    Text("Calls & messages. Your people.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Find someone by @username, GlobalCall ID or verified phone, then keep them in your contacts.", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f))
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Native WebRTC calls • Internet messaging", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        if (onlinePeople.isNotEmpty()) {
            item { Text("Online now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
            items(onlinePeople, key = { "online-${it.uid}" }) { person ->
                val onAnotherCall = person.callState in setOf("active", "calling", "ringing")
                ContactRow(
                    title = person.displayName.ifBlank { "GlobalCall user" },
                    subtitle = if (onAnotherCall) "On another call" else "Online • ${person.callCode.ifBlank { "Connected" }}",
                    photoData = person.photoData,
                    online = true,
                    enabled = !busy && !onAnotherCall,
                    onMessage = { onMessage(person) },
                    onVoice = { onDirectCall(person, false) },
                    onVideo = { onDirectCall(person, true) }
                )
            }
        }

        item { Text("Recent calls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        if (recentCalls.isEmpty()) {
            item { EmptyCard(Icons.Default.History, "No completed calls yet", "Find someone in People, connect once, then start a voice/video call or message them.") }
        } else {
            items(recentCalls, key = { it.id }) { call ->
                val peer = people.firstOrNull { it.uid == call.peerUid(currentUid) }
                val duration = call.durationSeconds()?.let(::formatCallDuration)
                val stateText = buildString {
                    append(call.outcomeFor(currentUid))
                    if (duration != null) append(" • $duration")
                }
                val onAnotherCall = peer?.callState in setOf("active", "calling", "ringing")
                ContactRow(
                    title = call.peerName(currentUid).ifBlank { peer?.displayName ?: "GlobalCall user" },
                    subtitle = when {
                        onAnotherCall -> "$stateText • Busy"
                        peer?.online == true -> "$stateText • Online"
                        else -> "$stateText • Offline"
                    },
                    photoData = peer?.photoData.orEmpty(),
                    online = peer?.online == true,
                    enabled = peer != null && !busy && !onAnotherCall,
                    onMessage = peer?.let { { onMessage(it) } },
                    onVoice = { peer?.let { onDirectCall(it, false) } },
                    onVideo = { peer?.let { onDirectCall(it, true) } }
                )
            }
        }
    }
}

@Composable
private fun ChatsTab(
    conversations: List<ConversationState>,
    people: List<AppUser>,
    currentUid: String,
    onMessage: (AppUser) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("Messages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Private conversations with your GlobalCall contacts", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (conversations.isEmpty()) {
            item { EmptyCard(Icons.Default.ChatBubbleOutline, "No conversations yet", "Open a contact and send your first GlobalCall message.") }
        } else {
            items(conversations, key = { it.id }) { conversation ->
                val peerUid = conversation.peerUid(currentUid)
                val peer = people.firstOrNull { it.uid == peerUid }
                    ?: AppUser(uid = peerUid, displayName = "GlobalCall contact")
                ElevatedCard(
                    onClick = { if (peer.uid.isNotBlank()) onMessage(peer) },
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            AvatarImage(peer.photoData, peer.displayName, 54.dp)
                            if (peer.online) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape)
                                        .background(Color(0xFF35D39A))
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    peer.displayName.ifBlank { "GlobalCall contact" },
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (conversation.isUnread(currentUid)) FontWeight.ExtraBold else FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                conversation.updatedAt?.let {
                                    Text(
                                        formatChatTime(it.toDate()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    conversation.lastMessage.ifBlank { "New conversation" },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (conversation.isUnread(currentUid)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (conversation.isUnread(currentUid)) FontWeight.Bold else FontWeight.Normal
                                )
                                if (conversation.isUnread(currentUid)) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge { Text("1") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(
    people: List<AppUser>,
    busy: Boolean,
    repository: GlobalCallRepository,
    discoveryRepository: UserDiscoveryRepository,
    onDirectCall: (AppUser, Boolean) -> Unit,
    onMessage: (AppUser) -> Unit
) {
    val scope = rememberCoroutineScope()
    var contactSearch by remember { mutableStateOf("") }
    var discoverQuery by remember { mutableStateOf("") }
    var discoverResult by remember { mutableStateOf<DiscoveredUser?>(null) }
    var discoverMessage by remember { mutableStateOf<String?>(null) }
    var discovering by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }

    val filtered = remember(people, contactSearch) {
        people.filter {
            contactSearch.isBlank() ||
                it.displayName.contains(contactSearch, true) ||
                it.callCode.contains(contactSearch, true) ||
                it.phoneLast4.contains(contactSearch.filter(Char::isDigit))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.PersonSearch, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Find people", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Use @username, GlobalCall ID, or a verified phone number.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = discoverQuery,
                        onValueChange = {
                            discoverQuery = it.take(32)
                            discoverResult = null
                            discoverMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username, ID or phone") },
                        placeholder = { Text("@rahim / GC-... / +8801...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                discovering = true
                                discoverMessage = null
                                discoverResult = null
                                runCatching { discoveryRepository.findUser(discoverQuery) }
                                    .onSuccess {
                                        discoverResult = it
                                        if (it == null) discoverMessage = "No matching GlobalCall account found"
                                    }
                                    .onFailure { discoverMessage = it.message ?: "Could not search GlobalCall" }
                                discovering = false
                            }
                        },
                        enabled = !discovering && discoverQuery.trim().length >= 4,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (discovering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Search GlobalCall")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Phone lookup works only for accounts with a verified phone number. @username and GlobalCall ID do not need SMS billing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    discoverMessage?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        discoverResult?.let { found ->
            item {
                val person = found.user
                val alreadyConnected = people.any { it.uid == person.uid }
                val onAnotherCall = person.callState in setOf("active", "calling", "ringing")
                ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(person.photoData, person.displayName, 58.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName.ifBlank { "GlobalCall user" }, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    when {
                                        found.username.isNotBlank() -> "@${found.username}"
                                        person.callCode.isNotBlank() -> person.callCode
                                        else -> "GlobalCall account"
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    when {
                                        onAnotherCall -> "On another call"
                                        person.online -> "Online"
                                        else -> "Offline"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (alreadyConnected) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onMessage(person) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Chat, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Message")
                                }
                                FilledIconButton(
                                    enabled = !busy && !onAnotherCall,
                                    onClick = { onDirectCall(person, false) }
                                ) { Icon(Icons.Default.Call, "Voice call") }
                                FilledIconButton(
                                    enabled = !busy && !onAnotherCall,
                                    onClick = { onDirectCall(person, true) }
                                ) { Icon(Icons.Default.Videocam, "Video call") }
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        connecting = true
                                        runCatching { discoveryRepository.connect(person) }
                                            .onSuccess { discoverMessage = "${person.displayName.ifBlank { "GlobalCall user" }} added to your contacts" }
                                            .onFailure { discoverMessage = it.message ?: "Could not add this account" }
                                        connecting = false
                                    }
                                },
                                enabled = !connecting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else {
                                    Icon(Icons.Default.PersonAdd, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add contact")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = contactSearch,
                onValueChange = { contactSearch = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your contacts") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }
        item { Text("Your contacts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        if (filtered.isEmpty()) {
            item { EmptyCard(Icons.Default.Groups, "No contacts yet", "Search an @username, GlobalCall ID, or verified phone number above.") }
        } else {
            items(filtered, key = { it.uid }) { person ->
                val onAnotherCall = person.callState in setOf("active", "calling", "ringing")
                ContactRow(
                    title = person.displayName.ifBlank { "GlobalCall user" },
                    subtitle = when {
                        onAnotherCall -> "On another call • ${person.callCode.ifBlank { "Connected" }}"
                        person.online -> "Online • ${person.callCode.ifBlank { "Connected" }}"
                        else -> "Offline • ${person.callCode.ifBlank { "Connected" }}"
                    },
                    photoData = person.photoData,
                    online = person.online,
                    enabled = !busy && !onAnotherCall,
                    onMessage = { onMessage(person) },
                    onVoice = { onDirectCall(person, false) },
                    onVideo = { onDirectCall(person, true) }
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    title: String,
    subtitle: String,
    photoData: String,
    online: Boolean,
    enabled: Boolean,
    onMessage: (() -> Unit)?,
    onVoice: () -> Unit,
    onVideo: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AvatarImage(photoData, title, 54.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape)
                        .background(if (online) Color(0xFF35D39A) else MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            onMessage?.let { action ->
                IconButton(onClick = action) { Icon(Icons.Default.Chat, "Message") }
            }
            IconButton(enabled = enabled, onClick = onVoice) { Icon(Icons.Default.Call, "Voice call") }
            IconButton(enabled = enabled, onClick = onVideo) { Icon(Icons.Default.Videocam, "Video call") }
        }
    }
}

@Composable
private fun EmptyCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Icon(icon, null, modifier = Modifier.padding(12.dp)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileTab(
    auth: FirebaseAuth,
    repository: GlobalCallRepository,
    discoveryRepository: UserDiscoveryRepository,
    profile: AppUser?,
    callCode: String,
    username: String,
    onUsernameChanged: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val user = auth.currentUser ?: return
    val identifier = user.phoneNumber ?: user.email.orEmpty()
    val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: user.displayName ?: "GlobalCall user"
    val photoData = profile?.photoData.orEmpty()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var savingPhoto by remember { mutableStateOf(false) }
    var usernameDraft by remember(username) { mutableStateOf(username) }
    var savingUsername by remember { mutableStateOf(false) }

    fun shareProfile() {
        if (username.isBlank() && callCode.isBlank()) return
        val text = buildString {
            append("Find me on GlobalCall")
            if (username.isNotBlank()) append(": @$username")
            if (callCode.isNotBlank()) append("\nBackup GlobalCall ID: $callCode")
            append("\nOpen GlobalCall → People and search this username or ID.")
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My GlobalCall profile")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share GlobalCall profile"))
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                savingPhoto = true
                runCatching {
                    val encoded = encodeProfilePhoto(context, uri)
                    repository.updateProfilePhoto(encoded)
                }.onSuccess {
                    onMessage("Profile photo saved")
                }.onFailure {
                    onMessage(it.message ?: "Could not save profile photo")
                }
                savingPhoto = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarImage(photoData, name, 112.dp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { photoPicker.launch("image/*") },
                        enabled = !savingPhoto
                    ) {
                        if (savingPhoto) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (photoData.isBlank()) "Add photo" else "Change photo")
                    }
                    if (photoData.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    savingPhoto = true
                                    runCatching { repository.updateProfilePhoto("") }
                                        .onSuccess { onMessage("Profile photo removed") }
                                        .onFailure { onMessage(it.message ?: "Could not remove photo") }
                                    savingPhoto = false
                                }
                            },
                            enabled = !savingPhoto
                        ) {
                            Icon(Icons.Default.DeleteOutline, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(identifier, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AlternateEmail, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Your GlobalCall username", fontWeight = FontWeight.ExtraBold)
                            Text("People can find you without typing the long GlobalCall ID.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = usernameDraft,
                        onValueChange = { usernameDraft = it.removePrefix("@").take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        prefix = { Text("@") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    savingUsername = true
                                    runCatching { discoveryRepository.updateMyUsername(usernameDraft) }
                                        .onSuccess {
                                            usernameDraft = it
                                            onUsernameChanged(it)
                                            onMessage("Username saved as @$it")
                                        }
                                        .onFailure { onMessage(it.message ?: "Could not save username") }
                                    savingUsername = false
                                }
                            },
                            enabled = !savingUsername && usernameDraft.length >= 4,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (savingUsername) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = ::shareProfile,
                            enabled = username.isNotBlank() || callCode.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Share")
                        }
                    }
                    if (username.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Your easy address: @$username", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("Backup GlobalCall ID", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(callCode.ifBlank { "Activating…" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(6.dp))
                    Text("This permanent ID still works if you do not know someone's username.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
                    Spacer(Modifier.height(14.dp))
                    Button(
                        enabled = callCode.isNotBlank(),
                        onClick = {
                            clipboard.setText(AnnotatedString(callCode))
                            onMessage("GlobalCall ID copied")
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy ID")
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Easy discovery is active", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Friends can search your @username without SMS. Verified phone lookup remains available when phone verification exists.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.setOnline(false) }
                        auth.signOut()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun SettingsTab(
    auth: FirebaseAuth,
    repository: GlobalCallRepository,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "Privacy, notifications, appearance and call health",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            ElevatedCard(
                onClick = { context.startActivity(Intent(context, PrivacySettingsActivity::class.java)) },
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Privacy & blocked users", fontWeight = FontWeight.Bold)
                        Text("Unblock users and manage muted contacts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Appearance", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = ThemePreferences.mode == ThemeMode.SYSTEM,
                            onClick = { ThemePreferences.setMode(context.applicationContext, ThemeMode.SYSTEM) },
                            label = { Text("System") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = ThemePreferences.mode == ThemeMode.LIGHT,
                            onClick = { ThemePreferences.setMode(context.applicationContext, ThemeMode.LIGHT) },
                            label = { Text("Light") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = ThemePreferences.mode == ThemeMode.DARK,
                            onClick = { ThemePreferences.setMode(context.applicationContext, ThemeMode.DARK) },
                            label = { Text("Dark") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Notifications", fontWeight = FontWeight.Bold)
                            Text("Incoming calls and messages need Android notifications enabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open notification settings") }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BuildCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Call status repair", fontWeight = FontWeight.Bold)
                            Text("Clears a stale Busy / On another call state when no real call is alive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching { repository.repairMyCallState() }
                                    .onSuccess { onMessage("Call status checked and repaired") }
                                    .onFailure { onMessage(it.message ?: "Could not repair call status") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Repair call status") }
                }
            }
        }
        item {
            Text(
                "GlobalCall ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.setOnline(false) }
                        auth.signOut()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun IncomingOverlay(
    invite: CallInvite,
    callerPhotoData: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(invite.id) {
        val player = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            MediaPlayer.create(context, uri)?.apply {
                isLooping = true
                start()
            }
        }.getOrNull()
        onDispose {
            runCatching { player?.stop() }
            runCatching { player?.release() }
        }
    }

    LaunchedEffect(invite.id) {
        delay(45_000)
        onDecline()
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xF20A0D13)) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (invite.video) "Incoming video call" else "Incoming voice call", color = Color.White.copy(alpha = .7f))
            Spacer(Modifier.height(20.dp))
            AvatarImage(callerPhotoData, invite.callerName, 120.dp)
            Spacer(Modifier.height(18.dp))
            Text(invite.callerName.ifBlank { "GlobalCall user" }, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("GlobalCall • secure ${if (invite.video) "video" else "voice"} call", color = Color.White.copy(alpha = .62f))
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                FilledIconButton(onClick = onDecline, modifier = Modifier.size(70.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF3B30))) {
                    Icon(Icons.Default.CallEnd, "Decline", modifier = Modifier.size(32.dp))
                }
                FilledIconButton(onClick = onAnswer, modifier = Modifier.size(70.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF35D07F))) {
                    Icon(if (invite.video) Icons.Default.Videocam else Icons.Default.Call, "Answer", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

private fun formatCallDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "%d:%02d".format(seconds / 60, seconds % 60)
    else -> "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}

private fun formatChatTime(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    return if (diff < 24 * 60 * 60_000L) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
