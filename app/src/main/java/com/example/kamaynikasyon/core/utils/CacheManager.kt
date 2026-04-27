package com.example.kamaynikasyon.core.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Persistent cache manager for storing data locally for offline access.
 * Uses file-based storage in the app's cache directory.
 */
object CacheManager {
    
    private const val TAG = "CacheManager"
    private const val CACHE_DIR = "app_data_cache"
    private val gson = Gson()
    
    /**
     * Cache data with a key. Data is serialized to JSON and stored in a file.
     * 
     * @param context Application context
     * @param category Category name (e.g., "lessons", "quizzes", "dictionary")
     * @param key Unique key for the data
     * @param data The data object to cache (will be serialized to JSON)
     * @return true if caching succeeded, false otherwise
     */
    suspend fun <T> cacheData(
        context: Context,
        category: String,
        key: String,
        data: T
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(context, category, key)
            cacheFile.parentFile?.mkdirs()
            
            val json = gson.toJson(data)
            FileWriter(cacheFile).use { writer ->
                writer.write(json)
            }
            
            Log.d(TAG, "Cached data: $category/$key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error caching data: $category/$key", e)
            false
        }
    }
    
    /**
     * Retrieve cached data by key.
     * 
     * @param context Application context
     * @param category Category name
     * @param key Unique key for the data
     * @param type Class type of the data
     * @return Cached data or null if not found or error
     */
    suspend fun <T> getCachedData(
        context: Context,
        category: String,
        key: String,
        type: Class<T>
    ): T? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(context, category, key)
            if (!cacheFile.exists() || !cacheFile.isFile) {
                return@withContext null
            }
            
            FileReader(cacheFile).use { reader ->
                val data = gson.fromJson(reader, type)
                Log.d(TAG, "Retrieved cached data: $category/$key")
                return@withContext data
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error retrieving cached data: $category/$key", e)
            null
        }
    }
    
    /**
     * Check if data is cached.
     */
    fun isCached(context: Context, category: String, key: String): Boolean {
        val cacheFile = getCacheFile(context, category, key)
        return cacheFile.exists() && cacheFile.isFile
    }
    
    /**
     * Clear cached data for a specific category and key, or all data.
     */
    fun clearCache(context: Context, category: String? = null, key: String? = null) {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) return
        
        when {
            category == null -> {
                // Clear all cache
                cacheDir.deleteRecursively()
                Log.d(TAG, "Cleared all cache")
            }
            key == null -> {
                // Clear category cache
                val categoryDir = File(cacheDir, category)
                if (categoryDir.exists()) {
                    categoryDir.deleteRecursively()
                    Log.d(TAG, "Cleared cache for category: $category")
                }
            }
            else -> {
                // Clear specific key cache
                val cacheFile = getCacheFile(context, category, key)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                    Log.d(TAG, "Cleared cache for: $category/$key")
                }
            }
        }
    }
    
    /**
     * Get cache file path.
     */
    private fun getCacheFile(context: Context, category: String, key: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        val categoryDir = File(cacheDir, category)
        // Sanitize key to be a valid filename
        val sanitizedKey = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(categoryDir, "$sanitizedKey.json")
    }
    
    /**
     * Get cache size in bytes for a category or all cache.
     */
    fun getCacheSize(context: Context, category: String? = null): Long {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) return 0
        
        return if (category == null) {
            cacheDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
        } else {
            val categoryDir = File(cacheDir, category)
            if (categoryDir.exists()) {
                categoryDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
            } else {
                0L
            }
        }
    }
    
    /**
     * Cache a list of items with a single key.
     */
    suspend fun <T> cacheList(
        context: Context,
        category: String,
        key: String,
        data: List<T>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(context, category, key)
            cacheFile.parentFile?.mkdirs()
            
            val json = gson.toJson(data)
            FileWriter(cacheFile).use { writer ->
                writer.write(json)
            }
            
            Log.d(TAG, "Cached list: $category/$key (${data.size} items)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error caching list: $category/$key", e)
            false
        }
    }
    
    /**
     * Retrieve a cached list.
     */
    suspend fun <T> getCachedList(
        context: Context,
        category: String,
        key: String,
        type: Class<Array<T>>
    ): List<T>? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(context, category, key)
            if (!cacheFile.exists() || !cacheFile.isFile) {
                return@withContext null
            }
            
            FileReader(cacheFile).use { reader ->
                val array = gson.fromJson(reader, type)
                val list = array.toList()
                Log.d(TAG, "Retrieved cached list: $category/$key (${list.size} items)")
                return@withContext list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error retrieving cached list: $category/$key", e)
            null
        }
    }
}

