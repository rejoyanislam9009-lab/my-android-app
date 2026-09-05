package com.globalcall.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.globalcall.app.ExternalCallRequest
import com.globalcall.app.MainActivity
import com.globalcall.app.R
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallInvite
import com.globalcall.app.model.CallRecord
import com.globalcall.app.model.CallSession
import com.globalcall.app.model.MainTab
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggedInShell(
    auth: FirebaseAuth,
    repository: GlobalCallRepository,
    externalCallRequest: ExternalCallRequest?,
    onExternalCallHandled: () -> Unit,
    onJoinCall: (CallSession) -> Unit
) {
    val user = requireNotNull(auth.currentUser)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(MainTab.Calls) }
    var people by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var incoming by remember { mutableStateOf<CallInvite?>(null) }
    var blocked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(user.uid) {
        runCatching { repository.setOnline(true) }
        runCatching { FirebaseMessaging.getInstance().token.await() }
            .onSuccess { runCatching { repository.saveFcmToken(it) } }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
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
            peopleReg.remove()
            callReg.remove()
            incomingReg.remove()
            blockedReg.remove()
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
                            busy = true
                            message = null
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
                            busy = true
                            message = null
                            runCatching { repository.startCall(peer, video) }
                                .onSuccess { onJoinCall(it) }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    },
                    onBlock = { peer, shouldBlock ->
                        scope.launch {
                            runCatching {
                                if (shouldBlock) repository.blockUser(peer.uid)
                                else repository.unblockUser(peer.uid)
                            }.onFailure { message = it.message }
                        }
                    },
                    onReport = { peer ->
                        scope.launch {
                            runCatching {
                                repository.reportUser(peer.uid, "User reported from Android app")
                            }.onSuccess {
                                message = "Report submitted"
                            }.onFailure {
                                message = it.message
                            }
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
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
            icon = {
                Icon(if (invite.video) Icons.Default.Videocam else Icons.Default.Call, null)
            },
            title = {
                Text(
                    if (invite.video) stringResource(R.string.incoming_video_call)
                    else stringResource(R.string.incoming_voice_call)
                )
            },
            text = { Text(invite.callerName.ifBlank { "GlobalCall user" }) },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { repository.acceptCall(invite) }
                                .onSuccess {
                                    incoming = null
                                    onJoinCall(it)
                                }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.accept))
                }
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
                ) {
                    Text(stringResource(R.string.decline))
                }
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
        Text(
            stringResource(R.string.recent_calls),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.recent_calls_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        if (calls.isEmpty()) {
            EmptyState(
                Icons.Default.Call,
                stringResource(R.string.no_calls_yet),
                stringResource(R.string.no_calls_message)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(calls, key = { it.id }) { call ->
                    val peerUid = call.peerUid(currentUid)
                    val peerName = call.peerName(currentUid).ifBlank { "GlobalCall user" }
                    val peer = people.firstOrNull { it.uid == peerUid }
                        ?: AppUser(uid = peerUid, displayName = peerName)
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
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(peer.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    peer.displayName.ifBlank { peer.email.ifBlank { "GlobalCall user" } },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            missed -> Icons.Default.PhoneMissed
                            outgoing -> Icons.Default.CallMade
                            else -> Icons.Default.CallReceived
                        },
                        null,
                        modifier = Modifier.size(15.dp),
                        tint = if (missed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        callStatusText(call, currentUid),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                call.createdAt?.let {
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(it.seconds * 1000)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
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
            search.isBlank() ||
                it.displayName.contains(search, ignoreCase = true) ||
                it.email.contains(search, ignoreCase = true)
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
            trailingIcon = if (search.isNotEmpty()) {
                {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.people),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Default.People,
                stringResource(R.string.no_people_found),
                stringResource(R.string.no_people_message)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
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
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Avatar(person.displayName)
                if (person.online && !isBlocked) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    person.displayName.ifBlank { "GlobalCall user" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isBlocked) stringResource(R.string.blocked) else person.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlocked) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (person.bio.isNotBlank() && !isBlocked) {
                    Text(
                        person.bio,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isBlocked) {
                IconButton(enabled = !busy, onClick = { onCall(person, false) }) {
                    Icon(Icons.Default.Call, stringResource(R.string.voice_call))
                }
                IconButton(enabled = !busy, onClick = { onCall(person, true) }) {
                    Icon(Icons.Default.Videocam, stringResource(R.string.video_call))
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isBlocked) stringResource(R.string.unblock)
                                else stringResource(R.string.block)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Block, null) },
                        onClick = {
                            menuOpen = false
                            onBlock(person, !isBlocked)
                        }
                    )
                    if (!isBlocked) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report)) },
                            leadingIcon = { Icon(Icons.Default.Report, null) },
                            onClick = {
                                menuOpen = false
                                onReport(person)
                            }
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, 86)
                Spacer(Modifier.height(10.dp))
                Text(
                    name.ifBlank { user.email.orEmpty() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(user.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.edit_profile), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(50) },
                        label = { Text(stringResource(R.string.display_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(120) },
                        label = { Text(stringResource(R.string.bio)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
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
                    ) {
                        Text(stringResource(R.string.save_changes))
                    }
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column {
                    SettingsRow(
                        Icons.Default.Notifications,
                        stringResource(R.string.call_notifications),
                        stringResource(R.string.call_notifications_desc)
                    )
                    HorizontalDivider()
                    SettingsRow(
                        Icons.Default.Block,
                        stringResource(R.string.blocked_users),
                        "$blockedCount ${stringResource(R.string.blocked_users_count)}"
                    )
                    HorizontalDivider()
                    SettingsRow(
                        Icons.Default.Security,
                        stringResource(R.string.privacy_security),
                        stringResource(R.string.privacy_security_desc)
                    )
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
                ) {
                    Icon(Icons.Default.MarkEmailUnread, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.verify_email))
                }
            }
        }

        item {
            OutlinedButton(onClick = { auth.signOut() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.logout))
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
                                FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(user.uid)
                                    .delete()
                                    .await()
                                user.delete().await()
                            }.onFailure {
                                onMessage(it.message ?: "Account deletion requires a recent sign-in")
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon,
                null,
                modifier = Modifier.padding(24.dp).size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun Avatar(name: String, size: Int = 52) {
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "G" }

    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.34f).sp
        )
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

private suspend fun loadInvite(callId: String, currentUid: String): CallInvite? {
    val doc = FirebaseFirestore.getInstance()
        .collection("calls")
        .document(callId)
        .get()
        .await()

    if (!doc.exists() || doc.getString("calleeUid") != currentUid || doc.getString("status") != "ringing") {
        return null
    }

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
