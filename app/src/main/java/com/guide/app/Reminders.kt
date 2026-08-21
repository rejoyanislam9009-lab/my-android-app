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
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

object ReminderScheduler {
    private const val CHANNEL_ID = "guide_alarm_v2"

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
            if (it.enabled) scheduleDaily(context, "alarm:${it.id}", it.title, "Guide alarm", it.hour, it.minute)
            else cancel(context, "alarm:${it.id}")
        }
    }

    fun scheduleDaily(context: Context, key: String, title: String, body: String, hour: Int, minute: Int) {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute)
    }

    fun test(context: Context) {
        scheduleAt(
            context,
            "test_alarm",
            "Guide test alarm",
            "Your alarm sound and vibration are working.",
            System.currentTimeMillis() + 10_000L,
            false,
            0,
            0
        )
    }

    private fun scheduleAt(
        context: Context,
        key: String,
        title: String,
        body: String,
        triggerAt: Long,
        daily: Boolean,
        hour: Int,
        minute: Int
    ) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, key, title, body, daily, hour, minute, PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, key: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarm.cancel(pending)
            pending.cancel()
        }
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
        flag: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("key", key)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("daily", daily)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(CHANNEL_ID, "Guide alarms & reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Routine, meal and custom alarm notifications"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 700)
            setSound(alarmSound, audio)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun channelId(): String = CHANNEL_ID
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.ensureChannel(context)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val key = intent.getStringExtra("key") ?: "guide_alarm"
        val title = intent.getStringExtra("title") ?: "Guide reminder"
        val body = intent.getStringExtra("body") ?: "You have something planned now."
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val openApp = PendingIntent.getActivity(
            context,
            key.hashCode() xor 0x51,
            Intent(context, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.channelId())
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 700))
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_view, "Open Guide", openApp)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(key.hashCode(), notification)

        if (intent.getBooleanExtra("daily", false)) {
            ReminderScheduler.scheduleDaily(
                context,
                key,
                title,
                body,
                intent.getIntExtra("hour", 8),
                intent.getIntExtra("minute", 0)
            )
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.ensureChannel(context)
            ReminderScheduler.scheduleAll(context)
        }
    }
}
