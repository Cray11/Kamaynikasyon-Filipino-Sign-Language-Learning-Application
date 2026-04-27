package com.example.kamaynikasyon.features.quizzes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kamaynikasyon.core.notifications.NotificationHelper
import com.example.kamaynikasyon.data.database.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyStreakReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Honor notification setting
        val notificationsEnabled = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) return

        NotificationHelper.ensureChannel(context)

        val today = LocalDate.now().toString()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context.applicationContext).dailyStreakProgressDao()
                val entries = dao.getByDates(listOf(today))
                val completed = entries.any { it.completed }
                if (!completed) {
                    NotificationHelper.showDailyStreakReminder(context)
                }
            } catch (_: Exception) {
                // Fail silently
            }
        }
    }
}

