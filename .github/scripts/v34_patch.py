from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# ReminderScheduler: remember every Guide schedule so Quick Off can reliably
# cancel AlarmClock schedules too, including keys that are no longer visible.
# ---------------------------------------------------------------------------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()

if 'guide_schedule_registry_v34' not in rs:
    rs = req(
        rs,
        '    private const val STATUS_NOTIFICATION_ID = 73001\n',
        '    private const val STATUS_NOTIFICATION_ID = 73001\n    private const val SCHEDULE_REGISTRY_PREFS = "guide_schedule_registry_v34"\n    private const val SCHEDULE_REGISTRY_KEY = "keys"\n',
        'schedule registry constants'
    )

    rs = req(
        rs,
        '        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager\n        val pending = pendingIntent(context, key, title, body, daily, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, prayerName, PendingIntent.FLAG_UPDATE_CURRENT)\n',
        '        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager\n        rememberScheduledKey(context, key)\n        val pending = pendingIntent(context, key, title, body, daily, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, prayerName, PendingIntent.FLAG_UPDATE_CURRENT)\n',
        'remember scheduled key'
    )

    old_cancel = '''    fun cancel(context: Context, key: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(context, key.hashCode(), Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { alarm.cancel(pending); pending.cancel() }
        refreshIndicatorSoon(context)
    }
'''
    new_cancel = '''    fun cancel(context: Context, key: String) {
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
        // Also clear the AlarmClock display intent that Android may cache for
        // a normal/prayer alarm after the broadcast operation is cancelled.
        PendingIntent.getActivity(
            context,
            key.hashCode() xor 0x2A71,
            Intent(context, AlarmActivity::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.cancel()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(key.hashCode())
        forgetScheduledKey(context, key)
        refreshIndicatorSoon(context)
    }

    fun cancelAllGuideSchedules(context: Context, store: GuideStore = GuideStore(context)) {
        val keys = scheduledKeys(context).toMutableSet()
        keys += store.routines().map { "routine:${it.id}" }
        keys += store.meals().map { "meal:${it.id}" }
        keys += store.alarms().map { "alarm:${it.id}" }
        keys += listOf("prayer:Fajr", "prayer:Dhuhr", "prayer:Asr", "prayer:Maghrib", "prayer:Isha", "test_alarm")
        val daily = DailyLifeStore(context)
        keys += daily.medicines().map { "medicine:${it.id}" }
        keys += daily.todos().map { "todo:${it.id}" }
        keys += daily.bills().map { "bill:${it.id}" }

        keys.forEach { cancel(context, it) }
        context.getSharedPreferences(SCHEDULE_REGISTRY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        AlarmSoundPlayer.stop()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(STATUS_NOTIFICATION_ID)
        refreshAlarmIndicator(context, store)
    }

    private fun rememberScheduledKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(SCHEDULE_REGISTRY_PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(SCHEDULE_REGISTRY_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        keys.add(key)
        prefs.edit().putStringSet(SCHEDULE_REGISTRY_KEY, keys).apply()
    }

    private fun forgetScheduledKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(SCHEDULE_REGISTRY_PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(SCHEDULE_REGISTRY_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (keys.remove(key)) prefs.edit().putStringSet(SCHEDULE_REGISTRY_KEY, keys).apply()
    }

    private fun scheduledKeys(context: Context): Set<String> =
        context.getSharedPreferences(SCHEDULE_REGISTRY_PREFS, Context.MODE_PRIVATE)
            .getStringSet(SCHEDULE_REGISTRY_KEY, emptySet())?.toSet() ?: emptySet()
'''
    rs = req(rs, old_cancel, new_cancel, 'robust cancel and registry')
    rp.write_text(rs)
    print('v3.4 ReminderScheduler cancellation registry applied')
else:
    print('v3.4 ReminderScheduler patch already applied')

# ---------------------------------------------------------------------------
# MainActivity: Quick cards must grow with font size, Alarm form must scroll,
# and Quick Off must cancel Android AlarmClock schedules before changing flags.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'অ্যালার্ম ফর্ম স্ক্রল করুন' not in ms:
    ms = req(
        ms,
        'quickRow.addView(compactAction("⏰", "Alarm", "Add reminder", "#6254F5") { addAlarm() }, LinearLayout.LayoutParams(0, dp(116), 1f))',
        'quickRow.addView(compactAction("⏰", "Alarm", "Add reminder", "#6254F5") { addAlarm() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))',
        'alarm quick card flexible height'
    )
    ms = req(
        ms,
        'quickRow.addView(compactAction("✓", "Attendance", store.attendanceFor(), "#E3785B") { currentTab = "track"; render() }, LinearLayout.LayoutParams(0, dp(116), 1f))',
        'quickRow.addView(compactAction("✓", "Attendance", store.attendanceFor(), "#E3785B") { currentTab = "track"; render() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))',
        'attendance quick card flexible height'
    )

    old_compact = '''    private fun compactAction(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E").apply { gravity = Gravity.CENTER_VERTICAL }; c.addView(text(icon, 25f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(accent, 14) }, LinearLayout.LayoutParams(dp(48), dp(48))); c.addView(text(titleText, 15f, "#FFFFFF", bold = true).apply { setPadding(0, dp(8), 0, 0) }); c.addView(text(subtitle, 11f, "#8190B8")); c.setOnClickListener { onClick() }; return c
    }
'''
    new_compact = '''    private fun compactAction(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E").apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(132)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        c.addView(text(icon, 25f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 14)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        c.addView(text(titleText, 15f, "#FFFFFF", bold = true).apply {
            setPadding(0, dp(9), 0, dp(3))
            maxLines = 2
        })
        c.addView(text(subtitle, 11f, "#8190B8").apply {
            maxLines = 2
            setPadding(0, 0, 0, dp(2))
        })
        c.setOnClickListener { onClick() }
        return c
    }
'''
    ms = req(ms, old_compact, new_compact, 'responsive quick action cards')

    # v3.3 adds this function before v3.4 runs.
    ms = req(
        ms,
        '''    private fun disableAllGuideReminders(syncCloud: Boolean = true) {
        store.saveRoutines''',
        '''    private fun disableAllGuideReminders(syncCloud: Boolean = true) {
        // First cancel every Android AlarmManager entry while all IDs are still available.
        ReminderScheduler.cancelAllGuideSchedules(this, store)
        store.saveRoutines''',
        'quick off cancels schedules first'
    )

    # Cancel a normal AlarmClock schedule before deleting its data record.
    ms = req(
        ms,
        '                2 -> items.removeAt(index)\n            }\n            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()',
        '                2 -> { ReminderScheduler.cancel(this, "alarm:${item.id}"); items.removeAt(index) }\n            }\n            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()',
        'cancel normal alarm before delete'
    )

    old_dialog = '''        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন অ্যালার্ম" else "অ্যালার্ম এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = titleInput.text.toString().trim().ifBlank { "Alarm" }
            val sound = soundCheck.isChecked
            val vibrate = if (!sound && !vibrateCheck.isChecked) true else vibrateCheck.isChecked
            if (!sound && !vibrateCheck.isChecked) Toast.makeText(this, "কমপক্ষে ভাইব্রেশন চালু রাখা হয়েছে", Toast.LENGTH_SHORT).show()
            val updated = AlarmItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, enabledCheck.isChecked, sound, vibrate, selectedRingtone)
            val items = store.alarms(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()
        }.setNegativeButton("বাতিল", null).create()
        dialog.setOnShowListener { updater.run() }; dialog.setOnDismissListener { dialogHandler.removeCallbacks(updater) }; dialog.show()
'''
    new_dialog = '''        val scrollBox = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            setPadding(0, 0, dp(2), dp(4))
            addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        box.addView(text("অ্যালার্ম ফর্ম স্ক্রল করুন • নিচের সব অপশন দেখা যাবে", 10.5f, "#9AA7C9").apply {
            setPadding(0, dp(6), 0, dp(4))
        })
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন অ্যালার্ম" else "অ্যালার্ম এডিট").setView(scrollBox).setPositiveButton("সেভ") { _, _ ->
            val name = titleInput.text.toString().trim().ifBlank { "Alarm" }
            val sound = soundCheck.isChecked
            val vibrate = if (!sound && !vibrateCheck.isChecked) true else vibrateCheck.isChecked
            if (!sound && !vibrateCheck.isChecked) Toast.makeText(this, "কমপক্ষে ভাইব্রেশন চালু রাখা হয়েছে", Toast.LENGTH_SHORT).show()
            val updated = AlarmItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, enabledCheck.isChecked, sound, vibrate, selectedRingtone)
            val items = store.alarms(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()
        }.setNegativeButton("বাতিল", null).create()
        dialog.setOnShowListener {
            updater.run()
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.88f).toInt())
        }
        dialog.setOnDismissListener { dialogHandler.removeCallbacks(updater) }
        dialog.show()
'''
    ms = req(ms, old_dialog, new_dialog, 'scrollable alarm dialog')

    mp.write_text(ms)
    print('v3.4 MainActivity UI/alarm fixes applied')
else:
    print('v3.4 MainActivity patch already applied')

# ---------------------------------------------------------------------------
# Version bump and cloud metadata.
# ---------------------------------------------------------------------------
g = Path('app/build.gradle.kts')
gs = g.read_text()
gs = gs.replace('versionCode = 16', 'versionCode = 17', 1)
gs = gs.replace('versionName = "3.3.0"', 'versionName = "3.4.0"', 1)
g.write_text(gs)

c = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = c.read_text().replace('"appVersion" to "3.3.0"', '"appVersion" to "3.4.0"', 1)
c.write_text(cs)
print('v3.4 version metadata applied')
