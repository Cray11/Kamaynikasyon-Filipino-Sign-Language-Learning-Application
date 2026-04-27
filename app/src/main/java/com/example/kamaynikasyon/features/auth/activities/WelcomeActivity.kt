package com.example.kamaynikasyon.features.auth.activities

import android.content.Intent
import com.example.kamaynikasyon.BuildConfig
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.base.BaseActivity
import com.example.kamaynikasyon.databinding.ActivityWelcomeBinding
import com.example.kamaynikasyon.MainActivity
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {
    
    override fun getViewBinding(): ActivityWelcomeBinding {
        return ActivityWelcomeBinding.inflate(layoutInflater)
    }
    
    override fun setupUI() {
        // Check if user is already logged in
        checkAuthState()
        setupClickListeners()
        displayAppVersion()
    }
    
    private fun checkAuthState() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // User is already logged in, redirect to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    private fun setupClickListeners() {
        binding.btnGetStarted.setOnClickListener {
            startActivity(Intent(this, GetStartedActivity::class.java))
        }
        
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
    
    private fun displayAppVersion() {
        val versionText = BuildConfig.VERSION_NAME
            .takeIf { it.isNotBlank() }
            ?.let { getString(R.string.app_version_format, it) }
            ?: getString(R.string.app_version_placeholder)
        binding.tvAppVersion.text = versionText
    }
    
    override fun setupObservers() {
        // Setup any observers here
    }
}
