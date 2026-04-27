package com.example.kamaynikasyon.features.quizzes.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kamaynikasyon.MainActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.ActivityQuizzesSelectionBinding
import com.example.kamaynikasyon.features.quizzes.adapters.QuizAdapter
import com.example.kamaynikasyon.features.quizzes.data.models.Quiz
import com.example.kamaynikasyon.features.quizzes.data.repositories.QuizRepository
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import androidx.lifecycle.lifecycleScope
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.core.utils.UserPrefs
import com.example.kamaynikasyon.core.utils.EmptyStateView
import com.example.kamaynikasyon.core.utils.AnalyticsLogger
import com.example.kamaynikasyon.core.utils.AnimationHelper
import com.example.kamaynikasyon.core.utils.startActivityWithTransition
import com.example.kamaynikasyon.core.utils.showLoading
import com.example.kamaynikasyon.core.utils.setTextWithSlideUp
import com.example.kamaynikasyon.core.utils.animateNumber
import com.example.kamaynikasyon.core.ui.DataLoadErrorDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class QuizzesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityQuizzesSelectionBinding
    private lateinit var quizRepository: QuizRepository
    private lateinit var quizAdapter: QuizAdapter
    private lateinit var skeletonAdapter: com.example.kamaynikasyon.features.quizzes.adapters.QuizSkeletonAdapter
    private var allQuizzes: List<com.example.kamaynikasyon.features.quizzes.data.models.Quiz> = emptyList()
    private var displayedQuizzes: MutableList<com.example.kamaynikasyon.features.quizzes.data.models.Quiz> = mutableListOf()
    private var filteredQuizzes: List<com.example.kamaynikasyon.features.quizzes.data.models.Quiz> = emptyList()
    private val ITEMS_PER_PAGE = 10
    private var isLoadingMore = false
    private var errorDialog: AlertDialog? = null
    private var selectedDifficulty: String? = null
    private var noQuizzesDialog: AlertDialog? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizzesSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRepository()
        setupRecyclerView()
        setupSwipeRefresh()
        setupDifficultyFilters()
        setupBackPressHandler()
        updateUIForDifficultySelection() // Start with difficulty selection visible
        loadQuizzes()
        loadStats()
        maybeShowQuizzesTutorial()
        
        // Log screen view
        AnalyticsLogger.logScreenView("Quizzes", "QuizzesActivity")
    }

    override fun onResume() {
        super.onResume()
        loadQuizzes()
        loadStats()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
        
        binding.btnInfo.setOnClickListener {
            showQuizzesTutorial()
        }
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }
    
    private fun handleBackPress() {
        // If dialog is showing, don't allow back press
        if (noQuizzesDialog?.isShowing == true) {
            return
        }
        
        // If a difficulty is selected, go back to difficulty selection
        if (selectedDifficulty != null) {
            selectedDifficulty = null
            updateFilterButtonStates(null)
            applyFilter()
            updateUIForDifficultySelection()
        } else {
            finish()
        }
    }
    
    private fun setupRepository() {
        quizRepository = QuizRepository(this)
    }
    
    private fun setupRecyclerView() {
        quizAdapter = QuizAdapter { quiz ->
            startQuiz(quiz)
        }
        
        skeletonAdapter = com.example.kamaynikasyon.features.quizzes.adapters.QuizSkeletonAdapter(itemCount = 5)
        
        val layoutManager = LinearLayoutManager(this@QuizzesActivity)
        binding.recyclerViewQuizzes.apply {
            this.layoutManager = layoutManager
            adapter = skeletonAdapter // Start with skeleton adapter
            
            // Add scroll listener for lazy loading
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    
                    // Load more when user scrolls near the end
                    if (!isLoadingMore && 
                        firstVisibleItemPosition + visibleItemCount >= totalItemCount - 3 &&
                        displayedQuizzes.size < filteredQuizzes.size) {
                        loadMoreQuizzes()
                    }
                }
            })
        }
    }
    
    private fun loadMoreQuizzes() {
        if (isLoadingMore || displayedQuizzes.size >= filteredQuizzes.size) return
        
        isLoadingMore = true
        val startIndex = displayedQuizzes.size
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, filteredQuizzes.size)
        val newQuizzes = filteredQuizzes.subList(startIndex, endIndex)
        
        displayedQuizzes.addAll(newQuizzes)
        quizAdapter.updateQuizzes(displayedQuizzes)
        isLoadingMore = false
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadQuizzes()
            loadStats()
        }
        binding.swipeRefresh.setColorSchemeResources(
            R.color.primary_color,
            R.color.secondary_color
        )
    }
    
    private fun setupDifficultyFilters() {
        binding.btnFilterEasy.setOnClickListener {
            filterByDifficulty("easy")
        }
        binding.btnFilterMedium.setOnClickListener {
            filterByDifficulty("medium")
        }
        binding.btnFilterHard.setOnClickListener {
            filterByDifficulty("hard")
        }
    }
    
    private fun filterByDifficulty(difficulty: String) {
        // Set the selected difficulty
        selectedDifficulty = difficulty
        updateFilterButtonStates(difficulty)
        updateUIForDifficultySelected()
        
        // If quizzes haven't been loaded yet, load them first
        if (allQuizzes.isEmpty()) {
            loadQuizzes()
        } else {
            applyFilter()
        }
    }
    
    private fun updateUIForDifficultySelection() {
        // Show difficulty buttons and stats, hide quizzes
        binding.difficultyFilterContainer.visibility = View.VISIBLE
        binding.recyclerViewQuizzes.visibility = View.GONE
        binding.recyclerViewQuizzes.adapter = null // Clear adapter to prevent skeleton showing
        binding.statsCard.visibility = View.VISIBLE
        val emptyStateView = findViewById<View>(R.id.empty_state_view)
        emptyStateView?.let { EmptyStateView.hide(it) }
        
        // Update stats to show all quizzes (not filtered)
        loadStats()
    }
    
    
    private fun updateUIForDifficultySelected() {
        // Hide difficulty buttons, show quizzes
        binding.difficultyFilterContainer.visibility = View.GONE
        binding.statsCard.visibility = View.VISIBLE
    }
    
    private fun updateFilterButtonStates(selectedDifficulty: String?) {
        val easyButton = binding.btnFilterEasy
        val mediumButton = binding.btnFilterMedium
        val hardButton = binding.btnFilterHard
        
        // Reset all buttons
        easyButton.isSelected = false
        mediumButton.isSelected = false
        hardButton.isSelected = false
        
        // Update button states - selected state is handled by isSelected property
        when (selectedDifficulty?.lowercase()) {
            "easy" -> {
                easyButton.isSelected = true
            }
            "medium" -> {
                mediumButton.isSelected = true
            }
            "hard" -> {
                hardButton.isSelected = true
            }
            else -> {
                // No filter selected
            }
        }
    }
    
    private fun applyFilter() {
        if (selectedDifficulty == null) {
            // No difficulty selected - hide quizzes
            binding.recyclerViewQuizzes.visibility = View.GONE
            val emptyStateView = findViewById<View>(R.id.empty_state_view)
            emptyStateView?.let { EmptyStateView.hide(it) }
            return
        }
        
        // Show skeleton loader while filtering
        binding.recyclerViewQuizzes.visibility = View.VISIBLE
        binding.recyclerViewQuizzes.adapter = skeletonAdapter
        AnimationHelper.fadeIn(binding.recyclerViewQuizzes, 200)
        
        filteredQuizzes = allQuizzes.filter { 
            it.difficulty.lowercase() == selectedDifficulty?.lowercase() 
        }
        
        // Reset pagination
        displayedQuizzes.clear()
        val initialPage = filteredQuizzes.take(ITEMS_PER_PAGE)
        displayedQuizzes.addAll(initialPage)
        quizAdapter.updateQuizzes(displayedQuizzes)
        
        // Update scores and completion for filtered quizzes
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).quizProgressDao().getAllScores()
            }
            val scoreMap = rows.associate { it.quizId to (it.correctAnswers to it.totalQuestions) }
            val completedIds = rows.filter { it.completed }.map { it.quizId }.toSet()
            quizAdapter.updateScores(scoreMap)
            quizAdapter.updateCompleted(completedIds)
            
            // Switch to real adapter
            binding.recyclerViewQuizzes.adapter = quizAdapter
            
            // Show dialog if no quizzes match filter
            if (filteredQuizzes.isEmpty()) {
                binding.recyclerViewQuizzes.visibility = View.GONE
                showNoQuizzesDialog()
            } else {
                noQuizzesDialog?.dismiss()
                noQuizzesDialog = null
                val emptyStateView = findViewById<View>(R.id.empty_state_view)
                emptyStateView?.let { EmptyStateView.hide(it) }
                binding.recyclerViewQuizzes.alpha = 1f
                binding.recyclerViewQuizzes.visibility = View.VISIBLE
                AnimationHelper.fadeIn(binding.recyclerViewQuizzes, 300)
            }
        }
    }
    
    private fun loadQuizzes() {
        // Show skeleton loader instead of progress indicator
        binding.progressLoading.visibility = View.GONE
        val emptyStateView = findViewById<View>(R.id.empty_state_view)
        emptyStateView?.let { EmptyStateView.hide(it) }
        
        // Show skeleton with fade-in animation
        binding.recyclerViewQuizzes.adapter = skeletonAdapter
        AnimationHelper.fadeIn(binding.recyclerViewQuizzes, 200)
        
        lifecycleScope.launch {
            try {
                val quizzes = withContext(Dispatchers.IO) {
                    quizRepository.loadQuizzes()
                }
                allQuizzes = quizzes
                
                // If no difficulty is selected, show difficulty selection UI
                if (selectedDifficulty == null) {
                    updateUIForDifficultySelection()
                    binding.swipeRefresh.isRefreshing = false
                    return@launch
                }
                
                // Apply current filter and load data
                applyFilter()
                
                // Hide swipe refresh
                binding.swipeRefresh.isRefreshing = false
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                AnimationHelper.fadeOut(binding.recyclerViewQuizzes, 150) {
                    showLoadQuizzesErrorDialog()
                }
            }
        }
    }

    private fun loadStats() {
        // Show loading state on stats
        binding.tvQuizzesScore.showLoading("🏆 Score: ...")
        binding.tvQuizzesProgressCount.showLoading("...")
        
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val quizProgress = withContext(Dispatchers.IO) { db.quizProgressDao().getAllScores() }
                
                // Calculate stats
                val totalScore = quizProgress.sumOf { it.correctAnswers }
                val quizzesCompleted = quizProgress.count { it.completed }
                
                // Always use all quizzes for stats (not filtered)
                val totalQuizzes = allQuizzes.size
                
                // Update UI with animations
                binding.tvQuizzesScore.setTextWithSlideUp("🏆 Score: $totalScore")
                binding.tvQuizzesProgressCount.setTextWithSlideUp("$quizzesCompleted/$totalQuizzes")
                binding.quizzesProgress.apply {
                    max = if (totalQuizzes > 0) totalQuizzes else 1
                    this.progress = quizzesCompleted.coerceAtMost(totalQuizzes)
                }
            } catch (e: Exception) {
                android.util.Log.e("QuizzesActivity", "Error loading stats", e)
            }
        }
    }
    
    private fun startQuiz(quiz: Quiz) {
        val intent = Intent(this, QuizSessionActivity::class.java)
        intent.putExtra(QuizSessionActivity.EXTRA_QUIZ_ID, quiz.id)
        startActivityWithTransition(intent)
    }

    private fun showLoadQuizzesErrorDialog() {
        if (isFinishing || isDestroyed) return
        errorDialog?.dismiss()
        errorDialog = DataLoadErrorDialog.create(
            context = this,
            messageRes = R.string.error_loading_quiz,
            onRetry = {
                loadQuizzes()
                loadStats()
            },
            onGoHome = { navigateHome() }
        )
        errorDialog?.show()
    }

    private fun navigateHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun showNoQuizzesDialog() {
        if (isFinishing || isDestroyed) return
        noQuizzesDialog?.dismiss()
        
        val difficultyText = selectedDifficulty?.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() 
        } ?: ""
        
        noQuizzesDialog = MaterialAlertDialogBuilder(this)
            .setTitle("No $difficultyText Quizzes Available")
            .setMessage("There are currently no quizzes available for the $difficultyText difficulty level. Please try selecting a different difficulty or check back later.")
            .setCancelable(false)
            .setPositiveButton("Choose Different Difficulty") { _, _ ->
                // Go back to difficulty selection
                selectedDifficulty = null
                updateFilterButtonStates(null)
                updateUIForDifficultySelection()
                noQuizzesDialog = null
            }
            .create()
        
        // Prevent dialog from being dismissed by any means
        noQuizzesDialog?.setCanceledOnTouchOutside(false)
        noQuizzesDialog?.setOnKeyListener { _, keyCode, _ ->
            // Prevent back button from dismissing
            keyCode == android.view.KeyEvent.KEYCODE_BACK
        }
        noQuizzesDialog?.show()
    }
    
    override fun onDestroy() {
        errorDialog?.dismiss()
        errorDialog = null
        noQuizzesDialog?.dismiss()
        noQuizzesDialog = null
        super.onDestroy()
    }
    
    private fun showQuizzesTutorial() {
        val tutorialPages = loadTutorialPagesFromAsset("quizzes")
        val dialog = TutorialDialog(this, tutorialPages) { dontShowAgain ->
            if (dontShowAgain) {
                UserPrefs.setShowQuizzesTutorial(this, false)
                Toast.makeText(this, "Tutorial won't show again", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }
    
    private fun maybeShowQuizzesTutorial() {
        if (UserPrefs.shouldShowQuizzesTutorial(this)) {
            showQuizzesTutorial()
        }
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
                val iconPath = if (isAssetPath) iconName else null
                android.util.Log.d("QuizzesActivity", "Loading tutorial page: title=${pageObj.getString("title")}, iconName=$iconName, isAssetPath=$isAssetPath, iconPath=$iconPath")
                tutorialPages.add(
                    TutorialPage(
                        iconRes = iconRes,
                        title = pageObj.getString("title"),
                        description = pageObj.getString("description"),
                        iconPath = iconPath
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("QuizzesActivity", "Error loading tutorial", e)
        }
        return tutorialPages
    }
}

