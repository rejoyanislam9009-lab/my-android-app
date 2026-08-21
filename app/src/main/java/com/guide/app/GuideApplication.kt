package com.guide.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class GuideApplication : Application() {
    private lateinit var audio: AudioManager
    private var alarmVolume = -1
    private var musicVolume = -1
    private var ringVolume = -1
    private var notificationVolume = -1
    private var observer: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
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

    private fun readVolumes() {
        alarmVolume = safeVolume(AudioManager.STREAM_ALARM)
        musicVolume = safeVolume(AudioManager.STREAM_MUSIC)
        ringVolume = safeVolume(AudioManager.STREAM_RING)
        notificationVolume = safeVolume(AudioManager.STREAM_NOTIFICATION)
    }

    private fun safeVolume(stream: Int): Int = runCatching { audio.getStreamVolume(stream) }.getOrDefault(-1)
}
