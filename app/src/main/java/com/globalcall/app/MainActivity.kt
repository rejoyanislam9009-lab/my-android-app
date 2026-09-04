package com.globalcall.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallInvite
import com.globalcall.app.model.CallRecord
import com.globalcall.app.model.CallSession
import com.globalcall.app.model.MainTab
import com.globalcall.app.notifications.GlobalCallMessagingService
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.SurfaceViewRenderer
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var externalCallRequest by mutableStateOf<ExternalCallRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readCallIntent(intent)
        setContent {
            GlobalCallTheme {
                GlobalCallApp(
                    externalCallRequest = externalCallRequest,
                    onExternalCallHandled = { externalCallRequest = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readCallIntent(intent)
    }

    private fun readCallIntent(intent: Intent?) {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (callId.isNotBlank()) {
            externalCallRequest = ExternalCallRequest(
                callId = callId,
                action = intent?.getStringExtra(EXTRA_CALL_ACTION).orEmpty()
            )
        }
    }

    companion object {
        const val EXTRA_CALL_ID = "globalcall_call_id"
        const val EXTRA_CALL_ACTION = "globalcall_call_action"
        const val ACTION_ANSWER = "answer"
    }
}

data class ExternalCallRequest(val callId: String, val action: String)

@Composable
private fun GlobalCallApp(
    externalCallRequest: ExternalCallRequest?,
    onExternalCallHandled: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val repository = remember { GlobalCallRepository(auth) }
    val scope = rememberCoroutineScope()
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var callSession by remember { mutableStateOf<CallSession?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    when {
        currentUser == null -> AuthScreen(auth)
        callSession != null -> CallScreen(
            session = callSession!!,
            repository = repository,
            onFinish = { updateServer ->
                val id = callSession?.callId
                callSession = null
                if (updateServer && id != null) {
                    scope.launch { repository.endCall(id) }
                }
            }
        )
        else -> LoggedInShell(
            auth = auth,
            repository = repository,
            externalCallRequest = externalCallRequest,
            onExternalCallHandled = onExternalCallHandled,
            onJoinCall = { callSession = it }
        )
    }
}

@Composable
private fun AuthScreen(auth: FirebaseAuth) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var createMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("GlobalCall", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.welcome), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !createMode,
                        onClick = { createMode = false; error = null },
                        label = { Text(stringResource(R.string.sign_in)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = createMode,
                        onClick = { createMode = true; error = null },
                        label = { Text(stringResource(R.string.create_account)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (createMode) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(50) },
                        label = { Text(stringResource(R.string.display_name)) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text(stringResource(R.string.email)) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                info?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            info = null
                            try {
                                if (createMode) {
                                    require(displayName.trim().length >= 2) { "Enter your name" }
                                    val result = auth.createUserWithEmailAndPassword(email, password).await()
                                    val user = requireNotNull(result.user)
                                    user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()).await()
                                    db.collection("users").document(user.uid).set(
                                        mapOf(
                                            "uid" to user.uid,
                                            "displayName" to displayName.trim(),
                                            "email" to email.lowercase(Locale.ROOT),
                                            "bio" to "",
                                            "locale" to Locale.getDefault().toLanguageTag(),
                                            "online" to true,
                                            "createdAt" to FieldValue.serverTimestamp(),
                                            "lastSeen" to FieldValue.serverTimestamp()
                                        )
                                    ).await()
                                } else {
                                    auth.signInWithEmailAndPassword(email, password).await()
                                }
                            } catch (t: Throwable) {
                                error = t.message ?: "Unable to continue"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading && email.isNotBlank() && password.length >= 6 && (!createMode || displayName.trim().length >= 2),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text(if (createMode) stringResource(R.string.create_account) else stringResource(R.string.sign_in))
                }

                if (!createMode) {
                    TextButton(
                        onClick = {
                            if (email.isBlank()) {
                                error = "Enter your email first"
                            } else {
                                scope.launch {
                                    runCatching { auth.sendPasswordResetEmail(email).await() }
                                        .onSuccess { info = "Password reset email sent" }
                                        .onFailure { error = it.message }
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.forgot_password)) }
                }
            }
        }
    }
}

@Composable
private fun LoggedInShell(
    auth: FirebaseAuth,
    repository: GlobalCallRepository,
    externalCallRequest: ExternalCallRequest?,
    onExternalCallHandled: () -> Unit,
    onJoinCall: (CallSession) -> Unit
) {
    val user = requireNotNull(auth.currentUser)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(MainTab.Calls) }
    var people by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var incoming by remember { mutableStateOf<CallInvite?>(null) }
    var blocked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(user.uid) {
        runCatching { repository.setOnline(true) }
        runCatching { FirebaseMessaging.getInstance().token.await() }
            .onSuccess { runCatching { repository.saveFcmToken(it) } }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(user.uid) {
        val peopleReg = repository.observePeople({ people = it }, { message = it.message })
        val callReg = repository.observeCallHistory(user.uid, { calls = it }, { message = it.message })
        val incomingReg = repository.observeIncomingCall(user.uid, { incoming = it }, { message = it.message })
        val blockedReg = repository.observeBlockedUsers(user.uid) { blocked = it }
        onDispose {
            peopleReg.remove(); callReg.remove(); incomingReg.remove(); blockedReg.remove()
        }
    }

    LaunchedEffect(externalCallRequest?.callId, user.uid) {
        val request = externalCallRequest ?: return@LaunchedEffect
        if (request.action == MainActivity.ACTION_ANSWER) {
            val invite = loadInvite(request.callId, user.uid)
            if (invite != null) {
                busy = true
                runCatching { repository.acceptCall(invite) }
                    .onSuccess { onJoinCall(it) }
                    .onFailure { message = it.message }
                busy = false
            }
        }
        onExternalCallHandled()
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
                    IconButton(onClick = { tab = MainTab.People }) {
                        Icon(Icons.Default.Search, stringResource(R.string.search_people))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.Calls,
                    onClick = { tab = MainTab.Calls },
                    icon = { Icon(Icons.Default.Call, null) },
                    label = { Text(stringResource(R.string.calls)) }
                )
                NavigationBarItem(
                    selected = tab == MainTab.People,
                    onClick = { tab = MainTab.People },
                    icon = { Icon(Icons.Default.People, null) },
                    label = { Text(stringResource(R.string.people)) }
                )
                NavigationBarItem(
                    selected = tab == MainTab.Profile,
                    onClick = { tab = MainTab.Profile },
                    icon = { Icon(Icons.Default.AccountCircle, null) },
                    label = { Text(stringResource(R.string.profile)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.Calls -> CallsScreen(
                    currentUid = user.uid,
                    calls = calls,
                    people = people,
                    busy = busy,
                    onCall = { peer, video ->
                        scope.launch {
                            busy = true; message = null
                            runCatching { repository.startCall(peer, video) }
                                .onSuccess { onJoinCall(it) }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }
                )
                MainTab.People -> PeopleScreen(
                    people = people,
                    blocked = blocked,
                    busy = busy,
                    onCall = { peer, video ->
                        scope.launch {
                            busy = true; message = null
                            runCatching { repository.startCall(peer, video) }
                                .onSuccess { onJoinCall(it) }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    },
                    onBlock = { peer, shouldBlock ->
                        scope.launch {
                            runCatching {
                                if (shouldBlock) repository.blockUser(peer.uid) else repository.unblockUser(peer.uid)
                            }.onFailure { message = it.message }
                        }
                    },
                    onReport = { peer ->
                        scope.launch {
                            runCatching { repository.reportUser(peer.uid, "User reported from Android app") }
                                .onSuccess { message = "Report submitted" }
                                .onFailure { message = it.message }
                        }
                    }
                )
                MainTab.Profile -> ProfileScreen(
                    auth = auth,
                    repository = repository,
                    blockedCount = blocked.size,
                    onMessage = { message = it }
                )
            }

            message?.let {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 6.dp
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { message = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }
        }
    }

    incoming?.let { invite ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(if (invite.video) Icons.Default.Videocam else Icons.Default.Call, null) },
            title = { Text(if (invite.video) stringResource(R.string.incoming_video_call) else stringResource(R.string.incoming_voice_call)) },
            text = { Text(invite.callerName.ifBlank { "GlobalCall user" }) },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { repository.acceptCall(invite) }
                                .onSuccess { incoming = null; onJoinCall(it) }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }
                ) { Text(stringResource(R.string.accept)) }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            runCatching { repository.declineCall(invite.id) }
                            incoming = null
                        }
                    }
                ) { Text(stringResource(R.string.decline)) }
            }
        )
    }
}

@Composable
private fun CallsScreen(
    currentUid: String,
    calls: List<CallRecord>,
    people: List<AppUser>,
    busy: Boolean,
    onCall: (AppUser, Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.recent_calls), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.recent_calls_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        if (calls.isEmpty()) {
            EmptyState(Icons.Default.Call, stringResource(R.string.no_calls_yet), stringResource(R.string.no_calls_message))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(calls, key = { it.id }) { call ->
                    val peerUid = call.peerUid(currentUid)
                    val peerName = call.peerName(currentUid).ifBlank { "GlobalCall user" }
                    val peer = people.firstOrNull { it.uid == peerUid } ?: AppUser(uid = peerUid, displayName = peerName)
                    CallHistoryCard(call, currentUid, peer, busy, onCall)
                }
            }
        }
    }
}

@Composable
private fun CallHistoryCard(
    call: CallRecord,
    currentUid: String,
    peer: AppUser,
    busy: Boolean,
    onCall: (AppUser, Boolean) -> Unit
) {
    val outgoing = call.isOutgoing(currentUid)
    val missed = !outgoing && call.status in listOf("declined", "ringing")
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(peer.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.displayName.ifBlank { peer.email.ifBlank { "GlobalCall user" } }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            missed -> Icons.Default.PhoneMissed
                            outgoing -> Icons.Default.CallMade
                            else -> Icons.Default.CallReceived
                        },
                        null,
                        modifier = Modifier.size(15.dp),
                        tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        callStatusText(call, currentUid),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                call.createdAt?.let {
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.seconds * 1000)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            FilledTonalIconButton(enabled = !busy, onClick = { onCall(peer, false) }) {
                Icon(Icons.Default.Call, stringResource(R.string.voice_call))
            }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(enabled = !busy, onClick = { onCall(peer, true) }) {
                Icon(Icons.Default.Videocam, stringResource(R.string.video_call))
            }
        }
    }
}

@Composable
private fun PeopleScreen(
    people: List<AppUser>,
    blocked: Set<String>,
    busy: Boolean,
    onCall: (AppUser, Boolean) -> Unit,
    onBlock: (AppUser, Boolean) -> Unit,
    onReport: (AppUser) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(people, search) {
        people.filter {
            search.isBlank() || it.displayName.contains(search, true) || it.email.contains(search, true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_people)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (search.isNotEmpty()) {{ IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, null) } }} else null,
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.people), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            EmptyState(Icons.Default.People, stringResource(R.string.no_people_found), stringResource(R.string.no_people_message))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(filtered, key = { it.uid }) { person ->
                    PersonCard(
                        person = person,
                        isBlocked = person.uid in blocked,
                        busy = busy,
                        onCall = onCall,
                        onBlock = onBlock,
                        onReport = onReport
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: AppUser,
    isBlocked: Boolean,
    busy: Boolean,
    onCall: (AppUser, Boolean) -> Unit,
    onBlock: (AppUser, Boolean) -> Unit,
    onReport: (AppUser) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(person.displayName)
                if (person.online && !isBlocked) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.displayName.ifBlank { "GlobalCall user" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (isBlocked) stringResource(R.string.blocked) else person.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (person.bio.isNotBlank() && !isBlocked) Text(person.bio, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!isBlocked) {
                IconButton(enabled = !busy, onClick = { onCall(person, false) }) { Icon(Icons.Default.Call, stringResource(R.string.voice_call)) }
                IconButton(enabled = !busy, onClick = { onCall(person, true) }) { Icon(Icons.Default.Videocam, stringResource(R.string.video_call)) }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isBlocked) stringResource(R.string.unblock) else stringResource(R.string.block)) },
                        leadingIcon = { Icon(Icons.Default.Block, null) },
                        onClick = { menuOpen = false; onBlock(person, !isBlocked) }
                    )
                    if (!isBlocked) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report)) },
                            leadingIcon = { Icon(Icons.Default.Report, null) },
                            onClick = { menuOpen = false; onReport(person) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    auth: FirebaseAuth,
    repository: GlobalCallRepository,
    blockedCount: Int,
    onMessage: (String?) -> Unit
) {
    val user = requireNotNull(auth.currentUser)
    val scope = rememberCoroutineScope()
    var name by remember(user.uid) { mutableStateOf(user.displayName.orEmpty()) }
    var bio by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, size = 86)
                Spacer(Modifier.height(10.dp))
                Text(name.ifBlank { user.email.orEmpty() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(user.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.edit_profile), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(50) }, label = { Text(stringResource(R.string.display_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = bio, onValueChange = { bio = it.take(120) }, label = { Text(stringResource(R.string.bio)) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                runCatching { repository.updateProfile(name, bio) }
                                    .onSuccess { onMessage("Profile updated") }
                                    .onFailure { onMessage(it.message) }
                                saving = false
                            }
                        },
                        enabled = !saving && name.trim().length >= 2,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.save_changes)) }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column {
                    SettingsRow(Icons.Default.Notifications, stringResource(R.string.call_notifications), stringResource(R.string.call_notifications_desc))
                    HorizontalDivider()
                    SettingsRow(Icons.Default.Block, stringResource(R.string.blocked_users), "$blockedCount ${stringResource(R.string.blocked_users_count)}")
                    HorizontalDivider()
                    SettingsRow(Icons.Default.Security, stringResource(R.string.privacy_security), stringResource(R.string.privacy_security_desc))
                }
            }
        }
        if (!user.isEmailVerified) {
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { user.sendEmailVerification().await() }
                                .onSuccess { onMessage("Verification email sent") }
                                .onFailure { onMessage(it.message) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.MarkEmailUnread, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.verify_email)) }
            }
        }
        item {
            OutlinedButton(onClick = { auth.signOut() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.logout))
            }
        }
        item {
            TextButton(onClick = { deleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_warning)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        deleteDialog = false
                        scope.launch {
                            runCatching {
                                FirebaseFirestore.getInstance().collection("users").document(user.uid).delete().await()
                                user.delete().await()
                            }.onFailure { onMessage(it.message ?: "Account deletion requires a recent sign-in") }
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = {}) { Icon(icon, null) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, modifier = Modifier.padding(24.dp).size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Avatar(name: String, size: Int = 52) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "G" }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = (size * 0.34f).sp)
    }
}

private fun callStatusText(call: CallRecord, currentUid: String): String {
    val outgoing = call.isOutgoing(currentUid)
    return when (call.status) {
        "declined" -> if (outgoing) "Declined" else "Missed call"
        "ringing" -> if (outgoing) "No answer" else "Missed call"
        "accepted", "ended" -> if (outgoing) "Outgoing call" else "Incoming call"
        else -> call.status.replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun CallScreen(
    session: CallSession,
    repository: GlobalCallRepository,
    onFinish: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = remember(session.callId) { LiveKit.create(context.applicationContext) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var localVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var micEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(session.video) }
    var speakerEnabled by remember { mutableStateOf(session.video) }
    var callStatus by remember { mutableStateOf(if (session.outgoing) "ringing" else "accepted") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    fun finish(updateServer: Boolean) {
        if (!finished) {
            finished = true
            room.disconnect()
            onFinish(updateServer)
        }
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var permissionsGranted by remember {
        mutableStateOf(hasPermission(Manifest.permission.RECORD_AUDIO) && (!session.video || hasPermission(Manifest.permission.CAMERA)))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO] ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        val camera = !session.video || (result[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA))
        permissionsGranted = mic && camera
    }

    DisposableEffect(session.callId) {
        val reg = repository.observeCallStatus(session.callId) { status ->
            callStatus = status
            if (status == "declined" || status == "ended") finish(false)
        }
        onDispose { reg.remove() }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            val permissions = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (session.video) add(Manifest.permission.CAMERA)
            }.toTypedArray()
            permissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(permissionsGranted, session.callId) {
        if (!permissionsGranted) return@LaunchedEffect
        try {
            launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> if (event.track is VideoTrack) remoteVideo = event.track as VideoTrack
                        is RoomEvent.TrackUnsubscribed -> if (event.track == remoteVideo) remoteVideo = null
                        else -> Unit
                    }
                }
            }
            room.connect(session.serverUrl, session.token)
            room.localParticipant.setMicrophoneEnabled(true)
            room.localParticipant.setCameraEnabled(session.video)
            if (session.video) {
                localVideo = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerEnabled
            connected = true
        } catch (t: Throwable) {
            error = t.message ?: "Call connection failed"
        }
    }

    LaunchedEffect(callStatus) {
        if (callStatus == "accepted") {
            while (true) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }

    DisposableEffect(room) {
        onDispose {
            room.disconnect()
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF090B10))) {
        if (session.video && remoteVideo != null) {
            VideoSurface(room, remoteVideo, Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(session.peerName, 110)
                Spacer(Modifier.height(18.dp))
                Text(session.peerName, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        error != null -> error!!
                        !connected -> stringResource(R.string.connecting)
                        callStatus == "ringing" -> stringResource(R.string.ringing)
                        else -> formatDuration(elapsedSeconds)
                    },
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        if (session.video && localVideo != null) {
            Card(
                Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 14.dp).width(112.dp).height(160.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                VideoSurface(room, localVideo, Modifier.fillMaxSize())
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 28.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (session.video && remoteVideo != null) {
                Text(session.peerName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (callStatus == "ringing") stringResource(R.string.ringing) else formatDuration(elapsedSeconds), color = Color.White.copy(alpha = 0.8f))
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            color = Color(0xCC1B1E26),
            shape = RoundedCornerShape(30.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CallControlButton(if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff, if (micEnabled) stringResource(R.string.mute) else stringResource(R.string.unmute)) {
                    scope.launch {
                        micEnabled = !micEnabled
                        room.localParticipant.setMicrophoneEnabled(micEnabled)
                    }
                }
                CallControlButton(if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, stringResource(R.string.speaker)) {
                    speakerEnabled = !speakerEnabled
                    audioManager.isSpeakerphoneOn = speakerEnabled
                }
                if (session.video) {
                    CallControlButton(if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, stringResource(R.string.camera)) {
                        scope.launch {
                            cameraEnabled = !cameraEnabled
                            room.localParticipant.setCameraEnabled(cameraEnabled)
                            localVideo = if (cameraEnabled) room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack else null
                        }
                    }
                }
                FilledIconButton(
                    onClick = { finish(true) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                    modifier = Modifier.size(58.dp)
                ) { Icon(Icons.Default.CallEnd, stringResource(R.string.end_call), modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
private fun CallControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) { Icon(icon, label) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VideoSurface(room: Room, track: VideoTrack?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember(room) { SurfaceViewRenderer(context).also { room.initVideoRenderer(it) } }
    DisposableEffect(track, renderer) {
        track?.addRenderer(renderer)
        onDispose { track?.removeRenderer(renderer) }
    }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    AndroidView(factory = { renderer }, modifier = modifier)
}

private suspend fun loadInvite(callId: String, currentUid: String): CallInvite? {
    val doc = FirebaseFirestore.getInstance().collection("calls").document(callId).get().await()
    if (!doc.exists() || doc.getString("calleeUid") != currentUid || doc.getString("status") != "ringing") return null
    return CallInvite(
        id = doc.id,
        callerUid = doc.getString("callerUid").orEmpty(),
        callerName = doc.getString("callerName").orEmpty(),
        calleeUid = currentUid,
        calleeName = doc.getString("calleeName").orEmpty(),
        status = "ringing",
        video = doc.getBoolean("video") ?: true
    )
}

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
