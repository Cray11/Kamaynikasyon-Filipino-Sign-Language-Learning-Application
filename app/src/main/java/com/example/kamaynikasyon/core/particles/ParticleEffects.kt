package com.example.kamaynikasyon.core.particles

import android.graphics.Color

/**
 * Pre-configured particle effects for common use cases.
 */
object ParticleEffects {
    
    /**
     * Confetti effect - perfect for level completion
     * Spawns particles continuously over 3 seconds
     */
    fun confetti(
        centerX: Float,
        centerY: Float,
        width: Float = 1000f,
        height: Float = 1000f
    ): ParticleEffect {
        val totalDuration = 3000L // 3 seconds
        val particleCount = 30 // Total particles to spawn over 3 seconds
        val spawnDelay = totalDuration / particleCount // Delay between each particle spawn
        
        return ParticleEffect(
            content = "🎉",
            startX = centerX,
            startY = centerY,
            spreadX = width,
            spreadY = height,
            velocityX = 0f,
            velocityY = -200f,
            velocitySpreadX = 300f,
            velocitySpreadY = 300f,
            gravity = 500f,
            rotationSpeed = 180f,
            textSize = 40f,
            alpha = 1f,
            duration = 2500L, // Individual particle duration (longer than spawn period)
            fadeOutStart = 0.6f,
            scaleOverTime = true,
            scaleStart = 1f,
            scaleEnd = 0f,
            particleCount = particleCount,
            spawnPattern = SpawnPattern.FOUNTAIN, // Continuous spawning
            spawnDelay = spawnDelay // Spawn one particle every ~50ms
        )
    }
    
    /**
     * Text pop-up effect - for "Good Job", "It's a Match", etc.
     */
    fun textPopUp(
        text: String,
        x: Float,
        y: Float,
        textSize: Float = 48f,
        color: Int = Color.WHITE,
        duration: Long = 2000L
    ): ParticleEffect {
        return ParticleEffect(
            content = text,
            startX = x,
            startY = y,
            spreadX = 0f,
            spreadY = 0f,
            velocityX = 0f,
            velocityY = -150f, // Float up
            velocitySpreadX = 0f,
            velocitySpreadY = 0f,
            gravity = 0f,
            rotationSpeed = 0f,
            textSize = textSize,
            color = color,
            alpha = 1f,
            duration = duration,
            fadeOutStart = 0.5f,
            scaleOverTime = true,
            scaleStart = 0.5f,
            scaleEnd = 1.2f,
            particleCount = 1,
            spawnPattern = SpawnPattern.BURST,
            isTextReadable = true // Text should be readable (no rotation)
        )
    }
    
    /**
     * Pop effect - for subtle particle bursts (like popping balls)
     */
    fun pop(
        x: Float,
        y: Float,
        content: String = "✨",
        particleCount: Int = 8,
        intensity: Float = 1f
    ): ParticleEffect {
        return ParticleEffect(
            content = content,
            startX = x,
            startY = y,
            spreadX = 50f * intensity,
            spreadY = 50f * intensity,
            velocityX = 0f,
            velocityY = 0f,
            velocitySpreadX = 100f * intensity,
            velocitySpreadY = 100f * intensity,
            gravity = 200f * intensity,
            rotationSpeed = 90f,
            textSize = 24f * intensity,
            alpha = 1f,
            duration = (800L * intensity).toLong(),
            fadeOutStart = 0.3f,
            scaleOverTime = true,
            scaleStart = 1f,
            scaleEnd = 0f,
            particleCount = particleCount,
            spawnPattern = SpawnPattern.EXPLOSION
        )
    }
    
    /**
     * Celebration burst - multiple confetti emojis
     */
    fun celebrationBurst(
        centerX: Float,
        centerY: Float,
        emojis: List<String> = listOf("🎉", "🎊", "✨", "⭐", "🎈")
    ): ParticleEffect {
        return ParticleEffect(
            content = emojis.random(),
            startX = centerX,
            startY = centerY,
            spreadX = 200f,
            spreadY = 200f,
            velocityX = 0f,
            velocityY = -150f,
            velocitySpreadX = 250f,
            velocitySpreadY = 250f,
            gravity = 400f,
            rotationSpeed = 120f,
            textSize = 35f,
            alpha = 1f,
            duration = 2500L,
            fadeOutStart = 0.5f,
            scaleOverTime = true,
            scaleStart = 0.8f,
            scaleEnd = 0f,
            particleCount = 20,
            spawnPattern = SpawnPattern.EXPLOSION
        )
    }
}

