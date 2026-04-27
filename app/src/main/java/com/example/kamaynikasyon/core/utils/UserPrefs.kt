package com.example.kamaynikasyon.core.utils

import android.content.Context
import android.content.SharedPreferences

object UserPrefs {
    private const val PREF_NAME = "kamaynikasyon_prefs"
    private const val KEY_SHOW_LESSONS_TUTORIAL = "show_lessons_tutorial"
    private const val KEY_SHOW_QUIZZES_TUTORIAL = "show_quizzes_tutorial"
    private const val KEY_SHOW_DAILY_STREAK_TUTORIAL = "show_daily_streak_tutorial"
    private const val KEY_SHOW_BUBBLE_SHOOTER_TUTORIAL = "show_bubble_shooter_tutorial"
    private const val KEY_SHOW_GESTURE_MATCH_TUTORIAL = "show_gesture_match_tutorial"
    private const val KEY_SHOW_SPELLING_SEQUENCE_TUTORIAL = "show_spelling_sequence_tutorial"
    private const val KEY_SHOW_PICTURE_QUIZ_TUTORIAL = "show_picture_quiz_tutorial"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun shouldShowLessonsTutorial(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_LESSONS_TUTORIAL, true)
    }

    fun setShowLessonsTutorial(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_LESSONS_TUTORIAL, show).apply()
    }

    fun shouldShowQuizzesTutorial(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_QUIZZES_TUTORIAL, true)
    }

    fun setShowQuizzesTutorial(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_QUIZZES_TUTORIAL, show).apply()
    }

    fun shouldShowDailyStreakTutorial(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_DAILY_STREAK_TUTORIAL, true)
    }

    fun setShowDailyStreakTutorial(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_DAILY_STREAK_TUTORIAL, show).apply()
    }

    fun shouldShowPictureQuizTutorial(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_PICTURE_QUIZ_TUTORIAL, true)
    }

    fun setShowPictureQuizTutorial(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_PICTURE_QUIZ_TUTORIAL, show).apply()
    }

    fun shouldShowBubbleShooterTutorial(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_BUBBLE_SHOOTER_TUTORIAL, true)
    }
    fun setShowBubbleShooterTutorial(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_BUBBLE_SHOOTER_TUTORIAL, show).apply()
    }

    fun shouldShowGestureMatchTutorial(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_GESTURE_MATCH_TUTORIAL, true)
    }
    fun setShowGestureMatchTutorial(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_GESTURE_MATCH_TUTORIAL, show).apply()
    }

    fun shouldShowSpellingSequenceTutorial(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_SPELLING_SEQUENCE_TUTORIAL, true)
    }
    fun setShowSpellingSequenceTutorial(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_SPELLING_SEQUENCE_TUTORIAL, show).apply()
    }
}


