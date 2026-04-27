package com.example.kamaynikasyon.features.quizzes.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.utils.showError
import com.example.kamaynikasyon.core.utils.AnalyticsLogger
import com.example.kamaynikasyon.databinding.ActivityQuizSessionBinding
import com.example.kamaynikasyon.features.quizzes.data.models.Quiz
import com.example.kamaynikasyon.features.quizzes.data.repositories.QuizRepository
import com.example.kamaynikasyon.features.quizzes.fragments.QuizQuestionFragment
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.data.database.QuizProgress
import com.example.kamaynikasyon.data.database.DailyStreakProgress
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.example.kamaynikasyon.core.particles.ParticleSystem
import com.example.kamaynikasyon.core.particles.ParticleEffects

class QuizSessionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityQuizSessionBinding
    private lateinit var quizRepository: QuizRepository
    private var currentQuiz: Quiz? = null
    private var originalQuestions: List<com.example.kamaynikasyon.features.quizzes.data.models.QuizQuestion> = emptyList()
    private var reorderedQuestions: MutableList<com.example.kamaynikasyon.features.quizzes.data.models.QuizQuestion> = mutableListOf()
    private var currentQuestionIndex = 0
    private var userAnswers = mutableMapOf<Int, Int>() // Maps question index to answer
    private var answeredQuestionIndices = mutableSetOf<Int>() // Tracks which original question indices have been answered
    private var isDailyStreak = false
    private var isQuestionAnswered = false
    private var pendingFeedbackDialog: androidx.appcompat.app.AlertDialog? = null
    
    companion object {
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_IS_DAILY_STREAK = "is_daily_streak"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRepository()
        setupToolbar()
        // Confirm on system back as well
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Dismiss any pending feedback dialog first
                pendingFeedbackDialog?.dismiss()
                showExitConfirmationDialog()
            }
        })
        setupNavigation()
        loadQuiz()
        
        // Log screen view
        val screenName = if (intent.getBooleanExtra(EXTRA_IS_DAILY_STREAK, false)) "Daily Streak" else "Quiz"
        AnalyticsLogger.logScreenView(screenName, "QuizSessionActivity")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pendingFeedbackDialog?.dismiss()
        pendingFeedbackDialog = null
    }
    
    private fun setupRepository() {
        quizRepository = QuizRepository(this)
    }
    
    private fun loadQuiz() {
        isDailyStreak = intent.getBooleanExtra(EXTRA_IS_DAILY_STREAK, false)
        
        // Show loading indicator and hide content
        binding.progressLoading.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
        
        // Disable navigation button during loading
        binding.btnNext.isEnabled = false
        
        lifecycleScope.launch {
            if (isDailyStreak) {
                // For daily streak, create a temporary quiz with random questions
                val randomQuestions = withContext(Dispatchers.IO) {
                    quizRepository.getRandomQuestionsForDailyStreak(5)
                }
                currentQuiz = Quiz(
                    id = "daily_streak_${System.currentTimeMillis()}",
                    title = "Daily Streak",
                    description = "Today's random quiz",
                    difficulty = "mixed",
                    questions = randomQuestions
                )
            } else {
                val quizId = intent.getStringExtra(EXTRA_QUIZ_ID)
                currentQuiz = quizId?.let { 
                    withContext(Dispatchers.IO) {
                        quizRepository.getQuizById(it)
                    }
                }
            }
            
            if (currentQuiz == null) {
                binding.progressLoading.visibility = View.GONE
                showError(R.string.error_quiz_not_found)
                finish()
                return@launch
            }
            
            // Store original questions and initialize reordered list
            originalQuestions = currentQuiz!!.questions
            reorderedQuestions = originalQuestions.toMutableList()
            
            // Hide loading indicator and show content
            binding.progressLoading.visibility = View.GONE
            binding.fragmentContainer.visibility = View.VISIBLE
            
            // Re-enable navigation button after loading
            binding.btnNext.isEnabled = false // Disabled until answer is selected
            
            // Setup progress bar and display first question after quiz is loaded
            setupProgressBar()
            displayCurrentQuestion()
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            showExitConfirmationDialog()
        }
        
        binding.btnInfo.setOnClickListener {
            showQuizSessionTutorial()
        }
        
        updateToolbarTitle()
    }
    
    private fun setupNavigation() {
        binding.btnNext.setOnClickListener {
            // Submit answer and show feedback
            submitAnswer()
        }
    }
    
    private fun setupProgressBar() {
        currentQuiz?.let { quiz ->
            binding.progressBar.max = 100
            updateProgressBar()
        }
    }
    
    private fun updateProgressBar() {
        val totalQuestions = reorderedQuestions.size
        if (totalQuestions > 0) {
            val progress = ((currentQuestionIndex + 1) * 100) / totalQuestions
            binding.progressBar.progress = progress
        }
    }
    
    private fun updateToolbarTitle() {
        binding.toolbar.title = "${currentQuiz?.title ?: "Quiz"} (${currentQuestionIndex + 1}/${reorderedQuestions.size})"
    }
    
    private fun displayCurrentQuestion() {
        if (reorderedQuestions.isEmpty()) return
        val question = reorderedQuestions.getOrNull(currentQuestionIndex) ?: return
        
        updateToolbarTitle()
        updateProgressBar()
        
        // Get the original question index to track answers
        val originalIndex = originalQuestions.indexOf(question)
        val previousAnswer = userAnswers[originalIndex]
        
        // Load question fragment
        val fragment = QuizQuestionFragment.newInstance(question, currentQuestionIndex)
        fragment.setOnAnswerSelectedListener { selectedAnswer ->
            // Store answer with original question index
            userAnswers[originalIndex] = selectedAnswer
            isQuestionAnswered = true
            binding.btnNext.isEnabled = true
        }
        
        // Reset question answered state for new question
        isQuestionAnswered = false
        binding.btnNext.isEnabled = previousAnswer != null // Enable if already answered
        
        // Update button text
        binding.btnNext.text = "Submit"
        
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
        
        // Restore previous answer after fragment is attached
        if (previousAnswer != null) {
            binding.fragmentContainer.post {
                fragment.setSelectedAnswer(previousAnswer)
            }
        }
    }
    
    private fun submitAnswer() {
        if (reorderedQuestions.isEmpty()) return
        val question = reorderedQuestions.getOrNull(currentQuestionIndex) ?: return
        val originalIndex = originalQuestions.indexOf(question)
        
        // Get user's answer
        val userAnswer = userAnswers[originalIndex] ?: -1
        
        if (userAnswer < 0) {
            // No answer selected
            Toast.makeText(this, "Please select an answer first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if answer is correct
        val isCorrect = userAnswer == question.correctAnswer
        
        // Show feedback dialog
        showAnswerFeedbackDialog(isCorrect, question) {
            // After dialog is dismissed, handle question reordering
            if (!isCorrect) {
                // Move incorrect question to the end (only if not already at the end)
                if (currentQuestionIndex < reorderedQuestions.size - 1) {
                    reorderedQuestions.removeAt(currentQuestionIndex)
                    reorderedQuestions.add(question)
                    // Don't increment index since we removed current question
                } else {
                    // Already at the end - user had their chance, move forward to complete quiz
                    currentQuestionIndex++
                }
            } else {
                // Mark as answered correctly
                answeredQuestionIndices.add(originalIndex)
                currentQuestionIndex++
            }
            
            // Check if quiz is complete
            if (currentQuestionIndex >= reorderedQuestions.size) {
                showQuizResults()
            } else {
                displayCurrentQuestion()
            }
        }
    }
    
    private fun showAnswerFeedbackDialog(isCorrect: Boolean, question: com.example.kamaynikasyon.features.quizzes.data.models.QuizQuestion, onDismiss: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_quiz_feedback, null)
        
        val emojiView = dialogView.findViewById<android.widget.TextView>(R.id.tv_feedback_emoji)
        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.tv_feedback_title)
        val messageView = dialogView.findViewById<android.widget.TextView>(R.id.tv_feedback_message)
        val continueBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_continue)
        
        if (isCorrect) {
            emojiView.text = "✓"
            emojiView.setTextColor(ContextCompat.getColor(this, R.color.correct_color))
            titleView.text = "Correct!"
            titleView.setTextColor(ContextCompat.getColor(this, R.color.correct_color))
            messageView.text = question.explanation ?: "Great job! You got it right."
            // Light haptic on correct
            com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateLight(this)
        } else {
            emojiView.text = "✗"
            emojiView.setTextColor(ContextCompat.getColor(this, R.color.wrong_color))
            titleView.text = "Incorrect"
            titleView.setTextColor(ContextCompat.getColor(this, R.color.wrong_color))
            messageView.text = question.explanation ?: "That's not quite right. You'll get another chance at the end."
            // Medium haptic on wrong
            com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateMedium(this)
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        continueBtn.setOnClickListener {
            dialog.dismiss()
            onDismiss()
        }
        
        pendingFeedbackDialog = dialog
        
        // Configure dialog window to extend to sides
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.7f)
        }
        
        dialog.show()
        
        // Remove AlertDialog's default padding by accessing the parent FrameLayout
        val parentView = dialogView.parent as? android.view.ViewGroup
        parentView?.setPadding(0, 0, 0, 0)
        
        // Set layout after showing (required for AlertDialog to override default constraints)
        val displayMetrics = resources.displayMetrics
        val dialogWidth = (displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(
            dialogWidth,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Also set the layout params directly to ensure it takes effect
        dialog.window?.attributes?.apply {
            width = dialogWidth
            height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        }
        dialog.window?.attributes = dialog.window?.attributes
    }
    
    
    private fun showQuizResults() {
        val quiz = currentQuiz ?: return
        val correctAnswers = calculateCorrectAnswers()
        val totalQuestions = originalQuestions.size
        val score = if (totalQuestions > 0) (correctAnswers * 100) / totalQuestions else 0
        // Save progress as counts
        if (!isDailyStreak) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                db.quizProgressDao().upsert(
                    QuizProgress(
                        quizId = quiz.id,
                        correctAnswers = correctAnswers,
                        totalQuestions = totalQuestions,
                        completed = true
                    )
                )
                // Log quiz completion to Crashlytics
                com.example.kamaynikasyon.core.utils.CrashlyticsLogger.logQuizEvent(
                    "completed",
                    quiz.id,
                    mapOf(
                        "score" to score.toString(),
                        "correctAnswers" to correctAnswers.toString(),
                        "totalQuestions" to totalQuestions.toString()
                    )
                )
                // Log quiz completion to Analytics
                AnalyticsLogger.logQuizEvent(
                    "completed",
                    quiz.id,
                    quiz.title,
                    correctAnswers,
                    totalQuestions
                )
            }
        } else {
            // Save daily streak progress with date
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                val today = LocalDate.now().toString()
                db.dailyStreakProgressDao().upsert(
                    DailyStreakProgress(
                        date = today,
                        correctAnswers = correctAnswers,
                        totalQuestions = totalQuestions,
                        completed = true
                    )
                )
            }
        }
        
        // Show custom completion dialog per flow
        val dialogView = if (isDailyStreak) {
            val v = layoutInflater.inflate(R.layout.dialog_daily_streak_complete, null)
            val today = java.time.LocalDate.now().toString()
            v.findViewById<android.widget.TextView>(R.id.tv_title).text = "Daily Streak Complete!"
            v.findViewById<android.widget.TextView>(R.id.tv_date).text = "Date: ${today}"
            v.findViewById<android.widget.TextView>(R.id.tv_correct).text = "Correct: ${correctAnswers}/${totalQuestions}"
            v.findViewById<android.widget.TextView>(R.id.tv_score).text = "Score: ${score}%"
            
            // Populate question breakdown
            populateQuestionBreakdown(v, quiz)
            
            v
        } else {
            val v = layoutInflater.inflate(R.layout.dialog_quiz_complete, null)
            v.findViewById<android.widget.TextView>(R.id.tv_title).text = "Quiz Complete!"
            v.findViewById<android.widget.TextView>(R.id.tv_correct).text = "Correct: ${correctAnswers}/${totalQuestions}"
            v.findViewById<android.widget.TextView>(R.id.tv_score).text = "Score: ${score}%"
            
            // Populate question breakdown
            populateQuestionBreakdown(v, quiz)
            
            v
        }

        // Medium haptic on quiz completion (daily or regular)
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateMedium(this)

        // Show confetti effect
        binding.root.post {
            val centerX = binding.root.width / 2f
            val centerY = binding.root.height / 2f
            ParticleSystem.playEffect(
                this@QuizSessionActivity,
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

        val homeBtn = dialogView.findViewById<android.widget.Button>(R.id.btn_home)
        val retryBtn = dialogView.findViewById<android.widget.Button>(R.id.btn_retry)
        val nextBtn = dialogView.findViewById<android.widget.Button>(R.id.btn_next)

        homeBtn.setOnClickListener {
            alert.dismiss()
            finish()
        }
        retryBtn.setOnClickListener {
            alert.dismiss()
            // Retry quiz session
            val i = intent
            startActivity(i)
            finish()
        }
        nextBtn.setOnClickListener {
            alert.dismiss()
            if (isDailyStreak) {
                startActivity(Intent(this, QuizzesActivity::class.java))
            } else {
                startActivity(Intent(this, com.example.kamaynikasyon.features.lessons.activities.LessonsActivity::class.java))
            }
            finish()
        }

        // For Daily Streak, remove Retry and Next buttons
        if (isDailyStreak) {
            retryBtn.visibility = android.view.View.GONE
            nextBtn.visibility = android.view.View.GONE
            homeBtn.text = "Close"
        }

        alert.show()
    }
    
    private fun calculateCorrectAnswers(): Int {
        var correct = 0
        originalQuestions.forEachIndexed { index, question ->
            val userAnswer = userAnswers[index]
            if (userAnswer != null && userAnswer == question.correctAnswer) {
                correct++
            }
        }
        return correct
    }
    
    private fun populateQuestionBreakdown(dialogView: View, quiz: Quiz) {
        val container = dialogView.findViewById<android.widget.LinearLayout>(R.id.question_breakdown_container)
        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.tv_breakdown_title)
        container?.removeAllViews()
        
        // Show all questions with chosen option and result
        originalQuestions.forEachIndexed { index, question ->
            val userAnswer = userAnswers[index] ?: -1
            val isCorrect = userAnswer == question.correctAnswer
            
            // Get option letter (A, B, C, D) or handle unanswered
            val optionLetter = if (userAnswer >= 0 && userAnswer < 26) {
                ('A' + userAnswer).toString()
            } else {
                "N/A"
            }
            
            // Format: Q1: Chose Option A - Wrong/Correct
            val statusText = if (userAnswer < 0) {
                "Not answered"
            } else if (isCorrect) {
                "Correct"
            } else {
                "Wrong"
            }
            val displayText = "Q${index + 1}: Chose Option $optionLetter - $statusText"
            
            val itemView = android.widget.TextView(this).apply {
                text = displayText
                textSize = 14f
                val color = when {
                    userAnswer < 0 -> ContextCompat.getColor(this@QuizSessionActivity, R.color.text_secondary)
                    isCorrect -> ContextCompat.getColor(this@QuizSessionActivity, R.color.correct_color)
                    else -> ContextCompat.getColor(this@QuizSessionActivity, R.color.wrong_color)
                }
                setTextColor(color)
                setPadding(0, 8, 0, 8)
            }
            container?.addView(itemView)
        }
        
        // Always show the title
        titleView?.visibility = android.view.View.VISIBLE
    }
    
    private fun getAnswerOptionText(option: com.example.kamaynikasyon.features.quizzes.data.models.AnswerOption): String {
        return when {
            option.text != null -> option.text
            option.media != null -> when (option.media.type) {
                com.example.kamaynikasyon.core.media.MediaType.VIDEO -> "Video answer"
                com.example.kamaynikasyon.core.media.MediaType.IMAGE -> "Image answer"
                else -> "Media answer"
            }
            else -> "Option ${option.id}"
        }
    }
    
    private fun showExitConfirmationDialog() {
        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_exit_confirmation, null)
        // Customize copy for quiz
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_dialog_title)?.text = "Exit Quiz"
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_dialog_message)?.text = "Are you sure you want to exit? Your progress will be lost."

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.example.kamaynikasyon.R.id.btn_continue_lesson)
            ?.setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.example.kamaynikasyon.R.id.btn_exit_lesson)
            ?.setOnClickListener {
                dialog.dismiss()
                finish()
            }

        dialog.show()
    }
    
    private fun showQuizSessionTutorial() {
        val tutorialPages = listOf(
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Quiz Session",
                description = "You're now taking a quiz. Answer each question by selecting the correct option. Your progress is shown in the title."
            ),
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Answering Questions",
                description = "Read the question carefully and select your answer. You can only select one option per question."
            ),
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Navigation",
                description = "Questions will advance automatically after you select an answer. Use the back button to exit if needed."
            ),
            TutorialPage(
                iconRes = R.drawable.default_image,
                title = "Results",
                description = "After completing all questions, you'll see your score and performance summary."
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
