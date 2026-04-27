package com.example.kamaynikasyon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spelling_sequence_progress")
data class SpellingSequenceProgress(
    @PrimaryKey val levelId: String,
    val bestScore: Int,
    val bestStars: Int,
    val bestTimeSeconds: Int,
    val completed: Boolean
)


