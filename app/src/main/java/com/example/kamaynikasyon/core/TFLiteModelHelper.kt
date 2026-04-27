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
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TFLiteModelHelper(
    private val context: Context,
    private val modelConfig: TFLiteModelConfig,
    private val useGPU: Boolean = false
) {
    private var interpreter: Interpreter? = null
    private val inputShape = intArrayOf(1, modelConfig.getInputSize())
    private val outputShape = intArrayOf(1, modelConfig.getOutputSize())

    init {
        loadModel()
    }

    private fun loadModel() {
        val modelPath = modelConfig.getModelPath()
        
        // Try loading from multiple locations in order:
        // 1. Absolute path (downloaded files)
        // 2. External files directory (downloaded content)
        // 3. Assets (bundled content)
        
        try {
            var modelFile: File? = null
            
            // Try absolute path first (for downloaded files like /storage/emulated/0/Download/models/greetings.tflite)
            if (modelPath.startsWith("/")) {
                val absoluteFile = File(modelPath)
                if (absoluteFile.exists() && absoluteFile.isFile) {
                    modelFile = absoluteFile
                    Log.d(TAG, "Model found at absolute path: ${modelFile.absolutePath}")
                }
            }
            
            // Try external files directory (for downloaded content)
            if (modelFile == null) {
                val externalFilesDir = context.getExternalFilesDir(null)
                if (externalFilesDir != null) {
                    val externalFile = File(externalFilesDir, modelPath)
                    if (externalFile.exists() && externalFile.isFile) {
                        modelFile = externalFile
                        Log.d(TAG, "Model found in external files: ${modelFile.absolutePath}")
                    }
                }
            }
            
            // Try copying from assets if not found in external storage
            if (modelFile == null || !modelFile.exists()) {
                val externalFilesDir = context.getExternalFilesDir(null)
                if (externalFilesDir != null) {
                    modelFile = File(externalFilesDir, modelPath)
                    if (!modelFile.exists()) {
                        // Copy from assets if available
                        copyModelFromAssets(modelPath)
                    }
                }
            }

            val options = Interpreter.Options().apply {
                if (useGPU) {
                    // Enable GPU delegation if needed
                    // setUseNNAPI(true)
                }
                setNumThreads(4)
            }

            // Load from file if available
            if (modelFile != null && modelFile.exists()) {
                interpreter = Interpreter(modelFile, options)
                Log.d(TAG, "TFLite model loaded successfully from file: ${modelFile.absolutePath}")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading model from file: ${e.message}")
        }
        
        // Fallback: Try loading directly from assets
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "TFLite model loaded successfully from assets: $modelPath")
        } catch (e2: Exception) {
            Log.e(TAG, "Error loading TFLite model from assets: ${e2.message}")
            Log.e(TAG, "Failed to load model from any location: $modelPath")
        }
    }

    private fun copyModelFromAssets(assetPath: String) {
        try {
            val inputStream = context.assets.open(assetPath)
            val outputFile = File(context.getExternalFilesDir(null), assetPath)
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            Log.d(TAG, "Model copied from assets to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying model from assets: ${e.message}")
        }
    }

    /**
     * Process hand landmarks from MediaPipe
     * @param landmarks List of landmarks from MediaPipe (21 landmarks per hand)
     * @return Prediction result from your TFLite model
     */
    fun processLandmarks(landmarks: List<List<Float>>): Float {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null")
            return -1f
        }

        try {
            // Flatten landmarks to 42 parameters (21 landmarks per hand)
            val inputArray = FloatArray(42)
            var index = 0

            // Process up to 2 hands (42 total parameters)
            for (handIndex in 0 until minOf(2, landmarks.size)) {
                val handLandmarks = landmarks[handIndex]
                for (landmarkIndex in 0 until minOf(21, handLandmarks.size)) {
                    val landmark = handLandmarks[landmarkIndex]
                    // Extract x, y coordinates (assuming each landmark has x, y, z)
                    // Adjust this based on your model's expected input format
                    if (index < 42) {
                        inputArray[index++] = landmark // x coordinate
                    }
                    if (index < 42) {
                        inputArray[index++] = landmark // y coordinate
                    }
                }
            }

            // Pad with zeros if less than 42 parameters
            while (index < 42) {
                inputArray[index++] = 0f
            }

            // Create input tensor
            val inputBuffer = TensorBuffer.createFixedSize(inputShape, org.tensorflow.lite.DataType.FLOAT32)
            inputBuffer.loadArray(inputArray)

            // Create output tensor
            val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)

            // Run inference
            interpreter?.run(inputBuffer.buffer, outputBuffer.buffer)

            // Get result
            val result = outputBuffer.floatArray[0]
            Log.d(TAG, "TFLite inference result: $result")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Error during TFLite inference: ${e.message}")
            return -1f
        }
    }

    /**
     * Process landmarks with more detailed input preparation
     * @param landmarks MediaPipe landmarks
     * @return Prediction result
     */
    fun processLandmarksDetailed(landmarks: List<List<Float>>): Float {
        if (interpreter == null) return -1f

        try {
            val inputArray = FloatArray(42)
            var index = 0

            // Process landmarks in the format your model expects
            for (handIndex in 0 until minOf(2, landmarks.size)) {
                val handLandmarks = landmarks[handIndex]

                // Assuming each landmark has x, y, z coordinates
                // You might need to adjust this based on your model's input format
                for (landmarkIndex in 0 until minOf(21, handLandmarks.size)) {
                    val landmark = handLandmarks[landmarkIndex]

                    // Extract x, y coordinates (skip z if not needed)
                    // Adjust this based on your specific model requirements
                    if (index < 42) {
                        inputArray[index++] = landmark // x coordinate
                    }
                    if (index < 42) {
                        inputArray[index++] = landmark // y coordinate
                    }
                }
            }

            // Normalize or preprocess the data if needed
            // inputArray = normalizeLandmarks(inputArray)

            val inputBuffer = TensorBuffer.createFixedSize(inputShape, org.tensorflow.lite.DataType.FLOAT32)
            inputBuffer.loadArray(inputArray)

            val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)

            interpreter?.run(inputBuffer.buffer, outputBuffer.buffer)

            return outputBuffer.floatArray[0]

        } catch (e: Exception) {
            Log.e(TAG, "Error during detailed TFLite inference: ${e.message}")
            return -1f
        }
    }

    /**
     * Process landmarks and get the full output array
     * @param landmarks List of landmarks from MediaPipe
     * @return Full output array from TFLite model
     */
    fun processLandmarksWithOutput(landmarks: List<List<Float>>): FloatArray? {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null")
            return null
        }

        try {
            // Process only the first hand (like C# implementation)
            if (landmarks.isEmpty() || landmarks[0].size < 42) {
                Log.w(TAG, "Insufficient landmarks for processing")
                return null
            }

            val handLandmarks = landmarks[0] // Take first hand only
            val inputArray = FloatArray(modelConfig.getInputSize())

            // Apply the same preprocessing as C# code:
            // 1. Convert to relative coordinates (subtract base point)
            // 2. Normalize by max value
            // 3. Flatten to x,y pairs

            // Get base point (first landmark)
            val baseX = handLandmarks[0]
            val baseY = handLandmarks[1]

            // Find max value for normalization
            var maxValue = 0f
            for (i in 0 until minOf(21, handLandmarks.size / 2)) {
                val x = handLandmarks[i * 2] - baseX
                val y = handLandmarks[i * 2 + 1] - baseY
                maxValue = maxOf(maxValue, maxOf(kotlin.math.abs(x), kotlin.math.abs(y)))
            }

            // Avoid division by zero
            if (maxValue == 0f) {
                Log.w(TAG, "Max value is zero, cannot normalize")
                return null
            }

            // Preprocess and flatten landmarks (like C# PreprocessLandmarks)
            for (i in 0 until minOf(21, handLandmarks.size / 2)) {
                val x = (handLandmarks[i * 2] - baseX) / maxValue
                val y = (handLandmarks[i * 2 + 1] - baseY) / maxValue
                
                if (i * 2 < inputArray.size) {
                    inputArray[i * 2] = x
                }
                if (i * 2 + 1 < inputArray.size) {
                    inputArray[i * 2 + 1] = y
                }
            }
            
            // Debug logging (like C# implementation)
            Log.d(TAG, "Preprocessed landmarks - Base: ($baseX, $baseY), MaxValue: $maxValue")
            Log.d(TAG, "First few processed values: ${inputArray.take(10).joinToString(", ")}")

            val inputBuffer = TensorBuffer.createFixedSize(inputShape, org.tensorflow.lite.DataType.FLOAT32)
            inputBuffer.loadArray(inputArray)

            val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)

            interpreter?.run(inputBuffer.buffer, outputBuffer.buffer)

            val result = outputBuffer.floatArray
            Log.d(TAG, "TFLite output array: ${result.joinToString(", ")}")
            Log.d(TAG, "TFLite output size: ${result.size}")
            
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Error during TFLite inference: ${e.message}")
            return null
        }
    }

    /**
     * Get prediction using the model configuration
     * @param landmarks List of landmarks from MediaPipe
     * @return Human-readable prediction string
     */
    fun getPrediction(landmarks: List<List<Float>>): String {
        val outputArray = processLandmarksWithOutput(landmarks)
        if (outputArray != null) {
            val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0
            val confidence = outputArray[maxIndex]
            return modelConfig.getPrediction(confidence, outputArray)
        }
        return "?"
    }

    /**
     * Alternative method using ByteBuffer for better performance
     */
    fun processLandmarksWithByteBuffer(landmarks: List<List<Float>>): Float {
        if (interpreter == null) return -1f

        try {
            val inputBuffer = ByteBuffer.allocateDirect(42 * 4) // 42 floats * 4 bytes
            inputBuffer.order(ByteOrder.nativeOrder())

            var index = 0
            for (handIndex in 0 until minOf(2, landmarks.size)) {
                val handLandmarks = landmarks[handIndex]
                for (landmarkIndex in 0 until minOf(21, handLandmarks.size)) {
                    val landmark = handLandmarks[landmarkIndex]
                    if (index < 42) {
                        inputBuffer.putFloat(landmark)
                        index++
                    }
                }
            }

            // Pad with zeros
            while (index < 42) {
                inputBuffer.putFloat(0f)
                index++
            }

            inputBuffer.rewind()

            val outputBuffer = ByteBuffer.allocateDirect(4) // 1 float output
            outputBuffer.order(ByteOrder.nativeOrder())

            interpreter?.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            return outputBuffer.float

        } catch (e: Exception) {
            Log.e(TAG, "Error during ByteBuffer TFLite inference: ${e.message}")
            return -1f
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "TFLiteModelHelper"
    }
}
