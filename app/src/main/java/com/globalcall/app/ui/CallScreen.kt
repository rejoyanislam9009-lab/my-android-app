package com.globalcall.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.globalcall.app.data.GlobalCallRepository
import com.globalcall.app.model.CallSession
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CallScreen(
    session: CallSession,
    repository: GlobalCallRepository? = null,
    onFinish: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val roomCode = remember(session.token) { session.token.removePrefix("GlobalCall-") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!session.video || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        )
    }
    var finished by remember { mutableStateOf(false) }

    fun finish(updateServer: Boolean) {
        if (!finished) {
            finished = true
            onFinish(updateServer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val camera = !session.video || (result[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
        permissionsGranted = mic && camera
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (session.video) add(Manifest.permission.CAMERA)
                }.toTypedArray()
            )
        }
    }

    DisposableEffect(session.callId, repository) {
        if (session.callId.startsWith("instant-") || repository == null) {
            onDispose { }
        } else {
            val registration = repository.observeCallStatus(session.callId) { status ->
                if (status == "declined" || status == "ended") finish(false)
            }
            onDispose { registration.remove() }
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            while (true) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080B12))) {
        if (permissionsGranted) {
            val meetingUrl = remember(session.token, session.video) {
                val base = session.serverUrl.trimEnd('/')
                val encodedRoom = Uri.encode(session.token)
                "$base/$encodedRoom#config.prejoinPageEnabled=false&config.disableDeepLinking=true&config.startWithAudioMuted=false&config.startWithVideoMuted=${!session.video}&interfaceConfig.MOBILE_APP_PROMO=false"
            }

            var webViewRef by remember { mutableStateOf<WebView?>(null) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.allowContentAccess = true
                        settings.allowFileAccess = false
                        settings.userAgentString = settings.userAgentString
                            .replace("; wv", "")
                            .replace(Regex("Version/[0-9.]+\\s"), "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }
                        }
                        loadUrl(meetingUrl)
                    }
                }
            )

            DisposableEffect(Unit) {
                onDispose {
                    webViewRef?.apply {
                        stopLoading()
                        loadUrl("about:blank")
                        clearHistory()
                        removeAllViews()
                        destroy()
                    }
                    webViewRef = null
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(18.dp))
                Text("Camera and microphone permission required", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (session.video) add(Manifest.permission.CAMERA)
                        }.toTypedArray()
                    )
                }) { Text("Allow permissions") }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 16.dp, end = 16.dp),
            color = Color(0xD9191D28),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (session.video) "GlobalCall video" else "GlobalCall voice",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Room $roomCode  •  ${formatDuration(elapsedSeconds)}",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(roomCode)) }) {
                    Icon(Icons.Default.ContentCopy, "Copy room code", tint = Color.White)
                }
            }
        }

        FilledIconButton(
            onClick = { finish(true) },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFFE53935),
                contentColor = Color.White
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(64.dp)
        ) {
            Icon(Icons.Default.CallEnd, "End call", modifier = Modifier.size(30.dp))
        }
    }
}

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
