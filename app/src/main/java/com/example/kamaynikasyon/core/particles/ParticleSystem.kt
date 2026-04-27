package com.example.kamaynikasyon.core.particles

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat

/**
 * Manager class for particle effects.
 * Provides easy-to-use methods for creating particle effects in activities.
 */
object ParticleSystem {
    
    /**
     * Play a particle effect in an activity.
     * Automatically creates and manages a ParticleView overlay.
     */
    fun playEffect(activity: Activity, effect: ParticleEffect) {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        playEffect(rootView, effect)
    }
    
    /**
     * Play a particle effect in a view group.
     */
    fun playEffect(container: ViewGroup, effect: ParticleEffect) {
        // Post to ensure layout is complete before adding view (prevents white flash)
        container.post {
            val particleView = ParticleView(container.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            
            container.addView(particleView)
            particleView.addParticles(effect)
            
            // Calculate total duration based on spawn pattern
            val totalDuration = when (effect.spawnPattern) {
                SpawnPattern.FOUNTAIN, SpawnPattern.SEQUENTIAL -> {
                    // For continuous spawning: spawn period + individual particle duration
                    val spawnPeriod = (effect.particleCount - 1) * effect.spawnDelay
                    spawnPeriod + effect.duration + 500 // Add buffer
                }
                else -> {
                    // For burst patterns: just particle duration
                    effect.duration + 500 // Add buffer
                }
            }
            
            // Auto-remove after animation completes
            particleView.postDelayed({
                container.removeView(particleView)
                particleView.stop()
            }, totalDuration)
        }
    }
    
    /**
     * Create a reusable ParticleView that can be manually managed.
     */
    fun createParticleView(container: ViewGroup): ParticleView {
        val particleView = ParticleView(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(particleView)
        return particleView
    }
}

