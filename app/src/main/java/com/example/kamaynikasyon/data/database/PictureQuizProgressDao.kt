package com.example.kamaynikasyon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PictureQuizProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PictureQuizProgress)

    @Query("SELECT * FROM picture_quiz_progress")
    suspend fun getAll(): List<PictureQuizProgress>

    @Query("SELECT * FROM picture_quiz_progress WHERE levelId = :levelId LIMIT 1")
    suspend fun get(levelId: String): PictureQuizProgress?

    @Query("DELETE FROM picture_quiz_progress")
    suspend fun deleteAll()
}


