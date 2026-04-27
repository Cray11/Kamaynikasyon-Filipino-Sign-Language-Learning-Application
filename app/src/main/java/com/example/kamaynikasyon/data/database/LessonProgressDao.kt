package com.example.kamaynikasyon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface LessonProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: LessonProgress)

    @Update
    suspend fun update(progress: LessonProgress)

    @Query("SELECT COUNT(*) FROM lesson_progress WHERE completed = 1")
    suspend fun countCompleted(): Int

    @Query("SELECT lessonId FROM lesson_progress WHERE completed = 1")
    suspend fun getCompletedIds(): List<String>

    @Query("UPDATE lesson_progress SET completed = 0")
    suspend fun resetAll()

    @Query("DELETE FROM lesson_progress")
    suspend fun deleteAll()
}


