package com.example.kamaynikasyon.features.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kamaynikasyon.features.auth.data.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import javax.inject.Inject

class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage
    
    init {
        checkAuthState()
    }
    
    private fun checkAuthState() {
        if (authRepository.isUserLoggedIn()) {
            authRepository.getCurrentUser()?.let { user ->
                _authState.value = AuthState.Authenticated(user)
            } ?: run {
                _authState.value = AuthState.Unauthenticated
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }
    
    fun signUpWithEmail(email: String, password: String, fullName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            authRepository.signUpWithEmail(email, password, fullName)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    _isLoading.value = false
                }
                .onFailure { exception ->
                    _errorMessage.value = exception.message ?: "Sign up failed"
                    _isLoading.value = false
                }
        }
    }
    
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            authRepository.signInWithEmail(email, password)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    _isLoading.value = false
                    // Update last login time
                    user.uid.let { uid ->
                        authRepository.updateLastLogin(uid)
                    }
                }
                .onFailure { exception ->
                    _errorMessage.value = exception.message ?: "Sign in failed"
                    _isLoading.value = false
                }
        }
    }
    
    fun signInWithGoogle(credential: com.google.firebase.auth.AuthCredential) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            authRepository.signInWithGoogle(credential)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    _isLoading.value = false
                    // Update last login time
                    user.uid.let { uid ->
                        authRepository.updateLastLogin(uid)
                    }
                }
                .onFailure { exception ->
                    _errorMessage.value = exception.message ?: "Google sign in failed"
                    _isLoading.value = false
                }
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            
            authRepository.signOut()
                .onSuccess {
                    _authState.value = AuthState.Unauthenticated
                    _isLoading.value = false
                }
                .onFailure { exception ->
                    _errorMessage.value = exception.message ?: "Sign out failed"
                    _isLoading.value = false
                }
        }
    }
    
    fun resetPassword(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            
            authRepository.resetPassword(email)
                .onSuccess {
                    _isLoading.value = false
                    _successMessage.value = "Password reset email sent! Please check your inbox."
                }
                .onFailure { exception ->
                    _errorMessage.value = exception.message ?: "Password reset failed"
                    _isLoading.value = false
                }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun clearSuccess() {
        _successMessage.value = null
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
}
