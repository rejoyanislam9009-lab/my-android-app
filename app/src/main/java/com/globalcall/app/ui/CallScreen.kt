package com.globalcall.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val instantCall = session.callId.startsWith("instant-") || repository == null
    val roomCode = remember(session.token) { session.token.removePrefix("GlobalCall-") }

    var callStatus by remember {
        mutableStateOf(
            when {
                instantCall -> "accepted"
                session.outgoing -> "ringing"
                else -> "accepted"
            }
        )
    }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED &&
                (!session.video || ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED)
        )
    }

    fun finish(updateServer: Boolean) {
        if (!finished) {
            finished = true
            onFinish(updateServer)
        }
    }

    BackHandler { finish(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED)
        val camera = !session.video || (result[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED))
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
        if (instantCall) {
            onDispose { }
        } else {
            val registration = requireNotNull(repository).observeCallStatus(session.callId) { status ->
                callStatus = status
                if (status == "declined" || status == "ended") {
                    finish(false)
                }
            }
            onDispose { registration.remove() }
        }
    }

    val mediaReady = permissionsGranted && (instantCall || callStatus == "accepted")

    LaunchedEffect(mediaReady) {
        if (mediaReady) {
            elapsedSeconds = 0
            while (true) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080D))
    ) {
        when {
            !permissionsGranted -> PermissionGate(
                video = session.video,
                onRequest = {
                    permissionLauncher.launch(
                        buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (session.video) add(Manifest.permission.CAMERA)
                        }.toTypedArray()
                    )
                },
                onEnd = { finish(true) }
            )

            !mediaReady -> OutgoingCallingScreen(
                session = session,
                status = callStatus,
                onEnd = { finish(true) }
            )

            else -> {
                val meetingUrl = remember(session.token, session.video) {
                    val base = session.serverUrl.trimEnd('/')
                    val encodedRoom = Uri.encode(session.token)
                    "$base/$encodedRoom#config.prejoinPageEnabled=false&config.disableDeepLinking=true&config.startWithAudioMuted=false&config.startWithVideoMuted=${!session.video}&config.disableInviteFunctions=true&interfaceConfig.MOBILE_APP_PROMO=false"
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
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    pageLoading = true
                                    pageError = null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    pageLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        pageLoading = false
                                        pageError = "Unable to connect to the live room"
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    val allowed = request?.resources.orEmpty().filter {
                                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                    }.toTypedArray()
                                    if (allowed.isNotEmpty()) {
                                        request?.grant(allowed)
                                    } else {
                                        request?.deny()
                                    }
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

                LiveCallTopBar(
                    session = session,
                    elapsedSeconds = elapsedSeconds,
                    roomCode = roomCode,
                    onCopy = { clipboard.setText(AnnotatedString(roomCode)) }
                )

                if (pageLoading) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xDD111722),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp,
                                color = Color(0xFF9FB3FF)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Connecting live call…", color = Color.White)
                        }
                    }
                }

                pageError?.let { error ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = Color(0xEE171B25),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(error, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Check the internet connection and try the room again.",
                                color = Color.White.copy(alpha = 0.68f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                LiveCallEndButton(onEnd = { finish(true) })
            }
        }
    }
}

@Composable
private fun PermissionGate(
    video: Boolean,
    onRequest: () -> Unit,
    onEnd: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF19345F), Color(0xFF09111E), Color(0xFF05080D)),
                    radius = 1100f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.09f)) {
                Icon(
                    if (video) Icons.Default.Videocam else Icons.Default.PhoneInTalk,
                    null,
                    tint = Color(0xFF9FB3FF),
                    modifier = Modifier.padding(22.dp).size(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (video) "Camera & microphone access" else "Microphone access",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "GlobalCall needs permission to start the live ${if (video) "video" else "voice"} call.",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onRequest) { Text("Allow & continue") }
        }
        LiveCallEndButton(onEnd = onEnd)
    }
}

@Composable
private fun OutgoingCallingScreen(
    session: CallSession,
    status: String,
    onEnd: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "calling")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "calling-pulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF193C73), Color(0xFF0B1628), Color(0xFF05080D)),
                    radius = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (session.video) "Video call" else "Voice call",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(78.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(166.dp)
                        .graphicsLayer(scaleX = pulse, scaleY = pulse)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                )
                Avatar(session.peerName.ifBlank { "GlobalCall" }, 126)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                session.peerName.ifBlank { "GlobalCall user" },
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF72D6FF))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status == "ringing") "Calling…" else "Connecting…",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Waiting for ${session.peerName.ifBlank { "the other person" }} to answer",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        LiveCallEndButton(onEnd = onEnd)
    }
}

@Composable
private fun LiveCallTopBar(
    session: CallSession,
    elapsedSeconds: Int,
    roomCode: String,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, start = 14.dp, end = 14.dp),
        color = Color(0xD910151E),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color(0xFF27C977).copy(alpha = 0.18f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF36E18B))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "LIVE",
                        color = Color(0xFF8FF1B8),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    session.peerName.ifBlank {
                        if (session.video) "GlobalCall video" else "GlobalCall voice"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${formatDuration(elapsedSeconds)} • Room $roomCode",
                        color = Color.White.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, "Copy room code", tint = Color.White)
            }
        }
    }
}

@Composable
private fun LiveCallEndButton(onEnd: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onEnd,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFE84545),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(66.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        "End call",
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "End",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
