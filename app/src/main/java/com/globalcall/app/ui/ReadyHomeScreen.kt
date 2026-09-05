package com.globalcall.app.ui

import android.content.Context
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
import com.globalcall.app.ExternalCallRequest
import com.globalcall.app.MainActivity
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallInvite
import com.globalcall.app.model.CallRecord
import com.globalcall.app.model.CallSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var tab by remember { mutableIntStateOf(0) }
    var allUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var myProfile by remember { mutableStateOf<AppUser?>(null) }
    var connectionUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var incoming by remember { mutableStateOf<CallInvite?>(null) }
    var myCallCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val people = remember(allUsers, connectionUids) {
        allUsers.filter { it.uid in connectionUids }
            .sortedWith(compareByDescending<AppUser> { it.online }.thenBy { it.displayName.lowercase() })
    }

    LaunchedEffect(user.uid) {
        runCatching { repository.ensureMyCallCode() }
            .onSuccess { myCallCode = it }
            .onFailure { message = it.message ?: "Could not activate your GlobalCall ID" }
        runCatching { repository.publishPhoneDirectory() }
    }

    DisposableEffect(user.uid) {
        val myProfileReg = repository.observeMyProfile(user.uid, onChange = { myProfile = it }, onError = { })
        val peopleReg = repository.observePeople(onChange = { allUsers = it }, onError = { })
        val connectionReg = repository.observeConnectionUids(user.uid, onChange = { connectionUids = it }, onError = { })
        val callsReg = repository.observeCallHistory(user.uid, onChange = { calls = it }, onError = { })

        // The rules authorize call reads by participantUids. Query by that same field,
        // then filter the receiver/status locally. This avoids Firestore query/rule mismatch.
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
            incomingReg.remove()
        }
    }

    DisposableEffect(lifecycleOwner, user.uid) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> scope.launch { runCatching { repository.setOnline(true) } }
                Lifecycle.Event.ON_STOP -> scope.launch { runCatching { repository.setOnline(false) } }
                else -> Unit
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
                    .onFailure { message = "Could not answer this call" }
            }
        }
        onExternalCallHandled()
    }

    fun directCall(peer: AppUser, video: Boolean) {
        if (busy) return
        scope.launch {
            busy = true

            // Clean up an older unfinished outgoing ring to this same contact before
            // starting a fresh call. This prevents stacked "Ringing" records.
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
                        it.message?.contains("offline", ignoreCase = true) == true -> "This contact is offline right now"
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
                    onSearch = { tab = 1 }
                )
            },
            bottomBar = { GlobalCallBottomBar(tab) { tab = it } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> CallsTab(
                        calls = calls,
                        people = people,
                        currentUid = user.uid,
                        busy = busy,
                        onDirectCall = ::directCall
                    )
                    1 -> PeopleTab(
                        people = people,
                        busy = busy,
                        repository = repository,
                        onDirectCall = ::directCall
                    )
                    else -> ProfileTab(
                        auth = auth,
                        repository = repository,
                        profile = myProfile,
                        callCode = myCallCode,
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
                            .onFailure { message = "Could not answer this call" }
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
private fun GlobalCallTopBar(name: String, onlineCount: Int, onSearch: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = .98f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
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
            FilledIconButton(onClick = onSearch) { Icon(Icons.Default.PersonAdd, "Connect people") }
        }
    }
}

@Composable
private fun GlobalCallBottomBar(selected: Int, onSelected: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(selected == 0, { onSelected(0) }, { Icon(Icons.Default.PhoneInTalk, null) }, label = { Text("Calls") })
        NavigationBarItem(selected == 1, { onSelected(1) }, { Icon(Icons.Default.Groups, null) }, label = { Text("People") })
        NavigationBarItem(selected == 2, { onSelected(2) }, { Icon(Icons.Default.AccountCircle, null) }, label = { Text("Profile") })
    }
}

@Composable
private fun CallsTab(
    calls: List<CallRecord>,
    people: List<AppUser>,
    currentUid: String,
    busy: Boolean,
    onDirectCall: (AppUser, Boolean) -> Unit
) {
    val onlinePeople = remember(people) { people.filter { it.online } }
    val recentCalls = remember(calls, currentUid) {
        // History stays in Firestore, but the home screen shows only the latest
        // finished call per contact. Temporary "ringing" rows no longer stack up.
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
                    Text("Real calls. Your contacts.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Connect once with a GlobalCall ID, then call again whenever they are online.", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f))
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Voice & video use the same secure call session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        if (onlinePeople.isNotEmpty()) {
            item { Text("Online now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
            items(onlinePeople, key = { "online-${it.uid}" }) { person ->
                ContactRow(
                    title = person.displayName.ifBlank { "GlobalCall user" },
                    subtitle = "Online • ${person.callCode.ifBlank { "Connected" }}",
                    photoData = person.photoData,
                    online = true,
                    enabled = !busy,
                    onVoice = { onDirectCall(person, false) },
                    onVideo = { onDirectCall(person, true) }
                )
            }
        }

        item { Text("Recent calls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        if (recentCalls.isEmpty()) {
            item { EmptyCard(Icons.Default.History, "No completed calls yet", "Connect someone with their GlobalCall ID, then start a real voice or video call.") }
        } else {
            items(recentCalls, key = { it.id }) { call ->
                val peer = people.firstOrNull { it.uid == call.peerUid(currentUid) }
                val stateText = when (call.status) {
                    "declined" -> "Declined"
                    "accepted" -> "Connected"
                    "ended" -> "Completed"
                    else -> call.status.replaceFirstChar { it.uppercase() }
                }
                ContactRow(
                    title = call.peerName(currentUid).ifBlank { peer?.displayName ?: "GlobalCall user" },
                    subtitle = if (peer?.online == true) "$stateText • Online" else "$stateText • Offline",
                    photoData = peer?.photoData.orEmpty(),
                    online = peer?.online == true,
                    enabled = peer?.online == true && !busy,
                    onVoice = { peer?.let { onDirectCall(it, false) } },
                    onVideo = { peer?.let { onDirectCall(it, true) } }
                )
            }
        }
    }
}

@Composable
private fun PeopleTab(
    people: List<AppUser>,
    busy: Boolean,
    repository: GlobalCallRepository,
    onDirectCall: (AppUser, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeMessage by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var phoneResult by remember { mutableStateOf<AppUser?>(null) }
    var phoneMessage by remember { mutableStateOf<String?>(null) }
    var searchingPhone by remember { mutableStateOf(false) }

    val filtered = remember(people, search) {
        people.filter {
            search.isBlank() || it.displayName.contains(search, true) || it.callCode.contains(search, true) || it.phoneLast4.contains(search.filter(Char::isDigit))
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
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Connect with GlobalCall ID", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Enter their account code once. They stay in your contacts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(20); codeMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("GlobalCall ID") },
                        placeholder = { Text("GC-1A2B-3C4D-5E6F") },
                        leadingIcon = { Icon(Icons.Default.Tag, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                connecting = true
                                codeMessage = null
                                runCatching { repository.connectByCode(code) }
                                    .onSuccess {
                                        codeMessage = "${it.displayName.ifBlank { "GlobalCall user" }} added to your contacts"
                                        code = ""
                                    }
                                    .onFailure { codeMessage = it.message ?: "Could not connect this account" }
                                connecting = false
                            }
                        },
                        enabled = !connecting && code.filter(Char::isLetterOrDigit).length == 14,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("Connect account") }
                    }
                    codeMessage?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Optional: find by verified phone", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Phone OTP requires Firebase SMS billing. GlobalCall ID does not.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(18); phoneResult = null; phoneMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone number") },
                        placeholder = { Text("+8801... / +9665...") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                searchingPhone = true
                                phoneMessage = null
                                runCatching { repository.findUserByPhone(phone) }
                                    .onSuccess {
                                        phoneResult = it
                                        if (it == null) phoneMessage = "No verified GlobalCall user found"
                                    }
                                    .onFailure { phoneMessage = it.message ?: "Could not search this number" }
                                searchingPhone = false
                            }
                        },
                        enabled = !searchingPhone && phone.trim().startsWith("+") && phone.filter(Char::isDigit).length >= 8,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (searchingPhone) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.PersonSearch, null); Spacer(Modifier.width(8.dp)); Text("Find verified account") }
                    }
                    phoneMessage?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        phoneResult?.let { result ->
            item {
                ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(result.photoData, result.displayName, 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.displayName.ifBlank { "GlobalCall user" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (result.callCode.isNotBlank()) result.callCode else "Open GlobalCall on the other account to activate its ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (result.callCode.isNotBlank()) {
                            FilledIconButton(onClick = {
                                scope.launch {
                                    connecting = true
                                    runCatching { repository.connectByCode(result.callCode) }
                                        .onSuccess { phoneMessage = "Account connected"; phoneResult = null }
                                        .onFailure { phoneMessage = it.message ?: "Could not connect" }
                                    connecting = false
                                }
                            }, enabled = !connecting) { Icon(Icons.Default.PersonAdd, "Add to contacts") }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your contacts") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }
        item { Text("Your contacts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        if (filtered.isEmpty()) {
            item { EmptyCard(Icons.Default.Groups, "No contacts yet", "Ask another GlobalCall user for the ID shown on their Profile screen.") }
        } else {
            items(filtered, key = { it.uid }) { person ->
                ContactRow(
                    title = person.displayName.ifBlank { "GlobalCall user" },
                    subtitle = if (person.online) "Online • ${person.callCode.ifBlank { "Connected" }}" else "Offline • ${person.callCode.ifBlank { "Connected" }}",
                    photoData = person.photoData,
                    online = person.online,
                    enabled = person.online && !busy,
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
    profile: AppUser?,
    callCode: String,
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
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("Your GlobalCall ID", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(callCode.ifBlank { "Activating…" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(6.dp))
                    Text("Share this ID. Another user enters it once to add your account and call you whenever you are online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
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
                        Text("Account calling is active", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Your GlobalCall ID works without SMS billing. Your saved photo is visible to connected GlobalCall users.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
