package com.example.kamaynikasyon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bubble_shooter_progress")
data class BubbleShooterProgress(
    @PrimaryKey val levelId: String,
    val bestScore: Int,
    val bestStars: Int,
    val bestTimeSeconds: Int,
    val completed: Boolean
)

