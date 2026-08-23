from pathlib import Path


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


mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideReminderUxV318' not in ms:
    if 'import android.media.AudioManager\n' not in ms:
        ms = req(ms, 'import android.media.RingtoneManager\n', 'import android.media.AudioManager\nimport android.media.RingtoneManager\n', 'main audio manager import')

    if 'volumeControlStream = AudioManager.STREAM_MUSIC' not in ms:
        ms = req(
            ms,
            '        super.onCreate(savedInstanceState)\n',
            '        super.onCreate(savedInstanceState)\n        // Guide v3.18: preview audio follows media/headphone/Bluetooth volume.\n        volumeControlStream = AudioManager.STREAM_MUSIC\n',
            'main preview volume stream relaxed anchor'
        )

    old_resume = '        if (::store.isInitialized && store.hasProfile()) render()\n'
    if old_resume in ms:
        ms = ms.replace(
            old_resume,
            '''        if (::store.isInitialized && store.hasProfile()) {
            ReminderScheduler.scheduleAll(this, store)
            render()
        }
''',
            1
        )

    helper_anchor = '    private fun buildTopBar(): View {\n'
    if helper_anchor not in ms:
        raise SystemExit('pattern not found: buildTopBar helper anchor')
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
    ms = ms.replace(helper_anchor, helpers + '    // GuideReminderUxV318\n' + helper_anchor, 1)

    routine_new = r'''    private fun addRoutine(existing: RoutineItem? = null) {
        val box = formBox()
        val titleInput = input("রুটিনের নাম").apply { setText(existing?.title ?: "") }
        val categoryInput = input("ক্যাটাগরি").apply { setText(existing?.category ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 8; minute = existing?.minute ?: 0 }
        val countdown = pickerCountdown(picker, "রুটিন শুরু হতে")
        var selectedRingtone = existing?.ringtoneUri ?: ""
        lateinit var ringtoneButton: Button
        ringtoneButton = pillButton("অডিও: ${ringtoneTitle(selectedRingtone)}", "#405179") {
            pickRingtone(selectedRingtone) { uri -> selectedRingtone = uri; ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}" }
        }
        val guideAudio = pillButton("Guide অডিও বাছাই / শুনুন", "#326E66") {
            showBuiltInSoundPicker("রুটিন অ্যালার্ম অডিও") { uri -> selectedRingtone = uri; ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}" }
        }
        val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "আপনার রুটিনের সময় হয়েছে")
        val alarmCheck = CheckBox(this).apply { text = "রুটিন অ্যালার্ম চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: false }
        box.addView(titleInput); box.addView(space(8)); box.addView(categoryInput); box.addView(picker); box.addView(countdown); box.addView(space(7)); box.addView(voicePreview); box.addView(space(7)); box.addView(guideAudio); box.addView(space(7)); box.addView(ringtoneButton); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন রুটিন" else "রুটিন এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val updated = RoutineItem(id = existing?.id ?: java.util.UUID.randomUUID().toString(), title = name, hour = picker.hour, minute = picker.minute, category = categoryInput.text.toString().trim().ifBlank { "Routine" }, doneDate = existing?.doneDate ?: "", alarmEnabled = alarmCheck.isChecked, ringtoneUri = selectedRingtone)
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
            pickRingtone(selectedRingtone) { uri -> selectedRingtone = uri; ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}" }
        }
        val guideAudio = pillButton("Guide অডিও বাছাই / শুনুন", "#287B69") {
            showBuiltInSoundPicker("খাবারের অ্যালার্ম অডিও") { uri -> selectedRingtone = uri; ringtoneButton.text = "অডিও: ${ringtoneTitle(uri)}" }
        }
        val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "খাবার খাওয়ার সময় হয়েছে")
        val alarmCheck = CheckBox(this).apply { text = "খাবারের অ্যালার্ম চালু করুন (ঐচ্ছিক)"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: false }
        box.addView(titleInput); box.addView(space(8)); box.addView(noteInput); box.addView(picker); box.addView(countdown); box.addView(space(7)); box.addView(voicePreview); box.addView(space(7)); box.addView(guideAudio); box.addView(space(7)); box.addView(ringtoneButton); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন খাবারের রুটিন" else "খাবারের রুটিন এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val updated = MealItem(id = existing?.id ?: java.util.UUID.randomUUID().toString(), title = name, hour = picker.hour, minute = picker.minute, note = noteInput.text.toString().trim(), doneDate = existing?.doneDate ?: "", alarmEnabled = alarmCheck.isChecked, ringtoneUri = selectedRingtone)
            val items = store.meals(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveMeals(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); ensureAlarmSystemReady(alarmCheck.isChecked); render()
        }.setNegativeButton("বাতিল", null).show()
    }

'''
    ms = replace_between(ms, '    private fun addMeal(existing: MealItem? = null) {', '    private fun alarmActions(item: AlarmItem) {', meal_new, 'meal editor')

    med_start = ms.find('    private fun addMedicine(existing: MedicineItem? = null) {')
    med_end = ms.find('    private fun medicineActions(item: MedicineItem) {', med_start)
    if med_start < 0 or med_end < 0:
        raise SystemExit('pattern not found: medicine editor')
    medicine_new = r'''    private fun addMedicine(existing: MedicineItem? = null) {
        val box = formBox()
        val name = input("ওষুধের নাম").apply { setText(existing?.name ?: "") }
        val dose = input("Dose / নির্দেশনা").apply { setText(existing?.dose ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 8; minute = existing?.minute ?: 0 }
        val countdown = pickerCountdown(picker, "ওষুধ খেতে")
        var selectedRingtone = existing?.ringtoneUri ?: ""
        lateinit var ringButton: Button
        ringButton = pillButton("অডিও: ${ringtoneTitle(selectedRingtone)}", "#49365F") { pickRingtone(selectedRingtone) { uri -> selectedRingtone = uri; ringButton.text = "অডিও: ${ringtoneTitle(uri)}" } }
        val guideAudio = pillButton("Guide অডিও বাছাই / শুনুন", "#7B4662") { showBuiltInSoundPicker("ওষুধের অ্যালার্ম অডিও") { uri -> selectedRingtone = uri; ringButton.text = "অডিও: ${ringtoneTitle(uri)}" } }
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
    ms = ms[:med_start] + medicine_new + ms[med_end:]

    alarm_marker = '        val soundCheck = CheckBox(this).apply { text = "রিংটোন বাজবে"; setTextColor(Color.WHITE); isChecked = existing?.soundEnabled ?: true }\n'
    if alarm_marker in ms and 'val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "আপনার অ্যালার্মের সময় হয়েছে")' not in ms:
        ms = ms.replace(alarm_marker, '        val voicePreview = voicePreviewButton("▶ বাংলা ভয়েস শুনুন", "আপনার অ্যালার্মের সময় হয়েছে")\n' + alarm_marker, 1)
        ms = ms.replace('box.addView(space(7)); box.addView(ringtoneButton); box.addView(soundCheck)', 'box.addView(space(7)); box.addView(ringtoneButton); box.addView(space(7)); box.addView(voicePreview); box.addView(soundCheck)', 1)

    save_alarm = '            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()\n'
    if save_alarm in ms:
        ms = ms.replace(save_alarm, '            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); ensureAlarmSystemReady(enabledCheck.isChecked); render()\n', 1)

    prayer_old = '                val subtitle = if (!isPrayer) "শুধু তথ্য" else if (enabled) "আজান অ্যালার্ম চালু" else "আজান অ্যালার্ম বন্ধ"\n'
    if prayer_old in ms:
        ms = ms.replace(prayer_old, '                val left = remainingText(prayer.time.hour, prayer.time.minute)\n                val subtitle = if (!isPrayer) "শুধু তথ্য • $left বাকি" else if (enabled) "আজান অ্যালার্ম চালু • $left বাকি" else "আজান অ্যালার্ম বন্ধ • $left বাকি"\n', 1)

    prayer_master = '                store.setPrayerEnabled(checked)\n                ReminderScheduler.scheduleAll(this@MainActivity, store)\n                render()\n'
    if prayer_master in ms:
        ms = ms.replace(prayer_master, '                store.setPrayerEnabled(checked)\n                ReminderScheduler.scheduleAll(this@MainActivity, store)\n                ensureAlarmSystemReady(checked)\n                render()\n', 1)

    mp.write_text(ms)
    print('v3.18 recovery: MainActivity reminder UX applied')

manifest = Path('app/src/main/AndroidManifest.xml')
mx = manifest.read_text()
if 'GuideAlarmService' not in mx:
    if 'android.permission.FOREGROUND_SERVICE"' not in mx:
        mx = req(mx, '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n', '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n', 'foreground service permissions')
    mx = req(mx, '        <activity android:name=".BackupActivity" android:exported="false" />\n', '        <service android:name=".GuideAlarmService" android:exported="false" android:foregroundServiceType="mediaPlayback" />\n\n        <activity android:name=".BackupActivity" android:exported="false" />\n', 'alarm service manifest entry')
    manifest.write_text(mx)
    print('v3.18 recovery: manifest service applied')

bp = Path('app/build.gradle.kts')
bs = bp.read_text()
if 'versionCode = 31' not in bs:
    bs = bs.replace('versionCode = 30', 'versionCode = 31', 1)
    bs = bs.replace('versionName = "3.17.0"', 'versionName = "3.18.0"', 1)
    bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
if '"appVersion" to "3.18.0"' not in cs:
    cs = cs.replace('"appVersion" to "3.17.0"', '"appVersion" to "3.18.0"', 1)
    cp.write_text(cs)

print('v3.18 recovery: version metadata applied')
