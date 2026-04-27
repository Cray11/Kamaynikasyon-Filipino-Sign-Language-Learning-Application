package com.example.kamaynikasyon.core.supabase

import android.content.Context
import com.example.kamaynikasyon.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    
    private var supabaseClient: SupabaseClient? = null
    
    // Storage bucket names (live)
    const val BUCKET_LESSONS = "lessons"
    const val BUCKET_QUIZZES = "quizzes"
    const val BUCKET_DICTIONARY = "dictionary"
    const val BUCKET_MINIGAMES = "minigames"
    const val BUCKET_MEDIA = "media"
    
    // Test bucket names
    const val BUCKET_LESSONS_TEST = "lessons_test"
    const val BUCKET_QUIZZES_TEST = "quizzes_test"
    const val BUCKET_DICTIONARY_TEST = "dictionary_test"
    const val BUCKET_MINIGAMES_TEST = "minigames_test"
    const val BUCKET_MEDIA_TEST = "media_test"
    
    /**
     * Gets the appropriate bucket name based on test mode setting
     * @param context Context to access SharedPreferences
     * @param bucketType One of the BUCKET_* constants (without _TEST suffix)
     * @return The bucket name (test or live) based on user setting
     */
    fun getBucket(context: Context, bucketType: String): String {
        val isTestMode = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("test_version_enabled", false)
        
        return when (bucketType) {
            BUCKET_LESSONS -> if (isTestMode) BUCKET_LESSONS_TEST else BUCKET_LESSONS
            BUCKET_QUIZZES -> if (isTestMode) BUCKET_QUIZZES_TEST else BUCKET_QUIZZES
            BUCKET_DICTIONARY -> if (isTestMode) BUCKET_DICTIONARY_TEST else BUCKET_DICTIONARY
            BUCKET_MINIGAMES -> if (isTestMode) BUCKET_MINIGAMES_TEST else BUCKET_MINIGAMES
            BUCKET_MEDIA -> if (isTestMode) BUCKET_MEDIA_TEST else BUCKET_MEDIA
            else -> bucketType // Return as-is if not recognized
        }
    }
    
    fun initialize(context: Context) {
        if (supabaseClient != null) return
        
        // Load Supabase credentials from BuildConfig (injected from local.properties at build time)
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
        
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            android.util.Log.w("SupabaseConfig", "Supabase credentials not configured. Content sync will be disabled.")
            android.util.Log.w("SupabaseConfig", "Please set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties")
            return
        }
        
        supabaseClient = createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey
        ) {
            install(Postgrest)
            install(Storage)
        }
        
        android.util.Log.d("SupabaseConfig", "Supabase initialized successfully")
    }
    
    fun getClient(): SupabaseClient? = supabaseClient
    
    fun isInitialized(): Boolean = supabaseClient != null
}

