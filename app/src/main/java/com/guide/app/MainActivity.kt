package com.guide.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var store: GuideStore
    private val handler = Handler(Looper.getMainLooper())
    private var clockView: TextView? = null
    private val prefs by lazy { getSharedPreferences("guide_ui", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuideStore(this)
        if (!store.hasProfile()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        store.seedDefaultsIfNeeded()
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
        setContentView(buildDashboard())
        startClock()
    }

    private fun buildDashboard(): View {
        val routines = store.routines()
        val meals = store.meals()
        val today = store.today()
        val routineDone = routines.count { it.doneDate == today }
        val mealDone = meals.count { it.doneDate == today }
        val total = routines.size + meals.size
        val done = routineDone + mealDone
        val complete = total > 0 && done == total
        store.updateCompletedDay(complete)
        val progress = if (total == 0) 0 else ((done.toDouble() / total) * 100).roundToInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(40))
            background = gradient("#0B1020", "#111831")
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val greeting = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        greeting.addView(TextView(this).apply {
            text = greetingText()
            textSize = 13f
            setTextColor(Color.parseColor("#8F9BC2"))
        })
        greeting.addView(TextView(this).apply {
            text = store.profileName()
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        top.addView(greeting, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Button(this).apply {
            text = "Lock"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded("#1D2848", 14)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }, LinearLayout.LayoutParams(dp(78), dp(44)))
        root.addView(top)
        root.addView(space(18))

        val hero = card("#18203C")
        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dateBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dateBlock.addView(TextView(this).apply {
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM"))
            textSize = 14f
            setTextColor(Color.parseColor("#9EABD0"))
        })
        clockView = TextView(this).apply {
            text = "--:--:--"
            textSize = 29f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        dateBlock.addView(clockView)
        heroTop.addView(dateBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heroTop.addView(TextView(this).apply {
            text = "🔥 ${store.streak()} day streak"
            textSize = 14f
            setTextColor(Color.parseColor("#FFD27D"))
            gravity = Gravity.CENTER
            background = rounded("#322B2A", 14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        })
        hero.addView(heroTop)
        hero.addView(space(18))
        hero.addView(TextView(this).apply {
            text = "Today's progress  •  $done of $total completed"
            textSize = 14f
            setTextColor(Color.parseColor("#CAD2EE"))
        })
        hero.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = progress
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7C5CFC"))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)).apply { topMargin = dp(8) })
        hero.addView(TextView(this).apply {
            text = "$progress% • ${nextReminderText()}"
            textSize = 13f
            setTextColor(Color.parseColor("#8997C1"))
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(hero)
        root.addView(space(16))

        root.addView(sectionTitle("Today at a glance"))
        root.addView(actionCard("✓", "Daily routines", "$routineDone/${routines.size} completed", "#6D5DFB") { openRoutines() })
        root.addView(space(10))
        root.addView(actionCard("🍽", "Meal routine", "$mealDone/${meals.size} meals checked", "#17A589") { openMeals() })
        root.addView(space(10))
        val attendance = store.attendanceFor()
        val summary = store.attendanceSummaryForCurrentMonth()
        root.addView(actionCard("◎", "Attendance", "$attendance • P ${summary["Present"]} / A ${summary["Absent"]}", "#E07A5F") { openAttendance() })
        root.addView(space(10))
        root.addView(waterCard())
        root.addView(space(18))

        root.addView(sectionTitle("Focus note"))
        val noteCard = card("#151D36")
        val note = prefs.getString("focus_${store.today()}", "") ?: ""
        noteCard.addView(TextView(this).apply {
            text = if (note.isBlank()) "Tap to add your most important focus for today." else "“$note”"
            textSize = 15f
            setTextColor(if (note.isBlank()) Color.parseColor("#8997C1") else Color.WHITE)
        })
        noteCard.setOnClickListener { editFocusNote(note) }
        root.addView(noteCard)
        root.addView(space(18))

        root.addView(TextView(this).apply {
            text = "GUIDE • Your day, one step at a time"
            textSize = 11f
            letterSpacing = 0.13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#58658A"))
        })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
    }

    private fun waterCard(): LinearLayout {
        val count = store.waterCount()
        val card = card("#151D36")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = "💧"
            textSize = 27f
            gravity = Gravity.CENTER
            background = rounded("#16344B", 16)
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        text.addView(TextView(this).apply {
            this.text = "Water goal"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        text.addView(TextView(this).apply {
            this.text = "$count / 8 glasses today"
            textSize = 13f
            setTextColor(Color.parseColor("#8F9BC2"))
        })
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "−"
            textSize = 20f
            setTextColor(Color.WHITE)
            background = rounded("#243151", 12)
            setOnClickListener { store.removeWater(); render() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        row.addView(spaceHorizontal(8))
        row.addView(Button(this).apply {
            text = "+"
            textSize = 20f
            setTextColor(Color.WHITE)
            background = rounded("#286B91", 12)
            setOnClickListener { store.addWater(); render() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        card.addView(row)
        return card
    }

    private fun openRoutines() {
        val items = store.routines()
        val labels = items.map { item ->
            val check = if (item.doneDate == store.today()) "✓" else "○"
            "$check  ${item.title}   ${timeText(item.hour, item.minute)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Daily routines")
            .setItems(if (labels.isEmpty()) arrayOf("No routines yet") else labels) { _, which ->
                if (items.isNotEmpty()) routineActions(items[which])
            }
            .setPositiveButton("Add") { _, _ -> addRoutine() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun routineActions(item: RoutineItem) {
        val done = item.doneDate == store.today()
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(arrayOf(if (done) "Mark not done" else "Mark done", "Delete routine")) { _, which ->
                val items = store.routines()
                val index = items.indexOfFirst { it.id == item.id }
                if (index < 0) return@setItems
                if (which == 0) {
                    items[index] = item.copy(doneDate = if (done) "" else store.today())
                    store.saveRoutines(items)
                } else {
                    ReminderScheduler.cancel(this, "routine:${item.id}")
                    items.removeAt(index)
                    store.saveRoutines(items)
                }
                render()
            }
            .show()
    }

    private fun addRoutine() {
        val box = formBox()
        val title = input("Routine title")
        val category = input("Category (Work, Health, Study…)")
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = 8; minute = 0 }
        box.addView(title)
        box.addView(space(8))
        box.addView(category)
        box.addView(picker)
        AlertDialog.Builder(this)
            .setTitle("New routine")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = title.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Routine title is required", Toast.LENGTH_SHORT).show()
                } else {
                    val item = RoutineItem(title = name, hour = picker.hour, minute = picker.minute, category = category.text.toString().trim().ifBlank { "Routine" })
                    val items = store.routines().apply { add(item) }
                    store.saveRoutines(items)
                    ReminderScheduler.schedule(this, "routine:${item.id}", item.title, "Daily routine • ${item.category}", item.hour, item.minute)
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openMeals() {
        val items = store.meals()
        val labels = items.map { item ->
            val check = if (item.doneDate == store.today()) "✓" else "○"
            "$check  ${item.title}   ${timeText(item.hour, item.minute)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Meal routine")
            .setItems(if (labels.isEmpty()) arrayOf("No meals yet") else labels) { _, which ->
                if (items.isNotEmpty()) mealActions(items[which])
            }
            .setPositiveButton("Add") { _, _ -> addMeal() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun mealActions(item: MealItem) {
        val done = item.doneDate == store.today()
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage(item.note.ifBlank { "Scheduled meal" })
            .setItems(arrayOf(if (done) "Mark not eaten" else "Mark eaten", "Delete meal")) { _, which ->
                val items = store.meals()
                val index = items.indexOfFirst { it.id == item.id }
                if (index < 0) return@setItems
                if (which == 0) {
                    items[index] = item.copy(doneDate = if (done) "" else store.today())
                    store.saveMeals(items)
                } else {
                    ReminderScheduler.cancel(this, "meal:${item.id}")
                    items.removeAt(index)
                    store.saveMeals(items)
                }
                render()
            }
            .show()
    }

    private fun addMeal() {
        val box = formBox()
        val title = input("Meal name")
        val note = input("Food note / plan")
        val picker = TimePicker(this).apply { setIs24HourView(false); hour = 13; minute = 0 }
        box.addView(title)
        box.addView(space(8))
        box.addView(note)
        box.addView(picker)
        AlertDialog.Builder(this)
            .setTitle("New meal reminder")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = title.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Meal name is required", Toast.LENGTH_SHORT).show()
                } else {
                    val item = MealItem(title = name, hour = picker.hour, minute = picker.minute, note = note.text.toString().trim())
                    val items = store.meals().apply { add(item) }
                    store.saveMeals(items)
                    ReminderScheduler.schedule(this, "meal:${item.id}", item.title, item.note.ifBlank { "Meal time" }, item.hour, item.minute)
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAttendance() {
        val summary = store.attendanceSummaryForCurrentMonth()
        AlertDialog.Builder(this)
            .setTitle("Attendance • ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM"))}")
            .setMessage("Today: ${store.attendanceFor()}\n\nPresent: ${summary["Present"]}   Absent: ${summary["Absent"]}   Leave: ${summary["Leave"]}")
            .setItems(arrayOf("Present", "Absent", "Leave")) { _, which ->
                store.setAttendance(arrayOf("Present", "Absent", "Leave")[which])
                render()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun editFocusNote(current: String) {
        val input = EditText(this).apply {
            setText(current)
            hint = "Today's main focus"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Focus note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("focus_${store.today()}", input.text.toString().trim()).apply()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun nextReminderText(): String {
        val now = LocalDateTime.now()
        val entries = mutableListOf<Pair<LocalDateTime, String>>()
        store.routines().forEach {
            var at = LocalDateTime.of(LocalDate.now(), LocalTime.of(it.hour, it.minute))
            if (!at.isAfter(now)) at = at.plusDays(1)
            entries += at to it.title
        }
        store.meals().forEach {
            var at = LocalDateTime.of(LocalDate.now(), LocalTime.of(it.hour, it.minute))
            if (!at.isAfter(now)) at = at.plusDays(1)
            entries += at to it.title
        }
        val next = entries.minByOrNull { it.first } ?: return "No reminders scheduled"
        return "Next: ${next.second} • ${next.first.format(DateTimeFormatter.ofPattern("hh:mm a"))}"
    }

    private fun startClock() {
        val tick = object : Runnable {
            override fun run() {
                clockView?.text = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tick)
    }

    private fun greetingText(): String = when (LocalTime.now().hour) {
        in 5..11 -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }

    private fun actionCard(icon: String, title: String, subtitle: String, accent: String, onClick: () -> Unit): LinearLayout {
        val card = card("#151D36")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = icon
            textSize = 25f
            gravity = Gravity.CENTER
            background = rounded(accent, 16)
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
        }
        text.addView(TextView(this).apply {
            this.text = title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        text.addView(TextView(this).apply {
            this.text = subtitle
            textSize = 13f
            setTextColor(Color.parseColor("#8F9BC2"))
        })
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 30f
            setTextColor(Color.parseColor("#66739B"))
        })
        card.addView(row)
        card.setOnClickListener { onClick() }
        return card
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#AEB8D8"))
        setPadding(dp(2), 0, 0, dp(9))
    }

    private fun card(hex: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(hex, 20)
        elevation = dp(3).toFloat()
    }

    private fun formBox() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
    }

    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 701)
        }
    }

    private fun timeText(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("hh:mm a"))

    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun spaceHorizontal(width: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
