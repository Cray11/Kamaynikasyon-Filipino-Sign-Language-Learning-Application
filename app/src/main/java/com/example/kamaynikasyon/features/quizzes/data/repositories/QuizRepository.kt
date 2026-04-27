package com.example.kamaynikasyon.features.quizzes.data.repositories

import android.content.Context
import android.util.Log
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.utils.CacheManager
import com.example.kamaynikasyon.core.utils.ErrorHandler
import com.example.kamaynikasyon.features.quizzes.data.models.Quiz
import com.example.kamaynikasyon.features.quizzes.data.models.QuizQuestion
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class QuizRepository(private val context: Context) {

    private var quizzesIndex: Map<String, Any>? = null
    private val quizCache = mutableMapOf<String, Quiz>()
    private val gson = Gson()
    private val TAG = "QuizRepository"

    private suspend fun loadQuizzesIndex(): Map<String, Any>? = withContext(Dispatchers.IO) {
        if (quizzesIndex != null) {
            return@withContext quizzesIndex
        }

        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(context)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = context.assets.open("quizzes/index.json").bufferedReader().use { it.readText() }
                val type = object : TypeToken<Map<String, Any>>() {}.type
                quizzesIndex = gson.fromJson(jsonString, type)
                Log.d(TAG, "Loaded quizzes index from assets (offline assets only mode)")
                quizzesIndex
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load index from assets", e)
                null
            }
        }

        val isOnline = ErrorHandler.isOnline(context)

        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    context,
                    SupabaseConfig.BUCKET_QUIZZES,
                    "index.json"
                )
                if (jsonString != null) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    quizzesIndex = gson.fromJson(jsonString, type)
                    Log.d(TAG, "Loaded quizzes index from Supabase and cached")
                    return@withContext quizzesIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load index from Supabase, trying cache", e)
            }
        }

        // Try persistent cache (useful when offline)
        if (!isOnline || !SupabaseConfig.isInitialized()) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_QUIZZES}/index.json")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    quizzesIndex = gson.fromJson(jsonString, type)
                    Log.d(TAG, "Loaded quizzes index from SupabaseStorage cache (offline)")
                    return@withContext quizzesIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from SupabaseStorage cache", e)
            }
        }

        // Fallback to assets
        return@withContext try {
            val jsonString = context.assets.open("quizzes/index.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Any>>() {}.type
            quizzesIndex = gson.fromJson(jsonString, type)
            Log.d(TAG, "Loaded quizzes index from assets")
            quizzesIndex
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load index from assets", e)
            null
        }
    }

    private suspend fun loadQuizData(fileName: String): Quiz? = withContext(Dispatchers.IO) {
        // Return in-memory cached if present
        quizCache[fileName]?.let { return@withContext it }

        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(context)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = context.assets.open("quizzes/$fileName").bufferedReader().use { it.readText() }
                val quiz = gson.fromJson(jsonString, Quiz::class.java)
                if (quiz != null) {
                    quizCache[fileName] = quiz
                    Log.d(TAG, "Loaded quiz from assets (offline assets only mode): $fileName")
                }
                quiz
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load quiz from assets: $fileName", e)
                null
            }
        }

        val isOnline = ErrorHandler.isOnline(context)

        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    context,
                    SupabaseConfig.BUCKET_QUIZZES,
                    fileName
                )
                if (jsonString != null) {
                    val quiz = gson.fromJson(jsonString, Quiz::class.java)
                    if (quiz != null) {
                        quizCache[fileName] = quiz
                        // Cache to persistent storage for offline use
                        CacheManager.cacheData(context, "quizzes", fileName, quiz)
                        Log.d(TAG, "Loaded quiz from Supabase and cached: $fileName")
                        return@withContext quiz
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load quiz from Supabase: $fileName, trying cache", e)
            }
        }

        // Try persistent cache (useful when offline)
        if (!isOnline || !SupabaseConfig.isInitialized()) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_QUIZZES}/$fileName")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    val quiz = gson.fromJson(jsonString, Quiz::class.java)
                    if (quiz != null) {
                        quizCache[fileName] = quiz
                        CacheManager.cacheData(context, "quizzes", fileName, quiz)
                        Log.d(TAG, "Loaded quiz from SupabaseStorage cache (offline): $fileName")
                        return@withContext quiz
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            val cachedQuiz = CacheManager.getCachedData(context, "quizzes", fileName, Quiz::class.java)
            if (cachedQuiz != null) {
                quizCache[fileName] = cachedQuiz
                Log.d(TAG, "Loaded quiz from CacheManager (offline): $fileName")
                return@withContext cachedQuiz
            }
        }

        // Fallback to assets
        return@withContext try {
            val jsonString = context.assets.open("quizzes/$fileName").bufferedReader().use { it.readText() }
            val quiz = gson.fromJson(jsonString, Quiz::class.java)
            if (quiz != null) {
                quizCache[fileName] = quiz
                // Cache assets data for future offline use
                CacheManager.cacheData(context, "quizzes", fileName, quiz)
                Log.d(TAG, "Loaded quiz from assets and cached: $fileName")
            }
            quiz
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load quiz from assets: $fileName", e)
            null
        }
    }

    suspend fun loadQuizzes(): List<Quiz> = withContext(Dispatchers.IO) {
        val allQuizzes = mutableListOf<Quiz>()
        val index = loadQuizzesIndex()

        @Suppress("UNCHECKED_CAST")
        val quizzesList = index?.get("quizzes") as? List<Map<String, Any>>

        quizzesList?.forEach { quizInfo ->
            val fileName = quizInfo["file"] as? String
            if (fileName != null) {
                loadQuizData(fileName)?.let { quiz ->
                    allQuizzes.add(quiz)
                }
            }
        }

        return@withContext allQuizzes
    }

    suspend fun getQuizById(quizId: String): Quiz? = withContext(Dispatchers.IO) {
        // Load all quizzes and find by ID (ID is stored in the quiz file itself)
        val allQuizzes = loadQuizzes()
        return@withContext allQuizzes.find { it.id == quizId }
    }

    suspend fun getQuizzesByDifficulty(difficulty: String): List<Quiz> {
        val allQuizzes = loadQuizzes()
        return allQuizzes.filter { it.difficulty.equals(difficulty, ignoreCase = true) }
    }

    suspend fun getRandomQuestionsForDailyStreak(questionCount: Int): List<QuizQuestion> {
        val allQuizzes = loadQuizzes()
        val allQuestions = allQuizzes.flatMap { it.questions }
        return allQuestions.shuffled().take(questionCount)
    }
}

