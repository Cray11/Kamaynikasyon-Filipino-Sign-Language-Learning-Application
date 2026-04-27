package com.example.kamaynikasyon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_streak_progress")
data class DailyStreakProgress(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val correctAnswers: Int,
    val totalQuestions: Int,
    val completed: Boolean
)


