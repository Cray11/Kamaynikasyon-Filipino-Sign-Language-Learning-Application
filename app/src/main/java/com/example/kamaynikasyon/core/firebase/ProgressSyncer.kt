package com.example.kamaynikasyon.core.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.core.utils.CrashlyticsLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object ProgressSyncer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncIfOnline(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (!isOnline(context)) return
        scope.launch {
            try {
                syncAll(context, user.uid)
                CrashlyticsLogger.logContentSyncEvent("completed", success = true)
            } catch (e: Exception) {
                CrashlyticsLogger.logContentSyncEvent("failed", success = false, 
                    mapOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }

    private suspend fun syncAll(context: Context, uid: String) {
        val db = AppDatabase.getInstance(context.applicationContext)
        val fs: FirebaseFirestore = FirebaseFirestore.getInstance()
        val userDoc = fs.collection("users").document(uid)

        // Lessons: upload completed lesson IDs as documents
        try {
            val completedLessons = db.lessonProgressDao().getCompletedIds()
            val lessonsCol = userDoc.collection("progress_lessons")
            for (lessonId in completedLessons) {
                lessonsCol.document(lessonId).set(
                    mapOf(
                        "lessonId" to lessonId,
                        "completed" to true,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) { }

        // Quizzes (regular): minimal score fields per quizId
        try {
            val quizRows = db.quizProgressDao().getAllScores()
            val quizzesCol = userDoc.collection("progress_quizzes")
            for (row in quizRows) {
                quizzesCol.document(row.quizId).set(
                    mapOf(
                        "quizId" to row.quizId,
                        "correctAnswers" to row.correctAnswers,
                        "totalQuestions" to row.totalQuestions,
                        "completed" to row.completed,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) { }

        // Daily streaks: upload last 30 days like Profile screen
        try {
            val dates = (0..30).map { java.time.LocalDate.now().minusDays(it.toLong()).toString() }
            val dailyRows = db.dailyStreakProgressDao().getByDates(dates)
            val dailyCol = userDoc.collection("progress_daily_streaks")
            for (row in dailyRows) {
                dailyCol.document(row.date).set(
                    mapOf(
                        "date" to row.date,
                        "correctAnswers" to row.correctAnswers,
                        "totalQuestions" to row.totalQuestions,
                        "completed" to row.completed,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) { }

        // Minigames: Bubble Shooter
        try {
            val all = db.bubbleShooterProgressDao().getAll()
            val col = userDoc.collection("progress_bubble_shooter")
            for (p in all) {
                col.document(p.levelId).set(
                    mapOf(
                        "levelId" to p.levelId,
                        "bestScore" to p.bestScore,
                        "bestStars" to p.bestStars,
                        "bestTimeSeconds" to p.bestTimeSeconds,
                        "completed" to p.completed,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) { }

        // Minigames: Picture Quiz
        try {
            val all = db.pictureQuizProgressDao().getAll()
            val col = userDoc.collection("progress_picture_quiz")
            for (p in all) {
                col.document(p.levelId).set(
                    mapOf(
                        "levelId" to p.levelId,
                        "bestScore" to p.bestScore,
                        "bestStars" to p.bestStars,
                        "bestTimeSeconds" to p.bestTimeSeconds,
                        "completed" to p.completed,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) { }

        // Minigames: Spelling Sequence
        try {
            val all = db.spellingSequenceProgressDao().getAll()
            val col = userDoc.collection("progress_spelling_sequence")
            for (p in all) {
                col.document(p.levelId).set(
                    mapOf(
                        "levelId" to p.levelId,
                        "bestScore" to p.bestScore,
                        "bestStars" to p.bestStars,
                        "bestTimeSeconds" to p.bestTimeSeconds,
                        "completed" to p.completed,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) { }

        // Minigames: Gesture Match
        try {
            val all = db.gestureMatchProgressDao().getAll()
            val col = userDoc.collection("progress_gesture_match")
            for (p in all) {
                col.document(p.levelId).set(
                    mapOf(
                        "levelId" to p.levelId,
                        "bestScore" to p.bestScore,
                        "bestStars" to p.bestStars,
                        "bestTimeSeconds" to p.bestTimeSeconds,
                        "bestAttempts" to p.bestAttempts,
                        "completed" to p.completed,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) { }

        // Last sync marker
        try {
            userDoc.collection("metadata").document("progressSync").set(
                mapOf("lastSyncedAt" to System.currentTimeMillis())
            ).await()
        } catch (_: Exception) { }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}


