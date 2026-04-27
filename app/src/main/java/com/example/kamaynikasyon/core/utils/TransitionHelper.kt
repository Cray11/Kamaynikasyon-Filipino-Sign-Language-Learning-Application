package com.example.kamaynikasyon.core.utils

import android.app.Activity
import android.content.Intent
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import android.view.View
import com.example.kamaynikasyon.R

/**
 * Utility class for smooth activity transitions.
 * Provides helper functions for consistent screen transitions across the app.
 */
object TransitionHelper {
    
    /**
     * Transition types for different navigation scenarios.
     */
    enum class TransitionType {
        SLIDE_RIGHT,    // Slide from right (forward navigation)
        SLIDE_LEFT,     // Slide from left (back navigation)
        FADE,           // Fade transition
        SCALE,          // Scale transition
        NONE            // No transition
    }
    
    /**
     * Start activity with smooth slide-right transition (forward navigation).
     * 
     * @param activity Current activity
     * @param intent Intent to start
     * @param sharedElements Optional shared elements for shared element transition
     */
    fun startActivityWithSlideRight(
        activity: Activity,
        intent: Intent,
        vararg sharedElements: Pair<View, String>
    ) {
        if (sharedElements.isNotEmpty()) {
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                activity,
                *sharedElements
            )
            activity.startActivity(intent, options.toBundle())
        } else {
            activity.startActivity(intent)
            activity.overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }
    
    /**
     * Start activity with smooth slide-left transition (back navigation).
     * 
     * @param activity Current activity
     * @param intent Intent to start
     */
    fun startActivityWithSlideLeft(
        activity: Activity,
        intent: Intent
    ) {
        activity.startActivity(intent)
        activity.overridePendingTransition(
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
    }
    
    /**
     * Start activity with fade transition.
     * 
     * @param activity Current activity
     * @param intent Intent to start
     */
    fun startActivityWithFade(
        activity: Activity,
        intent: Intent
    ) {
        activity.startActivity(intent)
        activity.overridePendingTransition(
            R.anim.fade_in,
            R.anim.fade_out
        )
    }
    
    /**
     * Start activity with scale transition.
     * 
     * @param activity Current activity
     * @param intent Intent to start
     */
    fun startActivityWithScale(
        activity: Activity,
        intent: Intent
    ) {
        activity.startActivity(intent)
        activity.overridePendingTransition(
            R.anim.scale_in,
            R.anim.scale_out
        )
    }
    
    /**
     * Start activity with custom transition type.
     * 
     * @param activity Current activity
     * @param intent Intent to start
     * @param type Transition type
     * @param sharedElements Optional shared elements
     */
    fun startActivityWithTransition(
        activity: Activity,
        intent: Intent,
        type: TransitionType = TransitionType.SLIDE_RIGHT,
        vararg sharedElements: Pair<View, String>
    ) {
        when (type) {
            TransitionType.SLIDE_RIGHT -> startActivityWithSlideRight(activity, intent, *sharedElements)
            TransitionType.SLIDE_LEFT -> startActivityWithSlideLeft(activity, intent)
            TransitionType.FADE -> startActivityWithFade(activity, intent)
            TransitionType.SCALE -> startActivityWithScale(activity, intent)
            TransitionType.NONE -> activity.startActivity(intent)
        }
    }
    
    /**
     * Finish activity with smooth transition.
     * 
     * @param activity Activity to finish
     * @param type Transition type (default: slide left for back navigation)
     */
    fun finishWithTransition(
        activity: Activity,
        type: TransitionType = TransitionType.SLIDE_LEFT
    ) {
        when (type) {
            TransitionType.SLIDE_LEFT -> {
                activity.finish()
                activity.overridePendingTransition(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
            }
            TransitionType.FADE -> {
                activity.finish()
                activity.overridePendingTransition(
                    R.anim.fade_in,
                    R.anim.fade_out
                )
            }
            TransitionType.SCALE -> {
                activity.finish()
                activity.overridePendingTransition(
                    R.anim.scale_in,
                    R.anim.scale_out
                )
            }
            else -> activity.finish()
        }
    }
}

/**
 * Extension function for Activity to start with slide-right transition.
 */
fun Activity.startActivityWithTransition(intent: Intent) {
    TransitionHelper.startActivityWithSlideRight(this, intent)
}

/**
 * Extension function for Activity to finish with transition.
 */
fun Activity.finishWithTransition() {
    TransitionHelper.finishWithTransition(this)
}

/**
 * Extension function for Fragment to start activity with transition.
 */
fun androidx.fragment.app.Fragment.startActivityWithTransition(intent: Intent) {
    activity?.let {
        TransitionHelper.startActivityWithSlideRight(it, intent)
    }
}

