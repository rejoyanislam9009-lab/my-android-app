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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
fun CallScreen(session: CallSession, repository: GlobalCallRepository?, onFinish: (Boolean) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val roomCode = remember(session.token) { session.token.removePrefix("GlobalCall-") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var micOn by remember { mutableStateOf(true) }
    var cameraOn by remember { mutableStateOf(session.video) }
    var speakerOn by remember { mutableStateOf(true) }
    var permissionsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && (!session.video || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
    }
    var finished by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    fun finish(updateServer: Boolean) { if (!finished) { finished = true; onFinish(updateServer) } }
    fun sendCommand(script: String) { webViewRef?.evaluateJavascript(script, null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val camera = !session.video || (result[Manifest.permission.CAMERA] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
        permissionsGranted = mic && camera
    }
    LaunchedEffect(Unit) { if (!permissionsGranted) permissionLauncher.launch(buildList { add(Manifest.permission.RECORD_AUDIO); if (session.video) add(Manifest.permission.CAMERA) }.toTypedArray()) }
    DisposableEffect(session.callId, repository) {
        if (session.callId.startsWith("instant-") || repository == null) onDispose { }
        else { val reg = repository.observeCallStatus(session.callId) { if (it == "declined" || it == "ended") finish(false) }; onDispose { reg.remove() } }
    }
    LaunchedEffect(permissionsGranted) { if (permissionsGranted) while (true) { delay(1000); elapsedSeconds++ } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070A10), Color(0xFF101622), Color(0xFF05070B))))) {
        if (permissionsGranted) {
            val meetingUrl = remember(session.token, session.video) {
                "${session.serverUrl.trimEnd('/')}/${Uri.encode(session.token)}#config.prejoinPageEnabled=false&config.disableDeepLinking=true&config.startWithAudioMuted=false&config.startWithVideoMuted=${!session.video}&interfaceConfig.MOBILE_APP_PROMO=false&interfaceConfig.TOOLBAR_ALWAYS_VISIBLE=false"
            }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this; setBackgroundColor(android.graphics.Color.BLACK); settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.databaseEnabled = true; settings.mediaPlaybackRequiresUserGesture = false; settings.cacheMode = WebSettings.LOAD_DEFAULT; settings.allowContentAccess = true; settings.allowFileAccess = false
                    settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/[0-9.]+\\s"), "")
                    CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() { override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = false }
                    webChromeClient = object : WebChromeClient() { override fun onPermissionRequest(request: PermissionRequest?) { request?.grant(request.resources) } }
                    loadUrl(meetingUrl)
                }
            })
            DisposableEffect(Unit) { onDispose { webViewRef?.apply { stopLoading(); loadUrl("about:blank"); clearHistory(); removeAllViews(); destroy() }; webViewRef = null } }
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = Color(0xFF20283A), modifier = Modifier.size(96.dp)) { Box(contentAlignment = Alignment.Center) { Icon(if (session.video) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(44.dp)) } }
                Spacer(Modifier.height(22.dp)); Text("Camera & microphone access", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Allow access to start your live call", color = Color.White.copy(alpha=.68f)); Spacer(Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(buildList { add(Manifest.permission.RECORD_AUDIO); if (session.video) add(Manifest.permission.CAMERA) }.toTypedArray()) }) { Text("Allow and continue") }
            }
        }

        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color(0xB20A0D13)) {
            Row(Modifier.statusBarsPadding().padding(horizontal=16.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={ finish(false) }) { Icon(Icons.Default.KeyboardArrowDown, "Back", tint=Color.White) }
                Column(Modifier.weight(1f), horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(session.peerName.ifBlank { "GlobalCall" }, color=Color.White, fontWeight=FontWeight.Bold, maxLines=1)
                    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF31D07C))); Spacer(Modifier.width(6.dp)); Text("Live • ${formatDuration(elapsedSeconds)}", color=Color.White.copy(alpha=.72f), style=MaterialTheme.typography.labelMedium) }
                }
                IconButton(onClick={ clipboard.setText(AnnotatedString(roomCode)) }) { Icon(Icons.Default.ContentCopy, "Copy room code", tint=Color.White) }
            }
        }

        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color=Color(0xE60B0E14), shape=RoundedCornerShape(topStart=30.dp, topEnd=30.dp)) {
            Column(Modifier.navigationBarsPadding().padding(start=18.dp,end=18.dp,top=16.dp,bottom=18.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                Text("Room $roomCode", color=Color.White.copy(alpha=.55f), style=MaterialTheme.typography.labelMedium); Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically) {
                    LiveControl(if(micOn) Icons.Default.Mic else Icons.Default.MicOff, if(micOn) "Mute" else "Unmute", micOn) { micOn=!micOn; sendCommand("document.querySelector('[aria-label*=microphone i]')?.click()") }
                    LiveControl(if(speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Speaker", speakerOn) { speakerOn=!speakerOn }
                    if(session.video) LiveControl(if(cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera", cameraOn) { cameraOn=!cameraOn; sendCommand("document.querySelector('[aria-label*=camera i]')?.click()") }
                    FilledIconButton(onClick={ finish(true) }, modifier=Modifier.size(64.dp), colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color(0xFFFF3B30), contentColor=Color.White)) { Icon(Icons.Default.CallEnd,"End call",modifier=Modifier.size(30.dp)) }
                }
            }
        }
    }
}

@Composable private fun LiveControl(icon: androidx.compose.ui.graphics.vector.ImageVector,label:String,active:Boolean,onClick:()->Unit){ Column(horizontalAlignment=Alignment.CenterHorizontally){ FilledIconButton(onClick=onClick,modifier=Modifier.size(54.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(active) Color(0xFF262B35) else Color.White,contentColor=if(active) Color.White else Color(0xFF11141A))){Icon(icon,label,modifier=Modifier.size(25.dp))};Spacer(Modifier.height(5.dp));Text(label,color=Color.White.copy(alpha=.78f),style=MaterialTheme.typography.labelSmall) } }
private fun formatDuration(seconds:Int):String="%02d:%02d".format(seconds/60,seconds%60)
