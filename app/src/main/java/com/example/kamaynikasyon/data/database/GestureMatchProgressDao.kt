package com.example.kamaynikasyon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GestureMatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: GestureMatchProgress)

    @Query("SELECT * FROM gesture_match_progress WHERE levelId = :levelId LIMIT 1")
    suspend fun get(levelId: String): GestureMatchProgress?

    @Query("SELECT * FROM gesture_match_progress")
    suspend fun getAll(): List<GestureMatchProgress>

    @Query("DELETE FROM gesture_match_progress")
    suspend fun deleteAll()
}


