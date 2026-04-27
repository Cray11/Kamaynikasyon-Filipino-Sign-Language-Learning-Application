package com.example.kamaynikasyon.core.supabase

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Data class for content metadata from Supabase
 */
@Serializable
data class ContentMetadata(
    val key: String,
    val bucket: String,
    val file_path: String,
    val content_hash: String,
    val file_size: Long,
    val updated_at: String,
    val is_test: Boolean = false
)

/**
 * Service for querying content metadata from Supabase
 * This allows fast sync checking without downloading entire JSON files
 */
object ContentMetadataService {
    private const val TAG = "ContentMetadataService"
    private const val TABLE_NAME = "content_metadata"
    
    /**
     * Gets metadata for a single file
     * 
     * @param bucket Storage bucket name
     * @param filePath Path to the JSON file
     * @return ContentMetadata if found, null otherwise
     */
    suspend fun getMetadata(bucket: String, filePath: String): ContentMetadata? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isInitialized()) {
            Log.d(TAG, "Supabase not initialized, cannot get metadata")
            return@withContext null
        }
        
        try {
            val client = SupabaseConfig.getClient() ?: return@withContext null
            
            // Create metadata key (same format as admin)
            val normalizedPath = filePath.replace("/", "_")
            val metadataKey = "${bucket}_${normalizedPath}"
            
            val result = client.from(TABLE_NAME)
                .select(columns = Columns.ALL) {
                    filter {
                        ContentMetadata::key eq metadataKey
                    }
                }
                .decodeSingle<ContentMetadata>()
            
            Log.d(TAG, "Retrieved metadata for $bucket/$filePath")
            return@withContext result
        } catch (e: io.github.jan.supabase.exceptions.NotFoundRestException) {
            // No metadata found - file might not have metadata yet
            Log.d(TAG, "No metadata found for $bucket/$filePath")
            return@withContext null
        } catch (e: Exception) {
            // Check if it's a 404/406 error by checking the message
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("404") || errorMessage.contains("406") || errorMessage.contains("not found", ignoreCase = true)) {
                Log.d(TAG, "No metadata found for $bucket/$filePath")
                return@withContext null
            }
            Log.w(TAG, "Error getting metadata for $bucket/$filePath", e)
            return@withContext null
        }
    }
    
    /**
     * Gets metadata for multiple files at once (bulk query)
     * 
     * @param files List of pairs (bucket, filePath)
     * @return Map of filePath to ContentMetadata
     */
    suspend fun getBulkMetadata(files: List<Pair<String, String>>): Map<String, ContentMetadata> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isInitialized() || files.isEmpty()) {
            return@withContext emptyMap()
        }
        
        try {
            val client = SupabaseConfig.getClient() ?: return@withContext emptyMap()
            
            // Build keys for all files
            val keys = files.map { (bucket, filePath) ->
                val normalizedPath = filePath.replace("/", "_")
                "${bucket}_${normalizedPath}"
            }
            
            // Query each key separately and combine results
            // (Supabase Kotlin doesn't support inList or OR in filter DSL)
            val allResults = mutableListOf<ContentMetadata>()
            keys.forEach { key ->
                try {
                    val result = client.from(TABLE_NAME)
                        .select(columns = Columns.ALL) {
                            filter {
                                ContentMetadata::key eq key
                            }
                        }
                        .decodeList<ContentMetadata>()
                    allResults.addAll(result)
                } catch (e: Exception) {
                    // Skip if not found - this is expected for some files
                    Log.d(TAG, "Metadata not found for key: $key")
                }
            }
            val results = allResults
            
            // Convert to map keyed by "bucket/filePath"
            val metadataMap = mutableMapOf<String, ContentMetadata>()
            results.forEach { metadata ->
                val key = "${metadata.bucket}/${metadata.file_path}"
                metadataMap[key] = metadata
            }
            
            Log.d(TAG, "Retrieved metadata for ${results.size} files")
            return@withContext metadataMap
        } catch (e: Exception) {
            Log.w(TAG, "Error getting bulk metadata", e)
            return@withContext emptyMap()
        }
    }
    
    /**
     * Calculates SHA-256 hash of a string
     * Used to compare local content with metadata hash
     */
    fun calculateHash(content: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Compares local content hash with metadata hash
     * 
     * @param localContent Local JSON content
     * @param metadataHash Hash from metadata
     * @return true if hashes match (content is the same)
     */
    fun isContentSame(localContent: String, metadataHash: String): Boolean {
        val localHash = calculateHash(localContent)
        return localHash == metadataHash
    }
}

