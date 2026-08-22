package com.guide.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class RoutineItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val category: String = "Routine",
    val doneDate: String = "",
    val alarmEnabled: Boolean = true
)

data class MealItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val note: String = "",
    val doneDate: String = "",
    val alarmEnabled: Boolean = true
)

data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val ringtoneUri: String = ""
)

data class MoneyRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val amount: Double,
    val category: String,
    val note: String = "",
    val date: String
)

data class CourseItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val progress: Int = 0,
    val nextTask: String = "",
    val dueDate: String = ""
)

data class AttendanceRecord(
    val date: String,
    val status: String,
    val time: String = "",
    val updatedAt: Long = 0L
)

data class PrayerSettings(
    val enabled: Boolean,
    val latitude: Double,
    val longitude: Double,
    val azanUri: String,
    val vibrateEnabled: Boolean,
    val enabledPrayers: Set<String>
) {
    fun hasLocation(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
}

class GuideStore(context: Context) {
    private val prefs = context.getSharedPreferences("guide_store", Context.MODE_PRIVATE)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a")

    fun hasProfile(): Boolean = prefs.contains("profile_name") && prefs.contains("pin_hash")
    fun profileName(): String = prefs.getString("profile_name", "Guide User") ?: "Guide User"

    fun saveProfile(name: String, pin: String) {
        prefs.edit().putString("profile_name", name.trim()).apply()
        savePin(pin)
    }

    fun setProfileName(name: String) {
        if (name.trim().length >= 2) prefs.edit().putString("profile_name", name.trim()).apply()
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        savePin(newPin)
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val expected = prefs.getString("pin_hash", "") ?: ""
        val salt = prefs.getString("pin_salt", null)
        if (!salt.isNullOrBlank()) return expected == pbkdf2(pin, salt)
        val legacyOk = expected == legacyHash(pin)
        if (legacyOk) savePin(pin)
        return legacyOk
    }

    private fun savePin(pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", pbkdf2(pin, salt)).apply()
    }

    private fun pbkdf2(value: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val spec = PBEKeySpec(value.toCharArray(), saltBytes, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun legacyHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun today(): String = LocalDate.now().format(dateFormatter)

    fun routines(): MutableList<RoutineItem> {
        val array = jsonArray("routines")
        return MutableList(array.length()) { i ->
            val o = array.optJSONObject(i) ?: JSONObject()
            RoutineItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Routine"),
                hour = o.optInt("hour", 8),
                minute = o.optInt("minute", 0),
                category = o.optString("category", "Routine"),
                doneDate = o.optString("doneDate", ""),
                alarmEnabled = o.optBoolean("alarmEnabled", true)
            )
        }
    }

    fun saveRoutines(items: List<RoutineItem>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("hour", item.hour); put("minute", item.minute)
            put("category", item.category); put("doneDate", item.doneDate); put("alarmEnabled", item.alarmEnabled)
        }) }
        putJson("routines", array)
    }

    fun meals(): MutableList<MealItem> {
        val array = jsonArray("meals")
        return MutableList(array.length()) { i ->
            val o = array.optJSONObject(i) ?: JSONObject()
            MealItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Meal"),
                hour = o.optInt("hour", 12),
                minute = o.optInt("minute", 0),
                note = o.optString("note", ""),
                doneDate = o.optString("doneDate", ""),
                alarmEnabled = o.optBoolean("alarmEnabled", true)
            )
        }
    }

    fun saveMeals(items: List<MealItem>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("hour", item.hour); put("minute", item.minute)
            put("note", item.note); put("doneDate", item.doneDate); put("alarmEnabled", item.alarmEnabled)
        }) }
        putJson("meals", array)
    }

    fun alarms(): MutableList<AlarmItem> {
        val array = jsonArray("alarms")
        return MutableList(array.length()) { i ->
            val o = array.optJSONObject(i) ?: JSONObject()
            AlarmItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Alarm"),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                enabled = o.optBoolean("enabled", true),
                soundEnabled = o.optBoolean("soundEnabled", true),
                vibrateEnabled = o.optBoolean("vibrateEnabled", true),
                ringtoneUri = o.optString("ringtoneUri", "")
            )
        }
    }

    fun saveAlarms(items: List<AlarmItem>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("hour", item.hour); put("minute", item.minute)
            put("enabled", item.enabled); put("soundEnabled", item.soundEnabled); put("vibrateEnabled", item.vibrateEnabled)
            put("ringtoneUri", item.ringtoneUri)
        }) }
        putJson("alarms", array)
    }

    fun prayerSettings(): PrayerSettings {
        val defaults = setOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        return PrayerSettings(
            enabled = prefs.getBoolean("prayer_enabled", false),
            latitude = prefs.getString("prayer_lat", "0")?.toDoubleOrNull() ?: 0.0,
            longitude = prefs.getString("prayer_lon", "0")?.toDoubleOrNull() ?: 0.0,
            azanUri = prefs.getString("prayer_azan_uri", "") ?: "",
            vibrateEnabled = prefs.getBoolean("prayer_vibrate", false),
            enabledPrayers = prefs.getStringSet("prayer_enabled_names", null)?.toSet() ?: defaults
        )
    }

    fun setPrayerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("prayer_enabled", enabled).apply()
    }

    fun setPrayerLocation(latitude: Double, longitude: Double) {
        prefs.edit().putString("prayer_lat", latitude.toString()).putString("prayer_lon", longitude.toString()).apply()
    }

    fun setPrayerAzanUri(uri: String) {
        prefs.edit().putString("prayer_azan_uri", uri).apply()
    }

    fun setPrayerVibrate(enabled: Boolean) {
        prefs.edit().putBoolean("prayer_vibrate", enabled).apply()
    }

    fun setPrayerAlarmEnabled(name: String, enabled: Boolean) {
        val values = prayerSettings().enabledPrayers.toMutableSet()
        if (enabled) values.add(name) else values.remove(name)
        prefs.edit().putStringSet("prayer_enabled_names", values).apply()
    }

    fun attendanceFor(date: String = today()): String = attendanceRecord(date).status

    fun attendanceRecord(date: String = today()): AttendanceRecord {
        val details = attendanceDetailsObject().optJSONObject(date)
        if (details != null) {
            return AttendanceRecord(
                date = date,
                status = details.optString("status", "Not marked"),
                time = details.optString("time", ""),
                updatedAt = details.optLong("updatedAt", 0L)
            )
        }
        val legacy = attendanceObject().optString(date, "Not marked")
        return AttendanceRecord(date = date, status = legacy)
    }

    fun setAttendance(status: String, date: String = today(), time: String = LocalTime.now().format(timeFormatter)) {
        val legacy = attendanceObject()
        val details = attendanceDetailsObject()
        if (status == "Not marked") {
            legacy.remove(date)
            details.remove(date)
        } else {
            legacy.put(date, status)
            details.put(date, JSONObject().apply {
                put("status", status)
                put("time", time)
                put("updatedAt", System.currentTimeMillis())
            })
        }
        prefs.edit().putString("attendance", legacy.toString()).putString("attendance_details", details.toString()).apply()
    }

    fun attendanceSummaryForCurrentMonth(): Map<String, Int> {
        val month = LocalDate.now().toString().substring(0, 7)
        val counts = linkedMapOf("Present" to 0, "Absent" to 0, "Leave" to 0)
        val dates = linkedSetOf<String>()
        val legacy = attendanceObject(); legacy.keys().forEach { dates.add(it) }
        val details = attendanceDetailsObject(); details.keys().forEach { dates.add(it) }
        dates.filter { it.startsWith(month) }.forEach { date ->
            val status = attendanceFor(date)
            if (counts.containsKey(status)) counts[status] = (counts[status] ?: 0) + 1
        }
        return counts
    }

    fun attendanceHistory(days: Int = 14): List<AttendanceRecord> = (0 until days).map { offset ->
        val date = LocalDate.now().minusDays(offset.toLong()).format(dateFormatter)
        attendanceRecord(date)
    }

    fun markedAttendanceHistory(days: Int = 60): List<AttendanceRecord> = attendanceHistory(days)
        .filter { it.status != "Not marked" }
        .sortedWith(compareByDescending<AttendanceRecord> { it.date }.thenByDescending { it.updatedAt })

    fun moneyRecords(): MutableList<MoneyRecord> {
        val array = jsonArray("money_records")
        val items = mutableListOf<MoneyRecord>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            items += MoneyRecord(
                id = o.optString("id", UUID.randomUUID().toString()),
                type = o.optString("type", "Expense"),
                amount = o.optDouble("amount", 0.0),
                category = o.optString("category", "General"),
                note = o.optString("note", ""),
                date = o.optString("date", today())
            )
        }
        return items.sortedByDescending { it.date }.toMutableList()
    }

    fun saveMoneyRecords(items: List<MoneyRecord>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("type", item.type); put("amount", item.amount); put("category", item.category)
            put("note", item.note); put("date", item.date)
        }) }
        putJson("money_records", array)
    }

    fun currentMonthMoneySummary(): Triple<Double, Double, Double> {
        val month = LocalDate.now().toString().substring(0, 7)
        val current = moneyRecords().filter { it.date.startsWith(month) }
        val income = current.filter { it.type == "Income" }.sumOf { it.amount }
        val expense = current.filter { it.type == "Expense" }.sumOf { it.amount }
        return Triple(income, expense, income - expense)
    }

    fun courses(): MutableList<CourseItem> {
        val array = jsonArray("courses")
        val items = mutableListOf<CourseItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            items += CourseItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Course"),
                progress = o.optInt("progress", 0).coerceIn(0, 100),
                nextTask = o.optString("nextTask", ""),
                dueDate = o.optString("dueDate", "")
            )
        }
        return items
    }

    fun saveCourses(items: List<CourseItem>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("progress", item.progress.coerceIn(0, 100))
            put("nextTask", item.nextTask); put("dueDate", item.dueDate)
        }) }
        putJson("courses", array)
    }

    fun waterCount(): Int {
        val savedDate = prefs.getString("water_date", "")
        return if (savedDate == today()) prefs.getInt("water_count", 0) else 0
    }

    fun addWater(): Int {
        val next = (waterCount() + 1).coerceAtMost(16)
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
        var cursor = LocalDate.now(); var count = 0
        while (dates.contains(cursor.format(dateFormatter))) { count++; cursor = cursor.minusDays(1) }
        return count
    }

    fun seedDefaultsIfNeeded() {
        if (!prefs.getBoolean("seeded", false)) {
            saveRoutines(listOf(
                RoutineItem(title = "Morning planning", hour = 7, minute = 30, category = "Focus"),
                RoutineItem(title = "Evening review", hour = 21, minute = 0, category = "Reflection")
            ))
            saveMeals(listOf(
                MealItem(title = "Breakfast", hour = 8, minute = 0, note = "Start with water and a balanced meal"),
                MealItem(title = "Lunch", hour = 13, minute = 30, note = "Keep portions balanced"),
                MealItem(title = "Dinner", hour = 20, minute = 0, note = "Prefer a lighter meal")
            ))
            prefs.edit().putBoolean("seeded", true).apply()
        }
        if (!prefs.getBoolean("v2_seeded", false)) {
            if (alarms().isEmpty()) saveAlarms(listOf(AlarmItem(title = "Wake up", hour = 6, minute = 30, enabled = false)))
            prefs.edit().putBoolean("v2_seeded", true).apply()
        }
    }

    fun exportJson(): String = JSONObject().apply {
        val prayer = prayerSettings()
        put("app", "Guide")
        put("version", 4)
        put("profileName", profileName())
        put("routines", jsonArray("routines"))
        put("meals", jsonArray("meals"))
        put("alarms", jsonArray("alarms"))
        put("attendance", attendanceObject())
        put("attendanceDetails", attendanceDetailsObject())
        put("moneyRecords", jsonArray("money_records"))
        put("courses", jsonArray("courses"))
        put("prayer", JSONObject().apply {
            put("enabled", prayer.enabled)
            put("latitude", prayer.latitude)
            put("longitude", prayer.longitude)
            put("azanUri", prayer.azanUri)
            put("vibrateEnabled", prayer.vibrateEnabled)
            put("enabledPrayers", JSONArray(prayer.enabledPrayers.toList()))
        })
    }.toString(2)

    private fun jsonArray(key: String): JSONArray {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun putJson(key: String, array: JSONArray) {
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun attendanceObject(): JSONObject {
        val raw = prefs.getString("attendance", "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun attendanceDetailsObject(): JSONObject {
        val raw = prefs.getString("attendance_details", "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }
}
