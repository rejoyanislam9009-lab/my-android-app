package com.globalcall.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.globalcall.app.ChatActivity
import com.globalcall.app.MainActivity
import com.globalcall.app.calls.ActiveCallService
import com.globalcall.app.data.GlobalCallRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GlobalCallMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val app = firebaseAppOrNull(this) ?: return
        val auth = runCatching { FirebaseAuth.getInstance(app) }.getOrNull() ?: return
        if (auth.currentUser != null) {
            serviceScope.launch {
                runCatching {
                    GlobalCallRepository(
                        auth = auth,
                        db = FirebaseFirestore.getInstance(app)
                    ).saveFcmToken(token)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = firebaseAppOrNull(this) ?: return
        val data = message.data
        when (data["type"]) {
            "incoming_call" -> {
                val callId = data["callId"].orEmpty()
                if (callId.isBlank()) return
                serviceScope.launch {
                    handleIncomingCall(
                        app = app,
                        callId = callId,
                        callerName = data["callerName"].orEmpty().ifBlank { "GlobalCall user" },
                        callerUid = data["callerUid"].orEmpty(),
                        video = data["video"] != "false"
                    )
                }
            }
            "call_waiting" -> {
                val callId = data["callId"].orEmpty()
                if (callId.isBlank()) return
                showCallWaiting(
                    callId = callId,
                    callerName = data["callerName"].orEmpty().ifBlank { "GlobalCall user" },
                    video = data["video"] != "false"
                )
            }
            "chat_message" -> {
                val senderUid = data["senderUid"].orEmpty()
                if (senderUid.isBlank()) return
                showChatMessage(
                    senderUid = senderUid,
                    senderName = data["senderName"].orEmpty().ifBlank { "GlobalCall contact" },
                    text = data["text"].orEmpty().ifBlank { "New message" }
                )
            }
        }
    }

    private suspend fun handleIncomingCall(
        app: FirebaseApp,
        callId: String,
        callerName: String,
        callerUid: String,
        video: Boolean
    ) {
        val auth = FirebaseAuth.getInstance(app)
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance(app)
        val userRef = db.collection("users").document(uid)

        val localBusy = ActiveCallService.hasAnotherActiveCall(this, callId)
        val profile = runCatching { userRef.get().await() }.getOrNull()
        val state = profile?.getString("callState").orEmpty()
        val currentCallId = profile?.getString("currentCallId").orEmpty()
        val stateAt = profile?.getTimestamp("callStateUpdatedAt")?.seconds ?: 0L
        val age = ((System.currentTimeMillis() / 1000L) - stateAt).coerceAtLeast(0L)
        val serverBusy = currentCallId.isNotBlank() && currentCallId != callId && when (state) {
            "active" -> age < 6 * 60 * 60L
            "calling", "ringing" -> age < 2 * 60L
            else -> false
        }

        if (localBusy || serverBusy) {
            showCallWaiting(callId, callerName, video)
            // Keep the waiting alert visible briefly, then tell the caller that this
            // user is on another call. Never replace/clear the existing active call.
            delay(3_500)
            runCatching {
                val callRef = db.collection("calls").document(callId)
                val call = callRef.get().await()
                if (call.getString("status") == "ringing") {
                    callRef.update(
                        mapOf(
                            "status" to "busy",
                            "endedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            return
        }

        runCatching {
            userRef.set(
                mapOf(
                    "callState" to "ringing",
                    "currentCallId" to callId,
                    "callStateUpdatedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
        showIncomingCall(callId, callerName, callerUid, video)
    }

    private fun showChatMessage(senderUid: String, senderName: String, text: String) {
        createMessageChannel()
        val intent = Intent(this, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ChatActivity.EXTRA_PEER_UID, senderUid)
            putExtra(ChatActivity.EXTRA_PEER_NAME, senderName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            senderUid.hashCode() xor 0x4D5347,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val sender = Person.Builder().setName(senderName).setKey(senderUid).build()
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(senderName)
            .setContentText(text)
            .setStyle(
                NotificationCompat.MessagingStyle(sender)
                    .setConversationTitle(senderName)
                    .addMessage(text, System.currentTimeMillis(), sender)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(senderUid.hashCode(), notification)
    }

    private fun showCallWaiting(callId: String, callerName: String, video: Boolean) {
        createCallWaitingChannel()
        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val returnPending = PendingIntent.getActivity(
            this,
            callId.hashCode() xor 0xCA17,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CALL_WAITING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Call waiting • $callerName")
            .setContentText(
                if (video) "Incoming video call while you're on another call"
                else "Incoming voice call while you're on another call"
            )
            .setContentIntent(returnPending)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000L)
            .build()

        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this)
            .notify(callId.hashCode() xor CALL_WAITING_NOTIFICATION_MASK, notification)
    }

    private fun showIncomingCall(
        callId: String,
        callerName: String,
        callerUid: String,
        video: Boolean
    ) {
        createCallChannel()

        val showIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_CALL_ACTION, MainActivity.ACTION_SHOW_INCOMING)
        }
        val showPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode() xor 0x51A0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_CALL_ACTION, MainActivity.ACTION_ANSWER)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this,
            callId.hashCode() xor 0xCA11,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(callerName)
            .setKey(callerUid.ifBlank { callId })
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerName)
            .setContentText(if (video) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(60_000L)
            .setFullScreenIntent(showPendingIntent, true)
            .setContentIntent(showPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
                ).setIsVideo(video)
            )
            .build()

        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(callId.hashCode(), notification)
    }

    private fun createMessageChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "GlobalCall chat messages"
                enableVibration(true)
            }
        )
    }

    private fun createCallWaitingChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(
                CALL_WAITING_CHANNEL_ID,
                "Call waiting",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when someone calls while another GlobalCall is active"
                setSound(sound, attributes)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun createCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtoneAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming GlobalCall voice and video calls"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(ringtoneUri, ringtoneAttributes)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CALL_CHANNEL_ID = "globalcall_incoming_calls"
        const val MESSAGE_CHANNEL_ID = "globalcall_messages_v2"
        const val CALL_WAITING_CHANNEL_ID = "globalcall_call_waiting_v1"
        private const val CALL_WAITING_NOTIFICATION_MASK = 0x77A1
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE) return
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) return

        val app = firebaseAppOrNull(context) ?: return
        val auth = runCatching { FirebaseAuth.getInstance(app) }.getOrNull() ?: return
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance(app)
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                db.collection("calls").document(callId).update(
                    mapOf(
                        "status" to "declined",
                        "endedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Only clear ringing state when this declined call is actually the user's
            // current call. Declining a waiting call must never erase another active call.
            runCatching {
                val userRef = db.collection("users").document(uid)
                db.runTransaction { tx ->
                    val user = tx.get(userRef)
                    val currentCallId = user.getString("currentCallId").orEmpty()
                    if (currentCallId == callId) {
                        tx.set(
                            userRef,
                            mapOf(
                                "callState" to "idle",
                                "currentCallId" to "",
                                "callStateUpdatedAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                    }
                }.await()
            }

            NotificationManagerCompat.from(context).cancel(callId.hashCode())
            pending.finish()
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.globalcall.app.action.DECLINE_CALL"
        const val EXTRA_CALL_ID = "call_id"
    }
}

private fun firebaseAppOrNull(context: Context): FirebaseApp? =
    FirebaseApp.getApps(context).firstOrNull()
        ?: runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
