package com.guide.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val dueDate: String,
    val hour: Int = 9,
    val minute: Int = 0,
    val priority: String = "Normal",
    val reminderEnabled: Boolean = true,
    val completedDate: String = ""
)

data class HabitItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val completedDates: Set<String> = emptySet()
)

data class MedicineItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dose: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val ringtoneUri: String = ""
)

data class BillItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val dueDate: String,
    val reminderEnabled: Boolean = true,
    val paidDate: String = ""
)

data class WeeklyLifeSummary(
    val todoDone: Int,
    val todoTotal: Int,
    val habitChecks: Int,
    val habitPossible: Int,
    val medicineCount: Int,
    val billsPending: Int
)

class DailyLifeStore(context: Context) {
    private val prefs = context.getSharedPreferences("guide_store", Context.MODE_PRIVATE)

    fun todos(): MutableList<TodoItem> {
        val a = array("daily_todos_v29")
        return MutableList(a.length()) { i ->
            val o = a.optJSONObject(i) ?: JSONObject()
            TodoItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Task"),
                dueDate = o.optString("dueDate", LocalDate.now().toString()),
                hour = o.optInt("hour", 9),
                minute = o.optInt("minute", 0),
                priority = o.optString("priority", "Normal"),
                reminderEnabled = o.optBoolean("reminderEnabled", true),
                completedDate = o.optString("completedDate", "")
            )
        }
    }

    fun saveTodos(items: List<TodoItem>) {
        val a = JSONArray()
        items.forEach { x -> a.put(JSONObject().apply {
            put("id", x.id); put("title", x.title); put("dueDate", x.dueDate); put("hour", x.hour); put("minute", x.minute)
            put("priority", x.priority); put("reminderEnabled", x.reminderEnabled); put("completedDate", x.completedDate)
        }) }
        save("daily_todos_v29", a)
    }

    fun habits(): MutableList<HabitItem> {
        val a = array("daily_habits_v29")
        return MutableList(a.length()) { i ->
            val o = a.optJSONObject(i) ?: JSONObject()
            val dates = mutableSetOf<String>()
            val d = o.optJSONArray("completedDates") ?: JSONArray()
            for (j in 0 until d.length()) dates += d.optString(j)
            HabitItem(o.optString("id", UUID.randomUUID().toString()), o.optString("title", "Habit"), dates)
        }
    }

    fun saveHabits(items: List<HabitItem>) {
        val a = JSONArray()
        items.forEach { x -> a.put(JSONObject().apply {
            put("id", x.id); put("title", x.title); put("completedDates", JSONArray(x.completedDates.toList()))
        }) }
        save("daily_habits_v29", a)
    }

    fun medicines(): MutableList<MedicineItem> {
        val a = array("daily_medicines_v29")
        return MutableList(a.length()) { i ->
            val o = a.optJSONObject(i) ?: JSONObject()
            MedicineItem(
                id = o.optString("id", UUID.randomUUID().toString()), name = o.optString("name", "Medicine"),
                dose = o.optString("dose", ""), hour = o.optInt("hour", 8), minute = o.optInt("minute", 0),
                enabled = o.optBoolean("enabled", true), vibrateEnabled = o.optBoolean("vibrateEnabled", true),
                ringtoneUri = o.optString("ringtoneUri", "")
            )
        }
    }

    fun saveMedicines(items: List<MedicineItem>) {
        val a = JSONArray()
        items.forEach { x -> a.put(JSONObject().apply {
            put("id", x.id); put("name", x.name); put("dose", x.dose); put("hour", x.hour); put("minute", x.minute)
            put("enabled", x.enabled); put("vibrateEnabled", x.vibrateEnabled); put("ringtoneUri", x.ringtoneUri)
        }) }
        save("daily_medicines_v29", a)
    }

    fun bills(): MutableList<BillItem> {
        val a = array("daily_bills_v29")
        return MutableList(a.length()) { i ->
            val o = a.optJSONObject(i) ?: JSONObject()
            BillItem(
                id = o.optString("id", UUID.randomUUID().toString()), title = o.optString("title", "Bill"),
                amount = o.optDouble("amount", 0.0), dueDate = o.optString("dueDate", LocalDate.now().toString()),
                reminderEnabled = o.optBoolean("reminderEnabled", true), paidDate = o.optString("paidDate", "")
            )
        }
    }

    fun saveBills(items: List<BillItem>) {
        val a = JSONArray()
        items.forEach { x -> a.put(JSONObject().apply {
            put("id", x.id); put("title", x.title); put("amount", x.amount); put("dueDate", x.dueDate)
            put("reminderEnabled", x.reminderEnabled); put("paidDate", x.paidDate)
        }) }
        save("daily_bills_v29", a)
    }

    fun weeklySummary(today: LocalDate = LocalDate.now()): WeeklyLifeSummary {
        val start = today.minusDays(6)
        fun inWeek(raw: String): Boolean = runCatching {
            val d = LocalDate.parse(raw); !d.isBefore(start) && !d.isAfter(today)
        }.getOrDefault(false)
        val todos = todos()
        val habits = habits()
        val habitChecks = habits.sumOf { h -> h.completedDates.count(::inWeek) }
        return WeeklyLifeSummary(
            todoDone = todos.count { it.completedDate.isNotBlank() && inWeek(it.completedDate) },
            todoTotal = todos.count { runCatching { !LocalDate.parse(it.dueDate).isBefore(start) && !LocalDate.parse(it.dueDate).isAfter(today) }.getOrDefault(false) },
            habitChecks = habitChecks,
            habitPossible = habits.size * 7,
            medicineCount = medicines().count { it.enabled },
            billsPending = bills().count { it.paidDate.isBlank() }
        )
    }

    fun habitStreak(item: HabitItem): Int {
        var d = LocalDate.now(); var n = 0
        while (item.completedDates.contains(d.toString())) { n++; d = d.minusDays(1) }
        return n
    }

    private fun array(key: String): JSONArray = runCatching { JSONArray(prefs.getString(key, "[]") ?: "[]") }.getOrDefault(JSONArray())
    private fun save(key: String, value: JSONArray) { prefs.edit().putString(key, value.toString()).apply() }
}
