package com.example.kamaynikasyon.core.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AdminAccessRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "AdminAccessRepository"
        private const val COLLECTION_ADMINS = "admins"
        private const val FIELD_EMAIL = "email"
    }

    suspend fun isAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false

        val normalizedEmail = email.trim().lowercase()

        return try {
            // 1. Try checking a document whose ID matches the email (structure shown in screenshot)
            val adminDoc = firestore.collection(COLLECTION_ADMINS)
                .document(normalizedEmail)
                .get()
                .await()

            if (adminDoc.exists()) {
                true
            } else {
                // 2. Fall back to the previous structure that stores the email in a field
                val snapshot = firestore.collection(COLLECTION_ADMINS)
                    .whereEqualTo(FIELD_EMAIL, normalizedEmail)
                    .limit(1)
                    .get()
                    .await()

                snapshot != null && !snapshot.isEmpty
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify admin access", e)
            false
        }
    }
}

