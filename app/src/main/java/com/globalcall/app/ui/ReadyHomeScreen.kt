package com.globalcall.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.globalcall.app.BuildConfig
import com.globalcall.app.ExternalCallRequest
import com.globalcall.app.MainActivity
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallInvite
import com.globalcall.app.model.CallRecord
import com.globalcall.app.model.CallSession
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID

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
    var tab by remember { mutableIntStateOf(0) }
    var people by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var incoming by remember { mutableStateOf<CallInvite?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid) {
        runCatching { repository.setOnline(true) }
        runCatching { repository.publishPhoneDirectory() }
    }

    DisposableEffect(user.uid) {
        val peopleReg = repository.observePeople(onChange = { people = it }, onError = { })
        val callsReg = repository.observeCallHistory(user.uid, onChange = { calls = it }, onError = { })
        val incomingReg = repository.observeIncomingCall(user.uid, onChange = { incoming = it }, onError = { })
        onDispose {
            peopleReg.remove()
            callsReg.remove()
            incomingReg.remove()
        }
    }

    LaunchedEffect(externalCallRequest?.callId) {
        val request = externalCallRequest ?: return@LaunchedEffect
        if (request.action == MainActivity.ACTION_ANSWER) {
            runCatching { repository.loadInvite(request.callId) }.getOrNull()?.let { invite ->
                runCatching { repository.acceptCall(invite) }
                    .onSuccess(onJoinCall)
                    .onFailure { message = it.message }
            }
        }
        onExternalCallHandled()
    }

    fun instantSession(code: String, video: Boolean): CallSession {
        val clean = code.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "").take(32)
        val finalCode = clean.ifBlank { UUID.randomUUID().toString().replace("-", "").take(10).uppercase() }
        return CallSession(
            callId = "instant-$finalCode",
            peerUid = "",
            peerName = "Room $finalCode",
            serverUrl = BuildConfig.MEETING_BASE_URL,
            token = "GlobalCall-$finalCode",
            video = video,
            outgoing = true
        )
    }

    fun directCall(peer: AppUser, video: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            runCatching { repository.startCall(peer, video) }
                .onSuccess(onJoinCall)
                .onFailure { message = it.message ?: "Could not start call" }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlobalCallTopBar(
                    name = user.displayName ?: user.phoneNumber ?: user.email.orEmpty(),
                    subtitle = user.phoneNumber ?: user.email.orEmpty(),
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
                        onDirectCall = ::directCall,
                        onInstantCall = { code, video -> onJoinCall(instantSession(code, video)) }
                    )
                    1 -> PeopleTab(
                        people = people,
                        busy = busy,
                        repository = repository,
                        onDirectCall = ::directCall
                    )
                    else -> ProfileTab(auth)
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
                onAnswer = {
                    scope.launch {
                        runCatching { repository.acceptCall(invite) }
                            .onSuccess { incoming = null; onJoinCall(it) }
                            .onFailure { message = it.message }
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
private fun GlobalCallTopBar(name: String, subtitle: String, onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = .97f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GlobalCall", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF35D39A)))
                }
                Text(name.ifBlank { subtitle }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledIconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Find people") }
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
    onDirectCall: (AppUser, Boolean) -> Unit,
    onInstantCall: (String, Boolean) -> Unit
) {
    var roomCode by remember { mutableStateOf("") }
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
                    Text("Call anyone, instantly", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Private voice & video rooms", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onInstantCall("", true) }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(8.dp)); Text("Video")
                        }
                        FilledTonalButton(onClick = { onInstantCall("", false) }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("Voice")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.uppercase().take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Join with room code") },
                        leadingIcon = { Icon(Icons.Default.Tag, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(enabled = roomCode.trim().length >= 4, onClick = { onInstantCall(roomCode, true) }, modifier = Modifier.weight(1f)) { Text("Join video") }
                        OutlinedButton(enabled = roomCode.trim().length >= 4, onClick = { onInstantCall(roomCode, false) }, modifier = Modifier.weight(1f)) { Text("Join voice") }
                    }
                }
            }
        }
        item { Text("Recent calls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (calls.isEmpty()) {
            item { EmptyCard(Icons.Default.History, "No calls yet", "Your recent calls will appear here.") }
        } else {
            items(calls.take(30), key = { it.id }) { call ->
                val peer = people.firstOrNull { it.uid == call.peerUid(currentUid) }
                ContactRow(
                    title = call.peerName(currentUid).ifBlank { "GlobalCall user" },
                    subtitle = when (call.status) {
                        "ringing" -> "Calling…"
                        "declined" -> "Declined"
                        "accepted" -> "Connected"
                        else -> "Completed"
                    },
                    online = false,
                    enabled = peer != null && !busy,
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
    var phone by remember { mutableStateOf("") }
    var phoneResult by remember { mutableStateOf<AppUser?>(null) }
    var phoneMessage by remember { mutableStateOf<String?>(null) }
    var searchingPhone by remember { mutableStateOf(false) }
    val filtered = remember(people, search) {
        people.filter {
            search.isBlank() || it.displayName.contains(search, true) || it.email.contains(search, true) || it.phoneLast4.contains(search.filter(Char::isDigit))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Phone, null, modifier = Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Find by phone number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Like IMO — enter the verified international number", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
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
                    Button(
                        onClick = {
                            scope.launch {
                                searchingPhone = true
                                phoneMessage = null
                                runCatching { repository.findUserByPhone(phone) }
                                    .onSuccess {
                                        phoneResult = it
                                        if (it == null) phoneMessage = "No GlobalCall user found with this number"
                                    }
                                    .onFailure { phoneMessage = it.message ?: "Could not search this number" }
                                searchingPhone = false
                            }
                        },
                        enabled = !searchingPhone && phone.trim().startsWith("+") && phone.filter(Char::isDigit).length >= 8,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (searchingPhone) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.PersonSearch, null); Spacer(Modifier.width(8.dp)); Text("Find GlobalCall user") }
                    }
                    phoneMessage?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        phoneResult?.let { result ->
            item {
                Text("Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ContactRow(
                    title = result.displayName.ifBlank { "GlobalCall user" },
                    subtitle = if (result.phoneLast4.isNotBlank()) "Verified • •••• ${result.phoneLast4}" else "Verified GlobalCall user",
                    online = result.online,
                    enabled = !busy,
                    onVoice = { onDirectCall(result, false) },
                    onVideo = { onDirectCall(result, true) }
                )
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search people") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }
        item { Text("People", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (filtered.isEmpty()) {
            item { EmptyCard(Icons.Default.Groups, "No contacts yet", "Find someone by their verified phone number above.") }
        } else {
            items(filtered, key = { it.uid }) { person ->
                ContactRow(
                    title = person.displayName.ifBlank { person.email.ifBlank { "GlobalCall user" } },
                    subtitle = if (person.phoneLast4.isNotBlank()) "•••• ${person.phoneLast4}" else if (person.online) "Online" else "GlobalCall user",
                    online = person.online,
                    enabled = !busy,
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
    online: Boolean,
    enabled: Boolean,
    onVoice: () -> Unit,
    onVideo: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(title.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) }
                }
                if (online) Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(Color(0xFF35D39A)))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Column { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ProfileTab(auth: FirebaseAuth) {
    val user = auth.currentUser ?: return
    val identifier = user.phoneNumber ?: user.email.orEmpty()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Surface(modifier = Modifier.size(110.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Text((user.displayName ?: identifier).take(1).uppercase(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.height(16.dp))
        Text(user.displayName ?: "GlobalCall user", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(identifier, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        ElevatedCard(shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (user.phoneNumber != null) Icons.Default.VerifiedUser else Icons.Default.AccountCircle, null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (user.phoneNumber != null) "Verified phone account" else "GlobalCall account", fontWeight = FontWeight.Bold)
                    Text(if (user.phoneNumber != null) "People can find you by your verified number." else "Sign in with phone to become discoverable by number.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { auth.signOut() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out")
        }
    }
}

@Composable
private fun IncomingOverlay(invite: CallInvite, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xF20A0D13)) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (invite.video) "Incoming video call" else "Incoming voice call", color = Color.White.copy(alpha = .7f))
            Spacer(Modifier.height(20.dp))
            Surface(modifier = Modifier.size(120.dp), shape = CircleShape, color = Color(0xFF263248)) {
                Box(contentAlignment = Alignment.Center) { Text(invite.callerName.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(18.dp))
            Text(invite.callerName.ifBlank { "GlobalCall user" }, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
