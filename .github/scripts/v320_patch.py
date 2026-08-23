from pathlib import Path


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


def replace_kotlin_function(text: str, signature: str, replacement: str) -> tuple[str, bool]:
    start = text.find(signature)
    if start < 0:
        return text, False
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    end = -1
    for i in range(brace, len(text)):
        ch = text[i]
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'closing brace not found: {signature}')
    while end < len(text) and text[end] == '\n':
        end += 1
    return text[:start] + replacement + text[end:], True


# ---------------------------------------------------------------------------
# Guide v3.20
# - OFF really means OFF until the user turns that reminder back on.
# - Recurring alarms expose two separate actions: stop now, or stop + disable
#   the next occurrence.
# - The persistent "next alarm" notification exposes an OFF action.
# - Enabled daily alarms again use AlarmClock semantics when exact alarms are
#   permitted, so Android/OEM status bars can show the system alarm icon.
# - Normal alarm cards get a direct ON/OFF button for the next occurrence.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Reminder scheduler + notification controls.
# ---------------------------------------------------------------------------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
rs = rp.read_text()

if 'GuideNextAlarmControlsV320' not in rs:
    stop_const = '    const val ACTION_STOP_ALARM = "com.guide.app.action.STOP_ALARM"\n'
    require(rs, stop_const, 'stop action constant')
    rs = rs.replace(
        stop_const,
        stop_const + '    const val ACTION_DISABLE_REMINDER = "com.guide.app.action.DISABLE_REMINDER_V320"\n',
        1
    )

    # v3.19 intentionally removed AlarmClock semantics to hide the icon. The
    # user now wants the status-bar alarm icon while an alarm is enabled. Keep
    # exact-alarm fallback behavior for devices without permission.
    v319_daily = '''        // GuideNoPersistentSystemAlarmIconV319: daily reminders stay exact without a persistent system alarm icon.
        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", false)
'''
    if v319_daily in rs:
        rs = rs.replace(
            v319_daily,
            '''        // GuideNextAlarmControlsV320: an enabled daily reminder advertises the next
        // occurrence as an AlarmClock when exact-alarm access is available.
        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", true)
''',
            1
        )
    else:
        direct_daily = '        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", false)\n'
        require(rs, direct_daily, 'daily exact schedule line')
        rs = rs.replace(
            direct_daily,
            '        // GuideNextAlarmControlsV320: enabled next alarm is visible to Android as an AlarmClock.\n        scheduleAt(context, key, title, body, next.timeInMillis, true, hour, minute, ringtoneUri, soundEnabled, vibrateEnabled, "", true)\n',
            1
        )

    # Add one source-of-truth function that disables the matching stored
    # reminder, cancels any already-scheduled occurrence, rebuilds schedules,
    # updates the indicator, and syncs the changed ON/OFF state.
    disable_anchor = '    fun exactAlarmAvailable(context: Context): Boolean {\n'
    require(rs, disable_anchor, 'exactAlarmAvailable anchor')
    disable_helper = r'''    fun disableReminder(context: Context, key: String) {
        val appContext = context.applicationContext
        cancel(appContext, key)
        val store = GuideStore(appContext)

        when {
            key.startsWith("routine:") -> {
                val id = key.removePrefix("routine:")
                val items = store.routines()
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) {
                    items[index] = items[index].copy(alarmEnabled = false)
                    store.saveRoutines(items)
                }
            }
            key.startsWith("meal:") -> {
                val id = key.removePrefix("meal:")
                val items = store.meals()
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) {
                    items[index] = items[index].copy(alarmEnabled = false)
                    store.saveMeals(items)
                }
            }
            key.startsWith("alarm:") -> {
                val id = key.removePrefix("alarm:")
                val items = store.alarms()
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) {
                    items[index] = items[index].copy(enabled = false)
                    store.saveAlarms(items)
                }
            }
            key.startsWith("medicine:") -> {
                val id = key.removePrefix("medicine:")
                val life = DailyLifeStore(appContext)
                val items = life.medicines()
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) {
                    items[index] = items[index].copy(enabled = false)
                    life.saveMedicines(items)
                }
            }
            key.startsWith("todo:") -> {
                val id = key.removePrefix("todo:")
                val life = DailyLifeStore(appContext)
                val items = life.todos()
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) {
                    items[index] = items[index].copy(reminderEnabled = false)
                    life.saveTodos(items)
                }
            }
            key.startsWith("bill:") -> {
                val id = key.removePrefix("bill:")
                val life = DailyLifeStore(appContext)
                val items = life.bills()
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) {
                    items[index] = items[index].copy(reminderEnabled = false)
                    life.saveBills(items)
                }
            }
            key.startsWith("prayer:") -> {
                val prayer = key.removePrefix("prayer:")
                store.setPrayerAlarmEnabled(prayer, false)
            }
        }

        scheduleAll(appContext, store)
        CloudSyncManager.scheduleUpload(appContext)
        refreshAlarmIndicator(appContext, store)
    }

'''
    rs = rs.replace(disable_anchor, disable_helper + disable_anchor, 1)

    # Replace the indicator so it retains the key of the shown next reminder.
    # This lets its notification action disable exactly what the user sees.
    new_indicator = r'''    fun refreshAlarmIndicator(context: Context, store: GuideStore = GuideStore(context)) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        data class Candidate(val time: Long, val key: String, val title: String, val label: String)
        val candidates = mutableListOf<Candidate>()

        store.routines().filter { it.alarmEnabled }.forEach { item ->
            candidates += Candidate(nextAlarmMillis(item.hour, item.minute), "routine:${item.id}", item.title,
                LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        store.meals().filter { it.alarmEnabled }.forEach { item ->
            candidates += Candidate(nextAlarmMillis(item.hour, item.minute), "meal:${item.id}", item.title,
                LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        store.alarms().filter { it.enabled }.forEach { item ->
            candidates += Candidate(nextAlarmMillis(item.hour, item.minute), "alarm:${item.id}", item.title,
                LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }

        val life = DailyLifeStore(context)
        life.medicines().filter { it.enabled }.forEach { item ->
            candidates += Candidate(nextAlarmMillis(item.hour, item.minute), "medicine:${item.id}", "ওষুধ: ${item.name}",
                LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        life.todos().filter { it.reminderEnabled && it.completedDate.isBlank() }.forEach { item ->
            val trigger = runCatching {
                java.time.LocalDate.parse(item.dueDate).atTime(item.hour, item.minute)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
            if (trigger != null && trigger > System.currentTimeMillis()) {
                val label = java.time.Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd MMM • hh:mm a"))
                candidates += Candidate(trigger, "todo:${item.id}", "করণীয়: ${item.title}", label)
            }
        }
        life.bills().filter { it.reminderEnabled && it.paidDate.isBlank() }.forEach { item ->
            val trigger = runCatching {
                java.time.LocalDate.parse(item.dueDate).atTime(9, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
            if (trigger != null && trigger > System.currentTimeMillis()) {
                val label = java.time.Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd MMM • hh:mm a"))
                candidates += Candidate(trigger, "bill:${item.id}", "বিল: ${item.title}", label)
            }
        }

        PrayerScheduler.nextPrayer(context, store)?.let { (prayer, target) ->
            val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            candidates += Candidate(
                millis,
                "prayer:${prayer.key}",
                "${prayer.nameBn} নামাজ",
                target.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a"))
            )
        }

        val next = candidates.minByOrNull { it.time }
        if (next == null) {
            manager.cancel(STATUS_NOTIFICATION_ID)
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            73002,
            Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableNext = PendingIntent.getBroadcast(
            context,
            next.key.hashCode() xor 0x4F20,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DISABLE_REMINDER
                putExtra("key", next.key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            STATUS_NOTIFICATION_ID,
            NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Guide অ্যালার্ম ON আছে")
                .setContentText("পরবর্তী: ${next.title} • ${next.label}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("পরবর্তী: ${next.title} • ${next.label}\nOFF চাপলে এই রিমাইন্ডার পরেরবার আর বাজবে না।"))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "পরের অ্যালার্ম OFF", disableNext)
                .build()
        )
    }

'''
    rs, replaced_indicator = replace_kotlin_function(
        rs,
        '    fun refreshAlarmIndicator(context: Context, store: GuideStore = GuideStore(context)) {',
        new_indicator
    )
    if not replaced_indicator:
        raise SystemExit('pattern not found: refreshAlarmIndicator')

    # Notification action handling. STOP only silences the current ringing
    # occurrence. DISABLE also persists OFF and cancels the next occurrence.
    receiver_anchor = '        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager\n'
    require(rs, receiver_anchor, 'ReminderReceiver manager anchor')
    receiver_disable = r'''        if (intent.action == ReminderScheduler.ACTION_DISABLE_REMINDER) {
            GuideAlarmService.stop(context.applicationContext)
            GuideVoicePrompt.stop()
            AlarmSoundPlayer.stop()
            manager.cancel(key.hashCode())
            ReminderScheduler.disableReminder(context.applicationContext, key)
            return
        }
'''
    rs = rs.replace(receiver_anchor, receiver_anchor + receiver_disable, 1)

    # Add a second action to the ringing receiver notification when that legacy
    # notification is rendered in addition to the foreground-service one.
    stop_pending = '''        val stopAlarm = PendingIntent.getBroadcast(
            context, key.hashCode() xor 0x7A31,
            Intent(context, ReminderReceiver::class.java).apply { action = ReminderScheduler.ACTION_STOP_ALARM; putExtra("key", key) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
'''
    if stop_pending in rs:
        rs = rs.replace(
            stop_pending,
            stop_pending + '''        val disableAlarm = PendingIntent.getBroadcast(
            context, key.hashCode() xor 0x4F20,
            Intent(context, ReminderReceiver::class.java).apply { action = ReminderScheduler.ACTION_DISABLE_REMINDER; putExtra("key", key) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
''',
            1
        )
        action_line = '                .addAction(android.R.drawable.ic_media_pause, "অ্যালার্ম বন্ধ", stopAlarm).build()\n'
        if action_line in rs:
            rs = rs.replace(
                action_line,
                '                .addAction(android.R.drawable.ic_media_pause, "এখন বন্ধ", stopAlarm)\n                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "পরেরটাও OFF", disableAlarm).build()\n',
                1
            )

    rs = rs.replace('object ReminderScheduler {\n    // GuideReliableAlarmEngineV318', 'object ReminderScheduler {\n    // GuideNextAlarmControlsV320\n    // GuideReliableAlarmEngineV318', 1)
    rp.write_text(rs)
    print('v3.20 reminder ON/OFF persistence, next-alarm indicator action and system icon behavior applied')
else:
    print('v3.20 Reminders patch already applied')


# ---------------------------------------------------------------------------
# Foreground alarm notification: explicit Stop Now and Stop + Next OFF actions.
# ---------------------------------------------------------------------------
sp = Path('app/src/main/java/com/guide/app/GuideAlarmService.kt')
ss = sp.read_text()
if 'GuideForegroundAlarmActionsV320' not in ss:
    stop_block = '''        val stop = PendingIntent.getBroadcast(
            this,
            key.hashCode() xor 0x7A31,
            Intent(this, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_STOP_ALARM
                putExtra("key", key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
'''
    require(ss, stop_block, 'foreground stop PendingIntent')
    ss = ss.replace(
        stop_block,
        stop_block + '''        val disableNext = PendingIntent.getBroadcast(
            this,
            key.hashCode() xor 0x4F20,
            Intent(this, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_DISABLE_REMINDER
                putExtra("key", key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
''',
        1
    )

    old_action = '            .addAction(android.R.drawable.ic_media_pause, "অ্যালার্ম বন্ধ", stop)\n'
    require(ss, old_action, 'foreground stop action')
    ss = ss.replace(
        old_action,
        '            .addAction(android.R.drawable.ic_media_pause, "এখন বন্ধ", stop)\n            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "পরেরটাও OFF", disableNext)\n',
        1
    )
    ss = ss.replace('class GuideAlarmService : Service() {', 'class GuideAlarmService : Service() {\n    // GuideForegroundAlarmActionsV320', 1)
    sp.write_text(ss)
    print('v3.20 foreground alarm notification actions applied')
else:
    print('v3.20 GuideAlarmService patch already applied')


# ---------------------------------------------------------------------------
# MainActivity: a direct ON/OFF button on each normal alarm card. OFF persists
# and cancels the next occurrence; ON schedules again and requests exact-alarm
# access when Android needs it.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()
if 'GuideAlarmCardToggleV320' not in ms:
    new_alarm_card = r'''    private fun alarmCard(item: AlarmItem, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            text(if (item.enabled) "⏰" else "○", 24f, "#FFFFFF", bold = true).apply {
                gravity = Gravity.CENTER
                background = rounded(if (item.enabled) "#2D7EA6" else "#45516D", 16)
            },
            LinearLayout.LayoutParams(dp(58), dp(58))
        )

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        labels.addView(text(item.title, 17f, "#FFFFFF", bold = true))
        val subtitle = text("", 13f, if (item.enabled) "#9CB6E4" else "#7F8AA8")
        labels.addView(subtitle)
        labels.addView(text(if (item.enabled) "পরের অ্যালার্ম: ON" else "পরের অ্যালার্ম: OFF", 11f,
            if (item.enabled) "#72D7B4" else "#FF9EA4", bold = true).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = smallAction(if (item.enabled) "ON" else "OFF", if (item.enabled) "#267B64" else "#8A4650") {
            val items = store.alarms()
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                val turnOn = !items[index].enabled
                if (!turnOn) ReminderScheduler.cancel(this, "alarm:${item.id}")
                items[index] = items[index].copy(enabled = turnOn)
                store.saveAlarms(items)
                ReminderScheduler.scheduleAll(this, store)
                CloudSyncManager.scheduleUpload(this)
                ensureAlarmSystemReady(turnOn)
                render()
            }
        }
        row.addView(toggle, LinearLayout.LayoutParams(dp(62), dp(42)))
        c.addView(row)
        c.setOnClickListener { onClick() }
        alarmCountdownViews.add(subtitle to item)
        updateAlarmSubtitle(subtitle, item)
        return c
    }

'''
    ms, replaced_alarm_card = replace_kotlin_function(
        ms,
        '    private fun alarmCard(item: AlarmItem, onClick: () -> Unit): LinearLayout {',
        new_alarm_card
    )
    if not replaced_alarm_card:
        raise SystemExit('pattern not found: alarmCard')
    ms = ms.replace('class MainActivity : AppCompatActivity() {', 'class MainActivity : AppCompatActivity() {\n    // GuideAlarmCardToggleV320', 1)
    mp.write_text(ms)
    print('v3.20 direct next-alarm ON/OFF card button applied')
else:
    print('v3.20 MainActivity patch already applied')


# ---------------------------------------------------------------------------
# Version metadata.
# ---------------------------------------------------------------------------
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
require(bs, 'versionCode = 32', 'v3.19 versionCode')
require(bs, 'versionName = "3.19.0"', 'v3.19 versionName')
bs = bs.replace('versionCode = 32', 'versionCode = 33', 1)
bs = bs.replace('versionName = "3.19.0"', 'versionName = "3.20.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
require(cs, '"appVersion" to "3.19.0"', 'v3.19 cloud appVersion')
cs = cs.replace('"appVersion" to "3.19.0"', '"appVersion" to "3.20.0"', 1)
cp.write_text(cs)
print('v3.20 version metadata applied')
