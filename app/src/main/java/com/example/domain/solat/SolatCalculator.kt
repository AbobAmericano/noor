package com.example.domain.solat

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class CalculationMethod(
    val aladhanMethodId: Int,
    val label: String,
    val shortLabel: String,
    val fajrAngle: Double,
    val ishaAngle: Double?,
    val ishaMinutesAfterMaghrib: Int? = null
) {
    JAKIM(3, "JAKIM Malaysia (20° / 18°)", "JAKIM", 20.0, 18.0),
    MWL(3, "Muslim World League (18° / 17°)", "MWL", 18.0, 17.0),
    ISNA(2, "ISNA North America (15° / 15°)", "ISNA", 15.0, 15.0),
    MAKKAH(4, "Umm al-Qura Makkah (18.5° / 90m)", "Makkah", 18.5, null, 90),
    EGYPT(5, "Egyptian General Authority (19.5° / 17.5°)", "Egypt", 19.5, 17.5),
    SINGAPORE(11, "MUIS Singapore (20° / 18°)", "MUIS", 20.0, 18.0),
    KARACHI(1, "Univ. Islamic Sciences Karachi (18° / 18°)", "Karachi", 18.0, 18.0);

    companion object {
        fun fromName(name: String): CalculationMethod =
            entries.firstOrNull { it.name == name } ?: JAKIM
    }
}

data class SolatTime(val hour: Int, val minute: Int) : Comparable<SolatTime> {
    val totalMinutes: Int get() = hour * 60 + minute

    override fun compareTo(other: SolatTime): Int = totalMinutes.compareTo(other.totalMinutes)

    fun format12Hour(): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.US, "%d:%02d %s", displayHour, minute, amPm)
    }

    fun format24Hour(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    companion object {
        fun parseHHmm(raw: String): SolatTime? {
            val clean = raw.trim().substringBefore(" ")
            val parts = clean.split(":")
            if (parts.size < 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return SolatTime(h.coerceIn(0, 23), m.coerceIn(0, 59))
        }
    }
}

data class SolatTimeEntry(
    val name: String,
    val arabicName: String,
    val time: SolatTime,
    val isObligatory: Boolean = true
)

data class SolatSchedule(
    val dateKey: String,
    val gregorianLabel: String,
    val hijriLabel: String,
    val timeZoneId: String,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val method: CalculationMethod,
    val entries: List<SolatTimeEntry>,
    val sourceLabel: String = "Local Astronomical Engine"
) {
    fun nextPrayer(nowMinutes: Int): Pair<SolatTimeEntry, Int> {
        val obligatory = entries.filter { it.isObligatory }
        val upcoming = obligatory.firstOrNull { it.time.totalMinutes > nowMinutes }
        return if (upcoming != null) {
            upcoming to (upcoming.time.totalMinutes - nowMinutes)
        } else {
            val tomorrowFajr = obligatory.first()
            tomorrowFajr to ((24 * 60 - nowMinutes) + tomorrowFajr.time.totalMinutes)
        }
    }
}

object SolatCalculator {

    private const val KAABA_LAT_DEG = 21.422487
    private const val KAABA_LON_DEG = 39.826206

    fun qiblaBearingDegrees(latitude: Double, longitude: Double): Double {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(KAABA_LAT_DEG)
        val lon2 = Math.toRadians(KAABA_LON_DEG)
        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg % 360.0) + 360.0) % 360.0
    }

    fun distanceToKaabaKm(latitude: Double, longitude: Double): Int {
        val r = 6371.0
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(KAABA_LAT_DEG)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(KAABA_LON_DEG - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).roundToInt()
    }

    fun todayDateKey(timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone)
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatGregorianDate(cal: Calendar): String {
        val monthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val dow = dayNames[(cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]
        val mon = monthNames[cal.get(Calendar.MONTH).coerceIn(0, 11)]
        return "$dow, ${cal.get(Calendar.DAY_OF_MONTH)} $mon ${cal.get(Calendar.YEAR)}"
    }

    /**
     * Tabular Islamic (Kuwaiti/Hijri) calendar calculation compatible down to API 24.
     */
    fun formatHijriDate(cal: Calendar): String {
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)

        val jd = floor(1461.0 * (year + 4800 + (month - 14) / 12) / 4.0) +
            floor(367.0 * (month - 2 - 12 * ((month - 14) / 12)) / 12.0) -
            floor(3.0 * ((year + 4900 + (month - 14) / 12) / 100) / 4.0) +
            day - 32075

        val l = jd - 1948440 + 10632
        val n = floor((l - 1) / 10631.0)
        val l1 = l - 10631 * n + 354
        val j = (floor((10985 - l1) / 5316.0)) * (floor((50 * l1) / 17719.0)) +
            (floor(l1 / 5670.0)) * (floor((43 * l1) / 15238.0))
        val l2 = l1 - (floor((30 - j) / 15.0)) * (floor((17719 * j) / 50.0)) -
            (floor(j / 16.0)) * (floor((15238 * j) / 43.0)) + 29
        val hMonth = floor((24 * l2) / 709.0).toInt()
        val hDay = (l2 - floor((709 * hMonth) / 24.0)).toInt()
        val hYear = (30 * n + j - 30).toInt()

        val hijriMonths = arrayOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
        val mName = hijriMonths.getOrElse(hMonth - 1) { "Hijri" }
        return "$hDay $mName $hYear AH"
    }

    fun calculateDaySchedule(
        calendar: Calendar,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        locationLabel: String,
        method: CalculationMethod
    ): SolatSchedule {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val jd = julianDay(year, month, day) - longitude / (15.0 * 24.0)
        val tzOffsetHours = timeZone.getOffset(calendar.timeInMillis) / 3600000.0

        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = asinDeg(sinDeg(e) * sinDeg(l))

        val dhuhr = 12.0 + tzOffsetHours - longitude / 15.0 - eqt
        val sunrise = dhuhr - hourAngle(latitude, decl, 0.833)
        val sunset = dhuhr + hourAngle(latitude, decl, 0.833)
        val fajr = dhuhr - hourAngle(latitude, decl, method.fajrAngle)
        val asr = dhuhr + asrHourAngle(latitude, decl, 1.0)
        val isha = if (method.ishaMinutesAfterMaghrib != null) {
            sunset + method.ishaMinutesAfterMaghrib / 60.0
        } else {
            dhuhr + hourAngle(latitude, decl, method.ishaAngle ?: 17.0)
        }

        val entries = listOf(
            SolatTimeEntry("Fajr", "الفجر", hoursToSolatTime(fajr), isObligatory = true),
            SolatTimeEntry("Syuruk", "الشروق", hoursToSolatTime(sunrise), isObligatory = false),
            SolatTimeEntry("Dhuhr", "الظهر", hoursToSolatTime(dhuhr + 2.0 / 60.0), isObligatory = true),
            SolatTimeEntry("Asr", "العصر", hoursToSolatTime(asr), isObligatory = true),
            SolatTimeEntry("Maghrib", "المغرب", hoursToSolatTime(sunset + 1.0 / 60.0), isObligatory = true),
            SolatTimeEntry("Isha", "العشاء", hoursToSolatTime(isha), isObligatory = true)
        )

        val dateKey = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        return SolatSchedule(
            dateKey = dateKey,
            gregorianLabel = formatGregorianDate(calendar),
            hijriLabel = formatHijriDate(calendar),
            timeZoneId = timeZone.id,
            locationLabel = locationLabel,
            latitude = latitude,
            longitude = longitude,
            method = method,
            entries = entries,
            sourceLabel = "Astronomical Engine (${timeZone.id})"
        )
    }

    fun inferDefaultJakimZone(latitude: Double, longitude: Double): String? {
        // Rough bounding box for Malaysia (Peninsular + Sabah/Sarawak)
        if (latitude in 0.8..7.5 && longitude in 99.5..119.5) {
            return when {
                latitude in 2.8..3.4 && longitude in 101.4..101.9 -> "WLY01" // KL / Putrajaya
                latitude in 1.2..2.4 && longitude in 102.5..104.5 -> "JHR02" // Johor Bahru
                latitude in 5.1..5.6 && longitude in 100.1..100.6 -> "PNG01" // Penang
                latitude in 5.8..6.3 && longitude in 115.9..116.3 -> "SBH07" // Kota Kinabalu
                latitude in 1.3..1.7 && longitude in 110.1..110.6 -> "SWK08" // Kuching
                else -> "SGR01" // Selangor / Central Malaysia
            }
        }
        return null
    }

    private fun hourAngle(lat: Double, decl: Double, angle: Double): Double {
        val term = (-sinDeg(angle) - sinDeg(lat) * sinDeg(decl)) / (cosDeg(lat) * cosDeg(decl))
        val clamped = term.coerceIn(-0.9999, 0.9999)
        return acosDeg(clamped) / 15.0
    }

    private fun asrHourAngle(lat: Double, decl: Double, shadowFactor: Double): Double {
        val target = atanDeg(1.0 / (shadowFactor + tan(Math.toRadians(abs(lat - decl)))))
        val term = (sinDeg(target) - sinDeg(lat) * sinDeg(decl)) / (cosDeg(lat) * cosDeg(decl))
        val clamped = term.coerceIn(-0.9999, 0.9999)
        return acosDeg(clamped) / 15.0
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun hoursToSolatTime(hours: Double): SolatTime {
        val normalized = ((hours % 24.0) + 24.0) % 24.0
        val totalMinutes = (normalized * 60.0).roundToInt() % (24 * 60)
        return SolatTime(totalMinutes / 60, totalMinutes % 60)
    }

    private fun fixAngle(a: Double): Double = ((a % 360.0) + 360.0) % 360.0
    private fun fixHour(a: Double): Double = ((a % 24.0) + 24.0) % 24.0
    private fun sinDeg(d: Double): Double = sin(d * PI / 180.0)
    private fun cosDeg(d: Double): Double = cos(d * PI / 180.0)
    private fun asinDeg(x: Double): Double = asin(x.coerceIn(-1.0, 1.0)) * 180.0 / PI
    private fun acosDeg(x: Double): Double = acos(x.coerceIn(-1.0, 1.0)) * 180.0 / PI
    private fun atanDeg(x: Double): Double = atan(x) * 180.0 / PI
    private fun atan2Deg(y: Double, x: Double): Double = atan2(y, x) * 180.0 / PI
}
