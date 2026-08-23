from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# Guide v3.12
# 1) Fix dashboard banner manual swipe with a ViewFlipper that intercepts only
#    horizontal gestures, leaving vertical page scrolling untouched.
# 2) Add category-aware Bengali voice prompts before normal Guide reminders.
#    Prayer alarms keep their selected adhan without a spoken overlay.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideBannerCarouselV312' not in ms:
    ms = req(
        ms,
        '        val flipper = android.widget.ViewFlipper(this).apply { isAutoStart = false }',
        '        // GuideBannerCarouselV312: intercept horizontal gestures at the parent level.\n        val flipper = SwipeBannerFlipper(this).apply { isAutoStart = false }',
        'use swipe-aware flipper'
    )

    touch_start = ms.find('        flipper.setOnTouchListener { _, event ->')
    touch_end_token = '        updateDots()\n        handler.postDelayed(autoSlide, 5600L)'
    touch_end = ms.find(touch_end_token, touch_start)
    if touch_start < 0 or touch_end < 0:
        raise SystemExit('pattern not found: old carousel touch listener')

    replacement = '''        flipper.onSwipeLeft = {
            setDirection(true)
            flipper.showNext()
            updateDots()
            restartAuto()
        }
        flipper.onSwipeRight = {
            setDirection(false)
            flipper.showPrevious()
            updateDots()
            restartAuto()
        }

'''
    ms = ms[:touch_start] + replacement + ms[touch_end:]
    mp.write_text(ms)
    print('v3.12 MainActivity robust horizontal swipe applied')
else:
    print('v3.12 MainActivity swipe patch already applied')

# A dedicated ViewFlipper that lets the containing ScrollView keep vertical
# gestures while intercepting horizontal swipes reliably, even when the slide
# itself is clickable.
swipe_file = Path('app/src/main/java/com/guide/app/SwipeBannerFlipper.kt')
swipe_file.write_text(r'''package com.guide.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ViewFlipper
import kotlin.math.abs

class SwipeBannerFlipper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewFlipper(context, attrs) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var interceptingHorizontal = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                interceptingHorizontal = false
                // Let the clickable slide receive taps unless a horizontal
                // gesture becomes clear during ACTION_MOVE.
                super.onInterceptTouchEvent(event)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                    interceptingHorizontal = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                interceptingHorizontal = false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> return interceptingHorizontal
            MotionEvent.ACTION_UP -> {
                if (!interceptingHorizontal) return super.onTouchEvent(event)
                val dx = event.x - downX
                val threshold = (touchSlop * 2.2f).coerceAtLeast(36f * resources.displayMetrics.density)
                when {
                    dx <= -threshold -> onSwipeLeft?.invoke()
                    dx >= threshold -> onSwipeRight?.invoke()
                }
                interceptingHorizontal = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                interceptingHorizontal = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }
}
''')
print('v3.12 SwipeBannerFlipper written')

# ---------------------------------------------------------------------------
# Contextual Bengali voice prompts for reminders.
# Android TextToSpeech is used so the phrases do not require a network call.
# If Bengali speech data is unavailable, Guide immediately falls back to the
# selected ringtone. Prayer alarms intentionally skip TTS and play adhan only.
# ---------------------------------------------------------------------------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()

if 'GuideVoicePromptV312' not in rs:
    rs = req(
        rs,
        'import android.media.ToneGenerator\n',
        'import android.media.ToneGenerator\nimport android.speech.tts.TextToSpeech\nimport android.speech.tts.UtteranceProgressListener\n',
        'tts imports'
    )
    if 'import java.util.Locale\n' not in rs:
        rs = req(rs, 'import java.util.Calendar\n', 'import java.util.Calendar\nimport java.util.Locale\n', 'locale import')

    voice_helper = '''    private fun voiceTextFor(key: String, title: String): String = when {
        key.startsWith("prayer:") -> ""
        key.startsWith("medicine:") -> "ওষুধ খাওয়ার সময় হয়েছে"
        key.startsWith("meal:") -> "খাবার খাওয়ার সময় হয়েছে"
        key.startsWith("routine:") -> "আপনার রুটিনের সময় হয়েছে"
        key.startsWith("todo:") -> "আপনার নির্ধারিত কাজের সময় হয়েছে"
        key.startsWith("bill:") -> "বিল পরিশোধের সময় হয়েছে"
        key.startsWith("alarm:") -> if (title.isBlank()) "আপনার অ্যালার্মের সময় হয়েছে" else "আপনার অ্যালার্মের সময় হয়েছে। $title"
        else -> ""
    }

'''
    rs = req(rs, '    private fun pendingIntent(\n', voice_helper + '    private fun pendingIntent(\n', 'voice text helper')

    rs = req(
        rs,
        '''            putExtra("soundEnabled", soundEnabled); putExtra("vibrateEnabled", vibrateEnabled); putExtra("prayerName", prayerName)
''',
        '''            putExtra("soundEnabled", soundEnabled); putExtra("vibrateEnabled", vibrateEnabled); putExtra("prayerName", prayerName)
            putExtra("voiceText", voiceTextFor(key, title))
''',
        'voice text pending intent'
    )

    voice_object = r'''// GuideVoicePromptV312: lightweight category voice before ringtone.
object GuideVoicePrompt {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var active = false
    private var afterSpeech: (() -> Unit)? = null

    @Synchronized
    fun isActive(): Boolean = active

    @Synchronized
    fun speakThen(context: Context, text: String, after: () -> Unit) {
        stop()
        if (text.isBlank()) {
            handler.post(after)
            return
        }
        active = true
        afterSpeech = after
        val appContext = context.applicationContext
        var created: TextToSpeech? = null
        created = TextToSpeech(appContext) { status ->
            val engine = created ?: tts
            if (!active || status != TextToSpeech.SUCCESS || engine == null) {
                finishAndContinue()
                return@TextToSpeech
            }
            tts = engine
            val language = engine.setLanguage(Locale("bn", "BD"))
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                finishAndContinue()
                return@TextToSpeech
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            engine.setSpeechRate(0.92f)
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finishAndContinue()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finishAndContinue()
                override fun onError(utteranceId: String?, errorCode: Int) = finishAndContinue()
            })
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide_voice_v312")
            if (result == TextToSpeech.ERROR) finishAndContinue()
        }
        tts = created
        handler.postDelayed({
            if (isActive()) finishAndContinue()
        }, 5500L)
    }

    @Synchronized
    private fun finishAndContinue() {
        if (!active && afterSpeech == null) return
        val callback = afterSpeech
        afterSpeech = null
        active = false
        val engine = tts
        tts = null
        handler.post {
            runCatching { engine?.stop() }
            runCatching { engine?.shutdown() }
            callback?.invoke()
        }
    }

    @Synchronized
    fun stop() {
        afterSpeech = null
        active = false
        val engine = tts
        tts = null
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
    }
}

'''
    rs = req(rs, 'object AlarmSoundPlayer {\n', voice_object + 'object AlarmSoundPlayer {\n', 'voice player object')

    rs = req(
        rs,
        '''        if (!AlarmSoundPlayer.isActive()) return false
        AlarmSoundPlayer.stop()
''',
        '''        val hadActive = AlarmSoundPlayer.isActive() || GuideVoicePrompt.isActive()
        if (!hadActive) return false
        GuideVoicePrompt.stop()
        AlarmSoundPlayer.stop()
''',
        'stop active voice and alarm'
    )

    rs = req(
        rs,
        '''            AlarmSoundPlayer.stop(); manager.cancel(key.hashCode()); ReminderScheduler.refreshAlarmIndicator(context); return
''',
        '''            GuideVoicePrompt.stop(); AlarmSoundPlayer.stop(); manager.cancel(key.hashCode()); ReminderScheduler.refreshAlarmIndicator(context); return
''',
        'notification stop voice'
    )

    rs = req(
        rs,
        '''        AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
''',
        '''        val voiceText = intent.getStringExtra("voiceText") ?: ""
        if (soundEnabled && voiceText.isNotBlank()) {
            GuideVoicePrompt.speakThen(context.applicationContext, voiceText) {
                AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
            }
        } else {
            AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
        }
''',
        'speak category prompt before ringtone'
    )

    # Quick Off must stop a prompt that may currently be speaking before the
    # ringtone has started.
    rs = req(
        rs,
        '''        AlarmSoundPlayer.stop()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
''',
        '''        GuideVoicePrompt.stop()
        AlarmSoundPlayer.stop()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
''',
        'quick off voice prompt',
        1
    )

    rp.write_text(rs)
    print('v3.12 contextual Bengali voice reminder flow applied')
else:
    print('v3.12 Reminders voice patch already applied')

# Alarm screen stop button/hardware key must also stop TTS before it hands off
# to the ringtone.
ap = Path('app/src/main/java/com/guide/app/AlarmActivity.kt')
asrc = ap.read_text()
if 'GuideVoicePrompt.stop()' not in asrc:
    asrc = req(
        asrc,
        '        AlarmSoundPlayer.stop()\n',
        '        GuideVoicePrompt.stop()\n        AlarmSoundPlayer.stop()\n',
        'alarm screen stops voice'
    )
    ap.write_text(asrc)
    print('v3.12 AlarmActivity voice stop applied')

# Version metadata.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 24', 'versionCode = 25', 1)
bs = bs.replace('versionName = "3.11.0"', 'versionName = "3.12.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.11.0"', '"appVersion" to "3.12.0"', 1)
cp.write_text(cs)
print('v3.12 version metadata applied')
