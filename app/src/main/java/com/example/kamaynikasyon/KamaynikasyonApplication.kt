package com.example.kamaynikasyon

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.kamaynikasyon.core.firebase.FirebaseConfig
import com.example.kamaynikasyon.core.firebase.ProgressSyncer
import com.example.kamaynikasyon.core.notifications.NotificationHelper
import com.example.kamaynikasyon.core.notifications.ReminderScheduler
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.example.kamaynikasyon.core.utils.AnalyticsLogger

class KamaynikasyonApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Force light mode (disable dark mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        FirebaseConfig.initialize()
        
        // Initialize Crashlytics
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(true)
        
        // Initialize Analytics
        AnalyticsLogger.initialize(this)
        
        // Sync local progress to Firestore when online and authenticated
        ProgressSyncer.syncIfOnline(this)
        
        // Initialize Supabase
        SupabaseConfig.initialize(this)
        
        // Verify and clear any stale sync status (in case app crashed during sync)
        ContentSyncManager.verifyAndClearStaleSyncStatus(this)

        // Notifications
        NotificationHelper.ensureChannel(this)
        // Only schedule if notifications are enabled in settings
        val enabled = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("notifications_enabled", true)
        if (enabled) {
            ReminderScheduler.scheduleDailyStreakReminder(this)
        } else {
            ReminderScheduler.cancelDailyStreakReminder(this)
        }
    }
}
