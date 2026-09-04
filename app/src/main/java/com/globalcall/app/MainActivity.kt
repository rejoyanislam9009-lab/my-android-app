package com.globalcall.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GlobalCallApp()
            }
        }
    }
}

data class AppUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = ""
)

data class CallInvite(
    val id: String,
    val callerUid: String,
    val callerName: String,
    val calleeUid: String,
    val calleeName: String,
    val status: String
)

data class CallSession(
    val callId: String,
    val peerName: String,
    val serverUrl: String,
    val token: String
)

@Composable
private fun GlobalCallApp() {
    val auth = remember { FirebaseAuth.getInstance() }
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
            onEnd = {
                FirebaseFirestore.getInstance().collection("calls")
                    .document(callSession!!.callId)
                    .update(
                        mapOf(
                            "status" to "ended",
                            "endedAt" to FieldValue.serverTimestamp()
                        )
                    )
                callSession = null
            }
        )
        else -> HomeScreen(
            auth = auth,
            onJoinCall = { callSession = it }
        )
    }
}

@Composable
private fun AuthScreen(auth: FirebaseAuth) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("GlobalCall", style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.display_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                enabled = !loading && email.isNotBlank() && password.length >= 6,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            auth.signInWithEmailAndPassword(email, password).await()
                        } catch (t: Throwable) {
                            error = t.message ?: "Unable to sign in"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator() else Text(stringResource(R.string.sign_in))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                enabled = !loading && displayName.isNotBlank() && email.isNotBlank() && password.length >= 6,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val result = auth.createUserWithEmailAndPassword(email, password).await()
                            val user = requireNotNull(result.user)
                            user.updateProfile(
                                UserProfileChangeRequest.Builder()
                                    .setDisplayName(displayName.trim())
                                    .build()
                            ).await()
                            db.collection("users").document(user.uid).set(
                                mapOf(
                                    "uid" to user.uid,
                                    "displayName" to displayName.trim(),
                                    "email" to email.lowercase(Locale.ROOT),
                                    "locale" to Locale.getDefault().toLanguageTag(),
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        } catch (t: Throwable) {
                            error = t.message ?: "Unable to create account"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.create_account))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    auth: FirebaseAuth,
    onJoinCall: (CallSession) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val user = requireNotNull(auth.currentUser)
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var incoming by remember { mutableStateOf<CallInvite?>(null) }
    var search by remember { mutableStateOf("") }
    var loadingCall by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(user.uid) {
        var usersRegistration: ListenerRegistration? = null
        var callsRegistration: ListenerRegistration? = null

        usersRegistration = db.collection("users")
            .addSnapshotListener { snapshot, _ ->
                people = snapshot?.documents.orEmpty()
                    .mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        if (uid == user.uid) null else AppUser(
                            uid = uid,
                            displayName = doc.getString("displayName").orEmpty(),
                            email = doc.getString("email").orEmpty()
                        )
                    }
            }

        callsRegistration = db.collection("calls")
            .whereEqualTo("calleeUid", user.uid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, _ ->
                incoming = snapshot?.documents?.firstOrNull()?.let { doc ->
                    CallInvite(
                        id = doc.id,
                        callerUid = doc.getString("callerUid").orEmpty(),
                        callerName = doc.getString("callerName").orEmpty(),
                        calleeUid = doc.getString("calleeUid").orEmpty(),
                        calleeName = doc.getString("calleeName").orEmpty(),
                        status = doc.getString("status").orEmpty()
                    )
                }
            }

        onDispose {
            usersRegistration?.remove()
            callsRegistration?.remove()
        }
    }

    val filtered = remember(people, search) {
        if (search.isBlank()) people else people.filter {
            it.displayName.contains(search, ignoreCase = true) ||
                it.email.contains(search, ignoreCase = true)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GlobalCall", style = MaterialTheme.typography.headlineMedium)
                    Text(user.displayName ?: user.email.orEmpty())
                }
                TextButton(onClick = { auth.signOut() }) {
                    Text(stringResource(R.string.logout))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.search_people)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.people), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.uid }) { person ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(person.displayName.ifBlank { "GlobalCall user" }, style = MaterialTheme.typography.titleMedium)
                                Text(person.email, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                enabled = !loadingCall,
                                onClick = {
                                    scope.launch {
                                        loadingCall = true
                                        message = null
                                        try {
                                            val callRef = db.collection("calls").document()
                                            callRef.set(
                                                mapOf(
                                                    "callerUid" to user.uid,
                                                    "callerName" to (user.displayName ?: user.email.orEmpty()),
                                                    "calleeUid" to person.uid,
                                                    "calleeName" to person.displayName,
                                                    "participantUids" to listOf(user.uid, person.uid),
                                                    "roomName" to "call_${callRef.id}",
                                                    "status" to "ringing",
                                                    "createdAt" to FieldValue.serverTimestamp()
                                                )
                                            ).await()
                                            val token = requestCallToken(auth, callRef.id)
                                            onJoinCall(
                                                CallSession(
                                                    callId = callRef.id,
                                                    peerName = person.displayName.ifBlank { person.email },
                                                    serverUrl = token.first,
                                                    token = token.second
                                                )
                                            )
                                        } catch (t: Throwable) {
                                            message = t.message ?: "Could not start call"
                                        } finally {
                                            loadingCall = false
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.video_call))
                            }
                        }
                    }
                }
            }
        }
    }

    incoming?.let { invite ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.incoming_call)) },
            text = { Text(invite.callerName.ifBlank { "GlobalCall user" }) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        loadingCall = true
                        message = null
                        try {
                            db.collection("calls").document(invite.id)
                                .update(
                                    mapOf(
                                        "status" to "accepted",
                                        "acceptedAt" to FieldValue.serverTimestamp()
                                    )
                                ).await()
                            val token = requestCallToken(auth, invite.id)
                            incoming = null
                            onJoinCall(
                                CallSession(
                                    callId = invite.id,
                                    peerName = invite.callerName,
                                    serverUrl = token.first,
                                    token = token.second
                                )
                            )
                        } catch (t: Throwable) {
                            message = t.message ?: "Could not accept call"
                        } finally {
                            loadingCall = false
                        }
                    }
                }) {
                    Text(stringResource(R.string.accept))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    db.collection("calls").document(invite.id).update(
                        mapOf(
                            "status" to "declined",
                            "endedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    incoming = null
                }) {
                    Text(stringResource(R.string.decline))
                }
            }
        )
    }
}

@Composable
private fun CallScreen(
    session: CallSession,
    onEnd: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = remember(session.callId) { LiveKit.create(context.applicationContext) }
    var localVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var micEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(true) }

    fun hasMediaPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var permissionsGranted by remember { mutableStateOf(hasMediaPermissions()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(permissionsGranted, session.callId) {
        if (!permissionsGranted) return@LaunchedEffect
        try {
            launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> {
                            if (event.track is VideoTrack) remoteVideo = event.track as VideoTrack
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            if (event.track == remoteVideo) remoteVideo = null
                        }
                        else -> Unit
                    }
                }
            }
            room.connect(session.serverUrl, session.token)
            room.localParticipant.setMicrophoneEnabled(true)
            room.localParticipant.setCameraEnabled(true)
            localVideo = room.localParticipant
                .getTrackPublication(Track.Source.CAMERA)
                ?.track as? VideoTrack
            connected = true
        } catch (t: Throwable) {
            error = t.message ?: "Call connection failed"
        }
    }

    DisposableEffect(room) {
        onDispose { room.disconnect() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoSurface(
            room = room,
            track = remoteVideo ?: localVideo,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(session.peerName, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    error != null -> error!!
                    connected -> "Connected"
                    else -> "Connecting…"
                },
                color = Color.White
            )
        }

        if (remoteVideo != null && localVideo != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp)
                    .fillMaxWidth(0.32f)
            ) {
                VideoSurface(
                    room = room,
                    track = localVideo,
                    modifier = Modifier.height(180.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = {
                scope.launch {
                    micEnabled = !micEnabled
                    room.localParticipant.setMicrophoneEnabled(micEnabled)
                }
            }) {
                Text(if (micEnabled) "Mute" else "Unmute", color = Color.White)
            }
            OutlinedButton(onClick = {
                scope.launch {
                    cameraEnabled = !cameraEnabled
                    room.localParticipant.setCameraEnabled(cameraEnabled)
                    localVideo = room.localParticipant
                        .getTrackPublication(Track.Source.CAMERA)
                        ?.track as? VideoTrack
                }
            }) {
                Text(if (cameraEnabled) "Camera off" else "Camera on", color = Color.White)
            }
            Button(onClick = {
                room.disconnect()
                onEnd()
            }) {
                Text(stringResource(R.string.end_call))
            }
        }
    }
}

@Composable
private fun VideoSurface(
    room: Room,
    track: VideoTrack?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(room) {
        SurfaceViewRenderer(context).also { room.initVideoRenderer(it) }
    }

    DisposableEffect(track, renderer) {
        track?.addRenderer(renderer)
        onDispose { track?.removeRenderer(renderer) }
    }

    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

private suspend fun requestCallToken(
    auth: FirebaseAuth,
    callId: String
): Pair<String, String> = withContext(Dispatchers.IO) {
    val user = requireNotNull(auth.currentUser) { "Not signed in" }
    val idToken = user.getIdToken(false).await().token ?: error("Missing Firebase ID token")
    val endpoint = BuildConfig.TOKEN_SERVER_URL
    require(!endpoint.contains("YOUR_DOMAIN")) {
        "Configure TOKEN_SERVER_URL in app/build.gradle.kts"
    }

    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 12_000
        readTimeout = 12_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Bearer $idToken")
    }

    val body = JSONObject().put("callId", callId).toString()
    connection.outputStream.use { it.write(body.toByteArray()) }

    val code = connection.responseCode
    val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
        .bufferedReader()
        .use { it.readText() }

    if (code !in 200..299) error("Token server error $code: $responseText")

    val json = JSONObject(responseText)
    json.getString("serverUrl") to json.getString("participantToken")
}
