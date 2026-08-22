from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# MainActivity: previews must use the media route, not the alarm route.
# This lets Android route preview audio to wired headphones / Bluetooth A2DP
# when they are the active media output. Real alarm firing keeps USAGE_ALARM.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

ms = req(
    ms,
    'AlarmSoundPlayer.start(this, uri, soundEnabled = true, vibrateEnabled = false)',
    'AlarmSoundPlayer.startPreview(this, uri)',
    'preview uses media route'
)
mp.write_text(ms)
print('v3.8 MainActivity preview routing applied')

# ---------------------------------------------------------------------------
# AlarmSoundPlayer: add a dedicated media-stream preview path.
# Actual scheduled alarms continue to use the existing USAGE_ALARM path.
# ---------------------------------------------------------------------------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()

if 'fun startPreview(context: Context, ringtoneUri: String)' not in rs:
    marker = '''    private fun startBundledAdhan(context: Context, uri: String) {\n'''
    preview_code = '''    @Synchronized
    fun startPreview(context: Context, ringtoneUri: String) {
        stop()
        when {
            ringtoneUri.startsWith("builtin-audio://") -> startBundledAdhanPreview(context, ringtoneUri)
            ringtoneUri.startsWith("builtin://") -> startBuiltInTonePreview(ringtoneUri)
            else -> startUriPreview(context, ringtoneUri)
        }
        val stopTask = Runnable { stop() }
        timeout = stopTask
        handler.postDelayed(stopTask, 90_000L)
    }

    private fun startBundledAdhanPreview(context: Context, uri: String) {
        val resId = when (uri) {
            "builtin-audio://adhan-beautiful" -> R.raw.guide_adhan_beautiful
            "builtin-audio://adhan-clear" -> R.raw.guide_adhan_clear
            "builtin-audio://adhan-short" -> R.raw.guide_adhan_short
            else -> 0
        }
        if (resId == 0) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        runCatching {
            val mp = MediaPlayer.create(context, resId, attrs, 0)
            player = mp
            mp?.isLooping = false
            mp?.setOnCompletionListener { completed ->
                runCatching { completed.release() }
                if (player === completed) player = null
            }
            mp?.start()
        }
    }

    private fun startBuiltInTonePreview(uri: String) {
        val soft = uri == "builtin://azan-soft"
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, if (soft) 65 else 100)
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

    private fun startUriPreview(context: Context, ringtoneUri: String) {
        val selected = ringtoneUri.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val uri = selected
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = false
                prepare()
                setOnCompletionListener { completed ->
                    runCatching { completed.release() }
                    if (player === completed) player = null
                }
                start()
            }
        }
    }

'''
    rs = req(rs, marker, preview_code + marker, 'media preview player')
    rp.write_text(rs)
    print('v3.8 media preview player applied')
else:
    print('v3.8 Reminders patch already applied')

# ---------------------------------------------------------------------------
# Version bump.
# ---------------------------------------------------------------------------
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 20', 'versionCode = 21', 1)
bs = bs.replace('versionName = "3.7.0"', 'versionName = "3.8.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.7.0"', '"appVersion" to "3.8.0"', 1)
cp.write_text(cs)
print('v3.8 version metadata applied')
