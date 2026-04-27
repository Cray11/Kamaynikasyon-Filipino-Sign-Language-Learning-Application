/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.kamaynikasyon.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.example.kamaynikasyon.R
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()
    private var debugPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    // Store preview view dimensions explicitly to ensure correct alignment
    private var previewViewWidth: Int = 0
    private var previewViewHeight: Int = 0

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        debugPaint.reset()
        offsetX = 0f
        offsetY = 0f
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context!!, R.color.color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL

        // Debug paint for showing camera image area
        debugPaint.color = Color.parseColor("#33FF0000") // Semi-transparent red
        debugPaint.style = Paint.Style.STROKE
        debugPaint.strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { handLandmarkerResult ->
            var visibleLandmarks = 0
            var totalLandmarks = 0
            
            for (landmark in handLandmarkerResult.landmarks()) {
                // Store landmark coordinates for connection drawing
                val landmarkPoints = mutableListOf<Pair<Float, Float>>()
                
                for (normalizedLandmark in landmark) {
                    totalLandmarks++
                    // Apply coordinate transformation with proper scaling and offset
                    val x = normalizedLandmark.x() * imageWidth * scaleFactor + offsetX
                    val y = normalizedLandmark.y() * imageHeight * scaleFactor + offsetY
                    
                    landmarkPoints.add(Pair(x, y))
                    
                    // Draw landmarks with more lenient bounds checking (allow some padding)
                    if (isPointVisible(x, y)) {
                        visibleLandmarks++
                        // Clamp coordinates to view bounds for drawing
                        val (clampedX, clampedY) = clampToBounds(x, y)
                        canvas.drawPoint(clampedX, clampedY, pointPaint)
                    }
                }

                // Draw connections between landmarks
                HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                    val startIndex = connection.start()
                    val endIndex = connection.end()
                    
                    if (startIndex < landmarkPoints.size && endIndex < landmarkPoints.size) {
                        val startPoint = landmarkPoints[startIndex]
                        val endPoint = landmarkPoints[endIndex]
                        
                        val startX = startPoint.first
                        val startY = startPoint.second
                        val endX = endPoint.first
                        val endY = endPoint.second
                        
                        // Draw connection if at least one point is visible (with padding)
                        val startVisible = isPointVisible(startX, startY)
                        val endVisible = isPointVisible(endX, endY)
                        
                        if (startVisible || endVisible) {
                            // Clamp coordinates to view bounds
                            val (clampedStartX, clampedStartY) = clampToBounds(startX, startY)
                            val (clampedEndX, clampedEndY) = clampToBounds(endX, endY)
                            
                            canvas.drawLine(clampedStartX, clampedStartY, clampedEndX, clampedEndY, linePaint)
                        }
                    }
                }
            }
            
            // Log landmark visibility for debugging
            if (totalLandmarks > 0) {
                android.util.Log.d("OverlayView", "Landmarks: $visibleLandmarks/$totalLandmarks visible")
            }
        }
    }

    /**
     * Set the preview view dimensions explicitly to ensure correct coordinate transformation.
     * This should be called whenever the preview view is measured or resized.
     */
    fun setPreviewViewDimensions(width: Int, height: Int) {
        previewViewWidth = width
        previewViewHeight = height
        // Recalculate transformation if we already have results
        if (results != null && imageWidth > 0 && imageHeight > 0) {
            calculateTransformation()
            invalidate()
        }
    }

    fun setResults(
        handLandmarkerResults: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = handLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        calculateTransformation(runningMode)
        
        // Log alignment information for debugging
        android.util.Log.d("OverlayView", "Alignment: scaleFactor=$scaleFactor, offsetX=$offsetX, offsetY=$offsetY, viewSize=${width}x${height}, previewViewSize=${previewViewWidth}x${previewViewHeight}, imageSize=${imageWidth}x${imageHeight}")
        
        invalidate()
    }

    private fun calculateTransformation(runningMode: RunningMode = RunningMode.LIVE_STREAM) {
        // Use preview view dimensions if available, otherwise fall back to overlay view dimensions
        val viewWidth = if (previewViewWidth > 0) previewViewWidth else width
        val viewHeight = if (previewViewHeight > 0) previewViewHeight else height

        // Ensure we have valid dimensions
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            android.util.Log.w("OverlayView", "Invalid dimensions: view=${viewWidth}x${viewHeight}, image=${imageWidth}x${imageHeight}")
            scaleFactor = 1f
            offsetX = 0f
            offsetY = 0f
            return
        }

        when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                // For images/videos, use fitCenter scaling
                scaleFactor = min(viewWidth * 1f / imageWidth, viewHeight * 1f / imageHeight)
                val scaledWidth = imageWidth * scaleFactor
                val scaledHeight = imageHeight * scaleFactor
                offsetX = (viewWidth - scaledWidth) / 2f
                offsetY = (viewHeight - scaledHeight) / 2f
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in centerCrop mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed, and account for cropping.
                scaleFactor = max(viewWidth * 1f / imageWidth, viewHeight * 1f / imageHeight)
                val scaledWidth = imageWidth * scaleFactor
                val scaledHeight = imageHeight * scaleFactor
                offsetX = (viewWidth - scaledWidth) / 2f
                offsetY = (viewHeight - scaledHeight) / 2f
            }
        }
    }

    private fun isPointVisible(x: Float, y: Float, padding: Float = 50f): Boolean {
        val viewWidth = if (previewViewWidth > 0) previewViewWidth else width
        val viewHeight = if (previewViewHeight > 0) previewViewHeight else height
        return x >= -padding && x <= viewWidth + padding && y >= -padding && y <= viewHeight + padding
    }
    
    private fun clampToBounds(x: Float, y: Float): Pair<Float, Float> {
        val viewWidth = if (previewViewWidth > 0) previewViewWidth else width
        val viewHeight = if (previewViewHeight > 0) previewViewHeight else height
        return Pair(
            x.coerceIn(0f, viewWidth.toFloat()),
            y.coerceIn(0f, viewHeight.toFloat())
        )
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}

