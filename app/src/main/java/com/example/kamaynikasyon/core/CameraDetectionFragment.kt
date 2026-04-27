package com.example.kamaynikasyon.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.ModelConfigFactory
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Flexible camera fragment for sign language detection
 * Can be embedded in different layouts and sizes
 */
class CameraDetectionFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener {

    private var previewView: PreviewView? = null
    private var overlayView: OverlayView? = null
    private var permissionOverlayText: android.widget.TextView? = null
    private var permissionOverlayContainer: FrameLayout? = null
    private var permissionRequestButton: android.widget.Button? = null
    private var cameraContainer: FrameLayout? = null
    private var predictionTextView: android.widget.TextView? = null
    private var statusTextView: android.widget.TextView? = null
    private var verificationProgressBar: android.widget.ProgressBar? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var handLandmarkerHelper: HandLandmarkerHelper? = null

    private lateinit var cameraExecutor: ExecutorService
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    // Configuration for different test scenarios
    private var modelConfig: TFLiteModelConfig? = null
    private var detectionEnabled = true

    // Camera preference values (defaults align with SettingsActivity)
    private var detectionThreshold = DEFAULT_DETECTION_THRESHOLD
    private var trackingThreshold = DEFAULT_TRACKING_THRESHOLD
    private var maxHands = DEFAULT_MAX_HANDS

    companion object {
        private const val TAG = "CameraDetectionFragment"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private const val CAMERA_PREFS = "camera_settings"
        private const val KEY_DETECTION_THRESHOLD = "detection_threshold"
        private const val KEY_TRACKING_THRESHOLD = "tracking_threshold"
        private const val KEY_MAX_HANDS = "max_hands"

        private const val DEFAULT_DETECTION_THRESHOLD = 0.5f
        private const val DEFAULT_TRACKING_THRESHOLD = 0.5f
        private const val DEFAULT_MAX_HANDS = 1

        fun newInstance(modelConfig: TFLiteModelConfig? = null): CameraDetectionFragment {
            val fragment = CameraDetectionFragment()
            // Config will be initialized with context in setupHandLandmarker
            fragment.modelConfig = modelConfig
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera_detection, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find views
        previewView = view.findViewById(R.id.preview_view)
        overlayView = view.findViewById(R.id.overlay_view)
        cameraContainer = view.findViewById(R.id.camera_container)
        predictionTextView = view.findViewById(R.id.prediction_text)
        statusTextView = view.findViewById(R.id.status_text)
        verificationProgressBar = view.findViewById(R.id.progress_verification)
        permissionOverlayText = view.findViewById(R.id.permission_overlay_text)
        permissionOverlayContainer = view.findViewById(R.id.permission_overlay_container)
        permissionRequestButton = view.findViewById(R.id.permission_request_button)

        permissionRequestButton?.setOnClickListener {
            try {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", requireContext().packageName, null)
                    )
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling permission request", e)
            }
        }

        // Check permissions and start camera
        updatePermissionOverlay()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupHandLandmarker()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove view tree observer
        previewView?.let { pv ->
            previewViewLayoutListener?.let { listener ->
                try {
                    pv.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                } catch (e: Exception) {
                    // ViewTreeObserver may already be dead, ignore
                }
            }
        }
        previewViewLayoutListener = null
        
        cameraExecutor.shutdown()
        handLandmarkerHelper?.clearHandLandmarker()
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView?.surfaceProvider)
        }
        
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()
        
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (detectionEnabled) {
                        detectHand(imageProxy)
                    }
                }
            }
        
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Mark camera as started so we don't repeatedly try to start it
            cameraStarted = true
            
            // Setup overlay alignment after camera is bound
            previewView?.post {
                ensureOverlayAlignment()
                // Add view tree observer to update overlay when preview view is resized
                setupPreviewViewObserver()
            }
            
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    // Track if camera use cases were bound successfully
    private var cameraStarted = false

    private fun updatePermissionOverlay() {
        try {
            if (allPermissionsGranted()) {
                permissionOverlayContainer?.visibility = View.GONE
                permissionOverlayText?.visibility = View.GONE
                permissionRequestButton?.visibility = View.GONE
            } else {
                permissionOverlayContainer?.visibility = View.VISIBLE
                permissionOverlayText?.visibility = View.VISIBLE
                permissionRequestButton?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating permission overlay", e)
        }
    }
    
    private var previewViewLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    private fun setupPreviewViewObserver() {
        try {
            previewView?.let { pv ->
                // Remove existing listener if any
                previewViewLayoutListener?.let { listener ->
                    pv.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
                
                val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        val previewWidth = pv.width
                        val previewHeight = pv.height
                        
                        if (previewWidth > 0 && previewHeight > 0) {
                            // Update overlay dimensions whenever preview view is resized
                            overlayView?.setPreviewViewDimensions(previewWidth, previewHeight)
                            ensureOverlayAlignment()
                        }
                    }
                }
                
                previewViewLayoutListener = listener
                pv.viewTreeObserver.addOnGlobalLayoutListener(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up preview view observer", e)
        }
    }

    private fun ensureOverlayAlignment() {
        try {
            // Ensure overlay has the same dimensions and position as the camera preview
            val previewWidth = previewView?.width ?: 0
            val previewHeight = previewView?.height ?: 0
            
            if (previewWidth > 0 && previewHeight > 0) {
                val layoutParams = FrameLayout.LayoutParams(
                    previewWidth,
                    previewHeight
                )
                layoutParams.leftMargin = 0
                layoutParams.topMargin = 0
                
                overlayView?.layoutParams = layoutParams
                // Set preview view dimensions explicitly in overlay to ensure correct coordinate transformation
                overlayView?.setPreviewViewDimensions(previewWidth, previewHeight)
                overlayView?.requestLayout()
                Log.d(TAG, "Overlay aligned: ${previewWidth}x${previewHeight}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring overlay alignment", e)
        }
    }
    
    private fun detectHand(imageProxy: ImageProxy) {
        try {
            Log.d(TAG, "Detecting hand - image size: ${imageProxy.width}x${imageProxy.height}")
            handLandmarkerHelper?.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting hand", e)
        }
    }
    
    private fun setupHandLandmarker() {
        try {
            loadCameraPreferences(requireContext())

            // Use provided config or default to alphabet mapping
            val configToUse = modelConfig ?: ModelConfigFactory.createFromMappingPath(
                requireContext(),
                "ml/alphabet_mapping.json"
            )
            
            modelConfig = configToUse
            
            Log.d(TAG, "Setting up HandLandmarker with model: ${configToUse.getModelName()}")
            handLandmarkerHelper = HandLandmarkerHelper(
                minHandDetectionConfidence = detectionThreshold,
                minHandTrackingConfidence = trackingThreshold,
                minHandPresenceConfidence = detectionThreshold,
                maxNumHands = maxHands,
                currentDelegate = HandLandmarkerHelper.DELEGATE_CPU,
                runningMode = RunningMode.LIVE_STREAM,
                context = requireContext(),
                handLandmarkerHelperListener = this,
                customModelConfig = configToUse
            )
            Log.d(TAG, "HandLandmarker setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up HandLandmarker", e)
        }
    }

    private fun loadCameraPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences(CAMERA_PREFS, Context.MODE_PRIVATE)
            detectionThreshold = prefs
                .getFloat(KEY_DETECTION_THRESHOLD, DEFAULT_DETECTION_THRESHOLD)
                .coerceIn(0.1f, 0.9f)
            trackingThreshold = prefs
                .getFloat(KEY_TRACKING_THRESHOLD, DEFAULT_TRACKING_THRESHOLD)
                .coerceIn(0.1f, 0.9f)
            maxHands = prefs
                .getInt(KEY_MAX_HANDS, DEFAULT_MAX_HANDS)
                .coerceIn(1, 2)
            Log.d(TAG, "Camera prefs loaded: detection=$detectionThreshold tracking=$trackingThreshold maxHands=$maxHands")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load camera preferences", e)
            detectionThreshold = DEFAULT_DETECTION_THRESHOLD
            trackingThreshold = DEFAULT_TRACKING_THRESHOLD
            maxHands = DEFAULT_MAX_HANDS
        }
    }
    
    // Public methods for external control
    fun setDetectionEnabled(enabled: Boolean) {
        detectionEnabled = enabled
        if (!enabled) {
            overlayView?.clear()
        }
        // Optionally, rebind camera use cases to restart detection if needed
        if (enabled) {
            bindCameraUseCases()
        }
    }
    
    fun switchCamera() {
        cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        bindCameraUseCases()
    }
    
    fun setModelConfig(config: TFLiteModelConfig) {
        modelConfig = config
        setupHandLandmarker()
    }

    // Allow external listeners (e.g. hosting fragments) to receive raw detection bundles
    private var onDetectionListener: ((HandLandmarkerHelper.ResultBundle) -> Unit)? = null

    fun setOnDetectionListener(listener: (HandLandmarkerHelper.ResultBundle) -> Unit) {
        onDetectionListener = listener
    }

    // Expose overlay UI elements for activities to update
    fun getPredictionTextView(): android.widget.TextView? = predictionTextView
    fun getStatusTextView(): android.widget.TextView? = statusTextView
    fun getConfidenceTextView(): android.widget.TextView? = statusTextView // Backward compatibility
    fun getVerificationProgressBar(): android.widget.ProgressBar? = verificationProgressBar
    
    // Convenience methods to update overlay UI from activities
    fun updatePredictionText(text: String) {
        activity?.runOnUiThread {
            predictionTextView?.text = text
        }
    }
    
    fun updateStatus(text: String) {
        activity?.runOnUiThread {
            statusTextView?.text = text
        }
    }
    
    fun updatePredictionWithConfidence(letter: String, confidence: Int) {
        activity?.runOnUiThread {
            val text = "Predicting: $letter ($confidence%)"
            predictionTextView?.text = text
        }
    }
    
    fun updateConfidenceText(text: String) {
        // Backward compatibility: update status text instead
        activity?.runOnUiThread {
            statusTextView?.text = text
        }
    }
    
    fun showVerificationProgress(show: Boolean) {
        activity?.runOnUiThread {
            verificationProgressBar?.visibility = if (show) View.VISIBLE else View.INVISIBLE
        }
    }
    
    fun setVerificationProgress(progress: Int) {
        activity?.runOnUiThread {
            verificationProgressBar?.progress = progress.coerceIn(0, 100)
        }
    }
    
    // HandLandmarkerHelper.LandmarkerListener implementation
    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        try {
            Log.d(TAG, "onResults called - results count: ${resultBundle.results.size}")
            activity?.runOnUiThread {
                try {
                    val previewWidth = previewView?.width ?: 0
                    val previewHeight = previewView?.height ?: 0
                    Log.d(TAG, "Preview dimensions: ${previewWidth}x${previewHeight}")

                    if (!detectionEnabled) {
                        // If detection is disabled, clear overlay and skip drawing
                        overlayView?.clear()
                        predictionTextView?.text = ""
                        Log.d(TAG, "Detection disabled, overlay cleared")
                        return@runOnUiThread
                    }

                    if (previewWidth > 0 && previewHeight > 0) {
                        // Ensure overlay has the correct preview view dimensions before setting results
                        overlayView?.setPreviewViewDimensions(previewWidth, previewHeight)
                        
                        // Offer raw result bundle to any external listener before UI update
                        try {
                            onDetectionListener?.invoke(resultBundle)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error invoking external detection listener", e)
                        }

                        if (resultBundle.results.isNotEmpty()) {
                            Log.d(TAG, "Setting results on overlay view")
                            overlayView?.setResults(
                                resultBundle.results.first(),
                                resultBundle.inputImageHeight,
                                resultBundle.inputImageWidth,
                                RunningMode.LIVE_STREAM
                            )
                        } else {
                            Log.d(TAG, "No hand landmarks detected")
                        }

                        val tfLiteResult = resultBundle.tfLiteResult
                        val predictedLetter = resultBundle.predictedLetter
                        Log.d(TAG, "Prediction: $predictedLetter, Confidence: $tfLiteResult")
                        if (predictedLetter != "?" && tfLiteResult > 0.5f) {
                            Log.d(TAG, "High confidence prediction: $predictedLetter (${(tfLiteResult * 100).toInt()}%)")
                            predictionTextView?.text = predictedLetter
                        } else {
                            predictionTextView?.text = ""
                        }

                        overlayView?.let { ov ->
                            ov.post {
                                try {
                                    ov.bringToFront()
                                    ov.invalidate()
                                    ov.requestLayout()
                                    Log.d(TAG, "Overlay brought to front and invalidated")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error refreshing overlay", e)
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "Preview dimensions are 0, skipping overlay update")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing results", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResults", e)
        }
    }
    
    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Log.e(TAG, "HandLandmarker error: $error (code: $errorCode)")
            view?.let {
                com.example.kamaynikasyon.core.utils.ErrorHandler.showError(
                    it,
                    com.example.kamaynikasyon.R.string.error_detection
                )
            } ?: run {
                Toast.makeText(requireContext(), getString(com.example.kamaynikasyon.R.string.error_detection), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updatePermissionOverlay()
            if (granted) {
                if (!cameraStarted) {
                    startCamera()
                }
            } else {
                view?.let {
                    com.example.kamaynikasyon.core.utils.ErrorHandler.showError(
                        it,
                        com.example.kamaynikasyon.R.string.error_camera_permission
                    )
                } ?: run {
                    Toast.makeText(requireContext(), getString(com.example.kamaynikasyon.R.string.error_camera_permission), Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onResume() {
        super.onResume()
        // If user granted permission externally (e.g., via Settings), ensure the overlay state
        updatePermissionOverlay()
        if (allPermissionsGranted() && !cameraStarted) {
            startCamera()
        }
    }
}
