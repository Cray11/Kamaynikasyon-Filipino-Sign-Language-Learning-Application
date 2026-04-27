package com.example.kamaynikasyon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_progress")
data class QuizProgress(
    @PrimaryKey val quizId: String,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val completed: Boolean
)


