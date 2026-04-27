package com.example.kamaynikasyon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gesture_match_progress")
data class GestureMatchProgress(
    @PrimaryKey val levelId: String, // e.g., "level1"
    val bestScore: Int,
    val bestStars: Int,
    val bestTimeSeconds: Int,
    val bestAttempts: Int,
    val completed: Boolean
)


