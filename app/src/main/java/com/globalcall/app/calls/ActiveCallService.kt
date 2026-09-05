package com.globalcall.app.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.globalcall.app.MainActivity
import com.globalcall.app.data.GlobalCallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps an accepted GlobalCall voice/video call in foreground priority while the
 * activity is backgrounded. The WebRTC engine still lives in the app process,
 * but Android now treats that process as an active microphone/camera call instead
 * of an ordinary background activity.
 */
class ActiveCallService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GlobalCall:activeCall"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()

        if (action == ACTION_STOP) {
            clearState(callId)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (callId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty()
            .ifBlank { "GlobalCall contact" }
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val connected = intent?.getBooleanExtra(EXTRA_CONNECTED, false) == true

        saveState(callId, peerName, video)
        val notification = buildNotification(callId, peerName, video, connected)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                if (video) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        } else 0

        ServiceCompat.startForeground(
            this,
            ACTIVE_NOTIFICATION_ID,
            notification,
            serviceType
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) runCatching { wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(
        callId: String,
        peerName: String,
        video: Boolean,
        connected: Boolean
    ): android.app.Notification {
        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val returnPending = PendingIntent.getActivity(
            this,
            callId.hashCode() xor 0xAC71,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(this, ActiveCallActionReceiver::class.java).apply {
            action = ActiveCallActionReceiver.ACTION_END_ACTIVE_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val endPending = PendingIntent.getBroadcast(
            this,
            callId.hashCode() xor 0xE0D,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = when {
            connected && video -> "Video call in progress"
            connected -> "Voice call in progress"
            video -> "Video call connecting"
            else -> "Voice call connecting"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(peerName)
            .setContentText("$status • tap to return")
            .setContentIntent(returnPending)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endPending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown only while a GlobalCall voice or video call is active"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun saveState(callId: String, peerName: String, video: Boolean) {
        prefs(this).edit()
            .putString(KEY_CALL_ID, callId)
            .putString(KEY_PEER_NAME, peerName)
            .putBoolean(KEY_VIDEO, video)
            .apply()
    }

    private fun clearState(callId: String) {
        val preferences = prefs(this)
        val current = preferences.getString(KEY_CALL_ID, "").orEmpty()
        if (callId.isBlank() || current.isBlank() || current == callId) {
            preferences.edit().clear().apply()
        }
    }

    companion object {
        private const val CHANNEL_ID = "globalcall_active_call_v1"
        private const val ACTIVE_NOTIFICATION_ID = 0x47434C
        private const val PREFS = "globalcall_active_call_state"
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_PEER_NAME = "peer_name"
        private const val KEY_VIDEO = "video"

        const val EXTRA_CALL_ID = "active_call_id"
        private const val EXTRA_PEER_NAME = "active_peer_name"
        private const val EXTRA_VIDEO = "active_video"
        private const val EXTRA_CONNECTED = "active_connected"
        private const val ACTION_START = "com.globalcall.app.action.START_ACTIVE_CALL"
        private const val ACTION_UPDATE = "com.globalcall.app.action.UPDATE_ACTIVE_CALL"
        private const val ACTION_STOP = "com.globalcall.app.action.STOP_ACTIVE_CALL"

        fun start(
            context: Context,
            callId: String,
            peerName: String,
            video: Boolean,
            connected: Boolean = false
        ) {
            if (callId.isBlank()) return
            val intent = Intent(context, ActiveCallService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_VIDEO, video)
                putExtra(EXTRA_CONNECTED, connected)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun update(
            context: Context,
            callId: String,
            peerName: String,
            video: Boolean,
            connected: Boolean
        ) {
            if (callId.isBlank()) return
            val intent = Intent(context, ActiveCallService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_VIDEO, video)
                putExtra(EXTRA_CONNECTED, connected)
            }
            runCatching { context.applicationContext.startService(intent) }
                .onFailure { ContextCompat.startForegroundService(context.applicationContext, intent) }
        }

        fun stop(context: Context, callId: String = "") {
            val intent = Intent(context, ActiveCallService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_CALL_ID, callId)
            }
            runCatching { context.applicationContext.startService(intent) }
            if (callId.isBlank() || activeCallId(context) == callId) {
                prefs(context).edit().clear().apply()
            }
        }

        fun activeCallId(context: Context): String =
            prefs(context).getString(KEY_CALL_ID, "").orEmpty()

        fun hasAnotherActiveCall(context: Context, incomingCallId: String): Boolean {
            val current = activeCallId(context)
            return current.isNotBlank() && current != incomingCallId
        }

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

class ActiveCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_END_ACTIVE_CALL) return
        val callId = intent.getStringExtra(ActiveCallService.EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { GlobalCallRepository().endCall(callId) }
            ActiveCallService.stop(context, callId)
            NotificationManagerCompat.from(context).cancel(callId.hashCode())
            pending.finish()
        }
    }

    companion object {
        const val ACTION_END_ACTIVE_CALL = "com.globalcall.app.action.END_ACTIVE_CALL"
    }
}
