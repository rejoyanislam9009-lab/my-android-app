from pathlib import Path

# -------- MainActivity --------
p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()

if 'দৈনন্দিন জীবন • NEW' not in s:
    def rep(old, new, name, count=1):
        global s
        if old not in s:
            raise SystemExit(f'pattern not found MainActivity: {name}')
        s = s.replace(old, new, count)

    rep('''            "prayer" -> "নামাজের সময়সূচি"
            else -> "Guide"''', '''            "prayer" -> "নামাজের সময়সূচি"
            "todos" -> "করণীয় কাজ"
            "habits" -> "অভ্যাস ট্র্যাকার"
            "medicine" -> "ওষুধের রিমাইন্ডার"
            "bills" -> "বিল রিমাইন্ডার"
            "weekly" -> "সাপ্তাহিক রিপোর্ট"
            else -> "Guide"''', 'screen titles')

    rep('''        menu.addView(drawerSection("অ্যাকাউন্ট"))''', '''        menu.addView(drawerSection("দৈনন্দিন জীবন • NEW"))
        menu.addView(drawerItem("☑", "করণীয় কাজ • NEW", detailPage == "todos") { navigate("plan", "todos") })
        menu.addView(drawerItem("★", "অভ্যাস ট্র্যাকার • NEW", detailPage == "habits") { navigate("track", "habits") })
        menu.addView(drawerItem("✚", "ওষুধের রিমাইন্ডার • NEW", detailPage == "medicine") { navigate("plan", "medicine") })
        menu.addView(drawerItem("▦", "বিল রিমাইন্ডার • NEW", detailPage == "bills") { navigate("track", "bills") })
        menu.addView(drawerItem("◫", "সাপ্তাহিক রিপোর্ট • NEW", detailPage == "weekly") { navigate("track", "weekly") })
        menu.addView(space(10))

        menu.addView(drawerSection("অ্যাকাউন্ট"))''', 'daily sidebar')

    rep('''            label.startsWith("PDF") -> "#3D446A"
            else -> "#202C4D"''', '''            label.startsWith("PDF") -> "#3D446A"
            label.startsWith("করণীয়") -> "#49365F"
            label.startsWith("অভ্যাস") -> "#24554D"
            label.startsWith("ওষুধ") -> "#5B3549"
            label.startsWith("বিল") -> "#5D4928"
            label.startsWith("সাপ্তাহিক") -> "#30496A"
            else -> "#202C4D"''', 'daily sidebar colors')

    rep('''        val today = store.today()
        val routines = store.routines()''', '''        val daily = DailyLifeStore(this)
        val todayDate = LocalDate.now()
        val dueToday = daily.todos().count { it.dueDate == todayDate.toString() && it.completedDate.isBlank() }
        val habits = daily.habits()
        val habitsDone = habits.count { it.completedDates.contains(todayDate.toString()) }
        val nextMedicine = daily.medicines().filter { it.enabled }.minByOrNull { nextTime(it.hour, it.minute) }
        val pendingBills = daily.bills().count { it.paidDate.isBlank() }
        val lifeCard = card("#14243A")
        lifeCard.addView(text("দৈনন্দিন জীবন • NEW", 13f, "#AFC4EA", bold = true))
        lifeCard.addView(text("আজকের কাজ $dueToday  •  অভ্যাস $habitsDone/${habits.size}  •  বাকি বিল $pendingBills", 15f, "#FFFFFF", bold = true).apply { setPadding(0, dp(7), 0, 0) })
        lifeCard.addView(text(if (nextMedicine == null) "আজ কোনো সক্রিয় ওষুধের রিমাইন্ডার নেই" else "পরবর্তী ওষুধ: ${nextMedicine.name} • ${timeText(nextMedicine.hour, nextMedicine.minute)}", 12f, "#8FA9D1").apply { setPadding(0, dp(6), 0, 0) })
        lifeCard.setOnClickListener { detailPage = "todos"; currentTab = "plan"; render() }
        root.addView(lifeCard)
        root.addView(space(18))

        val today = store.today()
        val routines = store.routines()''', 'dashboard daily summary')

    marker = '    private fun coursesPage(): LinearLayout {'
    if marker not in s:
        raise SystemExit('pattern not found MainActivity: courses marker')
    feature_code = r'''    private fun todosPage(): LinearLayout {
        val root = page(); detailHeader(root, "করণীয় কাজ • NEW", "Priority, deadline ও reminder সহ দৈনিক To-do.", "+ যোগ") { addTodo() }
        val items = DailyLifeStore(this).todos().sortedWith(compareBy<TodoItem> { it.completedDate.isNotBlank() }.thenBy { it.dueDate }.thenBy { it.hour }.thenBy { it.minute })
        if (items.isEmpty()) root.addView(emptyCard("কোনো কাজ নেই", "আজকের প্রথম কাজটি যোগ করুন।"))
        items.forEachIndexed { i, item ->
            val done = item.completedDate.isNotBlank()
            val priority = when (item.priority) { "High" -> "জরুরি"; "Low" -> "কম"; else -> "সাধারণ" }
            root.addView(itemCard(if (done) "✓" else "☑", item.title, "${item.dueDate} • ${timeText(item.hour, item.minute)} • $priority${if (item.reminderEnabled && !done) " • রিমাইন্ডার" else ""}", if (done) "#26705B" else if (item.priority == "High") "#A94C59" else "#5B4FC0") { todoActions(item) })
            if (i < items.lastIndex) root.addView(space(9))
        }
        return root
    }

    private fun addTodo(existing: TodoItem? = null) {
        val box = formBox(); val title = input("কাজের নাম").apply { setText(existing?.title ?: "") }; val date = input("তারিখ YYYY-MM-DD").apply { setText(existing?.dueDate ?: store.today()) }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 9; minute = existing?.minute ?: 0 }
        val priority = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("High", "Normal", "Low")); setSelection(listOf("High", "Normal", "Low").indexOf(existing?.priority ?: "Normal").coerceAtLeast(0)) }
        val reminder = CheckBox(this).apply { text = "রিমাইন্ডার চালু"; setTextColor(Color.WHITE); isChecked = existing?.reminderEnabled ?: true }
        box.addView(title); box.addView(space(8)); box.addView(date); box.addView(space(8)); box.addView(priority); box.addView(picker); box.addView(reminder)
        AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন করণীয়" else "কাজ এডিট করুন").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = title.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val due = date.text.toString().trim(); if (runCatching { LocalDate.parse(due) }.isFailure) { Toast.makeText(this, "তারিখ YYYY-MM-DD দিন", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
            val life = DailyLifeStore(this); val items = life.todos(); val updated = TodoItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, due, picker.hour, picker.minute, priority.selectedItem.toString(), reminder.isChecked, existing?.completedDate ?: "")
            val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated); life.saveTodos(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.setNegativeButton("বাতিল", null).show()
    }

    private fun todoActions(item: TodoItem) {
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (item.completedDate.isBlank()) "সম্পন্ন করুন" else "আবার অসম্পন্ন করুন", "এডিট করুন", "ডিলিট করুন")) { _, which ->
            val life = DailyLifeStore(this); val items = life.todos(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) { 0 -> items[index] = item.copy(completedDate = if (item.completedDate.isBlank()) store.today() else ""); 1 -> { addTodo(item); return@setItems }; 2 -> items.removeAt(index) }
            life.saveTodos(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }

    private fun habitsPage(): LinearLayout {
        val root = page(); detailHeader(root, "অভ্যাস ট্র্যাকার • NEW", "প্রতিদিনের ছোট ভালো অভ্যাস ও streak ট্র্যাক করুন।", "+ যোগ") { addHabit() }
        val life = DailyLifeStore(this); val items = life.habits(); val today = store.today()
        if (items.isEmpty()) root.addView(emptyCard("কোনো অভ্যাস নেই", "হাঁটা, পড়াশোনা, ব্যায়াম বা যেকোনো ভালো অভ্যাস যোগ করুন।"))
        items.forEachIndexed { i, item ->
            val done = item.completedDates.contains(today); root.addView(itemCard(if (done) "★" else "☆", item.title, "${if (done) "আজ সম্পন্ন" else "আজ বাকি"} • 🔥 ${life.habitStreak(item)} দিনের streak", if (done) "#278166" else "#486177") { habitActions(item) })
            if (i < items.lastIndex) root.addView(space(9))
        }
        return root
    }

    private fun addHabit(existing: HabitItem? = null) {
        val input = input("অভ্যাসের নাম").apply { setText(existing?.title ?: "") }
        AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন অভ্যাস" else "অভ্যাস এডিট").setView(input).setPositiveButton("সেভ") { _, _ ->
            val name = input.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val life = DailyLifeStore(this); val items = life.habits(); val updated = HabitItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, existing?.completedDates ?: emptySet()); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated); life.saveHabits(items); CloudSyncManager.scheduleUpload(this); render()
        }.setNegativeButton("বাতিল", null).show()
    }

    private fun habitActions(item: HabitItem) {
        val today = store.today(); val done = item.completedDates.contains(today)
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (done) "আজকের টিক সরান" else "আজ সম্পন্ন ✓", "এডিট করুন", "ডিলিট করুন")) { _, which ->
            val life = DailyLifeStore(this); val items = life.habits(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) { 0 -> { val dates = item.completedDates.toMutableSet(); if (done) dates.remove(today) else dates.add(today); items[index] = item.copy(completedDates = dates) }; 1 -> { addHabit(item); return@setItems }; 2 -> items.removeAt(index) }
            life.saveHabits(items); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }

    private fun medicinePage(): LinearLayout {
        val root = page(); detailHeader(root, "ওষুধের রিমাইন্ডার • NEW", "ওষুধ, dose, সময়, ringtone ও vibration সেট করুন।", "+ যোগ") { addMedicine() }
        val items = DailyLifeStore(this).medicines().sortedWith(compareBy<MedicineItem> { it.hour }.thenBy { it.minute })
        if (items.isEmpty()) root.addView(emptyCard("ওষুধ যোগ করা হয়নি", "প্রয়োজনীয় medicine reminder যোগ করুন।"))
        items.forEachIndexed { i, item -> root.addView(itemCard(if (item.enabled) "✚" else "○", item.name, "${item.dose.ifBlank { "Dose লেখা নেই" }} • ${timeText(item.hour, item.minute)} • ${if (item.enabled) remainingText(item.hour, item.minute) + " বাকি" else "বন্ধ"}", if (item.enabled) "#A84B69" else "#4C566B") { medicineActions(item) }).also { if (i < items.lastIndex) root.addView(space(9)) } }
        return root
    }

    private fun addMedicine(existing: MedicineItem? = null) {
        val box = formBox(); val name = input("ওষুধের নাম").apply { setText(existing?.name ?: "") }; val dose = input("Dose / নির্দেশনা").apply { setText(existing?.dose ?: "") }; val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 8; minute = existing?.minute ?: 0 }
        var selectedRingtone = existing?.ringtoneUri ?: ""; lateinit var ringButton: Button
        ringButton = pillButton("রিংটোন: ${ringtoneTitle(selectedRingtone)}", "#49365F") { pickRingtone(selectedRingtone) { uri -> selectedRingtone = uri; ringButton.text = "রিংটোন: ${ringtoneTitle(uri)}" } }
        val enabled = CheckBox(this).apply { text = "রিমাইন্ডার চালু"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: true }; val vibrate = CheckBox(this).apply { text = "ভাইব্রেশন"; setTextColor(Color.WHITE); isChecked = existing?.vibrateEnabled ?: true }
        box.addView(name); box.addView(space(8)); box.addView(dose); box.addView(picker); box.addView(ringButton); box.addView(enabled); box.addView(vibrate)
        AlertDialog.Builder(this).setTitle(if (existing == null) "ওষুধ যোগ করুন" else "ওষুধ এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val n = name.text.toString().trim(); if (n.isBlank()) return@setPositiveButton
            val life = DailyLifeStore(this); val items = life.medicines(); val updated = MedicineItem(existing?.id ?: java.util.UUID.randomUUID().toString(), n, dose.text.toString().trim(), picker.hour, picker.minute, enabled.isChecked, vibrate.isChecked, selectedRingtone); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated); life.saveMedicines(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.setNegativeButton("বাতিল", null).show()
    }

    private fun medicineActions(item: MedicineItem) {
        AlertDialog.Builder(this).setTitle(item.name).setItems(arrayOf(if (item.enabled) "রিমাইন্ডার বন্ধ" else "রিমাইন্ডার চালু", "এডিট করুন", "ডিলিট করুন")) { _, which ->
            val life = DailyLifeStore(this); val items = life.medicines(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) { 0 -> items[index] = item.copy(enabled = !item.enabled); 1 -> { addMedicine(item); return@setItems }; 2 -> items.removeAt(index) }; life.saveMedicines(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }

    private fun billsPage(): LinearLayout {
        val root = page(); detailHeader(root, "বিল রিমাইন্ডার • NEW", "ভাড়া, ইন্টারনেট, মোবাইল বা যেকোনো due bill ট্র্যাক করুন।", "+ যোগ") { addBill() }
        val items = DailyLifeStore(this).bills().sortedBy { it.dueDate }
        if (items.isEmpty()) root.addView(emptyCard("কোনো বিল নেই", "পরবর্তী bill due-date যোগ করুন।"))
        items.forEachIndexed { i, item ->
            val paid = item.paidDate.isNotBlank(); val days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(item.dueDate)) }.getOrDefault(0)
            root.addView(itemCard(if (paid) "✓" else "▦", item.title, "${moneyText(item.amount)} • ${item.dueDate} • ${if (paid) "পরিশোধ হয়েছে" else if (days >= 0) "$days দিন বাকি" else "${-days} দিন overdue"}", if (paid) "#27705B" else if (days < 0) "#A64C57" else "#9A7330") { billActions(item) }); if (i < items.lastIndex) root.addView(space(9))
        }
        return root
    }

    private fun addBill(existing: BillItem? = null) {
        val box = formBox(); val title = input("বিলের নাম").apply { setText(existing?.title ?: "") }; val amount = input("পরিমাণ").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; if (existing != null) setText(existing.amount.toString()) }; val date = input("Due date YYYY-MM-DD").apply { setText(existing?.dueDate ?: store.today()) }; val reminder = CheckBox(this).apply { text = "Due-date সকাল ৯টায় রিমাইন্ডার"; setTextColor(Color.WHITE); isChecked = existing?.reminderEnabled ?: true }
        box.addView(title); box.addView(space(8)); box.addView(amount); box.addView(space(8)); box.addView(date); box.addView(reminder)
        AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন বিল" else "বিল এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = title.text.toString().trim(); val value = amount.text.toString().toDoubleOrNull() ?: 0.0; val due = date.text.toString().trim(); if (name.isBlank() || value <= 0 || runCatching { LocalDate.parse(due) }.isFailure) { Toast.makeText(this, "সঠিক নাম, amount ও date দিন", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
            val life = DailyLifeStore(this); val items = life.bills(); val updated = BillItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, value, due, reminder.isChecked, existing?.paidDate ?: ""); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated); life.saveBills(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.setNegativeButton("বাতিল", null).show()
    }

    private fun billActions(item: BillItem) {
        val paid = item.paidDate.isNotBlank(); AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (paid) "পরিশোধ বাতিল করুন" else "পরিশোধ হয়েছে ✓", "এডিট করুন", "ডিলিট করুন")) { _, which ->
            val life = DailyLifeStore(this); val items = life.bills(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) { 0 -> items[index] = item.copy(paidDate = if (paid) "" else store.today()); 1 -> { addBill(item); return@setItems }; 2 -> items.removeAt(index) }; life.saveBills(items); ReminderScheduler.scheduleAll(this, store); CloudSyncManager.scheduleUpload(this); render()
        }.show()
    }

    private fun weeklyPage(): LinearLayout {
        val root = page(); detailHeader(root, "সাপ্তাহিক জীবন রিপোর্ট • NEW", "গত ৭ দিনের কাজ ও অভ্যাসের সহজ সারাংশ।")
        val life = DailyLifeStore(this); val w = life.weeklySummary(); val hero = card("#19304A")
        hero.addView(text("গত ৭ দিনের অগ্রগতি", 13f, "#9CB7D8", bold = true)); hero.addView(text("কাজ সম্পন্ন ${w.todoDone}/${w.todoTotal}", 21f, "#FFFFFF", bold = true).apply { setPadding(0, dp(6), 0, 0) }); hero.addView(text("অভ্যাস চেক ${w.habitChecks}/${w.habitPossible}  •  সক্রিয় ওষুধ ${w.medicineCount}  •  বাকি বিল ${w.billsPending}", 13f, "#A8BAD4").apply { setPadding(0, dp(7), 0, 0) }); root.addView(hero); root.addView(space(16))
        root.addView(rowCard("☑", "করণীয় কাজ", "সপ্তাহে ${w.todoDone}টি সম্পন্ন", "#6555D8") { detailPage = "todos"; render() }); root.addView(space(9)); root.addView(rowCard("★", "অভ্যাস", "${w.habitChecks}টি habit check", "#278166") { detailPage = "habits"; render() }); root.addView(space(9)); root.addView(rowCard("▦", "বাকি বিল", "${w.billsPending}টি pending", "#9A7330") { detailPage = "bills"; render() }); root.addView(space(18))
        root.addView(pillButton("রিপোর্ট শেয়ার করুন", "#5368D8") { shareWeeklyLife(w) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        return root
    }

    private fun shareWeeklyLife(w: WeeklyLifeSummary) {
        val message = "Guide সাপ্তাহিক জীবন রিপোর্ট\nকাজ সম্পন্ন: ${w.todoDone}/${w.todoTotal}\nঅভ্যাস চেক: ${w.habitChecks}/${w.habitPossible}\nসক্রিয় ওষুধ: ${w.medicineCount}\nবাকি বিল: ${w.billsPending}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, "রিপোর্ট শেয়ার করুন"))
    }

'''
    s = s.replace(marker, feature_code + marker, 1)

    rep('''        "attendance" -> attendancePage()
        else -> buildPlanPage()''', '''        "attendance" -> attendancePage()
        "todos" -> todosPage()
        "habits" -> habitsPage()
        "medicine" -> medicinePage()
        "bills" -> billsPage()
        "weekly" -> weeklyPage()
        else -> buildPlanPage()''', 'detail page routes')

    p.write_text(s)
    print('v2.9 MainActivity daily-life patch applied')
else:
    print('v2.9 MainActivity patch already applied')

# -------- Reminders --------
rp = Path('app/src/main/java/com/guide/app/Reminders.kt')
r = rp.read_text()
if 'medicine:${it.id}' not in r:
    old = '''        PrayerScheduler.scheduleAll(context, store)
        refreshAlarmIndicator(context, store)'''
    new = '''        val dailyLife = DailyLifeStore(context)
        dailyLife.medicines().forEach {
            if (it.enabled) scheduleDaily(context, "medicine:${it.id}", "ওষুধ: ${it.name}", it.dose.ifBlank { "ওষুধ খাওয়ার সময় হয়েছে" }, it.hour, it.minute, it.ringtoneUri, true, it.vibrateEnabled)
            else cancel(context, "medicine:${it.id}")
        }
        dailyLife.todos().forEach {
            val key = "todo:${it.id}"
            val trigger = runCatching { java.time.LocalDate.parse(it.dueDate).atTime(it.hour, it.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            if (it.reminderEnabled && it.completedDate.isBlank() && trigger != null && trigger > System.currentTimeMillis()) scheduleOneShot(context, key, "করণীয়: ${it.title}", "আজকের নির্ধারিত কাজ", trigger)
            else cancel(context, key)
        }
        dailyLife.bills().forEach {
            val key = "bill:${it.id}"
            val trigger = runCatching { java.time.LocalDate.parse(it.dueDate).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            if (it.reminderEnabled && it.paidDate.isBlank() && trigger != null && trigger > System.currentTimeMillis()) scheduleOneShot(context, key, "বিল বাকি: ${it.title}", "আজ বিল পরিশোধের শেষ তারিখ", trigger)
            else cancel(context, key)
        }
        PrayerScheduler.scheduleAll(context, store)
        refreshAlarmIndicator(context, store)'''
    if old not in r:
        raise SystemExit('pattern not found Reminders: scheduleAll end')
    r = r.replace(old, new, 1)

    old2 = '''        PrayerScheduler.nextPrayer(context, store)?.let { (prayer, target) ->
            val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            candidates += Triple(millis, "${prayer.nameBn} নামাজ", target.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        val next = candidates.minByOrNull { it.first }'''
    new2 = '''        PrayerScheduler.nextPrayer(context, store)?.let { (prayer, target) ->
            val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            candidates += Triple(millis, "${prayer.nameBn} নামাজ", target.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        val life = DailyLifeStore(context)
        life.medicines().filter { it.enabled }.forEach { item ->
            candidates += Triple(nextAlarmMillis(item.hour, item.minute), "ওষুধ: ${item.name}", LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a")))
        }
        life.todos().filter { it.reminderEnabled && it.completedDate.isBlank() }.forEach { item ->
            runCatching { java.time.LocalDate.parse(item.dueDate).atTime(item.hour, item.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()?.takeIf { it > System.currentTimeMillis() }?.let { candidates += Triple(it, "করণীয়: ${item.title}", LocalTime.of(item.hour, item.minute).format(DateTimeFormatter.ofPattern("hh:mm a"))) }
        }
        life.bills().filter { it.reminderEnabled && it.paidDate.isBlank() }.forEach { item ->
            runCatching { java.time.LocalDate.parse(item.dueDate).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()?.takeIf { it > System.currentTimeMillis() }?.let { candidates += Triple(it, "বিল: ${item.title}", "09:00 AM") }
        }
        val next = candidates.minByOrNull { it.first }'''
    if old2 not in r:
        raise SystemExit('pattern not found Reminders: indicator')
    r = r.replace(old2, new2, 1)
    rp.write_text(r)
    print('v2.9 ReminderScheduler daily-life reminders applied')
else:
    print('v2.9 ReminderScheduler patch already applied')
