package com.example.kamaynikasyon.core.utils

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.kamaynikasyon.R
import com.google.android.material.button.MaterialButton

/**
 * Utility class for managing empty state views.
 * Provides easy way to show/hide empty states with icons, messages, and actions.
 */
object EmptyStateView {
    
    /**
     * Show an empty state view with custom content.
     * 
     * @param container The empty state container view (from view_empty_state.xml)
     * @param icon Emoji or icon text to display
     * @param title Title text
     * @param message Message text
     * @param actionText Optional action button text
     * @param actionListener Optional action button click listener
     */
    fun show(
        container: View,
        icon: String,
        title: String,
        message: String,
        actionText: String? = null,
        actionListener: View.OnClickListener? = null
    ) {
        container.findViewById<TextView>(R.id.empty_state_icon)?.text = icon
        container.findViewById<TextView>(R.id.empty_state_title)?.text = title
        container.findViewById<TextView>(R.id.empty_state_message)?.text = message
        
        val actionButton = container.findViewById<MaterialButton>(R.id.empty_state_action)
        if (actionText != null && actionListener != null) {
            actionButton?.text = actionText
            actionButton?.setOnClickListener(actionListener)
            actionButton?.visibility = View.VISIBLE
        } else {
            actionButton?.visibility = View.GONE
        }
        
        container.visibility = View.VISIBLE
        // Add admin-style animation (fadeIn + slideInUp)
        com.example.kamaynikasyon.core.utils.AnimationHelper.showEmptyState(container)
    }
    
    /**
     * Show an empty state using string resources.
     */
    fun show(
        container: View,
        @StringRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        @StringRes actionTextRes: Int? = null,
        actionListener: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = context.getString(iconRes),
            title = context.getString(titleRes),
            message = context.getString(messageRes),
            actionText = actionTextRes?.let { context.getString(it) },
            actionListener = actionListener
        )
    }
    
    /**
     * Hide the empty state view.
     */
    fun hide(container: View) {
        container.visibility = View.GONE
    }
    
    /**
     * Show error state.
     */
    fun showError(
        container: View,
        @StringRes messageRes: Int,
        retryAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "⚠️",
            title = context.getString(R.string.error_generic),
            message = context.getString(messageRes),
            actionText = if (retryAction != null) context.getString(R.string.action_retry) else null,
            actionListener = retryAction
        )
    }
    
    /**
     * Show network error state.
     */
    fun showNetworkError(
        container: View,
        retryAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "📡",
            title = context.getString(R.string.error_network),
            message = context.getString(R.string.error_offline),
            actionText = if (retryAction != null) context.getString(R.string.action_retry) else null,
            actionListener = retryAction
        )
    }
    
    /**
     * Show empty lessons state.
     */
    fun showEmptyLessons(
        container: View,
        refreshAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "📚",
            title = context.getString(R.string.empty_lessons_title),
            message = context.getString(R.string.empty_lessons_message),
            actionText = if (refreshAction != null) context.getString(R.string.action_refresh) else null,
            actionListener = refreshAction
        )
    }
    
    /**
     * Show empty quizzes state.
     */
    fun showEmptyQuizzes(
        container: View,
        refreshAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "📝",
            title = context.getString(R.string.empty_quizzes_title),
            message = context.getString(R.string.empty_quizzes_message),
            actionText = if (refreshAction != null) context.getString(R.string.action_refresh) else null,
            actionListener = refreshAction
        )
    }
    
    /**
     * Show empty dictionary state.
     */
    fun showEmptyDictionary(
        container: View,
        searchAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "📖",
            title = context.getString(R.string.empty_dictionary_title),
            message = context.getString(R.string.empty_dictionary_message),
            actionText = if (searchAction != null) context.getString(R.string.action_search) else null,
            actionListener = searchAction
        )
    }
    
    /**
     * Show empty words state.
     */
    fun showEmptyWords(
        container: View,
        refreshAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "📝",
            title = context.getString(R.string.empty_words_title),
            message = context.getString(R.string.empty_words_message),
            actionText = if (refreshAction != null) context.getString(R.string.action_refresh) else null,
            actionListener = refreshAction
        )
    }
    
    /**
     * Show empty search results state.
     */
    fun showEmptySearchResults(
        container: View,
        searchQuery: String? = null
    ) {
        val context = container.context
        val message = if (searchQuery != null) {
            context.getString(R.string.empty_search_results_message_with_query, searchQuery)
        } else {
            context.getString(R.string.empty_search_results_message)
        }
        
        show(
            container = container,
            icon = "🔍",
            title = context.getString(R.string.empty_search_results_title),
            message = message
        )
    }
    
    /**
     * Show empty minigames state.
     */
    fun showEmptyMinigames(
        container: View,
        refreshAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "🎮",
            title = context.getString(R.string.empty_minigames_title),
            message = context.getString(R.string.empty_minigames_message),
            actionText = if (refreshAction != null) context.getString(R.string.action_refresh) else null,
            actionListener = refreshAction
        )
    }
    
    /**
     * Show empty progress state.
     */
    fun showEmptyProgress(
        container: View,
        startAction: View.OnClickListener? = null
    ) {
        val context = container.context
        show(
            container = container,
            icon = "📊",
            title = context.getString(R.string.empty_progress_title),
            message = context.getString(R.string.empty_progress_message),
            actionText = if (startAction != null) context.getString(R.string.action_start_learning) else null,
            actionListener = startAction
        )
    }
}

/**
 * Extension functions for easier empty state management.
 */

/**
 * Show empty state in a ViewGroup by inflating the empty state layout.
 */
fun View.showEmptyState(
    icon: String,
    title: String,
    message: String,
    actionText: String? = null,
    actionListener: View.OnClickListener? = null
) {
    val emptyStateView = if (this.findViewById<View>(R.id.empty_state_container) != null) {
        findViewById<View>(R.id.empty_state_container)
    } else {
        // Inflate if not exists
        android.view.LayoutInflater.from(context)
            .inflate(R.layout.view_empty_state, this as? android.view.ViewGroup, false)
            .also { (this as? android.view.ViewGroup)?.addView(it) }
    }
    
    emptyStateView?.let {
        EmptyStateView.show(it, icon, title, message, actionText, actionListener)
    }
}

/**
 * Hide empty state.
 */
fun View.hideEmptyState() {
    findViewById<View>(R.id.empty_state_container)?.let {
        EmptyStateView.hide(it)
    }
}

