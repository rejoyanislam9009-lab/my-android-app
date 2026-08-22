package com.guide.app

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class PrayerMoment(
    val key: String,
    val nameBn: String,
    val time: LocalTime
)

object PrayerTimeCalculator {
    private const val FAJR_ANGLE = -18.5
    private const val SUNSET_ANGLE = -0.833

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<PrayerMoment> {
        val jd = julianDate(date)
        val d = jd - 2451545.0
        val g = radians(normalizeDegrees(357.529 + 0.98560028 * d))
        val q = normalizeDegrees(280.459 + 0.98564736 * d)
        val l = radians(normalizeDegrees(q + 1.915 * sin(g) + 0.020 * sin(2 * g)))
        val e = radians(23.439 - 0.00000036 * d)

        val ra = normalizeHours(degrees(atan2(cos(e) * sin(l), cos(l))) / 15.0)
        val eqTime = q / 15.0 - ra
        val decl = asin(sin(e) * sin(l))
        val lat = radians(latitude.coerceIn(-89.5, 89.5))
        val timezone = date.atStartOfDay(zoneId).offset.totalSeconds / 3600.0
        val noon = 12.0 + timezone - longitude / 15.0 - eqTime

        val fajr = noon - hourAngle(FAJR_ANGLE, lat, decl)
        val sunrise = noon - hourAngle(SUNSET_ANGLE, lat, decl)
        val dhuhr = noon
        val asrAltitude = degrees(atan(1.0 / (1.0 + tan(abs(lat - decl)))))
        val asr = noon + hourAngle(asrAltitude, lat, decl)
        val maghrib = noon + hourAngle(SUNSET_ANGLE, lat, decl)
        val isha = maghrib + 1.5

        return listOf(
            PrayerMoment("Fajr", "ফজর", decimalToTime(fajr)),
            PrayerMoment("Sunrise", "সূর্যোদয়", decimalToTime(sunrise)),
            PrayerMoment("Dhuhr", "যোহর", decimalToTime(dhuhr)),
            PrayerMoment("Asr", "আসর", decimalToTime(asr)),
            PrayerMoment("Maghrib", "মাগরিব", decimalToTime(maghrib)),
            PrayerMoment("Isha", "এশা", decimalToTime(isha))
        )
    }

    private fun hourAngle(altitudeDegrees: Double, latitude: Double, declination: Double): Double {
        val altitude = radians(altitudeDegrees)
        val denominator = cos(latitude) * cos(declination)
        if (abs(denominator) < 1e-9) return 0.0
        val value = ((sin(altitude) - sin(latitude) * sin(declination)) / denominator).coerceIn(-1.0, 1.0)
        return degrees(acos(value)) / 15.0
    }

    private fun julianDate(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        val day = date.dayOfMonth.toDouble()
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun decimalToTime(value: Double): LocalTime {
        val normalized = normalizeHours(value)
        val totalMinutes = ((normalized * 60.0) + 0.5).toInt() % (24 * 60)
        return LocalTime.of(totalMinutes / 60, totalMinutes % 60)
    }

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    private fun normalizeHours(value: Double): Double = ((value % 24.0) + 24.0) % 24.0
    private fun radians(value: Double): Double = value * PI / 180.0
    private fun degrees(value: Double): Double = value * 180.0 / PI
}

object PrayerScheduler {
    private val alarmNames = setOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    fun scheduleAll(context: Context, store: GuideStore = GuideStore(context)) {
        val settings = store.prayerSettings()
        alarmNames.forEach { name ->
            if (!settings.enabled || !settings.hasLocation() || !settings.enabledPrayers.contains(name)) {
                ReminderScheduler.cancel(context, "prayer:$name")
            } else {
                schedulePrayer(context, name, store)
            }
        }
    }

    fun schedulePrayer(context: Context, name: String, store: GuideStore = GuideStore(context)) {
        val settings = store.prayerSettings()
        if (!settings.enabled || !settings.hasLocation() || !settings.enabledPrayers.contains(name)) {
            ReminderScheduler.cancel(context, "prayer:$name")
            return
        }

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var date = now.toLocalDate()
        var moment = PrayerTimeCalculator.calculate(date, settings.latitude, settings.longitude, zone)
            .firstOrNull { it.key == name } ?: return
        var target = LocalDateTime.of(date, moment.time)
        if (!target.isAfter(now)) {
            date = date.plusDays(1)
            moment = PrayerTimeCalculator.calculate(date, settings.latitude, settings.longitude, zone)
                .firstOrNull { it.key == name } ?: return
            target = LocalDateTime.of(date, moment.time)
        }

        ReminderScheduler.scheduleOneShot(
            context = context,
            key = "prayer:$name",
            title = "${moment.nameBn} নামাজের সময়",
            body = "নামাজের সময় হয়েছে • আজান",
            triggerAt = target.atZone(zone).toInstant().toEpochMilli(),
            ringtoneUri = settings.azanUri,
            soundEnabled = true,
            vibrateEnabled = settings.vibrateEnabled,
            prayerName = name,
            showAsAlarmClock = true
        )
    }

    fun nextPrayer(context: Context, store: GuideStore = GuideStore(context)): Pair<PrayerMoment, LocalDateTime>? {
        val settings = store.prayerSettings()
        if (!settings.enabled || !settings.hasLocation()) return null
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        repeat(2) { dayOffset ->
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val times = PrayerTimeCalculator.calculate(date, settings.latitude, settings.longitude, zone)
                .filter { it.key in alarmNames && settings.enabledPrayers.contains(it.key) }
            times.forEach { prayer ->
                val target = LocalDateTime.of(date, prayer.time)
                if (target.isAfter(now)) return prayer to target
            }
        }
        return null
    }
}
