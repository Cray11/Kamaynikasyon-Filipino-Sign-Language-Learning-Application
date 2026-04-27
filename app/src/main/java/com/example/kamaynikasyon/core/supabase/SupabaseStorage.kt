package com.example.kamaynikasyon.core.supabase

import android.content.Context
import android.util.Log
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SupabaseStorage {
    
    private const val TAG = "SupabaseStorage"
    private const val CACHE_DIR = "supabase_cache"
    
    /**
     * Downloads a file from Supabase Storage and caches it locally
     * Returns the cached file path, or null if download fails
     * 
     * @param forceRefresh If true, bypasses cache and forces a fresh download from Supabase
     */
    suspend fun downloadFile(
        context: Context,
        bucket: String,
        path: String,
        forceRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.getClient() ?: return@withContext null
        
        try {
            val cacheFile = getCacheFile(context, bucket, path)
            
            // If forceRefresh is true, clear the cache first to ensure fresh download
            if (forceRefresh && cacheFile.exists()) {
                if (cacheFile.isFile) {
                    cacheFile.delete()
                    Log.d(TAG, "Cleared cached file for forced refresh: ${cacheFile.absolutePath}")
                } else if (cacheFile.isDirectory) {
                    cacheFile.deleteRecursively()
                    Log.d(TAG, "Cleared cached directory for forced refresh: ${cacheFile.absolutePath}")
                }
            }
            
            // Check cache first (only if not forcing refresh)
            if (!forceRefresh && cacheFile.exists() && cacheFile.isFile) {
                Log.d(TAG, "Using cached file: ${cacheFile.absolutePath}")
                return@withContext cacheFile.absolutePath
            }
            
            // Download from Supabase
            val data = client.storage.from(bucket).downloadPublic(path)
            
            // Save to cache (ensure parent directories exist and it's a file, not directory)
            cacheFile.parentFile?.mkdirs()
            // If cacheFile is a directory, delete it first
            if (cacheFile.exists() && cacheFile.isDirectory) {
                cacheFile.delete()
            }
            FileOutputStream(cacheFile).use { output ->
                output.write(data)
            }
            
            Log.d(TAG, "Downloaded and cached: ${cacheFile.absolutePath}")
            return@withContext cacheFile.absolutePath
        } catch (e: io.github.jan.supabase.exceptions.NotFoundRestException) {
            // File doesn't exist in Supabase - this is expected for some files, just log as warning
            Log.d(TAG, "File not found in Supabase (will use assets): $bucket/$path")
            return@withContext null
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading file from Supabase: $bucket/$path", e)
            return@withContext null
        }
    }
    
    /**
     * Downloads a text file (JSON) from Supabase Storage
     * Returns the content as String, or null if download fails
     * 
     * @param forceRefresh If true, bypasses cache and forces a fresh download from Supabase
     */
    suspend fun downloadTextFile(
        context: Context,
        bucket: String,
        path: String,
        forceRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.getClient() ?: return@withContext null
        
        try {
            val cacheFile = getCacheFile(context, bucket, path)
            
            // If forceRefresh is true, clear the cache first to ensure fresh download
            if (forceRefresh && cacheFile.exists()) {
                if (cacheFile.isFile) {
                    cacheFile.delete()
                    Log.d(TAG, "Cleared cached file for forced refresh: ${cacheFile.absolutePath}")
                } else if (cacheFile.isDirectory) {
                    cacheFile.deleteRecursively()
                    Log.d(TAG, "Cleared cached directory for forced refresh: ${cacheFile.absolutePath}")
                }
            }
            
            // Check cache first (only if not forcing refresh)
            // Also prefer cache if sync is in progress to avoid conflicts
            val syncInProgress = ContentSyncManager.isSyncInProgress(context)
            if (!forceRefresh && cacheFile.exists() && cacheFile.isFile) {
                Log.d(TAG, "Using cached text file: ${cacheFile.absolutePath}${if (syncInProgress) " (sync in progress)" else ""}")
                return@withContext cacheFile.readText()
            }
            
            // If sync is in progress and cache exists but we're here, it means forceRefresh=true
            // Still try to use cache as fallback if download fails
            var cachedContent: String? = null
            if (syncInProgress && cacheFile.exists() && cacheFile.isFile) {
                try {
                    cachedContent = cacheFile.readText()
                    Log.d(TAG, "Sync in progress - cached content available as fallback: $bucket/$path")
                } catch (e: Exception) {
                    Log.d(TAG, "Could not read cached content for fallback", e)
                }
            }
            
            // Download from Supabase
            val data = client.storage.from(bucket).downloadPublic(path)
            val content = String(data)
            
            // Save to cache (ensure parent directories exist and it's a file, not directory)
            cacheFile.parentFile?.mkdirs()
            // If cacheFile is a directory, delete it first
            if (cacheFile.exists() && cacheFile.isDirectory) {
                cacheFile.delete()
            }
            cacheFile.writeText(content)
            
            Log.d(TAG, "Downloaded and cached text file: ${cacheFile.absolutePath}")
            return@withContext content
        } catch (e: io.github.jan.supabase.exceptions.NotFoundRestException) {
            // File doesn't exist in Supabase - try cache as fallback if available
            val cacheFile = getCacheFile(context, bucket, path)
            if (cacheFile.exists() && cacheFile.isFile) {
                try {
                    val cachedContent = cacheFile.readText()
                    Log.d(TAG, "File not found in Supabase, using cached fallback: $bucket/$path")
                    return@withContext cachedContent
                } catch (e2: Exception) {
                    Log.d(TAG, "File not found in Supabase and cache read failed (will use assets): $bucket/$path")
                }
            } else {
                Log.d(TAG, "File not found in Supabase (will use assets): $bucket/$path")
            }
            return@withContext null
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading text file from Supabase: $bucket/$path", e)
            // Try cache as fallback if available
            val cacheFile = getCacheFile(context, bucket, path)
            if (cacheFile.exists() && cacheFile.isFile) {
                try {
                    val cachedContent = cacheFile.readText()
                    Log.d(TAG, "Supabase download failed, using cached fallback: $bucket/$path")
                    return@withContext cachedContent
                } catch (e2: Exception) {
                    Log.w(TAG, "Supabase download failed and cache read failed: $bucket/$path", e2)
                }
            }
            return@withContext null
        }
    }
    
    /**
     * Gets the public URL for a file in Supabase Storage
     */
    fun getPublicUrl(bucket: String, path: String): String? {
        val client = SupabaseConfig.getClient() ?: return null
        return try {
            client.storage.from(bucket).publicUrl(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting public URL: $bucket/$path", e)
            null
        }
    }
    
    /**
     * Checks if a file exists in cache
     */
    fun isCached(context: Context, bucket: String, path: String): Boolean {
        val cacheFile = getCacheFile(context, bucket, path)
        return cacheFile.exists()
    }
    
    /**
     * Clears the cache for a specific file or all files
     */
    fun clearCache(context: Context, bucket: String? = null, path: String? = null) {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) return
        
        if (bucket == null) {
            // Clear all cache
            cacheDir.deleteRecursively()
            Log.d(TAG, "Cleared all Supabase cache")
        } else {
            val bucketDir = File(cacheDir, bucket)
            if (path == null) {
                // Clear bucket cache
                bucketDir.deleteRecursively()
                Log.d(TAG, "Cleared cache for bucket: $bucket")
            } else {
                // Clear specific file cache
                val file = File(bucketDir, path)
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Cleared cache for file: $bucket/$path")
                }
            }
        }
    }
    
    private fun getCacheFile(context: Context, bucket: String, path: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        val bucketDir = File(cacheDir, bucket)
        return File(bucketDir, path)
    }
}

