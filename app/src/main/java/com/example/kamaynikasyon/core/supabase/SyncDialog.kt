package com.example.kamaynikasyon.core.supabase

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import com.example.kamaynikasyon.MainActivity
import com.google.android.material.button.MaterialButton
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SyncDialog : DialogFragment() {
    
    private var syncResult: SyncResult? = null
    private var syncJob: Job? = null
    private var isSyncing = false
    
    companion object {
        private const val ARG_SYNC_RESULT = "sync_result"
        
        fun newInstance(syncResult: SyncResult): SyncDialog {
            return SyncDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_SYNC_RESULT, syncResult)
                }
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        syncResult = arguments?.getParcelable(ARG_SYNC_RESULT)
        
        val context = context ?: activity ?: throw IllegalStateException("Context is not available")
        val builder = AlertDialog.Builder(context)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(com.example.kamaynikasyon.R.layout.dialog_sync, null)
        
        val titleText = view.findViewById<TextView>(com.example.kamaynikasyon.R.id.sync_title)
        val messageText = view.findViewById<TextView>(com.example.kamaynikasyon.R.id.sync_message)
        val dontShowAgainCheckbox = view.findViewById<CheckBox>(com.example.kamaynikasyon.R.id.checkbox_dont_show_again)
        val downloadMediaCheckbox = view.findViewById<CheckBox>(com.example.kamaynikasyon.R.id.checkbox_download_media)
        val progressContainer = view.findViewById<View>(com.example.kamaynikasyon.R.id.sync_progress_container)
        val progressBar = view.findViewById<ProgressBar>(com.example.kamaynikasyon.R.id.sync_progress)
        val progressText = view.findViewById<TextView>(com.example.kamaynikasyon.R.id.sync_progress_text)
        val fileNameText = view.findViewById<TextView>(com.example.kamaynikasyon.R.id.sync_file_name)
        val syncButton = view.findViewById<MaterialButton>(com.example.kamaynikasyon.R.id.btn_sync)
        val skipButton = view.findViewById<MaterialButton>(com.example.kamaynikasyon.R.id.btn_skip)
        
        // Initially hide progress container
        progressContainer.visibility = View.GONE
        
        val differences = syncResult?.differences ?: emptyList()
        val differenceCount = differences.size
        
        titleText.text = "Content Update Available"
        messageText.text = if (differenceCount > 0) {
            "New content is available on the server ($differenceCount file${if (differenceCount > 1) "s" else ""} updated).\n\n" +
            "Would you like to sync the latest content? This will update your app with the latest lessons, quizzes, and minigames."
        } else {
            "Content sync is available. Would you like to sync?"
        }
        
        val dialog = builder.setView(view)
            .setCancelable(true)
            .setOnCancelListener {
                // Record that user dismissed the dialog
                context?.let { ctx ->
                    if (dontShowAgainCheckbox.isChecked) {
                        ContentSyncManager.setDontShowSyncDialog(ctx, true)
                        ContentSyncManager.setUseOfflineAssetsOnly(ctx, true)
                        ContentSyncManager.recordSyncCheck(ctx, "dismissed_with_offline_assets")
                    } else {
                        ContentSyncManager.recordSyncCheck(ctx, "dismissed")
                    }
                }
            }
            .create()
        
        // Handle skip button click
        skipButton.setOnClickListener {
            // If checkbox is checked, skip immediately, don't show again, and use offline assets only
            if (dontShowAgainCheckbox.isChecked) {
                context?.let { ctx ->
                    ContentSyncManager.setDontShowSyncDialog(ctx, true)
                    ContentSyncManager.setUseOfflineAssetsOnly(ctx, true)
                    ContentSyncManager.recordSyncCheck(ctx, "skipped_with_offline_assets")
                }
                dialog.dismiss()
                return@setOnClickListener
            }
            
            // Otherwise, show skip message
            context?.let { ctx ->
                ContentSyncManager.recordSyncCheck(ctx, "skipped")
            }
            
            // Update message to inform user about settings
            messageText.text = context.getString(com.example.kamaynikasyon.R.string.sync_skip_message)
            syncButton.visibility = View.GONE
            dontShowAgainCheckbox.visibility = View.GONE
            downloadMediaCheckbox.visibility = View.GONE
            skipButton.text = context.getString(com.example.kamaynikasyon.R.string.action_ok)
            skipButton.setOnClickListener {
                dialog.dismiss()
            }
        }
        
        syncButton.setOnClickListener {
            if (isSyncing) {
                // Cancel sync
                syncJob?.cancel()
                isSyncing = false
                // Mark sync as not in progress
                context?.let { ContentSyncManager.setSyncInProgress(it, false) }
                syncButton.text = "Sync"
                syncButton.isEnabled = true
                skipButton.visibility = View.VISIBLE
                dontShowAgainCheckbox.visibility = View.VISIBLE
                downloadMediaCheckbox.visibility = View.VISIBLE
                progressContainer.visibility = View.GONE
                messageText.text = "Sync cancelled."
                return@setOnClickListener
            }
            
            syncButton.isEnabled = false
            syncButton.text = "Cancel"
            skipButton.visibility = View.GONE
            dontShowAgainCheckbox.visibility = View.GONE
            downloadMediaCheckbox.visibility = View.GONE
            progressContainer.visibility = View.VISIBLE
            progressBar.progress = 0
            progressText.text = "0%"
            fileNameText.text = ""
            messageText.text = "Syncing content from server..."
            isSyncing = true
            // Mark sync as in progress in persistent storage
            context?.let { ContentSyncManager.setSyncInProgress(it, true) }
            
            syncJob = lifecycleScope.launch {
                val context = this@SyncDialog.context ?: return@launch
                val downloadMedia = downloadMediaCheckbox.isChecked
                val success = syncResult?.let { result ->
                    ContentSyncManager.syncFromSupabase(
                        context, 
                        result.differences,
                        downloadMedia,
                        object : ContentSyncManager.SyncProgressCallback {
                            override fun onProgress(current: Int, total: Int, fileName: String) {
                                // Update UI on main thread
                                view.post {
                                    if (isSyncing) {
                                        val percentage = if (total > 0) {
                                            (current * 100) / total
                                        } else 0
                                        // Cap percentage at 100%
                                        val cappedPercentage = percentage.coerceAtMost(100)
                                        progressBar.progress = cappedPercentage
                                        progressText.text = "$cappedPercentage% ($current/$total)"
                                    fileNameText.text = fileName
                                    }
                                }
                            }
                        }
                    )
                } ?: false
                
                // Save preferences if checkbox is checked
                if (dontShowAgainCheckbox.isChecked) {
                    ContentSyncManager.setDontShowSyncDialog(context, true)
                    // Enable auto sync so it syncs automatically in the future
                    ContentSyncManager.setAutoSyncEnabled(context, true)
                }
                
                // If sync was successful, clear "use offline assets only" preference
                // so that future syncs can work properly
                if (success) {
                    ContentSyncManager.setUseOfflineAssetsOnly(context, false)
                }
                
                isSyncing = false
                // Mark sync as not in progress (ContentSyncManager.syncFromSupabase already does this, but ensure it's cleared)
                ContentSyncManager.setSyncInProgress(context, false)
                ContentSyncManager.recordSyncCheck(context, if (success) "synced" else "sync_failed")
                
                view.post {
                progressContainer.visibility = View.GONE
                if (success) {
                    messageText.text = "Content synced successfully! The app will reload to use the latest content."
                    syncButton.text = "Done"
                    syncButton.isEnabled = true
                    syncButton.setOnClickListener {
                        dialog.dismiss()
                        restartApp(context)
                    }
                } else {
                    messageText.text = "Sync failed. Please check your internet connection and try again."
                    syncButton.text = "Retry"
                    syncButton.isEnabled = true
                    syncButton.setOnClickListener {
                        syncButton.isEnabled = false
                            syncButton.text = "Cancel"
                        skipButton.visibility = View.GONE
                        progressContainer.visibility = View.VISIBLE
                        progressBar.progress = 0
                        progressText.text = "0%"
                        fileNameText.text = ""
                        messageText.text = "Syncing content from server..."
                            isSyncing = true
                            // Mark sync as in progress
                            context?.let { ContentSyncManager.setSyncInProgress(it, true) }
                        
                            syncJob = lifecycleScope.launch {
                            val context = this@SyncDialog.context ?: return@launch
                            val downloadMedia = downloadMediaCheckbox.isChecked
                            val retrySuccess = syncResult?.let { result ->
                                ContentSyncManager.syncFromSupabase(
                                    context, 
                                    result.differences,
                                    downloadMedia,
                                    object : ContentSyncManager.SyncProgressCallback {
                                        override fun onProgress(current: Int, total: Int, fileName: String) {
                                            // Update UI on main thread
                                            view.post {
                                                    if (isSyncing) {
                                                        val percentage = if (total > 0) {
                                                            (current * 100) / total
                                                        } else 0
                                                        val cappedPercentage = percentage.coerceAtMost(100)
                                                        progressBar.progress = cappedPercentage
                                                        progressText.text = "$cappedPercentage% ($current/$total)"
                                                fileNameText.text = fileName
                                            }
                                                }
                                        }
                                    }
                                )
                            } ?: false
                            
                            // If retry sync was successful, clear "use offline assets only" preference
                            if (retrySuccess) {
                                ContentSyncManager.setUseOfflineAssetsOnly(context, false)
                            }
                            
                                isSyncing = false
                                // Mark sync as not in progress
                                ContentSyncManager.setSyncInProgress(context, false)
                                view.post {
                            progressContainer.visibility = View.GONE
                            if (retrySuccess) {
                                messageText.text = "Content synced successfully! The app will reload to use the latest content."
                                syncButton.text = "Done"
                                syncButton.isEnabled = true
                                syncButton.setOnClickListener {
                                    dialog.dismiss()
                                    restartApp(context)
                                }
                            } else {
                                messageText.text = "Sync failed. Please try again later."
                                syncButton.text = "Retry"
                                syncButton.isEnabled = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return dialog
    }
    
    /**
     * Restarts the app by navigating to MainActivity and clearing the task stack
     * Sets a flag to skip sync check on reload since we just synced successfully
     */
    private fun restartApp(context: Context) {
        // Set flag to skip sync check after reload since we just completed a successful sync
        ContentSyncManager.setSkipSyncCheckAfterReload(context, true)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        
        // Finish the current activity if it's not MainActivity
        activity?.let {
            if (it !is MainActivity) {
                it.finishAffinity()
            }
        }
    }
}



