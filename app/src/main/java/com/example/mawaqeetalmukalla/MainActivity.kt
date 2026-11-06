package com.example.mawaqeetalmukalla

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private val green = 0xFF163D32.toInt()
    private val pale = 0xFFEAF3ED.toInt()
    private val ink = 0xFF19352D.toInt()
    private val muted = 0xFF6B7D75.toInt()
    private val gold = 0xFFE2B85E.toInt()
    private val page = 0xFFF3F6F2.toInt()

    private lateinit var nextTitle: TextView
    private lateinit var countdownLabel: TextView
    private lateinit var countdown: Chronometer
    private lateinit var status: TextView
    private lateinit var refreshButton: Button
    private val timeViews = mutableMapOf<String, TextView>()
    private val iqamaInputs = mutableMapOf<String, EditText>()
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        // Retire the 15-minute polling job used by version 1.0.
        WorkManager.getInstance(applicationContext).cancelUniqueWork("prayer_times_refresh")
        setContentView(buildScreen())
        render()
        if (PrayerRepository.needsRefresh(applicationContext)) refreshTimes(false)
    }

    override fun onResume() {
        super.onResume()
        if (::countdown.isInitialized && !refreshing) render()
    }

    override fun onPause() {
        if (::countdown.isInitialized) countdown.stop()
        super.onPause()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(page)
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(content)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(16))
        }
        header.addView(label("مواقيت المكلا", 28f, green, true))
        header.addView(label("المكلا · حضرموت", 15f, muted))
        header.addView(label(todayLabel(), 13f, muted).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(header)

        val nextCard = card(green)
        nextCard.setPadding(dp(20), dp(18), dp(20), dp(18))
        nextCard.addView(label("الحدث القادم", 13f, 0xFFC8DAD0.toInt()))
        nextTitle = label("جارٍ تحميل المواقيت", 20f, 0xFFFFFFFF.toInt(), true).apply {
            setPadding(0, dp(8), 0, dp(2))
        }
        nextCard.addView(nextTitle)
        countdown = Chronometer(this).apply {
            textSize = 36f
            setTextColor(gold)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            format = "%s"
            setCountDown(true)
            gravity = Gravity.START
            setPadding(0, dp(5), 0, 0)
        }
        nextCard.addView(countdown)
        countdownLabel = label("الوقت المتبقي للأذان", 13f, 0xFFC8DAD0.toInt())
        nextCard.addView(countdownLabel)
        content.addView(nextCard, bottomMargin(dp(16)))

        val timesCard = card(0xFFFFFFFF.toInt())
        timesCard.addView(sectionTitle("مواقيت اليوم", "مواعيد الصلاة حسب توقيت المكلا"))
        PrayerData.keys.forEachIndexed { index, key ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = rounded(if (index % 2 == 0) 0xFFF5F8F5.toInt() else 0xFFFFFFFF.toInt(), 12)
            }
            row.addView(label(PrayerData.names[index], 15f, ink, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val time = label("--:--", 17f, green, true).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT }
            timeViews[key] = time
            row.addView(time)
            timesCard.addView(row, bottomMargin(dp(5)))
        }
        content.addView(timesCard, bottomMargin(dp(16)))

        val refreshCard = card(0xFFFFFFFF.toInt())
        status = label("المواقيت تحفظ على الجهاز للعمل دون اتصال بعد تحديثها.", 13f, muted)
        refreshCard.addView(status)
        refreshButton = Button(this).apply {
            text = "تحديث المواقيت الآن"
            isAllCaps = false
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(green, 12)
            setPadding(dp(14), dp(5), dp(14), dp(5))
            setOnClickListener { refreshTimes(true) }
        }
        refreshCard.addView(refreshButton, topMargin(dp(10)))
        content.addView(refreshCard, bottomMargin(dp(16)))

        val settingsCard = card(0xFFFFFFFF.toInt())
        settingsCard.addView(sectionTitle("إعدادات الإقامة", "اضبط المدة من الأذان إلى الإقامة لكل صلاة بالدقائق"))
        PrayerData.keys.forEachIndexed { index, key ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
                setPadding(0, dp(5), 0, dp(5))
            }
            row.addView(label(PrayerData.names[index], 15f, ink),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val field = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                textSize = 16f
                setTextColor(green)
                gravity = Gravity.CENTER
                setEms(4)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = rounded(pale, 10)
                setText(PrayerRepository.iqamaMinutes(this@MainActivity, key).toString())
            }
            iqamaInputs[key] = field
            row.addView(field)
            row.addView(label("دقيقة", 12f, muted).apply { setPadding(dp(7), 0, 0, 0) })
            settingsCard.addView(row)
        }
        val save = Button(this).apply {
            text = "حفظ إعدادات الإقامة"
            isAllCaps = false
            textSize = 14f
            setTextColor(green)
            background = rounded(pale, 12)
            setOnClickListener { saveIqamaSettings() }
        }
        settingsCard.addView(save, topMargin(dp(12)))
        content.addView(settingsCard, bottomMargin(dp(16)))
        content.addView(label(
            "ملاحظة: طريقة الحساب الحالية MWL. يُنصح بمطابقة المواقيت مع تقويم مسجد موثوق في المكلا.",
            12f, muted
        ).apply { setPadding(dp(4), dp(2), dp(4), 0) })
        return scroll
    }

    private fun render() {
        val times = PrayerRepository.storedTimes(applicationContext)
        PrayerData.keys.forEach { key ->
            timeViews[key]?.text = times[key]?.let(PrayerData::arabicDigits) ?: "--:--"
        }
        val event = PrayerData.nextEvent(times, { key -> PrayerRepository.iqamaMinutes(applicationContext, key) })
        if (event == null) {
            nextTitle.text = "لا توجد مواقيت محفوظة"
            countdownLabel.text = "حدّث المواقيت عند توفر الإنترنت"
            countdown.stop()
            countdown.text = "--:--"
        } else {
            nextTitle.text = event.title
            countdownLabel.text = event.countdownLabel
            val remaining = (event.targetMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            countdown.base = SystemClock.elapsedRealtime() + remaining
            countdown.setCountDown(true)
            countdown.start()
        }
        val updated = PrayerRepository.lastUpdated(applicationContext)
        status.text = if (updated == 0L) {
            "لم تُحدّث المواقيت بعد. يلزم اتصال بالإنترنت لأول تحميل."
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone(PrayerData.TIME_ZONE) }.format(Date(updated))
            "آخر تحديث: ${PrayerData.arabicDigits(time)} · المواقيت محفوظة للعمل دون اتصال"
        }
        refreshButton.isEnabled = !refreshing
        refreshButton.alpha = if (refreshing) 0.55f else 1f
    }

    private fun refreshTimes(showToast: Boolean) {
        if (refreshing) return
        refreshing = true
        status.text = "جارٍ تحديث المواقيت..."
        refreshButton.isEnabled = false
        refreshButton.alpha = 0.55f
        val appContext = applicationContext
        Thread {
            val success = PrayerRepository.refresh(appContext)
            runOnUiThread {
                refreshing = false
                if (isFinishing) return@runOnUiThread
                render()
                PrayerWidgetProvider.updateAll(appContext)
                if (showToast) Toast.makeText(
                    this,
                    if (success) "تم تحديث مواقيت الصلاة" else "تعذر التحديث؛ تحقق من الإنترنت",
                    Toast.LENGTH_SHORT
                ).show()
                if (!success && PrayerRepository.needsRefresh(appContext)) {
                    status.text = "تعذر التحديث؛ نحتفظ بآخر مواقيت محفوظة. تحقق من الإنترنت وأعد المحاولة."
                }
            }
        }.start()
    }

    private fun saveIqamaSettings() {
        iqamaInputs.forEach { (key, field) ->
            val entered = normalizeDigits(field.text.toString()).toIntOrNull()
            val minutes = (entered ?: PrayerRepository.iqamaMinutes(this, key)).coerceIn(1, 90)
            PrayerRepository.setIqama(this, key, minutes)
            field.setText(minutes.toString())
        }
        render()
        PrayerWidgetProvider.updateAll(this)
        Toast.makeText(this, "تم حفظ إعدادات الإقامة", Toast.LENGTH_SHORT).show()
    }

    private fun normalizeDigits(value: String): String {
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        return value.map { char ->
            when {
                char in '٠'..'٩' -> ('0'.code + arabic.indexOf(char)).toChar()
                char in '۰'..'۹' -> ('0'.code + persian.indexOf(char)).toChar()
                else -> char
            }
        }.joinToString("")
    }

    private fun todayLabel() =
        SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar", "YE"))
            .apply { timeZone = TimeZone.getTimeZone(PrayerData.TIME_ZONE) }.format(Date())

    private fun sectionTitle(title: String, subtitle: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(12))
            addView(label(title, 18f, ink, true))
            addView(label(subtitle, 12f, muted).apply { setPadding(0, dp(3), 0, 0) })
        }

    private fun card(color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
        setPadding(dp(14), dp(15), dp(14), dp(15))
        background = rounded(color, 18)
        elevation = dp(1).toFloat()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun bottomMargin(value: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = value
        }

    private fun topMargin(value: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = value
        }
}
