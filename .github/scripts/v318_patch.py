from pathlib import Path
import re


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)


def replace_between(text: str, start: str, end: str, replacement: str, name: str) -> str:
    a = text.find(start)
    if a < 0:
        raise SystemExit(f'pattern not found: {name} start')
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f'pattern not found: {name} end')
    return text[:a] + replacement + text[b:]


# ---------------------------------------------------------------------------
# Guide v3.18
# Reliable alarm engine + contextual Bengali voice/audio UX.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# GuideStore: routines and meals now remember their own ringtone choice.
# ---------------------------------------------------------------------------
gp = Path('app/src/main/java/com/guide/app/GuideStore.kt')
gs = gp.read_text()

if 'GuideReminderAudioV318' not in gs:
    old_tail = '''    val category: String = "Routine",
    val doneDate: String = "",
    val alarmEnabled: Boolean = false
)'''
    new_tail = '''    val category: String = "Routine",
    val doneDate: String = "",
    val alarmEnabled: Boolean = false,
    val ringtoneUri: String = ""
)'''
    gs = req(gs, old_tail, new_tail, 'routine ringtone model')

    old_meal_tail = '''    val note: String = "",
    val doneDate: String = "",
    val alarmEnabled: Boolean = false
)'''
    new_meal_tail = '''    val note: String = "",
    val doneDate: String = "",
    val alarmEnabled: Boolean = false,
    val ringtoneUri: String = ""
)'''
    gs = req(gs, old_meal_tail, new_meal_tail, 'meal ringtone model')

    gs = req(
        gs,
        '''                doneDate = o.optString("doneDate", ""),
                alarmEnabled = o.optBoolean("alarmEnabled", false)
            )''',
        '''                doneDate = o.optString("doneDate", ""),
                alarmEnabled = o.optBoolean("alarmEnabled", false),
                ringtoneUri = o.optString("ringtoneUri", "")
            )''',
        'read routine and meal ringtone',
        2
    )

    gs = req(
        gs,
        '''            put("category", item.category); put("doneDate", item.doneDate); put("alarmEnabled", item.alarmEnabled)
''',
        '''            put("category", item.category); put("doneDate", item.doneDate); put("alarmEnabled", item.alarmEnabled)
            put("ringtoneUri", item.ringtoneUri)
''',
        'save routine ringtone'
    )
    gs = req(
        gs,
        '''            put("note", item.note); put("doneDate", item.doneDate); put("alarmEnabled", item.alarmEnabled)
''',
        '''            put("note", item.note); put("doneDate", item.doneDate); put("alarmEnabled", item.alarmEnabled)
            put("ringtoneUri", item.ringtoneUri)
''',
        'save meal ringtone'
    )
    gs += '\n// GuideReminderAudioV318: per-routine/per-meal ringtone persistence.\n'
    gp.write_text(gs)
    print('v3.18 GuideStore reminder audio fields applied')
else:
    print('v3.18 GuideStore patch already applied')


# ---------------------------------------------------------------------------
# Reminder engine:
# - routine/meal use their selected audio
# - recurring audible reminders are AlarmClock-backed when exact alarm access is
#   available, improving reliability in Doze / background states
# - the foreground alarm service owns playback so the process is not killed
#   immediately after BroadcastReceiver.onReceive returns
# - status indicator includes routine/meal/medicine in addition to alarm/prayer
# ---------------------------------------------------------------------------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()

if 'GuideReliableAlarmEngineV318' not in rs:
    rs = req(
        rs,
        '''            if (it.alarmEnabled) scheduleDaily(context, "routine:${it.id}", it.title, "Routine • ${it.category}", it.hour, it.minute)
''',
        '''            if (it.alarmEnabled) scheduleDaily(context, "routine:${it.id}", it.title, "রুটিন • ${it.category}", it.hour, it.minute, it.ringtoneUri, true, true)
''',
        'routine selected ringtone scheduler'
    )
    rs = req(
        rs,
        '''            if (it.alarmEnabled) scheduleDaily(context, "meal:${it.id}", it.title, it.note.ifBlank { "Meal time" }, it.hour, it.minute)
''',
        '''            if (it.alarmEnabled) scheduleDaily(context, "meal:${it.id}", it.title, it.note.ifBlank { "খাবার খাওয়ার সময় হয়েছে" }, it.hour, it.minute, it.ringtoneUri, true, true)
''',
        'meal selected ringtone scheduler'
    )

    # v3.5 changes normal alarms to scheduleNormalAlarm. Make every daily audible
    # Guide reminder use AlarmClock semantics when Android allows exact alarms.
    rs = req(
        rs,
        '''        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", key.startsWith("alarm:"))
''',
        '''        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", true)
''',
        'daily audible reminders use alarm clock semantics'
    )

    # Contextual Bengali wording is visible and is also spoken by TTS.
    voice_start = rs.find('    private fun voiceTextFor(key: String, title: String): String = when {')
    voice_end = rs.find('    private fun pendingIntent(', voice_start)
    if voice_start < 0 or voice_end < 0:
        raise SystemExit('pattern not found: v3.12 voice text helper')
    voice_code = '''    private fun voiceTextFor(key: String, title: String): String = when {
        key.startsWith("prayer:") -> ""
        key.startsWith("medicine:") -> "ওষুধ খাওয়ার সময় হয়েছে${if (title.isNotBlank()) "। $title" else ""}"
        key.startsWith("meal:") -> "খাবার খাওয়ার সময় হয়েছে${if (title.isNotBlank()) "। $title" else ""}"
        key.startsWith("routine:") -> "আপনার রুটিনের সময় হয়েছে${if (title.isNotBlank()) "। $title" else ""}"
        key.startsWith("todo:") -> "আপনার নির্ধারিত কাজের সময় হয়েছে${if (title.isNotBlank()) "। $title" else ""}"
        key.startsWith("bill:") -> "বিল পরিশোধের সময় হয়েছে${if (title.isNotBlank()) "। $title" else ""}"
        key.startsWith("alarm:") -> "আপনার অ্যালার্মের সময় হয়েছে${if (title.isNotBlank()) "। $title" else ""}"
        else -> ""
    }

'''
    rs = rs[:voice_start] + voice_code + rs[voice_end:]

    # v3.12 currently starts TTS/MediaPlayer directly from the receiver. Move the
    # long-running work into a foreground service so it survives background
    # execution limits and Doze process cleanup.
    old_audio = '''        val voiceText = intent.getStringExtra("voiceText") ?: ""
        if (soundEnabled && voiceText.isNotBlank()) {
            GuideVoicePrompt.speakThen(context.applicationContext, voiceText) {
                AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
            }
        } else {
            AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
        }
'''
    new_audio = '''        val voiceText = intent.getStringExtra("voiceText") ?: ""
        GuideAlarmService.start(
            context = context.applicationContext,
            key = key,
            title = title,
            body = body,
            ringtoneUri = ringtoneUri,
            soundEnabled = soundEnabled,
            vibrateEnabled = vibrateEnabled,
            voiceText = voiceText
        )
'''
    rs = req(rs, old_audio, new_audio, 'receiver delegates playback to foreground service')

    rs = req(
        rs,
        '''            GuideVoicePrompt.stop(); AlarmSoundPlayer.stop(); manager.cancel(key.hashCode()); ReminderScheduler.refreshAlarmIndicator(context); return
''',
        '''            GuideAlarmService.stop(context.applicationContext)
            GuideVoicePrompt.stop(); AlarmSoundPlayer.stop(); manager.cancel(key.hashCode()); ReminderScheduler.refreshAlarmIndicator(context); return
''',
        'stop action stops foreground alarm service'
    )

    rs = req(
        rs,
        '''        GuideVoicePrompt.stop()
        AlarmSoundPlayer.stop()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
''',
        '''        GuideAlarmService.stop(context.applicationContext)
        GuideVoicePrompt.stop()
        AlarmSoundPlayer.stop()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
''',
        'quick off stops foreground alarm service',
        1
    )

    # Make the persistent next-alarm indicator useful for all core alarm types.
    candidate_anchor = '''        val candidates = mutableListOf<Triple<Long, String, String>>()
        store.alarms().filter { it.enabled }.forEach { item ->
'''
    candidate_new = '''        val candidates = mutableListOf<Triple<Long, String, String>>()
        store.routines().filter { it.alarmEnabled }.forEach { item ->
            candidates += Triple(nextAlarmMillis(item.hour, item.minute), item.title, LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        store.meals().filter { it.alarmEnabled }.forEach { item ->
            candidates += Triple(nextAlarmMillis(item.hour, item.minute), item.title, LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        DailyLifeStore(context).medicines().filter { it.enabled }.forEach { item ->
            candidates += Triple(nextAlarmMillis(item.hour, item.minute), "ওষুধ: ${item.name}", LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        store.alarms().filter { it.enabled }.forEach { item ->
'''
    rs = req(rs, candidate_anchor, candidate_new, 'indicator covers core reminders')

    rs = rs.replace('object ReminderScheduler {', 'object ReminderScheduler {\n    // GuideReliableAlarmEngineV318', 1)
    rp.write_text(rs)
    print('v3.18 reliable scheduler/receiver flow applied')
else:
    print('v3.18 Reminders patch already applied')


# ---------------------------------------------------------------------------
# Foreground playback service. The BroadcastReceiver schedules/reschedules, while
# this service keeps Bengali TTS, ringtone and vibration alive in background.
# ---------------------------------------------------------------------------
service = Path('app/src/main/java/com/guide/app/GuideAlarmService.kt')
service.write_text(r'''package com.guide.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class GuideAlarmService : Service() {
    companion object {
        private const val ACTION_START = "com.guide.app.alarm.START_V318"
        private const val ACTION_STOP = "com.guide.app.alarm.STOP_V318"

        fun start(
            context: Context,
            key: String,
            title: String,
            body: String,
            ringtoneUri: String,
            soundEnabled: Boolean,
            vibrateEnabled: Boolean,
            voiceText: String
        ) {
            val intent = Intent(context, GuideAlarmService::class.java).apply {
                action = ACTION_START
                putExtra("key", key)
                putExtra("title", title)
                putExtra("body", body)
                putExtra("ringtoneUri", ringtoneUri)
                putExtra("soundEnabled", soundEnabled)
                putExtra("vibrateEnabled", vibrateEnabled)
                putExtra("voiceText", voiceText)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { context.startService(intent) }
        }

        fun stop(context: Context) {
            GuideVoicePrompt.stop()
            AlarmSoundPlayer.stop()
            context.stopService(Intent(context, GuideAlarmService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var activeNotificationId: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlaybackAndSelf()
            return START_NOT_STICKY
        }

        val key = intent?.getStringExtra("key") ?: "guide_alarm"
        val title = intent?.getStringExtra("title") ?: "Guide অ্যালার্ম"
        val body = intent?.getStringExtra("body") ?: "আপনার নির্ধারিত সময় হয়েছে"
        val ringtoneUri = intent?.getStringExtra("ringtoneUri") ?: ""
        val soundEnabled = intent?.getBooleanExtra("soundEnabled", true) ?: true
        val vibrateEnabled = intent?.getBooleanExtra("vibrateEnabled", true) ?: true
        val voiceText = intent?.getStringExtra("voiceText") ?: ""

        ReminderScheduler.ensureChannel(this)
        activeNotificationId = key.hashCode()
        startForeground(activeNotificationId, foregroundNotification(key, title, body))

        handler.removeCallbacksAndMessages(null)
        GuideVoicePrompt.stop()
        AlarmSoundPlayer.stop()

        if (soundEnabled && voiceText.isNotBlank()) {
            GuideVoicePrompt.speakThen(applicationContext, voiceText) {
                AlarmSoundPlayer.start(applicationContext, ringtoneUri, true, vibrateEnabled)
            }
        } else {
            AlarmSoundPlayer.start(applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
        }

        // Safety timeout. AlarmSoundPlayer also has its own timeout, but the
        // foreground service must release itself too.
        handler.postDelayed({ stopPlaybackAndSelf() }, 10 * 60 * 1000L)
        return START_NOT_STICKY
    }

    private fun foregroundNotification(key: String, title: String, body: String): Notification {
        val alarmScreen = PendingIntent.getActivity(
            this,
            key.hashCode() xor 0x62A1,
            Intent(this, AlarmActivity::class.java).apply {
                putExtra("key", key)
                putExtra("title", title)
                putExtra("body", body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            this,
            key.hashCode() xor 0x7A31,
            Intent(this, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_STOP_ALARM
                putExtra("key", key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, ReminderScheduler.alarmChannelId())
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(alarmScreen)
            .setFullScreenIntent(alarmScreen, true)
            .addAction(android.R.drawable.ic_media_pause, "অ্যালার্ম বন্ধ", stop)
            .build()
    }

    private fun stopPlaybackAndSelf() {
        handler.removeCallbacksAndMessages(null)
        GuideVoicePrompt.stop()
        AlarmSoundPlayer.stop()
        if (android.os.Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        GuideVoicePrompt.stop()
        AlarmSoundPlayer.stop()
        super.onDestroy()
    }
}
''')
print('v3.18 GuideAlarmService written')


# ---------------------------------------------------------------------------
# A dedicated Bengali TTS preview player on the MEDIA route. This makes the
# form's voice preview follow wired/Bluetooth media routing and volume keys.
# ---------------------------------------------------------------------------
preview = Path('app/src/main/java/com/guide/app/GuideVoicePreview.kt')
preview.write_text(r'''package com.guide.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object GuideVoicePreview {
    private var tts: TextToSpeech? = null

    @Synchronized
    fun speak(context: Context, phrase: String, onUnavailable: (() -> Unit)? = null) {
        stop()
        if (phrase.isBlank()) return
        val app = context.applicationContext
        var created: TextToSpeech? = null
        created = TextToSpeech(app) { status ->
            val engine = created ?: tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                onUnavailable?.invoke()
                return@TextToSpeech
            }
            tts = engine
            var lang = engine.setLanguage(Locale("bn", "BD"))
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                lang = engine.setLanguage(Locale.forLanguageTag("bn"))
            }
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                stop()
                onUnavailable?.invoke()
                return@TextToSpeech
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            engine.setSpeechRate(0.91f)
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = stop()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = stop()
                override fun onError(utteranceId: String?, errorCode: Int) = stop()
            })
            if (engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "guide_preview_v318") == TextToSpeech.ERROR) {
                stop()
                onUnavailable?.invoke()
            }
        }
        tts = created
    }

    @Synchronized
    fun stop() {
        val engine = tts
        tts = null
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
    }
}
''')
print('v3.18 GuideVoicePreview written')


# ---------------------------------------------------------------------------
# AlarmActivity: volume keys must CHANGE alarm volume, never dismiss playback.
# ---------------------------------------------------------------------------
ap = Path('app/src/main/java/com/guide/app/AlarmActivity.kt')
asrc = ap.read_text()

if 'GuideVolumeKeysV318' not in asrc:
    asrc = req(asrc, 'import android.graphics.drawable.GradientDrawable\n', 'import android.graphics.drawable.GradientDrawable\nimport android.media.AudioManager\n', 'alarm audio manager import')
    asrc = req(
        asrc,
        '''        super.onCreate(savedInstanceState)
        window.addFlags(
''',
        '''        super.onCreate(savedInstanceState)
        // GuideVolumeKeysV318: hardware volume keys control the ALARM stream.
        volumeControlStream = AudioManager.STREAM_ALARM
        window.addFlags(
''',
        'alarm volume stream'
    )

    start = asrc.find('    override fun dispatchKeyEvent(event: KeyEvent): Boolean {')
    end = asrc.find('    private fun buildUi(source: Intent): LinearLayout {', start)
    if start < 0 or end < 0:
        raise SystemExit('pattern not found: AlarmActivity dispatchKeyEvent')
    dispatch = '''    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Volume Up/Down/Mute are intentionally NOT consumed. Android now changes
        // STREAM_ALARM volume normally instead of stopping the alarm.
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    stopAndFinish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

'''
    asrc = asrc[:start] + dispatch + asrc[end:]
    ap.write_text(asrc)
    print('v3.18 AlarmActivity volume behavior fixed')
else:
    print('v3.18 AlarmActivity patch already applied')


# ---------------------------------------------------------------------------
# MainActivity UX:
# - MEDIA volume route for previews
# - reschedule on resume (important after granting exact-alarm permission)
# - countdown under routine/meal/medicine time picker
# - per-section audio picker + Bengali voice preview
# - exact-alarm permission prompt when a user turns an alarm on
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideReminderUxV318' not in ms:
    if 'import android.media.AudioManager\n' not in ms:
        ms = req(ms, 'import android.media.RingtoneManager\n', 'import android.media.AudioManager\nimport android.media.RingtoneManager\n', 'main audio manager import')

    ms = req(
        ms,
        '''        super.onCreate(savedInstanceState)
        store = GuideStore(this)
''',
        '''        super.onCreate(savedInstanceState)
        // GuideReminderUxV318: preview audio follows media/headphone/Bluetooth volume.
        volumeControlStream = AudioManager.STREAM_MUSIC
        store = GuideStore(this)
''',
        'main preview volume stream'
    )

    ms = req(
        ms,
        '''        if (::store.isInitialized && store.hasProfile()) render()
''',
        '''        if (::store.isInitialized && store.hasProfile()) {
            // Re-create schedules after returning from exact-alarm settings or
            // notification settings so newly granted access takes effect now.
            ReminderScheduler.scheduleAll(this, store)
            render()
        }
''',
        'reschedule on resume'
    )

    helper_anchor = '    private fun buildTopBar(): View {\n'
    helpers = r'''    private fun pickerCountdown(picker: TimePicker, label: String): TextView {
        val view = text("", 13f, "#C9D3F3", bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedStroke("#121D39", "#48FFFFFF", 1, 13)
        }
        fun update(hour: Int, minute: Int) {
            view.text = "⏳ $label • ${remainingText(hour, minute)} বাকি"
        }
        update(picker.hour, picker.minute)
        picker.setOnTimeChangedListener { _, hour, minute -> update(hour, minute) }
        return view
    }

    private fun voicePreviewButton(label: String, phrase: String): Button = pillButton(label, "#2A7067") {
        volumeControlStream = AudioManager.STREAM_MUSIC
        GuideVoicePreview.speak(this, phrase) {
            Toast.makeText(this, "বাংলা Text-to-Speech voice পাওয়া যায়নি • ফোনের Speech Services আপডেট করুন", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureAlarmSystemReady(enabled: Boolean) {
        if (!enabled) return
        requestNotificationsIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ReminderScheduler.exactAlarmAvailable(this)) {
            Toast.makeText(this, "সঠিক সময়ে অ্যালার্মের জন্য ‘Alarms & reminders’ permission Allow করুন", Toast.LENGTH_LONG).show()
            handler.postDelayed({ if (!isFinishing) openExactAlarmSettings() }, 250L)
        }
    }

'''
    ms = req(ms, helper_anchor, helpers + '    // GuideReminderUxV318\n' + helper_anchor, 'v318 main helpers')

    # Replace routine editor completely; later patches do not own this function.
    routine_new = r'''    private fun addRoutine(existing: RoutineItem? = null) {
        val box = formBox()
        val titleInput = input("রুটিনের নাম").apply { setText(existing?.title ?: "") }
        val categoryInput = input("ক্যাটাগরি").apply { setText(existing?.category ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 8; minute = existing?.minute ?: 0 }
        val countdown = pickerCountdown(picker, "রুটিন শুরু হতে")
        var selectedRingtone = existing?.ringtoneUri ?: ""
        lateinit var ringtoneButton: Button
        ringtoneButton = pillButton("অডিও: ${ringtoneTitle(selectedRingtone)}", "#405179") {
            pickRingtone(selectedRingtone) { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}"
            }
        }
        val guideAudio = pillButton("Guide অডিও বাছাই / শুনুন", "#326E66") {
            showBuiltInSoundPicker("রুটিন অ্যালার্ম অডিও") { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}"
            }
        }
        val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "আপনার রুটিনের সময় হয়েছে")
        val alarmCheck = CheckBox(this).apply {
            text = "রুটিন অ্যালার্ম চালু করুন (ঐচ্ছিক)"
            setTextColor(Color.WHITE)
            isChecked = existing?.alarmEnabled ?: false
        }
        box.addView(titleInput); box.addView(space(8)); box.addView(categoryInput); box.addView(picker)
        box.addView(countdown); box.addView(space(7)); box.addView(voicePreview); box.addView(space(7)); box.addView(guideAudio); box.addView(space(7)); box.addView(ringtoneButton); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন রুটিন" else "রুটিন এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val updated = RoutineItem(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                title = name,
                hour = picker.hour,
                minute = picker.minute,
                category = categoryInput.text.toString().trim().ifBlank { "Routine" },
                doneDate = existing?.doneDate ?: "",
                alarmEnabled = alarmCheck.isChecked,
                ringtoneUri = selectedRingtone
            )
            val items = store.routines(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveRoutines(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); ensureAlarmSystemReady(alarmCheck.isChecked); render()
        }.setNegativeButton("বাতিল", null).show()
    }

'''
    ms = replace_between(ms, '    private fun addRoutine(existing: RoutineItem? = null) {', '    private fun mealActions(item: MealItem) {', routine_new, 'routine editor')

    meal_new = r'''    private fun addMeal(existing: MealItem? = null) {
        val box = formBox()
        val titleInput = input("খাবারের নাম").apply { setText(existing?.title ?: "") }
        val noteInput = input("Food note / plan").apply { setText(existing?.note ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 13; minute = existing?.minute ?: 0 }
        val countdown = pickerCountdown(picker, "খাবারের সময় হতে")
        var selectedRingtone = existing?.ringtoneUri ?: ""
        lateinit var ringtoneButton: Button
        ringtoneButton = pillButton("অডিও: ${ringtoneTitle(selectedRingtone)}", "#405179") {
            pickRingtone(selectedRingtone) { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}"
            }
        }
        val guideAudio = pillButton("Guide অডিও বাছাই / শুনুন", "#287B69") {
            showBuiltInSoundPicker("খাবারের অ্যালার্ম অডিও") { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}"
            }
        }
        val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "খাবার খাওয়ার সময় হয়েছে")
        val alarmCheck = CheckBox(this).apply {
            text = "খাবারের অ্যালার্ম চালু করুন (ঐচ্ছিক)"
            setTextColor(Color.WHITE)
            isChecked = existing?.alarmEnabled ?: false
        }
        box.addView(titleInput); box.addView(space(8)); box.addView(noteInput); box.addView(picker)
        box.addView(countdown); box.addView(space(7)); box.addView(voicePreview); box.addView(space(7)); box.addView(guideAudio); box.addView(space(7)); box.addView(ringtoneButton); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন খাবারের রুটিন" else "খাবারের রুটিন এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val updated = MealItem(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                title = name,
                hour = picker.hour,
                minute = picker.minute,
                note = noteInput.text.toString().trim(),
                doneDate = existing?.doneDate ?: "",
                alarmEnabled = alarmCheck.isChecked,
                ringtoneUri = selectedRingtone
            )
            val items = store.meals(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveMeals(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); ensureAlarmSystemReady(alarmCheck.isChecked); render()
        }.setNegativeButton("বাতিল", null).show()
    }

'''
    ms = replace_between(ms, '    private fun addMeal(existing: MealItem? = null) {', '    private fun alarmActions(item: AlarmItem) {', meal_new, 'meal editor')

    # Medicine was introduced by v2.9 and opt-in by v3.2. Replace it as one block.
    medicine_start = ms.find('    private fun addMedicine(existing: MedicineItem? = null) {')
    medicine_end = ms.find('    private fun medicineActions(item: MedicineItem) {', medicine_start)
    if medicine_start < 0 or medicine_end < 0:
        raise SystemExit('pattern not found: medicine editor')
    medicine_new = r'''    private fun addMedicine(existing: MedicineItem? = null) {
        val box = formBox()
        val name = input("ওষুধের নাম").apply { setText(existing?.name ?: "") }
        val dose = input("Dose / নির্দেশনা").apply { setText(existing?.dose ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 8; minute = existing?.minute ?: 0 }
        val countdown = pickerCountdown(picker, "ওষুধ খেতে")
        var selectedRingtone = existing?.ringtoneUri ?: ""
        lateinit var ringButton: Button
        ringButton = pillButton("অডিও: ${ringtoneTitle(selectedRingtone)}", "#49365F") {
            pickRingtone(selectedRingtone) { uri -> selectedRingtone = uri; ringButton.text = "অডিও: ${ringtoneTitle(uri)}" }
        }
        val guideAudio = pillButton("Guide অডিও বাছাই / শুনুন", "#7B4662") {
            showBuiltInSoundPicker("ওষুধের অ্যালার্ম অডিও") { uri -> selectedRingtone = uri; ringButton.text = "অডিও: ${ringtoneTitle(uri)}" }
        }
        val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "ওষুধ খাওয়ার সময় হয়েছে")
        val enabled = CheckBox(this).apply { text = "ওষুধের অ্যালার্ম চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: false }
        val vibrate = CheckBox(this).apply { text = "ভাইব্রেশন"; setTextColor(Color.WHITE); isChecked = existing?.vibrateEnabled ?: true }
        box.addView(name); box.addView(space(8)); box.addView(dose); box.addView(picker); box.addView(countdown); box.addView(space(7)); box.addView(voicePreview); box.addView(space(7)); box.addView(guideAudio); box.addView(space(7)); box.addView(ringButton); box.addView(enabled); box.addView(vibrate)
        AlertDialog.Builder(this).setTitle(if (existing == null) "ওষুধ যোগ করুন" else "ওষুধ এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val n = name.text.toString().trim(); if (n.isBlank()) return@setPositiveButton
            val life = DailyLifeStore(this); val items = life.medicines(); val updated = MedicineItem(existing?.id ?: java.util.UUID.randomUUID().toString(), n, dose.text.toString().trim(), picker.hour, picker.minute, enabled.isChecked, vibrate.isChecked, selectedRingtone); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            life.saveMedicines(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); ensureAlarmSystemReady(enabled.isChecked); render()
        }.setNegativeButton("বাতিল", null).show()
    }

'''
    ms = ms[:medicine_start] + medicine_new + ms[medicine_end:]

    # Add a clearly visible Bengali voice preview to the normal alarm editor.
    alarm_marker = '''        val soundCheck = CheckBox(this).apply { text = "রিংটোন বাজবে"; setTextColor(Color.WHITE); isChecked = existing?.soundEnabled ?: true }
'''
    if alarm_marker in ms:
        ms = req(
            ms,
            alarm_marker,
            '''        val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "আপনার অ্যালার্মের সময় হয়েছে")
''' + alarm_marker,
            'normal alarm voice preview'
        )
        ms = req(
            ms,
            'box.addView(space(7)); box.addView(ringtoneButton); box.addView(soundCheck)',
            'box.addView(space(7)); box.addView(ringtoneButton); box.addView(space(7)); box.addView(voicePreview); box.addView(soundCheck)',
            'normal alarm voice preview placement'
        )

    # Every alarm save should explain/request exact scheduling if needed.
    ms = req(
        ms,
        '''            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()
''',
        '''            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); ensureAlarmSystemReady(enabledCheck.isChecked); render()
''',
        'normal alarm exact permission readiness',
        1
    )

    # Prayer cards show time remaining. Prayer audio picker already has preview
    # controls from v3.7; keep adhan-only behavior (no spoken overlay).
    prayer_old = '''                val subtitle = if (!isPrayer) "শুধু তথ্য" else if (enabled) "আজান অ্যালার্ম চালু" else "আজান অ্যালার্ম বন্ধ"
'''
    prayer_new = '''                val left = remainingText(prayer.time.hour, prayer.time.minute)
                val subtitle = if (!isPrayer) "শুধু তথ্য • $left বাকি" else if (enabled) "আজান অ্যালার্ম চালু • $left বাকি" else "আজান অ্যালার্ম বন্ধ • $left বাকি"
'''
    if prayer_old in ms:
        ms = req(ms, prayer_old, prayer_new, 'prayer remaining time')

    # When master prayer alarm is enabled, request exact-alarm access if Android
    # still needs it. This replacement is intentionally limited to prayer page.
    prayer_master = '''                store.setPrayerEnabled(checked)
                ReminderScheduler.scheduleAll(this@MainActivity, store)
                render()
'''
    if prayer_master in ms:
        ms = req(
            ms,
            prayer_master,
            '''                store.setPrayerEnabled(checked)
                ReminderScheduler.scheduleAll(this@MainActivity, store)
                ensureAlarmSystemReady(checked)
                render()
''',
            'prayer exact alarm readiness'
        )

    mp.write_text(ms)
    print('v3.18 MainActivity countdown/audio UX applied')
else:
    print('v3.18 MainActivity patch already applied')


# ---------------------------------------------------------------------------
# Manifest foreground service permissions + service registration.
# ---------------------------------------------------------------------------
manifest = Path('app/src/main/AndroidManifest.xml')
mx = manifest.read_text()
if 'GuideAlarmService' not in mx:
    mx = req(
        mx,
        '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n',
        '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n',
        'foreground service permissions'
    )
    mx = req(
        mx,
        '        <activity android:name=".BackupActivity" android:exported="false" />\n',
        '        <service android:name=".GuideAlarmService" android:exported="false" android:foregroundServiceType="mediaPlayback" />\n\n        <activity android:name=".BackupActivity" android:exported="false" />\n',
        'alarm service manifest entry'
    )
    manifest.write_text(mx)
    print('v3.18 manifest alarm service registered')


# ---------------------------------------------------------------------------
# Version metadata.
# ---------------------------------------------------------------------------
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 30', 'versionCode = 31', 1)
bs = bs.replace('versionName = "3.17.0"', 'versionName = "3.18.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.17.0"', '"appVersion" to "3.18.0"', 1)
cp.write_text(cs)
print('v3.18 version metadata applied')
