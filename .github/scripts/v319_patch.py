from pathlib import Path


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# ---------------------------------------------------------------------------
# Guide v3.19
# 1) Make every long custom form dialog vertically scrollable so controls and
#    Save/Cancel remain reachable on short/small screens.
# 2) Hardware volume keys change the expected stream without stopping preview
#    audio or a currently ringing alarm.
# 3) Daily reminders use exact alarms rather than AlarmClock semantics so the
#    Android system alarm icon does not remain visible after a ringing alarm is
#    dismissed. One-shot normal alarms may still use AlarmClock until they fire.
# ---------------------------------------------------------------------------

# MainActivity: scrollable custom forms + robust MEDIA volume keys.
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideScrollableDialogsV319' not in ms:
    if 'import android.view.KeyEvent\n' not in ms:
        require(ms, 'import android.view.Gravity\n', 'MainActivity Gravity import')
        ms = ms.replace('import android.view.Gravity\n', 'import android.view.Gravity\nimport android.view.KeyEvent\n', 1)

    anchor = '    private fun buildTopBar(): View {\n'
    require(ms, anchor, 'MainActivity helper anchor')
    helpers = r'''    // GuideScrollableDialogsV319: AlertDialog custom content must be
    // scrollable on small screens. Dialog action buttons stay outside the
    // ScrollView, so Save/Cancel are always reachable.
    private fun scrollableDialogContent(content: View): ScrollView = ScrollView(this).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = true
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        clipToPadding = false
        addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    // Preview audio belongs to STREAM_MUSIC. Consume volume-key events here and
    // adjust that stream explicitly so no dialog/key handler can interpret a
    // volume press as a request to stop playback.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val audio = getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                    val direction = when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                        else -> AudioManager.ADJUST_TOGGLE_MUTE
                    }
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

'''
    ms = ms.replace(anchor, helpers + anchor, 1)

    form_count = ms.count('.setView(box)')
    if form_count == 0:
        raise SystemExit('pattern not found: custom form AlertDialog views')
    ms = ms.replace('.setView(box)', '.setView(scrollableDialogContent(box))')

    mp.write_text(ms)
    print(f'v3.19 MainActivity: wrapped {form_count} custom form dialogs and hardened MEDIA volume keys')
else:
    print('v3.19 MainActivity patch already applied')


# AlarmActivity: volume up/down/mute must never fall through to any stop path.
ap = Path('app/src/main/java/com/guide/app/AlarmActivity.kt')
asrc = ap.read_text()

if 'GuideExplicitAlarmVolumeV319' not in asrc:
    start = asrc.find('    override fun dispatchKeyEvent(event: KeyEvent): Boolean {')
    end = asrc.find('    private fun buildUi(source: Intent): LinearLayout {', start)
    if start < 0 or end < 0:
        raise SystemExit('pattern not found: AlarmActivity dispatchKeyEvent')

    dispatch = r'''    // GuideExplicitAlarmVolumeV319: volume keys only change STREAM_ALARM.
    // They are consumed here so they can never dismiss the ringing screen or
    // stop GuideAlarmService playback.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val direction = when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                        else -> AudioManager.ADJUST_TOGGLE_MUTE
                    }
                    audio.adjustStreamVolume(AudioManager.STREAM_ALARM, direction, AudioManager.FLAG_SHOW_UI)
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (event.action == KeyEvent.ACTION_DOWN) stopAndFinish()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

'''
    asrc = asrc[:start] + dispatch + asrc[end:]
    asrc = asrc.replace(
        'text = "ভলিউম বা Back বাটন চাপলেও অ্যালার্ম বন্ধ হবে"',
        'text = "ভলিউম বাটন অ্যালার্ম বন্ধ করবে না • বন্ধ করতে নিচের বাটন ব্যবহার করুন"',
        1
    )
    ap.write_text(asrc)
    print('v3.19 AlarmActivity: volume keys explicitly adjust alarm volume without stopping playback')
else:
    print('v3.19 AlarmActivity patch already applied')


# ReminderScheduler: v3.18 intentionally made every daily reminder an
# AlarmClock. That keeps Android's system alarm icon visible for the next daily
# schedule even after the current alarm is dismissed. Exact alarms are still
# used when permission exists, but daily reminders no longer advertise
# themselves as a system AlarmClock.
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()
marker = 'GuideNoPersistentSystemAlarmIconV319'
if marker not in rs:
    old = '        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", true)\n'
    require(rs, old, 'v3.18 daily AlarmClock scheduling')
    new = '        // GuideNoPersistentSystemAlarmIconV319: daily reminders stay exact without a persistent system alarm icon.\n        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", false)\n'
    rs = rs.replace(old, new, 1)
    rp.write_text(rs)
    print('v3.19 ReminderScheduler: daily alarms no longer leave the Android system alarm icon pinned')
else:
    print('v3.19 ReminderScheduler patch already applied')


# Version metadata.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
require(bs, 'versionCode = 31', 'v3.18 versionCode')
require(bs, 'versionName = "3.18.0"', 'v3.18 versionName')
bs = bs.replace('versionCode = 31', 'versionCode = 32', 1)
bs = bs.replace('versionName = "3.18.0"', 'versionName = "3.19.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
require(cs, '"appVersion" to "3.18.0"', 'v3.18 cloud appVersion')
cs = cs.replace('"appVersion" to "3.18.0"', '"appVersion" to "3.19.0"', 1)
cp.write_text(cs)
print('v3.19 version metadata applied')
