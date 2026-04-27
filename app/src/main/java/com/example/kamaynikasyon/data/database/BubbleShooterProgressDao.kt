package com.example.kamaynikasyon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BubbleShooterProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: BubbleShooterProgress)

    @Query("SELECT * FROM bubble_shooter_progress")
    suspend fun getAll(): List<BubbleShooterProgress>

    @Query("SELECT * FROM bubble_shooter_progress WHERE levelId = :levelId LIMIT 1")
    suspend fun get(levelId: String): BubbleShooterProgress?

    @Query("DELETE FROM bubble_shooter_progress")
    suspend fun deleteAll()
}

