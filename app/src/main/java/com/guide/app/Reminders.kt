package com.guide.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

object ReminderScheduler {
    private const val ALARM_CHANNEL_ID = "guide_alarm_v5"
    private const val STATUS_CHANNEL_ID = "guide_alarm_status_v3"
    private const val STATUS_NOTIFICATION_ID = 73001
    const val ACTION_STOP_ALARM = "com.guide.app.action.STOP_ALARM"

    fun scheduleAll(context: Context, store: GuideStore = GuideStore(context)) {
        store.routines().forEach {
            if (it.alarmEnabled) scheduleDaily(context, "routine:${it.id}", it.title, "Routine • ${it.category}", it.hour, it.minute)
            else cancel(context, "routine:${it.id}")
        }
        store.meals().forEach {
            if (it.alarmEnabled) scheduleDaily(context, "meal:${it.id}", it.title, it.note.ifBlank { "Meal time" }, it.hour, it.minute)
            else cancel(context, "meal:${it.id}")
        }
        store.alarms().forEach {
            if (it.enabled) scheduleDaily(
                context = context,
                key = "alarm:${it.id}",
                title = it.title,
                body = "Guide alarm",
                hour = it.hour,
                minute = it.minute,
                ringtoneUri = it.ringtoneUri,
                soundEnabled = it.soundEnabled,
                vibrateEnabled = it.vibrateEnabled
            ) else cancel(context, "alarm:${it.id}")
        }
        PrayerScheduler.scheduleAll(context, store)
        refreshAlarmIndicator(context, store)
    }

    fun scheduleDaily(
        context: Context,
        key: String,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        ringtoneUri: String = "",
        soundEnabled: Boolean = true,
        vibrateEnabled: Boolean = true
    ) {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", key.startsWith("alarm:"))
        refreshIndicatorSoon(context)
    }

    fun scheduleOneShot(
        context: Context,
        key: String,
        title: String,
        body: String,
        triggerAt: Long,
        ringtoneUri: String = "",
        soundEnabled: Boolean = true,
        vibrateEnabled: Boolean = true,
        prayerName: String = "",
        showAsAlarmClock: Boolean = false
    ) {
        scheduleAt(context, key, title, body, triggerAt, false, 0, 0, ringtoneUri, soundEnabled, vibrateEnabled, prayerName, showAsAlarmClock)
        refreshIndicatorSoon(context)
    }

    fun test(context: Context) {
        scheduleOneShot(context, "test_alarm", "Guide test alarm", "Your alarm sound and vibration are working.", System.currentTimeMillis() + 10_000L)
    }

    private fun scheduleAt(
        context: Context,
        key: String,
        title: String,
        body: String,
        triggerAt: Long,
        daily: Boolean,
        hour: Int,
        minute: Int,
        ringtoneUri: String,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean,
        prayerName: String,
        showAsAlarmClock: Boolean
    ) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, key, title, body, daily, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, prayerName, PendingIntent.FLAG_UPDATE_CURRENT)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (showAsAlarmClock && exactAllowed) {
            val showIntent = PendingIntent.getActivity(
                context, key.hashCode() xor 0x2A71,
                Intent(context, AlarmActivity::class.java).apply {
                    putExtra("key", key); putExtra("title", title); putExtra("body", body)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pending)
        } else if (exactAllowed) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun cancel(context: Context, key: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(context, key.hashCode(), Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { alarm.cancel(pending); pending.cancel() }
        refreshIndicatorSoon(context)
    }

    fun stopAnyActiveAlarm(context: Context): Boolean {
        if (!AlarmSoundPlayer.isActive()) return false
        AlarmSoundPlayer.stop()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        refreshAlarmIndicator(context)
        return true
    }

    fun exactAlarmAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    private fun pendingIntent(
        context: Context,
        key: String,
        title: String,
        body: String,
        daily: Boolean,
        hour: Int,
        minute: Int,
        ringtoneUri: String,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean,
        prayerName: String,
        flag: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("key", key); putExtra("title", title); putExtra("body", body); putExtra("daily", daily)
            putExtra("hour", hour); putExtra("minute", minute); putExtra("ringtoneUri", ringtoneUri)
            putExtra("soundEnabled", soundEnabled); putExtra("vibrateEnabled", vibrateEnabled); putExtra("prayerName", prayerName)
        }
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) manager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_ID, "Guide alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Active Guide alarm notifications"; enableVibration(false); setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        if (manager.getNotificationChannel(STATUS_CHANNEL_ID) == null) manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL_ID, "Guide alarm status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the next enabled Guide alarm"; enableVibration(false); setSound(null, null); setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun refreshAlarmIndicator(context: Context, store: GuideStore = GuideStore(context)) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val candidates = mutableListOf<Triple<Long, String, String>>()
        store.alarms().filter { it.enabled }.forEach { item ->
            val next = nextAlarmMillis(item.hour, item.minute)
            val label = LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a"))
            candidates += Triple(next, item.title, label)
        }
        PrayerScheduler.nextPrayer(context, store)?.let { (prayer, target) ->
            val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            candidates += Triple(millis, "${prayer.nameBn} নামাজ", target.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        val next = candidates.minByOrNull { it.first }
        if (next == null) { manager.cancel(STATUS_NOTIFICATION_ID); return }
        val openApp = PendingIntent.getActivity(
            context, 73002,
            Intent(context, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(STATUS_NOTIFICATION_ID, NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Guide অ্যালার্ম সেট আছে")
            .setContentText("পরবর্তী: ${next.second} • ${next.third}")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openApp).build())
    }

    private fun refreshIndicatorSoon(context: Context) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({ refreshAlarmIndicator(appContext) }, 300L)
    }

    private fun nextAlarmMillis(hour: Int, minute: Int): Long = Calendar.getInstance().let { now ->
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    fun alarmChannelId(): String = ALARM_CHANNEL_ID
}

object AlarmSoundPlayer {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var toneLoop: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null

    @Synchronized
    fun isActive(): Boolean = player != null || vibrator != null || toneGenerator != null

    @Synchronized
    fun start(context: Context, ringtoneUri: String, soundEnabled: Boolean, vibrateEnabled: Boolean) {
        stop()
        if (soundEnabled) {
            if (ringtoneUri.startsWith("builtin://")) startBuiltInTone(ringtoneUri)
            else {
                val selected = ringtoneUri.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
                val alarmUri = selected ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                runCatching {
                    player = MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        setDataSource(context, alarmUri); isLooping = true; prepare(); start()
                    }
                }
            }
        }
        if (vibrateEnabled) runCatching {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 900), 0))
            @Suppress("DEPRECATION") if (Build.VERSION.SDK_INT < 26) v.vibrate(longArrayOf(0, 600, 300, 600, 300, 900), 0)
            vibrator = v
        }
        val stopTask = Runnable { stop() }
        timeout = stopTask
        handler.postDelayed(stopTask, 10 * 60 * 1000L)
    }

    private fun startBuiltInTone(uri: String) {
        val soft = uri == "builtin://azan-soft"
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, if (soft) 65 else 100)
        toneGenerator = tone
        lateinit var loop: Runnable
        loop = object : Runnable {
            private var alternate = false
            override fun run() {
                val code = if (soft) {
                    if (alternate) ToneGenerator.TONE_DTMF_1 else ToneGenerator.TONE_DTMF_6
                } else {
                    if (alternate) ToneGenerator.TONE_DTMF_9 else ToneGenerator.TONE_DTMF_3
                }
                alternate = !alternate
                runCatching { tone.startTone(code, if (soft) 650 else 850) }
                handler.postDelayed(this, if (soft) 1300L else 1100L)
            }
        }
        toneLoop = loop
        handler.post(loop)
    }

    @Synchronized
    fun stop() {
        timeout?.let { handler.removeCallbacks(it) }; timeout = null
        toneLoop?.let { handler.removeCallbacks(it) }; toneLoop = null
        runCatching { toneGenerator?.stopTone() }; runCatching { toneGenerator?.release() }; toneGenerator = null
        runCatching { player?.stop() }; runCatching { player?.release() }; player = null
        runCatching { vibrator?.cancel() }; vibrator = null
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.ensureChannel(context)
        val key = intent.getStringExtra("key") ?: "guide_alarm"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (intent.action == ReminderScheduler.ACTION_STOP_ALARM) {
            AlarmSoundPlayer.stop(); manager.cancel(key.hashCode()); ReminderScheduler.refreshAlarmIndicator(context); return
        }
        val title = intent.getStringExtra("title") ?: "Guide reminder"
        val body = intent.getStringExtra("body") ?: "You have something planned now."
        val ringtoneUri = intent.getStringExtra("ringtoneUri") ?: ""
        val soundEnabled = intent.getBooleanExtra("soundEnabled", true)
        val vibrateEnabled = intent.getBooleanExtra("vibrateEnabled", true)
        val prayerName = intent.getStringExtra("prayerName") ?: ""
        AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("key", key); putExtra("title", title); putExtra("body", body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val alarmScreen = PendingIntent.getActivity(context, key.hashCode() xor 0x62A1, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openGuide = PendingIntent.getActivity(
            context, key.hashCode() xor 0x51,
            Intent(context, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAlarm = PendingIntent.getBroadcast(
            context, key.hashCode() xor 0x7A31,
            Intent(context, ReminderReceiver::class.java).apply { action = ReminderScheduler.ACTION_STOP_ALARM; putExtra("key", key) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val notification = NotificationCompat.Builder(context, ReminderScheduler.alarmChannelId())
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true).setAutoCancel(false).setContentIntent(alarmScreen).setFullScreenIntent(alarmScreen, true)
                .addAction(android.R.drawable.ic_menu_view, "Guide খুলুন", openGuide)
                .addAction(android.R.drawable.ic_media_pause, "অ্যালার্ম বন্ধ", stopAlarm).build()
            manager.notify(key.hashCode(), notification)
        }
        if (intent.getBooleanExtra("daily", false)) {
            ReminderScheduler.scheduleDaily(context, key, title, body, intent.getIntExtra("hour", 8), intent.getIntExtra("minute", 0), ringtoneUri, soundEnabled, vibrateEnabled)
        } else if (prayerName.isNotBlank()) PrayerScheduler.schedulePrayer(context, prayerName)
        ReminderScheduler.refreshAlarmIndicator(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.ensureChannel(context); ReminderScheduler.scheduleAll(context)
        }
    }
}
