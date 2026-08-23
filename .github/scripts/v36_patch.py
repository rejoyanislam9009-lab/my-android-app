from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# MainActivity: expose real bundled adhan audio in both the Prayer sound picker
# and the normal Alarm editor. Phone ringtone selection remains available.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'builtin-audio://adhan-beautiful' not in ms:
    old_title = '''    private fun ringtoneTitle(uri: String): String {
        if (uri.isBlank()) return "ফোনের ডিফল্ট অ্যালার্ম"
        if (uri == "builtin://azan-soft") return "বিল্ট-ইন আজান অ্যালার্ট ১ • শান্ত"
        if (uri == "builtin://azan-classic") return "বিল্ট-ইন আজান অ্যালার্ট ২ • জোরালো"
        return runCatching { RingtoneManager.getRingtone(this, Uri.parse(uri))?.getTitle(this) ?: "নির্বাচিত রিংটোন" }.getOrDefault("নির্বাচিত রিংটোন")
    }
'''
    new_title = '''    private fun ringtoneTitle(uri: String): String {
        if (uri.isBlank()) return "ফোনের ডিফল্ট অ্যালার্ম"
        if (uri == "builtin-audio://adhan-beautiful") return "Guide আজান ১ • পূর্ণ"
        if (uri == "builtin-audio://adhan-clear") return "Guide আজান ২ • পরিষ্কার"
        if (uri == "builtin-audio://adhan-short") return "Guide আজান ৩ • সংক্ষিপ্ত"
        if (uri == "builtin://azan-soft") return "Guide অ্যালার্ট টোন • শান্ত"
        if (uri == "builtin://azan-classic") return "Guide অ্যালার্ট টোন • জোরালো"
        return runCatching { RingtoneManager.getRingtone(this, Uri.parse(uri))?.getTitle(this) ?: "নির্বাচিত রিংটোন" }.getOrDefault("নির্বাচিত রিংটোন")
    }
'''
    ms = req(ms, old_title, new_title, 'bundled adhan ringtone titles')

    old_picker = '''    private fun chooseBuiltInAzan() {
        AlertDialog.Builder(this).setTitle("বিল্ট-ইন আজান অ্যালার্ট")
            .setItems(arrayOf("আজান অ্যালার্ট ১ • শান্ত", "আজান অ্যালার্ট ২ • জোরালো", "ফোনের ডিফল্ট অ্যালার্ম")) { _, which ->
                store.setPrayerAzanUri(when (which) { 0 -> "builtin://azan-soft"; 1 -> "builtin://azan-classic"; else -> "" })
                ReminderScheduler.scheduleAll(this, store); render()
            }.show()
    }
'''
    new_picker = '''    private fun bundledAlarmLabels(): Array<String> = arrayOf(
        "আজান ১ • পূর্ণ",
        "আজান ২ • পরিষ্কার",
        "আজান ৩ • সংক্ষিপ্ত",
        "অ্যালার্ট টোন • শান্ত",
        "অ্যালার্ট টোন • জোরালো",
        "ফোনের ডিফল্ট অ্যালার্ম"
    )

    private fun bundledAlarmValues(): Array<String> = arrayOf(
        "builtin-audio://adhan-beautiful",
        "builtin-audio://adhan-clear",
        "builtin-audio://adhan-short",
        "builtin://azan-soft",
        "builtin://azan-classic",
        ""
    )

    private fun chooseBuiltInAzan() {
        AlertDialog.Builder(this).setTitle("Guide-এর বিল্ট-ইন আজান ও অ্যালার্ট")
            .setItems(bundledAlarmLabels()) { _, which ->
                store.setPrayerAzanUri(bundledAlarmValues()[which])
                ReminderScheduler.scheduleAll(this, store)
                render()
            }.show()
    }

    private fun chooseBuiltInAlarmSound(onSelected: (String) -> Unit) {
        AlertDialog.Builder(this).setTitle("Guide-এর বিল্ট-ইন রিংটোন")
            .setItems(bundledAlarmLabels()) { _, which -> onSelected(bundledAlarmValues()[which]) }
            .show()
    }
'''
    ms = req(ms, old_picker, new_picker, 'bundled adhan picker')

    # Clarify Prayer page copy now that real bundled adhan audio exists.
    ms = ms.replace(
        'NEW • বিল্ট-ইন alert tone অথবা ফোনে থাকা আজান/অডিও বেছে নিন।',
        'NEW • Guide-এর বিল্ট-ইন আজান অথবা ফোনে থাকা আজান/অডিও বেছে নিন।',
        1
    )
    ms = ms.replace(
        'বিল্ট-ইন আজান অ্যালার্ট বাছাই  NEW',
        'Guide-এর বিল্ট-ইন আজান বাছাই  NEW',
        1
    )

    old_button = '''        ringtoneButton = pillButton("রিংটোন: ${ringtoneTitle(selectedRingtone)}", "#34466F") {
            pickRingtone(selectedRingtone) { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "রিংটোন: ${ringtoneTitle(uri)}"
            }
        }
        val soundCheck = CheckBox(this).apply { text = "রিংটোন বাজবে"; setTextColor(Color.WHITE); isChecked = existing?.soundEnabled ?: true }
'''
    new_button = '''        ringtoneButton = pillButton("রিংটোন: ${ringtoneTitle(selectedRingtone)}", "#34466F") {
            pickRingtone(selectedRingtone) { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "রিংটোন: ${ringtoneTitle(uri)}"
            }
        }
        val guideRingtoneButton = pillButton("Guide-এর বিল্ট-ইন আজান/টোন বাছাই", "#247B67") {
            chooseBuiltInAlarmSound { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "রিংটোন: ${ringtoneTitle(uri)}"
            }
        }
        val soundCheck = CheckBox(this).apply { text = "রিংটোন বাজবে"; setTextColor(Color.WHITE); isChecked = existing?.soundEnabled ?: true }
'''
    ms = req(ms, old_button, new_button, 'normal alarm bundled ringtone button')

    # v3.5 final alarm form line includes repeat controls. Add the new picker before
    # the phone ringtone button so users can distinguish built-in vs device audio.
    old_form = '''        box.addView(titleInput); box.addView(space(8)); box.addView(picker); box.addView(space(8)); box.addView(countdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); box.addView(space(8)); box.addView(ringtoneButton); box.addView(soundCheck); box.addView(vibrateCheck); box.addView(repeatCheck); box.addView(repeatHint); box.addView(enabledCheck)
'''
    new_form = '''        box.addView(titleInput); box.addView(space(8)); box.addView(picker); box.addView(space(8)); box.addView(countdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); box.addView(space(8)); box.addView(guideRingtoneButton); box.addView(space(7)); box.addView(ringtoneButton); box.addView(soundCheck); box.addView(vibrateCheck); box.addView(repeatCheck); box.addView(repeatHint); box.addView(enabledCheck)
'''
    ms = req(ms, old_form, new_form, 'alarm editor bundled ringtone placement')

    mp.write_text(ms)
    print('v3.6 MainActivity bundled adhan picker applied')
else:
    print('v3.6 MainActivity patch already applied')

# ---------------------------------------------------------------------------
# AlarmSoundPlayer: play bundled OGG resources using USAGE_ALARM attributes.
# The full adhan audio plays once; the existing synthetic alert tones remain.
# ---------------------------------------------------------------------------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()

if 'private fun startBundledAdhan' not in rs:
    rs = req(
        rs,
        '''        if (soundEnabled) {
            if (ringtoneUri.startsWith("builtin://")) startBuiltInTone(ringtoneUri)
            else {
''',
        '''        if (soundEnabled) {
            if (ringtoneUri.startsWith("builtin-audio://")) startBundledAdhan(context, ringtoneUri)
            else if (ringtoneUri.startsWith("builtin://")) startBuiltInTone(ringtoneUri)
            else {
''',
        'route bundled audio'
    )

    marker = '''    private fun startBuiltInTone(uri: String) {
'''
    bundled_fn = '''    private fun startBundledAdhan(context: Context, uri: String) {
        val resId = when (uri) {
            "builtin-audio://adhan-beautiful" -> R.raw.guide_adhan_beautiful
            "builtin-audio://adhan-clear" -> R.raw.guide_adhan_clear
            "builtin-audio://adhan-short" -> R.raw.guide_adhan_short
            else -> 0
        }
        if (resId == 0) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching {
            val mp = MediaPlayer.create(context, resId, attrs, 0)
            player = mp
            mp?.isLooping = false
            mp?.setOnCompletionListener { completed ->
                runCatching { completed.release() }
                if (player === completed) player = null
                runCatching { vibrator?.cancel() }
                vibrator = null
            }
            mp?.start()
        }
    }

'''
    rs = req(rs, marker, bundled_fn + marker, 'bundled adhan playback helper')
    rp.write_text(rs)
    print('v3.6 bundled adhan playback applied')
else:
    print('v3.6 Reminders patch already applied')

# ---------------------------------------------------------------------------
# Version bump and cloud metadata.
# ---------------------------------------------------------------------------
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 18', 'versionCode = 19', 1)
bs = bs.replace('versionName = "3.5.0"', 'versionName = "3.6.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.5.0"', '"appVersion" to "3.6.0"', 1)
cp.write_text(cs)
print('v3.6 version metadata applied')
