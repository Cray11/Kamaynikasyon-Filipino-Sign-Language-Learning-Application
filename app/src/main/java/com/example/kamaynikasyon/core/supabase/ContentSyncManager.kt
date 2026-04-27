package com.example.kamaynikasyon.core.supabase

import android.content.Context
import android.os.Parcelable
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

@Parcelize
data class SyncDifference(
    val bucket: String,
    val fileName: String,
    val assetContent: String?,
    val supabaseContent: String?
) : Parcelable

@Parcelize
data class SyncResult(
    val hasDifferences: Boolean,
    val differences: List<SyncDifference> = emptyList()
) : Parcelable

object ContentSyncManager {
    
    private const val TAG = "ContentSyncManager"
    private const val PREFS_NAME = "content_sync_prefs"
    private const val KEY_DONT_SHOW_SYNC_DIALOG = "dont_show_sync_dialog"
    private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    private const val KEY_LAST_SYNC_CHECK = "last_sync_check"
    private const val KEY_SYNC_DECISION = "sync_decision_"
    private const val KEY_USE_OFFLINE_ASSETS_ONLY = "use_offline_assets_only"
    private const val KEY_SKIP_SYNC_CHECK_AFTER_RELOAD = "skip_sync_check_after_reload"
    private const val KEY_SYNC_IN_PROGRESS = "sync_in_progress"
    
    private val gson = Gson()
    
    /**
     * Checks if there are differences between asset index files and Supabase index files
     * Returns SyncResult with differences found
     * 
     * Comparison logic:
     * - If "use offline assets only" is enabled: returns no differences (skip sync check)
     * - If checkbox is checked (user synced before): compares cache with admin
     * - Otherwise (first time or checkbox not checked): compares assets with admin
     */
    suspend fun checkForDifferences(context: Context): SyncResult = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isInitialized()) {
            Log.d(TAG, "Supabase not initialized, skipping sync check")
            return@withContext SyncResult(hasDifferences = false)
        }
        
        // If user declined sync with checkbox, use offline assets only - skip sync check
        if (isUseOfflineAssetsOnly(context)) {
            Log.d(TAG, "User opted to use offline assets only, skipping sync check")
            return@withContext SyncResult(hasDifferences = false)
        }
        
        val differences = mutableListOf<SyncDifference>()
        
        // Always prefer cache if it exists (meaning we've synced before)
        // Only use assets if no cache exists (first time, never synced)
        // This ensures that after syncing, we compare cache with Supabase, not assets
        Log.d(TAG, "Checking for cached content to use for comparison...")
        
        // Check each bucket's index.json
        val buckets = listOf(
            SupabaseConfig.BUCKET_LESSONS to "lessons/index.json",
            SupabaseConfig.BUCKET_QUIZZES to "quizzes/index.json",
            SupabaseConfig.BUCKET_DICTIONARY to "dictionary/index.json"
        )
        
        // Check minigame indexes
        val minigameBuckets = listOf(
            "picture_quiz" to "minigames/picture_quiz/index.json",
            "spelling_sequence" to "minigames/spelling_sequence/index.json",
            "bubble_shooter" to "minigames/bubble_shooter/index.json",
            "gesture_match" to "minigames/gesture_match/index.json"
        )
        
        // Check regular buckets using metadata for fast comparison
        buckets.forEach { (bucket, assetPath) ->
            try {
                Log.d(TAG, "Checking $bucket/index.json...")
                // Always try cache first (if we've synced before, cache exists)
                // Fallback to assets only if no cache exists (first time)
                val cachedContent = getCachedFileContent(context, bucket, "index.json")
                val localContent = cachedContent ?: loadAssetFile(context, assetPath)
                val isUsingCache = cachedContent != null
                Log.d(TAG, "Using ${if (isUsingCache) "cache" else "assets"} for comparison: $bucket")
                Log.d(TAG, "Local content loaded for $bucket: ${localContent != null} (length: ${localContent?.length ?: 0})")
                
                // Try metadata-based comparison first (much faster)
                val metadata = ContentMetadataService.getMetadata(bucket, "index.json")
                if (metadata != null && localContent != null) {
                    // Compare hashes instead of downloading entire file
                    val isSame = ContentMetadataService.isContentSame(localContent, metadata.content_hash)
                    Log.d(TAG, "Metadata comparison for $bucket: isSame=$isSame")
                    
                    if (!isSame) {
                        // Hashes don't match - download file to get actual content
                        Log.d(TAG, "Hash mismatch, downloading file for comparison")
                        val supabaseContent = downloadTextFileFromSupabase(context, bucket, "index.json")
                        if (supabaseContent != null) {
                            differences.add(SyncDifference(bucket, "index.json", localContent, supabaseContent))
                            Log.d(TAG, "Difference found in $bucket/index.json")
                        }
                    } else {
                        Log.d(TAG, "No difference in $bucket/index.json - files are identical (metadata check)")
                        // Even if index.json is the same, check referenced files for content changes
                        // Use local content for referenced file checks
                        val supabaseContent = localContent // Use local as reference since they're the same
                        checkReferencedFiles(context, bucket, localContent, supabaseContent, differences, isUsingCache, assetPath)
                    }
                } else {
                    // Fallback to old method if metadata not available
                    Log.d(TAG, "Metadata not available, using direct download comparison")
                    val supabaseContent = downloadTextFileFromSupabase(context, bucket, "index.json")
                    Log.d(TAG, "Supabase content downloaded for $bucket: ${supabaseContent != null} (length: ${supabaseContent?.length ?: 0})")
                    
                    if (localContent != null && supabaseContent != null) {
                        val areEqual = areJsonEqual(localContent, supabaseContent)
                        Log.d(TAG, "Comparison result for $bucket: areEqual=$areEqual")
                        if (!areEqual) {
                            differences.add(SyncDifference(bucket, "index.json", localContent, supabaseContent))
                            Log.d(TAG, "Difference found in $bucket/index.json")
                        } else {
                            Log.d(TAG, "No difference in $bucket/index.json - files are identical")
                            checkReferencedFiles(context, bucket, localContent, supabaseContent, differences, isUsingCache, assetPath)
                        }
                    } else if (supabaseContent != null && localContent == null) {
                        differences.add(SyncDifference(bucket, "index.json", null, supabaseContent))
                        Log.d(TAG, "New content in Supabase for $bucket/index.json (local is null)")
                    } else if (localContent != null && supabaseContent == null) {
                        Log.d(TAG, "Local content exists but Supabase content is null for $bucket/index.json")
                    } else {
                        Log.d(TAG, "Both local and Supabase content are null for $bucket/index.json")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking $bucket/index.json", e)
            }
        }
        
        // Check minigame buckets using metadata for fast comparison
        minigameBuckets.forEach { (gameName, assetPath) ->
            try {
                Log.d(TAG, "Checking minigame $gameName/index.json...")
                // Always try cache first (if we've synced before, cache exists)
                // Fallback to assets only if no cache exists (first time)
                val cachedContent = getCachedFileContent(context, SupabaseConfig.BUCKET_MINIGAMES, "$gameName/index.json")
                val localContent = cachedContent ?: loadAssetFile(context, assetPath)
                val isUsingCache = cachedContent != null
                Log.d(TAG, "Using ${if (isUsingCache) "cache" else "assets"} for comparison: minigame $gameName")
                Log.d(TAG, "Local content loaded for minigame $gameName: ${localContent != null} (length: ${localContent?.length ?: 0})")
                
                // Try metadata-based comparison first (much faster)
                val metadata = ContentMetadataService.getMetadata(SupabaseConfig.BUCKET_MINIGAMES, "$gameName/index.json")
                if (metadata != null && localContent != null) {
                    // Compare hashes instead of downloading entire file
                    val isSame = ContentMetadataService.isContentSame(localContent, metadata.content_hash)
                    Log.d(TAG, "Metadata comparison for minigame $gameName: isSame=$isSame")
                    
                    if (!isSame) {
                        // Hashes don't match - download file to get actual content
                        Log.d(TAG, "Hash mismatch, downloading file for comparison")
                        val supabaseContent = downloadTextFileFromSupabase(
                            context,
                            SupabaseConfig.BUCKET_MINIGAMES,
                            "$gameName/index.json"
                        )
                        if (supabaseContent != null) {
                            differences.add(
                                SyncDifference(
                                    SupabaseConfig.BUCKET_MINIGAMES,
                                    "$gameName/index.json",
                                    localContent,
                                    supabaseContent
                                )
                            )
                            Log.d(TAG, "Difference found in minigames/$gameName/index.json")
                        }
                    } else {
                        Log.d(TAG, "No difference in minigames/$gameName/index.json - files are identical (metadata check)")
                        // Even if index.json is the same, check referenced level files for content changes
                        val supabaseContent = localContent // Use local as reference since they're the same
                        checkMinigameLevelFiles(context, gameName, localContent, supabaseContent, differences, isUsingCache, assetPath)
                    }
                } else {
                    // Fallback to old method if metadata not available
                    Log.d(TAG, "Metadata not available, using direct download comparison")
                    val supabaseContent = downloadTextFileFromSupabase(
                        context,
                        SupabaseConfig.BUCKET_MINIGAMES,
                        "$gameName/index.json"
                    )
                    Log.d(TAG, "Supabase content downloaded for minigame $gameName: ${supabaseContent != null} (length: ${supabaseContent?.length ?: 0})")
                    
                    if (localContent != null && supabaseContent != null) {
                        val areEqual = areJsonEqual(localContent, supabaseContent)
                        Log.d(TAG, "Comparison result for minigame $gameName: areEqual=$areEqual")
                        if (!areEqual) {
                            differences.add(
                                SyncDifference(
                                    SupabaseConfig.BUCKET_MINIGAMES,
                                    "$gameName/index.json",
                                    localContent,
                                    supabaseContent
                                )
                            )
                            Log.d(TAG, "Difference found in minigames/$gameName/index.json")
                        } else {
                            Log.d(TAG, "No difference in minigames/$gameName/index.json - files are identical")
                            checkMinigameLevelFiles(context, gameName, localContent, supabaseContent, differences, isUsingCache, assetPath)
                        }
                    } else if (supabaseContent != null && localContent == null) {
                        differences.add(
                            SyncDifference(
                                SupabaseConfig.BUCKET_MINIGAMES,
                                "$gameName/index.json",
                                null,
                                supabaseContent
                            )
                        )
                        Log.d(TAG, "New content in Supabase for minigames/$gameName/index.json (local is null)")
                    } else if (localContent != null && supabaseContent == null) {
                        Log.d(TAG, "Local content exists but Supabase content is null for minigame $gameName/index.json")
                    } else {
                        Log.d(TAG, "Both local and Supabase content are null for minigame $gameName/index.json")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking minigames/$gameName/index.json", e)
            }
        }
        
        val hasDifferences = differences.isNotEmpty()
        Log.d(TAG, "Sync check complete. Has differences: $hasDifferences, count: ${differences.size}")
        
        return@withContext SyncResult(hasDifferences, differences)
    }
    
    /**
     * Checks referenced files in index.json for content changes
     * This detects changes in lesson/quiz content even if index.json hasn't changed
     */
    private suspend fun checkReferencedFiles(
        context: Context,
        bucket: String,
        localIndexContent: String,
        supabaseIndexContent: String,
        differences: MutableList<SyncDifference>,
        useCacheForComparison: Boolean,
        assetPath: String
    ) {
        try {
            val indexJson = JSONObject(supabaseIndexContent)
            
            when (bucket) {
                SupabaseConfig.BUCKET_LESSONS -> {
                    val lessons = indexJson.optJSONArray("lessons")
                    lessons?.let {
                        for (i in 0 until it.length()) {
                            val lesson = it.getJSONObject(i)
                            val fileName = lesson.optString("file", "")
                            if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                                checkFileDifference(context, bucket, fileName, "lessons/$fileName", differences, useCacheForComparison)
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_QUIZZES -> {
                    val quizzes = indexJson.optJSONArray("quizzes")
                    quizzes?.let {
                        for (i in 0 until it.length()) {
                            val quiz = it.getJSONObject(i)
                            val fileName = quiz.optString("file", "")
                            if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                                checkFileDifference(context, bucket, fileName, "quizzes/$fileName", differences, useCacheForComparison)
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_DICTIONARY -> {
                    val categories = indexJson.optJSONArray("categories")
                    categories?.let {
                        for (i in 0 until it.length()) {
                            val category = it.getJSONObject(i)
                            val fileName = category.optString("file", "")
                            if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                                checkFileDifference(context, bucket, fileName, "dictionary/$fileName", differences, useCacheForComparison)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking referenced files for $bucket", e)
        }
    }
    
    /**
     * Checks minigame level files for content changes
     * This detects changes in level content even if the minigame index.json hasn't changed
     */
    private suspend fun checkMinigameLevelFiles(
        context: Context,
        gameName: String,
        localIndexContent: String,
        supabaseIndexContent: String,
        differences: MutableList<SyncDifference>,
        useCacheForComparison: Boolean,
        assetPath: String
    ) {
        try {
            val indexJson = JSONObject(supabaseIndexContent)
            val levels = indexJson.optJSONArray("levels")
            
            levels?.let {
                for (i in 0 until it.length()) {
                    val level = it.getJSONObject(i)
                    val fileName = level.optString("file", "")
                    if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                        // Minigame level path is: gameName/fileName (e.g., "picture_quiz/level1.json")
                        val levelPath = "$gameName/$fileName"
                        val assetLevelPath = assetPath.replace("index.json", fileName)
                        
                        // Always try cache first if available, fallback to assets
                        val cachedContent = getCachedFileContent(context, SupabaseConfig.BUCKET_MINIGAMES, levelPath)
                        val localContent = cachedContent ?: loadAssetFile(context, assetLevelPath)
                        
                        // Try metadata-based comparison first
                        val metadata = ContentMetadataService.getMetadata(SupabaseConfig.BUCKET_MINIGAMES, levelPath)
                        if (metadata != null && localContent != null) {
                            val isSame = ContentMetadataService.isContentSame(localContent, metadata.content_hash)
                            if (!isSame) {
                                // Hash mismatch - download file to get actual content
                                val supabaseContent = downloadTextFileFromSupabase(
                                    context,
                                    SupabaseConfig.BUCKET_MINIGAMES,
                                    levelPath
                                )
                                if (supabaseContent != null) {
                                    differences.add(
                                        SyncDifference(
                                            SupabaseConfig.BUCKET_MINIGAMES,
                                            levelPath,
                                            localContent,
                                            supabaseContent
                                        )
                                    )
                                    Log.d(TAG, "Difference found in minigame level file: $levelPath (metadata check)")
                                }
                            }
                            // If same, no need to add to differences
                        } else {
                            // Fallback to old method if metadata not available
                            val supabaseContent = downloadTextFileFromSupabase(
                                context,
                                SupabaseConfig.BUCKET_MINIGAMES,
                                levelPath
                            )
                            
                            if (localContent != null && supabaseContent != null) {
                                val areEqual = areJsonEqual(localContent, supabaseContent)
                                if (!areEqual) {
                                    differences.add(
                                        SyncDifference(
                                            SupabaseConfig.BUCKET_MINIGAMES,
                                            levelPath,
                                            localContent,
                                            supabaseContent
                                        )
                                    )
                                    Log.d(TAG, "Difference found in minigame level file: $levelPath")
                                }
                            } else if (supabaseContent != null && localContent == null) {
                                // New level file in Supabase
                                differences.add(
                                    SyncDifference(
                                        SupabaseConfig.BUCKET_MINIGAMES,
                                        levelPath,
                                        null,
                                        supabaseContent
                                    )
                                )
                                Log.d(TAG, "New level file in Supabase: $levelPath")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking minigame level files for $gameName", e)
        }
    }
    
    /**
     * Checks if a specific file has differences between local and Supabase
     * Uses metadata for fast comparison when available
     */
    private suspend fun checkFileDifference(
        context: Context,
        bucket: String,
        fileName: String,
        assetPath: String,
        differences: MutableList<SyncDifference>,
        useCacheForComparison: Boolean
    ) {
        try {
            // Always try cache first if available, fallback to assets
            val cachedContent = getCachedFileContent(context, bucket, fileName)
            val localContent = cachedContent ?: loadAssetFile(context, assetPath)
            
            // Try metadata-based comparison first
            val metadata = ContentMetadataService.getMetadata(bucket, fileName)
            if (metadata != null && localContent != null) {
                val isSame = ContentMetadataService.isContentSame(localContent, metadata.content_hash)
                if (!isSame) {
                    // Hash mismatch - download file to get actual content
                    val supabaseContent = downloadTextFileFromSupabase(context, bucket, fileName)
                    if (supabaseContent != null) {
                        differences.add(SyncDifference(bucket, fileName, localContent, supabaseContent))
                        Log.d(TAG, "Difference found in referenced file: $bucket/$fileName (metadata check)")
                    }
                }
                // If same, no need to add to differences
            } else {
                // Fallback to old method if metadata not available
                val supabaseContent = downloadTextFileFromSupabase(context, bucket, fileName)
                
                if (localContent != null && supabaseContent != null) {
                    val areEqual = areJsonEqual(localContent, supabaseContent)
                    if (!areEqual) {
                        differences.add(SyncDifference(bucket, fileName, localContent, supabaseContent))
                        Log.d(TAG, "Difference found in referenced file: $bucket/$fileName")
                    }
                } else if (supabaseContent != null && localContent == null) {
                    // New file in Supabase
                    differences.add(SyncDifference(bucket, fileName, null, supabaseContent))
                    Log.d(TAG, "New file in Supabase: $bucket/$fileName")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking file $bucket/$fileName", e)
        }
    }
    
    /**
     * Progress callback for sync operations
     */
    interface SyncProgressCallback {
        fun onProgress(current: Int, total: Int, fileName: String)
    }
    
    /**
     * Extracts all media paths from a JSON content string
     * Recursively searches through the JSON structure for media objects
     */
    private fun extractMediaPaths(jsonContent: String, mediaPaths: MutableSet<String>) {
        try {
            val json = JSONObject(jsonContent)
            extractMediaPathsFromJson(json, mediaPaths)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing JSON for media extraction", e)
        }
    }
    
    /**
     * Recursively extracts media paths from a JSON object
     */
    private fun extractMediaPathsFromJson(json: JSONObject, mediaPaths: MutableSet<String>) {
        try {
            // Check if this object has a "media" field
            if (json.has("media")) {
                val mediaObj = json.optJSONObject("media")
                if (mediaObj != null) {
                    val path = mediaObj.optString("path", "")
                    if (path.isNotBlank()) {
                        mediaPaths.add(path)
                    }
                }
            }
            
            // Recursively check all keys
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                
                when (value) {
                    is JSONObject -> {
                        extractMediaPathsFromJson(value, mediaPaths)
                    }
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            val item = value.opt(i)
                            if (item is JSONObject) {
                                extractMediaPathsFromJson(item, mediaPaths)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting media paths from JSON object", e)
        }
    }
    
    /**
     * Syncs content from Supabase by downloading and caching files
     * This will make the cached files available for subsequent app opens
     * 
     * @param downloadMedia If true, also downloads all videos and media files referenced in the JSON files
     * @param progressCallback Optional callback to report sync progress
     */
    suspend fun syncFromSupabase(
        context: Context, 
        differences: List<SyncDifference>,
        downloadMedia: Boolean = false,
        progressCallback: SyncProgressCallback? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isInitialized()) {
            Log.w(TAG, "Supabase not initialized, cannot sync")
            return@withContext false
        }
        
        try {
            // Mark sync as in progress to ensure fallbacks are used
            setSyncInProgress(context, true)
            
            // Calculate total files to sync accurately
            // Count only files that will actually be synced
            var totalFiles = 0
            differences.forEach { diff ->
                totalFiles++ // Count the difference file itself
                // If it's an index.json, also count the referenced files that will be synced
                if (diff.fileName.endsWith("index.json")) {
                    totalFiles += countReferencedFiles(diff.bucket, diff.supabaseContent)
                }
            }
            
            var currentFile = 0
            var successCount = 0
            var failCount = 0
            
            // Sync main difference files
            differences.forEach { diff ->
                currentFile++
                progressCallback?.onProgress(currentFile, totalFiles, diff.fileName)
                
                try {
                    // Download and cache the file with forceRefresh=true to ensure fresh content
                    // This bypasses cache and downloads the latest version from Supabase
                    val content = SupabaseStorage.downloadTextFile(context, diff.bucket, diff.fileName, forceRefresh = true)
                    if (content != null) {
                        successCount++
                        Log.d(TAG, "Synced ${diff.bucket}/${diff.fileName}")
                    } else {
                        failCount++
                        Log.w(TAG, "Failed to sync ${diff.bucket}/${diff.fileName}")
                    }
                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "Error syncing ${diff.bucket}/${diff.fileName}", e)
                }
            }
            
            // Also sync all files referenced in the index
            differences.forEach { diff ->
                if (diff.fileName.endsWith("index.json")) {
                    try {
                        syncIndexReferencedFiles(
                            context, 
                            diff.bucket, 
                            diff.supabaseContent, 
                            diff.fileName,
                            progressCallback,
                            currentFile,
                            totalFiles
                        )?.let { currentFile = it }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing files referenced in ${diff.bucket}/${diff.fileName}", e)
                    }
                }
            }
            
            // If downloadMedia is enabled, download all media files referenced in synced JSON files
            if (downloadMedia) {
                try {
                    val mediaPaths = mutableSetOf<String>()
                    
                    // Collect all media paths from synced JSON files
                    differences.forEach { diff ->
                        if (diff.fileName.endsWith(".json")) {
                            val jsonContent = diff.supabaseContent ?: getCachedFileContent(context, diff.bucket, diff.fileName)
                            jsonContent?.let { extractMediaPaths(it, mediaPaths) }
                        }
                    }
                    
                    // Also extract from referenced files
                    differences.forEach { diff ->
                        if (diff.fileName.endsWith("index.json")) {
                            try {
                                val indexJson = JSONObject(diff.supabaseContent ?: "")
                                when (diff.bucket) {
                                    SupabaseConfig.BUCKET_LESSONS -> {
                                        val lessons = indexJson.optJSONArray("lessons")
                                        lessons?.let {
                                            for (i in 0 until it.length()) {
                                                val lesson = it.getJSONObject(i)
                                                val fileName = lesson.optString("file", "")
                                                if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                                                    val content = getCachedFileContent(context, diff.bucket, fileName)
                                                    content?.let { extractMediaPaths(it, mediaPaths) }
                                                }
                                            }
                                        }
                                    }
                                    SupabaseConfig.BUCKET_QUIZZES -> {
                                        val quizzes = indexJson.optJSONArray("quizzes")
                                        quizzes?.let {
                                            for (i in 0 until it.length()) {
                                                val quiz = it.getJSONObject(i)
                                                val fileName = quiz.optString("file", "")
                                                if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                                                    val content = getCachedFileContent(context, diff.bucket, fileName)
                                                    content?.let { extractMediaPaths(it, mediaPaths) }
                                                }
                                            }
                                        }
                                    }
                                    SupabaseConfig.BUCKET_DICTIONARY -> {
                                        val categories = indexJson.optJSONArray("categories")
                                        categories?.let {
                                            for (i in 0 until it.length()) {
                                                val category = it.getJSONObject(i)
                                                val fileName = category.optString("file", "")
                                                if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                                                    val content = getCachedFileContent(context, diff.bucket, fileName)
                                                    content?.let { extractMediaPaths(it, mediaPaths) }
                                                }
                                            }
                                        }
                                    }
                                    SupabaseConfig.BUCKET_MINIGAMES -> {
                                        // Extract game name from fileName (e.g., "picture_quiz/index.json" -> "picture_quiz")
                                        val gameName = if (diff.fileName.contains("/")) {
                                            diff.fileName.substringBefore("/")
                                        } else {
                                            diff.fileName.replace("index.json", "").trimEnd('/')
                                        }
                                        if (gameName.isNotBlank()) {
                                            val levels = indexJson.optJSONArray("levels")
                                            levels?.let {
                                                for (i in 0 until it.length()) {
                                                    val level = it.getJSONObject(i)
                                                    val fileName = level.optString("file", "")
                                                    if (fileName.isNotBlank() && fileName.endsWith(".json")) {
                                                        val levelPath = "$gameName/$fileName"
                                                        val content = getCachedFileContent(context, SupabaseConfig.BUCKET_MINIGAMES, levelPath)
                                                        content?.let { extractMediaPaths(it, mediaPaths) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error extracting media from index: ${diff.fileName}", e)
                            }
                        }
                    }
                    
                    // Update total files count to include media files
                    totalFiles += mediaPaths.size
                    Log.d(TAG, "Found ${mediaPaths.size} unique media files to download")
                    
                    // Download all media files
                    mediaPaths.forEach { mediaPath ->
                        currentFile++
                        progressCallback?.onProgress(currentFile, totalFiles, "Media: $mediaPath")
                        
                        try {
                            val bucket = SupabaseConfig.getBucket(context, SupabaseConfig.BUCKET_MEDIA)
                            val cachedPath = SupabaseStorage.downloadFile(context, bucket, mediaPath, forceRefresh = true)
                            if (cachedPath != null) {
                                successCount++
                                Log.d(TAG, "Downloaded media: $mediaPath")
                            } else {
                                failCount++
                                Log.w(TAG, "Failed to download media: $mediaPath")
                            }
                        } catch (e: Exception) {
                            failCount++
                            Log.e(TAG, "Error downloading media: $mediaPath", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during media download", e)
                }
            }
            
            Log.d(TAG, "Sync complete. Success: $successCount, Failed: $failCount")
            // Mark sync as complete
            setSyncInProgress(context, false)
            return@withContext successCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
            // Mark sync as complete even on error
            setSyncInProgress(context, false)
            return@withContext false
        }
    }
    
    /**
     * Counts the number of files referenced in an index.json that will actually be synced
     * This matches what syncIndexReferencedFiles actually syncs (only .json files)
     */
    private fun countReferencedFiles(bucket: String, indexContent: String?): Int {
        if (indexContent == null) return 0
        
        return try {
            val indexJson = JSONObject(indexContent)
            var count = 0
            when (bucket) {
                SupabaseConfig.BUCKET_LESSONS -> {
                    val lessons = indexJson.optJSONArray("lessons")
                    lessons?.let {
                        for (i in 0 until it.length()) {
                            val lesson = it.getJSONObject(i)
                            val file = lesson.optString("file", "")
                            if (file.isNotBlank() && file.endsWith(".json")) {
                                count++
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_QUIZZES -> {
                    val quizzes = indexJson.optJSONArray("quizzes")
                    quizzes?.let {
                        for (i in 0 until it.length()) {
                            val quiz = it.getJSONObject(i)
                            val file = quiz.optString("file", "")
                            if (file.isNotBlank() && file.endsWith(".json")) {
                                count++
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_DICTIONARY -> {
                    val categories = indexJson.optJSONArray("categories")
                    categories?.let {
                        for (i in 0 until it.length()) {
                            val category = it.getJSONObject(i)
                            val file = category.optString("file", "")
                            if (file.isNotBlank() && file.endsWith(".json")) {
                                count++
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_MINIGAMES -> {
                    // Count minigame files that will actually be synced
                    indexJson.keys().forEach { key ->
                        val gameObj = indexJson.optJSONObject(key)
                        gameObj?.optJSONArray("levels")?.let { levels ->
                            for (i in 0 until levels.length()) {
                                val level = levels.getJSONObject(i)
                                val file = level.optString("file", "")
                                if (file.isNotBlank() && file.endsWith(".json")) {
                                    count++
                                }
                            }
                        }
                    }
                }
                else -> 0
            }
            count
        } catch (e: Exception) {
            Log.w(TAG, "Error counting referenced files", e)
            0
        }
    }
    
    /**
     * Syncs all files referenced in an index.json
     * 
     * @return Updated current file count after syncing referenced files
     */
    private suspend fun syncIndexReferencedFiles(
        context: Context, 
        bucket: String, 
        indexContent: String?, 
        fileName: String,
        progressCallback: SyncProgressCallback? = null,
        currentFile: Int = 0,
        totalFiles: Int = 0
    ): Int? {
        if (indexContent == null) return currentFile
        
        var fileCount = currentFile
        
        try {
            val indexJson = JSONObject(indexContent)
            
            // Extract file references based on bucket type
            when (bucket) {
                SupabaseConfig.BUCKET_LESSONS -> {
                    val lessons = indexJson.optJSONArray("lessons")
                    lessons?.let {
                        for (i in 0 until it.length()) {
                            val lesson = it.getJSONObject(i)
                            val file = lesson.optString("file", "")
                            if (file.isNotBlank() && file.endsWith(".json")) {
                                fileCount++
                                progressCallback?.onProgress(fileCount, totalFiles, file)
                                try {
                                    SupabaseStorage.downloadTextFile(context, bucket, file, forceRefresh = true)
                                } catch (e: Exception) {
                                    Log.d(TAG, "Could not sync lesson file: $file (will use assets if available)")
                                }
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_QUIZZES -> {
                    val quizzes = indexJson.optJSONArray("quizzes")
                    quizzes?.let {
                        for (i in 0 until it.length()) {
                            val quiz = it.getJSONObject(i)
                            val file = quiz.optString("file", "")
                            if (file.isNotBlank() && file.endsWith(".json")) {
                                fileCount++
                                progressCallback?.onProgress(fileCount, totalFiles, file)
                                try {
                                    SupabaseStorage.downloadTextFile(context, bucket, file, forceRefresh = true)
                                } catch (e: Exception) {
                                    Log.d(TAG, "Could not sync quiz file: $file (will use assets if available)")
                                }
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_DICTIONARY -> {
                    val categories = indexJson.optJSONArray("categories")
                    categories?.let {
                        for (i in 0 until it.length()) {
                            val category = it.getJSONObject(i)
                            val file = category.optString("file", "")
                            if (file.isNotBlank() && file.endsWith(".json")) {
                                fileCount++
                                progressCallback?.onProgress(fileCount, totalFiles, file)
                                try {
                                    SupabaseStorage.downloadTextFile(context, bucket, file, forceRefresh = true)
                                } catch (e: Exception) {
                                    Log.d(TAG, "Could not sync dictionary file: $file (will use assets if available)")
                                }
                            }
                        }
                    }
                }
                SupabaseConfig.BUCKET_MINIGAMES -> {
                    val levels = indexJson.optJSONArray("levels")
                    levels?.let {
                        // Extract game name from the fileName (e.g., "picture_quiz/index.json" -> "picture_quiz")
                        val gameName = fileName.substringBefore("/")
                        if (gameName.isBlank()) {
                            Log.w(TAG, "Could not extract game name from fileName: $fileName")
                            return@let
                        }
                        
                        for (i in 0 until it.length()) {
                            val level = it.getJSONObject(i)
                            val file = level.optString("file", "")
                            if (file.isNotBlank() && file.endsWith(".json")) {
                                // Construct path: gameName/file (e.g., "picture_quiz/level1.json")
                                val levelPath = "$gameName/$file"
                                fileCount++
                                progressCallback?.onProgress(fileCount, totalFiles, levelPath)
                                try {
                                    SupabaseStorage.downloadTextFile(context, bucket, levelPath, forceRefresh = true)
                                } catch (e: Exception) {
                                    Log.d(TAG, "Could not sync level file: $levelPath (will use assets if available)")
                                }
                            }
                        }
                    }
                }
            }
            
            return fileCount
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing index to sync referenced files", e)
            return fileCount
        }
    }
    
    /**
     * Checks if user has already decided to skip sync for this session
     */
    fun shouldShowSyncDialog(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check if user selected "don't show again"
        if (prefs.getBoolean(KEY_DONT_SHOW_SYNC_DIALOG, false)) {
            return false
        }
        
        val lastCheck = prefs.getLong(KEY_LAST_SYNC_CHECK, 0)
        val now = System.currentTimeMillis()
        
        // Show dialog if:
        // 1. Never checked before, OR
        // 2. Last check was more than 24 hours ago
        return lastCheck == 0L || (now - lastCheck) > 24 * 60 * 60 * 1000
    }
    
    /**
     * Sets whether to show the sync dialog in the future
     */
    fun setDontShowSyncDialog(context: Context, dontShow: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DONT_SHOW_SYNC_DIALOG, dontShow)
            .apply()
    }
    
    /**
     * Checks if "don't show again" is enabled
     */
    fun isDontShowSyncDialogEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DONT_SHOW_SYNC_DIALOG, false)
    }
    
    /**
     * Sets whether to automatically sync in the future (without showing dialog)
     */
    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_SYNC_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Checks if auto sync is enabled
     */
    fun isAutoSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }
    
    /**
     * Sets whether to use offline assets only (when user declines sync with checkbox)
     */
    fun setUseOfflineAssetsOnly(context: Context, useOfflineOnly: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USE_OFFLINE_ASSETS_ONLY, useOfflineOnly)
            .apply()
    }
    
    /**
     * Checks if user opted to use offline assets only
     */
    fun isUseOfflineAssetsOnly(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_OFFLINE_ASSETS_ONLY, false)
    }
    
    /**
     * Records that user has seen the sync dialog
     */
    fun recordSyncCheck(context: Context, userDecision: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_SYNC_CHECK, System.currentTimeMillis())
            .putString(KEY_SYNC_DECISION + System.currentTimeMillis(), userDecision)
            .apply()
    }
    
    /**
     * Clears sync check record (for testing or force re-check)
     */
    fun clearSyncCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_SYNC_CHECK).apply()
    }
    
    /**
     * Sets a flag to skip sync check on next app start (used after successful sync and reload)
     */
    fun setSkipSyncCheckAfterReload(context: Context, skip: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SKIP_SYNC_CHECK_AFTER_RELOAD, skip)
            .apply()
    }
    
    /**
     * Checks if sync check should be skipped after reload
     */
    fun shouldSkipSyncCheckAfterReload(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SKIP_SYNC_CHECK_AFTER_RELOAD, false)
    }
    
    /**
     * Clears the skip sync check flag (should be called after checking it)
     */
    fun clearSkipSyncCheckAfterReload(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_SKIP_SYNC_CHECK_AFTER_RELOAD)
            .apply()
    }
    
    /**
     * Sets whether syncing is currently in progress
     * This is used to ensure fallbacks (cache/local) are used during sync
     */
    fun setSyncInProgress(context: Context, inProgress: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SYNC_IN_PROGRESS, inProgress)
            .apply()
        Log.d(TAG, "Sync in progress set to: $inProgress")
    }
    
    /**
     * Checks if syncing is currently in progress
     * This helps repositories decide whether to use cache/fallbacks instead of trying Supabase
     */
    fun isSyncInProgress(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SYNC_IN_PROGRESS, false)
    }
    
    /**
     * Verifies and clears stale sync status if needed
     * Should be called on app start to ensure sync status is accurate
     * If sync was in progress but app was closed/crashed, clear the flag
     */
    fun verifyAndClearStaleSyncStatus(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val syncInProgress = prefs.getBoolean(KEY_SYNC_IN_PROGRESS, false)
        if (syncInProgress) {
            // If sync was marked as in progress, it means app might have crashed during sync
            // Clear the flag to allow normal operation (repositories will use cache/fallbacks)
            Log.w(TAG, "Found stale sync in progress flag, clearing it")
            setSyncInProgress(context, false)
        }
    }
    
    /**
     * Gets cached file content if it exists, otherwise returns null
     */
    private fun getCachedFileContent(context: Context, bucket: String, path: String): String? {
        return try {
            val cacheDir = java.io.File(context.cacheDir, "supabase_cache")
            val bucketDir = java.io.File(cacheDir, bucket)
            val cacheFile = java.io.File(bucketDir, path)
            if (cacheFile.exists() && cacheFile.isFile) {
                cacheFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "No cached file found for $bucket/$path")
            null
        }
    }
    
    /**
     * Downloads text file from Supabase without using cache (for comparison purposes)
     */
    private suspend fun downloadTextFileFromSupabase(context: Context, bucket: String, path: String): String? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.getClient() ?: return@withContext null
        
        return@withContext try {
            // Download directly from Supabase without checking cache
            val data = client.storage.from(bucket).downloadPublic(path)
            String(data)
        } catch (e: io.github.jan.supabase.exceptions.NotFoundRestException) {
            Log.d(TAG, "File not found in Supabase: $bucket/$path")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading text file from Supabase: $bucket/$path", e)
            null
        }
    }
    
    private fun loadAssetFile(context: Context, path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        }
    }
    
    /**
     * Compares two JSON strings for equality (ignoring whitespace and order)
     * Uses Gson to normalize both JSON strings for accurate comparison
     */
    private fun areJsonEqual(json1: String, json2: String): Boolean {
        return try {
            // Parse and re-stringify both JSONs to normalize formatting
            val obj1 = JSONObject(json1)
            val obj2 = JSONObject(json2)
            
            // Compare normalized JSON strings
            val normalized1 = obj1.toString()
            val normalized2 = obj2.toString()
            
            val areEqual = normalized1 == normalized2
            if (!areEqual) {
                // Log first 200 chars of each for debugging
                Log.d(TAG, "JSON comparison failed. First 200 chars of json1: ${normalized1.take(200)}")
                Log.d(TAG, "JSON comparison failed. First 200 chars of json2: ${normalized2.take(200)}")
            }
            areEqual
        } catch (e: Exception) {
            Log.w(TAG, "Error comparing JSON, falling back to string comparison", e)
            // If parsing fails, do simple string comparison after trimming
            json1.trim() == json2.trim()
        }
    }
}

