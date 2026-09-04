package com.globalcall.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
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

@OptIn(ExperimentalMaterial3Api::class)
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
            onError = { message = "People directory is limited, but room-code calls still work." }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GlobalCall", fontWeight = FontWeight.Bold)
                        Text(
                            user.displayName ?: user.email.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { tab = 1 }) { Icon(Icons.Default.Search, "People") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Call, null) }, label = { Text("Calls") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.People, null) }, label = { Text("People") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.AccountCircle, null) }, label = { Text("Profile") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> ReadyCallsTab(
                    calls = calls,
                    currentUid = user.uid,
                    onInstantCall = { code, video -> onJoinCall(instantSession(code, video)) }
                )
                1 -> ReadyPeopleTab(
                    people = people,
                    busy = busy,
                    onCall = { peer, video ->
                        scope.launch {
                            busy = true
                            runCatching { repository.startCall(peer, video) }
                                .onSuccess(onJoinCall)
                                .onFailure { message = it.message ?: "Could not start direct call. Use room code instead." }
                            busy = false
                        }
                    },
                    onInstantCall = { code, video -> onJoinCall(instantSession(code, video)) }
                )
                else -> ReadyProfileTab(auth)
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
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(if (invite.video) Icons.Default.Videocam else Icons.Default.Call, null) },
            title = { Text(if (invite.video) "Incoming video call" else "Incoming voice call") },
            text = { Text(invite.callerName.ifBlank { "GlobalCall user" }) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching { repository.acceptCall(invite) }
                            .onSuccess {
                                incoming = null
                                onJoinCall(it)
                            }
                            .onFailure { message = it.message }
                    }
                }) { Text("Answer") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    scope.launch { runCatching { repository.declineCall(invite.id) } }
                    incoming = null
                }) { Text("Decline") }
            }
        )
    }
}

@Composable
private fun ReadyCallsTab(
    calls: List<CallRecord>,
    currentUid: String,
    onInstantCall: (String, Boolean) -> Unit
) {
    var roomCode by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Instant calling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Create a room or enter the same code on another phone. No token server is required.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onInstantCall("", true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Videocam, null)
                            Spacer(Modifier.width(6.dp))
                            Text("New video")
                        }
                        OutlinedButton(
                            onClick = { onInstantCall("", false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, null)
                            Spacer(Modifier.width(6.dp))
                            Text("New voice")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.uppercase().take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Room code") },
                        leadingIcon = { Icon(Icons.Default.Tag, null) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            enabled = roomCode.trim().length >= 4,
                            onClick = { onInstantCall(roomCode, true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Join video") }
                        FilledTonalButton(
                            enabled = roomCode.trim().length >= 4,
                            onClick = { onInstantCall(roomCode, false) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Join voice") }
                    }
                }
            }
        }

        item { Text("Recent calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (calls.isEmpty()) {
            item {
                Text("No cloud call history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(calls.take(20), key = { it.id }) { call ->
                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (call.video) Icons.Default.Videocam else Icons.Default.Call, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(call.peerName(currentUid).ifBlank { "GlobalCall user" }, fontWeight = FontWeight.SemiBold)
                            Text(call.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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
    val filtered = people.filter {
        search.isBlank() || it.displayName.contains(search, true) || it.email.contains(search, true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search people") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
        }
        if (filtered.isEmpty()) {
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Direct directory unavailable?", fontWeight = FontWeight.SemiBold)
                        Text("You can still call anyone by sharing one room code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = roomCode,
                            onValueChange = { roomCode = it.uppercase().take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Shared room code") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = roomCode.trim().length >= 4,
                            onClick = { onInstantCall(roomCode, true) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Join room") }
                    }
                }
            }
        } else {
            items(filtered, key = { it.uid }) { person ->
                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(person.displayName, 48)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.displayName.ifBlank { person.email }, fontWeight = FontWeight.SemiBold)
                            Text(person.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = !busy, onClick = { onCall(person, false) }) { Icon(Icons.Default.Call, "Voice call") }
                        IconButton(enabled = !busy, onClick = { onCall(person, true) }) { Icon(Icons.Default.Videocam, "Video call") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyProfileTab(auth: FirebaseAuth) {
    val user = requireNotNull(auth.currentUser)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        Avatar(user.displayName ?: user.email.orEmpty(), 92)
        Spacer(Modifier.height(14.dp))
        Text(user.displayName ?: "GlobalCall user", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(user.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
        ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Your GlobalCall account", fontWeight = FontWeight.SemiBold)
                Text("Voice and video rooms are ready on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { auth.signOut() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }
    }
}
