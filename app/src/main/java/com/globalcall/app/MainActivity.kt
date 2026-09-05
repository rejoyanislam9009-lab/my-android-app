package com.globalcall.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.CallSession
import com.globalcall.app.ui.AuthScreen
import com.globalcall.app.ui.CallScreen
import com.globalcall.app.ui.GuestReadyScreen
import com.globalcall.app.ui.ReadyHomeScreen
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

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
        const val ACTION_SHOW_INCOMING = "show_incoming"
        const val ACTION_ANSWER = "answer"
    }
}

data class ExternalCallRequest(
    val callId: String,
    val action: String
)

private data class CloudRuntime(
    val auth: FirebaseAuth,
    val repository: GlobalCallRepository
)

private fun ensureFirebaseApp(context: Context): FirebaseApp? =
    FirebaseApp.getApps(context).firstOrNull()
        ?: runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .build()
            )
        }.getOrNull()

@Composable
private fun GlobalCallApp(
    externalCallRequest: ExternalCallRequest?,
    onExternalCallHandled: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val initiallySignedIn = remember {
        ensureFirebaseApp(context)?.let { FirebaseAuth.getInstance(it).currentUser != null } ?: false
    }
    var cloudMode by remember { mutableStateOf(initiallySignedIn || externalCallRequest != null) }
    var callSession by remember { mutableStateOf<CallSession?>(null) }
    var activeRepository by remember { mutableStateOf<GlobalCallRepository?>(null) }

    LaunchedEffect(externalCallRequest?.callId) {
        if (externalCallRequest != null) cloudMode = true
    }

    val session = callSession
    if (session != null) {
        CallScreen(
            session = session,
            repository = activeRepository,
            onFinish = { updateServer ->
                val repository = activeRepository
                callSession = null
                activeRepository = null
                if (updateServer && repository != null && !session.callId.startsWith("instant-")) {
                    scope.launch { repository.endCall(session.callId) }
                }
            }
        )
        return
    }

    if (!cloudMode) {
        GuestReadyScreen(
            onJoinCall = {
                activeRepository = null
                callSession = it
            },
            onSignIn = { cloudMode = true }
        )
        return
    }

    CloudAccountHost(
        externalCallRequest = externalCallRequest,
        onExternalCallHandled = onExternalCallHandled,
        onBackToInstant = { cloudMode = false },
        onJoinCall = { repository, sessionToJoin ->
            activeRepository = repository
            callSession = sessionToJoin
        }
    )
}

@Composable
private fun CloudAccountHost(
    externalCallRequest: ExternalCallRequest?,
    onExternalCallHandled: () -> Unit,
    onBackToInstant: () -> Unit,
    onJoinCall: (GlobalCallRepository, CallSession) -> Unit
) {
    val uiContext = LocalContext.current
    val appContext = uiContext.applicationContext
    val scope = rememberCoroutineScope()
    val runtimeResult = remember {
        runCatching {
            val firebaseApp = requireNotNull(ensureFirebaseApp(appContext)) { "Firebase could not initialize" }
            val auth = FirebaseAuth.getInstance(firebaseApp)
            CloudRuntime(
                auth = auth,
                repository = GlobalCallRepository(
                    auth = auth,
                    db = FirebaseFirestore.getInstance(firebaseApp)
                )
            )
        }
    }

    val runtime = runtimeResult.getOrNull()
    if (runtime == null) {
        CloudUnavailableScreen(
            message = runtimeResult.exceptionOrNull()?.message.orEmpty(),
            onBack = onBackToInstant
        )
        return
    }

    var currentUser by remember(runtime.auth) { mutableStateOf(runtime.auth.currentUser) }
    DisposableEffect(runtime.auth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        runtime.auth.addAuthStateListener(listener)
        onDispose { runtime.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            // Push-first background architecture: do not keep an idle foreground service
            // alive just to show presence. Save the current FCM token so real incoming-call
            // pushes can wake the app without a permanent status notification.
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch { runCatching { runtime.repository.saveFcmToken(token) } }
            }
            scope.launch { runCatching { runtime.repository.setOnline(true) } }

            if (
                Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(uiContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                (uiContext as? Activity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4120)
            }
        }
    }

    if (currentUser == null) {
        AuthScreen(runtime.auth, onGuest = onBackToInstant)
    } else {
        ReadyHomeScreen(
            auth = runtime.auth,
            repository = runtime.repository,
            externalCallRequest = externalCallRequest,
            onExternalCallHandled = onExternalCallHandled,
            onJoinCall = { onJoinCall(runtime.repository, it) }
        )
    }
}

@Composable
private fun CloudUnavailableScreen(
    message: String,
    onBack: () -> Unit
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Cloud account unavailable",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "GlobalCall could not restore your account connection on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    message.take(180),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack) { Text("Use without account") }
        }
    }
}
