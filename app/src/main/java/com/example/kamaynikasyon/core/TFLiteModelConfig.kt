package com.example.kamaynikasyon.core

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.charset.Charset

/**
 * Interface for configuring TFLite models in different fragments/activities
 */
interface TFLiteModelConfig {
    /**
     * Get the model path (can be relative to assets, absolute path, or external storage path)
     */
    fun getModelPath(): String
    
    /**
     * Get the model name for display
     */
    fun getModelName(): String
    
    /**
     * Get the number of input parameters the model expects
     */
    fun getInputSize(): Int
    
    /**
     * Get the number of output classes the model provides
     */
    fun getOutputSize(): Int
    
    /**
     * Convert model output to human-readable prediction
     * @param confidence The confidence score from the model
     * @param outputArray The raw output array from the model
     * @return Human-readable prediction string
     */
    fun getPrediction(confidence: Float, outputArray: FloatArray): String
    
    /**
     * Check if the prediction is confident enough to display
     * @param confidence The confidence score from the model
     * @return True if prediction should be displayed
     */
    fun isConfident(confidence: Float): Boolean
}

/**
 * Dynamic model configuration that loads from a JSON mapping file
 * Fully JSON-driven - no hardcoded mappings
 */
class DynamicModelConfig(
    context: Context,
    private val mappingPath: String
) : TFLiteModelConfig {
    private val mappingData: ModelConfigLoader.ModelMappingData?
    
    init {
        mappingData = ModelConfigLoader.loadFromJson(context, mappingPath)
        if (mappingData == null) {
            Log.e("DynamicModelConfig", "Failed to load mapping from $mappingPath - JSON file is required")
        }
    }
    
    override fun getModelPath(): String = mappingData?.modelPath ?: "ml/alphabet.tflite"
    
    override fun getModelName(): String = mappingData?.modelName ?: "Unknown Model"
    
    override fun getInputSize(): Int = mappingData?.inputSize ?: 42
    
    override fun getOutputSize(): Int = mappingData?.outputSize ?: 26
    
    override fun getPrediction(confidence: Float, outputArray: FloatArray): String {
        val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0
        val maxConfidence = outputArray[maxIndex]
        
        // Fully dynamic - only use JSON mapping, no hardcoded fallback
        val mappedValue = mappingData?.mapping?.get(maxIndex) ?: "?"
        
        return if (mappingData?.includeConfidence == true) {
            "$mappedValue (${String.format("%.2f", maxConfidence)})"
        } else {
            mappedValue
        }
    }
    
    override fun isConfident(confidence: Float): Boolean {
        val threshold = mappingData?.confidenceThreshold ?: 0.5f
        return confidence > threshold
    }
}

/**
 * Factory for creating model configs dynamically from JSON references
 * Fully dynamic - only supports JSON paths, no hardcoded class names
 */
object ModelConfigFactory {
    /**
     * Create a model config from a JSON object that specifies the model configuration
     * @param context Context to load assets
     * @param configJson JSON object containing model configuration
     * Supported formats:
     * 1. String: mapping path (e.g., "ml/greetings_mapping.json")
     * 2. JSONObject: { "mappingPath": "ml/greetings_mapping.json", ... }
     * 3. Map: { "mappingPath": "ml/greetings_mapping.json", ... }
     */
    fun createFromJson(
        context: Context,
        configJson: Any?
    ): TFLiteModelConfig {
        if (configJson == null) {
            // Default to alphabet mapping if nothing specified
            return DynamicModelConfig(context, "ml/alphabet_mapping.json")
        }
        
        return when (configJson) {
            is String -> {
                // Must be a JSON file path - fully dynamic, no class name support
                if (configJson.endsWith(".json")) {
                    DynamicModelConfig(context, configJson)
                } else {
                    // Not a valid path, default to alphabet
                    Log.w("ModelConfigFactory", "Invalid config string: $configJson, defaulting to alphabet_mapping.json")
                    DynamicModelConfig(context, "ml/alphabet_mapping.json")
                }
            }
            is JSONObject -> {
                // JSON object with model config
                val mappingPath = configJson.optString("mappingPath")
                    .takeIf { it.isNotBlank() }
                    ?: configJson.optString("mapping")
                        .takeIf { it.isNotBlank() }
                    ?: "ml/alphabet_mapping.json"
                
                DynamicModelConfig(context, mappingPath)
            }
            is Map<*, *> -> {
                // Map (from Gson parsing)
                val mappingPath = (configJson["mappingPath"] as? String)
                    ?: (configJson["mapping"] as? String)
                    ?: "ml/alphabet_mapping.json"
                
                DynamicModelConfig(context, mappingPath)
            }
            else -> {
                Log.w("ModelConfigFactory", "Unknown config type: ${configJson.javaClass}, defaulting to alphabet_mapping.json")
                DynamicModelConfig(context, "ml/alphabet_mapping.json")
            }
        }
    }
    
    /**
     * Create model config from a simple mapping path string
     */
    fun createFromMappingPath(context: Context, mappingPath: String): TFLiteModelConfig {
        return DynamicModelConfig(context, mappingPath)
    }
}

/**
 * Helper object for loading JSON model configurations from assets or external storage
 * Supports both bundled assets and downloaded files
 */
object ModelConfigLoader {
    private val cache = mutableMapOf<String, ModelMappingData?>()
    
    data class ModelMappingData(
        val modelPath: String,
        val modelName: String,
        val inputSize: Int,
        val outputSize: Int,
        val confidenceThreshold: Float,
        val mapping: Map<Int, String>,
        val includeConfidence: Boolean
    )
    
    /**
     * Load JSON configuration from assets or external storage
     * Supports:
     * - Relative paths (assets): "ml/greetings_mapping.json"
     * - Absolute paths: "/storage/emulated/0/Download/models/greetings_mapping.json"
     * - External files directory: relative to getExternalFilesDir
     */
    fun loadFromJson(context: Context, jsonPath: String): ModelMappingData? {
        // Check cache first
        cache[jsonPath]?.let { return it }
        
        return try {
            val jsonString = loadJsonString(context, jsonPath)
                ?: return null.also { Log.e("ModelConfigLoader", "Failed to load JSON from $jsonPath") }
            
            val json = JSONObject(jsonString)
            
            // Parse mapping object
            val mappingObj = json.getJSONObject("mapping")
            val mapping = mutableMapOf<Int, String>()
            val keys = mappingObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                mapping[key.toInt()] = mappingObj.getString(key)
            }
            
            // Get model path - can be absolute or relative
            val modelPath = json.optString("modelPath", "ml/alphabet.tflite")
            
            val data = ModelMappingData(
                modelPath = modelPath,
                modelName = json.optString("modelName", "Unknown Model"),
                inputSize = json.optInt("inputSize", 42),
                outputSize = json.optInt("outputSize", 26),
                confidenceThreshold = json.optDouble("confidenceThreshold", 0.5).toFloat(),
                mapping = mapping,
                includeConfidence = json.optBoolean("includeConfidence", false)
            )
            
            cache[jsonPath] = data
            data
        } catch (e: Exception) {
            Log.e("ModelConfigLoader", "Error parsing JSON from $jsonPath: ${e.message}")
            null
        }
    }
    
    /**
     * Load JSON string from assets or external storage
     */
    private fun loadJsonString(context: Context, jsonPath: String): String? {
        return try {
            // Try absolute path first (for downloaded files)
            if (jsonPath.startsWith("/")) {
                val file = java.io.File(jsonPath)
                if (file.exists() && file.isFile) {
                    Log.d("ModelConfigLoader", "Loading JSON from absolute path: $jsonPath")
                    return file.readText(Charset.forName("UTF-8"))
                }
            }
            
            // Try external files directory (for downloaded content)
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val externalFile = java.io.File(externalFilesDir, jsonPath)
                if (externalFile.exists() && externalFile.isFile) {
                    Log.d("ModelConfigLoader", "Loading JSON from external files: ${externalFile.absolutePath}")
                    return externalFile.readText(Charset.forName("UTF-8"))
                }
            }
            
            // Try assets (bundled content)
            try {
                Log.d("ModelConfigLoader", "Loading JSON from assets: $jsonPath")
                return context.assets.open(jsonPath).use {
                    String(it.readBytes(), Charset.forName("UTF-8"))
                }
            } catch (e: Exception) {
                Log.w("ModelConfigLoader", "JSON not found in assets: $jsonPath")
            }
            
            null
        } catch (e: Exception) {
            Log.e("ModelConfigLoader", "Error loading JSON string from $jsonPath: ${e.message}")
            null
        }
    }
    
    fun clearCache() {
        cache.clear()
    }
}

// Removed all hardcoded config classes - use DynamicModelConfig only
