package com.example.kamaynikasyon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "picture_quiz_progress")
data class PictureQuizProgress(
    @PrimaryKey val levelId: String, // e.g., "level1"
    val bestScore: Int,
    val bestStars: Int,
    val bestTimeSeconds: Int,
    val completed: Boolean
)


