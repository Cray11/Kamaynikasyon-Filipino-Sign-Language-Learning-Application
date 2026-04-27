package com.example.kamaynikasyon.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.example.kamaynikasyon.R

/**
 * Centralized error handling utility for consistent error display across the app.
 * Provides user-friendly error messages and supports both Toast and Snackbar.
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * Applies styling to the snackbar (border and elevation).
     */
    private fun styleSnackbar(snackbar: Snackbar, context: Context) {
        val snackbarView = snackbar.view
        snackbarView.setBackgroundResource(R.drawable.bg_snackbar)
        val elevationDp = 2f
        val elevationPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            elevationDp,
            context.resources.displayMetrics
        )
        ViewCompat.setElevation(snackbarView, elevationPx)
    }

    /**
     * Shows an error message using Snackbar (preferred for better UX).
     * 
     * @param view The view to anchor the Snackbar to
     * @param messageResId String resource ID for the error message
     * @param actionResId Optional action button text resource ID
     * @param actionListener Optional action button click listener
     * @param duration Snackbar duration (default: LENGTH_LONG for errors)
     */
    fun showError(
        view: View,
        messageResId: Int,
        actionResId: Int? = null,
        actionListener: View.OnClickListener? = null,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        try {
            val message = view.context.getString(messageResId)
            val snackbar = Snackbar.make(view, message, duration)
            
            actionResId?.let { resId ->
                actionListener?.let { listener ->
                    snackbar.setAction(view.context.getString(resId), listener)
                }
            }
            
            styleSnackbar(snackbar, view.context)
            snackbar.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing Snackbar", e)
            // Log to Crashlytics
            logErrorToCrashlytics(e, "Error showing Snackbar")
            // Fallback to Toast if Snackbar fails
            view.context.showToast(view.context.getString(messageResId))
        }
    }

    /**
     * Shows an error message using Snackbar with a string message.
     */
    fun showError(
        view: View,
        message: String,
        actionText: String? = null,
        actionListener: View.OnClickListener? = null,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        try {
            val snackbar = Snackbar.make(view, message, duration)
            
            actionText?.let { text ->
                actionListener?.let { listener ->
                    snackbar.setAction(text, listener)
                }
            }
            
            styleSnackbar(snackbar, view.context)
            snackbar.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing Snackbar", e)
            // Log to Crashlytics
            logErrorToCrashlytics(e, "Error showing Snackbar")
            view.context.showToast(message)
        }
    }

    /**
     * Shows a network error with retry option.
     */
    fun showNetworkError(
        view: View,
        retryAction: View.OnClickListener? = null
    ) {
        val isOffline = !isOnline(view.context)
        val messageResId = if (isOffline) {
            R.string.error_offline
        } else {
            R.string.error_network
        }
        
        showError(
            view = view,
            messageResId = messageResId,
            actionResId = if (retryAction != null) R.string.action_retry else null,
            actionListener = retryAction,
            duration = Snackbar.LENGTH_INDEFINITE
        )
    }

    /**
     * Shows a generic error with optional retry.
     */
    fun showGenericError(
        view: View,
        retryAction: View.OnClickListener? = null
    ) {
        showError(
            view = view,
            messageResId = R.string.error_generic,
            actionResId = if (retryAction != null) R.string.action_try_again else null,
            actionListener = retryAction
        )
    }

    /**
     * Shows a loading error with retry option.
     */
    fun showLoadingError(
        view: View,
        retryAction: View.OnClickListener? = null
    ) {
        showError(
            view = view,
            messageResId = R.string.error_loading_failed,
            actionResId = if (retryAction != null) R.string.action_retry else null,
            actionListener = retryAction
        )
    }

    /**
     * Shows a saving error.
     */
    fun showSavingError(view: View) {
        showError(
            view = view,
            messageResId = R.string.error_saving_failed
        )
    }

    /**
     * Checks if device is online.
     */
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Gets a user-friendly error message from an exception.
     */
    fun getErrorMessage(exception: Throwable, context: Context): String {
        return when (exception) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException -> {
                context.getString(R.string.error_network)
            }
            is java.io.IOException -> {
                if (!isOnline(context)) {
                    context.getString(R.string.error_offline)
                } else {
                    context.getString(R.string.error_loading_failed)
                }
            }
            is OutOfMemoryError -> {
                context.getString(R.string.error_loading_media)
            }
            else -> {
                context.getString(R.string.error_generic)
            }
        }
    }

    /**
     * Logs an error to Crashlytics with additional context.
     */
    fun logErrorToCrashlytics(exception: Throwable, message: String? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (message != null) {
                crashlytics.log(message)
            }
            crashlytics.recordException(exception)
        } catch (e: Exception) {
            // If Crashlytics fails, just log to Android Log
            Log.e(TAG, "Failed to log to Crashlytics", e)
        }
    }

    /**
     * Logs a non-fatal error to Crashlytics.
     */
    fun logError(message: String, exception: Throwable? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log(message)
            exception?.let {
                crashlytics.recordException(it)
            } ?: run {
                // Record as a non-fatal exception
                crashlytics.recordException(Exception(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log to Crashlytics", e)
        }
    }
}

/**
 * Extension functions for easier error handling in Activities and Fragments.
 */

/**
 * Shows an error Snackbar anchored to the activity's root view.
 */
fun android.app.Activity.showError(
    messageResId: Int,
    actionResId: Int? = null,
    actionListener: View.OnClickListener? = null
) {
    val rootView = findViewById<View>(android.R.id.content) ?: window.decorView.rootView
    ErrorHandler.showError(rootView, messageResId, actionResId, actionListener)
}

/**
 * Shows an error Snackbar anchored to the fragment's view.
 */
fun Fragment.showError(
    messageResId: Int,
    actionResId: Int? = null,
    actionListener: View.OnClickListener? = null
) {
    view?.let {
        ErrorHandler.showError(it, messageResId, actionResId, actionListener)
    } ?: run {
        // Fallback to Toast if view is null
        requireContext().showToast(requireContext().getString(messageResId))
    }
}

/**
 * Shows a network error with retry option.
 */
fun View.showNetworkError(retryAction: View.OnClickListener? = null) {
    ErrorHandler.showNetworkError(this, retryAction)
}

/**
 * Shows a network error with retry option (Activity extension).
 */
fun android.app.Activity.showNetworkError(retryAction: View.OnClickListener? = null) {
    val rootView = findViewById<View>(android.R.id.content) ?: window.decorView.rootView
    ErrorHandler.showNetworkError(rootView, retryAction)
}

/**
 * Shows a network error with retry option (Fragment extension).
 */
fun Fragment.showNetworkError(retryAction: View.OnClickListener? = null) {
    view?.let {
        ErrorHandler.showNetworkError(it, retryAction)
    }
}

/**
 * Checks if device is online (Context extension).
 */
fun Context.isOnline(): Boolean = ErrorHandler.isOnline(this)

