package com.example.kamaynikasyon

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import android.util.Log
import com.example.kamaynikasyon.core.base.BaseActivity
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.supabase.SyncDialog
import com.example.kamaynikasyon.core.utils.ErrorHandler
import com.example.kamaynikasyon.databinding.ActivityMainBinding
import com.example.kamaynikasyon.features.auth.AuthViewModel
import com.example.kamaynikasyon.features.auth.AuthViewModelFactory
import com.example.kamaynikasyon.features.auth.data.AuthRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import android.widget.ImageButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.kamaynikasyon.features.settings.SettingsActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.kamaynikasyon.core.ui.StrokeTextView

class MainActivity : BaseActivity<ActivityMainBinding>() {
    
    private val authRepository = AuthRepository()
    private val authViewModel: AuthViewModel by viewModels { 
        AuthViewModelFactory(authRepository) 
    }
    
    private var syncCheckJob: Job? = null
    private var syncCheckSnackbar: Snackbar? = null
    private var syncCheckView: View? = null
    private var cancelSyncDialog: AlertDialog? = null
    
    private val progressResetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == SettingsActivity.ACTION_PROGRESS_RESET) {
                // Refresh the currently visible fragment
                refreshCurrentFragment()
            }
        }
    }
    
    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
    
    override fun setupUI() {
        // Setup ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide the default title
        
        // Set username greeting
        setupUsernameGreeting()
        
        // Add settings button click
        binding.toolbar.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            val intent = Intent(this, com.example.kamaynikasyon.features.settings.SettingsActivity::class.java)
            startActivity(intent)
        }
        
        setupNavigation()
        
        // Check if we should skip sync check (e.g., after successful sync and reload)
        val shouldSkipSyncCheck = ContentSyncManager.shouldSkipSyncCheckAfterReload(this)
        if (shouldSkipSyncCheck) {
            // Clear the flag since we've checked it
            ContentSyncManager.clearSkipSyncCheckAfterReload(this)
            Log.d("MainActivity", "Skipping sync check after successful sync reload")
        } else {
            // Check for content sync on app start
            checkForContentSync()
        }
        
        // Register broadcast receiver for progress reset
        LocalBroadcastManager.getInstance(this).registerReceiver(
            progressResetReceiver,
            IntentFilter(SettingsActivity.ACTION_PROGRESS_RESET)
        )
    }
    
    private fun refreshCurrentFragment() {
        // Get the current fragment and refresh it if it's ProfileFragment
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
            ?.childFragmentManager?.fragments?.firstOrNull()
        
        // If ProfileFragment is visible, it will handle its own refresh via broadcast
        // Other fragments that need refreshing should also listen to the broadcast
        // For now, we'll just ensure the navigation state is updated
        Log.d("MainActivity", "Progress reset detected, current fragment will refresh if listening")
    }
    
    private fun checkForContentSync() {
        // Only check if Supabase is initialized
        if (!com.example.kamaynikasyon.core.supabase.SupabaseConfig.isInitialized()) {
            Log.d("MainActivity", "Supabase not initialized, skipping sync check")
            return
        }
        
        // Check if user opted to use offline assets only - skip sync check
        if (ContentSyncManager.isUseOfflineAssetsOnly(this)) {
            Log.d("MainActivity", "User opted to use offline assets only, skipping sync check")
            return
        }
        
        // Check internet connectivity - only proceed if online
        if (!ErrorHandler.isOnline(this)) {
            Log.d("MainActivity", "No internet connection, skipping sync check")
            // Offline: app will use offline assets (already loaded by repositories)
            return
        }
        
        Log.d("MainActivity", "Starting sync check...")
        
        // Show loading Snackbar
        showSyncCheckSnackbar()
        
        // App starts with offline assets (repositories load from assets first)
        // Now check for sync in background after a short delay to ensure UI is ready
        syncCheckJob = lifecycleScope.launch {
            try {
                // Small delay to ensure app UI is loaded with offline assets first
                kotlinx.coroutines.delay(500)
                
            val syncResult = ContentSyncManager.checkForDifferences(this@MainActivity)
                Log.d("MainActivity", "Sync check result: hasDifferences=${syncResult.hasDifferences}, count=${syncResult.differences.size}")
            
            // Only proceed if activity is still in a valid state
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (syncResult.hasDifferences) {
                    Log.d("MainActivity", "Differences found, checking auto-sync and dialog conditions...")
                    // Check if auto sync is enabled (user checked "don't show again" and synced before)
                    val autoSyncEnabled = ContentSyncManager.isAutoSyncEnabled(this@MainActivity)
                    val dontShowDialog = ContentSyncManager.isDontShowSyncDialogEnabled(this@MainActivity)
                    Log.d("MainActivity", "Auto sync enabled: $autoSyncEnabled, Don't show dialog: $dontShowDialog")
                    
                    if (autoSyncEnabled && dontShowDialog) {
                        // Auto sync without showing dialog
                        Log.d("MainActivity", "Auto-syncing in background...")
                        lifecycleScope.launch {
                            val success = ContentSyncManager.syncFromSupabase(
                                this@MainActivity,
                                syncResult.differences,
                                downloadMedia = false, // Auto-sync doesn't download media by default
                                progressCallback = null // No progress callback for background sync
                            )
                            // If auto sync was successful, clear "use offline assets only" preference
                            if (success) {
                                ContentSyncManager.setUseOfflineAssetsOnly(this@MainActivity, false)
                            }
                            ContentSyncManager.recordSyncCheck(
                                this@MainActivity, 
                                if (success) "auto_synced" else "auto_sync_failed"
                            )
                            Log.d("MainActivity", "Auto-sync completed: success=$success")
                        }
                    } else {
                        // Show sync dialog when differences are found
                        // Don't check shouldShowSyncDialog() here - if there are differences, always show
                        Log.d("MainActivity", "Showing sync dialog...")
                        val dialog = SyncDialog.newInstance(syncResult)
                        dialog.show(supportFragmentManager, "SyncDialog")
                    }
                } else {
                    // No differences, record that we checked
                    Log.d("MainActivity", "No differences found")
                    ContentSyncManager.recordSyncCheck(this@MainActivity, "no_differences")
                    
                    // Show toast only if checkbox is not checked (user hasn't opted for offline assets only)
                    if (!ContentSyncManager.isUseOfflineAssetsOnly(this@MainActivity)) {
                        Toast.makeText(
                            this@MainActivity,
                            "Content up to date",
                            Toast.LENGTH_SHORT
                        ).show()
                }
            }
            } else {
                Log.d("MainActivity", "Activity not in valid state, skipping dialog")
            }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during sync check", e)
            } finally {
                // Always hide loading Snackbar when done
                hideSyncCheckSnackbar()
            }
        }
    }
    
    private fun showSyncCheckSnackbar() {
        // Hide any existing view
        hideSyncCheckSnackbar()
        
        // Get the root content view (FrameLayout) to overlay the indicator
        val rootView = findViewById<ViewGroup>(android.R.id.content) ?: return
        
        // Inflate the layout
        val syncView = LayoutInflater.from(this).inflate(com.example.kamaynikasyon.R.layout.view_sync_check_indicator, null)
        
        // Setup click listeners
        syncView.findViewById<TextView>(com.example.kamaynikasyon.R.id.btn_cancel).setOnClickListener {
            showCancelSyncDialog()
        }
        
        syncView.findViewById<TextView>(com.example.kamaynikasyon.R.id.btn_close).setOnClickListener {
            hideSyncCheckSnackbar()
            Toast.makeText(this, "Content checking will continue in the background", Toast.LENGTH_SHORT).show()
        }
        
        // Get navbar height to position above it
        val navView = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
        val marginBottom = resources.getDimensionPixelSize(R.dimen.sync_snackbar_margin_bottom)
        val marginHorizontal = resources.getDimensionPixelSize(R.dimen.sync_snackbar_margin_horizontal)
        
        // Use post to ensure navbar is measured
        navView?.post {
            val navHeight = navView.height
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = navHeight + marginBottom
                leftMargin = marginHorizontal
                rightMargin = marginHorizontal
                gravity = android.view.Gravity.BOTTOM
            }
                syncView.layoutParams = layoutParams
        }
        
        // Set initial position (will be updated if navbar is measured)
        val initialNavHeight = navView?.height ?: 0
        val layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = initialNavHeight + marginBottom
            leftMargin = marginHorizontal
            rightMargin = marginHorizontal
            gravity = android.view.Gravity.BOTTOM
        }
        syncView.layoutParams = layoutParams
        
        // Add elevation (2dp)
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
        syncCheckSnackbar?.dismiss()
        syncCheckSnackbar = null
        syncCheckView = null
    }
    
    private fun showCancelSyncDialog() {
        val dialogView = LayoutInflater.from(this).inflate(com.example.kamaynikasyon.R.layout.dialog_sync_cancel, null)
        val checkbox = dialogView.findViewById<CheckBox>(com.example.kamaynikasyon.R.id.checkbox_offline_assets)
        
        cancelSyncDialog?.dismiss()
        cancelSyncDialog = AlertDialog.Builder(this)
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
                    Log.d("MainActivity", "User opted to use offline assets only")
                }
                
                hideSyncCheckSnackbar()
                Log.d("MainActivity", "Sync check cancelled by user")
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
        
        // Unregister broadcast receiver
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(progressResetReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered, ignore
        }
    }
    
    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        
        navView.setupWithNavController(navController)
        
        // Show/hide action bar based on destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Show action bar for main navigation items, hide for other fragments
            when (destination.id) {
                R.id.navigation_home,
                R.id.navigation_dictionary,
                R.id.navigation_minigames,
                R.id.navigation_profile -> {
                    supportActionBar?.show()
                }
                else -> {
                    supportActionBar?.hide()
                }
            }
            
            // Set the dictionary nav item as selected for dictionary-related fragments
            when (destination.id) {
                R.id.wordsFragment,
                R.id.searchResultsFragment,
                R.id.wordDetailFragment,
                R.id.navigation_dictionary -> {
                    navView.menu.findItem(R.id.navigation_dictionary)?.isChecked = true
                }
            }
        }
    }
    
    private fun setupUsernameGreeting() {
        val usernameTextView = binding.toolbar.findViewById<StrokeTextView>(R.id.tv_username)
        val currentUser = FirebaseAuth.getInstance().currentUser
        val displayName = currentUser?.displayName
        val email = currentUser?.email
        
        val username = when {
            !displayName.isNullOrBlank() -> displayName
            !email.isNullOrBlank() -> email.substringBefore("@")
            else -> "User"
        }
        
        usernameTextView?.text = "Hi, $username"
        
        // Apply professional text stroke/border styling
        usernameTextView?.let { textView ->
            // Convert stroke width from dp to pixels (2dp stroke width)
            val strokeWidthDp = 2f
            val strokeWidthPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                strokeWidthDp,
                resources.displayMetrics
            )
            textView.setStrokeWidth(strokeWidthPx)
            // Set stroke color to white for contrast against the primary color background
            textView.setStrokeColor(android.graphics.Color.WHITE)
        }
    }
    
    override fun setupObservers() {
        // Authentication state is now handled in WelcomeActivity
        // MainActivity assumes user is already authenticated when reached
    }
}
