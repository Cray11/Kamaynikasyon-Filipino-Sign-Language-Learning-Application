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
import androidx.appcompat.app.AlertDialog
import com.example.kamaynikasyon.databinding.ActivityLoginBinding
import com.example.kamaynikasyon.features.auth.AuthViewModel
import com.example.kamaynikasyon.features.auth.AuthViewModelFactory
import com.example.kamaynikasyon.features.auth.data.AuthRepository
import com.example.kamaynikasyon.MainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

class LoginActivity : BaseActivity<ActivityLoginBinding>() {
    
    private val authRepository = AuthRepository()
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(authRepository)
    }
    private var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient? = null
    private var forgotPasswordDialog: AlertDialog? = null
    private var guestTermsDialog: AlertDialog? = null
    
    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
    
    override fun getViewBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }
    
    override fun setupUI() {
        setupGoogleSignIn()
        setupClickListeners()
        setupObservers()
    }
    
    private fun setupGoogleSignIn() {
        googleSignInClient = authRepository.createGoogleSignInClient(this)

        if (googleSignInClient == null) {
            binding.btnLoginWithGoogle.isEnabled = false
            binding.btnLoginWithGoogle.text = getString(R.string.google_services_unavailable)
            showToast(getString(R.string.google_services_missing_toast))
        }
    }
    
    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        
        binding.btnLoginWithGoogle.setOnClickListener {
            performGoogleLogin()
        }
        
        binding.btnContinueAsGuest.setOnClickListener {
            showGuestTermsDialog()
        }
        
        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        // Also set click listener for the individual TextView in case it's clicked directly
        binding.tvSignUp.getChildAt(1).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
        
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }
    
    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        if (email.isEmpty() || password.isEmpty()) {
            showError(R.string.error_fields_empty)
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(R.string.error_email_invalid)
            return
        }
        
        authViewModel.signInWithEmail(email, password)
    }
    
    private fun performGoogleLogin() {
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
                    CrashlyticsLogger.logAuthEvent("login", user.uid, success = true)
                    showToast(getString(R.string.success_login))
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is com.example.kamaynikasyon.features.auth.AuthState.Unauthenticated -> {
                    // User is not authenticated
                }
            }
        })
        
        authViewModel.isLoading.observe(this, Observer { isLoading ->
            binding.btnLogin.isEnabled = !isLoading
            binding.btnLoginWithGoogle.isEnabled = !isLoading
            binding.btnContinueAsGuest.isEnabled = !isLoading
            // You can also show/hide a progress indicator here
        })
        
        authViewModel.errorMessage.observe(this, Observer { errorMessage ->
            errorMessage?.let {
                // If forgot password dialog is showing, don't show toast here
                // The dialog's error observer will handle displaying the error
                if (forgotPasswordDialog?.isShowing != true) {
                    showToast(it)
                    authViewModel.clearError()
                }
                // If dialog is showing, let the dialog's observer handle clearing
            }
        })
        
        authViewModel.successMessage.observe(this, Observer { successMessage ->
            successMessage?.let {
                // Dismiss forgot password dialog if it's showing
                forgotPasswordDialog?.dismiss()
                forgotPasswordDialog = null
                showToast(it)
                authViewModel.clearSuccess()
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
                android.util.Log.e("LoginActivity", "Google Sign-In failed: ${e.statusCode} - ${e.message}", e)
                
                // Show specific error message based on error code
                val errorMessageResId = when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR -> {
                        // Error 10: Usually means SHA-1 fingerprint not registered in Firebase
                        android.util.Log.e("LoginActivity", "DEVELOPER_ERROR: SHA-1 fingerprint may not be registered in Firebase Console")
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
                        android.util.Log.e("LoginActivity", "Unknown Google Sign-In error code: ${e.statusCode}")
                        R.string.error_google_signin_failed
                    }
                }
                showError(errorMessageResId)
            }
        }
    }
    
    private fun showGuestTermsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_guest_terms, null)
        val checkbox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cb_accept_terms)
        val privacyLink = dialogView.findViewById<android.widget.TextView>(R.id.tv_privacy_link)
        val termsLink = dialogView.findViewById<android.widget.TextView>(R.id.tv_terms_link)
        val acceptButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_accept)
        var dialog: AlertDialog? = null
        
        privacyLink.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        
        termsLink.setOnClickListener {
            startActivity(Intent(this, TermsOfServiceActivity::class.java))
        }
        
        acceptButton.setOnClickListener {
            if (!checkbox.isChecked) {
                // Use a simple Toast so the message is visible even when a dialog is shown
                showToast(getString(R.string.error_terms_not_accepted))
                return@setOnClickListener
            }

            // User accepted terms, continue as guest
            dialog?.dismiss()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        
        dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        guestTermsDialog = dialog
        
        // Configure dialog window to match app style
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.5f)
            // Set dialog width to be 90% of screen width
            val displayMetrics = resources.displayMetrics
            setLayout(
                (displayMetrics.widthPixels * 0.9).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        dialog.setOnDismissListener { guestTermsDialog = null }
        dialog.show()
    }
    
    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_email)
        val emailInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_email)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val sendResetButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_send_reset)
        
        // Pre-fill email if available from login form
        val currentEmail = binding.etEmail.text.toString().trim()
        if (currentEmail.isNotEmpty()) {
            emailInput.setText(currentEmail)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Store dialog reference
        forgotPasswordDialog = dialog
        
        // Configure dialog window to match app style
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.5f)
            // Set dialog width to be 90% of screen width
            val displayMetrics = resources.displayMetrics
            setLayout(
                (displayMetrics.widthPixels * 0.9).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Track if we're waiting for a response
        var isProcessing = false
        
        cancelButton.setOnClickListener {
            if (!isProcessing) {
                dialog.dismiss()
                forgotPasswordDialog = null
            }
        }
        
        sendResetButton.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            
            val email = emailInput.text.toString().trim()
            
            if (email.isEmpty()) {
                emailInputLayout.error = getString(R.string.error_fields_empty)
                return@setOnClickListener
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInputLayout.error = getString(R.string.error_email_invalid)
                return@setOnClickListener
            }
            
            // Clear any previous error
            emailInputLayout.error = null
            
            // Disable button while processing
            isProcessing = true
            sendResetButton.isEnabled = false
            cancelButton.isEnabled = false
            sendResetButton.text = getString(R.string.action_sending)
            
            // Send password reset email
            authViewModel.resetPassword(email)
            
            // Set up one-time observers for this specific request
            val successObserver = object : Observer<String?> {
                override fun onChanged(successMessage: String?) {
                    if (successMessage != null && dialog.isShowing) {
                        authViewModel.successMessage.removeObserver(this)
                        // Dialog will be dismissed by the main observer
                    }
                }
            }
            
            val errorObserver = object : Observer<String?> {
                override fun onChanged(errorMessage: String?) {
                    if (errorMessage != null && dialog.isShowing) {
                        // Re-enable buttons on error
                        isProcessing = false
                        sendResetButton.isEnabled = true
                        cancelButton.isEnabled = true
                        sendResetButton.text = getString(R.string.action_send_reset_link)
                        
                        // Show error in the input field
                        emailInputLayout.error = errorMessage
                        
                        // Clear the error from ViewModel after displaying
                        authViewModel.clearError()
                        authViewModel.errorMessage.removeObserver(this)
                    }
                }
            }
            
            authViewModel.successMessage.observe(this, successObserver)
            authViewModel.errorMessage.observe(this, errorObserver)
        }
        
        dialog.setOnDismissListener {
            forgotPasswordDialog = null
        }
        
        dialog.show()
    }

    override fun onDestroy() {
        forgotPasswordDialog?.dismiss()
        forgotPasswordDialog = null
        guestTermsDialog?.dismiss()
        guestTermsDialog = null
        super.onDestroy()
    }
}
