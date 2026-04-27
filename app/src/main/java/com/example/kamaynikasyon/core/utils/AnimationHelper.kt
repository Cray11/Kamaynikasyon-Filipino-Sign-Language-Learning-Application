package com.example.kamaynikasyon.core.utils

import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import com.example.kamaynikasyon.R

/**
 * Utility class for consistent animations across the app.
 * Provides helper functions for common animation patterns.
 */
object AnimationHelper {
    
    /**
     * Fade in a view with default duration (300ms).
     * Sets visibility to VISIBLE and animates alpha from 0 to 1.
     * 
     * @param view The view to fade in
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun fadeIn(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Fade out a view with default duration (200ms).
     * Animates alpha from 1 to 0, then sets visibility to GONE.
     * 
     * @param view The view to fade out
     * @param duration Animation duration in milliseconds (default: 200)
     * @param onEnd Optional callback to execute when animation ends
     */
    fun fadeOut(view: View, duration: Long = 200, onEnd: (() -> Unit)? = null) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }
    
    /**
     * Cross-fade between two views.
     * Fades out the first view, then fades in the second view.
     * 
     * @param hideView The view to fade out
     * @param showView The view to fade in
     * @param duration Total animation duration in milliseconds (default: 300)
     */
    fun crossFade(hideView: View, showView: View, duration: Long = 300) {
        fadeOut(hideView, duration / 2) {
            fadeIn(showView, duration / 2)
        }
    }
    
    /**
     * Fade in a view quickly (200ms).
     * Convenience method for faster animations.
     * 
     * @param view The view to fade in
     */
    fun fadeInFast(view: View) {
        fadeIn(view, 200)
    }
    
    /**
     * Show view with fade-in animation.
     * If view is already visible, just ensures alpha is 1.
     * 
     * @param view The view to show
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun showWithFade(view: View, duration: Long = 300) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f) {
            return // Already visible
        }
        fadeIn(view, duration)
    }
    
    /**
     * Hide view with fade-out animation.
     * 
     * @param view The view to hide
     * @param duration Animation duration in milliseconds (default: 200)
     */
    fun hideWithFade(view: View, duration: Long = 200) {
        if (view.visibility == View.GONE) {
            return // Already hidden
        }
        fadeOut(view, duration)
    }
    
    /**
     * Start pulse animation on a view (for loading indicators).
     * Creates a pulsing effect by animating alpha.
     * 
     * @param view The view to animate
     */
    fun startPulseAnimation(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.pulse)
        view.startAnimation(animation)
    }
    
    /**
     * Start rotate animation on a view (for loading spinners).
     * Creates a continuous rotation effect.
     * 
     * @param view The view to animate
     */
    fun startRotateAnimation(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.rotate)
        view.startAnimation(animation)
    }
    
    /**
     * Stop any animation on a view.
     * 
     * @param view The view to stop animating
     */
    fun stopAnimation(view: View) {
        view.clearAnimation()
    }
    
    /**
     * Slide in a view from the right.
     * 
     * @param view The view to slide in
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun slideInFromRight(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.translationX = view.width.toFloat()
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Slide in a view from the left.
     * 
     * @param view The view to slide in
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun slideInFromLeft(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.translationX = -view.width.toFloat()
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Scale in a view with bounce effect.
     * 
     * @param view The view to scale in
     * @param duration Animation duration in milliseconds (default: 400)
     */
    fun scaleIn(view: View, duration: Long = 400) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Scale out a view.
     * 
     * @param view The view to scale out
     * @param duration Animation duration in milliseconds (default: 250)
     * @param onEnd Optional callback when animation ends
     */
    fun scaleOut(view: View, duration: Long = 250, onEnd: (() -> Unit)? = null) {
        view.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.scaleX = 1f
                view.scaleY = 1f
                view.alpha = 1f
                onEnd?.invoke()
            }
            .start()
    }
    
    /**
     * Show loading animation on a view (pulse effect).
     * Useful for loading indicators.
     * 
     * @param view The view to show loading animation on
     */
    fun showLoadingAnimation(view: View) {
        view.visibility = View.VISIBLE
        startPulseAnimation(view)
    }
    
    /**
     * Hide loading animation.
     * 
     * @param view The view to stop loading animation on
     */
    fun hideLoadingAnimation(view: View) {
        stopAnimation(view)
        view.visibility = View.GONE
    }
    
    /**
     * Slide in a view from the bottom (slide up).
     * Similar to admin's slideInUp animation.
     * 
     * @param view The view to slide in
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun slideInUp(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.translationY = 20f * view.context.resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Slide in a view from the top (slide down).
     * Similar to admin's slideInDown animation.
     * 
     * @param view The view to slide in
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun slideInDown(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.translationY = -20f * view.context.resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Slide out a view to the bottom (slide down).
     * 
     * @param view The view to slide out
     * @param duration Animation duration in milliseconds (default: 250)
     * @param onEnd Optional callback when animation ends
     */
    fun slideOutDown(view: View, duration: Long = 250, onEnd: (() -> Unit)? = null) {
        val translationY = 20f * view.context.resources.displayMetrics.density
        view.animate()
            .alpha(0f)
            .translationY(translationY)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.translationY = 0f
                view.alpha = 1f
                onEnd?.invoke()
            }
            .start()
    }
    
    /**
     * Slide out a view to the top (slide up).
     * 
     * @param view The view to slide out
     * @param duration Animation duration in milliseconds (default: 250)
     * @param onEnd Optional callback when animation ends
     */
    fun slideOutUp(view: View, duration: Long = 250, onEnd: (() -> Unit)? = null) {
        val translationY = -20f * view.context.resources.displayMetrics.density
        view.animate()
            .alpha(0f)
            .translationY(translationY)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.translationY = 0f
                view.alpha = 1f
                onEnd?.invoke()
            }
            .start()
    }
    
    /**
     * Animate list item with staggered delay (like admin's staggered animations).
     * 
     * @param view The view to animate
     * @param position Position in the list (for delay calculation)
     * @param delayPerItem Delay per item in milliseconds (default: 50)
     * @param animationType Type of animation (default: slideInUp)
     */
    fun animateListItem(
        view: View,
        position: Int,
        delayPerItem: Long = 50,
        animationType: String = "slideInUp"
    ) {
        val delay = position * delayPerItem
        view.alpha = 0f
        
        when (animationType) {
            "slideInUp" -> {
                view.translationY = 20f * view.context.resources.displayMetrics.density
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            "fadeIn" -> {
                view.animate()
                    .alpha(1f)
                    .setStartDelay(delay)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            "scaleIn" -> {
                view.scaleX = 0.95f
                view.scaleY = 0.95f
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(delay)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            else -> {
                view.animate()
                    .alpha(1f)
                    .setStartDelay(delay)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }
    
    /**
     * Show empty state with admin-style animation (fadeIn + slideInUp).
     * 
     * @param view The empty state view
     * @param duration Animation duration in milliseconds (default: 400)
     */
    fun showEmptyState(view: View, duration: Long = 400) {
        view.alpha = 0f
        view.translationY = 20f * view.context.resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}

