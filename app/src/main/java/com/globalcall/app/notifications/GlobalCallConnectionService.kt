package com.globalcall.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.globalcall.app.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class GlobalCallConnectionService : Service() {
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private var callsRegistration: ListenerRegistration? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentUid: String? = null
    private var lastIncomingId: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification("Connecting…"))

        val app = FirebaseApp.getApps(this).firstOrNull()
            ?: runCatching { FirebaseApp.initializeApp(this) }.getOrNull()
        if (app == null) {
            stopSelf()
            return
        }

        auth = FirebaseAuth.getInstance(app)
        db = FirebaseFirestore.getInstance(app)
        currentUid = auth?.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            stopSelf()
            return
        }

        listenForNetwork()
        listenForIncomingCalls()
        updatePresence(hasUsableInternet())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            updatePresence(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (auth?.currentUser == null) {
            updatePresence(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun listenForNetwork() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updatePresence(hasUsableInternet())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updatePresence(
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }

            override fun onLost(network: Network) {
                updatePresence(hasUsableInternet())
            }
        }
        networkCallback = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    private fun hasUsableInternet(): Boolean {
        val manager = connectivity ?: (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updatePresence(online: Boolean) {
        val uid = currentUid ?: return
        val firestore = db ?: return
        firestore.collection("users").document(uid).set(
            mapOf(
                "online" to online,
                "lastSeen" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        val notification = serviceNotification(
            if (online) "Online • ready for calls" else "Waiting for internet"
        )
        NotificationManagerCompat.from(this).notify(SERVICE_NOTIFICATION_ID, notification)
    }

    private fun listenForIncomingCalls() {
        val uid = currentUid ?: return
        val firestore = db ?: return
        callsRegistration?.remove()
        callsRegistration = firestore.collection("calls")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val incoming = snapshot.documents
                    .filter {
                        it.getString("calleeUid") == uid &&
                            it.getString("status") == "ringing"
                    }
                    .maxByOrNull { it.getTimestamp("createdAt")?.seconds ?: 0L }

                if (incoming == null) {
                    lastIncomingId?.let { NotificationManagerCompat.from(this).cancel(it.hashCode()) }
                    lastIncomingId = null
                    return@addSnapshotListener
                }

                if (incoming.id == lastIncomingId) return@addSnapshotListener
                lastIncomingId?.let { NotificationManagerCompat.from(this).cancel(it.hashCode()) }
                lastIncomingId = incoming.id
                showIncomingCall(
                    callId = incoming.id,
                    callerName = incoming.getString("callerName").orEmpty().ifBlank { "GlobalCall user" },
                    callerUid = incoming.getString("callerUid").orEmpty(),
                    video = incoming.getBoolean("video") ?: true
                )
            }
    }

    private fun serviceNotification(status: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            SERVICE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("GlobalCall is active")
            .setContentText(status)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showIncomingCall(
        callId: String,
        callerName: String,
        callerUid: String,
        video: Boolean
    ) {
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

        val notification = NotificationCompat.Builder(this, GlobalCallMessagingService.CALL_CHANNEL_ID)
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
        if (allowed) {
            NotificationManagerCompat.from(this).notify(callId.hashCode(), notification)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                "GlobalCall background connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps GlobalCall online and ready to receive calls"
                setSound(null, null)
                enableVibration(false)
            }
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtoneAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(
                GlobalCallMessagingService.CALL_CHANNEL_ID,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming GlobalCall voice and video calls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(ringtoneUri, ringtoneAttributes)
                enableVibration(true)
            }
        )
    }

    override fun onDestroy() {
        callsRegistration?.remove()
        callsRegistration = null
        networkCallback?.let { callback ->
            runCatching { connectivity?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        updatePresence(false)
        super.onDestroy()
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "globalcall_background_connection"
        const val SERVICE_NOTIFICATION_ID = 9101
        private const val ACTION_STOP = "com.globalcall.app.action.STOP_CONNECTION_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, GlobalCallConnectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GlobalCallConnectionService::class.java).setAction(ACTION_STOP)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { context.stopService(Intent(context, GlobalCallConnectionService::class.java)) }
        }
    }
}
