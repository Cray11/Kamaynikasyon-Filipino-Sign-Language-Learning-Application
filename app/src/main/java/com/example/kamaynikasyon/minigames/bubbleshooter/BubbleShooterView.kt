package com.example.kamaynikasyon.minigames.bubbleshooter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.core.content.res.ResourcesCompat
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class BubbleShooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Dedicated Paint objects for stroked text (optimized, no state changes needed)
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.BLACK
    }
    private val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val bubbleOverlayDrawable by lazy {
        ResourcesCompat.getDrawable(resources, com.example.kamaynikasyon.R.drawable.bubble_overlay, null)
    }

    private var gameState: GameState? = null
    private var ballRadius = 45f
    private var shooterX = 0f
    private var shooterY = 0f
    private var wallLeft = 0f
    private var wallRight = 0f
    private var gridStartY = 0f

    private var isAiming = false
    private var aimStartX = 0f
    private var aimStartY = 0f
    private var aimDirectionX = 0f
    private var aimDirectionY = 0f
    private val aimLine = mutableListOf<PointF>()

    var onBallShot: ((Float, Float) -> Unit)? = null
    var onGridConfigChanged: ((Float, Float, Float) -> Unit)? = null

    init {
        textPaint.color = Color.WHITE
        textPaint.textSize = 40f
        textPaint.textAlign = Paint.Align.CENTER
        try {
            val tf = ResourcesCompat.getFont(context, com.example.kamaynikasyon.R.font.fredoka)
            if (tf != null) {
                textPaint.typeface = tf
                // Share the same typeface with stroke/fill paints
                textStrokePaint.typeface = tf
                textFillPaint.typeface = tf
            }
        } catch (_: Exception) {}
        // Configure text alignment for stroke/fill paints
        textStrokePaint.textAlign = Paint.Align.CENTER
        textFillPaint.textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ballRadius = min(w, h) * 0.05f * 1.3f
        shooterX = w / 2f
        shooterY = h - ballRadius * 6.5f // Moved up to avoid panel overlap
        // Open walls by additional 1/2 ball: shift left wall leftwards and right wall rightwards
        wallLeft = -ballRadius * 0.5f
        wallRight = w + ballRadius * 0.5f
        val horizontalSpacing = ballRadius * 2f
        val maxGridWidth = 9 * horizontalSpacing + ballRadius * 3f
        val available = wallRight - wallLeft
        val centerOffset = (available - maxGridWidth) / 2f
        val gridStartX = wallLeft + centerOffset + ballRadius
        gridStartY = ballRadius * 1f
        onGridConfigChanged?.invoke(ballRadius, gridStartX, gridStartY)
    }

    fun setGameState(state: GameState) {
        gameState = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Keep background transparent so camera remains visible beneath
        val state = gameState ?: return
        drawGrid(canvas, state)
        drawMoving(canvas, state.movingBalls)
        drawFalling(canvas, state.fallingBalls)
        drawShooter(canvas, state)
        if (isAiming) drawAimLine(canvas)
        // Walls remain for physics but are invisible visually
        drawLoseLine(canvas)
        if (state.isGameWon) {
            textPaint.color = Color.GREEN
            textPaint.textSize = 48f
            canvas.drawText("Level Complete!", width / 2f, height / 2f, textPaint)
            textPaint.color = Color.WHITE
        } else if (state.isGameOver) {
            textPaint.color = Color.RED
            textPaint.textSize = 48f
            canvas.drawText("Game Over", width / 2f, height / 2f, textPaint)
            textPaint.color = Color.WHITE
        }
    }

    private fun drawLoseLine(canvas: Canvas) {
        val loseRowIndex = 7
        val verticalSpacing = ballRadius * kotlin.math.sqrt(3f)
        val lineY = gridStartY + loseRowIndex * verticalSpacing
        if (lineY in 0f..height.toFloat()) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.RED
            paint.strokeWidth = 6f
            paint.pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
            canvas.drawLine(wallLeft, lineY, wallRight, lineY, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawGrid(canvas: Canvas, state: GameState) {
		for ((pos, ball) in state.gridBalls) {
            if (pos in state.emptyPositions) continue
            drawBubble(canvas, ball)
            if (ball.radius > 20f) {
				val size = ball.radius * 0.6f
				drawTextWithStroke(canvas, ball.ballType.letter, ball.centerX, ball.centerY + size / 3, size)
            }
        }
    }

    private fun drawMoving(canvas: Canvas, balls: List<Ball>) {
        for (ball in balls) drawBubble(canvas, ball)
    }

    private fun drawFalling(canvas: Canvas, balls: List<Ball>) {
        paint.alpha = 200
        for (ball in balls) drawBubble(canvas, ball)
        paint.alpha = 255
    }

    private fun drawShooter(canvas: Canvas, state: GameState) {
        val color = if (state.canShoot) state.currentBall.color else Color.GRAY
        drawBubbleAt(canvas, shooterX, shooterY, ballRadius, color)
		val size = ballRadius * 0.6f
		drawTextWithStroke(canvas, state.currentBall.letter.ifEmpty { "?" }, shooterX, shooterY + size / 3, size)
        // Use same text size as result panel (16sp converted to pixels)
        val textSizeSp = 16f
        textPaint.textSize = textSizeSp * resources.displayMetrics.scaledDensity
        textPaint.color = if (state.canShoot) Color.GREEN else Color.RED
        val status = if (state.canShoot) "Ready: ${state.predictedLetter}" else "Gesture: ${state.predictedLetter}"
        canvas.drawText(status, shooterX, shooterY + ballRadius * 2f, textPaint)
        textPaint.color = Color.WHITE
    }

	private fun drawTextWithStroke(canvas: Canvas, text: String, x: Float, y: Float, size: Float) {
		// Stroke (outline) - using dedicated Paint object (no state changes)
		textStrokePaint.strokeWidth = size * 0.12f
		textStrokePaint.textSize = size
		canvas.drawText(text, x, y, textStrokePaint)
		// Fill - using dedicated Paint object (no state changes)
		textFillPaint.textSize = size
		canvas.drawText(text, x, y, textFillPaint)
	}

    private fun drawBubble(canvas: Canvas, ball: Ball) {
        drawBubbleAt(canvas, ball.centerX, ball.centerY, ball.radius, ball.ballType.color)
    }

    private fun drawBubbleAt(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Base colored circle (no stroke)
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, paint)
        // Overlay highlights (no tint)
        val d = bubbleOverlayDrawable ?: return
        val left = (cx - radius).toInt()
        val top = (cy - radius).toInt()
        val right = (cx + radius).toInt()
        val bottom = (cy + radius).toInt()
        d.setBounds(left, top, right, bottom)
        d.alpha = paint.alpha
        d.draw(canvas)
    }

    private fun drawAimLine(canvas: Canvas) {
        if (aimLine.size < 2) return
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        val path = Path()
        path.moveTo(aimLine[0].x, aimLine[0].y)
        for (i in 1 until aimLine.size) path.lineTo(aimLine[i].x, aimLine[i].y)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
    }

    private fun drawWalls(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, wallLeft, height.toFloat(), paint)
        canvas.drawRect(wallRight, 0f, width.toFloat(), height.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawLine(wallLeft, 0f, wallLeft, height.toFloat(), paint)
        canvas.drawLine(wallRight, 0f, wallRight, height.toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val state = gameState
        if (state != null && (state.isGameOver || state.isGameWon)) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isAiming = true
                aimStartX = shooterX
                aimStartY = shooterY
                val dx = event.x - shooterX
                val dy = event.y - shooterY
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                aimDirectionX = dx / d
                aimDirectionY = dy / d
                updateAimLine()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isAiming) {
                    val dx = event.x - shooterX
                    val dy = event.y - shooterY
                    val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    aimDirectionX = dx / d
                    aimDirectionY = dy / d
                    updateAimLine()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isAiming) {
                    isAiming = false
                    aimLine.clear()
                    if (state?.canShoot == true) {
                        val targetX = shooterX + aimDirectionX * 1000f
                        val targetY = shooterY + aimDirectionY * 1000f
                        onBallShot?.invoke(targetX, targetY)
                    }
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateAimLine() {
        aimLine.clear()
        var sx = aimStartX
        var sy = aimStartY
        var dx = aimDirectionX
        var dy = aimDirectionY

        fun firstIntersection(x: Float, y: Float, vx: Float, vy: Float): PointF {
            var tHit: Float? = null
            var hx = x
            var hy = y
            // Left wall
            if (vx < 0f) {
                val wx = wallLeft + ballRadius
                val t = (wx - x) / vx
                if (t > 0f) { tHit = t; hx = wx; hy = y + vy * t }
            }
            // Right wall
            if (vx > 0f) {
                val wx = wallRight - ballRadius
                val t = (wx - x) / vx
                if (t > 0f && (tHit == null || t < tHit!!)) { tHit = t; hx = wx; hy = y + vy * t }
            }
            // Top boundary
            if (vy < 0f) {
                val ty = 0f
                val t = (ty - y) / vy
                if (t > 0f && (tHit == null || t < tHit!!)) { tHit = t; hx = x + vx * t; hy = ty }
            }
            if (tHit == null) {
                val far = 10000f
                return PointF(x + vx * far, y + vy * far)
            }
            return PointF(hx, hy)
        }

        // Segment 1: start -> first hit
        val hit1 = firstIntersection(sx, sy, dx, dy)
        aimLine.add(PointF(sx, sy))
        aimLine.add(hit1)

        // Determine if hit was a wall; if so, reflect once and add a second segment
        var reflected = false
        if (hit1.y > 0f) { // not top
            if (Math.abs(hit1.x - (wallLeft + ballRadius)) < 1e-3f || Math.abs(hit1.x - (wallRight - ballRadius)) < 1e-3f) {
                reflected = true
            }
        }
        if (reflected) {
            // Reflect horizontally
            dx = -dx
            sx = hit1.x
            sy = hit1.y
            val hit2 = firstIntersection(sx, sy, dx, dy)
            aimLine.add(hit2)
        }
    }
}


