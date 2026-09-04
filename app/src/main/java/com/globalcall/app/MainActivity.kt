package com.globalcall.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.CallSession
import com.globalcall.app.ui.AuthScreen
import com.globalcall.app.ui.CallScreen
import com.globalcall.app.ui.ReadyHomeScreen
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

@Composable
private fun GlobalCallApp(
    externalCallRequest: ExternalCallRequest?,
    onExternalCallHandled: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val firebaseApp = remember {
        FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .build()
            )
    }

    val auth = remember(firebaseApp) { FirebaseAuth.getInstance(firebaseApp) }
    val repository = remember(firebaseApp, auth) {
        GlobalCallRepository(
            auth = auth,
            db = FirebaseFirestore.getInstance(firebaseApp)
        )
    }
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
            session = requireNotNull(callSession),
            repository = repository,
            onFinish = { updateServer ->
                val callId = callSession?.callId
                callSession = null
                if (updateServer && callId != null && !callId.startsWith("instant-")) {
                    scope.launch { repository.endCall(callId) }
                }
            }
        )

        else -> ReadyHomeScreen(
            auth = auth,
            repository = repository,
            externalCallRequest = externalCallRequest,
            onExternalCallHandled = onExternalCallHandled,
            onJoinCall = { callSession = it }
        )
    }
}
