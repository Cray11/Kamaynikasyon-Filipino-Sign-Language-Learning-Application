package com.example.kamaynikasyon.core.particles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import kotlin.random.Random

/**
 * Custom View for rendering particles.
 * Can be overlayed on top of other views to show particle effects.
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    init {
        // Make view completely transparent and non-interactive
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setWillNotDraw(false) // Ensure onDraw is called
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        // Ensure touches pass through to views behind
        setOnTouchListener { _, _ -> false }
    }
    
    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        // Allow touches to pass through to views behind
        return false
    }
    
    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        // Use app's default font (Fredoka)
        try {
            val typeface = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kamaynikasyon.R.font.fredoka)
            if (typeface != null) {
                this.typeface = typeface
            }
        } catch (_: Exception) {
            // Fallback to default if font not found
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastFrameTime = System.currentTimeMillis()
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            
            val currentTime = System.currentTimeMillis()
            updateParticles(currentTime)
            invalidate()
            
            if (particles.isNotEmpty() || isRunning) {
                handler.postDelayed(this, 16) // ~60 FPS
            }
        }
    }
    
    /**
     * Start the particle animation loop
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameTime = System.currentTimeMillis()
        handler.post(updateRunnable)
    }
    
    /**
     * Stop the particle animation loop
     */
    fun stop() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        particles.clear()
        invalidate()
    }
    
    /**
     * Add particles from a ParticleEffect configuration
     */
    fun addParticles(effect: ParticleEffect) {
        val random = Random(System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()
        
        // Get drawable if specified
        val drawable = effect.drawableResId?.let { 
            ResourcesCompat.getDrawable(resources, it, null)
        }
        
        for (i in 0 until effect.particleCount) {
            val spawnTime = when (effect.spawnPattern) {
                SpawnPattern.BURST -> currentTime
                SpawnPattern.SEQUENTIAL -> currentTime + (i * effect.spawnDelay)
                SpawnPattern.FOUNTAIN -> currentTime + (i * effect.spawnDelay)
                SpawnPattern.CIRCLE -> currentTime
                SpawnPattern.EXPLOSION -> currentTime
            }
            
            val (x, y) = calculateSpawnPosition(effect, i, random)
            val (vx, vy) = calculateVelocity(effect, i, random)
            
            // Determine if this is a readable text particle
            // Text particles should start with normal orientation and no rotation
            val isTextReadable = drawable == null && effect.isTextReadable
            val initialRotation = if (isTextReadable) 0f else random.nextFloat() * 360f
            val particleRotationSpeed = if (isTextReadable) 0f else (effect.rotationSpeed + random.nextFloat() * 20f - 10f)
            
            val particle = Particle(
                x = x,
                y = y,
                velocityX = vx,
                velocityY = vy,
                rotationSpeed = particleRotationSpeed,
                rotation = initialRotation,
                alpha = effect.alpha,
                scale = effect.scale,
                textSize = effect.textSize,
                color = effect.color,
                content = effect.content,
                drawable = drawable,
                drawableScale = effect.scale,
                startTime = spawnTime,
                duration = effect.duration,
                fadeOutStart = effect.fadeOutStart,
                scaleOverTime = effect.scaleOverTime,
                scaleStart = effect.scaleStart,
                scaleEnd = effect.scaleEnd,
                gravity = effect.gravity
            )
            
            particles.add(particle)
        }
        
        if (!isRunning) {
            start()
        }
    }
    
    private fun calculateSpawnPosition(effect: ParticleEffect, index: Int, random: Random): Pair<Float, Float> {
        return when (effect.spawnPattern) {
            SpawnPattern.CIRCLE -> {
                val angle = (index.toFloat() / effect.particleCount) * 360f * kotlin.math.PI / 180f
                val radius = effect.spreadX
                val x = effect.startX + kotlin.math.cos(angle).toFloat() * radius
                val y = effect.startY + kotlin.math.sin(angle).toFloat() * radius
                Pair(x, y)
            }
            SpawnPattern.EXPLOSION -> {
                val angle = random.nextFloat() * 360f * kotlin.math.PI / 180f
                val radius = random.nextFloat() * effect.spreadX
                val x = effect.startX + kotlin.math.cos(angle).toFloat() * radius
                val y = effect.startY + kotlin.math.sin(angle).toFloat() * radius
                Pair(x, y)
            }
            else -> {
                val x = effect.startX + random.nextFloat() * effect.spreadX - effect.spreadX / 2f
                val y = effect.startY + random.nextFloat() * effect.spreadY - effect.spreadY / 2f
                Pair(x, y)
            }
        }
    }
    
    private fun calculateVelocity(effect: ParticleEffect, index: Int, random: Random): Pair<Float, Float> {
        return when (effect.spawnPattern) {
            SpawnPattern.CIRCLE, SpawnPattern.EXPLOSION -> {
                val angle = if (effect.spawnPattern == SpawnPattern.CIRCLE) {
                    (index.toFloat() / effect.particleCount) * 360f * kotlin.math.PI / 180f
                } else {
                    random.nextFloat() * 360f * kotlin.math.PI / 180f
                }
                val speed = effect.velocityY.coerceAtLeast(effect.velocityX).let { 
                    it + random.nextFloat() * effect.velocitySpreadY - effect.velocitySpreadY / 2f
                }
                val vx = kotlin.math.cos(angle).toFloat() * speed
                val vy = kotlin.math.sin(angle).toFloat() * speed
                Pair(vx, vy)
            }
            else -> {
                val vx = effect.velocityX + random.nextFloat() * effect.velocitySpreadX - effect.velocitySpreadX / 2f
                val vy = effect.velocityY + random.nextFloat() * effect.velocitySpreadY - effect.velocitySpreadY / 2f
                Pair(vx, vy)
            }
        }
    }
    
    private fun updateParticles(currentTime: Long) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            if (!particle.update(currentTime)) {
                iterator.remove()
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        // Don't call super.onDraw() to avoid drawing any background
        if (particles.isEmpty()) return
        for (particle in particles) {
            particle.draw(canvas, paint)
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Ensure view has proper size
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}

