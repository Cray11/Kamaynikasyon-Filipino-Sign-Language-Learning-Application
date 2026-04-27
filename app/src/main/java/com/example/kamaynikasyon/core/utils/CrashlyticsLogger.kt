package com.example.kamaynikasyon.core.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.util.Log

/**
 * Utility class for logging custom events and user actions to Crashlytics.
 * This helps track important user interactions and app behavior.
 */
object CrashlyticsLogger {

    private const val TAG = "CrashlyticsLogger"

    /**
     * Logs a custom event to Crashlytics.
     */
    fun logEvent(eventName: String, additionalInfo: Map<String, String>? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            val message = buildString {
                append("Event: $eventName")
                additionalInfo?.forEach { (key, value) ->
                    append(", $key: $value")
                }
            }
            crashlytics.log(message)
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event to Crashlytics", e)
        }
    }

    /**
     * Logs a user action (e.g., login, lesson completion, quiz attempt).
     */
    fun logUserAction(action: String, details: Map<String, String>? = null) {
        logEvent("UserAction_$action", details)
    }

    /**
     * Logs authentication events.
     */
    fun logAuthEvent(event: String, userId: String? = null, success: Boolean = true) {
        val details = mutableMapOf<String, String>(
            "success" to success.toString()
        )
        userId?.let { details["userId"] = it }
        logEvent("Auth_$event", details)
    }

    /**
     * Logs lesson-related events.
     */
    fun logLessonEvent(event: String, lessonId: String? = null, details: Map<String, String>? = null) {
        val eventDetails = details?.toMutableMap() ?: mutableMapOf()
        lessonId?.let { eventDetails["lessonId"] = it }
        logEvent("Lesson_$event", eventDetails)
    }

    /**
     * Logs quiz-related events.
     */
    fun logQuizEvent(event: String, quizId: String? = null, details: Map<String, String>? = null) {
        val eventDetails = details?.toMutableMap() ?: mutableMapOf()
        quizId?.let { eventDetails["quizId"] = it }
        logEvent("Quiz_$event", eventDetails)
    }

    /**
     * Logs minigame-related events.
     */
    fun logMinigameEvent(event: String, gameId: String? = null, details: Map<String, String>? = null) {
        val eventDetails = details?.toMutableMap() ?: mutableMapOf()
        gameId?.let { eventDetails["gameId"] = it }
        logEvent("Minigame_$event", eventDetails)
    }

    /**
     * Logs content sync events.
     */
    fun logContentSyncEvent(event: String, success: Boolean, details: Map<String, String>? = null) {
        val eventDetails = details?.toMutableMap() ?: mutableMapOf()
        eventDetails["success"] = success.toString()
        logEvent("ContentSync_$event", eventDetails)
    }

    /**
     * Sets a custom key-value pair for all subsequent crash reports.
     */
    fun setCustomKey(key: String, value: String) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom key in Crashlytics", e)
        }
    }

    /**
     * Sets user identifier for crash reports.
     */
    fun setUserId(userId: String) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user ID in Crashlytics", e)
        }
    }
}

