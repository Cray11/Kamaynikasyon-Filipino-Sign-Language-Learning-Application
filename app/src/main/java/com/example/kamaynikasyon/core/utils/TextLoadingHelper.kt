package com.example.kamaynikasyon.core.utils

import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.kamaynikasyon.R

/**
 * Utility class for animating dynamically loaded text values.
 * Provides loading states and smooth text change animations.
 */
object TextLoadingHelper {
    
    /**
     * Show loading state on a TextView (pulse animation).
     * 
     * @param textView The TextView to show loading state on
     * @param placeholderText Optional placeholder text to show while loading (default: "...")
     */
    fun showLoading(textView: TextView, placeholderText: String = "...") {
        textView.text = placeholderText
        textView.alpha = 0.5f
        val animation = AnimationUtils.loadAnimation(textView.context, R.anim.pulse)
        textView.startAnimation(animation)
    }
    
    /**
     * Hide loading state and set text with animation.
     * 
     * @param textView The TextView to update
     * @param text The new text to display
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun setTextWithAnimation(
        textView: TextView,
        text: String,
        duration: Long = 300
    ) {
        textView.clearAnimation()
        
        // Fade out current text
        textView.animate()
            .alpha(0f)
            .setDuration(duration / 2)
            .withEndAction {
                // Update text
                textView.text = text
                // Fade in new text
                textView.animate()
                    .alpha(1f)
                    .setDuration(duration / 2)
                    .start()
            }
            .start()
    }
    
    /**
     * Set text with slide-up animation (admin-style).
     * 
     * @param textView The TextView to update
     * @param text The new text to display
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun setTextWithSlideUp(
        textView: TextView,
        text: String,
        duration: Long = 300
    ) {
        textView.clearAnimation()
        
        val density = textView.context.resources.displayMetrics.density
        val slideDistance = 10f * density
        
        // Slide out current text
        textView.animate()
            .alpha(0f)
            .translationY(-slideDistance)
            .setDuration(duration / 2)
            .withEndAction {
                // Update text and position
                textView.text = text
                textView.translationY = slideDistance
                textView.alpha = 0f
                
                // Slide in new text
                textView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(duration / 2)
                    .start()
            }
            .start()
    }
    
    /**
     * Set text with scale animation.
     * 
     * @param textView The TextView to update
     * @param text The new text to display
     * @param duration Animation duration in milliseconds (default: 300)
     */
    fun setTextWithScale(
        textView: TextView,
        text: String,
        duration: Long = 300
    ) {
        textView.clearAnimation()
        
        // Scale out current text
        textView.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(duration / 2)
            .withEndAction {
                // Update text and reset scale
                textView.text = text
                textView.scaleX = 1.2f
                textView.scaleY = 1.2f
                textView.alpha = 0f
                
                // Scale in new text
                textView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration / 2)
                    .start()
            }
            .start()
    }
    
    /**
     * Animate number change with counting effect.
     * 
     * @param textView The TextView to update
     * @param targetValue The target number value
     * @param duration Animation duration in milliseconds (default: 800)
     * @param format Optional format string (e.g., "%d", "Score: %d")
     */
    fun animateNumberChange(
        textView: TextView,
        targetValue: Int,
        duration: Long = 800,
        format: String = "%d"
    ) {
        textView.clearAnimation()
        
        val currentText = textView.text.toString()
        val currentValue = try {
            currentText.filter { it.isDigit() }.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
        
        if (currentValue == targetValue) {
            textView.text = String.format(format, targetValue)
            return
        }
        
        val startValue = currentValue
        val valueRange = targetValue - startValue
        val steps = (duration / 16).toInt().coerceAtLeast(1) // ~60fps
        val stepValue = valueRange.toFloat() / steps
        
        var currentStep = 0
        
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (currentStep <= steps) {
                    val animatedValue = (startValue + (stepValue * currentStep)).toInt()
                    textView.text = String.format(format, animatedValue)
                    currentStep++
                    if (currentStep <= steps) {
                        handler.postDelayed(this, duration / steps)
                    }
                }
            }
        }
        handler.post(runnable)
    }
}

/**
 * Extension function for TextView to show loading state.
 */
fun TextView.showLoading(placeholderText: String = "...") {
    TextLoadingHelper.showLoading(this, placeholderText)
}

/**
 * Extension function for TextView to set text with animation.
 */
fun TextView.setTextAnimated(text: String, duration: Long = 300) {
    TextLoadingHelper.setTextWithAnimation(this, text, duration)
}

/**
 * Extension function for TextView to set text with slide-up animation.
 */
fun TextView.setTextWithSlideUp(text: String, duration: Long = 300) {
    TextLoadingHelper.setTextWithSlideUp(this, text, duration)
}

/**
 * Extension function for TextView to animate number change.
 */
fun TextView.animateNumber(value: Int, duration: Long = 800, format: String = "%d") {
    TextLoadingHelper.animateNumberChange(this, value, duration, format)
}

