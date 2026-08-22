package com.guide.app

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class GuideApplication : Application(), Application.ActivityLifecycleCallbacks {
    private lateinit var audio: AudioManager
    private var alarmVolume = -1
    private var musicVolume = -1
    private var ringVolume = -1
    private var notificationVolume = -1
    private var observer: ContentObserver? = null
    private val syncHandler = Handler(Looper.getMainLooper())
    private var resumedActivities = 0

    private val periodicSync = object : Runnable {
        override fun run() {
            if (resumedActivities > 0) {
                CloudSyncManager.scheduleUpload(this@GuideApplication)
                syncHandler.postDelayed(this, 60_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        readVolumes()
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val newAlarm = safeVolume(AudioManager.STREAM_ALARM)
                val newMusic = safeVolume(AudioManager.STREAM_MUSIC)
                val newRing = safeVolume(AudioManager.STREAM_RING)
                val newNotification = safeVolume(AudioManager.STREAM_NOTIFICATION)
                val changed = newAlarm != alarmVolume || newMusic != musicVolume || newRing != ringVolume || newNotification != notificationVolume

                alarmVolume = newAlarm
                musicVolume = newMusic
                ringVolume = newRing
                notificationVolume = newNotification

                if (changed && AlarmSoundPlayer.isActive()) {
                    AlarmSoundPlayer.stop()
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
                    ReminderScheduler.refreshAlarmIndicator(this@GuideApplication)
                }
            }
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer!!)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivities++
        syncHandler.removeCallbacks(periodicSync)
        syncHandler.postDelayed(periodicSync, 60_000L)
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
        CloudSyncManager.scheduleUpload(this)
        if (resumedActivities == 0) syncHandler.removeCallbacks(periodicSync)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun readVolumes() {
        alarmVolume = safeVolume(AudioManager.STREAM_ALARM)
        musicVolume = safeVolume(AudioManager.STREAM_MUSIC)
        ringVolume = safeVolume(AudioManager.STREAM_RING)
        notificationVolume = safeVolume(AudioManager.STREAM_NOTIFICATION)
    }

    private fun safeVolume(stream: Int): Int = runCatching { audio.getStreamVolume(stream) }.getOrDefault(-1)
}
