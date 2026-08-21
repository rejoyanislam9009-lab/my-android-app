package com.guide.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var store: GuideStore
    private val handler = Handler(Looper.getMainLooper())
    private val uiPrefs by lazy { getSharedPreferences("guide_ui", MODE_PRIVATE) }
    private var clockView: TextView? = null
    private var currentTab = "home"
    private var detailPage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuideStore(this)
        if (!store.hasProfile()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        store.seedDefaultsIfNeeded()
        ReminderScheduler.ensureChannel(this)
        ReminderScheduler.scheduleAll(this, store)
        requestNotificationsIfNeeded()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && store.hasProfile()) render()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    private fun render() {
        handler.removeCallbacksAndMessages(null)
        setContentView(buildShell())
        if (currentTab == "home" && detailPage == null) startClock()
    }

    private fun buildShell(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient("#080D1A", "#111A35")
        }

        val body = if (detailPage != null) buildDetailPage(detailPage!!) else when (currentTab) {
            "plan" -> buildPlanPage()
            "track" -> buildTrackPage()
            "more" -> buildMorePage()
            else -> buildHomePage()
        }

        shell.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        shell.addView(buildBottomNav())
        return shell
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(30))
    }

    private fun buildHomePage(): LinearLayout {
        val root = page()
        val today = store.today()
        val routines = store.routines()
        val meals = store.meals()
        val routineDone = routines.count { it.doneDate == today }
        val mealDone = meals.count { it.doneDate == today }
        val total = routines.size + meals.size
        val done = routineDone + mealDone
        val progress = if (total == 0) 0 else ((done.toDouble() / total) * 100).roundToInt()
        store.updateCompletedDay(total > 0 && done == total)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val greeting = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        greeting.addView(text(greetingText().uppercase(Locale.getDefault()), 11f, "#7F8BB4", bold = true).apply { letterSpacing = 0.12f })
        greeting.addView(text(store.profileName(), 27f, "#FFFFFF", bold = true))
        top.addView(greeting, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(pillButton("Lock", "#1D2949") { lockApp() }, LinearLayout.LayoutParams(dp(76), dp(44)))
        root.addView(top)
        root.addView(space(18))

        val hero = card("#182342")
        val heroTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dateBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dateBlock.addView(text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM")), 14f, "#A7B3D8"))
        clockView = text("--:--:--", 31f, "#FFFFFF", bold = true)
        dateBlock.addView(clockView)
        heroTop.addView(dateBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heroTop.addView(statusPill("🔥 ${store.streak()} day streak", "#342B2B", "#FFD285"))
        hero.addView(heroTop)
        hero.addView(space(18))
        hero.addView(text("Today's progress • $done of $total completed", 14f, "#D2D8EF"))
        hero.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = progress
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7A5CFF"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#44506E"))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(10) })
        hero.addView(text("$progress% • ${nextReminderText()}", 13f, "#8F9DC8").apply { setPadding(0, dp(8), 0, 0) })
        root.addView(hero)
        root.addView(space(18))

        root.addView(sectionTitle("Quick actions"))
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quickRow.addView(compactAction("⏰", "Alarm", "Add reminder", "#6254F5") { addAlarm() }, LinearLayout.LayoutParams(0, dp(116), 1f))
        quickRow.addView(hSpace(10))
        quickRow.addView(compactAction("✓", "Attendance", store.attendanceFor(), "#E3785B") { currentTab = "track"; render() }, LinearLayout.LayoutParams(0, dp(116), 1f))
        root.addView(quickRow)
        root.addView(space(18))

        root.addView(sectionTitle("Up next"))
        root.addView(rowCard("⏱", "Next reminder", nextReminderText(), "#315B8C") { currentTab = "plan"; render() })
        root.addView(space(18))

        root.addView(sectionTitle("Today's routines"))
        if (routines.isEmpty()) {
            root.addView(emptyCard("No routines yet", "Add your first routine from Plan."))
        } else {
            routines.sortedWith(compareBy<RoutineItem> { it.hour }.thenBy { it.minute }).take(3).forEachIndexed { index, item ->
                val doneNow = item.doneDate == today
                root.addView(itemCard(if (doneNow) "✓" else "○", item.title, "${timeText(item.hour, item.minute)} • ${item.category}", if (doneNow) "#1B6D58" else "#3D4C7D") {
                    toggleRoutine(item)
                })
                if (index < minOf(2, routines.size - 1)) root.addView(space(9))
            }
        }
        root.addView(space(18))

        val money = store.currentMonthMoneySummary()
        root.addView(sectionTitle("This month"))
        root.addView(rowCard("▣", "Accounts", "Income ${moneyText(money.first)} • Expense ${moneyText(money.second)}", "#287A67") {
            detailPage = "money"; currentTab = "track"; render()
        })
        root.addView(space(10))
        root.addView(waterCard())
        root.addView(space(18))

        root.addView(sectionTitle("Focus note"))
        val note = uiPrefs.getString("focus_${store.today()}", "") ?: ""
        val noteCard = card("#151F3B")
        noteCard.addView(text(if (note.isBlank()) "Tap to set your main focus for today." else "“$note”", 15f, if (note.isBlank()) "#8795BF" else "#FFFFFF"))
        noteCard.setOnClickListener { editFocusNote(note) }
        root.addView(noteCard)
        return root
    }

    private fun buildPlanPage(): LinearLayout {
        val root = page()
        pageHeader(root, "Plan", "Build a schedule that actually works.")
        val routines = store.routines()
        val meals = store.meals()
        val alarms = store.alarms()
        val courses = store.courses()

        root.addView(sectionTitle("Planning tools"))
        root.addView(rowCard("✓", "Routines", "${routines.size} routines • ${routines.count { it.alarmEnabled }} reminders on", "#6759F5") { detailPage = "routines"; render() })
        root.addView(space(10))
        root.addView(rowCard("🍽", "Meals", "${meals.size} meal plans • ${meals.count { it.alarmEnabled }} reminders on", "#1E9B83") { detailPage = "meals"; render() })
        root.addView(space(10))
        root.addView(rowCard("⏰", "Alarms", "${alarms.count { it.enabled }} active custom alarms", "#3179B5") { detailPage = "alarms"; render() })
        root.addView(space(10))
        root.addView(rowCard("▤", "Courses", "${courses.size} active learning plans", "#BA7A33") { detailPage = "courses"; render() })
        root.addView(space(22))

        root.addView(sectionTitle("Today's schedule"))
        val schedule = mutableListOf<Triple<Int, Int, String>>()
        routines.forEach { schedule += Triple(it.hour, it.minute, "Routine • ${it.title}") }
        meals.forEach { schedule += Triple(it.hour, it.minute, "Meal • ${it.title}") }
        alarms.filter { it.enabled }.forEach { schedule += Triple(it.hour, it.minute, "Alarm • ${it.title}") }
        if (schedule.isEmpty()) root.addView(emptyCard("Nothing scheduled", "Use the planning tools above to add your day."))
        else schedule.sortedWith(compareBy<Triple<Int, Int, String>> { it.first }.thenBy { it.second }).take(10).forEachIndexed { i, entry ->
            root.addView(simpleLine(timeText(entry.first, entry.second), entry.third))
            if (i < schedule.size.coerceAtMost(10) - 1) root.addView(space(8))
        }
        return root
    }

    private fun buildTrackPage(): LinearLayout {
        val root = page()
        pageHeader(root, "Track", "Attendance, money and daily consistency.")

        val summary = store.attendanceSummaryForCurrentMonth()
        root.addView(sectionTitle("Attendance"))
        val attendanceCard = card("#182342")
        attendanceCard.addView(text("Today: ${store.attendanceFor()}", 18f, "#FFFFFF", bold = true))
        attendanceCard.addView(text("Present ${summary["Present"]}  •  Absent ${summary["Absent"]}  •  Leave ${summary["Leave"]}", 13f, "#93A0C8").apply { setPadding(0, dp(5), 0, dp(14)) })
        val markRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        markRow.addView(smallAction("Present", "#237B64") { store.setAttendance("Present"); render() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        markRow.addView(hSpace(8))
        markRow.addView(smallAction("Absent", "#A14E58") { store.setAttendance("Absent"); render() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        markRow.addView(hSpace(8))
        markRow.addView(smallAction("Leave", "#9B7331") { store.setAttendance("Leave"); render() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        attendanceCard.addView(markRow)
        attendanceCard.setOnLongClickListener { detailPage = "attendance"; render(); true }
        root.addView(attendanceCard)
        root.addView(text("Long-press attendance card for history", 11f, "#69789F").apply { setPadding(dp(4), dp(7), 0, 0) })
        root.addView(space(20))

        val money = store.currentMonthMoneySummary()
        root.addView(sectionTitle("Accounts"))
        val accountCard = card("#17243B")
        accountCard.addView(text("Balance ${moneyText(money.third)}", 21f, if (money.third >= 0) "#70D0AE" else "#FF8B93", bold = true))
        accountCard.addView(text("Income ${moneyText(money.first)}  •  Expense ${moneyText(money.second)}", 13f, "#94A3C7").apply { setPadding(0, dp(6), 0, dp(14)) })
        accountCard.addView(pillButton("Open accounts", "#245F58") { detailPage = "money"; render() })
        root.addView(accountCard)
        root.addView(space(20))

        root.addView(sectionTitle("Hydration"))
        root.addView(waterCard())
        root.addView(space(20))

        root.addView(sectionTitle("Recent attendance"))
        store.attendanceHistory(7).forEachIndexed { i, pair ->
            val date = runCatching { LocalDate.parse(pair.first).format(DateTimeFormatter.ofPattern("EEE, dd MMM")) }.getOrDefault(pair.first)
            root.addView(simpleLine(date, pair.second))
            if (i < 6) root.addView(space(8))
        }
        return root
    }

    private fun buildMorePage(): LinearLayout {
        val root = page()
        pageHeader(root, "More", "Profile, reminder settings and backup.")

        root.addView(sectionTitle("Reminder system"))
        val exact = ReminderScheduler.exactAlarmAvailable(this)
        val notificationOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        root.addView(settingCard("Notifications", if (notificationOk) "Allowed" else "Permission needed", if (notificationOk) "#54C6A2" else "#FF9B6A") { openNotificationSettings() })
        root.addView(space(9))
        root.addView(settingCard("Precise alarms", if (exact) "Enabled" else "Allow exact timing", if (exact) "#54C6A2" else "#FFB45F") { openExactAlarmSettings() })
        root.addView(space(9))
        root.addView(settingCard("Test alarm", "Rings in about 10 seconds", "#6E7CFF") {
            ReminderScheduler.test(this)
            Toast.makeText(this, "Test alarm scheduled", Toast.LENGTH_SHORT).show()
        })
        root.addView(space(20))

        root.addView(sectionTitle("Profile"))
        root.addView(rowCard("◎", "Profile name", store.profileName(), "#6C5EF4") { changeName() })
        root.addView(space(9))
        root.addView(rowCard("⌁", "Change PIN", "Update your 4-digit access PIN", "#3E6EA8") { changePin() })
        root.addView(space(9))
        root.addView(rowCard("¤", "Currency", currency(), "#2F826E") { changeCurrency() })
        root.addView(space(20))

        root.addView(sectionTitle("Data"))
        root.addView(rowCard("⇧", "Share backup", "Export your Guide data as JSON", "#6C668E") { shareBackup() })
        root.addView(space(20))

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "2.0"
        root.addView(text("GUIDE • Version $version", 11f, "#5F6D95", bold = true).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f })
        return root
    }

    private fun buildDetailPage(type: String): LinearLayout = when (type) {
        "routines" -> routinesPage()
        "meals" -> mealsPage()
        "alarms" -> alarmsPage()
        "courses" -> coursesPage()
        "money" -> moneyPage()
        "attendance" -> attendancePage()
        else -> buildPlanPage()
    }

    private fun detailHeader(root: LinearLayout, titleText: String, subtitle: String, addLabel: String? = null, addAction: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(pillButton("‹", "#1A2747") { detailPage = null; render() }, LinearLayout.LayoutParams(dp(48), dp(44)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        labels.addView(text(titleText, 25f, "#FFFFFF", bold = true))
        labels.addView(text(subtitle, 12f, "#8290B8"))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (addLabel != null && addAction != null) row.addView(pillButton(addLabel, "#6A56F4", addAction), LinearLayout.LayoutParams(dp(78), dp(44)))
        root.addView(row)
        root.addView(space(20))
    }

    private fun routinesPage(): LinearLayout {
        val root = page()
        detailHeader(root, "Routines", "Tap a routine to manage it.", "+ Add") { addRoutine() }
        val items = store.routines().sortedWith(compareBy<RoutineItem> { it.hour }.thenBy { it.minute })
        if (items.isEmpty()) root.addView(emptyCard("No routines", "Create a routine and choose whether it should alert you."))
        items.forEachIndexed { i, item ->
            val done = item.doneDate == store.today()
            root.addView(itemCard(if (done) "✓" else "○", item.title, "${timeText(item.hour, item.minute)} • ${item.category} • ${if (item.alarmEnabled) "Alarm on" else "Alarm off"}", if (done) "#1B765D" else "#564AE0") { routineActions(item) })
            if (i < items.lastIndex) root.addView(space(9))
        }
        return root
    }

    private fun mealsPage(): LinearLayout {
        val root = page()
        detailHeader(root, "Meal plan", "Food routine with timed reminders.", "+ Add") { addMeal() }
        val items = store.meals().sortedWith(compareBy<MealItem> { it.hour }.thenBy { it.minute })
        if (items.isEmpty()) root.addView(emptyCard("No meal plans", "Add breakfast, lunch, dinner or any custom meal."))
        items.forEachIndexed { i, item ->
            val done = item.doneDate == store.today()
            root.addView(itemCard(if (done) "✓" else "🍽", item.title, "${timeText(item.hour, item.minute)} • ${if (item.alarmEnabled) "Alarm on" else "Alarm off"}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", if (done) "#1D765E" else "#198B77") { mealActions(item) })
            if (i < items.lastIndex) root.addView(space(9))
        }
        return root
    }

    private fun alarmsPage(): LinearLayout {
        val root = page()
        detailHeader(root, "Alarms", "Uses your phone's default alarm sound.", "+ Add") { addAlarm() }
        val items = store.alarms().sortedWith(compareBy<AlarmItem> { it.hour }.thenBy { it.minute })
        if (items.isEmpty()) root.addView(emptyCard("No custom alarms", "Add a wake-up, work, study or personal alarm."))
        items.forEachIndexed { i, item ->
            root.addView(itemCard(if (item.enabled) "⏰" else "○", item.title, "${timeText(item.hour, item.minute)} • ${if (item.enabled) "Active" else "Off"}", if (item.enabled) "#286F9C" else "#3B465F") { alarmActions(item) })
            if (i < items.lastIndex) root.addView(space(9))
        }
        root.addView(space(18))
        root.addView(settingCard("Test your alarm sound", "A test alert will fire in about 10 seconds", "#6E7CFF") {
            ReminderScheduler.test(this)
            Toast.makeText(this, "Test alarm scheduled", Toast.LENGTH_SHORT).show()
        })
        return root
    }

    private fun coursesPage(): LinearLayout {
        val root = page()
        detailHeader(root, "Courses", "Track learning progress and next steps.", "+ Add") { addCourse() }
        val items = store.courses()
        if (items.isEmpty()) root.addView(emptyCard("No courses", "Add a course, training plan or study target."))
        items.forEachIndexed { i, item ->
            val c = card("#17213E")
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(item.title, 17f, "#FFFFFF", bold = true))
            labels.addView(text(item.nextTask.ifBlank { "No next task set" }, 13f, "#8F9CC2"))
            header.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(statusPill("${item.progress}%", "#302B54", "#B9ADFF"))
            c.addView(header)
            c.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = item.progress
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7A5CFF"))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3B4565"))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(9)).apply { topMargin = dp(14) })
            if (item.dueDate.isNotBlank()) c.addView(text("Target: ${item.dueDate}", 12f, "#7786AF").apply { setPadding(0, dp(8), 0, 0) })
            c.setOnClickListener { courseActions(item) }
            root.addView(c)
            if (i < items.lastIndex) root.addView(space(9))
        }
        return root
    }

    private fun moneyPage(): LinearLayout {
        val root = page()
        detailHeader(root, "Accounts", "Track income and expenses.", "+ Add") { addMoneyRecord() }
        val summary = store.currentMonthMoneySummary()
        val hero = card("#17263F")
        hero.addView(text("Monthly balance", 12f, "#8B9AC0", bold = true))
        hero.addView(text(moneyText(summary.third), 28f, if (summary.third >= 0) "#69D0AA" else "#FF8990", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        hero.addView(text("Income ${moneyText(summary.first)}  •  Expense ${moneyText(summary.second)}", 13f, "#A0ADCD").apply { setPadding(0, dp(7), 0, 0) })
        root.addView(hero)
        root.addView(space(18))
        root.addView(sectionTitle("Transactions"))
        val items = store.moneyRecords()
        if (items.isEmpty()) root.addView(emptyCard("No transactions", "Add income or expense records to see your balance."))
        items.take(30).forEachIndexed { i, item ->
            val sign = if (item.type == "Income") "+" else "−"
            val color = if (item.type == "Income") "#61CBA7" else "#FF858D"
            val c = card("#151F3A")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(item.category, 16f, "#FFFFFF", bold = true))
            labels.addView(text("${item.date}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 12f, "#7F8DB5"))
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("$sign${moneyText(item.amount)}", 16f, color, bold = true))
            c.addView(row)
            c.setOnClickListener { moneyActions(item) }
            root.addView(c)
            if (i < items.take(30).lastIndex) root.addView(space(8))
        }
        return root
    }

    private fun attendancePage(): LinearLayout {
        val root = page()
        detailHeader(root, "Attendance", "Daily attendance history")
        val summary = store.attendanceSummaryForCurrentMonth()
        val stats = card("#182342")
        stats.addView(text("This month", 12f, "#8795BB", bold = true))
        stats.addView(text("P ${summary["Present"]}   •   A ${summary["Absent"]}   •   L ${summary["Leave"]}", 22f, "#FFFFFF", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        root.addView(stats)
        root.addView(space(18))
        store.attendanceHistory(31).forEachIndexed { i, pair ->
            val date = runCatching { LocalDate.parse(pair.first).format(DateTimeFormatter.ofPattern("EEE, dd MMM")) }.getOrDefault(pair.first)
            val c = simpleLine(date, pair.second)
            c.setOnClickListener { editAttendanceDate(pair.first, pair.second) }
            root.addView(c)
            if (i < 30) root.addView(space(7))
        }
        return root
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(9))
            background = rounded("#0E1730", 0)
            elevation = dp(14).toFloat()
        }
        val items = listOf(
            Triple("home", "⌂", "Home"),
            Triple("plan", "▤", "Plan"),
            Triple("track", "◉", "Track"),
            Triple("more", "•••", "More")
        )
        items.forEach { item ->
            val active = currentTab == item.first
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                background = if (active) rounded("#202B50", 14) else rounded("#0E1730", 14)
                setOnClickListener {
                    currentTab = item.first
                    detailPage = null
                    render()
                }
            }
            tab.addView(text(item.second, 19f, if (active) "#FFFFFF" else "#7180AA", bold = true).apply { gravity = Gravity.CENTER })
            tab.addView(text(item.third, 11f, if (active) "#B8C3E8" else "#65739B", bold = active).apply { gravity = Gravity.CENTER })
            nav.addView(tab, LinearLayout.LayoutParams(0, dp(62), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        return nav
    }

    private fun routineActions(item: RoutineItem) {
        val done = item.doneDate == store.today()
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(arrayOf(if (done) "Mark not done" else "Mark done", "Edit routine", if (item.alarmEnabled) "Turn alarm off" else "Turn alarm on", "Delete")) { _, which ->
                val items = store.routines()
                val index = items.indexOfFirst { it.id == item.id }
                if (index < 0) return@setItems
                when (which) {
                    0 -> items[index] = item.copy(doneDate = if (done) "" else store.today())
                    1 -> { addRoutine(item); return@setItems }
                    2 -> {
                        val updated = item.copy(alarmEnabled = !item.alarmEnabled)
                        items[index] = updated
                        if (updated.alarmEnabled) ReminderScheduler.scheduleDaily(this, "routine:${updated.id}", updated.title, "Routine • ${updated.category}", updated.hour, updated.minute)
                        else ReminderScheduler.cancel(this, "routine:${updated.id}")
                    }
                    3 -> {
                        ReminderScheduler.cancel(this, "routine:${item.id}")
                        items.removeAt(index)
                    }
                }
                store.saveRoutines(items); render()
            }.show()
    }

    private fun toggleRoutine(item: RoutineItem) {
        val items = store.routines()
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val done = item.doneDate == store.today()
        items[index] = item.copy(doneDate = if (done) "" else store.today())
        store.saveRoutines(items)
        render()
    }

    private fun addRoutine(existing: RoutineItem? = null) {
        val box = formBox()
        val titleInput = input("Routine title").apply { setText(existing?.title ?: "") }
        val categoryInput = input("Category").apply { setText(existing?.category ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 8; minute = existing?.minute ?: 0 }
        val alarmCheck = CheckBox(this).apply { text = "Daily alarm reminder"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: true }
        box.addView(titleInput); box.addView(space(8)); box.addView(categoryInput); box.addView(picker); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "New routine" else "Edit routine").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = titleInput.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val updated = RoutineItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, categoryInput.text.toString().trim().ifBlank { "Routine" }, existing?.doneDate ?: "", alarmCheck.isChecked)
                val items = store.routines()
                val index = items.indexOfFirst { it.id == updated.id }
                if (index >= 0) items[index] = updated else items.add(updated)
                store.saveRoutines(items)
                ReminderScheduler.cancel(this, "routine:${updated.id}")
                if (updated.alarmEnabled) ReminderScheduler.scheduleDaily(this, "routine:${updated.id}", updated.title, "Routine • ${updated.category}", updated.hour, updated.minute)
                render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun mealActions(item: MealItem) {
        val done = item.doneDate == store.today()
        AlertDialog.Builder(this).setTitle(item.title)
            .setItems(arrayOf(if (done) "Mark not eaten" else "Mark eaten", "Edit meal", if (item.alarmEnabled) "Turn alarm off" else "Turn alarm on", "Delete")) { _, which ->
                val items = store.meals(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
                when (which) {
                    0 -> items[index] = item.copy(doneDate = if (done) "" else store.today())
                    1 -> { addMeal(item); return@setItems }
                    2 -> {
                        val updated = item.copy(alarmEnabled = !item.alarmEnabled); items[index] = updated
                        if (updated.alarmEnabled) ReminderScheduler.scheduleDaily(this, "meal:${updated.id}", updated.title, updated.note.ifBlank { "Meal time" }, updated.hour, updated.minute)
                        else ReminderScheduler.cancel(this, "meal:${updated.id}")
                    }
                    3 -> { ReminderScheduler.cancel(this, "meal:${item.id}"); items.removeAt(index) }
                }
                store.saveMeals(items); render()
            }.show()
    }

    private fun addMeal(existing: MealItem? = null) {
        val box = formBox()
        val titleInput = input("Meal name").apply { setText(existing?.title ?: "") }
        val noteInput = input("Food note / plan").apply { setText(existing?.note ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 13; minute = existing?.minute ?: 0 }
        val alarmCheck = CheckBox(this).apply { text = "Meal alarm reminder"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: true }
        box.addView(titleInput); box.addView(space(8)); box.addView(noteInput); box.addView(picker); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "New meal" else "Edit meal").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
                val updated = MealItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, noteInput.text.toString().trim(), existing?.doneDate ?: "", alarmCheck.isChecked)
                val items = store.meals(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
                store.saveMeals(items); ReminderScheduler.cancel(this, "meal:${updated.id}")
                if (updated.alarmEnabled) ReminderScheduler.scheduleDaily(this, "meal:${updated.id}", updated.title, updated.note.ifBlank { "Meal time" }, updated.hour, updated.minute)
                render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun alarmActions(item: AlarmItem) {
        AlertDialog.Builder(this).setTitle(item.title)
            .setItems(arrayOf(if (item.enabled) "Turn off" else "Turn on", "Edit alarm", "Delete")) { _, which ->
                val items = store.alarms(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
                when (which) {
                    0 -> {
                        val updated = item.copy(enabled = !item.enabled); items[index] = updated
                        if (updated.enabled) ReminderScheduler.scheduleDaily(this, "alarm:${updated.id}", updated.title, "Guide alarm", updated.hour, updated.minute)
                        else ReminderScheduler.cancel(this, "alarm:${updated.id}")
                    }
                    1 -> { addAlarm(item); return@setItems }
                    2 -> { ReminderScheduler.cancel(this, "alarm:${item.id}"); items.removeAt(index) }
                }
                store.saveAlarms(items); render()
            }.show()
    }

    private fun addAlarm(existing: AlarmItem? = null) {
        val box = formBox()
        val titleInput = input("Alarm label").apply { setText(existing?.title ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 7; minute = existing?.minute ?: 0 }
        val enabledCheck = CheckBox(this).apply { text = "Alarm enabled"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: true }
        box.addView(titleInput); box.addView(picker); box.addView(enabledCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "New alarm" else "Edit alarm").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = titleInput.text.toString().trim().ifBlank { "Alarm" }
                val updated = AlarmItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, enabledCheck.isChecked)
                val items = store.alarms(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
                store.saveAlarms(items); ReminderScheduler.cancel(this, "alarm:${updated.id}")
                if (updated.enabled) ReminderScheduler.scheduleDaily(this, "alarm:${updated.id}", updated.title, "Guide alarm", updated.hour, updated.minute)
                render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun courseActions(item: CourseItem) {
        AlertDialog.Builder(this).setTitle(item.title)
            .setItems(arrayOf("Update course", "Add 10% progress", "Delete")) { _, which ->
                val items = store.courses(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
                when (which) {
                    0 -> { addCourse(item); return@setItems }
                    1 -> items[index] = item.copy(progress = (item.progress + 10).coerceAtMost(100))
                    2 -> items.removeAt(index)
                }
                store.saveCourses(items); render()
            }.show()
    }

    private fun addCourse(existing: CourseItem? = null) {
        val box = formBox()
        val titleInput = input("Course / skill name").apply { setText(existing?.title ?: "") }
        val nextInput = input("Next lesson or task").apply { setText(existing?.nextTask ?: "") }
        val dueInput = input("Target date (YYYY-MM-DD)").apply { setText(existing?.dueDate ?: "") }
        val progressInput = input("Progress 0-100").apply { inputType = InputType.TYPE_CLASS_NUMBER; setText((existing?.progress ?: 0).toString()) }
        box.addView(titleInput); box.addView(space(8)); box.addView(nextInput); box.addView(space(8)); box.addView(dueInput); box.addView(space(8)); box.addView(progressInput)
        AlertDialog.Builder(this).setTitle(if (existing == null) "New course" else "Update course").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
                val progress = progressInput.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 0
                val updated = CourseItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, progress, nextInput.text.toString().trim(), dueInput.text.toString().trim())
                val items = store.courses(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
                store.saveCourses(items); render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun addMoneyRecord(existing: MoneyRecord? = null) {
        val box = formBox()
        val typeSpinner = Spinner(this)
        val types = listOf("Expense", "Income")
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        if (existing?.type == "Income") typeSpinner.setSelection(1)
        val amountInput = input("Amount").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; if (existing != null) setText(existing.amount.toString()) }
        val categoryInput = input("Category").apply { setText(existing?.category ?: "") }
        val noteInput = input("Note").apply { setText(existing?.note ?: "") }
        val dateInput = input("Date (YYYY-MM-DD)").apply { setText(existing?.date ?: store.today()) }
        box.addView(typeSpinner); box.addView(space(8)); box.addView(amountInput); box.addView(space(8)); box.addView(categoryInput); box.addView(space(8)); box.addView(noteInput); box.addView(space(8)); box.addView(dateInput)
        AlertDialog.Builder(this).setTitle(if (existing == null) "Add transaction" else "Edit transaction").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val updated = MoneyRecord(existing?.id ?: java.util.UUID.randomUUID().toString(), types[typeSpinner.selectedItemPosition], amount, categoryInput.text.toString().trim().ifBlank { "General" }, noteInput.text.toString().trim(), dateInput.text.toString().trim().ifBlank { store.today() })
                val items = store.moneyRecords(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
                store.saveMoneyRecords(items); render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun moneyActions(item: MoneyRecord) {
        AlertDialog.Builder(this).setTitle(item.category).setItems(arrayOf("Edit", "Delete")) { _, which ->
            if (which == 0) addMoneyRecord(item) else {
                val items = store.moneyRecords(); items.removeAll { it.id == item.id }; store.saveMoneyRecords(items); render()
            }
        }.show()
    }

    private fun editAttendanceDate(date: String, current: String) {
        AlertDialog.Builder(this).setTitle(date).setSingleChoiceItems(arrayOf("Present", "Absent", "Leave", "Not marked"), arrayOf("Present", "Absent", "Leave", "Not marked").indexOf(current).coerceAtLeast(0)) { dialog, which ->
            val status = arrayOf("Present", "Absent", "Leave", "Not marked")[which]
            store.setAttendance(status, date)
            dialog.dismiss(); render()
        }.show()
    }

    private fun waterCard(): LinearLayout {
        val count = store.waterCount()
        val c = card("#15213C")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text("💧", 28f, "#75CFFF").apply { gravity = Gravity.CENTER; background = rounded("#16384E", 16) }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(10), 0) }
        labels.addView(text("Water goal", 17f, "#FFFFFF", bold = true))
        labels.addView(text("$count / 8 glasses today", 13f, "#8C9AC1"))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(smallAction("−", "#263456") { store.removeWater(); render() }, LinearLayout.LayoutParams(dp(48), dp(46)))
        row.addView(hSpace(8))
        row.addView(smallAction("+", "#2C7AA0") { store.addWater(); render() }, LinearLayout.LayoutParams(dp(48), dp(46)))
        c.addView(row)
        return c
    }

    private fun pageHeader(root: LinearLayout, titleText: String, subtitle: String) {
        root.addView(text(titleText, 29f, "#FFFFFF", bold = true))
        root.addView(text(subtitle, 14f, "#8C9BC3").apply { setPadding(0, dp(4), 0, dp(22)) })
    }

    private fun rowCard(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(icon, 25f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(accent, 16) }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0) }
        labels.addView(text(titleText, 17f, "#FFFFFF", bold = true))
        labels.addView(text(subtitle, 13f, "#8997BF"))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("›", 30f, "#65749E"))
        c.addView(row); c.setOnClickListener { onClick() }
        return c
    }

    private fun itemCard(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout = rowCard(icon, titleText, subtitle, accent, onClick)

    private fun compactAction(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E").apply { gravity = Gravity.CENTER_VERTICAL }
        c.addView(text(icon, 25f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(accent, 14) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        c.addView(text(titleText, 15f, "#FFFFFF", bold = true).apply { setPadding(0, dp(8), 0, 0) })
        c.addView(text(subtitle, 11f, "#8190B8"))
        c.setOnClickListener { onClick() }
        return c
    }

    private fun simpleLine(left: String, right: String): LinearLayout {
        val c = card("#141E39", padding = 14)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(left, 14f, "#FFFFFF", bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text(right, 13f, "#8D9AC0"))
        c.addView(row)
        return c
    }

    private fun settingCard(titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#151F3B", padding = 15)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(titleText, 16f, "#FFFFFF", bold = true)); labels.addView(text(subtitle, 12f, "#8593BA"))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("●", 15f, accent))
        c.addView(row); c.setOnClickListener { onClick() }
        return c
    }

    private fun emptyCard(titleText: String, subtitle: String): LinearLayout {
        val c = card("#141E38")
        c.addView(text(titleText, 16f, "#FFFFFF", bold = true))
        c.addView(text(subtitle, 13f, "#7F8DB5").apply { setPadding(0, dp(5), 0, 0) })
        return c
    }

    private fun sectionTitle(value: String) = text(value, 13f, "#A8B4D7", bold = true).apply { setPadding(dp(2), 0, 0, dp(9)); letterSpacing = 0.04f }

    private fun statusPill(value: String, bg: String, fg: String) = text(value, 12f, fg, bold = true).apply {
        gravity = Gravity.CENTER; background = rounded(bg, 14); setPadding(dp(11), dp(7), dp(11), dp(7))
    }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(Color.parseColor(color)); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun pillButton(label: String, bg: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(bg, 13); setOnClickListener { action() }
    }

    private fun smallAction(label: String, bg: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(bg, 12); setOnClickListener { action() }
    }

    private fun card(hex: String, padding: Int = 17) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); background = rounded(hex, 20); elevation = dp(2).toFloat()
    }

    private fun formBox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(4)) }

    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText; setSingleLine(true); setPadding(dp(14), dp(10), dp(14), dp(10)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply { setColor(Color.parseColor(hex)); cornerRadius = dp(radiusDp).toFloat() }

    private fun gradient(top: String, bottom: String) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor(top), Color.parseColor(bottom)))

    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun hSpace(width: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }

    private fun startClock() {
        fun tick() {
            clockView?.text = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
            handler.postDelayed({ tick() }, 1000)
        }
        tick()
    }

    private fun greetingText(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    private fun nextReminderText(): String {
        val now = LocalDateTime.now()
        val entries = mutableListOf<Pair<LocalDateTime, String>>()
        store.routines().filter { it.alarmEnabled }.forEach { entries += nextTime(it.hour, it.minute) to it.title }
        store.meals().filter { it.alarmEnabled }.forEach { entries += nextTime(it.hour, it.minute) to it.title }
        store.alarms().filter { it.enabled }.forEach { entries += nextTime(it.hour, it.minute) to it.title }
        val next = entries.minByOrNull { it.first } ?: return "No upcoming reminders"
        val day = if (next.first.toLocalDate() == now.toLocalDate()) "Today" else "Tomorrow"
        return "$day • ${next.second} • ${next.first.format(DateTimeFormatter.ofPattern("hh:mm a"))}"
    }

    private fun nextTime(hour: Int, minute: Int): LocalDateTime {
        var next = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        return next
    }

    private fun timeText(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("hh:mm a"))

    private fun editFocusNote(current: String) {
        val input = input("Today's main focus").apply { setText(current) }
        AlertDialog.Builder(this).setTitle("Focus note").setView(input).setPositiveButton("Save") { _, _ ->
            uiPrefs.edit().putString("focus_${store.today()}", input.text.toString().trim()).apply(); render()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun changeName() {
        val input = input("Profile name").apply { setText(store.profileName()) }
        AlertDialog.Builder(this).setTitle("Profile name").setView(input).setPositiveButton("Save") { _, _ -> store.setProfileName(input.text.toString()); render() }.setNegativeButton("Cancel", null).show()
    }

    private fun changePin() {
        val box = formBox()
        val oldPin = input("Current PIN").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val newPin = input("New 4-digit PIN").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        box.addView(oldPin); box.addView(space(8)); box.addView(newPin)
        AlertDialog.Builder(this).setTitle("Change PIN").setView(box).setPositiveButton("Update") { _, _ ->
            val newValue = newPin.text.toString()
            if (newValue.length != 4 || newValue.any { !it.isDigit() }) Toast.makeText(this, "Use a 4-digit PIN", Toast.LENGTH_SHORT).show()
            else if (!store.changePin(oldPin.text.toString(), newValue)) Toast.makeText(this, "Current PIN is incorrect", Toast.LENGTH_SHORT).show()
            else Toast.makeText(this, "PIN updated", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun currency(): String = uiPrefs.getString("currency", "SAR") ?: "SAR"

    private fun changeCurrency() {
        val input = input("Currency code, e.g. SAR, USD").apply { setText(currency()) }
        AlertDialog.Builder(this).setTitle("Currency").setView(input).setPositiveButton("Save") { _, _ ->
            val value = input.text.toString().trim().uppercase(Locale.getDefault()).take(5).ifBlank { "SAR" }
            uiPrefs.edit().putString("currency", value).apply(); render()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun moneyText(amount: Double): String = "${currency()} ${String.format(Locale.getDefault(), "%.2f", amount)}"

    private fun shareBackup() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Guide backup")
            putExtra(Intent.EXTRA_TEXT, store.exportJson())
        }
        startActivity(Intent.createChooser(send, "Share Guide backup"))
    }

    private fun lockApp() {
        startActivity(Intent(this, LoginActivity::class.java)); finish()
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 210)
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 210)
            return
        }
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, packageName) })
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "Precise alarms are supported", Toast.LENGTH_SHORT).show(); return
        }
        if (ReminderScheduler.exactAlarmAvailable(this)) {
            Toast.makeText(this, "Precise alarms are enabled", Toast.LENGTH_SHORT).show(); return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
