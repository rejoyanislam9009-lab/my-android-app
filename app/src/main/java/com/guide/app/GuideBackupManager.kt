package com.guide.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object GuideBackupManager {
    private const val PREFS = "guide_backup"
    private const val AUTO_ENABLED = "auto_enabled"
    private const val LAST_BACKUP_AT = "last_backup_at"
    private const val KEEP_DAYS = 14

    data class BackupStatus(
        val enabled: Boolean,
        val lastBackupAt: Long,
        val count: Int,
        val latestName: String?
    )

    fun isAutoEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO_ENABLED, true)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUTO_ENABLED, enabled).apply()
    }

    fun status(context: Context): BackupStatus {
        val files = backupFiles(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return BackupStatus(
            enabled = isAutoEnabled(context),
            lastBackupAt = prefs.getLong(LAST_BACKUP_AT, 0L),
            count = files.size,
            latestName = files.firstOrNull()?.name
        )
    }

    fun autoBackupIfNeeded(context: Context, store: GuideStore, force: Boolean = false): File? {
        if (!isAutoEnabled(context) && !force) return null
        val day = LocalDate.now().toString()
        val dir = backupDir(context)
        val file = File(dir, "guide-auto-$day.json")
        if (force || !file.exists() || file.readText() != store.exportJson()) {
            file.writeText(store.exportJson())
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(LAST_BACKUP_AT, System.currentTimeMillis())
                .apply()
        }
        trimOldBackups(context)
        return file
    }

    fun backupFiles(context: Context): List<File> = backupDir(context)
        .listFiles { file -> file.isFile && file.name.endsWith(".json") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    fun latestBackup(context: Context): File? = backupFiles(context).firstOrNull()

    fun writeManualBackup(context: Context, store: GuideStore, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
            writer.write(store.exportJson())
        } ?: error("Unable to open backup destination")
    }

    fun restoreFromUri(context: Context, store: GuideStore, uri: Uri): Boolean {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return false
        return restoreJson(store, json)
    }

    fun restoreLatestLocal(context: Context, store: GuideStore): Boolean {
        val file = latestBackup(context) ?: return false
        return restoreJson(store, file.readText())
    }

    fun restoreJson(store: GuideStore, raw: String): Boolean = runCatching {
        val root = JSONObject(raw)
        require(root.optString("app") == "Guide") { "Not a Guide backup" }

        root.optString("profileName").takeIf { it.isNotBlank() }?.let(store::setProfileName)

        val routines = mutableListOf<RoutineItem>()
        root.optJSONArray("routines")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                routines += RoutineItem(
                    id = o.optString("id"), title = o.optString("title", "Routine"),
                    hour = o.optInt("hour", 8), minute = o.optInt("minute", 0),
                    category = o.optString("category", "Routine"), doneDate = o.optString("doneDate", ""),
                    alarmEnabled = o.optBoolean("alarmEnabled", true)
                )
            }
        }
        if (root.has("routines")) store.saveRoutines(routines)

        val meals = mutableListOf<MealItem>()
        root.optJSONArray("meals")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                meals += MealItem(
                    id = o.optString("id"), title = o.optString("title", "Meal"),
                    hour = o.optInt("hour", 12), minute = o.optInt("minute", 0),
                    note = o.optString("note", ""), doneDate = o.optString("doneDate", ""),
                    alarmEnabled = o.optBoolean("alarmEnabled", true)
                )
            }
        }
        if (root.has("meals")) store.saveMeals(meals)

        val alarms = mutableListOf<AlarmItem>()
        root.optJSONArray("alarms")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                alarms += AlarmItem(
                    id = o.optString("id"), title = o.optString("title", "Alarm"),
                    hour = o.optInt("hour", 7), minute = o.optInt("minute", 0),
                    enabled = o.optBoolean("enabled", true), soundEnabled = o.optBoolean("soundEnabled", true),
                    vibrateEnabled = o.optBoolean("vibrateEnabled", true), ringtoneUri = o.optString("ringtoneUri", "")
                )
            }
        }
        if (root.has("alarms")) store.saveAlarms(alarms)

        val money = mutableListOf<MoneyRecord>()
        root.optJSONArray("moneyRecords")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                money += MoneyRecord(
                    id = o.optString("id"), type = o.optString("type", "Expense"),
                    amount = o.optDouble("amount", 0.0), category = o.optString("category", "General"),
                    note = o.optString("note", ""), date = o.optString("date", LocalDate.now().toString())
                )
            }
        }
        if (root.has("moneyRecords")) store.saveMoneyRecords(money)

        val courses = mutableListOf<CourseItem>()
        root.optJSONArray("courses")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                courses += CourseItem(
                    id = o.optString("id"), title = o.optString("title", "Course"),
                    progress = o.optInt("progress", 0), nextTask = o.optString("nextTask", ""),
                    dueDate = o.optString("dueDate", "")
                )
            }
        }
        if (root.has("courses")) store.saveCourses(courses)

        val attendance = root.optJSONObject("attendanceDetails")
        attendance?.keys()?.forEach { date ->
            val o = attendance.optJSONObject(date) ?: return@forEach
            val status = o.optString("status", "Not marked")
            val time = o.optString("time", "")
            store.setAttendance(status, date, time.ifBlank { "Restored" })
        }

        root.optJSONObject("prayer")?.let { p ->
            store.setPrayerEnabled(p.optBoolean("enabled", false))
            val lat = p.optDouble("latitude", 0.0)
            val lon = p.optDouble("longitude", 0.0)
            if (lat in -90.0..90.0 && lon in -180.0..180.0) store.setPrayerLocation(lat, lon)
            store.setPrayerAzanUri(p.optString("azanUri", ""))
            store.setPrayerVibrate(p.optBoolean("vibrateEnabled", false))
            val enabled = p.optJSONArray("enabledPrayers") ?: JSONArray()
            val names = setOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
            names.forEach { name ->
                var found = false
                for (i in 0 until enabled.length()) if (enabled.optString(i) == name) found = true
                store.setPrayerAlarmEnabled(name, found)
            }
        }
        true
    }.getOrDefault(false)

    fun formattedLastBackup(context: Context): String {
        val millis = status(context).lastBackupAt
        if (millis <= 0) return "এখনও ব্যাকআপ হয়নি"
        return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy • hh:mm a"))
    }

    private fun backupDir(context: Context): File = File(context.filesDir, "guide_backups").apply { mkdirs() }

    private fun trimOldBackups(context: Context) {
        backupFiles(context).drop(KEEP_DAYS).forEach { runCatching { it.delete() } }
    }
}
