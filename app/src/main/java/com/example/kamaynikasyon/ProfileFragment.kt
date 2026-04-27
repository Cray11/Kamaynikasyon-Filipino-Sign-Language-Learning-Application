package com.example.kamaynikasyon

import com.example.kamaynikasyon.core.base.BaseFragment
import com.example.kamaynikasyon.databinding.FragmentProfileBinding
import androidx.lifecycle.lifecycleScope
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.features.auth.activities.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.kamaynikasyon.features.auth.data.AuthRepository
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import android.widget.Toast
import android.widget.ProgressBar
import com.example.kamaynikasyon.features.lessons.data.repositories.LessonRepository
import com.example.kamaynikasyon.features.quizzes.data.repositories.QuizRepository
import androidx.core.content.ContextCompat
import android.widget.TextView
import android.content.res.ColorStateList
import com.example.kamaynikasyon.core.utils.showLoading
import com.example.kamaynikasyon.core.utils.setTextWithSlideUp
import com.example.kamaynikasyon.core.utils.animateNumber
import com.example.kamaynikasyon.core.utils.AnimationHelper
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import org.json.JSONObject
import java.nio.charset.Charset
import android.view.animation.DecelerateInterpolator
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.kamaynikasyon.features.settings.SettingsActivity

data class MinigameStats(
    val name: String,
    val totalScore: Int = 0,
    val totalStars: Int = 0,
    val levelsCompleted: Int = 0,
    val totalLevels: Int = 0
)

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {
    
    private var statsExpanded = false
    private val progressResetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == SettingsActivity.ACTION_PROGRESS_RESET) {
                // Reload progress data when progress is reset
                loadProgressData()
            }
        }
    }
    
    private data class ProgressStats(
        val lessonsCompleted: Int = 0,
        val totalLessons: Int = 0,
        val quizzesCompleted: Int = 0,
        val totalQuizzes: Int = 0,
        val dailyStreaksCompleted: Int = 0,
        val bubbleShooter: MinigameStats = MinigameStats("Bubble Shooter"),
        val spellingSequence: MinigameStats = MinigameStats("Spelling Sequence"),
        val pictureQuiz: MinigameStats = MinigameStats("Picture Quiz"),
        val gestureMatch: MinigameStats = MinigameStats("Gesture Match"),
        // New interesting stats
        val averageQuizScore: Double = 0.0,
        val dailyStreak: Int = 0,
        val totalQuestionsAnswered: Int = 0,
        val totalPlayTimeMinutes: Int = 0,
        val bestPerformingMinigame: String = "None",
        val totalScore: Long = 0L
    )
    
    private data class LevelInfo(
        val name: String,
        val colorRes: Int
    )
    
    private fun getLevelInfo(percentage: Double): LevelInfo {
        return when {
            percentage >= 100.0 -> LevelInfo("Master", R.color.primary_color)
            percentage >= 75.0 -> LevelInfo("Expert", android.R.color.holo_purple)
            percentage >= 50.0 -> LevelInfo("Advanced", android.R.color.holo_blue_dark)
            percentage >= 25.0 -> LevelInfo("Intermediate", android.R.color.holo_green_dark)
            else -> LevelInfo("Beginner", R.color.gray_200)
        }
    }
    
    override fun getViewBinding(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?): FragmentProfileBinding {
        return FragmentProfileBinding.inflate(inflater, container, false)
    }
    
    override fun setupUI() {
        // Load Firebase user profile (Google accounts show photo/name/email)
        val currentUser = FirebaseAuth.getInstance().currentUser
        val email = currentUser?.email
        val displayName = currentUser?.displayName ?: email?.substringBefore("@")
        val photoUrl = currentUser?.photoUrl
        
        // Check if user is a guest (no email, no display name, or anonymous)
        val isGuest = email.isNullOrBlank() && displayName.isNullOrBlank() && 
                     (currentUser?.isAnonymous == true || currentUser == null)

        if (!displayName.isNullOrBlank()) {
            binding.textDisplayName.text = displayName
            binding.textWelcomeMessage.text = "Welcome back, $displayName!"
        } else {
            binding.textDisplayName.text = "Guest User"
            binding.textWelcomeMessage.text = "Welcome to Kamaynikasyon!"
        }
        
        if (!email.isNullOrBlank()) {
            binding.textEmail.text = email
            binding.textEmail.visibility = View.VISIBLE
        } else {
            binding.textEmail.visibility = View.GONE
        }
        
        // Update button based on guest status
        setupAuthButton(isGuest)
        
        // Animate profile image
        binding.imageProfile.alpha = 0f
        binding.imageProfile.scaleX = 0.8f
        binding.imageProfile.scaleY = 0.8f
        
        if (photoUrl != null) {
            Glide.with(this)
                .load(photoUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.mipmap.ic_launcher_round)
                .into(binding.imageProfile)
            
            // Animate image after a short delay to allow Glide to load
            binding.imageProfile.postDelayed({
                binding.imageProfile.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }, 200)
        } else {
            binding.imageProfile.setImageResource(R.mipmap.ic_launcher_round)
            // Animate placeholder image
            binding.imageProfile.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Initialize cards with hidden state for animations
        initializeCardsForAnimation()

        // Show loading indicator with animation
        binding.progressLoading.visibility = View.VISIBLE
        AnimationHelper.fadeIn(binding.progressLoading, 200)
        AnimationHelper.startRotateAnimation(binding.progressLoading)

        // Animate profile header card
        animateProfileHeader()

        // Show loading state on all stat text views
        showLoadingOnStats()

        // Load all progress data
        loadProgressData()

        // Stats toggle button with animation
        binding.layoutStatsToggle.setOnClickListener {
            toggleStatsContainer()
        }

        // Click listener for lessons/quizzes card
        binding.cardLessonsQuizzes.setOnClickListener {
            showDifficultyBreakdownDialog()
        }

        // Auth button (Sign Out or Log in) is set up in setupAuthButton()
    }
    
    /**
     * Helper function to get total levels from index.json file
     * Matches the logic used in selection activities
     */
    private suspend fun getTotalLevelsFromIndex(
        minigameName: String,
        progressSize: Int
    ): Int = withContext(Dispatchers.IO) {
        var totalLevels = progressSize
        try {
            val indexPath = "minigames/$minigameName/index.json"
            val jsonString = if (SupabaseConfig.isInitialized()) {
                SupabaseStorage.downloadTextFile(
                    requireContext(),
                    SupabaseConfig.BUCKET_MINIGAMES,
                    "$minigameName/index.json"
                ) ?: loadFromAssets(indexPath)
            } else {
                loadFromAssets(indexPath)
            }
            if (jsonString != null) {
                val root = JSONObject(jsonString)
                val levels = root.getJSONArray("levels")
                totalLevels = maxOf(totalLevels, levels.length())
            }
        } catch (_: Exception) {
            // Use progress size as fallback
        }
        totalLevels
    }
    
    /**
     * Helper function to load JSON from assets
     */
    private fun loadFromAssets(assetPath: String): String? {
        return try {
            String(requireContext().assets.open(assetPath).use { it.readBytes() }, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            null
        }
    }
    
    private fun loadProgressData() {
        lifecycleScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(requireContext())
                    
                    // Load lessons
                    val lessonsCompleted = db.lessonProgressDao().countCompleted()
                    val lessonRepository = LessonRepository(requireContext())
                    val totalLessons = lessonRepository.loadLessons().size
                    
                    // Load quizzes
                    val allQuizzes = db.quizProgressDao().getAllScores()
                    val quizzesCompleted = allQuizzes.count { it.completed }
                    val quizRepository = QuizRepository(requireContext())
                    val totalQuizzes = quizRepository.loadQuizzes().size
                    
                    // Load daily streaks
                    val dailyStreaks = db.dailyStreakProgressDao().getByDates(
                        (0..30).map { 
                            java.time.LocalDate.now().minusDays(it.toLong()).toString() 
                        }
                    )
                    val dailyStreaksCompleted = dailyStreaks.count { it.completed }
                    
                    // Load Bubble Shooter progress
                    val bubbleShooterProgress = db.bubbleShooterProgressDao().getAll()
                    val bubbleShooterTotalLevels = getTotalLevelsFromIndex("bubble_shooter", bubbleShooterProgress.size)
                    val bubbleShooterStats = MinigameStats(
                        name = "Bubble Shooter",
                        totalScore = bubbleShooterProgress.sumOf { it.bestScore },
                        totalStars = bubbleShooterProgress.sumOf { it.bestStars },
                        levelsCompleted = bubbleShooterProgress.count { it.completed },
                        totalLevels = bubbleShooterTotalLevels
                    )
                    
                    // Load Spelling Sequence progress
                    val spellingProgress = db.spellingSequenceProgressDao().getAll()
                    val spellingTotalLevels = getTotalLevelsFromIndex("spelling_sequence", spellingProgress.size)
                    val spellingStats = MinigameStats(
                        name = "Spelling Sequence",
                        totalScore = spellingProgress.sumOf { it.bestScore },
                        totalStars = spellingProgress.sumOf { it.bestStars },
                        levelsCompleted = spellingProgress.count { it.completed },
                        totalLevels = spellingTotalLevels
                    )
                    
                    // Load Picture Quiz progress
                    val pictureQuizProgress = db.pictureQuizProgressDao().getAll()
                    val pictureQuizTotalLevels = getTotalLevelsFromIndex("picture_quiz", pictureQuizProgress.size)
                    val pictureQuizStats = MinigameStats(
                        name = "Picture Quiz",
                        totalScore = pictureQuizProgress.sumOf { it.bestScore },
                        totalStars = pictureQuizProgress.sumOf { it.bestStars },
                        levelsCompleted = pictureQuizProgress.count { it.completed },
                        totalLevels = pictureQuizTotalLevels
                    )
                    
                    // Load Gesture Match progress
                    val gestureMatchProgress = db.gestureMatchProgressDao().getAll()
                    val gestureMatchTotalLevels = getTotalLevelsFromIndex("gesture_match", gestureMatchProgress.size)
                    val gestureMatchStats = MinigameStats(
                        name = "Gesture Match",
                        totalScore = gestureMatchProgress.sumOf { it.bestScore },
                        totalStars = gestureMatchProgress.sumOf { it.bestStars },
                        levelsCompleted = gestureMatchProgress.count { it.completed },
                        totalLevels = gestureMatchTotalLevels
                    )
                    
                    // Calculate interesting stats
                    // Average Quiz Score
                    val allQuizScores = allQuizzes.filter { it.totalQuestions > 0 }
                    val dailyStreakScores = dailyStreaks.filter { it.totalQuestions > 0 }
                    val totalCorrect = (allQuizScores.sumOf { it.correctAnswers } + 
                                      dailyStreakScores.sumOf { it.correctAnswers })
                    val totalQuestions = (allQuizScores.sumOf { it.totalQuestions } + 
                                         dailyStreakScores.sumOf { it.totalQuestions })
                    val averageQuizScore = if (totalQuestions > 0) {
                        (totalCorrect.toDouble() / totalQuestions.toDouble()) * 100.0
                    } else 0.0
                    
                    // Daily Streak - calculate consecutive days from today backwards
                    val completedDates = dailyStreaks
                        .filter { it.completed }
                        .map { java.time.LocalDate.parse(it.date) }
                        .toSet()
                    
                    var streak = 0
                    var checkDate = java.time.LocalDate.now()
                    // Check if today is completed, if not start from yesterday
                    if (!completedDates.contains(checkDate)) {
                        checkDate = checkDate.minusDays(1)
                    }
                    
                    // Count consecutive days backwards
                    while (completedDates.contains(checkDate)) {
                        streak++
                        checkDate = checkDate.minusDays(1)
                    }
                    
                    // Total Questions Answered
                    val totalQuestionsAnswered = totalQuestions
                    
                    // Total Play Time (in minutes)
                    val totalTimeSeconds = (bubbleShooterProgress.sumOf { it.bestTimeSeconds } +
                                          spellingProgress.sumOf { it.bestTimeSeconds } +
                                          pictureQuizProgress.sumOf { it.bestTimeSeconds } +
                                          gestureMatchProgress.sumOf { it.bestTimeSeconds })
                    val totalPlayTimeMinutes = totalTimeSeconds / 60
                    
                    // Best Performing Minigame (by stars)
                    val minigameStars = mapOf(
                        "Bubble Shooter" to bubbleShooterStats.totalStars,
                        "Spelling Sequence" to spellingStats.totalStars,
                        "Picture Quiz" to pictureQuizStats.totalStars,
                        "Gesture Match" to gestureMatchStats.totalStars
                    )
                    val bestPerformingMinigame = minigameStars.maxByOrNull { it.value }?.key ?: "None"
                    
                    // Total Score across all activities
                    val totalScore = (bubbleShooterStats.totalScore.toLong() +
                                    spellingStats.totalScore.toLong() +
                                    pictureQuizStats.totalScore.toLong() +
                                    gestureMatchStats.totalScore.toLong())
                    
                    ProgressStats(
                        lessonsCompleted = lessonsCompleted,
                        totalLessons = totalLessons,
                        quizzesCompleted = quizzesCompleted,
                        totalQuizzes = totalQuizzes,
                        dailyStreaksCompleted = dailyStreaksCompleted,
                        bubbleShooter = bubbleShooterStats,
                        spellingSequence = spellingStats,
                        pictureQuiz = pictureQuizStats,
                        gestureMatch = gestureMatchStats,
                        averageQuizScore = averageQuizScore,
                        dailyStreak = streak,
                        totalQuestionsAnswered = totalQuestionsAnswered,
                        totalPlayTimeMinutes = totalPlayTimeMinutes,
                        bestPerformingMinigame = bestPerformingMinigame,
                        totalScore = totalScore
                    )
                }
                
                withContext(Dispatchers.Main) {
                    displayProgress(stats)
                    // Animate cards in with staggered delay
                    animateCardsIn()
                    // Hide loading indicator with animation
                    AnimationHelper.stopAnimation(binding.progressLoading)
                    AnimationHelper.fadeOut(binding.progressLoading, 150) {
                        binding.progressLoading.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Error loading progress", e)
                withContext(Dispatchers.Main) {
                    // Hide loading indicator with animation
                    AnimationHelper.stopAnimation(binding.progressLoading)
                    AnimationHelper.fadeOut(binding.progressLoading, 150) {
                        binding.progressLoading.visibility = View.GONE
                    }
                    // Display error state
                    displayProgress(ProgressStats())
                    // Still animate cards even on error
                    animateCardsIn()
                }
            }
        }
    }
    
    private fun showLoadingOnStats() {
        // Show loading state on all stat text views
        binding.textLessonsCompleted.showLoading()
        binding.textQuizzesCompleted.showLoading()
        binding.textDailyQuizzesCompleted.showLoading()
        binding.textBubbleShooterScore.showLoading()
        binding.textBubbleShooterStars.showLoading()
        binding.textBubbleShooterLevels.showLoading()
        binding.textSpellingSequenceScore.showLoading()
        binding.textSpellingSequenceStars.showLoading()
        binding.textSpellingSequenceLevels.showLoading()
        binding.textPictureQuizScore.showLoading()
        binding.textPictureQuizStars.showLoading()
        binding.textPictureQuizLevels.showLoading()
        binding.textGestureMatchScore.showLoading()
        binding.textGestureMatchStars.showLoading()
        binding.textGestureMatchLevels.showLoading()
        binding.textTotalStars.showLoading()
        binding.textTotalLevels.showLoading()
        binding.textAverageScore.showLoading()
        binding.textDailyStreak.showLoading()
        binding.textTotalQuestions.showLoading()
        binding.textTotalPlayTime.showLoading()
        binding.textBestMinigame.showLoading()
        binding.textTotalScore.showLoading()
    }
    
    private fun displayProgress(stats: ProgressStats) {
        // Lessons
        binding.textLessonsCompleted.text = "${stats.lessonsCompleted}/${stats.totalLessons}"
        binding.progressLessons.max = if (stats.totalLessons > 0) stats.totalLessons else 1
        binding.progressLessons.progress = stats.lessonsCompleted.coerceAtMost(stats.totalLessons)
        
        // Quizzes
        binding.textQuizzesCompleted.text = "${stats.quizzesCompleted}/${stats.totalQuizzes}"
        binding.progressQuizzes.max = if (stats.totalQuizzes > 0) stats.totalQuizzes else 1
        binding.progressQuizzes.progress = stats.quizzesCompleted.coerceAtMost(stats.totalQuizzes)
        binding.textDailyQuizzesCompleted.animateNumber(stats.dailyStreaksCompleted, format = "%d")
        
        // Bubble Shooter
        binding.textBubbleShooterScore.setTextWithSlideUp(formatNumber(stats.bubbleShooter.totalScore))
        binding.textBubbleShooterStars.animateNumber(stats.bubbleShooter.totalStars, format = "%d")
        binding.textBubbleShooterLevels.text = "${stats.bubbleShooter.levelsCompleted}/${stats.bubbleShooter.totalLevels}"
        binding.progressBubbleShooter.max = if (stats.bubbleShooter.totalLevels > 0) stats.bubbleShooter.totalLevels else 1
        binding.progressBubbleShooter.progress = stats.bubbleShooter.levelsCompleted.coerceAtMost(stats.bubbleShooter.totalLevels)
        
        // Spelling Sequence
        binding.textSpellingSequenceScore.setTextWithSlideUp(formatNumber(stats.spellingSequence.totalScore))
        binding.textSpellingSequenceStars.animateNumber(stats.spellingSequence.totalStars, format = "%d")
        binding.textSpellingSequenceLevels.text = "${stats.spellingSequence.levelsCompleted}/${stats.spellingSequence.totalLevels}"
        binding.progressSpellingSequence.max = if (stats.spellingSequence.totalLevels > 0) stats.spellingSequence.totalLevels else 1
        binding.progressSpellingSequence.progress = stats.spellingSequence.levelsCompleted.coerceAtMost(stats.spellingSequence.totalLevels)
        
        // Picture Quiz
        binding.textPictureQuizScore.setTextWithSlideUp(formatNumber(stats.pictureQuiz.totalScore))
        binding.textPictureQuizStars.animateNumber(stats.pictureQuiz.totalStars, format = "%d")
        binding.textPictureQuizLevels.text = "${stats.pictureQuiz.levelsCompleted}/${stats.pictureQuiz.totalLevels}"
        binding.progressPictureQuiz.max = if (stats.pictureQuiz.totalLevels > 0) stats.pictureQuiz.totalLevels else 1
        binding.progressPictureQuiz.progress = stats.pictureQuiz.levelsCompleted.coerceAtMost(stats.pictureQuiz.totalLevels)
        
        // Gesture Match
        binding.textGestureMatchScore.setTextWithSlideUp(formatNumber(stats.gestureMatch.totalScore))
        binding.textGestureMatchStars.animateNumber(stats.gestureMatch.totalStars, format = "%d")
        binding.textGestureMatchLevels.text = "${stats.gestureMatch.levelsCompleted}/${stats.gestureMatch.totalLevels}"
        binding.progressGestureMatch.max = if (stats.gestureMatch.totalLevels > 0) stats.gestureMatch.totalLevels else 1
        binding.progressGestureMatch.progress = stats.gestureMatch.levelsCompleted.coerceAtMost(stats.gestureMatch.totalLevels)
        
        // Update level chips based on progress percentages
        // Combined progress for lessons & quizzes
        val totalLessonsAndQuizzes = stats.totalLessons + stats.totalQuizzes
        val completedLessonsAndQuizzes = stats.lessonsCompleted + stats.quizzesCompleted
        updateLevelChip(binding.chipLessonsQuizzesLevel, completedLessonsAndQuizzes, totalLessonsAndQuizzes)
        
        val bubbleShooterPercentage = if (stats.bubbleShooter.totalLevels > 0) {
            (stats.bubbleShooter.levelsCompleted.toDouble() / stats.bubbleShooter.totalLevels.toDouble()) * 100.0
        } else 0.0
        updateLevelChip(binding.chipBubbleShooterLevel, bubbleShooterPercentage)
        
        val spellingSequencePercentage = if (stats.spellingSequence.totalLevels > 0) {
            (stats.spellingSequence.levelsCompleted.toDouble() / stats.spellingSequence.totalLevels.toDouble()) * 100.0
        } else 0.0
        updateLevelChip(binding.chipSpellingSequenceLevel, spellingSequencePercentage)
        
        val pictureQuizPercentage = if (stats.pictureQuiz.totalLevels > 0) {
            (stats.pictureQuiz.levelsCompleted.toDouble() / stats.pictureQuiz.totalLevels.toDouble()) * 100.0
        } else 0.0
        updateLevelChip(binding.chipPictureQuizLevel, pictureQuizPercentage)
        
        val gestureMatchPercentage = if (stats.gestureMatch.totalLevels > 0) {
            (stats.gestureMatch.levelsCompleted.toDouble() / stats.gestureMatch.totalLevels.toDouble()) * 100.0
        } else 0.0
        updateLevelChip(binding.chipGestureMatchLevel, gestureMatchPercentage)
        
        // Overall stats
        val totalStars = stats.bubbleShooter.totalStars + stats.spellingSequence.totalStars + 
                        stats.pictureQuiz.totalStars + stats.gestureMatch.totalStars
        val totalLevels = stats.bubbleShooter.levelsCompleted + stats.spellingSequence.levelsCompleted + 
                         stats.pictureQuiz.levelsCompleted + stats.gestureMatch.levelsCompleted
        
        binding.textTotalStars.animateNumber(totalStars, format = "%d")
        binding.textTotalLevels.animateNumber(totalLevels, format = "%d")
        
        // New interesting stats with animations
        binding.textAverageScore.setTextWithSlideUp(String.format("%.1f%%", stats.averageQuizScore))
        binding.textDailyStreak.animateNumber(stats.dailyStreak, format = "%d days")
        binding.textTotalQuestions.setTextWithSlideUp(formatNumber(stats.totalQuestionsAnswered))
        binding.textTotalPlayTime.setTextWithSlideUp(formatTime(stats.totalPlayTimeMinutes))
        binding.textBestMinigame.setTextWithSlideUp(stats.bestPerformingMinigame)
        binding.textTotalScore.setTextWithSlideUp(formatNumber(stats.totalScore.toInt()))
        
        // Animate stat boxes with subtle scale effect
        animateStatBoxes()
    }
    
    /**
     * Updates a level chip with the appropriate level name and color based on progress percentage
     */
    private fun updateLevelChip(textView: TextView, percentage: Double) {
        val levelInfo = getLevelInfo(percentage)
        textView.text = levelInfo.name
        val color = ContextCompat.getColor(requireContext(), levelInfo.colorRes)
        textView.backgroundTintList = ColorStateList.valueOf(color)
    }
    
    /**
     * Updates a level chip for lessons & quizzes based on completed/total counts
     */
    private fun updateLevelChip(textView: TextView, completed: Int, total: Int) {
        val percentage = if (total > 0) {
            (completed.toDouble() / total.toDouble()) * 100.0
        } else 0.0
        updateLevelChip(textView, percentage)
    }
    
    private fun formatTime(minutes: Int): String {
        return when {
            minutes >= 1440 -> String.format("%.1f days", minutes / 1440.0)
            minutes >= 60 -> String.format("%.1f hours", minutes / 60.0)
            else -> "$minutes min"
        }
    }
    
    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
            else -> number.toString()
        }
    }
    
    /**
     * Setup authentication button based on guest status
     */
    private fun setupAuthButton(isGuest: Boolean) {
        if (isGuest) {
            // Guest user - show "Log into an account" button
            binding.btnLogout.text = "Log into an account"
            binding.btnLogout.setOnClickListener {
                // Navigate to WelcomeActivity to allow login
                val intent = Intent(requireContext(), WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        } else {
            // Authenticated user - show "Sign Out" button
            binding.btnLogout.text = "Sign Out"
            binding.btnLogout.setOnClickListener {
                lifecycleScope.launch {
                    val repo = AuthRepository()
                    val result = repo.signOutAll(requireContext(), revokeAccess = true)
                    result.onSuccess {
                        val intent = Intent(requireContext(), WelcomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }.onFailure { error ->
                        Toast.makeText(
                            requireContext(),
                            error.message ?: "Failed to sign out. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    
    /**
     * Initialize cards with hidden state for entrance animations
     */
    private fun initializeCardsForAnimation() {
        // Set initial state for cards (hidden, ready for animation)
        binding.cardProfileHeader.alpha = 0f
        binding.cardLessonsQuizzes.alpha = 0f
        binding.cardBubbleShooter.alpha = 0f
        binding.cardSpellingSequence.alpha = 0f
        binding.cardPictureQuiz.alpha = 0f
        binding.cardGestureMatch.alpha = 0f
        
        // Set translation for slide-up effect
        val slideDistance = 30f * resources.displayMetrics.density
        binding.cardProfileHeader.translationY = slideDistance
        binding.cardLessonsQuizzes.translationY = slideDistance
        binding.cardBubbleShooter.translationY = slideDistance
        binding.cardSpellingSequence.translationY = slideDistance
        binding.cardPictureQuiz.translationY = slideDistance
        binding.cardGestureMatch.translationY = slideDistance
    }
    
    /**
     * Animate profile header card entrance
     */
    private fun animateProfileHeader() {
        AnimationHelper.slideInUp(binding.cardProfileHeader, 400)
    }
    
    /**
     * Animate all progress cards with staggered delay
     */
    private fun animateCardsIn() {
        val cards = listOf(
            binding.cardLessonsQuizzes,
            binding.cardBubbleShooter,
            binding.cardSpellingSequence,
            binding.cardPictureQuiz,
            binding.cardGestureMatch
        )
        
        cards.forEachIndexed { index, card ->
            AnimationHelper.animateListItem(
                card,
                index,
                delayPerItem = 80,
                animationType = "slideInUp"
            )
        }
    }
    
    /**
     * Toggle stats container with smooth animation
     */
    private fun toggleStatsContainer() {
        statsExpanded = !statsExpanded
        if (statsExpanded) {
            binding.layoutStatsContainer.visibility = View.VISIBLE
            binding.textStatsToggleArrow.text = "▲"
            AnimationHelper.slideInDown(binding.layoutStatsContainer, 300)
        } else {
            AnimationHelper.slideOutUp(binding.layoutStatsContainer, 250) {
                binding.layoutStatsContainer.visibility = View.GONE
            }
            binding.textStatsToggleArrow.text = "▼"
        }
    }
    
    /**
     * Animate stat boxes with subtle scale effect when data loads
     * Excludes total stars and total levels backgrounds (no animation)
     */
    private fun animateStatBoxes() {
        // Only animate the expandable stats, not total stars and total levels
        val statBoxes = listOf(
            binding.textAverageScore.parent as? View,
            binding.textDailyStreak.parent as? View
        ).filterNotNull()
        
        statBoxes.forEachIndexed { index, box ->
            box.scaleX = 0.95f
            box.scaleY = 0.95f
            box.alpha = 0.8f
            box.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setStartDelay(index * 50L)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
    
    override fun setupObservers() {
        // No observers
    }
    
    override fun onResume() {
        super.onResume()
        // Register broadcast receiver for progress reset
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            progressResetReceiver,
            IntentFilter(SettingsActivity.ACTION_PROGRESS_RESET)
        )
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(progressResetReceiver)
    }
    
    private fun showDifficultyBreakdownDialog() {
        val dialogView = android.view.LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_difficulty_breakdown, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()
        
        // Set title
        val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
        titleView?.text = "Lessons & Quizzes - Difficulty Breakdown"
        
        // Load and display difficulty breakdown
        lifecycleScope.launch {
            val breakdown = withContext(Dispatchers.IO) {
                loadDifficultyBreakdown()
            }
            
            withContext(Dispatchers.Main) {
                displayDifficultyBreakdown(dialogView, breakdown)
            }
        }
        
        dialog.show()
    }
    
    private data class DifficultyBreakdown(
        val easy: DifficultyStats,
        val medium: DifficultyStats,
        val hard: DifficultyStats
    )
    
    private data class DifficultyStats(
        val total: Int,
        val completed: Int,
        val completedItems: List<String>
    )
    
    private suspend fun loadDifficultyBreakdown(): DifficultyBreakdown = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(requireContext())
        val lessonRepository = LessonRepository(requireContext())
        val quizRepository = QuizRepository(requireContext())
        
        // Load all lessons and quizzes
        val allLessons = lessonRepository.loadLessons()
        val allQuizzes = quizRepository.loadQuizzes()
        
        // Get completed IDs
        val completedLessonIds = db.lessonProgressDao().getCompletedIds().toSet()
        val completedQuizIds = db.quizProgressDao().getAllScores()
            .filter { it.completed }
            .map { it.quizId }
            .toSet()
        
        // Group by difficulty
        fun groupByDifficulty(items: List<com.example.kamaynikasyon.features.lessons.data.models.Lesson>): Map<String, DifficultyStats> {
            val grouped = items.groupBy { it.difficulty.lowercase() }
            return mapOf(
                "easy" to DifficultyStats(
                    total = grouped["easy"]?.size ?: 0,
                    completed = grouped["easy"]?.count { completedLessonIds.contains(it.id) } ?: 0,
                    completedItems = grouped["easy"]?.filter { completedLessonIds.contains(it.id) }
                        ?.map { it.title } ?: emptyList()
                ),
                "medium" to DifficultyStats(
                    total = grouped["medium"]?.size ?: 0,
                    completed = grouped["medium"]?.count { completedLessonIds.contains(it.id) } ?: 0,
                    completedItems = grouped["medium"]?.filter { completedLessonIds.contains(it.id) }
                        ?.map { it.title } ?: emptyList()
                ),
                "hard" to DifficultyStats(
                    total = grouped["hard"]?.size ?: 0,
                    completed = grouped["hard"]?.count { completedLessonIds.contains(it.id) } ?: 0,
                    completedItems = grouped["hard"]?.filter { completedLessonIds.contains(it.id) }
                        ?.map { it.title } ?: emptyList()
                )
            )
        }
        
        fun groupQuizzesByDifficulty(items: List<com.example.kamaynikasyon.features.quizzes.data.models.Quiz>): Map<String, DifficultyStats> {
            val grouped = items.groupBy { it.difficulty.lowercase() }
            return mapOf(
                "easy" to DifficultyStats(
                    total = grouped["easy"]?.size ?: 0,
                    completed = grouped["easy"]?.count { completedQuizIds.contains(it.id) } ?: 0,
                    completedItems = grouped["easy"]?.filter { completedQuizIds.contains(it.id) }
                        ?.map { it.title } ?: emptyList()
                ),
                "medium" to DifficultyStats(
                    total = grouped["medium"]?.size ?: 0,
                    completed = grouped["medium"]?.count { completedQuizIds.contains(it.id) } ?: 0,
                    completedItems = grouped["medium"]?.filter { completedQuizIds.contains(it.id) }
                        ?.map { it.title } ?: emptyList()
                ),
                "hard" to DifficultyStats(
                    total = grouped["hard"]?.size ?: 0,
                    completed = grouped["hard"]?.count { completedQuizIds.contains(it.id) } ?: 0,
                    completedItems = grouped["hard"]?.filter { completedQuizIds.contains(it.id) }
                        ?.map { it.title } ?: emptyList()
                )
            )
        }
        
        val lessonsByDifficulty = groupByDifficulty(allLessons)
        val quizzesByDifficulty = groupQuizzesByDifficulty(allQuizzes)
        
        // Combine lessons and quizzes
        DifficultyBreakdown(
            easy = DifficultyStats(
                total = lessonsByDifficulty["easy"]!!.total + quizzesByDifficulty["easy"]!!.total,
                completed = lessonsByDifficulty["easy"]!!.completed + quizzesByDifficulty["easy"]!!.completed,
                completedItems = lessonsByDifficulty["easy"]!!.completedItems + quizzesByDifficulty["easy"]!!.completedItems
            ),
            medium = DifficultyStats(
                total = lessonsByDifficulty["medium"]!!.total + quizzesByDifficulty["medium"]!!.total,
                completed = lessonsByDifficulty["medium"]!!.completed + quizzesByDifficulty["medium"]!!.completed,
                completedItems = lessonsByDifficulty["medium"]!!.completedItems + quizzesByDifficulty["medium"]!!.completedItems
            ),
            hard = DifficultyStats(
                total = lessonsByDifficulty["hard"]!!.total + quizzesByDifficulty["hard"]!!.total,
                completed = lessonsByDifficulty["hard"]!!.completed + quizzesByDifficulty["hard"]!!.completed,
                completedItems = lessonsByDifficulty["hard"]!!.completedItems + quizzesByDifficulty["hard"]!!.completedItems
            )
        )
    }
    
    private fun displayDifficultyBreakdown(view: View, breakdown: DifficultyBreakdown) {
        // Easy
        val easyProgress = view.findViewById<TextView>(R.id.text_easy_progress)
        val easyProgressBar = view.findViewById<ProgressBar>(R.id.progress_easy)
        val easyRecycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_easy_items)
        
        easyProgress.text = "${breakdown.easy.completed}/${breakdown.easy.total}"
        easyProgressBar.max = if (breakdown.easy.total > 0) breakdown.easy.total else 1
        easyProgressBar.progress = breakdown.easy.completed.coerceAtMost(breakdown.easy.total)
        setupCompletedItemsRecycler(easyRecycler, breakdown.easy.completedItems)
        
        // Medium
        val mediumProgress = view.findViewById<TextView>(R.id.text_medium_progress)
        val mediumProgressBar = view.findViewById<ProgressBar>(R.id.progress_medium)
        val mediumRecycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_medium_items)
        
        mediumProgress.text = "${breakdown.medium.completed}/${breakdown.medium.total}"
        mediumProgressBar.max = if (breakdown.medium.total > 0) breakdown.medium.total else 1
        mediumProgressBar.progress = breakdown.medium.completed.coerceAtMost(breakdown.medium.total)
        setupCompletedItemsRecycler(mediumRecycler, breakdown.medium.completedItems)
        
        // Hard
        val hardProgress = view.findViewById<TextView>(R.id.text_hard_progress)
        val hardProgressBar = view.findViewById<ProgressBar>(R.id.progress_hard)
        val hardRecycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_hard_items)
        
        hardProgress.text = "${breakdown.hard.completed}/${breakdown.hard.total}"
        hardProgressBar.max = if (breakdown.hard.total > 0) breakdown.hard.total else 1
        hardProgressBar.progress = breakdown.hard.completed.coerceAtMost(breakdown.hard.total)
        setupCompletedItemsRecycler(hardRecycler, breakdown.hard.completedItems)
    }
    
    private fun setupCompletedItemsRecycler(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        items: List<String>
    ) {
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        recyclerView.adapter = CompletedItemsAdapter(items)
    }
    
    private class CompletedItemsAdapter(private val items: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<CompletedItemsAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val itemName: TextView = view.findViewById(R.id.text_item_name)
            val itemStatus: TextView = view.findViewById(R.id.text_item_status)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_completed_item, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemName.text = items[position]
            holder.itemStatus.text = "✓"
        }
        
        override fun getItemCount() = items.size
    }
}
