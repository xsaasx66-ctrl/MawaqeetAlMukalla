package com.example.mawaqeetalmukalla

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object PrayerRepository {
    private const val TAG = "PrayerRepository"
    private const val LAT = 14.5425
    private const val LON = 49.1242
    private const val METHOD = 3 // MWL; compare with a trusted local mosque calendar.
    private const val PREFS = "prayer_data"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000
    private val timePattern = Regex("^\\d{2}:\\d{2}$")

    fun needsRefresh(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.getString("date", null) != PrayerData.todayDate()) return true
        return PrayerData.keys.any { !isValidTime(prefs.getString(it, null)) }
    }

    fun refresh(context: Context): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val date = PrayerData.todayDate()
            val endpoint = "https://api.aladhan.com/v1/timings/$date" +
                "?latitude=$LAT&longitude=$LON&method=$METHOD&school=0"
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode !in 200..299) return false
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("code", -1) != 200) return false

            val timings = root.getJSONObject("data").getJSONObject("timings")
            val parsed = PrayerData.keys.associateWith { key ->
                val raw = timings.getString(key).substringBefore(' ').trim()
                require(Regex("^\\d{1,2}:\\d{2}$").matches(raw)) { "Invalid time for $key" }
                val parts = raw.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                require(hour in 0..23 && minute in 0..59) { "Out-of-range time for $key" }
                String.format(Locale.US, "%02d:%02d", hour, minute)
            }
            require(parsed.size == PrayerData.keys.size)

            prefs(context).edit().apply {
                parsed.forEach { (key, value) -> putString(key, value) }
                putString("date", date)
                putLong("updated_at", System.currentTimeMillis())
            }.apply()
            true
        } catch (error: Exception) {
            Log.w(TAG, "Could not refresh prayer times; existing cache is preserved", error)
            false
        } finally {
            connection?.disconnect()
        }
    }

    fun storedTimes(context: Context): Map<String, String> {
        val preferences = prefs(context)
        return PrayerData.keys.mapNotNull { key ->
            preferences.getString(key, null)?.takeIf(::isValidTime)?.let { key to it }
        }.toMap()
    }

    fun lastUpdated(context: Context): Long = prefs(context).getLong("updated_at", 0L)

    fun iqamaMinutes(context: Context, key: String): Int {
        val default = PrayerData.defaultIqama(PrayerData.keyIndex(key))
        return prefs(context).getInt("iqama_$key", default).coerceIn(1, 90)
    }

    fun setIqama(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt("iqama_$key", value.coerceIn(1, 90)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun isValidTime(value: String?): Boolean {
        if (value == null || !timePattern.matches(value)) return false
        val parts = value.split(":")
        val hour = parts[0].toIntOrNull() ?: return false
        val minute = parts[1].toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
    }
}
