package com.example.mawaqeetalmukalla

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        scheduleDailyRefresh(context)
        enqueueRefreshIfNeeded(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
        scheduleDailyRefresh(context)
        enqueueRefreshIfNeeded(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK_NAME)
            cancelUniqueWork(ONE_TIME_WORK_NAME)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                updateAll(context)
                enqueueRefresh(context, force = true)
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                updateAll(context)
                scheduleDailyRefresh(context)
                enqueueRefreshIfNeeded(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.mawaqeetalmukalla.REFRESH"
        private const val PERIODIC_WORK_NAME = "prayer_times_daily_refresh"
        private const val ONE_TIME_WORK_NAME = "prayer_times_refresh_once"
        private const val LEGACY_WORK_NAME = "prayer_times_refresh"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PrayerWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun scheduleDailyRefresh(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(LEGACY_WORK_NAME)
            val request = PeriodicWorkRequestBuilder<PrayerWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints())
                .build()
            work.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        private fun enqueueRefreshIfNeeded(context: Context) {
            if (PrayerRepository.needsRefresh(context)) enqueueRefresh(context, false)
        }

        private fun enqueueRefresh(context: Context, force: Boolean) {
            val request = OneTimeWorkRequestBuilder<PrayerWorker>()
                .setInputData(Data.Builder().putBoolean("force", force).build())
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.prayer_widget)
            val refreshIntent = Intent(context, PrayerWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            views.setOnClickPendingIntent(
                R.id.widget_refresh,
                PendingIntent.getBroadcast(
                    context, 1000 + id, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 2000 + id, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val times = PrayerRepository.storedTimes(context)
            val list = PrayerData.keys.indices.joinToString("\n") { index ->
                val key = PrayerData.keys[index]
                val value = times[key]?.let(PrayerData::arabicDigits) ?: "--:--"
                "${PrayerData.names[index]}     $value"
            }
            views.setTextViewText(R.id.prayer_list, list)

            val event = PrayerData.nextEvent(times, { key ->
                PrayerRepository.iqamaMinutes(context, key)
            })
            if (event == null) {
                views.setTextViewText(R.id.next_prayer, "لم تُحمّل المواقيت بعد")
                views.setTextViewText(R.id.countdown_label, "اضغط تحديث عند توفر الإنترنت")
                views.setTextViewText(R.id.iqama, "المكلا · حضرموت")
                views.setTextViewText(R.id.countdown, "--:--")
            } else {
                views.setTextViewText(R.id.next_prayer, event.title)
                views.setTextViewText(R.id.countdown_label, event.countdownLabel)
                val remaining = (event.targetMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                views.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + remaining, "%s", true)
                views.setChronometerCountDown(R.id.countdown, true)
                val minutes = PrayerRepository.iqamaMinutes(context, event.prayerKey)
                views.setTextViewText(
                    R.id.iqama,
                    "مدة الأذان إلى الإقامة: ${PrayerData.arabicDigits(minutes.toString())} دقيقة"
                )
            }
            manager.updateAppWidget(id, views)
        }
    }
}