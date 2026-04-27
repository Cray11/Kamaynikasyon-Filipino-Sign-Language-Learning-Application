package com.example.kamaynikasyon.features.lessons.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.os.BundleCompat
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.CameraDetectionFragment
import com.example.kamaynikasyon.core.ModelConfigFactory
import com.example.kamaynikasyon.core.TFLiteModelConfig
import com.example.kamaynikasyon.core.particles.ParticleEffects
import com.example.kamaynikasyon.core.particles.ParticleSystem
import com.example.kamaynikasyon.core.utils.showError
import com.example.kamaynikasyon.databinding.FragmentLessonCameraBinding
import com.example.kamaynikasyon.features.lessons.data.models.Lesson
import com.example.kamaynikasyon.features.lessons.data.models.LessonPage
import android.graphics.Color


class LessonCameraFragment : Fragment() {
    
    private var _binding: FragmentLessonCameraBinding? = null
    private val binding get() = _binding!!
    
    private var lesson: Lesson? = null
    private var page: LessonPage? = null
    // CameraDetectionFragment will handle camera and detection
    private var cameraFragment: CameraDetectionFragment? = null
    
    private var onDetectionListener: ((Boolean) -> Unit)? = null
    private var isDetectionComplete = false
    
    // Gesture verification variables
    private var isVerifying = false
    private var verificationStartTime = 0L
    private var verificationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var verificationRunnable: Runnable? = null
    private val VERIFICATION_DURATION = 2000L // 2 seconds
    
    companion object {
        private const val TAG = "LessonCameraFragment"
        private const val ARG_LESSON = "lesson"
        private const val ARG_PAGE = "page"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        
        fun newInstance(lesson: Lesson, page: LessonPage): LessonCameraFragment {
            val fragment = LessonCameraFragment()
            val args = Bundle()
            args.putParcelable(ARG_LESSON, lesson)
            args.putParcelable(ARG_PAGE, page)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLessonCameraBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Get lesson and page data
            lesson = arguments?.let { BundleCompat.getParcelable(it, ARG_LESSON, Lesson::class.java) }
            page = arguments?.let { BundleCompat.getParcelable(it, ARG_PAGE, LessonPage::class.java) }

            if (lesson == null || page == null) {
                Log.e(TAG, "Lesson or page data is missing")
                showError(R.string.error_missing_lesson_data)
                return
            }

            // Update UI with lesson-specific information
            updateLessonUI()

            // Embed the shared CameraDetectionFragment (used by camera test activities)
            // Load model config dynamically from JSON (supports string paths, JSON objects, or legacy class names)
            val modelConfig: TFLiteModelConfig = ModelConfigFactory.createFromJson(
                requireContext(),
                page?.modelConfig ?: lesson?.modelConfig
            )

            cameraFragment = CameraDetectionFragment.newInstance(modelConfig)
            // Listen to raw detection bundles so the lesson fragment can decide correctness
            cameraFragment?.setOnDetectionListener { resultBundle ->
                activity?.runOnUiThread {
                    if (!isDetectionComplete) {
                        try {
                            val predictedLetter = resultBundle.predictedLetter
                            val expectedSign = page?.expectedSign
                            val confidence = resultBundle.tfLiteResult ?: 0f
                            val confidencePercentage = (confidence * 100).toInt()

                            // Update prediction and confidence in overlay with format "Predicting: A (100%)"
                            if (predictedLetter != null && predictedLetter.isNotBlank()) {
                                cameraFragment?.updatePredictionWithConfidence(predictedLetter, confidencePercentage)
                            } else {
                                cameraFragment?.updatePredictionText("Predicting: -")
                            }

                            if (predictedLetter == expectedSign) {
                                displayDetectionResult(true, predictedLetter)
                            } else {
                                displayDetectionResult(false, predictedLetter)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing detection bundle", e)
                        }
                    }
                }
            }

            // Place the detection fragment into the camera container
            childFragmentManager.beginTransaction()
                .replace(binding.cameraContainer.id, cameraFragment!!)
                .commit()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            showError(R.string.error_camera_setup)
        }
    }
    

    
    private fun updateLessonUI() {
        page?.let { currentPage ->
            binding.tvTitle.text = currentPage.title
            binding.tvContent.text = currentPage.content
            binding.tvInstructions.text = "Try signing: ${currentPage.expectedSign ?: "the expected sign"}"
        }
    }
    
    
    
    private fun displayDetectionResult(isCorrect: Boolean, detectedSign: String) {
        if (isCorrect) {
            cameraFragment?.updateStatus("Status: Verifying")
            
            // Start verification process
            if (!isVerifying) {
                startGestureVerification()
            }
        } else {
            // Stop verification if wrong gesture detected
            stopGestureVerification()
            
            cameraFragment?.updateStatus("Status: Keep Trying ❌")
        }
    }
    
    private fun startGestureVerification() {
        if (isVerifying) return
        
        isVerifying = true
        verificationStartTime = System.currentTimeMillis()
        
        // Show loading bar in camera overlay
        cameraFragment?.showVerificationProgress(true)
        cameraFragment?.setVerificationProgress(0)
        
        // Start smooth progress animation
        startProgressAnimation()
    }
    
    private fun stopGestureVerification() {
        if (!isVerifying) return
        
        isVerifying = false
        verificationRunnable?.let { verificationHandler.removeCallbacks(it) }
        
        // Hide loading bar
        cameraFragment?.showVerificationProgress(false)
        cameraFragment?.setVerificationProgress(0)
    }
    
    private fun startProgressAnimation() {
        verificationRunnable = object : Runnable {
            override fun run() {
                if (!isVerifying) return
                
                val elapsed = System.currentTimeMillis() - verificationStartTime
                val progress = ((elapsed.toFloat() / VERIFICATION_DURATION) * 100).coerceIn(0f, 100f)
                
                cameraFragment?.setVerificationProgress(progress.toInt())
                
                if (elapsed >= VERIFICATION_DURATION) {
                    // Verification complete
                    completeGestureVerification()
                } else {
                    // Continue animation
                    verificationHandler.postDelayed(this, 16) // ~60fps
                }
            }
        }
        verificationHandler.post(verificationRunnable!!)
    }
    
    private fun completeGestureVerification() {
        isVerifying = false
        isDetectionComplete = true
        
        // Update UI with success message
        cameraFragment?.updateStatus("Status: Good Job ✓")
        cameraFragment?.setVerificationProgress(100)
        
        // Show "Good Job" particle effect
        binding.root.post {
            val centerX = binding.root.width / 2f
            val centerY = binding.root.height * 0.85f // Bottom middle of screen
            activity?.let { act ->
                ParticleSystem.playEffect(
                    act,
                    ParticleEffects.textPopUp(
                        text = "Good Job",
                        x = centerX,
                        y = centerY,
                        textSize = 48f,
                        color = Color.GREEN
                    )
                )
            }
        }
        
        // Hide loading bar after a short delay
        verificationHandler.postDelayed({
            cameraFragment?.showVerificationProgress(false)
        }, 500)
        
        // Notify parent activity
        onDetectionListener?.invoke(true)
    }
    
    fun setOnDetectionListener(listener: (Boolean) -> Unit) {
        onDetectionListener = listener
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        stopGestureVerification()
        _binding = null
    }
}
