package com.example.kamaynikasyon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpellingSequenceProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: SpellingSequenceProgress)

    @Query("SELECT * FROM spelling_sequence_progress")
    suspend fun getAll(): List<SpellingSequenceProgress>

    @Query("SELECT * FROM spelling_sequence_progress WHERE levelId = :levelId LIMIT 1")
    suspend fun get(levelId: String): SpellingSequenceProgress?

    @Query("DELETE FROM spelling_sequence_progress")
    suspend fun deleteAll()
}


