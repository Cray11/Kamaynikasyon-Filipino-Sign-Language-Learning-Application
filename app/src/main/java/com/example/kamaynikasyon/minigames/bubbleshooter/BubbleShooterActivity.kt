package com.example.kamaynikasyon.minigames.bubbleshooter

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.example.kamaynikasyon.R
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.kamaynikasyon.databinding.ActivityBubbleShooterBinding
import androidx.fragment.app.commit
import com.example.kamaynikasyon.core.CameraDetectionFragment
import com.example.kamaynikasyon.core.TFLiteModelConfig
import com.example.kamaynikasyon.core.ModelConfigFactory
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.nio.charset.Charset
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import android.util.Log
import com.example.kamaynikasyon.core.particles.ParticleSystem
import com.example.kamaynikasyon.core.particles.ParticleEffects
import com.example.kamaynikasyon.core.utils.AnalyticsLogger
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.ui.DataLoadErrorDialog
import com.example.kamaynikasyon.core.utils.CacheManager
import com.example.kamaynikasyon.core.utils.ErrorHandler
import com.example.kamaynikasyon.MainActivity
import android.widget.TextView
import android.widget.Button
import android.view.View

class BubbleShooterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBubbleShooterBinding
    private lateinit var gameView: BubbleShooterView
    private val gameEngine = BubbleShooterGameEngine()
    private val handler = Handler(Looper.getMainLooper())
    private var loop: Runnable? = null
    private var pendingLevel: BubbleShooterLevel? = null
    private var levelStartTimeMs: Long = 0L
    private var gridConfigSet = false
    private var gridRadius: Float = 0f
    private var gridStartX: Float = 0f
    private var gridStartY: Float = 0f
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var hasShownResult = false
    private var errorDialog: AlertDialog? = null
    
    // In-memory cache for level data
    private val levelCache = mutableMapOf<String, String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBubbleShooterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupBackPressedHandler()
        
        // Resolve requested level id (defaults to level1)
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        
        // Show loading indicator if available
        try {
            val progressLoading = binding.root.findViewById<View>(R.id.progress_loading)
            val contentPanel = binding.root.findViewById<View>(R.id.quiz_panel)
            progressLoading?.visibility = View.VISIBLE
            contentPanel?.visibility = View.GONE
        } catch (_: Exception) {
            // Views might not exist, continue anyway
        }
        
        // Embed camera like in Picture Quiz (into dedicated camera_container)
        // Model config will be loaded from level JSON in loadLevel()
        val cameraFragment = CameraDetectionFragment.newInstance(ModelConfigFactory.createFromMappingPath(this, "ml/alphabet_mapping.json"))
        supportFragmentManager.commit {
            replace(R.id.camera_container, cameraFragment)
        }
        supportFragmentManager.executePendingTransactions()
        
        // Load level asynchronously
        loadLevel(levelId)

        // Add game view over the camera container
        val container = binding.contentPlaceholder as FrameLayout
        val overlay = container.findViewById<FrameLayout>(R.id.game_overlay)
        gameView = BubbleShooterView(this)
        overlay.addView(
            gameView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        gameView.bringToFront()

        // Initialize timer UI
        binding.textTimer.text = "00:00"
        binding.textScore.text = "Score: 0"
        
        // Wire game view with engine
        gameView.onGridConfigChanged = { radius, startX, startY ->
            gameEngine.setGridConfig(radius, startX, startY)
            gridConfigSet = true
            gridRadius = radius
            gridStartX = startX
            gridStartY = startY
            // If level is already loaded, initialize the game
            pendingLevel?.let {
                initializeGameWithLevel(it)
            }
        }
        gameView.onBallShot = { tx, ty ->
            val current = gameEngine.getCurrentGameState()
            if (current != null) {
                val sx = gameView.width / 2f
                val sy = gameView.height - (gameView.height * 0.1f)
                gameEngine.shootBall(sx, sy, tx, ty)
            }
        }
        var lastScore = 0
        var lastPoppingBallsKeys = emptySet<Pair<Int, Int>>()
        gameEngine.onGameStateChanged = { state ->
            gameView.setGameState(state)
            // Update score display in real-time
            binding.textScore.text = "Score: ${state.score}"
            
            // Track when new balls are added to poppingBalls
            val currentPoppingKeys = state.poppingBalls.keys.toSet()
            val newlyAddedKeys = currentPoppingKeys - lastPoppingBallsKeys
            
            // Light haptic when score increases (approx. per ball popped)
            if (state.score > lastScore) {
                com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateLight(this)
                lastScore = state.score
            }
            
            // Show particle effects for newly popped balls
            if (newlyAddedKeys.isNotEmpty()) {
                // Convert gameView coordinates to content view coordinates
                val gameViewLocation = IntArray(2)
                gameView.getLocationOnScreen(gameViewLocation)
                val contentView = findViewById<ViewGroup>(android.R.id.content)
                val contentViewLocation = IntArray(2)
                contentView.getLocationOnScreen(contentViewLocation)
                
                // Calculate offset from gameView to contentView
                val offsetX = gameViewLocation[0] - contentViewLocation[0]
                val offsetY = gameViewLocation[1] - contentViewLocation[1]
                
                for (key in newlyAddedKeys) {
                    val ball = state.poppingBalls[key]
                    if (ball != null) {
                        // Convert ball position from gameView coordinates to content view coordinates
                        val particleX = ball.centerX + offsetX
                        val particleY = ball.centerY + offsetY
                        
                        ParticleSystem.playEffect(
                            contentView,
                            ParticleEffects.pop(
                                x = particleX,
                                y = particleY,
                                content = "✨",
                                particleCount = 6,
                                intensity = 0.8f
                            )
                        )
                    }
                }
            }
            
            lastPoppingBallsKeys = currentPoppingKeys
            
            // Check for win/lose conditions
            if (!hasShownResult) {
                if (state.isGameWon) {
                    hasShownResult = true
                    stopTimer()
                    showCompletionDialog(state)
                } else if (state.isGameOver) {
                    hasShownResult = true
                    stopTimer()
                    showLoseDialog(state)
                }
            }
        }

        // Basic listener: forward predictions to engine
        cameraFragment.setOnDetectionListener { resultBundle ->
            runOnUiThread {
                val predicted = (resultBundle.predictedLetter ?: "").trim().uppercase()
                if (predicted.isNotEmpty()) {
                    gameEngine.onGesturePredicted(predicted)
                }
            }
        }

        // Start simple game loop
        loop = object : Runnable {
            override fun run() {
                val dt = 1f / 60f
                gameEngine.updateGame(dt, gameView.width.toFloat(), gameView.height.toFloat())
                handler.postDelayed(this, 16)
            }
        }
        handler.post(loop!!)
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val info = binding.toolbar.findViewById<android.widget.ImageView>(R.id.btn_info)
        info?.setOnClickListener { showTutorial() }
    }
    
    private fun loadLevel(levelId: String) {
        val assetPath = "minigames/bubble_shooter/${levelId}.json"
        
        lifecycleScope.launch {
            try {
                // Try Supabase first, then fall back to assets
                val jsonString = withContext(Dispatchers.IO) {
                    loadLevelData(levelId, "bubble_shooter/$levelId.json", assetPath)
                }
                
                if (jsonString == null) {
                    try {
                        val progressLoading = binding.root.findViewById<View>(R.id.progress_loading)
                        val contentPanel = binding.root.findViewById<View>(R.id.quiz_panel)
                        progressLoading?.visibility = View.GONE
                        contentPanel?.visibility = View.GONE
                    } catch (_: Exception) {}
                    showLevelLoadErrorDialog()
                    return@launch
                }
                
                val json = JSONObject(jsonString)
                
                // Parse level data
                val layoutArr = json.optJSONArray("layout")
                val layout = mutableListOf<String>()
                if (layoutArr != null) {
                    for (i in 0 until layoutArr.length()) {
                        layout.add(layoutArr.optString(i))
                    }
                }
                val lettersObj = json.optJSONObject("ballLetters")
                val lettersMap = mutableMapOf<String, String>()
                if (lettersObj != null) {
                    val keys = lettersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        lettersMap[k] = lettersObj.optString(k)
                    }
                }
                val typesArr = json.optJSONArray("ballTypes")
                val types = mutableListOf<Int>()
                if (typesArr != null) {
                    for (i in 0 until typesArr.length()) {
                        types.add(typesArr.optInt(i))
                    }
                }
                
                pendingLevel = BubbleShooterLevel(
                    id = levelId,
                    layout = layout,
                    ballLetters = lettersMap,
                    ballTypes = types
                )
                
                // Load model config from JSON if specified
                val modelConfigJson = json.opt("modelConfig") ?: json.optJSONObject("level")?.opt("modelConfig")
                val modelConfig: TFLiteModelConfig = ModelConfigFactory.createFromJson(
                    this@BubbleShooterActivity,
                    modelConfigJson
                )
                
                // Update camera fragment with the model config from JSON
                val cameraFrag = supportFragmentManager.findFragmentById(R.id.camera_container) as? CameraDetectionFragment
                cameraFrag?.setModelConfig(modelConfig)
                
                // Hide loading indicator and show content
                try {
                    val progressLoading = binding.root.findViewById<View>(R.id.progress_loading)
                    val contentPanel = binding.root.findViewById<View>(R.id.quiz_panel)
                    progressLoading?.visibility = View.GONE
                    contentPanel?.visibility = View.VISIBLE
                } catch (_: Exception) {
                    // Views might not exist, continue anyway
                }
                
                // If grid config is already set, initialize the game now
                if (gridConfigSet) {
                    initializeGameWithLevel(pendingLevel!!)
                }
            } catch (e: Exception) {
                Log.e("BubbleShooter", "Failed to load $assetPath", e)
                try {
                    val progressLoading = binding.root.findViewById<View>(R.id.progress_loading)
                    val contentPanel = binding.root.findViewById<View>(R.id.quiz_panel)
                    progressLoading?.visibility = View.GONE
                    contentPanel?.visibility = View.GONE
                } catch (_: Exception) {}
                showLevelLoadErrorDialog()
            }
        }
    }
    
    private suspend fun loadLevelData(
        levelId: String,
        levelPath: String,
        assetPath: String
    ): String? = withContext(Dispatchers.IO) {
        // Check in-memory cache first
        levelCache[levelId]?.let {
            Log.d("BubbleShooter", "Loaded level from in-memory cache: $levelId")
            return@withContext it
        }
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(this@BubbleShooterActivity)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = loadFromAssets(assetPath)
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    Log.d("BubbleShooter", "Loaded level from assets (offline assets only mode): $levelId")
                }
                jsonString
            } catch (e: Exception) {
                Log.e("BubbleShooter", "Failed to load level from assets: $levelId", e)
                null
            }
        }
        
        val isOnline = ErrorHandler.isOnline(this@BubbleShooterActivity)
        
        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    this@BubbleShooterActivity,
                    SupabaseConfig.BUCKET_MINIGAMES,
                    levelPath
                )
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    // Cache to persistent storage for offline use
                    CacheManager.cacheData(this@BubbleShooterActivity, "minigames", "bubble_shooter_$levelId", jsonString)
                    Log.d("BubbleShooter", "Loaded level from Supabase and cached: $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("BubbleShooter", "Failed to load level from Supabase: $levelId, trying cache", e)
                // Log critical errors to Crashlytics (but not network errors which are expected)
                if (e !is java.net.UnknownHostException && e !is java.net.ConnectException) {
                    ErrorHandler.logErrorToCrashlytics(
                        e, "Failed to load level from Supabase: $levelId"
                    )
                }
            }
        }
        
        // Try persistent cache (useful when offline)
        if (!isOnline || !SupabaseConfig.isInitialized()) {
            try {
                // Check SupabaseStorage cache first
                val cacheFile = java.io.File(applicationContext.cacheDir, "supabase_cache/${SupabaseConfig.BUCKET_MINIGAMES}/$levelPath")
                if (cacheFile.exists() && cacheFile.isFile) {
                    val jsonString = cacheFile.readText()
                    levelCache[levelId] = jsonString
                    Log.d("BubbleShooter", "Loaded level from SupabaseStorage cache (offline): $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("BubbleShooter", "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            // Try CacheManager cache
            try {
                val cachedData = CacheManager.getCachedData(
                    this@BubbleShooterActivity,
                    "minigames",
                    "bubble_shooter_$levelId",
                    String::class.java
                )
                if (cachedData != null) {
                    levelCache[levelId] = cachedData
                    Log.d("BubbleShooter", "Loaded level from CacheManager (offline): $levelId")
                    return@withContext cachedData
                }
            } catch (e: Exception) {
                Log.w("BubbleShooter", "Failed to load from CacheManager", e)
            }
        }
        
        // Fallback to assets
        return@withContext try {
            val jsonString = loadFromAssets(assetPath)
            if (jsonString != null) {
                levelCache[levelId] = jsonString
                // Cache assets data for future offline use
                CacheManager.cacheData(this@BubbleShooterActivity, "minigames", "bubble_shooter_$levelId", jsonString)
                Log.d("BubbleShooter", "Loaded level from assets and cached: $levelId")
            }
            jsonString
        } catch (e: Exception) {
            Log.e("BubbleShooter", "Failed to load level from assets: $levelId", e)
            null
        }
    }
    
    private fun loadFromAssets(assetPath: String): String? {
        return try {
            String(assets.open(assetPath).use { it.readBytes() }, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            null
        }
    }
    
    private fun showLevelLoadErrorDialog() {
        if (isFinishing || isDestroyed) return
        errorDialog?.dismiss()
        errorDialog = DataLoadErrorDialog.create(
            context = this,
            messageRes = R.string.error_loading_minigame,
            onRetry = { 
                val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
                loadLevel(levelId)
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
    
    /**
     * Initializes the game with the loaded level
     * Called either when grid config changes (if level is already loaded)
     * or when level finishes loading (if grid config is already set)
     */
    private fun initializeGameWithLevel(level: BubbleShooterLevel) {
        if (!gridConfigSet) {
            // Grid config not set yet, wait for onGridConfigChanged
            return
        }
        gameEngine.setGridConfig(gridRadius, gridStartX, gridStartY)
        gameEngine.initializeGame(level)
        levelStartTimeMs = System.currentTimeMillis()
        hasShownResult = false
        startTimer()
    }

    private fun showCompletionDialog(state: GameState) {
        val elapsedSec = ((System.currentTimeMillis() - levelStartTimeMs) / 1000L).toInt().coerceAtLeast(0)
        val ballCount = state.originalBallsCount
        // Recalculate score to ensure it's accurate
        val baseScore = 50 * ballCount
        val penalty = 5 * elapsedSec
        val score = (baseScore - penalty).coerceAtLeast(0)
        
        // Calculate stars based on score
        val threeStarThreshold = 30 * ballCount
        val twoStarThreshold = 15 * ballCount
        val stars = when {
            score >= threeStarThreshold -> 3
            score >= twoStarThreshold -> 2
            else -> 1
        }
        val starsStr = "⭐".repeat(stars) + "☆".repeat(3 - stars)
        
        val timeStr = formatElapsed(elapsedSec)
        
        // Persist progress (best score/stars/time) for this level
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(applicationContext)
                val dao = db.bubbleShooterProgressDao()
                val existing = dao.get(levelId)
                val bestScore = maxOf(existing?.bestScore ?: 0, score)
                val bestStars = maxOf(existing?.bestStars ?: 0, stars)
                val prevBestTime = existing?.bestTimeSeconds ?: Int.MAX_VALUE
                val bestTime = if (prevBestTime <= 0) elapsedSec else minOf(prevBestTime, elapsedSec)
                dao.upsert(
                    com.example.kamaynikasyon.data.database.BubbleShooterProgress(
                        levelId = levelId,
                        bestScore = bestScore,
                        bestStars = bestStars,
                        bestTimeSeconds = bestTime,
                        completed = true
                    )
                )
                // Log analytics
                AnalyticsLogger.logMinigameEvent(
                    event = "completed",
                    gameType = "bubble_shooter",
                    levelId = levelId,
                    score = score,
                    stars = stars
                )
            } catch (e: Exception) {
                Log.e("BubbleShooter", "Error saving progress", e)
            }
        }
        
        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_level_complete, null)
        dialogView.findViewById<TextView>(com.example.kamaynikasyon.R.id.tv_time).text = "Time: $timeStr"
        dialogView.findViewById<TextView>(com.example.kamaynikasyon.R.id.tv_stars).text = "Stars: $starsStr"
        dialogView.findViewById<TextView>(com.example.kamaynikasyon.R.id.tv_score).text = "Score: $score"
        
        // Haptic feedback on success (respects settings)
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateMedium(this)
        
        // Show confetti effect
        binding.root.post {
            val centerX = binding.root.width / 2f
            val centerY = binding.root.height / 2f
            ParticleSystem.playEffect(
                this@BubbleShooterActivity,
                ParticleEffects.confetti(
                    centerX = centerX,
                    centerY = centerY,
                    width = binding.root.width.toFloat(),
                    height = binding.root.height.toFloat()
                )
            )
        }
        
        val alert = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialogView.findViewById<Button>(com.example.kamaynikasyon.R.id.btn_home).setOnClickListener {
            alert.dismiss()
            finish()
        }
        dialogView.findViewById<Button>(com.example.kamaynikasyon.R.id.btn_retry).setOnClickListener {
            alert.dismiss()
            recreate()
        }
        val nextBtn = dialogView.findViewById<Button>(com.example.kamaynikasyon.R.id.btn_next)
        val currentLevelId = (intent.getStringExtra("LEVEL_ID") ?: "level1")
        val nextLevelId = getNextLevelId(currentLevelId)
        val hasNext = nextLevelId != null && levelAssetExists("minigames/bubble_shooter/${nextLevelId}.json")
        if (hasNext && nextLevelId != null) {
            nextBtn.setOnClickListener {
                alert.dismiss()
                // Navigate to selection activity and show next level dialog
                val selectionIntent = Intent(this, BubbleShooterSelectionActivity::class.java)
                selectionIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // Extract level number from nextLevelId (e.g., "level5" -> 5)
                val levelRegex = Regex("level(\\d+)")
                val levelNumber = levelRegex.find(nextLevelId)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                selectionIntent.putExtra("SHOW_LEVEL", levelNumber)
                finish()
                startActivity(selectionIntent)
            }
        } else {
            // Show congratulatory dialog when no more levels
            nextBtn.setOnClickListener {
                alert.dismiss()
                showCongratulationsDialog()
            }
        }
        
        alert.show()
    }

    private fun showLoseDialog(state: GameState) {
        val elapsedSec = ((System.currentTimeMillis() - levelStartTimeMs) / 1000L).toInt().coerceAtLeast(0)
        val score = state.score
        
        // Save progress before showing dialog
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(applicationContext)
                val dao = db.bubbleShooterProgressDao()
                val existing = dao.get(levelId)
                val bestScore = maxOf(existing?.bestScore ?: 0, score)
                dao.upsert(
                    com.example.kamaynikasyon.data.database.BubbleShooterProgress(
                        levelId = levelId,
                        bestScore = bestScore,
                        bestStars = existing?.bestStars ?: 0,
                        bestTimeSeconds = existing?.bestTimeSeconds ?: Int.MAX_VALUE,
                        completed = existing?.completed ?: false
                    )
                )
            } catch (_: Exception) {}
        }
        
        // Heavy haptic on lose
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateHeavy(this)
        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage("A ball reached the red line!\nScore: $score")
            .setPositiveButton("Try Again") { d, _ ->
                d.dismiss()
                // Restart game
                pendingLevel?.let {
                    hasShownResult = false
                    levelStartTimeMs = System.currentTimeMillis()
                    gameEngine.initializeGame(it)
                    startTimer()
                }
            }
            .setNegativeButton("Quit") { d, _ ->
                d.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startTimer() {
        levelStartTimeMs = System.currentTimeMillis()
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsedSec = ((System.currentTimeMillis() - levelStartTimeMs) / 1000L).toInt().coerceAtLeast(0)
                binding.textTimer.text = formatElapsed(elapsedSec)
                timerHandler.postDelayed(this, 500)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun formatElapsed(totalSeconds: Int): String {
        val mm = (totalSeconds / 60).toString().padStart(2, '0')
        val ss = (totalSeconds % 60).toString().padStart(2, '0')
        return "$mm:$ss"
    }

    private fun getNextLevelId(current: String): String? {
        val levelRegex = Regex("level(\\d+)")
        val nextNumber = levelRegex.find(current)?.groupValues?.getOrNull(1)?.toIntOrNull()?.plus(1) ?: return null
        return "level$nextNumber"
    }

    private fun levelAssetExists(path: String): Boolean {
        return try {
            assets.open(path).close(); true
        } catch (e: Exception) {
            false
        }
    }

    private fun showCongratulationsDialog() {
        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_congratulations, null)
        val alert = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(com.example.kamaynikasyon.R.id.btn_home).setOnClickListener {
            alert.dismiss()
            finish()
        }

        alert.show()
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun showExitConfirmationDialog() {
        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_exit_confirmation, null)
        
        // Update text for minigame
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_dialog_title)?.text = "Exit Minigame"
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_dialog_message)?.text = "Are you sure you want to exit? Your progress will be lost."
        
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

    private fun showTutorial() {
        val pages = loadTutorialPagesFromAsset("bubble_shooter")
        val dialog = TutorialDialog(this, pages) { dontShowAgain ->
            // No need to save preference during gameplay, tutorial is always accessible
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
            Log.e("BubbleShooter", "Error loading tutorial", e)
        }
        return tutorialPages
    }

    override fun onDestroy() {
        errorDialog?.dismiss()
        errorDialog = null
        loop?.let { handler.removeCallbacks(it) }
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
        super.onDestroy()
    }
}


