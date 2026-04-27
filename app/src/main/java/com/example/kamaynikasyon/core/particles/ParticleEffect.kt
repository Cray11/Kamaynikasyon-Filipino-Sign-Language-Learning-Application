package com.example.kamaynikasyon.core.particles

import android.graphics.drawable.Drawable

/**
 * Configuration data class for particle effects.
 * Provides flexible parameters for creating various particle effects.
 */
data class ParticleEffect(
    // Particle content - can be text, emoji, or drawable resource ID
    val content: String = "🎉", // Text or emoji
    val drawableResId: Int? = null, // Drawable resource ID if using drawable instead of text
    
    // Position
    val startX: Float = 0f,
    val startY: Float = 0f,
    val spreadX: Float = 100f, // Horizontal spread for multiple particles
    val spreadY: Float = 100f, // Vertical spread for multiple particles
    
    // Movement
    val velocityX: Float = 0f, // Horizontal velocity
    val velocityY: Float = -100f, // Vertical velocity (negative = up)
    val velocitySpreadX: Float = 50f, // Random spread for velocity X
    val velocitySpreadY: Float = 50f, // Random spread for velocity Y
    val gravity: Float = 0f, // Gravity effect (positive = down)
    val rotationSpeed: Float = 0f, // Rotation speed in degrees per second
    
    // Appearance
    val textSize: Float = 40f, // Text size for text/emoji particles
    val scale: Float = 1f, // Scale for drawable particles
    val color: Int? = null, // Text color (null = default)
    val alpha: Float = 1f, // Initial alpha (0-1)
    
    // Animation
    val duration: Long = 2000L, // Duration in milliseconds
    val fadeOutStart: Float = 0.5f, // When to start fading (0-1 of duration)
    val scaleOverTime: Boolean = false, // Whether to scale over time
    val scaleStart: Float = 1f, // Starting scale
    val scaleEnd: Float = 0f, // Ending scale
    
    // Particle count
    val particleCount: Int = 1, // Number of particles to spawn
    
    // Spawn pattern
    val spawnPattern: SpawnPattern = SpawnPattern.BURST, // How particles spawn
    val spawnDelay: Long = 0L, // Delay before spawning (for sequential patterns)
    
    // Text readability
    val isTextReadable: Boolean = false, // If true, text particles start with normal orientation (no rotation)
    
    // Timing
    val repeatCount: Int = 1, // How many times to repeat (0 = infinite until stopped)
    val repeatDelay: Long = 0L // Delay between repeats
)

/**
 * Enum for different spawn patterns
 */
enum class SpawnPattern {
    BURST, // All particles spawn at once
    SEQUENTIAL, // Particles spawn one after another with delay
    FOUNTAIN, // Continuous stream of particles
    CIRCLE, // Particles spawn in a circle
    EXPLOSION // Particles explode outward from center
}

