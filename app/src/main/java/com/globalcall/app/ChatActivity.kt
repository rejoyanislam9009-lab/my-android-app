package com.globalcall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.globalcall.app.data.ChatMessage
import com.globalcall.app.data.ChatRepository
import com.globalcall.app.ui.theme.GlobalCallTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val peerUid = intent.getStringExtra(EXTRA_PEER_UID).orEmpty()
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "GlobalCall contact" }
        if (peerUid.isBlank() || FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }
        setContent {
            GlobalCallTheme {
                ChatScreen(peerUid = peerUid, peerName = peerName, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PEER_UID = "globalcall_peer_uid"
        const val EXTRA_PEER_NAME = "globalcall_peer_name"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(peerUid: String, peerName: String, onBack: () -> Unit) {
    val repository = remember { ChatRepository() }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember(peerUid) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember(peerUid) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(peerUid) {
        val registration = repository.observeMessages(
            peerUid = peerUid,
            onChange = { messages = it; error = null },
            onError = { error = "Messages are syncing. Check your connection." }
        )
        onDispose { registration.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(peerName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("GlobalCall Messages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillParentMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("💬", modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.headlineMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Start a conversation", fontWeight = FontWeight.Bold)
                            Text("Messages use internet, not carrier SMS.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, mine = message.senderUid == myUid)
                }
            }

            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 2000) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message $peerName") },
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        enabled = draft.isNotBlank() && !sending,
                        onClick = {
                            val text = draft
                            scope.launch {
                                sending = true
                                runCatching { repository.sendMessage(peerUid, text) }
                                    .onSuccess { draft = ""; error = null }
                                    .onFailure { error = it.message ?: "Could not send message" }
                                sending = false
                            }
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (mine) 20.dp else 5.dp,
                bottomEnd = if (mine) 5.dp else 20.dp
            ),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp).fillMaxWidth(.78f)) {
                Text(message.text, color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                val time = message.createdAt?.toDate()?.let {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it.time))
                }.orEmpty()
                if (time.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
                }
            }
        }
    }
}
