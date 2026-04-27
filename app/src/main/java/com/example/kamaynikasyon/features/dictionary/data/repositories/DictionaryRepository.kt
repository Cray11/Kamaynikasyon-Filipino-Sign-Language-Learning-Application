package com.example.kamaynikasyon.features.dictionary.data.repositories

import android.content.Context
import android.util.Log
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.utils.CacheManager
import com.example.kamaynikasyon.core.utils.ErrorHandler
import com.google.gson.Gson
import com.example.kamaynikasyon.features.dictionary.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class DictionaryRepository(private val context: Context) {
    
    private var dictionaryIndex: DictionaryIndex? = null
    private val categoryDataCache = mutableMapOf<String, CategoryData?>()
    private val gson = Gson()
    private val TAG = "DictionaryRepository"
    
    private suspend fun loadDictionaryIndex(): DictionaryIndex? = withContext(Dispatchers.IO) {
        if (dictionaryIndex != null) {
            return@withContext dictionaryIndex
        }
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(context)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = context.assets.open("dictionary/index.json").bufferedReader().use { it.readText() }
                dictionaryIndex = gson.fromJson(jsonString, DictionaryIndex::class.java)
                Log.d(TAG, "Loaded dictionary index from assets (offline assets only mode)")
                dictionaryIndex
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
                    SupabaseConfig.BUCKET_DICTIONARY,
                    "index.json"
                )
                if (jsonString != null) {
                    dictionaryIndex = gson.fromJson(jsonString, DictionaryIndex::class.java)
                    // Cache for offline use
                    dictionaryIndex?.let { CacheManager.cacheData(context, "dictionary", "index", it) }
                    Log.d(TAG, "Loaded dictionary index from Supabase and cached")
                    return@withContext dictionaryIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load index from Supabase, trying cache", e)
            }
        }
        
        // Try persistent cache (useful when offline)
        if (!isOnline || !SupabaseConfig.isInitialized()) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_DICTIONARY}/index.json")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    dictionaryIndex = gson.fromJson(jsonString, DictionaryIndex::class.java)
                    Log.d(TAG, "Loaded dictionary index from SupabaseStorage cache (offline)")
                    return@withContext dictionaryIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            val cachedIndex = CacheManager.getCachedData(context, "dictionary", "index", DictionaryIndex::class.java)
            if (cachedIndex != null) {
                dictionaryIndex = cachedIndex
                Log.d(TAG, "Loaded dictionary index from CacheManager (offline)")
                return@withContext dictionaryIndex
            }
        }
        
        // Fallback to assets
        return@withContext try {
            val jsonString = context.assets.open("dictionary/index.json").bufferedReader().use { it.readText() }
            dictionaryIndex = gson.fromJson(jsonString, DictionaryIndex::class.java)
            // Cache assets data for future offline use
            dictionaryIndex?.let { CacheManager.cacheData(context, "dictionary", "index", it) }
            Log.d(TAG, "Loaded dictionary index from assets and cached")
            dictionaryIndex
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load index from assets", e)
            null
        }
    }
    
    private suspend fun loadCategoryData(fileName: String): CategoryData? = withContext(Dispatchers.IO) {
        // Check in-memory cache first
        categoryDataCache[fileName]?.let { return@withContext it }
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(context)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = context.assets.open("dictionary/$fileName").bufferedReader().use { it.readText() }
                val loadedData = gson.fromJson(jsonString, CategoryData::class.java)
                Log.d(TAG, "Loaded category data from assets (offline assets only mode): $fileName")
                loadedData
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load category data from assets: $fileName", e)
                null
            }
        }
        
        val isOnline = ErrorHandler.isOnline(context)
        
        // Load if not in cache
        val data = try {
            // Try Supabase first if available and online
            if (SupabaseConfig.isInitialized() && isOnline) {
                try {
                    val jsonString = SupabaseStorage.downloadTextFile(
                        context,
                        SupabaseConfig.BUCKET_DICTIONARY,
                        fileName
                    )
                    if (jsonString != null) {
                        val loadedData = gson.fromJson(jsonString, CategoryData::class.java)
                        // Cache for offline use
                        loadedData?.let { CacheManager.cacheData(context, "dictionary", fileName, it) }
                        Log.d(TAG, "Loaded category data from Supabase and cached: $fileName")
                        loadedData
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load category data from Supabase: $fileName, trying cache", e)
                    null
                }
            } else null
        } catch (e: Exception) {
            null
        } ?: run {
            // Try persistent cache when offline
            if (!isOnline || !SupabaseConfig.isInitialized()) {
                try {
                    val cacheFile = java.io.File(context.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_DICTIONARY}/$fileName")
                    if (cacheFile.exists() && cacheFile.isFile) {
                        val jsonString = cacheFile.readText()
                        val loadedData = gson.fromJson(jsonString, CategoryData::class.java)
                        Log.d(TAG, "Loaded category data from SupabaseStorage cache (offline): $fileName")
                        return@withContext loadedData
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load from SupabaseStorage cache, trying CacheManager", e)
                }
                
                val cachedData = CacheManager.getCachedData(context, "dictionary", fileName, CategoryData::class.java)
                if (cachedData != null) {
                    Log.d(TAG, "Loaded category data from CacheManager (offline): $fileName")
                    return@withContext cachedData
                }
            }
            
            // Fallback to assets
            try {
                val jsonString = context.assets.open("dictionary/$fileName").bufferedReader().use { it.readText() }
                val loadedData = gson.fromJson(jsonString, CategoryData::class.java)
                // Cache assets data for future offline use
                loadedData?.let { CacheManager.cacheData(context, "dictionary", fileName, it) }
                Log.d(TAG, "Loaded category data from assets and cached: $fileName")
                loadedData
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load category data from assets: $fileName", e)
                null
            }
        }
        
        // Cache the result (even if null to avoid repeated failed attempts)
        categoryDataCache[fileName] = data
        return@withContext data
    }
    
    suspend fun getAllCategories(): List<CategoryIndex> {
        val index = loadDictionaryIndex()
        return index?.categories ?: emptyList()
    }
    
    suspend fun getAllWords(): List<Word> = withContext(Dispatchers.IO) {
        val allWords = mutableListOf<Word>()
        val index = loadDictionaryIndex()
        index?.categories?.forEach { category ->
            loadCategoryData(category.file)?.words?.let { words ->
                allWords.addAll(words)
            }
        }
        return@withContext allWords
    }
    
    suspend fun getWordsByCategory(categoryFileName: String): List<Word> {
        // Find category by file name instead of id
        val index = loadDictionaryIndex()
        val category = index?.categories?.find { it.file == categoryFileName } ?: return emptyList()
        return loadCategoryData(category.file)?.words ?: emptyList()
    }
    
    suspend fun searchWords(query: String): List<Word> {
        if (query.isBlank()) return emptyList()
        
        val lowercaseQuery = query.lowercase()
        val allWords = getAllWords()
        return allWords.filter { word ->
            word.name.lowercase().contains(lowercaseQuery) ||
            word.description.lowercase().contains(lowercaseQuery) ||
            word.category.lowercase().contains(lowercaseQuery)
        }
    }
    
    suspend fun getTopSearchResults(query: String, limit: Int = 5): List<Word> {
        return searchWords(query).take(limit)
    }
}
