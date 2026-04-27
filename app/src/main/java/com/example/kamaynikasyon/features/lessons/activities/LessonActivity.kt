package com.example.kamaynikasyon.features.lessons.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.utils.showError
import com.example.kamaynikasyon.core.utils.AnalyticsLogger
import com.example.kamaynikasyon.databinding.ActivityLessonBinding
import com.example.kamaynikasyon.features.lessons.data.models.Lesson
import com.example.kamaynikasyon.features.lessons.data.models.PageType
import com.example.kamaynikasyon.features.lessons.data.repositories.LessonRepository
import com.example.kamaynikasyon.features.lessons.fragments.LessonTextFragment
import com.example.kamaynikasyon.features.lessons.fragments.LessonQuestionFragment
import com.example.kamaynikasyon.features.lessons.fragments.LessonCameraFragment
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.data.database.LessonProgress
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.kamaynikasyon.core.particles.ParticleSystem
import com.example.kamaynikasyon.core.particles.ParticleEffects

class LessonActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLessonBinding
    private lateinit var lessonRepository: LessonRepository
    private var currentLesson: Lesson? = null
    private var currentPageIndex = 0
    private var isQuestionAnswered = false
    
    companion object {
        const val EXTRA_LESSON_ID = "lesson_id"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRepository()
        setupNavigation()
        loadLesson()
        
        // Log screen view
        AnalyticsLogger.logScreenView("Lesson", "LessonActivity")
    }
    
    private fun setupRepository() {
        lessonRepository = LessonRepository(this)
    }
    
    private fun loadLesson() {
        val lessonId = intent.getStringExtra(EXTRA_LESSON_ID)
        
        // Show loading indicator and hide content
        binding.progressLoading.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
        
        // Disable navigation buttons during loading
        binding.btnPrevious.isEnabled = false
        binding.btnNext.isEnabled = false
        binding.btnSkip.isEnabled = false
        
        lifecycleScope.launch {
            currentLesson = lessonId?.let { 
                withContext(Dispatchers.IO) {
                    lessonRepository.getLessonById(it)
                }
            }
            
            if (currentLesson == null) {
                binding.progressLoading.visibility = View.GONE
                showError(R.string.error_lesson_not_found)
                finish()
                return@launch
            }
            // set toolbar title to loaded lesson's title
            binding.toolbar.title = currentLesson?.title
            // Setup progress bar after lesson is loaded
            currentLesson?.let { lesson ->
                setupProgressBar()
            }
            
            // Hide loading indicator and show content
            binding.progressLoading.visibility = View.GONE
            binding.fragmentContainer.visibility = View.VISIBLE
            
            // Re-enable navigation buttons after loading
            binding.btnPrevious.isEnabled = currentPageIndex > 0
            binding.btnNext.isEnabled = true
            binding.btnSkip.isEnabled = true
            
            // Load the first page after lesson is loaded
            displayCurrentPage()
        }
    }
    
    private fun setupNavigation() {
        binding.toolbar.setNavigationOnClickListener {
            showExitConfirmationDialog()
        }
        
        // Info button disabled for now
        // binding.btnInfo.setOnClickListener {
        //     showLessonTutorial()
        // }
        
        binding.btnPrevious.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex--
                displayCurrentPage()
            }
        }
        
        binding.btnSkip.setOnClickListener {
            showSkipWarningDialog()
        }
        
        binding.btnNext.setOnClickListener {
            if (currentPageIndex < (currentLesson?.pages?.size ?: 0) - 1) {
                currentPageIndex++
                displayCurrentPage()
            } else {
                // Lesson completed
                launchLessonResultScreen()
            }
        }
    }
    
    private fun displayCurrentPage() {
        val lesson = currentLesson ?: return
        val page = lesson.pages.getOrNull(currentPageIndex) ?: return
        
        // Update progress bar
        updateProgressBar()
        
        // Update navigation buttons
        binding.btnPrevious.apply {
            isEnabled = currentPageIndex > 0
            visibility = if (currentPageIndex > 0) View.VISIBLE else View.GONE
        }
        
        // Load appropriate fragment based on page type
        val fragment = when (page.type) {
            PageType.TEXT -> LessonTextFragment.newInstance(page)
            PageType.CAMERA -> LessonCameraFragment.newInstance(lesson, page)
            PageType.QUESTION -> LessonQuestionFragment.newInstance(page)
        }
        
        // Set up fragment communication and button state
        if (fragment is LessonQuestionFragment) {
            // For question pages, Next button is disabled until correct answer
            isQuestionAnswered = false
            binding.btnNext.isEnabled = false
            fragment.setOnAnswerListener { answered ->
                isQuestionAnswered = answered
                binding.btnNext.isEnabled = answered
            }
        } else if (fragment is LessonCameraFragment) {
            // For camera pages, hide Next button and show skip button as alternative
            binding.btnNext.visibility = View.GONE
            binding.btnSkip.visibility = View.VISIBLE
            fragment.setOnDetectionListener { detected ->
                // Hide skip button when gesture is detected and show Next button
                if (detected) {
                    binding.btnSkip.visibility = View.GONE
                    binding.btnNext.visibility = View.VISIBLE
                    binding.btnNext.isEnabled = true
                }
            }
        } else {
            // For text pages, Next button is always enabled
            binding.btnNext.isEnabled = true
            binding.btnNext.visibility = View.VISIBLE
            binding.btnSkip.visibility = View.GONE
        }
        
        // Update Next button text
        binding.btnNext.text = if (currentPageIndex < lesson.pages.size - 1) "Next" else "Complete"
        
        // Replace fragment
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }

    private fun setupProgressBar() {
        currentLesson?.let { lesson ->
            binding.progressBar.max = 100
            updateProgressBar()
        }
    }
    
    private fun updateProgressBar() {
        currentLesson?.let { lesson ->
            val totalPages = lesson.pages.size
            if (totalPages > 0) {
                val progress = ((currentPageIndex + 1) * 100) / totalPages
                binding.progressBar.progress = progress
            }
        }
    }
    
    private fun showExitConfirmationDialog() {
        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_exit_confirmation, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.example.kamaynikasyon.R.id.btn_continue_lesson)
            .setOnClickListener {
                dialog.dismiss()
            }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.example.kamaynikasyon.R.id.btn_exit_lesson)
            .setOnClickListener {
                dialog.dismiss()
                finish()
            }
        
        dialog.show()
    }
    
    private fun showSkipWarningDialog() {
        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_skip_warning, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_keep_practicing)
            .setOnClickListener {
                dialog.dismiss()
            }
        
        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_skip_anyway)
            .setOnClickListener {
                dialog.dismiss()
                // Proceed to next page
                if (currentPageIndex < (currentLesson?.pages?.size ?: 0) - 1) {
                    currentPageIndex++
                    displayCurrentPage()
                } else {
                    // Lesson completed
                    launchLessonResultScreen()
                }
            }
        
        dialog.show()
    }
    
    // Show completion dialog instead of result activity
    private fun launchLessonResultScreen() {
        val lesson = currentLesson ?: return
        // Save completion asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            db.lessonProgressDao().upsert(LessonProgress(lessonId = lesson.id, completed = true))
            // Log lesson completion to Crashlytics
            com.example.kamaynikasyon.core.utils.CrashlyticsLogger.logLessonEvent(
                "completed",
                lesson.id,
                mapOf("title" to (lesson.title ?: "Unknown"))
            )
            // Log lesson completion to Analytics
            AnalyticsLogger.logLessonEvent(
                "completed",
                lesson.id,
                lesson.title,
                mapOf("pages_count" to lesson.pages.size)
            )
        }

        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_lesson_complete, null)
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_title).text = "Lesson Complete!"
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_lesson).text = lesson.title
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_pages).text = "Pages: ${lesson.pages.size}"

        // Medium haptic on lesson completion
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateMedium(this)

        // Show confetti effect
        binding.root.post {
            val centerX = binding.root.width / 2f
            val centerY = binding.root.height / 2f
            ParticleSystem.playEffect(
                this@LessonActivity,
                ParticleEffects.confetti(
                    centerX = centerX,
                    centerY = centerY,
                    width = binding.root.width.toFloat(),
                    height = binding.root.height.toFloat()
                )
            )
        }

        val alert = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_home).setOnClickListener {
            alert.dismiss()
            finish()
        }
        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_retry).setOnClickListener {
            alert.dismiss()
            // Restart current lesson from the beginning
            currentPageIndex = 0
            displayCurrentPage()
        }
        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_next).setOnClickListener {
            alert.dismiss()
            startActivity(Intent(this, LessonsActivity::class.java))
            finish()
        }

        alert.show()
    }
    
    private fun showLessonTutorial() {
        val tutorialPages = listOf(
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Lesson Navigation",
                description = "Use the Previous and Next buttons to navigate through lesson pages. The progress bar below shows your progress."
            ),
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Page Types",
                description = "Text pages explain concepts, camera pages let you practice gestures, and question pages test your knowledge."
            ),
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Camera Practice",
                description = "For camera pages, perform the gesture shown. The Skip button appears as an alternative if needed."
            ),
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Questions",
                description = "Answer questions correctly to proceed. The Next button is disabled until you select the right answer."
            )
        )
        
        val dialog = TutorialDialog(this, tutorialPages) { dontShowAgain ->
            // TODO: Save preference to not show again
            if (dontShowAgain) {
                Toast.makeText(this, "Tutorial won't show again", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }
}
