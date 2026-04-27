package com.example.kamaynikasyon.minigames.picturequiz

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kamaynikasyon.MainActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import com.example.kamaynikasyon.core.utils.UserPrefs
import com.example.kamaynikasyon.data.database.AppDatabase
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.utils.CacheManager
import com.example.kamaynikasyon.core.utils.ErrorHandler
import com.example.kamaynikasyon.core.ui.DataLoadErrorDialog
import com.example.kamaynikasyon.core.utils.AnimationHelper
import com.example.kamaynikasyon.core.utils.setTextWithSlideUp
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import android.view.View
import com.example.kamaynikasyon.minigames.PictureQuizLevelAdapter
import com.example.kamaynikasyon.minigames.PictureQuizLevelItem

class PictureQuizSelectionActivity : AppCompatActivity() {
    
    private var errorDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minigame_selection)

        setupToolbar()
        setupSwipeRefresh()
        setupLevelsRecycler()
        loadStats()
        maybeShowPictureQuizTutorial()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
        setupLevelsRecycler() // Refresh levels to update unlock status after completing a level
        
        // Check if we should show a level info dialog automatically
        val levelToShow = intent.getIntExtra("SHOW_LEVEL", -1)
        if (levelToShow > 0) {
            // Clear the intent extra so it doesn't show again
            intent.removeExtra("SHOW_LEVEL")
            // Show the level info dialog after a short delay to ensure the activity is fully loaded
            lifecycleScope.launch {
                kotlinx.coroutines.delay(300)
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val progress = withContext(Dispatchers.IO) { 
                        db.pictureQuizProgressDao().get("level$levelToShow") 
                    }
                    showLevelInfoDialog(levelToShow, "picture_quiz", progress)
                } catch (_: Exception) {
                    showLevelInfoDialog(levelToShow, "picture_quiz", null)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { setIntent(it) }
    }

    private fun setupSwipeRefresh() {
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            setupLevelsRecycler()
            loadStats()
        }
        swipeRefresh.setColorSchemeResources(
            R.color.primary_color,
            R.color.secondary_color
        )
    }

    private fun setupLevelsRecycler() {
        val recycler = findViewById<RecyclerView>(R.id.recycler_levels)
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        recycler.layoutManager = GridLayoutManager(this, 3)
        
        // Show skeleton loader instead of progress indicator
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_loading)
        progressBar?.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        
        val skeletonAdapter = LevelSkeletonAdapter(itemCount = 9)
        recycler.adapter = skeletonAdapter
        // Fade in skeleton loader
        AnimationHelper.fadeIn(recycler, 200)

        val indexPath = "minigames/picture_quiz/index.json"
        // Theme color for picture quiz (matches stats card)
        val themeColor = android.graphics.Color.parseColor("#2DC046")
        val items = mutableListOf<PictureQuizLevelItem>()
        
        lifecycleScope.launch {
            try {
                // Try Supabase first, then fall back to assets
                val jsonString = withContext(Dispatchers.IO) {
                    loadIndexData(indexPath)
                }
                
                if (jsonString != null) {
                    val root = JSONObject(jsonString)
                    val levels = root.getJSONArray("levels")

                    for (i in 0 until levels.length()) {
                        val elem = levels.getJSONObject(i)
                        val fileName = elem.optString("file", "level${i + 1}.json")
                        // Extract level number from filename (remove .json extension and extract number)
                        val levelId = fileName.removeSuffix(".json")
                        val levelNumber = Regex("(\\d+)").find(levelId)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (i + 1)
                        val title = elem.optString("title", "Level $levelNumber")
                        // Initial unlock status will be updated from database
                        val isUnlocked = levelNumber == 1 // Only level 1 is initially unlocked
                        val score = 0
                        val stars = 0
                        items.add(
                            PictureQuizLevelItem(
                                levelNumber,
                                title,
                                isUnlocked,
                                score,
                                stars
                            )
                        )
                    }
                } else {
                    handleLevelsLoadError(recycler, swipeRefresh)
                    return@launch
                }
            } catch (e: Exception) {
                handleLevelsLoadError(recycler, swipeRefresh)
                return@launch
            }

            // Set a temporary adapter immediately for responsiveness (sorted by level number)
            val sortedItemsForAdapter = items.sortedBy { it.levelNumber }
            recycler.adapter = PictureQuizLevelAdapter(sortedItemsForAdapter, { levelNumber ->
                lifecycleScope.launch {
                    try {
                        val db = AppDatabase.getInstance(applicationContext)
                        val progress = withContext(Dispatchers.IO) {
                            db.pictureQuizProgressDao().get("level$levelNumber")
                        }
                        showLevelInfoDialog(levelNumber, "picture_quiz", progress)
                    } catch (_: Exception) {
                        showLevelInfoDialog(levelNumber, "picture_quiz", null)
                    }
                }
            }, themeColor)

            // Load saved scores and update list with unlock status
            try {
                val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(applicationContext)
                val rows = withContext(Dispatchers.IO) { db.pictureQuizProgressDao().getAll() }
                val byLevel = rows.associateBy { it.levelId }
                val completedLevels = rows.filter { it.completed }.map { it.levelId }.toSet()
                
                // Sort items by level number to ensure proper unlock logic
                val sortedItems = items.sortedBy { it.levelNumber }
                
                val updated = sortedItems.map { item ->
                    val key = "level${item.levelNumber}"
                    val best = byLevel[key]
                    
                    // Calculate unlock status:
                    // Level 1 is always unlocked
                    // Level N is unlocked if Level N-1 is completed
                    val isUnlocked = if (item.levelNumber == 1) {
                        true
                    } else {
                        val previousKey = "level${item.levelNumber - 1}"
                        completedLevels.contains(previousKey)
                    }
                    
                    item.copy(
                        score = best?.bestScore ?: item.score,
                        stars = best?.bestStars ?: 0,
                        isUnlocked = isUnlocked
                    )
                }
                recycler.adapter = PictureQuizLevelAdapter(updated, { levelNumber ->
                    lifecycleScope.launch {
                        showLevelInfoDialog(
                            levelNumber,
                            "picture_quiz",
                            byLevel["level$levelNumber"]
                        )
                    }
                }, themeColor)
            } catch (_: Exception) {
                // On error, use sorted items
                val sortedItemsForError = items.sortedBy { it.levelNumber }
                recycler.adapter = PictureQuizLevelAdapter(sortedItemsForError, { levelNumber ->
                    lifecycleScope.launch {
                        try {
                            val db = AppDatabase.getInstance(applicationContext)
                            val progress = withContext(Dispatchers.IO) {
                                db.pictureQuizProgressDao().get("level$levelNumber")
                            }
                            showLevelInfoDialog(levelNumber, "picture_quiz", progress)
                        } catch (_: Exception) {
                            showLevelInfoDialog(levelNumber, "picture_quiz", null)
                        }
                    }
                }, themeColor)
            }
            
            // Hide loading indicator and show content
            findViewById<android.widget.ProgressBar>(R.id.progress_loading)?.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            // Ensure content is visible (already visible from skeleton, just ensure alpha is 1)
            recycler.alpha = 1f
            swipeRefresh.isRefreshing = false
        }
    }
    
    private suspend fun loadIndexData(assetPath: String): String? = withContext(Dispatchers.IO) {
        val indexPath = "picture_quiz/index.json"
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(this@PictureQuizSelectionActivity)
        if (useOfflineAssetsOnly) {
            return@withContext try {
                val jsonString = loadFromAssets(assetPath)
                Log.d("PictureQuizSelection", "Loaded index from assets (offline assets only mode)")
                jsonString
            } catch (e: Exception) {
                Log.e("PictureQuizSelection", "Failed to load index from assets", e)
                null
            }
        }
        
        val isOnline = ErrorHandler.isOnline(this@PictureQuizSelectionActivity)
        
        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    this@PictureQuizSelectionActivity,
                    SupabaseConfig.BUCKET_MINIGAMES,
                    indexPath
                )
                if (jsonString != null) {
                    // Cache to persistent storage for offline use
                    CacheManager.cacheData(this@PictureQuizSelectionActivity, "minigames", "picture_quiz_index", jsonString)
                    Log.d("PictureQuizSelection", "Loaded index from Supabase and cached")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("PictureQuizSelection", "Failed to load index from Supabase, trying cache", e)
            }
        }
        
        // Try persistent cache (useful when offline)
        if (!isOnline || !SupabaseConfig.isInitialized()) {
            try {
                // Check SupabaseStorage cache first
                val cacheFile = java.io.File(applicationContext.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_MINIGAMES}/$indexPath")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    Log.d("PictureQuizSelection", "Loaded index from SupabaseStorage cache (offline)")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("PictureQuizSelection", "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            // Try CacheManager cache
            try {
                val cachedData = CacheManager.getCachedData(
                    this@PictureQuizSelectionActivity,
                    "minigames",
                    "picture_quiz_index",
                    String::class.java
                )
                if (cachedData != null) {
                    Log.d("PictureQuizSelection", "Loaded index from CacheManager (offline)")
                    return@withContext cachedData
                }
            } catch (e: Exception) {
                Log.w("PictureQuizSelection", "Failed to load from CacheManager", e)
            }
        }
        
        // Fallback to assets
        return@withContext try {
            val jsonString = loadFromAssets(assetPath)
            if (jsonString != null) {
                // Cache assets data for future offline use
                CacheManager.cacheData(this@PictureQuizSelectionActivity, "minigames", "picture_quiz_index", jsonString)
                Log.d("PictureQuizSelection", "Loaded index from assets and cached")
            }
            jsonString
        } catch (e: Exception) {
            Log.e("PictureQuizSelection", "Failed to load index from assets", e)
            null
        }
    }
    
    private suspend fun loadLevelDataForDialog(levelId: String, assetPath: String): String? = withContext(Dispatchers.IO) {
        val levelPath = "picture_quiz/$levelId.json"
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(this@PictureQuizSelectionActivity)
        if (useOfflineAssetsOnly) {
            return@withContext loadFromAssets(assetPath)
        }
        
        val isOnline = ErrorHandler.isOnline(this@PictureQuizSelectionActivity)
        
        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    this@PictureQuizSelectionActivity,
                    SupabaseConfig.BUCKET_MINIGAMES,
                    levelPath
                )
                if (jsonString != null) {
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("PictureQuizSelection", "Failed to load level from Supabase: $levelId, trying cache", e)
            }
        }
        
        // Try persistent cache (useful when offline)
        if (!isOnline || !SupabaseConfig.isInitialized()) {
            try {
                val cacheFile = java.io.File(applicationContext.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_MINIGAMES}/$levelPath")
                if (cacheFile.exists() && cacheFile.isFile) {
                    return@withContext cacheFile.readText()
                }
            } catch (e: Exception) {
                // Continue to next fallback
            }
            
            try {
                val cachedData = CacheManager.getCachedData(
                    this@PictureQuizSelectionActivity,
                    "minigames",
                    "picture_quiz_$levelId",
                    String::class.java
                )
                if (cachedData != null) {
                    return@withContext cachedData
                }
            } catch (e: Exception) {
                // Continue to assets
            }
        }
        
        // Fallback to assets
        return@withContext loadFromAssets(assetPath)
    }
    
    private fun loadFromAssets(assetPath: String): String? {
        return try {
            String(assets.open(assetPath).use { it.readBytes() }, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            null
        }
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(applicationContext)
                val progress = withContext(Dispatchers.IO) { db.pictureQuizProgressDao().getAll() }
                
                // Calculate stats
                val totalScore = progress.sumOf { it.bestScore }
                val totalStars = progress.sumOf { it.bestStars }
                val levelsCompleted = progress.count { it.completed }
                
                // Get total levels from index.json
                var totalLevels = progress.size
                try {
                    val indexPath = "minigames/picture_quiz/index.json"
                    val jsonString = withContext(Dispatchers.IO) {
                        if (SupabaseConfig.isInitialized()) {
                            SupabaseStorage.downloadTextFile(
                                this@PictureQuizSelectionActivity,
                                SupabaseConfig.BUCKET_MINIGAMES,
                                "picture_quiz/index.json"
                            ) ?: loadFromAssets(indexPath)
                        } else {
                            loadFromAssets(indexPath)
                        }
                    }
                    if (jsonString != null) {
                        val root = JSONObject(jsonString)
                        val levels = root.getJSONArray("levels")
                        totalLevels = maxOf(totalLevels, levels.length())
                    }
                } catch (_: Exception) {
                    // Use progress size as fallback
                }
                
                // Update UI with animations
                findViewById<TextView>(R.id.tv_minigame_score)?.setTextWithSlideUp("🏆 Score: $totalScore")
                findViewById<TextView>(R.id.tv_minigame_stars)?.setTextWithSlideUp("⭐ Stars: $totalStars")
                findViewById<TextView>(R.id.tv_minigame_progress_count)?.setTextWithSlideUp("$levelsCompleted/$totalLevels")
                findViewById<android.widget.ProgressBar>(R.id.minigame_progress)?.apply {
                    max = if (totalLevels > 0) totalLevels else 1
                    this.progress = levelsCompleted.coerceAtMost(totalLevels)
                }
            } catch (e: Exception) {
                android.util.Log.e("PictureQuizSelection", "Error loading stats", e)
            }
        }
    }

    private fun handleLevelsLoadError(
        recycler: RecyclerView,
        swipeRefresh: SwipeRefreshLayout
    ) {
        findViewById<android.widget.ProgressBar>(R.id.progress_loading)?.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        AnimationHelper.fadeOut(recycler, 150) {
            recycler.visibility = View.GONE
            showMinigameLoadErrorDialog()
        }
    }

    private fun showMinigameLoadErrorDialog() {
        if (isFinishing || isDestroyed) return
        errorDialog?.dismiss()
        errorDialog = DataLoadErrorDialog.create(
            context = this,
            messageRes = R.string.error_loading_minigame,
            onRetry = { setupLevelsRecycler() },
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

    override fun onDestroy() {
        errorDialog?.dismiss()
        errorDialog = null
        super.onDestroy()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val info = toolbar.findViewById<android.widget.ImageView>(R.id.btn_info)
        info.setOnClickListener { showTutorial() }
        
        // Update stats card stroke color to match Picture Quiz theme
        val themeColor = android.graphics.Color.parseColor("#2DC046")
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.stats_card)?.setStrokeColor(themeColor)
        // Update progress bar and text color
        findViewById<android.widget.ProgressBar>(R.id.minigame_progress)?.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)
        findViewById<android.widget.TextView>(R.id.tv_minigame_progress_count)?.setTextColor(themeColor)
    }

    private fun maybeShowPictureQuizTutorial() {
        if (com.example.kamaynikasyon.core.utils.UserPrefs.shouldShowPictureQuizTutorial(this)) {
            showTutorial()
        }
    }

    private fun showTutorial() {
        val pages = loadTutorialPagesFromAsset("picture_quiz")
        val dialog = TutorialDialog(this, pages) { dontShowAgain ->
            if (dontShowAgain) {
                UserPrefs.setShowPictureQuizTutorial(this, false)
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
            android.util.Log.e("PictureQuizSelection", "Error loading tutorial", e)
            }
        return tutorialPages
    }

    private suspend fun showLevelInfoDialog(
        levelNumber: Int,
        minigameType: String,
        progress: com.example.kamaynikasyon.data.database.PictureQuizProgress?
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_level_info, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_title)
        val tvProgressInfo = dialogView.findViewById<TextView>(R.id.tv_progress_info)
        val tvStarConditions = dialogView.findViewById<TextView>(R.id.tv_star_conditions)
        val btnStart = dialogView.findViewById<Button>(R.id.btn_start)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

        tvTitle.text = "Level $levelNumber"

        // Load level data to calculate star conditions before showing dialog
        val levelId = "level$levelNumber"
        val levelPath = "minigames/picture_quiz/$levelId.json"
        var lettersCount = 0
        
        // Load level data synchronously to get lettersCount before showing dialog
        try {
            val jsonString = withContext(Dispatchers.IO) {
                loadLevelDataForDialog(levelId, levelPath)
            }
            if (jsonString != null) {
                val json = org.json.JSONObject(jsonString)
                val correctAnswer = json.optString("correctAnswer", "").takeIf { it.isNotBlank() }
                    ?: json.optJSONArray("questions")?.optJSONObject(0)?.optString("word")?.takeIf { it.isNotBlank() }
                    ?: json.optJSONObject("level")?.optString("description")?.takeIf { it.isNotBlank() }?.filter { it.isLetter() || it == ' ' }?.uppercase()
                    ?: ""
                lettersCount = correctAnswer.count { it != ' ' }
            }
        } catch (_: Exception) {
            // lettersCount remains 0, will use fallback
        }

        // Build progress info
        val progressText = if (progress != null) {
            val starsStr = "⭐".repeat(progress.bestStars) + "☆".repeat(3 - progress.bestStars)
            val timeStr = formatTime(progress.bestTimeSeconds)
            "Best Score: ${progress.bestScore}\nBest Time: $timeStr\nBest Stars: $starsStr\nStatus: ${if (progress.completed) "Completed" else "In Progress"}"
        } else {
            "Not Started"
        }
        tvProgressInfo.text = progressText

        // Build star conditions with actual numbers
        if (lettersCount > 0) {
            val threeStarThreshold = 65 * lettersCount
            val twoStarThreshold = 35 * lettersCount
            val starConditions = "⭐⭐⭐: Score ≥ $threeStarThreshold\n⭐⭐: Score ≥ $twoStarThreshold\n⭐: Score < $twoStarThreshold"
            tvStarConditions.text = starConditions
        } else {
            // Fallback if we can't determine letters count - show formula instead of generic text
            tvStarConditions.text = "⭐⭐⭐: Score ≥ 65 per letter\n⭐⭐: Score ≥ 35 per letter\n⭐: Score < 35 per letter"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnStart.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, PictureQuizActivity::class.java)
            intent.putExtra("LEVEL_ID", levelId)
            startActivity(intent)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatTime(seconds: Int): String {
        val mm = (seconds / 60).toString().padStart(2, '0')
        val ss = (seconds % 60).toString().padStart(2, '0')
        return "$mm:$ss"
    }
}


