package com.example.streaky.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.streaky.R

object NotificationHelper {

    private const val CHANNEL_ID   = "habit_reminder_channel"
    private const val CHANNEL_NAME = "Напоминания о привычках"
    private const val NOTIF_ID     = 1001

    /** Вызывается один раз при старте приложения. */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Ежедневное напоминание отметить привычки"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /** Показывает уведомление-напоминание. */
    fun showReminderNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fire)          // замени на свою иконку если нужно
            .setContentTitle("Streaky 🔥")
            .setContentText("Не забудь отметить привычки сегодня!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, notification)
    }
}