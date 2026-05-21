package com.example.streaky.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.streaky.notification.NotificationHelper

class HabitReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        NotificationHelper.showReminderNotification(applicationContext)
        return Result.success()
    }
}