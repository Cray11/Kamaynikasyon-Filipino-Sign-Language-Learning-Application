package com.example.kamaynikasyon.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.features.quizzes.activities.DailyStreakActivity

object NotificationHelper {
    const val CHANNEL_ID_DAILY = "daily_streak_reminders"
    private const val CHANNEL_NAME = "Daily Streak Reminders"
    private const val CHANNEL_DESC = "Notifications reminding you to complete the daily streak"
    private const val NOTIF_ID_DAILY = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_DAILY,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun showDailyStreakReminder(context: Context) {
        val intent = Intent(context, DailyStreakActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DAILY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Daily Streak Reminder")
            .setContentText("You haven't completed today's streak. Tap to start!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)

        NotificationManagerCompat.from(context).notify(NOTIF_ID_DAILY, builder.build())
    }
}


