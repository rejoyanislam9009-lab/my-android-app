package com.guide.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.RingtoneManager
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
    private var drawerOverlay: View? = null
    private var drawerPanel: LinearLayout? = null
    private var lastBackPressedAt = 0L
    private val alarmCountdownViews = mutableListOf<Pair<TextView, AlarmItem>>()
    private var pendingRingtoneResult: ((String) -> Unit)? = null

    private val ringtonePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (picked != null) pendingRingtoneResult?.invoke(picked.toString())
        }
        pendingRingtoneResult = null
    }

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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleSystemBack()
        })
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 441 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            useCurrentPrayerLocation()
        }
    }

    private fun render() {
        handler.removeCallbacksAndMessages(null)
        alarmCountdownViews.clear()
        setContentView(buildShell())
        if (currentTab == "home" && detailPage == null) startClock()
        if (detailPage == "alarms") startAlarmCountdowns()
    }

    private fun buildShell(): View {
        val frame = FrameLayout(this).apply { background = gradient("#080D1A", "#111A35") }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient("#080D1A", "#111A35")
        }
        shell.addView(buildTopBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))

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
        frame.addView(shell, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val overlay = View(this).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { closeDrawer() }
        }
        drawerOverlay = overlay
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val drawer = buildSidebar().apply {
            visibility = View.GONE
            elevation = dp(24).toFloat()
        }
        drawerPanel = drawer
        frame.addView(drawer, FrameLayout.LayoutParams(dp(302), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        return frame
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(14), dp(9))
            background = rounded("#0D1630", 0)
            elevation = dp(8).toFloat()
        }
        bar.addView(pillButton("☰", "#1C294A") { openDrawer() }, LinearLayout.LayoutParams(dp(48), dp(46)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        labels.addView(text("GUIDE", 11f, "#7C8BB6", bold = true).apply { letterSpacing = 0.13f })
        labels.addView(text(screenTitle(), 17f, "#FFFFFF", bold = true))
        bar.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val initial = store.profileName().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
        bar.addView(text(initial, 16f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded("#6A56F4", 15)
            setOnClickListener { openDrawer() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        return bar
    }

    private fun screenTitle(): String = detailPage?.let {
        when (it) {
            "routines" -> "রুটিন"
            "meals" -> "খাবারের রুটিন"
            "alarms" -> "অ্যালার্ম"
            "courses" -> "কোর্স"
            "money" -> "হিসাব"
            "attendance" -> "হাজিরা"
            "prayer" -> "নামাজের সময়সূচি"
            else -> "Guide"
        }
    } ?: when (currentTab) {
        "plan" -> "দৈনিক পরিকল্পনা"
        "track" -> "ট্র্যাকিং"
        "more" -> "সেটিংস"
        else -> "ড্যাশবোর্ড"
    }

    private fun buildSidebar(): LinearLayout {
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(14))
            background = gradient("#121C38", "#0B1227")
        }

        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(text("G", 25f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded("#7257FF", 20)
        }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(13), 0, 0, 0) }
        brandText.addView(text("Guide", 22f, "#FFFFFF", bold = true))
        brandText.addView(text(store.profileName(), 12f, "#8E9BC3"))
        brand.addView(brandText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        drawer.addView(brand)
        drawer.addView(space(14))

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        menu.addView(drawerSection("মূল মেনু"))
        menu.addView(drawerItem("⌂", "ড্যাশবোর্ড", currentTab == "home" && detailPage == null) { navigate("home") })
        menu.addView(drawerItem("▤", "দৈনিক পরিকল্পনা", currentTab == "plan" && detailPage == null) { navigate("plan") })
        menu.addView(space(10))

        menu.addView(drawerSection("পরিকল্পনা"))
        menu.addView(drawerItem("✓", "রুটিন", detailPage == "routines") { navigate("plan", "routines") })
        menu.addView(drawerItem("🍽", "খাবারের রুটিন", detailPage == "meals") { navigate("plan", "meals") })
        menu.addView(drawerItem("⏰", "অ্যালার্ম", detailPage == "alarms") { navigate("plan", "alarms") })
        menu.addView(drawerItem("☪", "নামাজের সময়সূচি", detailPage == "prayer") { navigate("plan", "prayer") })
        menu.addView(drawerItem("▤", "কোর্স", detailPage == "courses") { navigate("plan", "courses") })
        menu.addView(space(10))

        menu.addView(drawerSection("হিসাব ও ট্র্যাকিং"))
        menu.addView(drawerItem("◎", "হাজিরা", detailPage == "attendance") { navigate("track", "attendance") })
        menu.addView(drawerItem("▣", "হিসাব", detailPage == "money") { navigate("track", "money") })
        menu.addView(drawerItem("◉", "ট্র্যাকিং সারাংশ", currentTab == "track" && detailPage == null) { navigate("track") })
        menu.addView(space(10))

        menu.addView(drawerSection("অ্যাকাউন্ট"))
        menu.addView(drawerItem("⚙", "সেটিংস", currentTab == "more" && detailPage == null) { navigate("more") })

        drawer.addView(ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            addView(menu)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        drawer.addView(space(8))

        val lockCard = card("#1A2444", padding = 12).apply {
            background = roundedStroke("#1A2444", "#70FFFFFF", 1, 16)
        }
        val lockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lockRow.addView(text("⌁", 21f, "#FFB5B9", bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val lockLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        lockLabels.addView(text("Guide লক করুন", 15f, "#FFFFFF", bold = true))
        lockLabels.addView(text("আবার PIN লাগবে", 11f, "#7F8EB7"))
        lockRow.addView(lockLabels)
        lockCard.addView(lockRow)
        lockCard.setOnClickListener { lockApp() }
        drawer.addView(lockCard)
        return drawer
    }

    private fun drawerSection(label: String) = text(label, 11f, "#7181AB", bold = true).apply {
        setPadding(dp(8), dp(4), 0, dp(7))
    }

    private fun drawerItem(icon: String, label: String, active: Boolean, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = roundedStroke(if (active) "#26335B" else "#121C38", "#72FFFFFF", 1, 15)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5); bottomMargin = dp(5)
            }
            setOnClickListener { action() }
            addView(text(icon, 18f, if (active) "#C6BCFF" else "#9CA9CF", bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(text(label, 14f, if (active) "#FFFFFF" else "#C5CDEA", bold = active).apply { setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (active) addView(text("•", 18f, "#8D79FF", bold = true))
        }
    }

    private fun openDrawer() {
        val panel = drawerPanel ?: return
        val overlay = drawerOverlay ?: return
        overlay.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        panel.translationX = -dp(302).toFloat()
        panel.animate().translationX(0f).setDuration(190).start()
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(170).start()
    }

    private fun closeDrawer(immediate: Boolean = false) {
        val panel = drawerPanel ?: return
        val overlay = drawerOverlay ?: return
        if (panel.visibility != View.VISIBLE) return
        if (immediate) {
            panel.visibility = View.GONE; overlay.visibility = View.GONE; panel.translationX = 0f; overlay.alpha = 1f
            return
        }
        panel.animate().translationX(-dp(302).toFloat()).setDuration(170).withEndAction {
            panel.visibility = View.GONE; panel.translationX = 0f
        }.start()
        overlay.animate().alpha(0f).setDuration(150).withEndAction {
            overlay.visibility = View.GONE; overlay.alpha = 1f
        }.start()
    }

    private fun navigate(tab: String, detail: String? = null) {
        currentTab = tab
        detailPage = detail
        closeDrawer(true)
        render()
    }

    private fun handleSystemBack() {
        if (drawerPanel?.visibility == View.VISIBLE) { closeDrawer(); return }
        if (detailPage != null) { detailPage = null; render(); return }
        if (currentTab != "home") { currentTab = "home"; render(); return }
        val now = System.currentTimeMillis()
        if (now - lastBackPressedAt <= 1800L) finish()
        else {
            lastBackPressedAt = now
            Toast.makeText(this, "Guide বন্ধ করতে আবার Back চাপুন", Toast.LENGTH_SHORT).show()
        }
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
            max = 100; this.progress = progress
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
        if (routines.isEmpty()) root.addView(emptyCard("No routines yet", "Add your first routine from Plan."))
        else routines.sortedWith(compareBy<RoutineItem> { it.hour }.thenBy { it.minute }).take(3).forEachIndexed { index, item ->
            val doneNow = item.doneDate == today
            root.addView(itemCard(if (doneNow) "✓" else "○", item.title, "${timeText(item.hour, item.minute)} • ${item.category}", if (doneNow) "#1B6D58" else "#3D4C7D") { toggleRoutine(item) })
            if (index < minOf(2, routines.size - 1)) root.addView(space(9))
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
        noteCard.addView(text(if (note.isBlank()) "Tap to set your main focus for today." else "“$note”", 15f, if (note.isBlank()) "#8795C1" else "#FFFFFF"))
        noteCard.setOnClickListener { editFocusNote(note) }
        root.addView(noteCard)
        return root
    }

    private fun buildPlanPage(): LinearLayout {
        val root = page()
        pageHeader(root, "Plan", "Build a schedule that actually works.")
        val routines = store.routines(); val meals = store.meals(); val alarms = store.alarms(); val courses = store.courses()
        root.addView(sectionTitle("Planning tools"))
        root.addView(rowCard("✓", "Routines", "${routines.size} routines • ${routines.count { it.alarmEnabled }} reminders on", "#6759F5") { detailPage = "routines"; render() })
        root.addView(space(10))
        root.addView(rowCard("🍽", "Meals", "${meals.size} meal plans • ${meals.count { it.alarmEnabled }} reminders on", "#1E9B83") { detailPage = "meals"; render() })
        root.addView(space(10))
        root.addView(rowCard("⏰", "Alarms", "${alarms.count { it.enabled }} active custom alarms", "#3179B5") { detailPage = "alarms"; render() })
        root.addView(space(10))
        val prayer = store.prayerSettings()
        root.addView(rowCard("☪", "নামাজের সময়", if (prayer.enabled && prayer.hasLocation()) "আজান অ্যালার্ম চালু" else "সেটআপ করুন", "#247B67") { detailPage = "prayer"; render() })
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
        val todayRecord = store.attendanceRecord()
        root.addView(sectionTitle("Attendance"))
        val attendanceCard = card("#182342")
        attendanceCard.addView(text("Today: ${todayRecord.status}", 18f, "#FFFFFF", bold = true))
        val savedMeta = if (todayRecord.time.isBlank()) "Tap a button to save today's attendance" else "${friendlyDate(todayRecord.date)} • ${todayRecord.time}"
        attendanceCard.addView(text(savedMeta, 12f, "#8E9CC5").apply { setPadding(0, dp(4), 0, dp(6)) })
        attendanceCard.addView(text("Present ${summary["Present"]}  •  Absent ${summary["Absent"]}  •  Leave ${summary["Leave"]}", 13f, "#A2AED0").apply { setPadding(0, dp(3), 0, dp(14)) })
        val markRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        markRow.addView(smallAction("Present", "#237B64") { markAttendance("Present") }, LinearLayout.LayoutParams(0, dp(44), 1f))
        markRow.addView(hSpace(8)); markRow.addView(smallAction("Absent", "#A14E58") { markAttendance("Absent") }, LinearLayout.LayoutParams(0, dp(44), 1f))
        markRow.addView(hSpace(8)); markRow.addView(smallAction("Leave", "#9B7331") { markAttendance("Leave") }, LinearLayout.LayoutParams(0, dp(44), 1f))
        attendanceCard.addView(markRow)
        attendanceCard.setOnClickListener { detailPage = "attendance"; render() }
        root.addView(attendanceCard)
        root.addView(space(20))

        val money = store.currentMonthMoneySummary()
        root.addView(sectionTitle("Accounts"))
        val accountCard = card("#17243B")
        accountCard.addView(text("Balance ${moneyText(money.third)}", 21f, if (money.third >= 0) "#70D0AE" else "#FF8B93", bold = true))
        accountCard.addView(text("Income ${moneyText(money.first)}  •  Expense ${moneyText(money.second)}", 13f, "#94A3C7").apply { setPadding(0, dp(6), 0, dp(14)) })
        accountCard.addView(pillButton("Open accounts", "#245F58") { detailPage = "money"; render() })
        root.addView(accountCard)
        root.addView(space(20))

        root.addView(sectionTitle("Hydration")); root.addView(waterCard()); root.addView(space(20))
        root.addView(sectionTitle("Saved attendance"))
        val recent = store.markedAttendanceHistory(30).take(7)
        if (recent.isEmpty()) root.addView(emptyCard("No attendance saved", "Press Present, Absent or Leave to create your first record."))
        else recent.forEachIndexed { i, record ->
            root.addView(attendanceRecordCard(record) { detailPage = "attendance"; render() })
            if (i < recent.lastIndex) root.addView(space(8))
        }
        return root
    }

    private fun buildMorePage(): LinearLayout {
        val root = page()
        pageHeader(root, "Settings", "Profile, reminder settings and backup.")
        root.addView(sectionTitle("Reminder system"))
        val exact = ReminderScheduler.exactAlarmAvailable(this)
        val notificationOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        root.addView(settingCard("Notifications", if (notificationOk) "Allowed" else "Permission needed", if (notificationOk) "#54C6A2" else "#FF9B6A") { openNotificationSettings() })
        root.addView(space(9))
        root.addView(settingCard("Precise alarms", if (exact) "Enabled" else "Allow exact timing", if (exact) "#54C6A2" else "#FFB45F") { openExactAlarmSettings() })
        root.addView(space(9))
        root.addView(settingCard("Test alarm", "Rings in about 10 seconds", "#6E7CFF") {
            ReminderScheduler.test(this); Toast.makeText(this, "Test alarm scheduled", Toast.LENGTH_SHORT).show()
        })
        root.addView(space(20))
        root.addView(sectionTitle("Profile"))
        root.addView(rowCard("◎", "Profile name", store.profileName(), "#6C5EF4") { changeName() })
        root.addView(space(9)); root.addView(rowCard("⌁", "Change PIN", "Update your 4-digit access PIN", "#3E6EA8") { changePin() })
        root.addView(space(9)); root.addView(rowCard("¤", "Currency", currency(), "#2F826E") { changeCurrency() })
        root.addView(space(20))
        root.addView(sectionTitle("Data")); root.addView(rowCard("⇧", "Share backup", "Export your Guide data as JSON", "#6C668E") { shareBackup() })
        root.addView(space(20))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "2.4"
        root.addView(text("GUIDE • Version $version", 11f, "#5F6D95", bold = true).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f })
        return root
    }

    private fun buildDetailPage(type: String): LinearLayout = when (type) {
        "routines" -> routinesPage()
        "meals" -> mealsPage()
        "alarms" -> alarmsPage()
        "prayer" -> prayerPage()
        "courses" -> coursesPage()
        "money" -> moneyPage()
        "attendance" -> attendancePage()
        else -> buildPlanPage()
    }

    private fun detailHeader(root: LinearLayout, titleText: String, subtitle: String, addLabel: String? = null, addAction: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(pillButton("‹", "#1A2747") { detailPage = null; render() }, LinearLayout.LayoutParams(dp(48), dp(44)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        labels.addView(text(titleText, 25f, "#FFFFFF", bold = true)); labels.addView(text(subtitle, 12f, "#8290B8"))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (addLabel != null && addAction != null) row.addView(pillButton(addLabel, "#6A56F4", addAction), LinearLayout.LayoutParams(dp(78), dp(44)))
        root.addView(row); root.addView(space(20))
    }

    private fun routinesPage(): LinearLayout {
        val root = page(); detailHeader(root, "Routines", "Tap a routine to manage it.", "+ Add") { addRoutine() }
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
        val root = page(); detailHeader(root, "Meal plan", "Food routine with timed reminders.", "+ Add") { addMeal() }
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
        detailHeader(root, "অ্যালার্ম", "সময়, রিংটোন ও ভাইব্রেশন নিজের মতো সেট করুন।", "+ যোগ") { addAlarm() }
        val items = store.alarms().sortedWith(compareBy<AlarmItem> { it.hour }.thenBy { it.minute })
        if (items.isEmpty()) root.addView(emptyCard("কোনো অ্যালার্ম নেই", "নতুন অ্যালার্ম যোগ করুন।"))
        items.forEachIndexed { i, item ->
            root.addView(alarmCard(item) { alarmActions(item) })
            if (i < items.lastIndex) root.addView(space(10))
        }
        root.addView(space(18))
        root.addView(settingCard("অ্যালার্ম পরীক্ষা করুন", "প্রায় ১০ সেকেন্ড পর বাজবে", "#6E7CFF") {
            ReminderScheduler.test(this); Toast.makeText(this, "টেস্ট অ্যালার্ম সেট হয়েছে", Toast.LENGTH_SHORT).show()
        })
        return root
    }

    private fun prayerPage(): LinearLayout {
        val root = page()
        detailHeader(root, "নামাজের সময়সূচি", "লোকেশন অনুযায়ী অফলাইনে সময় হিসাব ও আজান অ্যালার্ম।")
        val settings = store.prayerSettings()

        val setup = card("#17213E")
        val master = CheckBox(this).apply {
            text = "নামাজের আজান অ্যালার্ম চালু"
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = settings.enabled
            setOnCheckedChangeListener { _, checked ->
                store.setPrayerEnabled(checked)
                ReminderScheduler.scheduleAll(this@MainActivity, store)
                render()
            }
        }
        setup.addView(master)
        setup.addView(text(
            if (settings.hasLocation()) "লোকেশন: %.5f, %.5f".format(Locale.US, settings.latitude, settings.longitude) else "এখনও লোকেশন সেট করা হয়নি",
            12f, "#8FA0C8"
        ).apply { setPadding(0, dp(5), 0, dp(12)) })
        val locRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        locRow.addView(smallAction("বর্তমান লোকেশন", "#286E73") { useCurrentPrayerLocation() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        locRow.addView(hSpace(8))
        locRow.addView(smallAction("হাতে লিখুন", "#3A4D79") { editPrayerLocation() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        setup.addView(locRow)
        root.addView(setup)
        root.addView(space(16))

        root.addView(sectionTitle("আজকের নামাজের সময়"))
        if (!settings.hasLocation()) {
            root.addView(emptyCard("লোকেশন প্রয়োজন", "বর্তমান লোকেশন ব্যবহার করুন অথবা latitude/longitude লিখুন।"))
        } else {
            val times = PrayerTimeCalculator.calculate(LocalDate.now(), settings.latitude, settings.longitude, ZoneId.systemDefault())
            times.forEachIndexed { index, prayer ->
                val isPrayer = prayer.key != "Sunrise"
                val enabled = settings.enabledPrayers.contains(prayer.key)
                val subtitle = if (!isPrayer) "শুধু তথ্য" else if (enabled) "আজান অ্যালার্ম চালু" else "আজান অ্যালার্ম বন্ধ"
                val card = settingCard("${prayer.nameBn} • ${prayer.time.format(DateTimeFormatter.ofPattern("hh:mm a"))}", subtitle, if (enabled) "#55C6A2" else "#7C89AC") {
                    if (isPrayer) {
                        store.setPrayerAlarmEnabled(prayer.key, !enabled)
                        ReminderScheduler.scheduleAll(this, store)
                        render()
                    }
                }
                root.addView(card)
                if (index < times.lastIndex) root.addView(space(8))
            }
        }
        root.addView(space(18))

        root.addView(sectionTitle("আজান সাউন্ড"))
        val soundCard = card("#17213E")
        val soundTitle = text("${if (settings.azanUri.isBlank()) "আজান সাউন্ড নির্বাচন করুন" else ringtoneTitle(settings.azanUri)}", 15f, "#FFFFFF", bold = true)
        soundCard.addView(soundTitle)
        soundCard.addView(text("ফোনে থাকা আজান/অডিও রিংটোন থেকে বেছে নিন।", 12f, "#8897BE").apply { setPadding(0, dp(4), 0, dp(10)) })
        soundCard.addView(pillButton("ফোন থেকে আজান সাউন্ড বাছাই", "#5B4AD6") {
            pickRingtone(settings.azanUri) { uri ->
                store.setPrayerAzanUri(uri)
                ReminderScheduler.scheduleAll(this, store)
                render()
            }
        })
        val vibrate = CheckBox(this).apply {
            text = "আজানের সাথে ভাইব্রেশন হবে"
            setTextColor(Color.WHITE)
            isChecked = settings.vibrateEnabled
            setOnCheckedChangeListener { _, checked ->
                store.setPrayerVibrate(checked)
                ReminderScheduler.scheduleAll(this@MainActivity, store)
            }
        }
        soundCard.addView(vibrate)
        root.addView(soundCard)
        root.addView(space(14))
        root.addView(text("নোট: সময় অফলাইন সৌর হিসাব (Umm al-Qura style) দিয়ে তৈরি হয়; স্থানীয় মসজিদের ঘোষিত সময়ের সাথে কয়েক মিনিট পার্থক্য হতে পারে।", 11f, "#7281A8"))
        return root
    }

    private fun coursesPage(): LinearLayout {
        val root = page(); detailHeader(root, "Courses", "Track learning progress and next steps.", "+ Add") { addCourse() }
        val items = store.courses()
        if (items.isEmpty()) root.addView(emptyCard("No courses", "Add a course, training plan or study target."))
        items.forEachIndexed { i, item ->
            val c = card("#17213E")
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(item.title, 17f, "#FFFFFF", bold = true)); labels.addView(text(item.nextTask.ifBlank { "No next task set" }, 13f, "#8F9CC2"))
            header.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); header.addView(statusPill("${item.progress}%", "#302B54", "#B9ADFF"))
            c.addView(header)
            c.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = item.progress
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7A5CFF")); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3B4565"))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(9)).apply { topMargin = dp(14) })
            if (item.dueDate.isNotBlank()) c.addView(text("Target: ${item.dueDate}", 12f, "#7786AF").apply { setPadding(0, dp(8), 0, 0) })
            c.setOnClickListener { courseActions(item) }; root.addView(c); if (i < items.lastIndex) root.addView(space(9))
        }
        return root
    }

    private fun moneyPage(): LinearLayout {
        val root = page(); detailHeader(root, "Accounts", "Track income and expenses.", "+ Add") { addMoneyRecord() }
        val summary = store.currentMonthMoneySummary(); val hero = card("#17263F")
        hero.addView(text("Monthly balance", 12f, "#8B9AC0", bold = true)); hero.addView(text(moneyText(summary.third), 28f, if (summary.third >= 0) "#69D0AA" else "#FF8990", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        hero.addView(text("Income ${moneyText(summary.first)}  •  Expense ${moneyText(summary.second)}", 13f, "#A0ADCD").apply { setPadding(0, dp(7), 0, 0) }); root.addView(hero); root.addView(space(18)); root.addView(sectionTitle("Transactions"))
        val items = store.moneyRecords()
        if (items.isEmpty()) root.addView(emptyCard("No transactions", "Add income or expense records to see your balance."))
        items.take(30).forEachIndexed { i, item ->
            val sign = if (item.type == "Income") "+" else "−"; val color = if (item.type == "Income") "#61CBA7" else "#FF858D"; val c = card("#151F3A")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(item.category, 16f, "#FFFFFF", bold = true)); labels.addView(text("${item.date}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 12f, "#7F8DB5"))
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(text("$sign${moneyText(item.amount)}", 16f, color, bold = true)); c.addView(row); c.setOnClickListener { moneyActions(item) }
            root.addView(c); if (i < items.take(30).lastIndex) root.addView(space(8))
        }
        return root
    }

    private fun attendancePage(): LinearLayout {
        val root = page(); detailHeader(root, "Attendance", "Date and exact save time are recorded automatically.")
        val summary = store.attendanceSummaryForCurrentMonth(); val stats = card("#182342")
        stats.addView(text("This month", 12f, "#8795BB", bold = true)); stats.addView(text("P ${summary["Present"]}   •   A ${summary["Absent"]}   •   L ${summary["Leave"]}", 22f, "#FFFFFF", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        root.addView(stats); root.addView(space(16)); root.addView(sectionTitle("Mark today"))
        val markCard = card("#17213E"); val today = store.attendanceRecord()
        markCard.addView(text(if (today.status == "Not marked") "Not marked yet" else "${today.status} • ${today.time}", 16f, "#FFFFFF", bold = true)); markCard.addView(text(friendlyDate(store.today()), 12f, "#8694BC").apply { setPadding(0, dp(4), 0, dp(12)) })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(smallAction("Present", "#237B64") { markAttendance("Present") }, LinearLayout.LayoutParams(0, dp(44), 1f)); buttons.addView(hSpace(8)); buttons.addView(smallAction("Absent", "#A14E58") { markAttendance("Absent") }, LinearLayout.LayoutParams(0, dp(44), 1f)); buttons.addView(hSpace(8)); buttons.addView(smallAction("Leave", "#9B7331") { markAttendance("Leave") }, LinearLayout.LayoutParams(0, dp(44), 1f))
        markCard.addView(buttons); root.addView(markCard); root.addView(space(20)); root.addView(sectionTitle("Saved attendance"))
        val records = store.markedAttendanceHistory(90).take(60)
        if (records.isEmpty()) root.addView(emptyCard("No saved records", "Your attendance history will appear here with date and time."))
        else records.forEachIndexed { i, record -> root.addView(attendanceRecordCard(record) { editAttendanceDate(record.date, record.status) }); if (i < records.lastIndex) root.addView(space(8)) }
        return root
    }

    private fun routineActions(item: RoutineItem) {
        val done = item.doneDate == store.today()
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (done) "Mark not done" else "Mark done", "Edit routine", if (item.alarmEnabled) "Turn alarm off" else "Turn alarm on", "Delete")) { _, which ->
            val items = store.routines(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) {
                0 -> items[index] = item.copy(doneDate = if (done) "" else store.today())
                1 -> { addRoutine(item); return@setItems }
                2 -> items[index] = item.copy(alarmEnabled = !item.alarmEnabled)
                3 -> items.removeAt(index)
            }
            store.saveRoutines(items); ReminderScheduler.scheduleAll(this, store); render()
        }.show()
    }

    private fun toggleRoutine(item: RoutineItem) {
        val items = store.routines(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return
        val done = item.doneDate == store.today(); items[index] = item.copy(doneDate = if (done) "" else store.today()); store.saveRoutines(items); render()
    }

    private fun addRoutine(existing: RoutineItem? = null) {
        val box = formBox(); val titleInput = input("Routine title").apply { setText(existing?.title ?: "") }; val categoryInput = input("Category").apply { setText(existing?.category ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 8; minute = existing?.minute ?: 0 }; val alarmCheck = CheckBox(this).apply { text = "Daily alarm reminder"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: true }
        box.addView(titleInput); box.addView(space(8)); box.addView(categoryInput); box.addView(picker); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "New routine" else "Edit routine").setView(box).setPositiveButton("Save") { _, _ ->
            val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val updated = RoutineItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, categoryInput.text.toString().trim().ifBlank { "Routine" }, existing?.doneDate ?: "", alarmCheck.isChecked)
            val items = store.routines(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveRoutines(items); ReminderScheduler.scheduleAll(this, store); render()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun mealActions(item: MealItem) {
        val done = item.doneDate == store.today()
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (done) "Mark not eaten" else "Mark eaten", "Edit meal", if (item.alarmEnabled) "Turn alarm off" else "Turn alarm on", "Delete")) { _, which ->
            val items = store.meals(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) {
                0 -> items[index] = item.copy(doneDate = if (done) "" else store.today())
                1 -> { addMeal(item); return@setItems }
                2 -> items[index] = item.copy(alarmEnabled = !item.alarmEnabled)
                3 -> items.removeAt(index)
            }
            store.saveMeals(items); ReminderScheduler.scheduleAll(this, store); render()
        }.show()
    }

    private fun addMeal(existing: MealItem? = null) {
        val box = formBox(); val titleInput = input("Meal name").apply { setText(existing?.title ?: "") }; val noteInput = input("Food note / plan").apply { setText(existing?.note ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 13; minute = existing?.minute ?: 0 }; val alarmCheck = CheckBox(this).apply { text = "Meal alarm reminder"; setTextColor(Color.WHITE); isChecked = existing?.alarmEnabled ?: true }
        box.addView(titleInput); box.addView(space(8)); box.addView(noteInput); box.addView(picker); box.addView(alarmCheck)
        AlertDialog.Builder(this).setTitle(if (existing == null) "New meal" else "Edit meal").setView(box).setPositiveButton("Save") { _, _ ->
            val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val updated = MealItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, noteInput.text.toString().trim(), existing?.doneDate ?: "", alarmCheck.isChecked)
            val items = store.meals(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveMeals(items); ReminderScheduler.scheduleAll(this, store); render()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun alarmActions(item: AlarmItem) {
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(if (item.enabled) "অ্যালার্ম বন্ধ করুন" else "অ্যালার্ম চালু করুন", "এডিট করুন", "ডিলিট করুন")) { _, which ->
            val items = store.alarms(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) {
                0 -> items[index] = item.copy(enabled = !item.enabled)
                1 -> { addAlarm(item); return@setItems }
                2 -> items.removeAt(index)
            }
            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()
        }.show()
    }

    private fun addAlarm(existing: AlarmItem? = null) {
        val box = formBox()
        val titleInput = input("অ্যালার্মের নাম").apply { setText(existing?.title ?: "") }
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = existing?.hour ?: 7; minute = existing?.minute ?: 0 }
        val countdown = text("", 14f, "#BFC9EA", bold = true).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(11), dp(12), dp(11)); background = roundedStroke("#151F3A", "#66FFFFFF", 1, 13)
        }
        var selectedRingtone = existing?.ringtoneUri ?: ""
        lateinit var ringtoneButton: Button
        ringtoneButton = pillButton("রিংটোন: ${ringtoneTitle(selectedRingtone)}", "#34466F") {
            pickRingtone(selectedRingtone) { uri ->
                selectedRingtone = uri
                ringtoneButton.text = "রিংটোন: ${ringtoneTitle(uri)}"
            }
        }
        val soundCheck = CheckBox(this).apply { text = "রিংটোন বাজবে"; setTextColor(Color.WHITE); isChecked = existing?.soundEnabled ?: true }
        val vibrateCheck = CheckBox(this).apply { text = "ভাইব্রেশন হবে"; setTextColor(Color.WHITE); isChecked = existing?.vibrateEnabled ?: true }
        val enabledCheck = CheckBox(this).apply { text = "অ্যালার্ম চালু"; setTextColor(Color.WHITE); isChecked = existing?.enabled ?: true }
        box.addView(titleInput); box.addView(space(8)); box.addView(picker); box.addView(space(8)); box.addView(countdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); box.addView(space(8)); box.addView(ringtoneButton); box.addView(soundCheck); box.addView(vibrateCheck); box.addView(enabledCheck)

        val dialogHandler = Handler(Looper.getMainLooper())
        lateinit var updater: Runnable
        updater = object : Runnable {
            override fun run() { countdown.text = "বাজতে ${remainingText(picker.hour, picker.minute)} বাকি"; dialogHandler.postDelayed(this, 1000) }
        }
        picker.setOnTimeChangedListener { _, hour, minute -> countdown.text = "বাজতে ${remainingText(hour, minute)} বাকি" }

        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন অ্যালার্ম" else "অ্যালার্ম এডিট").setView(box).setPositiveButton("সেভ") { _, _ ->
            val name = titleInput.text.toString().trim().ifBlank { "Alarm" }
            val sound = soundCheck.isChecked
            val vibrate = if (!sound && !vibrateCheck.isChecked) true else vibrateCheck.isChecked
            if (!sound && !vibrateCheck.isChecked) Toast.makeText(this, "কমপক্ষে ভাইব্রেশন চালু রাখা হয়েছে", Toast.LENGTH_SHORT).show()
            val updated = AlarmItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, picker.hour, picker.minute, enabledCheck.isChecked, sound, vibrate, selectedRingtone)
            val items = store.alarms(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated)
            store.saveAlarms(items); ReminderScheduler.scheduleAll(this, store); render()
        }.setNegativeButton("বাতিল", null).create()
        dialog.setOnShowListener { updater.run() }; dialog.setOnDismissListener { dialogHandler.removeCallbacks(updater) }; dialog.show()
    }

    private fun courseActions(item: CourseItem) {
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf("Update course", "Add 10% progress", "Delete")) { _, which ->
            val items = store.courses(); val index = items.indexOfFirst { it.id == item.id }; if (index < 0) return@setItems
            when (which) { 0 -> { addCourse(item); return@setItems }; 1 -> items[index] = item.copy(progress = (item.progress + 10).coerceAtMost(100)); 2 -> items.removeAt(index) }
            store.saveCourses(items); render()
        }.show()
    }

    private fun addCourse(existing: CourseItem? = null) {
        val box = formBox(); val titleInput = input("Course / skill name").apply { setText(existing?.title ?: "") }; val nextInput = input("Next lesson or task").apply { setText(existing?.nextTask ?: "") }; val dueInput = input("Target date (YYYY-MM-DD)").apply { setText(existing?.dueDate ?: "") }; val progressInput = input("Progress 0-100").apply { inputType = InputType.TYPE_CLASS_NUMBER; setText((existing?.progress ?: 0).toString()) }
        box.addView(titleInput); box.addView(space(8)); box.addView(nextInput); box.addView(space(8)); box.addView(dueInput); box.addView(space(8)); box.addView(progressInput)
        AlertDialog.Builder(this).setTitle(if (existing == null) "New course" else "Update course").setView(box).setPositiveButton("Save") { _, _ ->
            val name = titleInput.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
            val progress = progressInput.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 0
            val updated = CourseItem(existing?.id ?: java.util.UUID.randomUUID().toString(), name, progress, nextInput.text.toString().trim(), dueInput.text.toString().trim())
            val items = store.courses(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated); store.saveCourses(items); render()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun addMoneyRecord(existing: MoneyRecord? = null) {
        val box = formBox(); val typeSpinner = Spinner(this); val types = listOf("Expense", "Income"); typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types); if (existing?.type == "Income") typeSpinner.setSelection(1)
        val amountInput = input("Amount").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; if (existing != null) setText(existing.amount.toString()) }; val categoryInput = input("Category").apply { setText(existing?.category ?: "") }; val noteInput = input("Note").apply { setText(existing?.note ?: "") }; val dateInput = input("Date (YYYY-MM-DD)").apply { setText(existing?.date ?: store.today()) }
        box.addView(typeSpinner); box.addView(space(8)); box.addView(amountInput); box.addView(space(8)); box.addView(categoryInput); box.addView(space(8)); box.addView(noteInput); box.addView(space(8)); box.addView(dateInput)
        AlertDialog.Builder(this).setTitle(if (existing == null) "Add transaction" else "Edit transaction").setView(box).setPositiveButton("Save") { _, _ ->
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0; if (amount <= 0) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
            val updated = MoneyRecord(existing?.id ?: java.util.UUID.randomUUID().toString(), types[typeSpinner.selectedItemPosition], amount, categoryInput.text.toString().trim().ifBlank { "General" }, noteInput.text.toString().trim(), dateInput.text.toString().trim().ifBlank { store.today() })
            val items = store.moneyRecords(); val index = items.indexOfFirst { it.id == updated.id }; if (index >= 0) items[index] = updated else items.add(updated); store.saveMoneyRecords(items); render()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun moneyActions(item: MoneyRecord) {
        AlertDialog.Builder(this).setTitle(item.category).setItems(arrayOf("Edit", "Delete")) { _, which ->
            if (which == 0) addMoneyRecord(item) else { val items = store.moneyRecords(); items.removeAll { it.id == item.id }; store.saveMoneyRecords(items); render() }
        }.show()
    }

    private fun editAttendanceDate(date: String, current: String) {
        AlertDialog.Builder(this).setTitle(friendlyDate(date)).setSingleChoiceItems(arrayOf("Present", "Absent", "Leave", "Not marked"), arrayOf("Present", "Absent", "Leave", "Not marked").indexOf(current).coerceAtLeast(0)) { dialog, which ->
            val status = arrayOf("Present", "Absent", "Leave", "Not marked")[which]; store.setAttendance(status, date); dialog.dismiss(); render()
        }.show()
    }

    private fun markAttendance(status: String) {
        store.setAttendance(status); val saved = store.attendanceRecord(); Toast.makeText(this, "$status saved • ${friendlyDate(saved.date)} • ${saved.time}", Toast.LENGTH_SHORT).show(); render()
    }

    private fun attendanceRecordCard(record: AttendanceRecord, onClick: () -> Unit): LinearLayout {
        val color = when (record.status) { "Present" -> "#2B8B70"; "Absent" -> "#A9515C"; else -> "#9B7535" }
        val c = card("#151F3A", padding = 14); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(record.status.take(1), 16f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(color, 12) }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }; labels.addView(text(record.status, 15f, "#FFFFFF", bold = true)); labels.addView(text("${friendlyDate(record.date)}${if (record.time.isNotBlank()) " • ${record.time}" else ""}", 12f, "#8C9AC1"))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(text("›", 25f, "#66759E")); c.addView(row); c.setOnClickListener { onClick() }; return c
    }

    private fun alarmCard(item: AlarmItem, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(if (item.enabled) "⏰" else "○", 24f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(if (item.enabled) "#2D7EA6" else "#45516D", 16) }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0) }; labels.addView(text(item.title, 17f, "#FFFFFF", bold = true)); val subtitle = text("", 13f, if (item.enabled) "#9CB6E4" else "#7F8AA8"); labels.addView(subtitle)
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(text("›", 30f, "#65749E")); c.addView(row); c.setOnClickListener { onClick() }; alarmCountdownViews.add(subtitle to item); updateAlarmSubtitle(subtitle, item); return c
    }

    private fun updateAlarmSubtitle(view: TextView, item: AlarmItem) {
        val mode = when {
            item.soundEnabled && item.vibrateEnabled -> "রিং + ভাইব্রেশন"
            item.soundEnabled -> "শুধু রিং"
            else -> "শুধু ভাইব্রেশন"
        }
        view.text = if (item.enabled) "${timeText(item.hour, item.minute)} • ${remainingText(item.hour, item.minute)} বাকি • $mode" else "${timeText(item.hour, item.minute)} • বন্ধ"
    }

    private fun startAlarmCountdowns() {
        fun tick() {
            if (detailPage != "alarms") return
            alarmCountdownViews.forEach { (view, item) -> updateAlarmSubtitle(view, item) }
            handler.postDelayed({ tick() }, 1000)
        }
        tick()
    }

    private fun remainingText(hour: Int, minute: Int): String {
        val seconds = Duration.between(LocalDateTime.now(), nextTime(hour, minute)).seconds.coerceAtLeast(0); val hours = seconds / 3600; val minutes = (seconds % 3600) / 60; val secs = seconds % 60
        return when {
            hours > 0 -> "${hours}ঘ ${minutes.toString().padStart(2, '0')}মি ${secs.toString().padStart(2, '0')}সে"
            minutes > 0 -> "${minutes}মি ${secs.toString().padStart(2, '0')}সে"
            else -> "${secs}সে"
        }
    }

    private fun pickRingtone(current: String, callback: (String) -> Unit) {
        pendingRingtoneResult = callback
        val existing = current.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtonePickerLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "রিংটোন বাছাই করুন")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        })
    }

    private fun ringtoneTitle(uri: String): String {
        if (uri.isBlank()) return "ফোনের ডিফল্ট অ্যালার্ম"
        return runCatching { RingtoneManager.getRingtone(this, Uri.parse(uri))?.getTitle(this) ?: "নির্বাচিত রিংটোন" }.getOrDefault("নির্বাচিত রিংটোন")
    }

    private fun useCurrentPrayerLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 441)
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val location = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }.maxByOrNull { it.time }
        if (location == null) {
            Toast.makeText(this, "ফোনের Location চালু করে আবার চেষ্টা করুন, অথবা হাতে লিখুন", Toast.LENGTH_LONG).show()
            return
        }
        store.setPrayerLocation(location.latitude, location.longitude)
        store.setPrayerEnabled(true)
        ReminderScheduler.scheduleAll(this, store)
        Toast.makeText(this, "লোকেশন সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun editPrayerLocation() {
        val settings = store.prayerSettings(); val box = formBox()
        val lat = input("Latitude").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED; if (settings.hasLocation()) setText(settings.latitude.toString()) }
        val lon = input("Longitude").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED; if (settings.hasLocation()) setText(settings.longitude.toString()) }
        box.addView(lat); box.addView(space(8)); box.addView(lon)
        AlertDialog.Builder(this).setTitle("নামাজের লোকেশন").setView(box).setPositiveButton("সেভ") { _, _ ->
            val latitude = lat.text.toString().toDoubleOrNull(); val longitude = lon.text.toString().toDoubleOrNull()
            if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) Toast.makeText(this, "সঠিক latitude/longitude দিন", Toast.LENGTH_SHORT).show()
            else { store.setPrayerLocation(latitude, longitude); store.setPrayerEnabled(true); ReminderScheduler.scheduleAll(this, store); render() }
        }.setNegativeButton("বাতিল", null).show()
    }

    private fun friendlyDate(date: String): String = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")) }.getOrDefault(date)

    private fun waterCard(): LinearLayout {
        val count = store.waterCount(); val c = card("#15213C"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text("💧", 28f, "#75CFFF").apply { gravity = Gravity.CENTER; background = rounded("#16384E", 16) }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(10), 0) }; labels.addView(text("Water goal", 17f, "#FFFFFF", bold = true)); labels.addView(text("$count / 8 glasses today", 13f, "#8C9AC1")); row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(smallAction("−", "#263456") { store.removeWater(); render() }, LinearLayout.LayoutParams(dp(48), dp(46))); row.addView(hSpace(8)); row.addView(smallAction("+", "#2C7AA0") { store.addWater(); render() }, LinearLayout.LayoutParams(dp(48), dp(46))); c.addView(row); return c
    }

    private fun pageHeader(root: LinearLayout, titleText: String, subtitle: String) { root.addView(text(titleText, 29f, "#FFFFFF", bold = true)); root.addView(text(subtitle, 14f, "#8C9BC3").apply { setPadding(0, dp(4), 0, dp(22)) }) }

    private fun rowCard(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(icon, 25f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(accent, 16) }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0) }; labels.addView(text(titleText, 17f, "#FFFFFF", bold = true)); labels.addView(text(subtitle, 13f, "#8997BF")); row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(text("›", 30f, "#65749E")); c.addView(row); c.setOnClickListener { onClick() }; return c
    }

    private fun itemCard(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout = rowCard(icon, titleText, subtitle, accent, onClick)

    private fun compactAction(icon: String, titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#17213E").apply { gravity = Gravity.CENTER_VERTICAL }; c.addView(text(icon, 25f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(accent, 14) }, LinearLayout.LayoutParams(dp(48), dp(48))); c.addView(text(titleText, 15f, "#FFFFFF", bold = true).apply { setPadding(0, dp(8), 0, 0) }); c.addView(text(subtitle, 11f, "#8190B8")); c.setOnClickListener { onClick() }; return c
    }

    private fun simpleLine(left: String, right: String): LinearLayout {
        val c = card("#141E39", padding = 14); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; row.addView(text(left, 14f, "#FFFFFF", bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(text(right, 13f, "#8D9AC0")); c.addView(row); return c
    }

    private fun settingCard(titleText: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val c = card("#151F3B", padding = 15); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; labels.addView(text(titleText, 16f, "#FFFFFF", bold = true)); labels.addView(text(subtitle, 12f, "#8593BA")); row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(text("●", 15f, accent)); c.addView(row); c.setOnClickListener { onClick() }; return c
    }

    private fun emptyCard(titleText: String, subtitle: String): LinearLayout { val c = card("#141E38"); c.addView(text(titleText, 16f, "#FFFFFF", bold = true)); c.addView(text(subtitle, 13f, "#7F8DB5").apply { setPadding(0, dp(5), 0, 0) }); return c }
    private fun sectionTitle(value: String) = text(value, 13f, "#A8B4D7", bold = true).apply { setPadding(dp(2), 0, 0, dp(9)); letterSpacing = 0.04f }
    private fun statusPill(value: String, bg: String, fg: String) = text(value, 12f, fg, bold = true).apply { gravity = Gravity.CENTER; background = rounded(bg, 14); setPadding(dp(11), dp(7), dp(11), dp(7)) }
    private fun text(value: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply { this.text = value; textSize = size; setTextColor(Color.parseColor(color)); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun pillButton(label: String, bg: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(bg, 13); setOnClickListener { action() } }
    private fun smallAction(label: String, bg: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(bg, 12); setOnClickListener { action() } }
    private fun card(hex: String, padding: Int = 17) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); background = rounded(hex, 20); elevation = dp(2).toFloat() }
    private fun formBox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(4)) }
    private fun input(hintText: String) = EditText(this).apply { hint = hintText; setSingleLine(true); setPadding(dp(14), dp(10), dp(14), dp(10)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply { setColor(Color.parseColor(hex)); cornerRadius = dp(radiusDp).toFloat() }
    private fun roundedStroke(fill: String, stroke: String, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply { setColor(Color.parseColor(fill)); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(strokeDp), Color.parseColor(stroke)) }
    private fun gradient(top: String, bottom: String) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor(top), Color.parseColor(bottom)))
    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun hSpace(width: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }

    private fun startClock() {
        fun tick() { clockView?.text = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a")); handler.postDelayed({ tick() }, 1000) }
        tick()
    }

    private fun greetingText(): String = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }

    private fun nextReminderText(): String {
        val now = LocalDateTime.now(); val entries = mutableListOf<Pair<LocalDateTime, String>>()
        store.routines().filter { it.alarmEnabled }.forEach { entries += nextTime(it.hour, it.minute) to it.title }
        store.meals().filter { it.alarmEnabled }.forEach { entries += nextTime(it.hour, it.minute) to it.title }
        store.alarms().filter { it.enabled }.forEach { entries += nextTime(it.hour, it.minute) to it.title }
        PrayerScheduler.nextPrayer(this, store)?.let { entries += it.second to it.first.nameBn }
        val next = entries.minByOrNull { it.first } ?: return "No upcoming reminders"; val day = if (next.first.toLocalDate() == now.toLocalDate()) "Today" else "Tomorrow"
        return "$day • ${next.second} • ${next.first.format(DateTimeFormatter.ofPattern("hh:mm a"))}"
    }

    private fun nextTime(hour: Int, minute: Int): LocalDateTime { var next = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute)); if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1); return next }
    private fun timeText(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("hh:mm a"))

    private fun editFocusNote(current: String) { val input = input("Today's main focus").apply { setText(current) }; AlertDialog.Builder(this).setTitle("Focus note").setView(input).setPositiveButton("Save") { _, _ -> uiPrefs.edit().putString("focus_${store.today()}", input.text.toString().trim()).apply(); render() }.setNegativeButton("Cancel", null).show() }
    private fun changeName() { val input = input("Profile name").apply { setText(store.profileName()) }; AlertDialog.Builder(this).setTitle("Profile name").setView(input).setPositiveButton("Save") { _, _ -> store.setProfileName(input.text.toString()); render() }.setNegativeButton("Cancel", null).show() }

    private fun changePin() {
        val box = formBox(); val oldPin = input("Current PIN").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }; val newPin = input("New 4-digit PIN").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }; box.addView(oldPin); box.addView(space(8)); box.addView(newPin)
        AlertDialog.Builder(this).setTitle("Change PIN").setView(box).setPositiveButton("Update") { _, _ ->
            val newValue = newPin.text.toString(); if (newValue.length != 4 || newValue.any { !it.isDigit() }) Toast.makeText(this, "Use a 4-digit PIN", Toast.LENGTH_SHORT).show() else if (!store.changePin(oldPin.text.toString(), newValue)) Toast.makeText(this, "Current PIN is incorrect", Toast.LENGTH_SHORT).show() else Toast.makeText(this, "PIN updated", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun currency(): String = uiPrefs.getString("currency", "SAR") ?: "SAR"
    private fun changeCurrency() { val input = input("Currency code, e.g. SAR, USD").apply { setText(currency()) }; AlertDialog.Builder(this).setTitle("Currency").setView(input).setPositiveButton("Save") { _, _ -> val value = input.text.toString().trim().uppercase(Locale.getDefault()).take(5).ifBlank { "SAR" }; uiPrefs.edit().putString("currency", value).apply(); render() }.setNegativeButton("Cancel", null).show() }
    private fun moneyText(amount: Double): String = "${currency()} ${String.format(Locale.getDefault(), "%.2f", amount)}"
    private fun shareBackup() { val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Guide backup"); putExtra(Intent.EXTRA_TEXT, store.exportJson()) }; startActivity(Intent.createChooser(send, "Share Guide backup")) }
    private fun lockApp() { startActivity(Intent(this, LoginActivity::class.java)); finish() }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 210)
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 210); return }
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, packageName) })
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { Toast.makeText(this, "Precise alarms are supported", Toast.LENGTH_SHORT).show(); return }
        if (ReminderScheduler.exactAlarmAvailable(this)) { Toast.makeText(this, "Precise alarms are enabled", Toast.LENGTH_SHORT).show(); return }
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }.onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
