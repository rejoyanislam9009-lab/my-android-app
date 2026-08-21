from pathlib import Path


def replace_required(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, name: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f'pattern not found: {name} start')
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f'pattern not found: {name} end')
    return text[:start] + replacement + text[end:]


# ---------------------------------------------------------------------------
# GuideStore: routines/meals default alarms OFF and prayer selections explicit.
# Existing saved values are preserved because stored booleans still win.
# ---------------------------------------------------------------------------
store_path = Path('app/src/main/java/com/guide/app/GuideStore.kt')
store = store_path.read_text()

if 'prayer_alarm_selection_v32' not in store:
    store = replace_required(store, 'val alarmEnabled: Boolean = true', 'val alarmEnabled: Boolean = false', 'routine alarm default', 2)
    store = replace_required(store, 'alarmEnabled = o.optBoolean("alarmEnabled", true)', 'alarmEnabled = o.optBoolean("alarmEnabled", false)', 'routine/meal JSON alarm default', 2)

    prayer_block = '''    fun prayerSettings(): PrayerSettings {
        val defaults = setOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val masterEnabled = prefs.getBoolean("prayer_enabled", false)
        val savedNames = prefs.getStringSet("prayer_enabled_names", null)?.toSet()
        val selectedNames = if (!prefs.getBoolean("prayer_alarm_selection_v32", false)) {
            // Preserve legacy users that already had the prayer master alarm ON.
            // New users start with every individual prayer alarm OFF.
            val initial = savedNames ?: if (masterEnabled) defaults else emptySet()
            prefs.edit()
                .putStringSet("prayer_enabled_names", initial)
                .putBoolean("prayer_alarm_selection_v32", true)
                .apply()
            initial
        } else {
            savedNames ?: emptySet()
        }
        return PrayerSettings(
            enabled = masterEnabled,
            latitude = prefs.getString("prayer_lat", "0")?.toDoubleOrNull() ?: 0.0,
            longitude = prefs.getString("prayer_lon", "0")?.toDoubleOrNull() ?: 0.0,
            azanUri = prefs.getString("prayer_azan_uri", "") ?: "",
            vibrateEnabled = prefs.getBoolean("prayer_vibrate", false),
            enabledPrayers = selectedNames
        )
    }

'''
    store = replace_between(
        store,
        '    fun prayerSettings(): PrayerSettings {',
        '    fun setPrayerEnabled(enabled: Boolean) {',
        prayer_block,
        'prayer settings migration'
    )
    store_path.write_text(store)
    print('v3.2 GuideStore opt-in reminder defaults applied')
else:
    print('v3.2 GuideStore patch already applied')


# ---------------------------------------------------------------------------
# DailyLifeStore: To-do, medicine and bills default reminder OFF.
# ---------------------------------------------------------------------------
daily_path = Path('app/src/main/java/com/guide/app/DailyLifeStore.kt')
daily = daily_path.read_text()

if 'v32_reminder_defaults' not in daily:
    daily = replace_required(daily, 'val reminderEnabled: Boolean = true', 'val reminderEnabled: Boolean = false', 'todo/bill data defaults', 2)
    daily = replace_required(daily, 'val enabled: Boolean = true', 'val enabled: Boolean = false', 'medicine data default', 1)
    daily = replace_required(daily, 'o.optBoolean("reminderEnabled", true)', 'o.optBoolean("reminderEnabled", false)', 'todo/bill JSON defaults', 2)
    daily = replace_required(daily, 'o.optBoolean("enabled", true)', 'o.optBoolean("enabled", false)', 'medicine JSON default', 1)
    daily += '\n// v32_reminder_defaults: new reminders are opt-in; existing saved flags are preserved.\n'
    daily_path.write_text(daily)
    print('v3.2 DailyLifeStore opt-in reminder defaults applied')
else:
    print('v3.2 DailyLifeStore patch already applied')


# ---------------------------------------------------------------------------
# MainActivity: visible opt-in controls and explicit prayer ON/OFF switches.
# ---------------------------------------------------------------------------
main_path = Path('app/src/main/java/com/guide/app/MainActivity.kt')
main = main_path.read_text()

if 'রিমাইন্ডার ডিফল্টভাবে বন্ধ থাকবে' not in main:
    # Routine and meal forms.
    main = replace_required(
        main,
        'val alarmCheck = CheckBox(this).apply { text = "Daily alarm reminder"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: true }',
        'val alarmCheck = CheckBox(this).apply { text = "অ্যালার্ম চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: false }',
        'routine alarm checkbox'
    )
    main = replace_required(
        main,
        'val alarmCheck = CheckBox(this).apply { text = "Meal alarm reminder"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: true }',
        'val alarmCheck = CheckBox(this).apply { text = "খাবারের অ্যালার্ম চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: false }',
        'meal alarm checkbox'
    )

    # To-do form and card/action status.
    main = replace_required(
        main,
        'val reminder = CheckBox(this).apply { text = "রিমাইন্ডার চালু"; setTextColor(Color.WHITE); isChecked = existing?.reminderEnabled ?: true }',
        'val reminder = CheckBox(this).apply { text = "রিমাইন্ডার চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.reminderEnabled ?: false }',
        'todo reminder checkbox',
        1
    )
    main = replace_required(
        main,
        '${if (item.reminderEnabled && !done) " • রিমাইন্ডার" else ""}',
        '${if (item.reminderEnabled && !done) " • রিমাইন্ডার ON" else " • রিমাইন্ডার OFF"}',
        'todo reminder status'
    )
    old_todo_actions = '''    private fun todoActions(item: TodoItem) {
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (item.completedDate.isBlank()) "সম্পন্ন করুন" else "আবার অসম্পন্ন করুন", "এডিট করুন", "ডিলিট করুন")) { _, which ->
            val life = DailyLifeStore(this); val items = life.todos(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) { 0 -> items[index] = item.copy(completedDate = if (item.completedDate.isBlank()) store.today() else ""); 1 -> { addTodo(item); return@setItems }; 2 -> items.removeAt(index) }
            life.saveTodos(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }
'''
    new_todo_actions = '''    private fun todoActions(item: TodoItem) {
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(
            if (item.completedDate.isBlank()) "সম্পন্ন করুন" else "আবার অসম্পন্ন করুন",
            if (item.reminderEnabled) "রিমাইন্ডার বন্ধ করুন" else "রিমাইন্ডার চালু করুন",
            "এডিট করুন",
            "ডিলিট করুন"
        )) { _, which ->
            val life = DailyLifeStore(this); val items = life.todos(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) {
                0 -> items[index] = item.copy(completedDate = if (item.completedDate.isBlank()) store.today() else "")
                1 -> items[index] = item.copy(reminderEnabled = !item.reminderEnabled)
                2 -> { addTodo(item); return@setItems }
                3 -> items.removeAt(index)
            }
            life.saveTodos(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }
'''
    main = replace_required(main, old_todo_actions, new_todo_actions, 'todo reminder action')

    # Medicine form is opt-in. Existing medicine action already has ON/OFF toggle.
    main = replace_required(
        main,
        'val enabled = CheckBox(this).apply { text = "রিমাইন্ডার চালু"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: true }; val vibrate = CheckBox(this).apply { text = "ভাইব্রেশন"; setTextColor(Color.WHITE); isChecked = existing?.vibrateEnabled ?: true }',
        'val enabled = CheckBox(this).apply { text = "ওষুধের অ্যালার্ম চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: false }; val vibrate = CheckBox(this).apply { text = "ভাইব্রেশন"; setTextColor(Color.WHITE); isChecked = existing?.vibrateEnabled ?: true }',
        'medicine reminder checkbox'
    )

    # Bill form/card/action gets an explicit reminder toggle.
    main = replace_required(
        main,
        'val reminder = CheckBox(this).apply { text = "Due-date সকাল ৯টায় রিমাইন্ডার"; setTextColor(Color.WHITE); isChecked = existing?.reminderEnabled ?: true }',
        'val reminder = CheckBox(this).apply { text = "Due-date সকাল ৯টায় রিমাইন্ডার চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.reminderEnabled ?: false }',
        'bill reminder checkbox'
    )
    main = replace_required(
        main,
        '"${moneyText(item.amount)} • ${item.dueDate} • ${if (paid) "পরিশোধ হয়েছে" else if (days >= 0) "$days দিন বাকি" else "${-days} দিন overdue"}"',
        '"${moneyText(item.amount)} • ${item.dueDate} • ${if (paid) "পরিশোধ হয়েছে" else if (days >= 0) "$days দিন বাকি" else "${-days} দিন overdue"} • ${if (item.reminderEnabled) "রিমাইন্ডার ON" else "রিমাইন্ডার OFF"}"',
        'bill reminder status'
    )
    old_bill_actions = '''    private fun billActions(item: BillItem) {
        val paid = item.paidDate.isNotBlank(); AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (paid) "পরিশোধ বাতিল করুন" else "পরিশোধ হয়েছে ✓", "এডিট করুন", "ডিলিট করুন")) { _, which ->
            val life = DailyLifeStore(this); val items = life.bills(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) { 0 -> items[index] = item.copy(paidDate = if (paid) "" else store.today()); 1 -> { addBill(item); return@setItems }; 2 -> items.removeAt(index) }; life.saveBills(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }
'''
    new_bill_actions = '''    private fun billActions(item: BillItem) {
        val paid = item.paidDate.isNotBlank()
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(
            if (paid) "পরিশোধ বাতিল করুন" else "পরিশোধ হয়েছে ✓",
            if (item.reminderEnabled) "রিমাইন্ডার বন্ধ করুন" else "রিমাইন্ডার চালু করুন",
            "এডিট করুন",
            "ডিলিট করুন"
        )) { _, which ->
            val life = DailyLifeStore(this); val items = life.bills(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) {
                0 -> items[index] = item.copy(paidDate = if (paid) "" else store.today())
                1 -> items[index] = item.copy(reminderEnabled = !item.reminderEnabled)
                2 -> { addBill(item); return@setItems }
                3 -> items.removeAt(index)
            }
            life.saveBills(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }
'''
    main = replace_required(main, old_bill_actions, new_bill_actions, 'bill reminder action')

    # Make the page copy clear: alarms/reminders are always optional.
    main = replace_required(main, '"Priority, deadline ও reminder সহ দৈনিক To-do."', '"কাজ সেভ করুন; দরকার হলে আলাদা করে রিমাইন্ডার ON করুন।"', 'todo subtitle')
    main = replace_required(main, '"ওষুধ, dose, সময়, ringtone ও vibration সেট করুন।"', '"ওষুধ সেভ করুন; অ্যালার্ম দরকার হলে আলাদা করে ON করুন।"', 'medicine subtitle')
    main = replace_required(main, '"ভাড়া, ইন্টারনেট, মোবাইল বা যেকোনো due bill ট্র্যাক করুন।"', '"বিল ট্র্যাক করুন; reminder দরকার হলে আলাদা করে ON করুন।"', 'bill subtitle')

    # Prayer master and per-prayer explicit switches.
    main = replace_required(main, 'text = "নামাজের আজান অ্যালার্ম চালু"', 'text = "নামাজের অ্যালার্ম সিস্টেম ON/OFF"', 'prayer master label')
    main = replace_required(
        main,
        '''        setup.addView(master)
        setup.addView(text(''',
        '''        setup.addView(master)
        setup.addView(text("রিমাইন্ডার ডিফল্টভাবে বন্ধ থাকবে। Master ON করার পর নিচে প্রয়োজনীয় নামাজ আলাদা ON করুন।", 12f, "#7DD5BC").apply { setPadding(0, dp(5), 0, dp(8)) })
        setup.addView(text(''',
        'prayer opt-in hint'
    )
    main = replace_required(
        main,
        '''                store.setPrayerEnabled(checked)
                ReminderScheduler.scheduleAll(this@MainActivity, store)
                render()''',
        '''                store.setPrayerEnabled(checked)
                ReminderScheduler.scheduleAll(this@MainActivity, store)
                if (checked && store.prayerSettings().enabledPrayers.isEmpty()) {
                    Toast.makeText(this@MainActivity, "নিচে যেসব নামাজের অ্যালার্ম দরকার সেগুলো ON করুন", Toast.LENGTH_LONG).show()
                }
                render()''',
        'prayer master listener'
    )

    prayer_switches = '''            times.forEachIndexed { index, prayer ->
                val isPrayer = prayer.key != "Sunrise"
                val enabled = settings.enabledPrayers.contains(prayer.key)
                val c = card("#17213E", padding = 14)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text("${prayer.nameBn} • ${prayer.time.format(DateTimeFormatter.ofPattern("hh:mm a"))}", 15f, "#FFFFFF", bold = true))
                labels.addView(text(if (!isPrayer) "শুধু তথ্য" else if (enabled) "আজান অ্যালার্ম ON" else "আজান অ্যালার্ম OFF", 12f, if (enabled) "#73D6B4" else "#8795BB"))
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (isPrayer) {
                    val toggle = CheckBox(this).apply {
                        text = if (enabled) "ON" else "OFF"
                        setTextColor(if (enabled) Color.parseColor("#73D6B4") else Color.parseColor("#9AA7C7"))
                        isChecked = enabled
                        setOnCheckedChangeListener { button, checked ->
                            button.text = if (checked) "ON" else "OFF"
                            button.setTextColor(if (checked) Color.parseColor("#73D6B4") else Color.parseColor("#9AA7C7"))
                            store.setPrayerAlarmEnabled(prayer.key, checked)
                            if (checked && !store.prayerSettings().enabled) store.setPrayerEnabled(true)
                            ReminderScheduler.scheduleAll(this@MainActivity, store)
                            Toast.makeText(this@MainActivity, "${prayer.nameBn} অ্যালার্ম ${if (checked) "চালু" else "বন্ধ"}", Toast.LENGTH_SHORT).show()
                            render()
                        }
                    }
                    row.addView(toggle)
                }
                c.addView(row)
                root.addView(c)
                if (index < times.lastIndex) root.addView(space(8))
            }
        }
'''
    main = replace_between(
        main,
        '            times.forEachIndexed { index, prayer ->',
        '        root.addView(space(18))\n\n        root.addView(sectionTitle("আজান সাউন্ড"))',
        prayer_switches,
        'prayer per-item switches'
    )

    main_path.write_text(main)
    print('v3.2 MainActivity opt-in reminder UI applied')
else:
    print('v3.2 MainActivity patch already applied')
