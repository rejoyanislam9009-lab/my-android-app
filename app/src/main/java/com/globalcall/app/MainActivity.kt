package com.globalcall.app

import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.CallSession
import com.globalcall.app.ui.AuthScreen
import com.globalcall.app.ui.CallScreen
import com.globalcall.app.ui.LoggedInShell
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.google.firebase.FirebaseApp
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
            ?: runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
    }

    if (firebaseApp == null) {
        FirebaseSetupRequiredScreen()
        return
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
                if (updateServer && callId != null) {
                    scope.launch { repository.endCall(callId) }
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
private fun FirebaseSetupRequiredScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "GlobalCall",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Service setup required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This APK was built without the Firebase configuration required for sign-in, contacts and calling. The app is installed correctly and will no longer crash. Add the production google-services.json and rebuild to enable all services.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
