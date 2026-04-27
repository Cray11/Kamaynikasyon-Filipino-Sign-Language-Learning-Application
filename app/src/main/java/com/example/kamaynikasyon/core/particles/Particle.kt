package com.example.kamaynikasyon.core.particles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable

/**
 * Represents a single particle in the particle system
 */
data class Particle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    val rotationSpeed: Float,
    var rotation: Float = 0f,
    var alpha: Float = 1f,
    var scale: Float = 1f,
    val textSize: Float,
    val color: Int?,
    val content: String,
    val drawable: Drawable?,
    val drawableScale: Float,
    val startTime: Long,
    val duration: Long,
    val fadeOutStart: Float,
    val scaleOverTime: Boolean,
    val scaleStart: Float,
    val scaleEnd: Float,
    val gravity: Float
) {
    private var lastUpdateTime: Long = startTime
    
    fun update(currentTime: Long): Boolean {
        val elapsed = currentTime - startTime
        if (elapsed >= duration) return false
        
        val progress = elapsed.toFloat() / duration
        
        // Update position
        val deltaTime = (currentTime - lastUpdateTime).coerceAtMost(50L) / 1000f // Cap at 50ms
        x += velocityX * deltaTime
        y += velocityY * deltaTime
        
        // Apply gravity
        if (gravity != 0f) {
            velocityY += gravity * deltaTime
        }
        
        // Update rotation
        rotation += rotationSpeed * deltaTime
        
        // Update alpha
        if (progress >= fadeOutStart) {
            val fadeProgress = (progress - fadeOutStart) / (1f - fadeOutStart)
            alpha = (1f - fadeProgress).coerceIn(0f, 1f)
        }
        
        // Update scale
        if (scaleOverTime) {
            val scaleProgress = progress
            scale = scaleStart + (scaleEnd - scaleStart) * scaleProgress
        }
        
        lastUpdateTime = currentTime
        return true
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        if (alpha <= 0f) return
        
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.scale(scale, scale)
        
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        
        if (drawable != null) {
            // Draw drawable
            val bounds = drawable.bounds
            val width = bounds.width() * drawableScale * scale
            val height = bounds.height() * drawableScale * scale
            drawable.setBounds(
                (-width / 2).toInt(),
                (-height / 2).toInt(),
                (width / 2).toInt(),
                (height / 2).toInt()
            )
            drawable.alpha = paint.alpha
            drawable.draw(canvas)
        } else {
            // Draw text/emoji
            paint.textSize = textSize * scale
            color?.let { paint.color = it }
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(content, 0f, 0f, paint)
        }
        
        canvas.restore()
    }
}

