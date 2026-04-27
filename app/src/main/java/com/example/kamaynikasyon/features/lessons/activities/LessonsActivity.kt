package com.example.kamaynikasyon.features.lessons.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kamaynikasyon.MainActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import com.example.kamaynikasyon.core.utils.AnalyticsLogger
import com.example.kamaynikasyon.core.utils.EmptyStateView
import com.example.kamaynikasyon.core.utils.UserPrefs
import com.example.kamaynikasyon.core.utils.AnimationHelper
import com.example.kamaynikasyon.core.utils.startActivityWithTransition
import com.example.kamaynikasyon.core.utils.showLoading
import com.example.kamaynikasyon.core.utils.setTextWithSlideUp
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.databinding.ActivityLessonsSelectionBinding
import com.example.kamaynikasyon.features.lessons.adapters.LessonAdapter
import com.example.kamaynikasyon.features.lessons.data.models.Lesson
import com.example.kamaynikasyon.features.lessons.data.repositories.LessonRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class LessonsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLessonsSelectionBinding
    private lateinit var lessonRepository: LessonRepository
    private lateinit var lessonAdapter: LessonAdapter
    private lateinit var skeletonAdapter: com.example.kamaynikasyon.features.lessons.adapters.LessonSkeletonAdapter
    private var totalLessons: Int = 0
    private var allLessons: List<com.example.kamaynikasyon.features.lessons.data.models.Lesson> = emptyList()
    private var displayedLessons: MutableList<com.example.kamaynikasyon.features.lessons.data.models.Lesson> = mutableListOf()
    private var filteredLessons: List<com.example.kamaynikasyon.features.lessons.data.models.Lesson> = emptyList()
    private val ITEMS_PER_PAGE = 10
    private var isLoadingMore = false
    private var errorDialog: AlertDialog? = null
    private var selectedDifficulty: String? = null
    private var noLessonsDialog: AlertDialog? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonsSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRepository()
        setupRecyclerView()
        setupSwipeRefresh()
        setupDifficultyFilters()
        setupBackPressHandler()
        updateUIForDifficultySelection() // Start with difficulty selection visible
        loadLessons()
        // Tutorial auto popup disabled for now
        // maybeShowLessonsTutorial()
        
        // Log screen view
        AnalyticsLogger.logScreenView("Lessons", "LessonsActivity")
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
        
        // Info button disabled for now
        // binding.btnInfo.setOnClickListener {
        //     showLessonsTutorial()
        // }
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
        if (noLessonsDialog?.isShowing == true) {
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
        lessonRepository = LessonRepository(this)
    }
    
    private fun setupRecyclerView() {
        lessonAdapter = LessonAdapter { lesson ->
            startLesson(lesson)
        }
        
        skeletonAdapter = com.example.kamaynikasyon.features.lessons.adapters.LessonSkeletonAdapter(itemCount = 5)
        
        val layoutManager = LinearLayoutManager(this@LessonsActivity)
        binding.recyclerViewLessons.apply {
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
                        displayedLessons.size < filteredLessons.size) {
                        loadMoreLessons()
                    }
                }
            })
        }
    }
    
    private fun loadMoreLessons() {
        if (isLoadingMore || displayedLessons.size >= filteredLessons.size) return
        
        isLoadingMore = true
        val startIndex = displayedLessons.size
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, filteredLessons.size)
        val newLessons = filteredLessons.subList(startIndex, endIndex)
        
        displayedLessons.addAll(newLessons)
        lessonAdapter.updateLessons(displayedLessons)
        isLoadingMore = false
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadLessons()
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
        
        // If lessons haven't been loaded yet, load them first
        if (allLessons.isEmpty()) {
            loadLessons()
        } else {
            applyFilter()
        }
    }
    
    private fun updateUIForDifficultySelection() {
        // Show difficulty buttons and progress, hide lessons
        binding.difficultyFilterContainer.visibility = View.VISIBLE
        binding.recyclerViewLessons.visibility = View.GONE
        binding.recyclerViewLessons.adapter = null // Clear adapter to prevent skeleton showing
        binding.progressContainer.visibility = View.VISIBLE
        val emptyStateView = findViewById<View>(R.id.empty_state_view)
        emptyStateView?.let { EmptyStateView.hide(it) }
        
        // Update progress to show all lessons (not filtered)
        lifecycleScope.launch {
            val completedIds = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).lessonProgressDao().getCompletedIds()
            }
            updateProgress(completedIds.size, allLessons.size)
        }
    }
    
    
    private fun updateUIForDifficultySelected() {
        // Hide difficulty buttons, show lessons
        binding.difficultyFilterContainer.visibility = View.GONE
        binding.progressContainer.visibility = View.VISIBLE
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
            // No difficulty selected - hide lessons
            binding.recyclerViewLessons.visibility = View.GONE
            val emptyStateView = findViewById<View>(R.id.empty_state_view)
            emptyStateView?.let { EmptyStateView.hide(it) }
            return
        }
        
        // Show skeleton loader while filtering
        binding.recyclerViewLessons.visibility = View.VISIBLE
        binding.recyclerViewLessons.adapter = skeletonAdapter
        AnimationHelper.fadeIn(binding.recyclerViewLessons, 200)
        
        filteredLessons = allLessons.filter { 
            it.difficulty.lowercase() == selectedDifficulty?.lowercase() 
        }
        
        // Reset pagination
        displayedLessons.clear()
        val initialPage = filteredLessons.take(ITEMS_PER_PAGE)
        displayedLessons.addAll(initialPage)
        lessonAdapter.updateLessons(displayedLessons)
        
        // Update progress based on filtered lessons
        lifecycleScope.launch {
            val completedIds = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).lessonProgressDao().getCompletedIds()
            }
            val filteredCompleted = completedIds.filter { id ->
                filteredLessons.any { it.id == id }
            }
            lessonAdapter.updateCompleted(completedIds.toSet())
            updateProgress(filteredCompleted.size, filteredLessons.size)
            
            // Switch to real adapter
            binding.recyclerViewLessons.adapter = lessonAdapter
            
            // Show dialog if no lessons match filter
            if (filteredLessons.isEmpty()) {
                binding.recyclerViewLessons.visibility = View.GONE
                showNoLessonsDialog()
            } else {
                noLessonsDialog?.dismiss()
                noLessonsDialog = null
                val emptyStateView = findViewById<View>(R.id.empty_state_view)
                emptyStateView?.let { EmptyStateView.hide(it) }
                binding.recyclerViewLessons.alpha = 1f
                binding.recyclerViewLessons.visibility = View.VISIBLE
                AnimationHelper.fadeIn(binding.recyclerViewLessons, 300)
            }
        }
    }
    
    private fun loadLessons() {
        // Show skeleton loader instead of progress indicator
        binding.progressLoading.visibility = View.GONE
        val emptyStateView = findViewById<View>(R.id.empty_state_view)
        emptyStateView?.let { EmptyStateView.hide(it) }
        
        // Show skeleton with fade-in animation
        binding.recyclerViewLessons.adapter = skeletonAdapter
        AnimationHelper.fadeIn(binding.recyclerViewLessons, 200)
        
        lifecycleScope.launch {
            try {
                val lessons = withContext(Dispatchers.IO) {
                    lessonRepository.loadLessons()
                }
                totalLessons = lessons.size
                allLessons = lessons
                
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
                AnimationHelper.fadeOut(binding.recyclerViewLessons, 150) {
                    showLoadLessonsErrorDialog()
                }
            }
        }
    }

    private fun showLoadLessonsErrorDialog() {
        if (isFinishing || isDestroyed) return
        errorDialog?.dismiss()
        errorDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_generic)
            .setMessage(R.string.error_loading_lesson)
            .setCancelable(false)
            .setPositiveButton(R.string.action_retry) { dialog, _ ->
                dialog.dismiss()
                loadLessons()
            }
            .setNegativeButton(R.string.action_go_home) { dialog, _ ->
                dialog.dismiss()
                navigateHome()
            }
            .create()
        errorDialog?.show()
    }
    
    private fun navigateHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh completion state when returning from a lesson
        lifecycleScope.launch {
            val completedIds = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).lessonProgressDao().getCompletedIds()
            }
            lessonAdapter.updateCompleted(completedIds.toSet())
            updateProgress(completedIds.size, totalLessons)
        }
    }

    private fun updateProgress(completed: Int, total: Int) {
        binding.lessonsProgress.max = if (total > 0) total else 1
        binding.lessonsProgress.progress = completed.coerceAtMost(total)
        binding.tvLessonsProgressCount.setTextWithSlideUp("$completed/$total")
    }
    
    private fun showNoLessonsDialog() {
        if (isFinishing || isDestroyed) return
        noLessonsDialog?.dismiss()
        
        val difficultyText = selectedDifficulty?.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() 
        } ?: ""
        
        noLessonsDialog = MaterialAlertDialogBuilder(this)
            .setTitle("No $difficultyText Lessons Available")
            .setMessage("There are currently no lessons available for the $difficultyText difficulty level. Please try selecting a different difficulty or check back later.")
            .setCancelable(false)
            .setPositiveButton("Choose Different Difficulty") { _, _ ->
                // Go back to difficulty selection
                selectedDifficulty = null
                updateFilterButtonStates(null)
                updateUIForDifficultySelection()
                noLessonsDialog = null
            }
            .create()
        
        // Prevent dialog from being dismissed by any means
        noLessonsDialog?.setCanceledOnTouchOutside(false)
        noLessonsDialog?.setOnKeyListener { _, keyCode, _ ->
            // Prevent back button from dismissing
            keyCode == android.view.KeyEvent.KEYCODE_BACK
        }
        noLessonsDialog?.show()
    }
    
    override fun onDestroy() {
        errorDialog?.dismiss()
        errorDialog = null
        noLessonsDialog?.dismiss()
        noLessonsDialog = null
        super.onDestroy()
    }
    
    private fun startLesson(lesson: Lesson) {
        val intent = Intent(this, LessonActivity::class.java)
        intent.putExtra(LessonActivity.EXTRA_LESSON_ID, lesson.id)
        startActivityWithTransition(intent)
    }
    
    private fun showLessonsTutorial() {
        val tutorialPages = loadTutorialPagesFromAsset("lessons")
        val dialog = TutorialDialog(this, tutorialPages) { dontShowAgain ->
            if (dontShowAgain) {
                UserPrefs.setShowLessonsTutorial(this, false)
            }
        }
        dialog.show()
    }
    
    private fun maybeShowLessonsTutorial() {
        if (UserPrefs.shouldShowLessonsTutorial(this)) {
            showLessonsTutorial()
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
            android.util.Log.e("LessonsActivity", "Error loading tutorial", e)
        }
        return tutorialPages
    }
    

}

