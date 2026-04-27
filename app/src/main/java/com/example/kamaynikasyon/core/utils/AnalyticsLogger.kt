package com.example.kamaynikasyon.core.utils

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase

/**
 * Utility for logging analytics events to Firebase Analytics.
 * Provides structured event tracking for user actions and app usage.
 */
object AnalyticsLogger {
    
    private var analytics: FirebaseAnalytics? = null
    
    /**
     * Initialize AnalyticsLogger with context.
     * Should be called from Application.onCreate()
     */
    fun initialize(context: Context) {
        analytics = Firebase.analytics
    }
    
    /**
     * Log a custom event with parameters.
     * 
     * @param eventName Name of the event
     * @param parameters Map of parameter names to values
     */
    fun logEvent(eventName: String, parameters: Map<String, Any>? = null) {
        analytics?.logEvent(eventName) {
            parameters?.forEach { (key, value) ->
                param(key, value.toString())
            }
        }
    }
    
    /**
     * Log lesson-related events.
     */
    fun logLessonEvent(
        event: String,
        lessonId: String? = null,
        lessonTitle: String? = null,
        additionalParams: Map<String, Any>? = null
    ) {
        val params = mutableMapOf<String, Any>()
        lessonId?.let { params["lesson_id"] = it }
        lessonTitle?.let { params["lesson_title"] = it }
        additionalParams?.let { params.putAll(it) }
        
        logEvent("lesson_$event", params)
    }
    
    /**
     * Log quiz-related events.
     */
    fun logQuizEvent(
        event: String,
        quizId: String? = null,
        quizTitle: String? = null,
        score: Int? = null,
        totalQuestions: Int? = null,
        additionalParams: Map<String, Any>? = null
    ) {
        val params = mutableMapOf<String, Any>()
        quizId?.let { params["quiz_id"] = it }
        quizTitle?.let { params["quiz_title"] = it }
        score?.let { params["score"] = it }
        totalQuestions?.let { params["total_questions"] = it }
        score?.let { totalQuestions?.let { total ->
            params["score_percentage"] = ((score.toFloat() / total) * 100).toInt()
        } }
        additionalParams?.let { params.putAll(it) }
        
        logEvent("quiz_$event", params)
    }
    
    /**
     * Log minigame-related events.
     */
    fun logMinigameEvent(
        event: String,
        gameType: String,
        levelId: String? = null,
        score: Int? = null,
        stars: Int? = null,
        additionalParams: Map<String, Any>? = null
    ) {
        val params = mutableMapOf<String, Any>(
            "game_type" to gameType
        )
        levelId?.let { params["level_id"] = it }
        score?.let { params["score"] = it }
        stars?.let { params["stars"] = it }
        additionalParams?.let { params.putAll(it) }
        
        logEvent("minigame_$event", params)
    }
    
    /**
     * Log dictionary-related events.
     */
    fun logDictionaryEvent(
        event: String,
        searchTerm: String? = null,
        wordId: String? = null,
        additionalParams: Map<String, Any>? = null
    ) {
        val params = mutableMapOf<String, Any>()
        searchTerm?.let { params["search_term"] = it }
        wordId?.let { params["word_id"] = it }
        additionalParams?.let { params.putAll(it) }
        
        logEvent("dictionary_$event", params)
    }
    
    /**
     * Log authentication events.
     */
    fun logAuthEvent(
        event: String,
        method: String? = null,
        additionalParams: Map<String, Any>? = null
    ) {
        val params = mutableMapOf<String, Any>()
        method?.let { params["auth_method"] = it }
        additionalParams?.let { params.putAll(it) }
        
        logEvent("auth_$event", params)
    }
    
    /**
     * Log content sync events.
     */
    fun logContentSyncEvent(
        event: String,
        success: Boolean? = null,
        itemCount: Int? = null,
        additionalParams: Map<String, Any>? = null
    ) {
        val params = mutableMapOf<String, Any>()
        success?.let { params["success"] = if (it) 1 else 0 }
        itemCount?.let { params["item_count"] = it }
        additionalParams?.let { params.putAll(it) }
        
        logEvent("content_sync_$event", params)
    }
    
    /**
     * Log feature usage events.
     */
    fun logFeatureUsage(
        featureName: String,
        action: String? = null,
        additionalParams: Map<String, Any>? = null
    ) {
        val params = mutableMapOf<String, Any>()
        action?.let { params["action"] = it }
        additionalParams?.let { params.putAll(it) }
        
        logEvent("feature_usage", params + ("feature_name" to featureName))
    }
    
    /**
     * Log screen view events.
     */
    fun logScreenView(screenName: String, screenClass: String? = null) {
        val params = mutableMapOf<String, Any>(
            FirebaseAnalytics.Param.SCREEN_NAME to screenName
        )
        screenClass?.let { params[FirebaseAnalytics.Param.SCREEN_CLASS] = it }
        
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }
    
    /**
     * Set user property for analytics.
     */
    fun setUserProperty(name: String, value: String) {
        analytics?.setUserProperty(name, value)
    }
    
    /**
     * Set user ID for analytics.
     */
    fun setUserId(userId: String?) {
        analytics?.setUserId(userId)
    }
}

