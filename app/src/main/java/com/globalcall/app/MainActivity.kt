package com.globalcall.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.globalcall.app.calls.ActiveCallEngineStore
import com.globalcall.app.calls.CallStateHealth
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.data.UserDiscoveryRepository
import com.globalcall.app.model.CallSession
import com.globalcall.app.ui.AuthScreen
import com.globalcall.app.ui.CallScreen
import com.globalcall.app.ui.GuestReadyScreen
import com.globalcall.app.ui.ReadyHomeScreen
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.globalcall.app.ui.theme.ThemeMode
import com.globalcall.app.ui.theme.ThemePreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var externalCallRequest by mutableStateOf<ExternalCallRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readCallIntent(intent)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { CallStateHealth.repair(applicationContext) }
        }

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
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { CallStateHealth.repair(applicationContext) }
        }
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
    val restoredSession = remember { ActiveCallEngineStore.session() }
    val restoredRepository = remember(restoredSession?.callId) {
        if (restoredSession == null) null
        else ensureFirebaseApp(context)?.let { app ->
            val auth = FirebaseAuth.getInstance(app)
            if (auth.currentUser == null) null
            else GlobalCallRepository(auth = auth, db = FirebaseFirestore.getInstance(app))
        }
    }

    var cloudMode by remember { mutableStateOf(initiallySignedIn || externalCallRequest != null || restoredSession != null) }
    var callSession by remember { mutableStateOf(restoredSession) }
    var activeRepository by remember { mutableStateOf(restoredRepository) }

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
    var showAccountTools by remember { mutableStateOf(false) }
    DisposableEffect(runtime.auth) {
        val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
        runtime.auth.addAuthStateListener(listener)
        onDispose { runtime.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch { runCatching { runtime.repository.saveFcmToken(token) } }
            }
            scope.launch {
                runCatching { runtime.repository.repairMyCallState() }
                runCatching { runtime.repository.setOnline(true) }
            }

            if (
                Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(uiContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                (uiContext as? Activity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4120)
            }

            if (
                Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(uiContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                (uiContext as? Activity)?.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 4121)
            }
        }
    }

    if (currentUser == null) {
        AuthScreen(runtime.auth, onGuest = onBackToInstant)
    } else {
        Box(Modifier.fillMaxSize()) {
            ReadyHomeScreen(
                auth = runtime.auth,
                repository = runtime.repository,
                externalCallRequest = externalCallRequest,
                onExternalCallHandled = onExternalCallHandled,
                onJoinCall = { onJoinCall(runtime.repository, it) }
            )
            SmallFloatingActionButton(
                onClick = { showAccountTools = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 92.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Settings, "Account and appearance")
            }
        }

        if (showAccountTools) {
            AccountAppearanceDialog(
                auth = runtime.auth,
                repository = runtime.repository,
                onDismiss = { showAccountTools = false }
            )
        }
    }
}

@Composable
private fun AccountAppearanceDialog(
    auth: FirebaseAuth,
    repository: GlobalCallRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val user = auth.currentUser ?: return
    val discoveryRepository = remember(auth) { UserDiscoveryRepository(auth = auth) }
    var username by remember(user.uid) { mutableStateOf("") }
    var callCode by remember(user.uid) { mutableStateOf("") }
    var recipient by remember(user.uid) { mutableStateOf(user.email.orEmpty()) }
    var loadingDetails by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user.uid) {
        loadingDetails = true
        val codeResult = runCatching { repository.ensureMyCallCode() }
        val usernameResult = runCatching { discoveryRepository.ensureMyUsername() }
        callCode = codeResult.getOrDefault("")
        username = usernameResult.getOrDefault("")
        message = codeResult.exceptionOrNull()?.message ?: usernameResult.exceptionOrNull()?.message
        loadingDetails = false
    }

    fun emailDetails() {
        val target = recipient.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(target).matches()) {
            message = "Enter a valid email address"
            return
        }
        if (username.isBlank() && callCode.isBlank()) {
            message = "Your GlobalCall details are still syncing"
            return
        }
        val body = buildString {
            append("Your GlobalCall account details\n\n")
            append("Name: ${user.displayName ?: "GlobalCall user"}\n")
            if (username.isNotBlank()) append("Username: @$username\n")
            if (callCode.isNotBlank()) append("Backup GlobalCall ID: $callCode\n")
            if (!user.email.isNullOrBlank()) append("Account email: ${user.email}\n")
            append("\nKeep this email so you can find your GlobalCall username and backup ID later.")
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(target)}")
            putExtra(Intent.EXTRA_SUBJECT, "My GlobalCall username and ID")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Email GlobalCall details"))
        }.onFailure {
            message = "No email app is available on this phone"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, null) },
        title = { Text("Account & appearance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Choose how GlobalCall looks on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = ThemePreferences.mode == ThemeMode.SYSTEM,
                        onClick = { ThemePreferences.setMode(context.applicationContext, ThemeMode.SYSTEM) },
                        label = { Text("System") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = ThemePreferences.mode == ThemeMode.LIGHT,
                        onClick = { ThemePreferences.setMode(context.applicationContext, ThemeMode.LIGHT) },
                        label = { Text("Light") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = ThemePreferences.mode == ThemeMode.DARK,
                        onClick = { ThemePreferences.setMode(context.applicationContext, ThemeMode.DARK) },
                        label = { Text("Dark") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        context.startActivity(Intent(context, PrivacySettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Security, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy & blocked users")
                }

                HorizontalDivider()

                Text("Email my account details", fontWeight = FontWeight.Bold)
                Text(
                    if (loadingDetails) "Preparing your username and backup ID…"
                    else buildString {
                        if (username.isNotBlank()) append("@$username")
                        if (username.isNotBlank() && callCode.isNotBlank()) append(" • ")
                        if (callCode.isNotBlank()) append(callCode)
                    }.ifBlank { "Account details unavailable" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it.trim().take(120); message = null },
                    label = { Text("Send details to email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = ::emailDetails,
                    enabled = !loadingDetails,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Email my details")
                }
                Text(
                    "For security, GlobalCall opens your email app with everything filled in. You review it and tap Send; no email password or SMTP key is stored inside the APK.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
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
    )
}
