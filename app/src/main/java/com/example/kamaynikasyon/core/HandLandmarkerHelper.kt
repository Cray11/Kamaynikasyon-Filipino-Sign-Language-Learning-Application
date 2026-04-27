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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import kotlin.math.min
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.example.kamaynikasyon.core.ModelConfigFactory

class HandLandmarkerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var maxNumHands: Int = DEFAULT_NUM_HANDS,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    // this listener is only used when running in RunningMode.LIVE_STREAM
    val handLandmarkerHelperListener: LandmarkerListener? = null,
    // Optional custom model configuration
    val customModelConfig: TFLiteModelConfig? = null
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Hand Landmarker will not change, a lazy val would be preferable.
    private var handLandmarker: HandLandmarker? = null
    
    // TensorFlow Lite model helper for processing hand landmarks
    private var tfLiteModelHelper: TFLiteModelHelper? = null

    init {
        setupHandLandmarker()
        setupTFLiteModel()
    }

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    // Return running status of HandLandmarkerHelper
    fun isClose(): Boolean {
        return handLandmarker == null
    }

    // Initialize the Hand landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupHandLandmarker() {
        // Set general hand landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        // Check if runningMode is consistent with handLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (handLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "handLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            // Create an option builder with base options and specific
            // options only use for Hand Landmarker.
            val optionsBuilder =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setNumHands(maxNumHands)
                    .setRunningMode(runningMode)

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            handLandmarker =
                HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        } catch (e: UnsatisfiedLinkError) {
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker native libraries missing for this device architecture. Detection disabled."
            )
            logAbiWarning(e)
        }
    }

    // Setup TensorFlow Lite model for processing hand landmarks
    private fun setupTFLiteModel() {
        try {
            // Use custom model config if provided, otherwise use default alphabet mapping
            val modelConfig = customModelConfig ?: ModelConfigFactory.createFromMappingPath(
                context,
                "ml/alphabet_mapping.json"
            )
            
            tfLiteModelHelper = TFLiteModelHelper(
                context = context,
                modelConfig = modelConfig,
                useGPU = currentDelegate == DELEGATE_GPU
            )
            Log.d(TAG, "TFLite model setup successful with config: ${modelConfig.getModelName()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup TFLite model: ${e.message}")
        }
    }

    private fun logAbiWarning(error: UnsatisfiedLinkError) {
        val supportedAbis = Build.SUPPORTED_ABIS?.joinToString() ?: "unknown"
        Log.e(
            TAG,
            "Native library load failed: ${error.message}. Device ABIs: $supportedAbis. " +
                "Ensure arm64-v8a or armeabi-v7a builds are installed."
        )
    }

    // Helper method to safely convert ImageProxy (YUV_420_888) to Bitmap
    // This converts the YUV planes to an NV21 byte array, compresses to JPEG via YuvImage,
    // then decodes the JPEG to a Bitmap. This is resilient to row/ pixel stride variations
    // and avoids direct copyPixelsFromBuffer on the Y plane buffer which can cause
    // Buffer not large enough for pixels exceptions.
    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val image = imageProxy.image
        if (image == null) {
            // Fallback to an empty bitmap with correct size
            return Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        }

        val width = image.width
        val height = image.height

        // Collect YUV planes
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y
        yBuffer.get(nv21, 0, ySize)

        // Interleave V and U into NV21 format (V then U)
        var pos = ySize

        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStride = image.planes[1].pixelStride

        // Duplicate buffers so we can access them by index without changing their position
        val vBufDup = image.planes[2].buffer.duplicate()
        val uBufDup = image.planes[1].buffer.duplicate()

        val chromaHeight = height / 2
        val chromaWidth = width / 2

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * chromaRowStride + col * chromaPixelStride
                val uIndex = row * chromaRowStride + col * chromaPixelStride
                // Safety checks
                if (vIndex >= 0 && vIndex < vBufDup.limit() && uIndex >= 0 && uIndex < uBufDup.limit()) {
                    nv21[pos++] = vBufDup.get(vIndex)
                    nv21[pos++] = uBufDup.get(uIndex)
                } else {
                    // In case of unexpected stride layouts, append zeros to keep array size consistent
                    nv21[pos++] = 0
                    nv21[pos++] = 0
                }
            }
        }

        // Convert NV21 byte array to JPEG, then decode to Bitmap
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        val success = yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        if (!success) {
            // Fallback empty bitmap
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // Convert MediaPipe landmarks to TFLite format and process
    private fun processLandmarksWithTFLite(landmarks: List<List<*>>): Float {
        return try {
            // Check if no hands are detected
            if (landmarks.isEmpty() || landmarks.all { it.isEmpty() }) {
                return -1f
            }
            
            // Convert landmarks to Float coordinates (like C# implementation)
            val flattenedLandmarks = mutableListOf<Float>()
            
            // Process only the first hand (like C# implementation)
            val firstHand = landmarks[0]
            if (firstHand.size >= 21) { // Need at least 21 landmarks
                for (i in 0 until 21) {
                    val landmark = firstHand[i]
                    if (landmark != null) {
                        try {
                            val x = landmark.javaClass.getMethod("x").invoke(landmark) as? Float ?: 0f
                            val y = landmark.javaClass.getMethod("y").invoke(landmark) as? Float ?: 0f
                            
                            flattenedLandmarks.add(x)
                            flattenedLandmarks.add(y)
                        } catch (e: Exception) {
                            flattenedLandmarks.add(0f)
                            flattenedLandmarks.add(0f)
                        }
                    } else {
                        flattenedLandmarks.add(0f)
                        flattenedLandmarks.add(0f)
                    }
                }
            }
            
            // Check if we have valid landmark data (should be 42 values: 21 landmarks × 2 coordinates)
            if (flattenedLandmarks.size != 42) {
                Log.w(TAG, "Invalid landmark data size: ${flattenedLandmarks.size}, expected 42")
                return -1f
            }
            
            // Convert to the format TFLite expects
            val tfLiteInput = listOf(flattenedLandmarks)
            
            // Get the full output array and extract the confidence
            val outputArray = tfLiteModelHelper?.processLandmarksWithOutput(tfLiteInput)
            if (outputArray != null && outputArray.isNotEmpty()) {
                // Find the index with highest confidence
                val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0
                val confidence = outputArray[maxIndex]
                Log.d(TAG, "TFLite confidence: $confidence at index $maxIndex")
                return confidence
            }
            
            -1f
        } catch (e: Exception) {
            Log.e(TAG, "Error processing landmarks with TFLite: ${e.message}")
            -1f
        }
    }

    // Get predicted letter from TFLite model
    private fun getPredictedLetter(landmarks: List<List<*>>): String {
        return try {
            // Check if no hands are detected
            if (landmarks.isEmpty() || landmarks.all { it.isEmpty() }) {
                return "?"
            }
            
            // Convert landmarks to the format TFLite expects (like C# implementation)
            // Each landmark should provide x, y coordinates in sequence
            val floatLandmarks = mutableListOf<Float>()
            
            // Process only the first hand (like C# implementation)
            val firstHand = landmarks[0]
            if (firstHand.size >= 21) { // Need at least 21 landmarks
                for (i in 0 until 21) {
                    val landmark = firstHand[i]
                    if (landmark != null) {
                        try {
                            val x = landmark.javaClass.getMethod("x").invoke(landmark) as? Float ?: 0f
                            val y = landmark.javaClass.getMethod("y").invoke(landmark) as? Float ?: 0f
                            floatLandmarks.add(x)
                            floatLandmarks.add(y)
                        } catch (e: Exception) {
                            floatLandmarks.add(0f)
                            floatLandmarks.add(0f)
                        }
                    } else {
                        floatLandmarks.add(0f)
                        floatLandmarks.add(0f)
                    }
                }
            }
            
            // Check if we have valid landmark data (should be 42 values: 21 landmarks × 2 coordinates)
            if (floatLandmarks.size != 42) {
                Log.w(TAG, "Invalid landmark data size: ${floatLandmarks.size}, expected 42")
                return "?"
            }
            
            tfLiteModelHelper?.getPrediction(listOf(floatLandmarks)) ?: "?"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting predicted letter: ${e.message}")
            "?"
        }
    }

    // Convert the ImageProxy to MP Image and feed it to HandlandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // Convert ImageProxy to Bitmap using a more robust method
        val bitmapBuffer = convertImageProxyToBitmap(imageProxy)
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    // Run hand hand landmark using MediaPipe Hand Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        handLandmarker?.detectAsync(mpImage, frameTime)
        // As we're using running mode LIVE_STREAM, the landmark result will
        // be returned in returnLivestreamResult function
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // hand landmarker inference on the video. This process will evaluate every
    // frame in the video and attach the results to a bundle that will be
    // returned.
    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        // Load frames from the video and run the hand landmarker.
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()

        // Note: We need to read width/height from frame instead of getting the width/height
        // of the video directly because MediaRetriever returns frames that are smaller than the
        // actual dimension of the video file.
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        // If the video is invalid, returns a null detection result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run detection on these frames.
        val resultList = mutableListOf<HandLandmarkerResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever
                .getFrameAtTime(
                    timestampMs * 1000, // convert from ms to micro-s
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                ?.let { frame ->
                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                    val argb8888Frame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                    // Convert the input Bitmap object to an MPImage object to run inference
                    val mpImage = BitmapImageBuilder(argb8888Frame).build()

                    // Run hand landmarker using MediaPipe Hand Landmarker API
                    handLandmarker?.detectForVideo(mpImage, timestampMs)
                        ?.let { detectionResult ->
                            resultList.add(detectionResult)
                        } ?: run{
                            didErrorOccurred = true
                            handLandmarkerHelperListener?.onError(
                                "ResultBundle could not be returned" +
                                        " in detectVideoFile"
                            )
                        }
                }
                ?: run {
                    didErrorOccurred = true
                    handLandmarkerHelperListener?.onError(
                        "Frame at specified time could not be" +
                                " retrieved when detecting in video."
                    )
                }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            // For video processing, we'll use the first result for TFLite processing
            // since we can't process all frames individually in this context
            val tfLiteResult = if (resultList.isNotEmpty()) {
                processLandmarksWithTFLite(resultList.first().landmarks())
            } else -1f
            val predictedLetter = if (resultList.isNotEmpty()) {
                getPredictedLetter(resultList.first().landmarks())
            } else "?"
            
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width, tfLiteResult, predictedLetter)
        }
    }

    // Accepted a Bitmap and runs hand landmarker inference on it to return
    // results back to the caller
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" +
                        " while not using RunningMode.IMAGE"
            )
        }


        // Inference time is the difference between the system time at the
        // start and finish of the process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run hand landmarker using MediaPipe Hand Landmarker API
        handLandmarker?.detect(mpImage)?.also { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            
            // Process landmarks with TFLite model for image detection
            val tfLiteResult = processLandmarksWithTFLite(landmarkResult.landmarks())
            val predictedLetter = getPredictedLetter(landmarkResult.landmarks())
            
            return ResultBundle(
                listOf(landmarkResult),
                inferenceTimeMs,
                image.height,
                image.width,
                tfLiteResult,
                predictedLetter
            )
        }

        // If handLandmarker?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        handLandmarkerHelperListener?.onError(
            "Hand Landmarker failed to detect."
        )
        return null
    }

    // Return the landmark result to this HandLandmarkerHelper's caller
    private fun returnLivestreamResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        // Process landmarks with TFLite model
        val tfLiteResult = processLandmarksWithTFLite(result.landmarks())
        val predictedLetter = getPredictedLetter(result.landmarks())

        handLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width,
                tfLiteResult,
                predictedLetter
            )
        )
    }

    // Return errors thrown during detection to this HandLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        handLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "ml/hand_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_HANDS = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val tfLiteResult: Float = -1f,
        val predictedLetter: String = "?"
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
