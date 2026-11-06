package com.example.mawaqeetalmukalla

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class PrayerWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val forceRefresh = inputData.getBoolean("force", false)
        if (!forceRefresh && !PrayerRepository.needsRefresh(applicationContext)) {
            PrayerWidgetProvider.updateAll(applicationContext)
            return Result.success()
        }

        return if (PrayerRepository.refresh(applicationContext)) {
            PrayerWidgetProvider.updateAll(applicationContext)
            Result.success()
        } else {
            Result.retry()
        }
    }
}
