package com.example.kamaynikasyon.features.auth.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    
    fun isUserLoggedIn(): Boolean = auth.currentUser != null
    
    suspend fun signUpWithEmail(
        email: String, 
        password: String, 
        fullName: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            
            if (user != null) {
                // Update user profile with display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()
                user.updateProfile(profileUpdates).await()
                
                // Save additional user data to Firestore
                saveUserToFirestore(user.uid, fullName, email)
                
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to create user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to sign in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signInWithGoogle(credential: AuthCredential): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            
            if (user != null) {
                // Save user data to Firestore if it's a new user
                if (result.additionalUserInfo?.isNewUser == true) {
                    saveUserToFirestore(
                        user.uid, 
                        user.displayName ?: "User", 
                        user.email ?: ""
                    )
                }
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to sign in with Google"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOutAll(
        context: Context,
        revokeAccess: Boolean = true
    ): Result<Unit> {
        val firebaseResult = runCatching { auth.signOut() }
        if (firebaseResult.isFailure) {
            return Result.failure(firebaseResult.exceptionOrNull()!!)
        }

        val googleClient = runCatching { createGoogleSignInClient(context) }.getOrNull()
        if (googleClient != null) {
            runCatching {
                if (revokeAccess) {
                    googleClient.revokeAccess().await()
                } else {
                    googleClient.signOut().await()
                }
            }.onFailure { error ->
                android.util.Log.w(
                    "AuthRepository",
                    "Google sign-out failed but Firebase session cleared",
                    error
                )
            }
        }

        return Result.success(Unit)
    }
    
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getTodayDateKey(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(calendar.time)
    }
    
    private suspend fun saveUserToFirestore(uid: String, fullName: String, email: String) {
        try {
            val now = FieldValue.serverTimestamp()
            val todayDateKey = getTodayDateKey()
            
            val userData = hashMapOf(
                "uid" to uid,
                "fullName" to fullName,
                "email" to email,
                "createdAt" to now,
                "lastLoginAt" to now,
                "loginDates" to arrayListOf(todayDateKey) // Initialize with today's date
            )
            firestore.collection("users").document(uid).set(userData).await()
        } catch (e: Exception) {
            // Log error but don't fail the authentication
            println("Failed to save user to Firestore: ${e.message}")
        }
    }
    
    suspend fun updateLastLogin(uid: String) {
        try {
            val now = FieldValue.serverTimestamp()
            val todayDateKey = getTodayDateKey()
            
            // Update both lastLoginAt (for backward compatibility) and add to loginDates array
            firestore.collection("users").document(uid)
                .update(
                    "lastLoginAt", now,
                    "loginDates", FieldValue.arrayUnion(todayDateKey)
                ).await()
        } catch (e: Exception) {
            println("Failed to update last login: ${e.message}")
        }
    }
    
    fun createGoogleSignInClient(context: Context): GoogleSignInClient? {
        val availability = GoogleApiAvailabilityLight.getInstance()
            .isGooglePlayServicesAvailable(context)

        if (availability != ConnectionResult.SUCCESS) {
            println("Google Play services unavailable (code: $availability). Google sign-in disabled.")
            return null
        }

        // Get Google Sign-In Client ID from BuildConfig (injected from local.properties at build time)
        val clientId = com.example.kamaynikasyon.BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID
        
        if (clientId.isBlank()) {
            println("Google Sign-In Client ID not configured. Google sign-in disabled.")
            return null
        }
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        
        return GoogleSignIn.getClient(context, gso)
    }
    
    fun getGoogleAuthCredential(account: GoogleSignInAccount): AuthCredential {
        return GoogleAuthProvider.getCredential(account.idToken, null)
    }
}
