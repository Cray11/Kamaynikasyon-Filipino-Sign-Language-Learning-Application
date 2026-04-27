package com.example.kamaynikasyon.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserProfile::class, LessonProgress::class, QuizProgress::class, DailyStreakProgress::class, PictureQuizProgress::class, GestureMatchProgress::class, SpellingSequenceProgress::class, BubbleShooterProgress::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun quizProgressDao(): QuizProgressDao
    abstract fun dailyStreakProgressDao(): DailyStreakProgressDao
    abstract fun pictureQuizProgressDao(): PictureQuizProgressDao
    abstract fun gestureMatchProgressDao(): GestureMatchProgressDao
    abstract fun spellingSequenceProgressDao(): SpellingSequenceProgressDao
    abstract fun bubbleShooterProgressDao(): BubbleShooterProgressDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kamaynikasyon.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}


