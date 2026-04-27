package com.example.kamaynikasyon.features.settings

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import androidx.core.view.ViewCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.kamaynikasyon.BuildConfig
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.core.firebase.AdminAccessRepository
import com.example.kamaynikasyon.core.notifications.ReminderScheduler
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.supabase.SyncDialog
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.utils.ErrorHandler
import com.example.kamaynikasyon.core.utils.CacheManager
import com.example.kamaynikasyon.core.utils.showError
import com.example.kamaynikasyon.core.utils.DataDeletionManager
import com.example.kamaynikasyon.features.privacy.PrivacyPolicyActivity
import com.example.kamaynikasyon.features.privacy.TermsOfServiceActivity
import com.example.kamaynikasyon.features.auth.activities.LoginActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    
    // UI Elements
    private lateinit var switchNotifications: Switch
    private lateinit var switchVibrations: Switch
    private lateinit var switchTestVersion: Switch
    private lateinit var btnSyncContent: com.google.android.material.button.MaterialButton
    private lateinit var btnClearCache: com.google.android.material.button.MaterialButton
    private lateinit var btnResetProgress: com.google.android.material.button.MaterialButton
    private lateinit var tvAppVersion: TextView
    private lateinit var tvTestVersionNote: TextView
    
    // Sync check variables
    private var syncCheckJob: Job? = null
    private var syncCheckView: View? = null
    private var cancelSyncDialog: AlertDialog? = null
    
    // Camera Settings UI Elements
    private lateinit var detectionThresholdMinus: ImageButton
    private lateinit var detectionThresholdPlus: ImageButton
    private lateinit var detectionThresholdValue: TextView
    private lateinit var trackingThresholdMinus: ImageButton
    private lateinit var trackingThresholdPlus: ImageButton
    private lateinit var trackingThresholdValue: TextView
    private lateinit var maxHandsMinus: ImageButton
    private lateinit var maxHandsPlus: ImageButton
    private lateinit var maxHandsValue: TextView
    
    // Collapsible sections
    private lateinit var cameraSettingsHeader: LinearLayout
    private lateinit var cameraSettingsContent: LinearLayout
    private lateinit var ivCameraSettingsArrow: ImageView
    private lateinit var creditsHeader: LinearLayout
    private lateinit var creditsContent: LinearLayout
    private lateinit var ivCreditsArrow: ImageView
    // REMOVED: editProfileHeader and edit profile logic

    private val adminAccessRepository by lazy { AdminAccessRepository() }
    private var isAdminUser: Boolean = false
    private var currentTestVersionState: Boolean = false
    private var isVerifyingAdminAccess: Boolean = false

    companion object {
        private const val TAG = "SettingsActivity"
        
        // Camera settings keys
        private const val KEY_DETECTION_THRESHOLD = "detection_threshold"
        private const val KEY_TRACKING_THRESHOLD = "tracking_threshold"
        private const val KEY_MAX_HANDS = "max_hands"
        
        // Default values
        private const val DEFAULT_DETECTION_THRESHOLD = 0.5f
        private const val DEFAULT_TRACKING_THRESHOLD = 0.5f
        private const val DEFAULT_MAX_HANDS = 1
        
        // Broadcast action for progress reset
        const val ACTION_PROGRESS_RESET = "com.example.kamaynikasyon.PROGRESS_RESET"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set up Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide the default title
        
        // Set back button click listener
        findViewById<android.widget.ImageButton>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        initializeViews()
        setupClickListeners()
        loadSettings()
        configureTestVersionSwitchListener()
        loadCameraSettings()
        refreshAdminAccess()
    }

    private fun initializeViews() {
        switchNotifications = findViewById(R.id.switch_notifications)
        switchVibrations = findViewById(R.id.switch_vibrations)
        switchTestVersion = findViewById(R.id.switch_test_version)
        btnSyncContent = findViewById(R.id.btn_sync_content)
        btnClearCache = findViewById(R.id.btn_clear_cache)
        btnResetProgress = findViewById(R.id.btn_reset_progress)
        tvAppVersion = findViewById(R.id.tv_app_version)
        tvTestVersionNote = findViewById(R.id.tv_test_version_note)
        
        // Camera settings views
        detectionThresholdMinus = findViewById(R.id.detection_threshold_minus)
        detectionThresholdPlus = findViewById(R.id.detection_threshold_plus)
        detectionThresholdValue = findViewById(R.id.detection_threshold_value)
        trackingThresholdMinus = findViewById(R.id.tracking_threshold_minus)
        trackingThresholdPlus = findViewById(R.id.tracking_threshold_plus)
        trackingThresholdValue = findViewById(R.id.tracking_threshold_value)
        maxHandsMinus = findViewById(R.id.max_hands_minus)
        maxHandsPlus = findViewById(R.id.max_hands_plus)
        maxHandsValue = findViewById(R.id.max_hands_value)
        
        // Collapsible sections
        cameraSettingsHeader = findViewById(R.id.camera_settings_header)
        cameraSettingsContent = findViewById(R.id.camera_settings_content)
        ivCameraSettingsArrow = findViewById(R.id.iv_camera_settings_arrow)
        creditsHeader = findViewById(R.id.credits_header)
        creditsContent = findViewById(R.id.credits_content)
        ivCreditsArrow = findViewById(R.id.iv_credits_arrow)
        // REMOVED: editProfileHeader and edit profile logic
    }

    private fun setupClickListeners() {
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked)
        }
        
        switchVibrations.setOnCheckedChangeListener { _, isChecked ->
            saveVibrationSetting(isChecked)
        }
        btnSyncContent.setOnClickListener { checkAndSyncContent() }
        btnClearCache.setOnClickListener { showClearCacheDialog() }
        btnResetProgress.setOnClickListener { showResetProgressDialog() }
        
        // Privacy and Terms click listeners
        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            startActivity(android.content.Intent(this, PrivacyPolicyActivity::class.java))
        }
        
        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            startActivity(android.content.Intent(this, TermsOfServiceActivity::class.java))
        }
        
        findViewById<TextView>(R.id.tv_delete_account_data).setOnClickListener {
            showDeleteAccountDataDialog()
        }
        
        // Camera settings click listeners
        setupCameraSettingsListeners()
        
        // Collapsible sections click listeners
        setupCollapsibleSections()
    }

    private fun setupCameraSettingsListeners() {
        // Detection threshold controls
        detectionThresholdMinus.setOnClickListener {
            val currentValue = getDetectionThreshold()
            if (currentValue >= 0.2f) {
                val newValue = (currentValue - 0.1f).coerceAtLeast(0.1f)
                setDetectionThreshold(newValue)
                updateDetectionThresholdDisplay(newValue)
            }
        }
        
        detectionThresholdPlus.setOnClickListener {
            val currentValue = getDetectionThreshold()
            if (currentValue <= 0.8f) {
                val newValue = (currentValue + 0.1f).coerceAtMost(0.9f)
                setDetectionThreshold(newValue)
                updateDetectionThresholdDisplay(newValue)
            }
        }
        
        // Tracking threshold controls
        trackingThresholdMinus.setOnClickListener {
            val currentValue = getTrackingThreshold()
            if (currentValue >= 0.2f) {
                val newValue = (currentValue - 0.1f).coerceAtLeast(0.1f)
                setTrackingThreshold(newValue)
                updateTrackingThresholdDisplay(newValue)
            }
        }
        
        trackingThresholdPlus.setOnClickListener {
            val currentValue = getTrackingThreshold()
            if (currentValue <= 0.8f) {
                val newValue = (currentValue + 0.1f).coerceAtMost(0.9f)
                setTrackingThreshold(newValue)
                updateTrackingThresholdDisplay(newValue)
            }
        }
        
        // Max hands controls
        maxHandsMinus.setOnClickListener {
            val currentValue = getMaxHands()
            if (currentValue > 1) {
                val newValue = currentValue - 1
                setMaxHands(newValue)
                updateMaxHandsDisplay(newValue)
            }
        }
        
        maxHandsPlus.setOnClickListener {
            val currentValue = getMaxHands()
            if (currentValue < 2) {
                val newValue = currentValue + 1
                setMaxHands(newValue)
                updateMaxHandsDisplay(newValue)
            }
        }
    }

    private fun loadSettings() {
        try {
            // Load notification setting
            val notificationsEnabled = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getBoolean("notifications_enabled", true)
            switchNotifications.isChecked = notificationsEnabled
            // Load vibration setting
            val vibrationEnabled = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getBoolean("vibration_enabled", true)
            switchVibrations.isChecked = vibrationEnabled
            // Load test version setting
            val testVersionEnabled = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getBoolean("test_version_enabled", false)
            currentTestVersionState = testVersionEnabled
            switchTestVersion.isChecked = currentTestVersionState
            // Set app version from PackageManager (works across SDK versions)
            val versionName = try {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) {
                null
            }
            val versionLabel = versionName
                ?.takeIf { it.isNotBlank() }
                ?.let { getString(R.string.app_version_format, it) }
                ?: getString(R.string.app_version_placeholder)
            tvAppVersion.text = versionLabel
            
            Log.d(TAG, "Settings loaded successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings", e)
            showError(R.string.error_loading_settings)
        }
    }

    private fun loadCameraSettings() {
        try {
            val detectionThreshold = getDetectionThreshold()
            val trackingThreshold = getTrackingThreshold()
            val maxHands = getMaxHands()

            updateDetectionThresholdDisplay(detectionThreshold)
            updateTrackingThresholdDisplay(trackingThreshold)
            updateMaxHandsDisplay(maxHands)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading camera settings", e)
            showError(R.string.error_loading_camera_settings)
        }
    }

    private fun refreshAdminAccess() {
        lifecycleScope.launch {
            val email = FirebaseAuth.getInstance().currentUser?.email
            val isAdmin = withContext(Dispatchers.IO) {
                adminAccessRepository.isAdmin(email)
            }
            updateTestVersionAccessState(isAdmin)
        }
    }

    private fun updateTestVersionAccessState(isAdmin: Boolean) {
        isAdminUser = isAdmin
        if (isAdmin) {
            tvTestVersionNote.visibility = View.GONE
        } else {
            tvTestVersionNote.visibility = View.VISIBLE
            tvTestVersionNote.text = getString(R.string.test_version_admin_only)
        }
        configureTestVersionSwitchListener()
    }

    private fun setupCollapsibleSections() {
        // Camera settings collapsible
        cameraSettingsHeader.setOnClickListener {
            toggleCameraSettings()
        }
        
        // Credits collapsible
        creditsHeader.setOnClickListener {
            toggleCredits()
        }
        
        // REMOVED: editProfileHeader.setOnClickListener { showEditProfileDialog() }
    }

    private fun toggleCameraSettings() {
        if (cameraSettingsContent.visibility == View.VISIBLE) {
            // Hide content - arrow points right (collapsed)
            cameraSettingsContent.visibility = View.GONE
            ivCameraSettingsArrow.rotation = 90f
        } else {
            // Show content - arrow points down (expanded)
            cameraSettingsContent.visibility = View.VISIBLE
            ivCameraSettingsArrow.rotation = 0f
        }
    }

    private fun toggleCredits() {
        if (creditsContent.visibility == View.VISIBLE) {
            // Hide content - arrow points right (collapsed)
            creditsContent.visibility = View.GONE
            ivCreditsArrow.rotation = 90f
        } else {
            // Show content - arrow points down (expanded)
            creditsContent.visibility = View.VISIBLE
            ivCreditsArrow.rotation = 0f
        }
    }

    private fun showClearCacheDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear Cache")
            .setMessage("This will clear all cached content (lessons, quizzes, dictionary, minigames, and media files). The app will need to download content again when you use it. This does not affect your progress or settings.")
            .setPositiveButton("Clear") { _, _ ->
                clearCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCache() {
        try {
            // Clear CacheManager cache (lessons, quizzes, dictionary)
            CacheManager.clearCache(this)
            
            // Clear SupabaseStorage cache (media files, JSON files)
            SupabaseStorage.clearCache(this)
            
            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
            showError(R.string.error_clearing_cache)
        }
    }

    private fun showResetProgressDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Progress")
            .setMessage("Are you sure you want to reset your lesson progress? This will clear all completed lessons but keep your profile information.")
            .setPositiveButton("Reset") { _, _ ->
                resetUserProgress()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetUserProgress() {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.resetting_progress)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.lessonProgressDao().deleteAll()
                    db.quizProgressDao().deleteAll()
                    db.dailyStreakProgressDao().deleteAll()
                    db.bubbleShooterProgressDao().deleteAll()
                    db.spellingSequenceProgressDao().deleteAll()
                    db.pictureQuizProgressDao().deleteAll()
                    db.gestureMatchProgressDao().deleteAll()

                    getSharedPreferences("user_progress", MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                }

                loadingDialog.dismiss()
                
                // Send broadcast to notify all UI components
                val broadcastIntent = Intent(ACTION_PROGRESS_RESET)
                LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(broadcastIntent)
                
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.success_progress_reset),
                    Toast.LENGTH_SHORT
                ).show()
                Log.d(TAG, "Progress reset completed and broadcast sent")
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e(TAG, "Error resetting progress", e)
                showError(R.string.error_progress_reset)
            }
        }
    }

    private fun showDeleteAccountDataDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_account_data_title)
            .setMessage(R.string.delete_account_data_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteAllAccountData()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun deleteAllAccountData() {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.deleting_data)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                val result = DataDeletionManager.deleteAllUserData(this@SettingsActivity)
                
                loadingDialog.dismiss()
                
                if (result.isSuccess) {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.success_data_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    Log.d(TAG, "Account data deleted successfully")
                    
                    // Navigate to login screen
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    showError(R.string.error_data_deletion_failed)
                    Log.e(TAG, "Failed to delete account data", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showError(R.string.error_data_deletion_failed)
                Log.e(TAG, "Error deleting account data", e)
            }
        }
    }

    private fun showTestVersionDialog(targetState: Boolean) {
        val mode = if (targetState) "Test" else "Live"
        val explanation = if (targetState) {
            "Test Version allows you to access test content that is being developed and tested before being released to all users.\n\n" +
            "When enabled:\n" +
            "• You will see test lessons, quizzes, and minigames\n" +
            "• Content may be incomplete or experimental\n" +
            "• This is useful for testing new features\n\n" +
            "Do you want to enable Test Version?"
        } else {
            "Switching back to Live Version will:\n\n" +
            "• Show only released, stable content\n" +
            "• Hide all test content\n" +
            "• Use the production content buckets\n\n" +
            "Do you want to switch to Live Version?"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Switch to $mode Version")
            .setMessage(explanation)
            .setPositiveButton("Yes") { _, _ ->
                applyTestVersionState(targetState)
            }
            .setNegativeButton("Cancel") { _, _ ->
                switchTestVersion.isChecked = currentTestVersionState
                configureTestVersionSwitchListener()
            }
            .setOnCancelListener {
                switchTestVersion.isChecked = currentTestVersionState
                configureTestVersionSwitchListener()
            }
            .show()
    }
    
    private fun applyTestVersionState(enabled: Boolean) {
        currentTestVersionState = enabled
        saveTestVersionSetting(enabled)
        switchTestVersion.isChecked = currentTestVersionState
        configureTestVersionSwitchListener()
    }
    
    private fun configureTestVersionSwitchListener() {
        switchTestVersion.setOnCheckedChangeListener(null)
        switchTestVersion.isChecked = currentTestVersionState
        switchTestVersion.setOnCheckedChangeListener { _, isChecked ->
            handleTestVersionToggleRequest(isChecked)
        }
    }
    
    private fun handleTestVersionToggleRequest(desiredState: Boolean) {
        if (desiredState == currentTestVersionState || isVerifyingAdminAccess) {
            switchTestVersion.setOnCheckedChangeListener(null)
            switchTestVersion.isChecked = currentTestVersionState
            configureTestVersionSwitchListener()
            return
        }
        
        switchTestVersion.setOnCheckedChangeListener(null)
        switchTestVersion.isChecked = currentTestVersionState
        switchTestVersion.jumpDrawablesToCurrentState()
        verifyAdminAccessForTestVersion(desiredState)
    }
    
    private fun verifyAdminAccessForTestVersion(targetState: Boolean) {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Verifying Access")
            .setMessage("Checking admin permissions...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        isVerifyingAdminAccess = true
        
        lifecycleScope.launch {
            try {
                val email = FirebaseAuth.getInstance().currentUser?.email
                val hasAccess = withContext(Dispatchers.IO) {
                    adminAccessRepository.isAdmin(email)
                }
                loadingDialog.dismiss()
                isVerifyingAdminAccess = false
                
                if (hasAccess) {
                    isAdminUser = true
                    tvTestVersionNote.visibility = View.GONE
                    showTestVersionDialog(targetState)
                } else {
                    isAdminUser = false
                    tvTestVersionNote.visibility = View.VISIBLE
                    tvTestVersionNote.text = getString(R.string.test_version_admin_only)
                    MaterialAlertDialogBuilder(this@SettingsActivity)
                        .setTitle("Access Denied")
                        .setMessage(getString(R.string.test_version_admin_only))
                        .setPositiveButton("OK", null)
                        .show()
                    configureTestVersionSwitchListener()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                isVerifyingAdminAccess = false
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.error_loading_settings),
                    Toast.LENGTH_LONG
                ).show()
                configureTestVersionSwitchListener()
            }
        }
    }

    private fun updateDetectionThresholdDisplay(value: Float) {
        detectionThresholdValue.text = String.format("%.1f", value)
    }

    private fun updateTrackingThresholdDisplay(value: Float) {
        trackingThresholdValue.text = String.format("%.1f", value)
    }

    private fun updateMaxHandsDisplay(value: Int) {
        maxHandsValue.text = value.toString()
    }

    private fun getDetectionThreshold(): Float {
        return getSharedPreferences("camera_settings", MODE_PRIVATE)
            .getFloat(KEY_DETECTION_THRESHOLD, DEFAULT_DETECTION_THRESHOLD)
    }

    private fun setDetectionThreshold(value: Float) {
        getSharedPreferences("camera_settings", MODE_PRIVATE)
            .edit()
            .putFloat(KEY_DETECTION_THRESHOLD, value)
            .apply()
        Log.d(TAG, "Detection threshold set to: $value")
    }

    private fun getTrackingThreshold(): Float {
        return getSharedPreferences("camera_settings", MODE_PRIVATE)
            .getFloat(KEY_TRACKING_THRESHOLD, DEFAULT_TRACKING_THRESHOLD)
    }

    private fun setTrackingThreshold(value: Float) {
        getSharedPreferences("camera_settings", MODE_PRIVATE)
            .edit()
            .putFloat(KEY_TRACKING_THRESHOLD, value)
            .apply()
        Log.d(TAG, "Tracking threshold set to: $value")
    }

    private fun getMaxHands(): Int {
        return getSharedPreferences("camera_settings", MODE_PRIVATE)
            .getInt(KEY_MAX_HANDS, DEFAULT_MAX_HANDS)
    }

    private fun setMaxHands(value: Int) {
        getSharedPreferences("camera_settings", MODE_PRIVATE)
            .edit()
            .putInt(KEY_MAX_HANDS, value)
            .apply()
        Log.d(TAG, "Max hands set to: $value")
    }

    private fun persistTestVersionState(enabled: Boolean) {
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("test_version_enabled", enabled)
            .apply()
    }

    private fun saveNotificationSetting(enabled: Boolean) {
        try {
            getSharedPreferences("app_settings", MODE_PRIVATE)
                .edit()
                .putBoolean("notifications_enabled", enabled)
                .apply()
            
            val message = if (enabled) getString(R.string.success_settings_saved) else getString(R.string.success_settings_saved)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            
            Log.d(TAG, "Notification setting saved: $enabled")
            // Apply scheduling immediately
            if (enabled) {
                ReminderScheduler.scheduleDailyStreakReminder(this)
            } else {
                ReminderScheduler.cancelDailyStreakReminder(this)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification setting", e)
            showError(R.string.error_saving_settings)
        }
    }

    private fun saveVibrationSetting(enabled: Boolean) {
        try {
            getSharedPreferences("app_settings", MODE_PRIVATE)
                .edit()
                .putBoolean("vibration_enabled", enabled)
                .apply()
            val message = getString(R.string.success_settings_saved)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Vibration setting saved: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving vibration setting", e)
            showError(R.string.error_saving_settings)
        }
    }

    private fun saveTestVersionSetting(enabled: Boolean) {
        try {
            persistTestVersionState(enabled)
            val message = getString(R.string.success_settings_saved)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Test version setting saved: $enabled")
            
            // Note: Content will be reloaded from the correct bucket on next sync or app restart
            // You may want to clear caches here to force immediate reload
        } catch (e: Exception) {
            Log.e(TAG, "Error saving test version setting", e)
            showError(R.string.error_saving_settings)
        }
    }

    private fun checkAndSyncContent() {
        // Check if Supabase is initialized
        if (!com.example.kamaynikasyon.core.supabase.SupabaseConfig.isInitialized()) {
            showError(R.string.error_content_sync_unavailable)
            return
        }
        
        // Check if user has opted to use offline assets only
        if (ContentSyncManager.isUseOfflineAssetsOnly(this)) {
            // Show dialog to inform user and allow them to enable sync checks again
            MaterialAlertDialogBuilder(this)
                .setTitle("Offline Assets Only Mode")
                .setMessage("You have enabled 'offline assets only' mode. Sync checks are disabled. Would you like to enable sync checks again?")
                .setPositiveButton("Enable Sync Checks") { _, _ ->
                    // Clear the offline assets only preference
                    ContentSyncManager.setUseOfflineAssetsOnly(this, false)
                    // Now check for sync
                    performSyncCheck()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        
        // Check internet connectivity
        if (!ErrorHandler.isOnline(this)) {
            Toast.makeText(this, "No internet connection. Please check your connection and try again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        performSyncCheck()
    }
    
    private fun performSyncCheck() {
        // Disable button while checking to prevent double-taps
        btnSyncContent.isEnabled = false
        btnSyncContent.text = getString(R.string.sync_checking_for_updates)
        
        // Show loading indicator
        showSyncCheckSnackbar()
        
        syncCheckJob = lifecycleScope.launch {
            try {
                val syncResult = ContentSyncManager.checkForDifferences(this@SettingsActivity)
                
                // Hide loading indicator
                hideSyncCheckSnackbar()
                
                // Only update UI and show dialog if activity is still in a valid state
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    btnSyncContent.isEnabled = true
                    btnSyncContent.text = getString(R.string.sync_content_button_label)
                    
                    if (syncResult.hasDifferences) {
                        // Always show sync dialog when called from settings (don't auto-sync)
                        // This allows user to manually control sync from settings
                        val dialog = SyncDialog.newInstance(syncResult)
                        dialog.show(supportFragmentManager, "SyncDialog")
                    } else {
                        Toast.makeText(this@SettingsActivity, getString(R.string.success_content_up_to_date), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for content sync", e)
                // Hide loading indicator on error
                hideSyncCheckSnackbar()
                // Only update UI if activity is still in a valid state
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    btnSyncContent.isEnabled = true
                    btnSyncContent.text = getString(R.string.sync_content_button_label)
                    val rootView = findViewById<View>(android.R.id.content) ?: window.decorView.rootView
                    ErrorHandler.showError(
                        rootView,
                        R.string.error_content_sync_failed,
                        R.string.action_retry,
                        View.OnClickListener { checkAndSyncContent() }
                    )
                }
            }
        }
    }
    
    private fun showSyncCheckSnackbar() {
        // Hide any existing view
        hideSyncCheckSnackbar()
        
        // Get the root view to overlay the indicator
        val rootView = findViewById<ViewGroup>(android.R.id.content) ?: return
        
        // Inflate the layout
        val syncView = LayoutInflater.from(this).inflate(R.layout.view_sync_check_indicator, null)
        
        // Setup click listeners
        syncView.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            showCancelSyncDialog()
        }
        
        syncView.findViewById<TextView>(R.id.btn_close).setOnClickListener {
            hideSyncCheckSnackbar()
            Toast.makeText(this, "Content checking will continue in the background", Toast.LENGTH_SHORT).show()
        }
        
        // Position at bottom with margins (consistent with MainActivity)
        val marginBottom = resources.getDimensionPixelSize(R.dimen.sync_snackbar_margin_bottom)
        val marginHorizontal = resources.getDimensionPixelSize(R.dimen.sync_snackbar_margin_horizontal)
        
        // Use FrameLayout params to overlay at the bottom
        val layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = marginBottom
            leftMargin = marginHorizontal
            rightMargin = marginHorizontal
            gravity = android.view.Gravity.BOTTOM
        }
        syncView.layoutParams = layoutParams
        
        // Add elevation (4dp - consistent with MainActivity)
        val elevationDp = 2f
        val elevationPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            elevationDp,
            resources.displayMetrics
        )
        ViewCompat.setElevation(syncView, elevationPx)
        
        // Add as overlay to root view (won't push content)
        rootView.addView(syncView)
        syncCheckView = syncView
        
        // Animate in
        syncView.alpha = 0f
        syncView.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }
    
    private fun hideSyncCheckSnackbar() {
        syncCheckView?.let { view ->
            view.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    (view.parent as? ViewGroup)?.removeView(view)
                }
                .start()
        }
        syncCheckView = null
    }
    
    private fun showCancelSyncDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sync_cancel, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.checkbox_offline_assets)
        
        cancelSyncDialog?.dismiss()
        cancelSyncDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Sync Check?")
            .setView(dialogView)
            .setPositiveButton("Continue") { dialog, _ ->
                dialog.dismiss()
                // Re-show the snackbar since user wants to continue
                if (syncCheckJob?.isActive == true) {
                    showSyncCheckSnackbar()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // Cancel the sync check
                syncCheckJob?.cancel()
                syncCheckJob = null
                
                // If checkbox is checked, set offline assets only
                if (checkbox.isChecked) {
                    ContentSyncManager.setUseOfflineAssetsOnly(this, true)
                    ContentSyncManager.setDontShowSyncDialog(this, true)
                    Log.d(TAG, "User opted to use offline assets only")
                }
                
                hideSyncCheckSnackbar()
                btnSyncContent.isEnabled = true
                btnSyncContent.text = getString(R.string.sync_content_button_label)
                Log.d(TAG, "Sync check cancelled by user")
                dialog.dismiss()
            }
            .setCancelable(true)
            .setOnCancelListener {
                // If user dismisses dialog, continue the sync check
            }
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel sync check if activity is destroyed
        syncCheckJob?.cancel()
        syncCheckJob = null
        hideSyncCheckSnackbar()
        cancelSyncDialog?.dismiss()
        cancelSyncDialog = null
    }


    override fun onResume() {
        super.onResume()
        // Refresh settings when returning to the activity
        loadSettings()
        loadCameraSettings()
        refreshAdminAccess()
    }

}
