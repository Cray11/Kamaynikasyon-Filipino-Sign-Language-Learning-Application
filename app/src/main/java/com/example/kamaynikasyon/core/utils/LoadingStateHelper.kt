package com.example.kamaynikasyon.core.utils

import android.view.View
import android.widget.ProgressBar
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Utility for managing loading states consistently across the app.
 * Provides helper functions to show/hide loading indicators with animations.
 */
object LoadingStateHelper {
    
    /**
     * Show loading indicator and hide content views with animations.
     * 
     * @param loadingIndicator The loading indicator view (ProgressBar or CircularProgressIndicator)
     * @param contentViews Views to hide while loading
     * @param animate Whether to animate the loading indicator (default: true)
     */
    fun showLoading(
        loadingIndicator: View,
        vararg contentViews: View,
        animate: Boolean = true
    ) {
        loadingIndicator.visibility = View.VISIBLE
        if (animate) {
            AnimationHelper.fadeIn(loadingIndicator, 200)
            // Add pulse animation for visual feedback
            AnimationHelper.startPulseAnimation(loadingIndicator)
        }
        contentViews.forEach { 
            AnimationHelper.fadeOut(it, 150)
        }
    }
    
    /**
     * Hide loading indicator and show content views with animations.
     * 
     * @param loadingIndicator The loading indicator view
     * @param contentViews Views to show after loading
     * @param animate Whether to animate the transition (default: true)
     */
    fun hideLoading(
        loadingIndicator: View,
        vararg contentViews: View,
        animate: Boolean = true
    ) {
        if (animate) {
            AnimationHelper.stopAnimation(loadingIndicator)
            AnimationHelper.fadeOut(loadingIndicator, 150) {
                loadingIndicator.visibility = View.GONE
            }
            contentViews.forEach { 
                AnimationHelper.fadeIn(it, 300)
            }
        } else {
            AnimationHelper.stopAnimation(loadingIndicator)
            loadingIndicator.visibility = View.GONE
            contentViews.forEach { it.visibility = View.VISIBLE }
        }
    }
    
    /**
     * Show loading state with error handling.
     * Shows loading indicator, hides content, and hides error views.
     * 
     * @param loadingIndicator The loading indicator
     * @param contentViews Views to hide
     * @param errorViews Error views to hide
     */
    fun showLoadingWithErrorHandling(
        loadingIndicator: View,
        contentViews: List<View>,
        errorViews: List<View> = emptyList()
    ) {
        loadingIndicator.visibility = View.VISIBLE
        contentViews.forEach { it.visibility = View.GONE }
        errorViews.forEach { it.visibility = View.GONE }
    }
    
    /**
     * Show content after successful load.
     * Hides loading indicator and error views, shows content.
     * 
     * @param loadingIndicator The loading indicator
     * @param contentViews Views to show
     * @param errorViews Error views to hide
     */
    fun showContent(
        loadingIndicator: View,
        contentViews: List<View>,
        errorViews: List<View> = emptyList()
    ) {
        loadingIndicator.visibility = View.GONE
        errorViews.forEach { it.visibility = View.GONE }
        contentViews.forEach { it.visibility = View.VISIBLE }
    }
    
    /**
     * Show error state.
     * Hides loading indicator and content, shows error views.
     * 
     * @param loadingIndicator The loading indicator
     * @param contentViews Content views to hide
     * @param errorViews Error views to show
     */
    fun showError(
        loadingIndicator: View,
        contentViews: List<View>,
        errorViews: List<View>
    ) {
        loadingIndicator.visibility = View.GONE
        contentViews.forEach { it.visibility = View.GONE }
        errorViews.forEach { it.visibility = View.VISIBLE }
    }
    
    /**
     * Enable or disable views during loading to prevent double-taps.
     * 
     * @param isLoading Whether loading is in progress
     * @param views Views to enable/disable
     */
    fun setViewsEnabled(isLoading: Boolean, vararg views: View) {
        views.forEach { it.isEnabled = !isLoading }
    }
}

