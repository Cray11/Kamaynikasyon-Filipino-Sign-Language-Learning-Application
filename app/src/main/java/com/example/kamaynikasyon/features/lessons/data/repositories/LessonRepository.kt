package com.example.kamaynikasyon.features.lessons.data.repositories

import android.content.Context
import android.util.Log
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.utils.CacheManager
import com.example.kamaynikasyon.core.utils.ErrorHandler
import com.example.kamaynikasyon.features.lessons.data.models.Lesson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class LessonRepository(private val context: Context) {
    
    private var lessonsIndex: Map<String, Any>? = null
    private val lessonCache = mutableMapOf<String, Lesson>()
    private val gson = Gson()
    private val TAG = "LessonRepository"
    
    private suspend fun loadLessonsIndex(): Map<String, Any>? = withContext(Dispatchers.IO) {
        if (lessonsIndex != null) {
            return@withContext lessonsIndex
        }
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(context)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = context.assets.open("lessons/index.json").bufferedReader().use { it.readText() }
                val type = object : TypeToken<Map<String, Any>>() {}.type
                lessonsIndex = gson.fromJson(jsonString, type)
                Log.d(TAG, "Loaded lessons index from assets (offline assets only mode)")
                lessonsIndex
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load index from assets", e)
                null
            }
        }
        
        val isOnline = ErrorHandler.isOnline(context)
        val syncInProgress = ContentSyncManager.isSyncInProgress(context)
        
        // If sync is in progress, prefer cache to avoid conflicts
        if (syncInProgress) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_LESSONS}/index.json")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    lessonsIndex = gson.fromJson(jsonString, type)
                    Log.d(TAG, "Loaded lessons index from cache (sync in progress)")
                    return@withContext lessonsIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from cache during sync, will try Supabase", e)
            }
        }
        
        // Try Supabase first if available and online (and not syncing)
        if (SupabaseConfig.isInitialized() && isOnline && !syncInProgress) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    context,
                    SupabaseConfig.BUCKET_LESSONS,
                    "index.json"
                )
                if (jsonString != null) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    lessonsIndex = gson.fromJson(jsonString, type)
                    // Cache the parsed index for offline use
                    lessonsIndex?.let { CacheManager.cacheData(context, "lessons", "index", it) }
                    Log.d(TAG, "Loaded lessons index from Supabase and cached")
                    return@withContext lessonsIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load index from Supabase, trying cache", e)
            }
        }
        
        // Try persistent cache (useful when offline or sync in progress)
        // Check if we have a cached file from SupabaseStorage first
        if (!isOnline || !SupabaseConfig.isInitialized() || syncInProgress) {
            try {
                // SupabaseStorage already caches files, try reading from its cache
                val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_LESSONS}/index.json")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    lessonsIndex = gson.fromJson(jsonString, type)
                    Log.d(TAG, "Loaded lessons index from SupabaseStorage cache (offline)")
                    return@withContext lessonsIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from SupabaseStorage cache", e)
            }
        }
        
        // Fallback to assets
        return@withContext try {
            val jsonString = context.assets.open("lessons/index.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Any>>() {}.type
            lessonsIndex = gson.fromJson(jsonString, type)
            // Cache assets data for future offline use
            lessonsIndex?.let { CacheManager.cacheData(context, "lessons", "index", it) }
            Log.d(TAG, "Loaded lessons index from assets and cached")
            lessonsIndex
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load index from assets", e)
            null
        }
    }
    
    private suspend fun loadLessonData(fileName: String): Lesson? = withContext(Dispatchers.IO) {
        // Return in-memory cached if present
        lessonCache[fileName]?.let { return@withContext it }

        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(context)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = context.assets.open("lessons/$fileName").bufferedReader().use { it.readText() }
                val lesson = gson.fromJson(jsonString, Lesson::class.java)
                if (lesson != null) {
                    lessonCache[fileName] = lesson
                    Log.d(TAG, "Loaded lesson from assets (offline assets only mode): $fileName")
                }
                lesson
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load lesson from assets: $fileName", e)
                null
            }
        }

        val isOnline = ErrorHandler.isOnline(context)
        val syncInProgress = ContentSyncManager.isSyncInProgress(context)
        
        // If sync is in progress, prefer cache to avoid conflicts
        if (syncInProgress) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_LESSONS}/$fileName")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    val lesson = gson.fromJson(jsonString, Lesson::class.java)
                    if (lesson != null) {
                        lessonCache[fileName] = lesson
                        Log.d(TAG, "Loaded lesson from cache (sync in progress): $fileName")
                        return@withContext lesson
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from cache during sync, will try Supabase", e)
            }
        }
        
        // Try Supabase first if available and online (and not syncing)
        if (SupabaseConfig.isInitialized() && isOnline && !syncInProgress) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    context,
                    SupabaseConfig.BUCKET_LESSONS,
                    fileName
                )
                if (jsonString != null) {
                    val lesson = gson.fromJson(jsonString, Lesson::class.java)
                    if (lesson != null) {
                        lessonCache[fileName] = lesson
                        // Cache to persistent storage for offline use
                        CacheManager.cacheData(context, "lessons", fileName, lesson)
                        Log.d(TAG, "Loaded lesson from Supabase and cached: $fileName")
                        return@withContext lesson
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load lesson from Supabase: $fileName, trying cache", e)
                // Log critical errors to Crashlytics (but not network errors which are expected)
                if (e !is java.net.UnknownHostException && e !is java.net.ConnectException) {
                    com.example.kamaynikasyon.core.utils.ErrorHandler.logErrorToCrashlytics(
                        e, "Failed to load lesson from Supabase: $fileName"
                    )
                }
            }
        }
        
        // Try persistent cache (useful when offline or sync in progress)
        // Check if we have a cached file from SupabaseStorage first
        if (!isOnline || !SupabaseConfig.isInitialized() || syncInProgress) {
            try {
                // SupabaseStorage already caches files, try reading from its cache
                val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_LESSONS}/$fileName")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    val lesson = gson.fromJson(jsonString, Lesson::class.java)
                    if (lesson != null) {
                        lessonCache[fileName] = lesson
                        // Also cache to our CacheManager for faster access
                        CacheManager.cacheData(context, "lessons", fileName, lesson)
                        Log.d(TAG, "Loaded lesson from SupabaseStorage cache (offline): $fileName")
                        return@withContext lesson
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            // Try CacheManager cache
            val cachedLesson = CacheManager.getCachedData(context, "lessons", fileName, Lesson::class.java)
            if (cachedLesson != null) {
                lessonCache[fileName] = cachedLesson
                Log.d(TAG, "Loaded lesson from CacheManager (offline): $fileName")
                return@withContext cachedLesson
            }
        }

        // Fallback to assets
        return@withContext try {
            val jsonString = context.assets.open("lessons/$fileName").bufferedReader().use { it.readText() }
            val lesson = gson.fromJson(jsonString, Lesson::class.java)
            if (lesson != null) {
                lessonCache[fileName] = lesson
                // Cache assets data for future offline use
                CacheManager.cacheData(context, "lessons", fileName, lesson)
                Log.d(TAG, "Loaded lesson from assets and cached: $fileName")
            }
            lesson
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load lesson from assets: $fileName", e)
            null
        }
    }
    
    suspend fun loadLessons(): List<Lesson> = withContext(Dispatchers.IO) {
        val allLessons = mutableListOf<Lesson>()
        val index = loadLessonsIndex()
        
        @Suppress("UNCHECKED_CAST")
        val lessonsList = index?.get("lessons") as? List<Map<String, Any>>
        
        lessonsList?.forEach { lessonInfo ->
            val fileName = lessonInfo["file"] as? String
            if (fileName != null) {
                loadLessonData(fileName)?.let { lesson ->
                    allLessons.add(lesson)
                }
            }
        }
        
        return@withContext allLessons
    }
    
    suspend fun getLessonById(lessonId: String): Lesson? = withContext(Dispatchers.IO) {
        // Load all lessons and find by ID (ID is stored in the lesson file itself)
        val allLessons = loadLessons()
        return@withContext allLessons.find { it.id == lessonId }
    }
    
    suspend fun getLessonsByDifficulty(difficulty: String): List<Lesson> {
        val allLessons = loadLessons()
        return allLessons.filter { it.difficulty.equals(difficulty, ignoreCase = true) }
    }
}

