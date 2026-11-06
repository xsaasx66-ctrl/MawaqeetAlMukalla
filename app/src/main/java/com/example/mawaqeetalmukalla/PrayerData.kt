package com.example.mawaqeetalmukalla

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class PrayerEvent(
    val prayerKey: String,
    val title: String,
    val countdownLabel: String,
    val targetMillis: Long,
    val isIqama: Boolean
)

object PrayerData {
    const val TIME_ZONE = "Asia/Aden"
    val keys = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val names = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
    private val defaultIqama = listOf(20, 15, 15, 10, 15)

    fun todayDate(): String {
        val zone = TimeZone.getTimeZone(TIME_ZONE)
        return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { timeZone = zone }
            .format(Calendar.getInstance(zone).time)
    }

    fun defaultIqama(index: Int): Int = defaultIqama[index.coerceIn(0, 4)]
    fun keyIndex(key: String): Int = keys.indexOf(key).coerceAtLeast(0)

    fun nextEvent(
        times: Map<String, String>,
        iqamaMinutes: (String) -> Int,
        nowMillis: Long = System.currentTimeMillis()
    ): PrayerEvent? {
        if (keys.any { times[it] == null }) return null
        val adhanTimes = keys.map { timeAtOffset(times.getValue(it), nowMillis, 0) }

        for (index in keys.indices) {
            val key = keys[index]
            val iqamaEnd = adhanTimes[index] + iqamaMinutes(key).coerceIn(1, 90) * 60_000L
            if (nowMillis >= adhanTimes[index] && nowMillis < iqamaEnd) {
                return PrayerEvent(key, "بعد أذان ${names[index]}", "الوقت المتبقي للإقامة", iqamaEnd, true)
            }
        }

        for (index in keys.indices) {
            if (adhanTimes[index] > nowMillis) {
                return PrayerEvent(keys[index], "الصلاة القادمة: ${names[index]}", "الوقت المتبقي للأذان", adhanTimes[index], false)
            }
        }

        return PrayerEvent(
            "Fajr", "الصلاة القادمة: الفجر", "الوقت المتبقي للأذان",
            timeAtOffset(times.getValue("Fajr"), nowMillis, 1), false
        )
    }

    fun arabicDigits(value: String): String {
        val digits = "٠١٢٣٤٥٦٧٨٩"
        return buildString(value.length) {
            value.forEach { char -> append(if (char in '0'..'9') digits[char - '0'] else char) }
        }
    }

    private fun timeAtOffset(value: String, nowMillis: Long, dayOffset: Int): Long {
        val parts = value.split(":")
        require(parts.size == 2)
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        require(hour in 0..23 && minute in 0..59)
        return Calendar.getInstance(TimeZone.getTimeZone(TIME_ZONE)).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
