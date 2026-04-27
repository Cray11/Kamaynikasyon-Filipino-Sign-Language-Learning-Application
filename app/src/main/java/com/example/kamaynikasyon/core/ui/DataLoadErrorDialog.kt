package com.example.kamaynikasyon.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.example.kamaynikasyon.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DataLoadErrorDialog {

    fun create(
        context: Context,
        @StringRes messageRes: Int,
        onRetry: () -> Unit,
        onGoHome: () -> Unit
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.error_generic)
            .setMessage(messageRes)
            .setCancelable(false)
            .setPositiveButton(R.string.action_retry) { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton(R.string.action_go_home) { dialog, _ ->
                dialog.dismiss()
                onGoHome()
            }
            .create()
    }
}


