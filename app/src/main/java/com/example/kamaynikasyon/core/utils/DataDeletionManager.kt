package com.example.kamaynikasyon.core.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.kamaynikasyon.data.database.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

/**
 * Utility for deleting all user data from the app.
 * Handles deletion of:
 * - Room database (all tables)
 * - SharedPreferences (all preference files)
 * - Cache files
 * - Firebase data (user profile and progress collections)
 * - Signs out the user
 */
object DataDeletionManager {
    
    private const val TAG = "DataDeletionManager"
    
    /**
     * Delete all user data from the app.
     * This is a destructive operation that cannot be undone.
     * 
     * @param context Application context
     * @return Result indicating success or failure
     */
    suspend fun deleteAllUserData(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting data deletion process...")
            
            // 1. Delete Room database data
            deleteRoomDatabaseData(context)
            
            // 2. Clear SharedPreferences
            clearSharedPreferences(context)
            
            // 3. Clear cache files
            clearCacheFiles(context)
            
            // 4. Delete Firebase data (if user is logged in)
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                deleteFirebaseData(user.uid)
            }
            
            // 5. Sign out the user
            signOutUser(context)
            
            Log.d(TAG, "Data deletion completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during data deletion", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete all data from Room database.
     */
    private suspend fun deleteRoomDatabaseData(context: Context) {
        try {
            val db = AppDatabase.getInstance(context.applicationContext)
            
            // Delete all data from all tables
            db.userProfileDao().deleteAll()
            db.lessonProgressDao().deleteAll()
            db.quizProgressDao().deleteAll()
            db.dailyStreakProgressDao().deleteAll()
            db.pictureQuizProgressDao().deleteAll()
            db.gestureMatchProgressDao().deleteAll()
            db.spellingSequenceProgressDao().deleteAll()
            db.bubbleShooterProgressDao().deleteAll()
            
            Log.d(TAG, "Room database data deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Room database data", e)
            throw e
        }
    }
    
    /**
     * Clear all SharedPreferences files.
     */
    private fun clearSharedPreferences(context: Context) {
        try {
            // List of known SharedPreferences files
            val prefFiles = listOf(
                "app_settings",
                "user_progress",
                "kamaynikasyon_prefs",
                "camera_settings"
            )
            
            prefFiles.forEach { prefName ->
                try {
                    context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                    Log.d(TAG, "Cleared SharedPreferences: $prefName")
                } catch (e: Exception) {
                    Log.w(TAG, "Error clearing SharedPreferences: $prefName", e)
                }
            }
            
            Log.d(TAG, "SharedPreferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing SharedPreferences", e)
            throw e
        }
    }
    
    /**
     * Clear all cache files.
     */
    private fun clearCacheFiles(context: Context) {
        try {
            // Clear CacheManager cache
            CacheManager.clearCache(context)
            
            // Clear app cache directory
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    try {
                        if (file.isDirectory) {
                            file.deleteRecursively()
                        } else {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error deleting cache file: ${file.name}", e)
                    }
                }
            }
            
            Log.d(TAG, "Cache files cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache files", e)
            throw e
        }
    }
    
    /**
     * Delete Firebase data (user profile and progress collections).
     */
    private suspend fun deleteFirebaseData(uid: String) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = firestore.collection("users").document(uid)
            
            // Delete progress collections
            val progressCollections = listOf(
                "progress_lessons",
                "progress_quizzes",
                "progress_daily_streaks",
                "progress_bubble_shooter",
                "progress_picture_quiz",
                "progress_spelling_sequence",
                "progress_gesture_match",
                "metadata"
            )
            
            progressCollections.forEach { collectionName ->
                try {
                    val collectionRef = userDoc.collection(collectionName)
                    val documents = collectionRef.get().await()
                    
                    documents.documents.forEach { doc ->
                        doc.reference.delete().await()
                    }
                    
                    Log.d(TAG, "Deleted Firebase collection: $collectionName")
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting Firebase collection: $collectionName", e)
                    // Continue with other collections even if one fails
                }
            }
            
            // Delete user document
            try {
                userDoc.delete().await()
                Log.d(TAG, "Deleted Firebase user document")
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting Firebase user document", e)
                // Continue even if user document deletion fails
            }
            
            Log.d(TAG, "Firebase data deletion completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Firebase data", e)
            // Don't throw - Firebase deletion is best effort
        }
    }
    
    /**
     * Sign out the user from Firebase and Google.
     */
    private suspend fun signOutUser(context: Context) {
        try {
            val auth = FirebaseAuth.getInstance()
            auth.signOut()
            
            // Also sign out from Google if applicable
            try {
                // Get Google Sign-In Client ID from BuildConfig
                val clientId = com.example.kamaynikasyon.BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID
                
                if (clientId.isNotBlank()) {
                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                        com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                    )
                        .requestIdToken(clientId)
                        .requestEmail()
                        .build()
                    
                    val googleClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                    googleClient.signOut().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error signing out from Google", e)
                // Continue even if Google sign out fails
            }
            
            Log.d(TAG, "User signed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out user", e)
            throw e
        }
    }
}

