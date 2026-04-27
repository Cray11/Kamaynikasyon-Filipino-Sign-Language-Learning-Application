package com.example.kamaynikasyon.features.auth.activities

import android.content.Intent
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.utils.showToast
import com.example.kamaynikasyon.core.utils.showError
import com.example.kamaynikasyon.core.utils.CrashlyticsLogger
import com.example.kamaynikasyon.core.base.BaseActivity
import com.example.kamaynikasyon.features.privacy.PrivacyPolicyActivity
import com.example.kamaynikasyon.features.privacy.TermsOfServiceActivity
import com.example.kamaynikasyon.databinding.ActivitySignUpBinding
import com.example.kamaynikasyon.features.auth.AuthViewModel
import com.example.kamaynikasyon.features.auth.AuthViewModelFactory
import com.example.kamaynikasyon.features.auth.data.AuthRepository
import com.example.kamaynikasyon.MainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

class SignUpActivity : BaseActivity<ActivitySignUpBinding>() {
    
    private val authRepository = AuthRepository()
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(authRepository)
    }
    private var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient? = null
    
    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
    
    override fun getViewBinding(): ActivitySignUpBinding {
        return ActivitySignUpBinding.inflate(layoutInflater)
    }
    
    override fun setupUI() {
        setupGoogleSignIn()
        setupClickListeners()
        setupObservers()
    }
    
    private fun setupGoogleSignIn() {
        googleSignInClient = authRepository.createGoogleSignInClient(this)

        if (googleSignInClient == null) {
            binding.btnSignUpWithGoogle.isEnabled = false
            binding.btnSignUpWithGoogle.text = getString(R.string.google_services_unavailable)
            showToast(getString(R.string.google_services_missing_toast))
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSignUp.setOnClickListener {
            performSignUp()
        }
        
        binding.btnSignUpWithGoogle.setOnClickListener {
            performGoogleSignUp()
        }
        
        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        
        binding.tvPrivacyLink.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        
        binding.tvTermsLink.setOnClickListener {
            startActivity(Intent(this, TermsOfServiceActivity::class.java))
        }
    }
    
    private fun performSignUp() {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError(R.string.error_fields_empty)
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(R.string.error_email_invalid)
            return
        }
        
        if (password.length < 6) {
            showError(R.string.error_password_too_short)
            return
        }
        
        if (password != confirmPassword) {
            showError(R.string.error_password_mismatch)
            return
        }
        
        if (!binding.cbAcceptTerms.isChecked) {
            showError(R.string.error_terms_not_accepted)
            return
        }
        
        authViewModel.signUpWithEmail(email, password, fullName)
    }
    
    private fun performGoogleSignUp() {
        if (!binding.cbAcceptTerms.isChecked) {
            showError(R.string.error_terms_not_accepted)
            return
        }
        
        val client = googleSignInClient
        if (client == null) {
            showToast(getString(R.string.google_services_missing_toast))
            return
        }

        val signInIntent = client.signInIntent
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
    }
    
    override fun setupObservers() {
        authViewModel.authState.observe(this, Observer { authState ->
            when (authState) {
                is com.example.kamaynikasyon.features.auth.AuthState.Authenticated -> {
                    val user = authState.user
                    CrashlyticsLogger.setUserId(user.uid)
                    CrashlyticsLogger.logAuthEvent("signup", user.uid, success = true)
                    showToast(getString(R.string.success_signup))
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is com.example.kamaynikasyon.features.auth.AuthState.Unauthenticated -> {
                    // User is not authenticated
                }
            }
        })
        
        authViewModel.isLoading.observe(this, Observer { isLoading ->
            binding.btnSignUp.isEnabled = !isLoading
            binding.btnSignUpWithGoogle.isEnabled = !isLoading
            // You can also show/hide a progress indicator here
        })
        
        authViewModel.errorMessage.observe(this, Observer { errorMessage ->
            errorMessage?.let {
                showToast(it)
                authViewModel.clearError()
            }
        })
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = authRepository.getGoogleAuthCredential(account)
                authViewModel.signInWithGoogle(credential)
            } catch (e: ApiException) {
                // Log detailed error for debugging
                android.util.Log.e("SignUpActivity", "Google Sign-In failed: ${e.statusCode} - ${e.message}", e)
                
                // Show specific error message based on error code
                val errorMessageResId = when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR -> {
                        // Error 10: Usually means SHA-1 fingerprint not registered in Firebase
                        android.util.Log.e("SignUpActivity", "DEVELOPER_ERROR: SHA-1 fingerprint may not be registered in Firebase Console")
                        R.string.error_google_signin_developer
                    }
                    CommonStatusCodes.NETWORK_ERROR -> {
                        R.string.error_google_signin_network
                    }
                    CommonStatusCodes.INTERNAL_ERROR -> {
                        R.string.error_google_signin_internal
                    }
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> {
                        // User cancelled, don't show error
                        return
                    }
                    else -> {
                        android.util.Log.e("SignUpActivity", "Unknown Google Sign-In error code: ${e.statusCode}")
                        R.string.error_google_signin_failed
                    }
                }
                showError(errorMessageResId)
            }
        }
    }
}
