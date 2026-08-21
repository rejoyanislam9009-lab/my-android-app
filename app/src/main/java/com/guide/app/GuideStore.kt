package com.guide.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class RoutineItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val category: String = "Routine",
    val doneDate: String = ""
)

data class MealItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val note: String = "",
    val doneDate: String = ""
)

class GuideStore(context: Context) {
    private val prefs = context.getSharedPreferences("guide_store", Context.MODE_PRIVATE)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun hasProfile(): Boolean = prefs.contains("profile_name") && prefs.contains("pin_hash")

    fun profileName(): String = prefs.getString("profile_name", "Guide User") ?: "Guide User"

    fun saveProfile(name: String, pin: String) {
        prefs.edit()
            .putString("profile_name", name.trim())
            .putString("pin_hash", hash(pin))
            .apply()
    }

    fun verifyPin(pin: String): Boolean = prefs.getString("pin_hash", "") == hash(pin)

    fun today(): String = LocalDate.now().format(dateFormatter)

    fun routines(): MutableList<RoutineItem> {
        val raw = prefs.getString("routines", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val items = mutableListOf<RoutineItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            items += RoutineItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Routine"),
                hour = o.optInt("hour", 8),
                minute = o.optInt("minute", 0),
                category = o.optString("category", "Routine"),
                doneDate = o.optString("doneDate", "")
            )
        }
        return items
    }

    fun saveRoutines(items: List<RoutineItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("hour", item.hour)
                put("minute", item.minute)
                put("category", item.category)
                put("doneDate", item.doneDate)
            })
        }
        prefs.edit().putString("routines", array.toString()).apply()
    }

    fun meals(): MutableList<MealItem> {
        val raw = prefs.getString("meals", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val items = mutableListOf<MealItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            items += MealItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Meal"),
                hour = o.optInt("hour", 12),
                minute = o.optInt("minute", 0),
                note = o.optString("note", ""),
                doneDate = o.optString("doneDate", "")
            )
        }
        return items
    }

    fun saveMeals(items: List<MealItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("hour", item.hour)
                put("minute", item.minute)
                put("note", item.note)
                put("doneDate", item.doneDate)
            })
        }
        prefs.edit().putString("meals", array.toString()).apply()
    }

    fun attendanceFor(date: String = today()): String {
        val obj = attendanceObject()
        return obj.optString(date, "Not marked")
    }

    fun setAttendance(status: String, date: String = today()) {
        val obj = attendanceObject()
        obj.put(date, status)
        prefs.edit().putString("attendance", obj.toString()).apply()
    }

    fun attendanceSummaryForCurrentMonth(): Map<String, Int> {
        val month = LocalDate.now().toString().substring(0, 7)
        val counts = linkedMapOf("Present" to 0, "Absent" to 0, "Leave" to 0)
        val obj = attendanceObject()
        obj.keys().forEach { date ->
            if (date.startsWith(month)) {
                val status = obj.optString(date)
                if (counts.containsKey(status)) counts[status] = (counts[status] ?: 0) + 1
            }
        }
        return counts
    }

    fun waterCount(): Int {
        val savedDate = prefs.getString("water_date", "")
        return if (savedDate == today()) prefs.getInt("water_count", 0) else 0
    }

    fun addWater(): Int {
        val next = (waterCount() + 1).coerceAtMost(12)
        prefs.edit().putString("water_date", today()).putInt("water_count", next).apply()
        return next
    }

    fun removeWater(): Int {
        val next = (waterCount() - 1).coerceAtLeast(0)
        prefs.edit().putString("water_date", today()).putInt("water_count", next).apply()
        return next
    }

    fun updateCompletedDay(isComplete: Boolean) {
        val dates = prefs.getStringSet("completed_days", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (isComplete) dates.add(today()) else dates.remove(today())
        prefs.edit().putStringSet("completed_days", dates).apply()
    }

    fun streak(): Int {
        val dates = prefs.getStringSet("completed_days", emptySet()) ?: emptySet()
        var cursor = LocalDate.now()
        var count = 0
        while (dates.contains(cursor.format(dateFormatter))) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun seedDefaultsIfNeeded() {
        if (prefs.getBoolean("seeded", false)) return
        saveRoutines(
            listOf(
                RoutineItem(title = "Morning planning", hour = 7, minute = 30, category = "Focus"),
                RoutineItem(title = "Evening review", hour = 21, minute = 0, category = "Reflection")
            )
        )
        saveMeals(
            listOf(
                MealItem(title = "Breakfast", hour = 8, minute = 0, note = "Start with water and a balanced meal"),
                MealItem(title = "Lunch", hour = 13, minute = 30, note = "Keep portions balanced"),
                MealItem(title = "Dinner", hour = 20, minute = 0, note = "Prefer a lighter meal")
            )
        )
        prefs.edit().putBoolean("seeded", true).apply()
    }

    private fun attendanceObject(): JSONObject {
        val raw = prefs.getString("attendance", "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun hash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
