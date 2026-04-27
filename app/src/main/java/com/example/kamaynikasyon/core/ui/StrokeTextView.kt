package com.example.kamaynikasyon.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * A custom TextView that supports text stroke/border for professional text styling.
 * This allows text to have an outline/stroke effect around the characters.
 */
class StrokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeWidth: Float = 0f
    private var strokeColor: Int = android.graphics.Color.WHITE

    /**
     * Set the stroke width in pixels
     */
    fun setStrokeWidth(width: Float) {
        strokeWidth = width
        invalidate()
    }

    /**
     * Set the stroke color
     */
    fun setStrokeColor(color: Int) {
        strokeColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (strokeWidth > 0) {
            // Save the current text color
            val textColor = textColors.defaultColor
            
            // Draw stroke first
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            setTextColor(strokeColor)
            super.onDraw(canvas)
            
            // Draw fill on top
            paint.style = Paint.Style.FILL
            setTextColor(textColor)
            super.onDraw(canvas)
        } else {
            // No stroke, draw normally
            super.onDraw(canvas)
        }
    }
}

