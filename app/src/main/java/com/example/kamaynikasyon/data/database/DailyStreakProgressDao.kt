package com.example.kamaynikasyon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyStreakProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: DailyStreakProgress)

    @Query("SELECT * FROM daily_streak_progress WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyStreakProgress?

    @Query("SELECT * FROM daily_streak_progress WHERE date IN (:dates)")
    suspend fun getByDates(dates: List<String>): List<DailyStreakProgress>

    @Query("SELECT MIN(date) FROM daily_streak_progress")
    suspend fun getEarliestDate(): String?

    @Query("DELETE FROM daily_streak_progress")
    suspend fun deleteAll()
}


