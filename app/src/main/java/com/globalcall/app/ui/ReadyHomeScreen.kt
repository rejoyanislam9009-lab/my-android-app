package com.globalcall.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    }

    DisposableEffect(user.uid) {
        val peopleReg = repository.observePeople(
            onChange = { people = it },
            onError = { message = "Direct directory is unavailable right now. Room-code calling is still ready." }
        )
        val callsReg = repository.observeCallHistory(
            uid = user.uid,
            onChange = { calls = it },
            onError = { }
        )
        val incomingReg = repository.observeIncomingCall(
            uid = user.uid,
            onChange = { incoming = it },
            onError = { }
        )
        onDispose {
            peopleReg.remove()
            callsReg.remove()
            incomingReg.remove()
        }
    }

    LaunchedEffect(externalCallRequest?.callId) {
        val request = externalCallRequest ?: return@LaunchedEffect
        if (request.action == MainActivity.ACTION_ANSWER) {
            runCatching { repository.loadInvite(request.callId) }
                .getOrNull()
                ?.let { invite ->
                    runCatching { repository.acceptCall(invite) }
                        .onSuccess(onJoinCall)
                        .onFailure { message = it.message }
                }
        }
        onExternalCallHandled()
    }

    fun instantSession(code: String, video: Boolean): CallSession {
        val clean = code.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "").take(32)
        val finalCode = clean.ifBlank {
            UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        }
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

    fun startDirectCall(peer: AppUser, video: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            runCatching { repository.startCall(peer, video) }
                .onSuccess(onJoinCall)
                .onFailure {
                    message = it.message ?: "Could not start the direct call. You can use a room code instead."
                }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PremiumTopBar(
                    name = user.displayName ?: user.email.orEmpty(),
                    email = user.email.orEmpty(),
                    onSearch = { tab = 1 }
                )
            },
            bottomBar = {
                PremiumBottomBar(selected = tab, onSelected = { tab = it })
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> ReadyCallsTab(
                        calls = calls,
                        currentUid = user.uid,
                        people = people,
                        busy = busy,
                        onDirectCall = ::startDirectCall,
                        onInstantCall = { code, video -> onJoinCall(instantSession(code, video)) }
                    )

                    1 -> ReadyPeopleTab(
                        people = people,
                        busy = busy,
                        onCall = ::startDirectCall,
                        onInstantCall = { code, video -> onJoinCall(instantSession(code, video)) }
                    )

                    else -> ReadyProfileTab(auth)
                }

                message?.let {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { message = null }) { Text("OK") }
                        }
                    ) { Text(it) }
                }
            }
        }

        incoming?.let { invite ->
            IncomingCallOverlay(
                invite = invite,
                onAnswer = {
                    scope.launch {
                        runCatching { repository.acceptCall(invite) }
                            .onSuccess {
                                incoming = null
                                onJoinCall(it)
                            }
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
private fun PremiumTopBar(
    name: String,
    email: String,
    onSearch: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "GlobalCall",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
                Text(
                    name.ifBlank { email },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledIconButton(
                onClick = onSearch,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.Search, "Search people")
            }
        }
    }
}

@Composable
private fun PremiumBottomBar(selected: Int, onSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelected(0) },
            icon = { Icon(Icons.Default.PhoneInTalk, null) },
            label = { Text("Calls") }
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { onSelected(1) },
            icon = { Icon(Icons.Default.Groups, null) },
            label = { Text("People") }
        )
        NavigationBarItem(
            selected = selected == 2,
            onClick = { onSelected(2) },
            icon = { Icon(Icons.Default.AccountCircle, null) },
            label = { Text("Profile") }
        )
    }
}

@Composable
private fun ReadyCallsTab(
    calls: List<CallRecord>,
    currentUid: String,
    people: List<AppUser>,
    busy: Boolean,
    onDirectCall: (AppUser, Boolean) -> Unit,
    onInstantCall: (String, Boolean) -> Unit
) {
    var roomCode by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                            ) {
                                Icon(
                                    Icons.Default.PhoneInTalk,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(11.dp).size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Call anyone, instantly",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Private room code • Voice or video",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onInstantCall("", true) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(Icons.Default.Videocam, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Video call", fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = { onInstantCall("", false) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(Icons.Default.Call, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Voice call", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = roomCode,
                            onValueChange = { roomCode = it.uppercase().take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Join with room code") },
                            leadingIcon = { Icon(Icons.Default.Tag, null) },
                            trailingIcon = {
                                if (roomCode.isNotBlank()) {
                                    Icon(Icons.Default.ContentCopy, null)
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                enabled = roomCode.trim().length >= 4,
                                onClick = { onInstantCall(roomCode, true) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Join video") }
                            OutlinedButton(
                                enabled = roomCode.trim().length >= 4,
                                onClick = { onInstantCall(roomCode, false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Join voice") }
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent calls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (calls.isEmpty()) "Ready" else "${calls.size} recent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (calls.isEmpty()) {
            item { EmptyCallsCard() }
        } else {
            items(calls.take(24), key = { it.id }) { call ->
                val peer = people.firstOrNull { it.uid == call.peerUid(currentUid) }
                RecentCallRow(
                    call = call,
                    currentUid = currentUid,
                    canRedial = peer != null && !busy,
                    onVoice = { peer?.let { onDirectCall(it, false) } },
                    onVideo = { peer?.let { onDirectCall(it, true) } }
                )
            }
        }
    }
}

@Composable
private fun EmptyCallsCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(
                    Icons.Default.PhoneInTalk,
                    null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("No calls yet", fontWeight = FontWeight.Bold)
                Text(
                    "Start a video or voice call above. Your recent calls will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentCallRow(
    call: CallRecord,
    currentUid: String,
    canRedial: Boolean,
    onVoice: () -> Unit,
    onVideo: () -> Unit
) {
    val outgoing = call.isOutgoing(currentUid)
    val missed = call.status == "declined" && !outgoing
    val directionIcon = when {
        missed -> Icons.Default.CallMissed
        outgoing -> Icons.Default.CallMade
        else -> Icons.Default.CallReceived
    }
    val statusText = when {
        missed -> "Missed call"
        call.status == "declined" -> "Declined"
        call.status == "ringing" -> if (outgoing) "Calling…" else "Incoming call"
        call.status == "accepted" -> "Connected"
        else -> "Completed"
    }
    val statusColor = if (missed || call.status == "declined") {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Avatar(call.peerName(currentUid).ifBlank { "GlobalCall" }, 52)
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Icon(
                        directionIcon,
                        null,
                        modifier = Modifier.padding(3.dp),
                        tint = statusColor
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    call.peerName(currentUid).ifBlank { "GlobalCall user" },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                    Text(
                        if (call.video) " • Video" else " • Voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(enabled = canRedial, onClick = onVoice) {
                Icon(Icons.Default.Call, "Voice call")
            }
            IconButton(enabled = canRedial, onClick = onVideo) {
                Icon(Icons.Default.Videocam, "Video call")
            }
        }
    }
}

@Composable
private fun ReadyPeopleTab(
    people: List<AppUser>,
    busy: Boolean,
    onCall: (AppUser, Boolean) -> Unit,
    onInstantCall: (String, Boolean) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var roomCode by remember { mutableStateOf("") }
    val filtered = remember(people, search) {
        people.filter {
            search.isBlank() ||
                it.displayName.contains(search, true) ||
                it.email.contains(search, true)
        }
    }
    val online = people.filter { it.online }.take(10)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search people") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )
        }

        if (online.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "Online now",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(online, key = { it.uid }) { person ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(72.dp)
                            ) {
                                Box {
                                    Avatar(person.displayName.ifBlank { person.email }, 58)
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd).size(16.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.background
                                    ) {
                                        Box(
                                            Modifier
                                                .padding(3.dp)
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiary)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    person.displayName.ifBlank { person.email.substringBefore('@') },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                if (filtered.isEmpty()) "Connect by code" else "People",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (filtered.isEmpty()) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(
                                    Icons.Default.Groups,
                                    null,
                                    modifier = Modifier.padding(10.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Share one room code", fontWeight = FontWeight.Bold)
                                Text(
                                    "The other phone joins the same secure room.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = roomCode,
                            onValueChange = { roomCode = it.uppercase().take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Shared room code") },
                            leadingIcon = { Icon(Icons.Default.Tag, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = roomCode.trim().length >= 4,
                            onClick = { onInstantCall(roomCode, true) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Videocam, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Join video room")
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.uid }) { person ->
                ElevatedCard(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Avatar(person.displayName.ifBlank { person.email }, 54)
                            if (person.online) {
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).size(16.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Box(
                                        Modifier
                                            .padding(3.dp)
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                person.displayName.ifBlank { person.email },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (person.online) "Online" else person.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (person.online) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledIconButton(
                            enabled = !busy,
                            onClick = { onCall(person, false) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Call, "Voice call")
                        }
                        Spacer(Modifier.width(6.dp))
                        FilledIconButton(
                            enabled = !busy,
                            onClick = { onCall(person, true) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Videocam, "Video call")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyProfileTab(auth: FirebaseAuth) {
    val user = requireNotNull(auth.currentUser)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Box {
                Avatar(user.displayName ?: user.email.orEmpty(), 104)
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        Modifier
                            .padding(5.dp)
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                user.displayName ?: "GlobalCall user",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                user.email.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("GlobalCall account", fontWeight = FontWeight.Bold)
                            Text(
                                "Voice and video calling ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                            Text("ACTIVE")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    ProfileStatusRow(
                        icon = Icons.Default.CheckCircle,
                        title = "Email",
                        value = if (user.isEmailVerified) "Verified" else "Signed in"
                    )
                    Spacer(Modifier.height(12.dp))
                    ProfileStatusRow(
                        icon = Icons.Default.Lock,
                        title = "Calling",
                        value = "Secure room transport"
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { auth.signOut() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProfileStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(icon, null, modifier = Modifier.padding(8.dp).size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IncomingCallOverlay(
    invite: CallInvite,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "incoming-ring")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "incoming-pulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF07101E)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF1D3A78),
                            Color(0xFF0B1527),
                            Color(0xFF050912)
                        ),
                        radius = 1200f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (invite.video) "Incoming video call" else "Incoming voice call",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(72.dp))

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(154.dp)
                            .graphicsLayer(scaleX = pulse, scaleY = pulse)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                    Avatar(invite.callerName.ifBlank { "GlobalCall user" }, 118)
                }

                Spacer(Modifier.height(26.dp))
                Text(
                    invite.callerName.ifBlank { "GlobalCall user" },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (invite.video) Icons.Default.Videocam else Icons.Default.Call,
                        null,
                        tint = Color(0xFF8DB5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (invite.video) "GlobalCall video • secure room" else "GlobalCall voice • secure room",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallAction(
                        icon = Icons.Default.Call,
                        label = "Decline",
                        background = Color(0xFFE94A4A),
                        rotation = 135f,
                        onClick = onDecline
                    )
                    CallAction(
                        icon = if (invite.video) Icons.Default.Videocam else Icons.Default.PhoneInTalk,
                        label = "Answer",
                        background = Color(0xFF28C76F),
                        onClick = onAnswer
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun CallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    rotation: Float = 0f,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(70.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = background,
                contentColor = Color.White
            )
        ) {
            Icon(
                icon,
                label,
                modifier = Modifier.size(30.dp).graphicsLayer(rotationZ = rotation)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
