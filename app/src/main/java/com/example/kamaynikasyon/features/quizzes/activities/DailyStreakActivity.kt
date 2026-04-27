package com.example.kamaynikasyon.features.quizzes.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.ActivityDailyStreakBinding
import com.example.kamaynikasyon.features.quizzes.data.repositories.QuizRepository
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import com.example.kamaynikasyon.core.utils.UserPrefs
import com.example.kamaynikasyon.data.database.AppDatabase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray

class DailyStreakActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDailyStreakBinding
    private lateinit var quizRepository: QuizRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyStreakBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        // Confirm on system back as well
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
        setupRepository()
        setupUI()
        maybeShowDailyStreakTutorial()
        renderWeek()
    }

    override fun onResume() {
        super.onResume()
        renderWeek() // Refresh stats when returning from quiz
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide the default title
        
        binding.btnBack.setOnClickListener {
            showExitConfirmationDialog()
        }
        
        binding.btnInfo.setOnClickListener {
            showDailyStreakTutorial()
        }
    }

    private fun showExitConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_exit_confirmation, null)
        // Customize copy for daily streak hub
        dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_title)?.text = "Exit Daily Streak"
        dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_message)?.text = "Are you sure you want to exit?"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_continue_lesson)
            ?.setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_exit_lesson)
            ?.setOnClickListener {
                dialog.dismiss()
                finish()
            }

        dialog.show()
    }
    
    private fun showDailyStreakTutorial() {
        val tutorialPages = loadTutorialPagesFromAsset("daily_streak")
        val dialog = TutorialDialog(this, tutorialPages) { dontShowAgain ->
            if (dontShowAgain) {
                UserPrefs.setShowDailyStreakTutorial(this, false)
            }
        }
        dialog.show()
    }

    private fun loadTutorialPagesFromAsset(tutorialKey: String): List<TutorialPage> {
        val tutorialPages = mutableListOf<TutorialPage>()
        try {
            val json = assets.open("index.json").use { input ->
                org.json.JSONObject(String(input.readBytes(), java.nio.charset.Charset.forName("UTF-8")))
            }
            val tutorialsObj = json.optJSONObject("tutorials") ?: return tutorialPages
            val tutorialObj = tutorialsObj.optJSONObject(tutorialKey) ?: return tutorialPages
            val pageArray: JSONArray = tutorialObj.optJSONArray("tutorialPages") ?: JSONArray()
            for (i in 0 until pageArray.length()) {
                val pageObj = pageArray.getJSONObject(i)
                val iconName = pageObj.getString("iconRes")
                // Check if it's an asset path (contains "/" or starts with "img/")
                val isAssetPath = iconName.contains("/") || iconName.startsWith("img/")
                val iconRes = if (isAssetPath) {
                    R.drawable.default_image  // Use default as fallback
                } else {
                    resources.getIdentifier(iconName, "drawable", packageName).takeIf { it != 0 } ?: R.drawable.default_image
                }
                tutorialPages.add(
                    TutorialPage(
                        iconRes = iconRes,
                        title = pageObj.getString("title"),
                        description = pageObj.getString("description"),
                        iconPath = if (isAssetPath) iconName else null
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DailyStreakActivity", "Error loading tutorial", e)
        }
        return tutorialPages
    }

    private fun maybeShowDailyStreakTutorial() {
        if (UserPrefs.shouldShowDailyStreakTutorial(this)) {
            showDailyStreakTutorial()
        }
    }
    
    private fun setupRepository() {
        quizRepository = QuizRepository(this)
    }
    
    private fun setupUI() {
        // For now, show a simple UI with start button
        binding.btnStartQuiz.setOnClickListener {
            startDailyStreak()
        }
        
        // Show today's date
        val today = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())
        binding.tvDate.text = today
        
        // Show preview of today's questions
        lifecycleScope.launch {
            val todayQuestions = withContext(Dispatchers.IO) {
                quizRepository.getRandomQuestionsForDailyStreak(5)
            }
        binding.tvQuizPreview.text = "Today's Streak: ${todayQuestions.size} questions"
        }
    }

    private fun renderWeek() {
        lifecycleScope.launch {
            val today = LocalDate.now()
            // Ensure the displayed week always starts on Sunday
            val sunday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
            val dates = (0..6).map { sunday.plusDays(it.toLong()) }
            val dateKeys = dates.map { it.toString() }
            val progresses = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).dailyStreakProgressDao().getByDates(dateKeys)
            }
            val progressMap = progresses.associateBy { it.date }

            // Update 7 day views (labels + circles)
            val labelViews = listOf(
                binding.dayLabel1, binding.dayLabel2, binding.dayLabel3, binding.dayLabel4, binding.dayLabel5, binding.dayLabel6, binding.dayLabel7
            )
            val circleViews = listOf(
                binding.dayCircle1, binding.dayCircle2, binding.dayCircle3, binding.dayCircle4, binding.dayCircle5, binding.dayCircle6, binding.dayCircle7
            )
            circleViews.forEachIndexed { idx, circleView ->
                val d = dates[idx]
                val dateKey = d.toString()
                val isFuture = d.isAfter(today)
                val progress = progressMap[dateKey]
                
                // Update label text and color
                val labelView = labelViews[idx]
                labelView.text = d.dayOfWeek.name.substring(0, 3)
                
                // Update circle and label color based on status
                when {
                    isFuture -> {
                        circleView.text = ""
                        circleView.setBackgroundResource(R.drawable.circle_gray)
                        circleView.setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
                        labelView.setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
                    }
                    progress?.completed == true -> {
                        circleView.text = "✓"
                        circleView.setBackgroundResource(R.drawable.circle_green)
                        circleView.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        labelView.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    }
                    d.isEqual(today) -> {
                        circleView.text = ""
                        circleView.setBackgroundResource(R.drawable.circle_yellow)
                        circleView.setTextColor(android.graphics.Color.parseColor("#FFC107"))
                        labelView.setTextColor(android.graphics.Color.parseColor("#FFC107"))
                    }
                    else -> {
                        circleView.text = "X"
                        circleView.setBackgroundResource(R.drawable.circle_red)
                        circleView.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                        labelView.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    }
                }
            }

            // Compute current streak up to today
            var streak = 0
            var day = today
            while (true) {
                val entry = progressMap[day.toString()]
                if (entry?.completed == true) {
                    streak++
                    day = day.minusDays(1)
                } else {
                    break
                }
            }
            binding.tvCurrentStreak.text = "🔥 Streak: $streak"
            
            // Compute total completed
            val totalCompleted = progresses.count { it.completed }
            binding.tvTotalCompleted.text = "✅ Completed: $totalCompleted"

            binding.btnShowDates.setOnClickListener {
                // Start the progress activity instead of showing a dialog
                val intent = Intent(this@DailyStreakActivity, DailyStreakProgressActivity::class.java)
                startActivity(intent)
            }
        }
    }
    
    private fun startDailyStreak() {
        val intent = Intent(this, QuizSessionActivity::class.java)
        intent.putExtra(QuizSessionActivity.EXTRA_IS_DAILY_STREAK, true)
        startActivity(intent)
    }
    
}

