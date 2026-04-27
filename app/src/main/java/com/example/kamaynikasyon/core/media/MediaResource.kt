package com.example.kamaynikasyon.core.media

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.Locale

@Parcelize
data class MediaResource(
    val type: MediaType,
    val path: String,
    val thumbnailPath: String? = null
) : Parcelable {

    fun isVideo(): Boolean = type == MediaType.VIDEO

    fun isImage(): Boolean = type == MediaType.IMAGE

    fun isAudio(): Boolean = type == MediaType.AUDIO

    fun asUri(): Uri {
        return toUri(path)
    }

    fun thumbnailUri(): Uri? = thumbnailPath?.let { toUri(it) }

    /**
     * Converts a relative media path to Supabase URL if Supabase is initialized
     * Otherwise returns the original path for asset loading
     * Uses test or live bucket based on user settings
     */
    fun toSupabaseUrl(context: Context? = null): String? {
        if (!SupabaseConfig.isInitialized() || context == null) {
            android.util.Log.d("MediaResource", "Supabase not initialized or context null for path: $path")
            return null
        }
        
        // If path is already a full URL, return as is
        if (path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)) {
            android.util.Log.d("MediaResource", "Path is already a full URL: $path")
            return path
        }
        
        // Get the appropriate bucket (test or live) based on user settings
        val bucket = SupabaseConfig.getBucket(context, SupabaseConfig.BUCKET_MEDIA)
        android.util.Log.d("MediaResource", "Using bucket: $bucket for path: $path")
        
        // Convert relative path to Supabase public URL
        val url = SupabaseStorage.getPublicUrl(bucket, path)
        android.util.Log.d("MediaResource", "Generated Supabase URL: $url")
        return url
    }

    private fun toUri(rawPath: String): Uri {
        return when {
            rawPath.startsWith("http://", ignoreCase = true) ||
                rawPath.startsWith("https://", ignoreCase = true) ||
                rawPath.startsWith("android.resource://", ignoreCase = true) ||
                rawPath.startsWith("file://", ignoreCase = true) -> {
                Uri.parse(rawPath)
            }
            else -> {
                val encoded = rawPath.replace(" ", "%20")
                Uri.parse("file:///android_asset/$encoded")
            }
        }
    }
    
    /**
     * Gets the URI for this media resource, preferring Supabase URL if available
     * Uses test or live bucket based on user settings
     */
    fun asUriWithSupabase(context: Context? = null): Uri {
        // Try Supabase URL first if available (context is required for test/live bucket selection)
        if (context != null) {
            val supabaseUrl = toSupabaseUrl(context)
            if (supabaseUrl != null) {
                return Uri.parse(supabaseUrl)
            }
        }
        
        // Fallback to standard URI conversion
        return asUri()
    }
}

enum class MediaType {
    @SerializedName("video")
    VIDEO,

    @SerializedName("image")
    IMAGE,

    @SerializedName("audio")
    AUDIO
}

fun String.toMediaResource(explicitType: MediaType? = null): MediaResource {
    val type = explicitType ?: MediaTypeGuesser.fromPath(this)
    return MediaResource(type = type, path = this)
}

private object MediaTypeGuesser {
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif")
    private val audioExtensions = setOf("mp3", "wav", "ogg", "m4a")

    fun fromPath(path: String): MediaType {
        val lowercase = path.lowercase(Locale.ROOT)

        return when {
            videoExtensions.any { lowercase.endsWith(".$it") } -> MediaType.VIDEO
            imageExtensions.any { lowercase.endsWith(".$it") } -> MediaType.IMAGE
            audioExtensions.any { lowercase.endsWith(".$it") } -> MediaType.AUDIO
            "video" in lowercase -> MediaType.VIDEO
            "image" in lowercase || "drawable" in lowercase -> MediaType.IMAGE
            "audio" in lowercase || "sound" in lowercase -> MediaType.AUDIO
            else -> MediaType.VIDEO
        }
    }
}

