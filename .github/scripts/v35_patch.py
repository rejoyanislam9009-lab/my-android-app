from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# GuideStore: normal alarms are one-shot by default. Daily repeat is opt-in.
# Existing saved alarms that do not have the new repeatDaily field are treated
# as one-shot alarms, which prevents an old alarm from silently repeating.
# ---------------------------------------------------------------------------
gp = Path('app/src/main/java/com/guide/app/GuideStore.kt')
gs = gp.read_text()

if 'val repeatDaily: Boolean = false' not in gs:
    gs = req(
        gs,
        '''data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val ringtoneUri: String = ""
)''',
        '''data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val ringtoneUri: String = "",
    val repeatDaily: Boolean = false
)''',
        'alarm repeat field'
    )

    gs = req(
        gs,
        '''                enabled = o.optBoolean("enabled", true),
                soundEnabled = o.optBoolean("soundEnabled", true),
                vibrateEnabled = o.optBoolean("vibrateEnabled", true),
                ringtoneUri = o.optString("ringtoneUri", "")
            )''',
        '''                enabled = o.optBoolean("enabled", true),
                soundEnabled = o.optBoolean("soundEnabled", true),
                vibrateEnabled = o.optBoolean("vibrateEnabled", true),
                ringtoneUri = o.optString("ringtoneUri", ""),
                repeatDaily = o.optBoolean("repeatDaily", false)
            )''',
        'read alarm repeat field'
    )

    gs = req(
        gs,
        '''            put("enabled", item.enabled); put("soundEnabled", item.soundEnabled); put("vibrateEnabled", item.vibrateEnabled)
            put("ringtoneUri", item.ringtoneUri)''',
        '''            put("enabled", item.enabled); put("soundEnabled", item.soundEnabled); put("vibrateEnabled", item.vibrateEnabled)
            put("ringtoneUri", item.ringtoneUri); put("repeatDaily", item.repeatDaily)''',
        'save alarm repeat field'
    )

    gp.write_text(gs)
    print('v3.5 GuideStore alarm repeat preference applied')
else:
    print('v3.5 GuideStore patch already applied')

# ---------------------------------------------------------------------------
# ReminderScheduler: one-shot normal alarms stay one-shot and become disabled
# immediately after firing. Only repeatDaily alarms are scheduled again.
# ---------------------------------------------------------------------------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()

if 'scheduleNormalAlarm(context, it)' not in rs:
    old_alarm_schedule = '''        store.alarms().forEach {
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
        }'''
    new_alarm_schedule = '''        store.alarms().forEach {
            if (it.enabled) scheduleNormalAlarm(context, it)
            else cancel(context, "alarm:${it.id}")
        }'''
    rs = req(rs, old_alarm_schedule, new_alarm_schedule, 'normal alarm scheduling mode')

    marker = '''    fun scheduleDaily(
        context: Context,
'''
    normal_fn = '''    private fun scheduleNormalAlarm(context: Context, item: AlarmItem) {
        val key = "alarm:${item.id}"
        if (item.repeatDaily) {
            scheduleDaily(
                context = context,
                key = key,
                title = item.title,
                body = "Guide alarm",
                hour = item.hour,
                minute = item.minute,
                ringtoneUri = item.ringtoneUri,
                soundEnabled = item.soundEnabled,
                vibrateEnabled = item.vibrateEnabled
            )
        } else {
            scheduleOneShot(
                context = context,
                key = key,
                title = item.title,
                body = "Guide alarm",
                triggerAt = nextAlarmMillis(item.hour, item.minute),
                ringtoneUri = item.ringtoneUri,
                soundEnabled = item.soundEnabled,
                vibrateEnabled = item.vibrateEnabled,
                showAsAlarmClock = true
            )
        }
    }

'''
    rs = req(rs, marker, normal_fn + marker, 'normal alarm helper')

    # A one-shot normal alarm must be switched OFF as soon as it fires. This is
    # deliberately done before the active notification is rendered/refreshed,
    # so the status card cannot jump to tomorrow after the user stops it.
    rs = req(
        rs,
        '''        val prayerName = intent.getStringExtra("prayerName") ?: ""
        AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
''',
        '''        val prayerName = intent.getStringExtra("prayerName") ?: ""
        val isDaily = intent.getBooleanExtra("daily", false)
        if (key.startsWith("alarm:") && !isDaily) {
            val store = GuideStore(context)
            val alarmId = key.removePrefix("alarm:")
            val alarms = store.alarms()
            val index = alarms.indexOfFirst { it.id == alarmId }
            if (index >= 0 && alarms[index].enabled) {
                alarms[index] = alarms[index].copy(enabled = false)
                store.saveAlarms(alarms)
                CloudSyncManager.scheduleUpload(context.applicationContext)
            }
        }
        AlarmSoundPlayer.start(context.applicationContext, ringtoneUri, soundEnabled, vibrateEnabled)
''',
        'disable one-shot alarm after fire'
    )

    rs = req(
        rs,
        '''        if (intent.getBooleanExtra("daily", false)) {
            ReminderScheduler.scheduleDaily(context, key, title, body, intent.getIntExtra("hour", 8), intent.getIntExtra("minute", 0), ringtoneUri, soundEnabled, vibrateEnabled)
        } else if (prayerName.isNotBlank()) PrayerScheduler.schedulePrayer(context, prayerName)
''',
        '''        if (isDaily) {
            ReminderScheduler.scheduleDaily(context, key, title, body, intent.getIntExtra("hour", 8), intent.getIntExtra("minute", 0), ringtoneUri, soundEnabled, vibrateEnabled)
        } else if (prayerName.isNotBlank()) PrayerScheduler.schedulePrayer(context, prayerName)
''',
        'daily variable reuse'
    )

    rp.write_text(rs)
    print('v3.5 one-shot alarm scheduler applied')
else:
    print('v3.5 ReminderScheduler patch already applied')

# ---------------------------------------------------------------------------
# MainActivity: expose the repeat choice clearly in the alarm editor and show
# the repeat mode in the alarm list. New alarms default to one-time.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'প্রতিদিন একই সময়ে বাজবে' not in ms:
    ms = req(
        ms,
        '''        val soundCheck = CheckBox(this).apply { text = "রিংটোন বাজবে"; setTextColor(Color.WHITE); isChecked = existing?.soundEnabled ?: true }
        val vibrateCheck = CheckBox(this).apply { text = "ভাইব্রেশন হবে"; setTextColor(Color.WHITE); isChecked = existing?.vibrateEnabled ?: true }
        val enabledCheck = CheckBox(this).apply { text = "অ্যালার্ম চালু"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: true }
        box.addView(titleInput); box.addView(space(8)); box.addView(picker); box.addView(space(8)); box.addView(countdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); box.addView(space(8)); box.addView(ringtoneButton); box.addView(soundCheck); box.addView(vibrateCheck); box.addView(enabledCheck)
''',
        '''        val soundCheck = CheckBox(this).apply { text = "রিংটোন বাজবে"; setTextColor(Color.WHITE); isChecked = existing?.soundEnabled ?: true }
        val vibrateCheck = CheckBox(this).apply { text = "ভাইব্রেশন হবে"; setTextColor(Color.WHITE); isChecked = existing?.vibrateEnabled ?: true }
        val repeatCheck = CheckBox(this).apply {
            text = "প্রতিদিন একই সময়ে বাজবে"
            setTextColor(Color.WHITE)
            isChecked = existing?.repeatDaily ?: false
        }
        val repeatHint = text("OFF থাকলে একবার বাজে নিজে থেকেই বন্ধ হবে। প্রতিদিন চাইলে এই অপশন ON করুন।", 11f, "#AAB6D7").apply {
            setPadding(dp(38), 0, dp(4), dp(6))
        }
        val enabledCheck = CheckBox(this).apply { text = "অ্যালার্ম চালু"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: true }
        box.addView(titleInput); box.addView(space(8)); box.addView(picker); box.addView(space(8)); box.addView(countdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); box.addView(space(8)); box.addView(ringtoneButton); box.addView(soundCheck); box.addView(vibrateCheck); box.addView(repeatCheck); box.addView(repeatHint); box.addView(enabledCheck)
''',
        'alarm repeat checkbox'
    )

    # v3.4 source after the previous patch creates AlarmItem positionally.
    ms = req(
        ms,
        '''            val updated = AlarmItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, enabledCheck.isChecked, sound, vibrate, selectedRingtone)
''',
        '''            val updated = AlarmItem(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                title = name,
                hour = picker.hour,
                minute = picker.minute,
                enabled = enabledCheck.isChecked,
                soundEnabled = sound,
                vibrateEnabled = vibrate,
                ringtoneUri = selectedRingtone,
                repeatDaily = repeatCheck.isChecked
            )
''',
        'save repeat preference'
    )

    old_subtitle = '''        view.text = if (item.enabled) "${timeText(item.hour, item.minute)} • ${remainingText(item.hour, item.minute)} বাকি • $mode" else "${timeText(item.hour, item.minute)} • বন্ধ"
'''
    new_subtitle = '''        val repeatText = if (item.repeatDaily) "প্রতিদিন" else "একবার"
        view.text = if (item.enabled) "${timeText(item.hour, item.minute)} • ${remainingText(item.hour, item.minute)} বাকি • $repeatText • $mode" else "${timeText(item.hour, item.minute)} • বন্ধ • $repeatText"
'''
    ms = req(ms, old_subtitle, new_subtitle, 'alarm list repeat subtitle')

    mp.write_text(ms)
    print('v3.5 alarm repeat UI applied')
else:
    print('v3.5 MainActivity patch already applied')

# ---------------------------------------------------------------------------
# Version bump and cloud metadata.
# ---------------------------------------------------------------------------
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 17', 'versionCode = 18', 1)
bs = bs.replace('versionName = "3.4.0"', 'versionName = "3.5.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.4.0"', '"appVersion" to "3.5.0"', 1)
cp.write_text(cs)
print('v3.5 version metadata applied')
