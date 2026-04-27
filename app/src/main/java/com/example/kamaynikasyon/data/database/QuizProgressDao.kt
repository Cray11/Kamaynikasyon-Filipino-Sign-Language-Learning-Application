package com.example.kamaynikasyon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuizProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: QuizProgress)

    @Query("SELECT correctAnswers, totalQuestions FROM quiz_progress WHERE quizId = :quizId LIMIT 1")
    suspend fun getScore(quizId: String): ScoreOnly?

    @Query("SELECT quizId, correctAnswers, totalQuestions, completed FROM quiz_progress")
    suspend fun getAllScores(): List<ScoreRow>

    @Query("DELETE FROM quiz_progress")
    suspend fun deleteAll()
}

data class ScoreRow(
    val quizId: String,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val completed: Boolean
)

data class ScoreOnly(
    val correctAnswers: Int,
    val totalQuestions: Int
)


