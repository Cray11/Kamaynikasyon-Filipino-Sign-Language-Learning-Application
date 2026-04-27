package com.example.kamaynikasyon.minigames.spellingsequence

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.kamaynikasyon.databinding.ActivitySpellingSequenceBinding
import androidx.fragment.app.commit
import com.example.kamaynikasyon.core.CameraDetectionFragment
import com.example.kamaynikasyon.core.TFLiteModelConfig
import com.example.kamaynikasyon.core.ModelConfigFactory
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.nio.charset.Charset
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import com.example.kamaynikasyon.R
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
import android.graphics.Color
import android.view.View

class SpellingSequenceActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySpellingSequenceBinding

    private var correctAnswer: String = ""
    private var currentSlotIndex: Int = -1
    private val successfulSlots = mutableSetOf<Int>()

    // Verification logic
    private var isVerifying: Boolean = false
    private var verificationStartTime: Long = 0L
    private val verificationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var verificationRunnable: Runnable? = null
    private val VERIFICATION_DURATION = 1500L
    private var currentVerifyingLetter: String? = null

    // Timer
    private var levelStartTimeMs: Long = 0L
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    // Camera fragment reference
    private var cameraFragment: CameraDetectionFragment? = null
    
    // Error dialog
    private var errorDialog: AlertDialog? = null
    
    // In-memory cache for level data
    private val levelCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpellingSequenceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBackPressedHandler()

        // Initial UI
        binding.textTimer.text = "00:00"
        binding.textScore.text = ""

        // Load level
        loadLevelJson()

        // Start timer
        startTimer()

        // Embed camera detection (model config will be loaded from level JSON)
        cameraFragment = CameraDetectionFragment.newInstance(ModelConfigFactory.createFromMappingPath(this, "ml/alphabet_mapping.json"))
        supportFragmentManager.commit {
            replace(binding.contentPlaceholder.id, cameraFragment!!)
        }
        supportFragmentManager.executePendingTransactions()

        // Listen to detection results and verify sequentially
        cameraFragment?.setOnDetectionListener { resultBundle ->
            runOnUiThread {
                val rawPrediction = (resultBundle.predictedLetter ?: "").trim().uppercase()
                val confidence = resultBundle.tfLiteResult ?: 0f
                val confidencePercentage = (confidence * 100).toInt()

                val targetIndex = currentSlotIndex
                if (targetIndex < 0) return@runOnUiThread

                val targetLetter = getSlotAt(targetIndex)?.text?.toString()?.uppercase()

                // Extract a single A-Z letter from prediction
                val predicted = when {
                    rawPrediction.length == 1 && rawPrediction[0].isLetter() -> rawPrediction
                    else -> rawPrediction.firstOrNull { it.isLetter() }?.toString()?.uppercase()
                }

                Log.d("SpellingSequence", "Pred=$predicted conf=$confidence target=$targetLetter idx=$targetIndex")

                // Update prediction display with format "Predicting: A (100%)"
                if (predicted != null) {
                    cameraFragment?.updatePredictionWithConfidence(predicted, confidencePercentage)
                } else {
                    cameraFragment?.updatePredictionText("Predicting: -")
                }

                if (predicted != null && targetLetter != null && predicted == targetLetter && confidence > 0.6f) {
                    if (!isVerifying || currentVerifyingLetter != predicted) {
                        stopVerification()
                        currentVerifyingLetter = predicted
                        cameraFragment?.updateStatus("Status: Verifying")
                        startVerification {
                            // Light haptic on each verified letter
                            com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateLight(this@SpellingSequenceActivity)
                            markSlotSuccess(targetIndex)
                            
                            // Show "Good Job" particle effect
                            binding.root.post {
                                val centerX = binding.root.width / 2f
                                val centerY = binding.root.height * 0.85f // Bottom middle of screen
                                ParticleSystem.playEffect(
                                    this@SpellingSequenceActivity,
                                    ParticleEffects.textPopUp(
                                        text = "Good Job",
                                        x = centerX,
                                        y = centerY,
                                        textSize = 48f,
                                        color = Color.GREEN
                                    )
                                )
                            }
                            
                            advanceToNextSlot()
                        }
                    }
                } else {
                    val statusText = if (targetLetter != null) "Status: Show the letter $targetLetter" else "Status: -"
                    cameraFragment?.updateStatus(statusText)
                    stopVerification()
                }
            }
        }
    }

    private fun loadLevelJson() {
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        // Load directly by id from index mapping; each level file is named <id>.json
        val assetPath = "minigames/spelling_sequence/$levelId.json"
        
        // Show loading indicator if available
        try {
            val progressLoading = binding.root.findViewById<View>(R.id.progress_loading)
            val contentPanel = binding.root.findViewById<View>(R.id.quiz_panel)
            progressLoading?.visibility = View.VISIBLE
            contentPanel?.visibility = View.GONE
        } catch (_: Exception) {
            // Views might not exist, continue anyway
        }
        
        lifecycleScope.launch {
            try {
                // Try Supabase first, then fall back to assets
                val jsonString = withContext(Dispatchers.IO) {
                    loadLevelData(levelId, "spelling_sequence/$levelId.json", assetPath)
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
                
                val root = JSONObject(jsonString)

                // Prefer root-level fields
                val rootTitle = root.optString("title", "")
                if (rootTitle.isNotEmpty()) {
                    binding.textLevelTitle.text = rootTitle
                } else {
                    val levelObj = root.optJSONObject("level")
                    val levelTitle = levelObj?.optString("title", "") ?: ""
                    if (levelTitle.isNotEmpty()) {
                        binding.textLevelTitle.text = levelTitle
                    } else {
                        binding.textLevelTitle.text = "Spelling Sequence"
                    }
                }

                correctAnswer = root.optString("correctAnswer").takeIf { it.isNotBlank() }
                    ?: root.optJSONArray("questions")?.optJSONObject(0)?.optString("word")?.takeIf { it.isNotBlank() }
                    ?: root.optJSONObject("level")?.optString("description")?.takeIf { it.isNotBlank() }?.filter { it.isLetter() || it == ' ' }?.uppercase()
                    ?: "HI"

                val hint = (root.optString("hints").takeIf { it.isNotBlank() }
                    ?: root.optJSONArray("questions")?.optJSONObject(0)?.optString("hints")?.takeIf { it.isNotBlank() }
                    ?: "")
                binding.questionText.text = hint

                // Load model config from JSON if specified
                val modelConfigJson = root.opt("modelConfig")
                    ?: root.optJSONObject("level")?.opt("modelConfig")
                    ?: root.optJSONArray("questions")?.optJSONObject(0)?.opt("modelConfig")
                
                val modelConfig: TFLiteModelConfig = ModelConfigFactory.createFromJson(
                    this@SpellingSequenceActivity,
                    modelConfigJson
                )
                
                // Update camera fragment with the model config from JSON
                cameraFragment?.setModelConfig(modelConfig)

                createLetterSlotsPrefilled(correctAnswer)
                
                // Hide loading indicator and show content
                try {
                    val progressLoading = binding.root.findViewById<View>(R.id.progress_loading)
                    val contentPanel = binding.root.findViewById<View>(R.id.quiz_panel)
                    progressLoading?.visibility = View.GONE
                    contentPanel?.visibility = View.VISIBLE
                } catch (_: Exception) {
                    // Views might not exist, continue anyway
                }
            } catch (e: Exception) {
                Log.e("SpellingSequence", "Failed to load $assetPath", e)
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
            Log.d("SpellingSequence", "Loaded level from in-memory cache: $levelId")
            return@withContext it
        }
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(this@SpellingSequenceActivity)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = loadFromAssets(assetPath)
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    Log.d("SpellingSequence", "Loaded level from assets (offline assets only mode): $levelId")
                }
                jsonString
            } catch (e: Exception) {
                Log.e("SpellingSequence", "Failed to load level from assets: $levelId", e)
                null
            }
        }
        
        val isOnline = ErrorHandler.isOnline(this@SpellingSequenceActivity)
        
        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    this@SpellingSequenceActivity,
                    SupabaseConfig.BUCKET_MINIGAMES,
                    levelPath
                )
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    // Cache to persistent storage for offline use
                    CacheManager.cacheData(this@SpellingSequenceActivity, "minigames", "spelling_sequence_$levelId", jsonString)
                    Log.d("SpellingSequence", "Loaded level from Supabase and cached: $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("SpellingSequence", "Failed to load level from Supabase: $levelId, trying cache", e)
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
                    Log.d("SpellingSequence", "Loaded level from SupabaseStorage cache (offline): $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("SpellingSequence", "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            // Try CacheManager cache
            try {
                val cachedData = CacheManager.getCachedData(
                    this@SpellingSequenceActivity,
                    "minigames",
                    "spelling_sequence_$levelId",
                    String::class.java
                )
                if (cachedData != null) {
                    levelCache[levelId] = cachedData
                    Log.d("SpellingSequence", "Loaded level from CacheManager (offline): $levelId")
                    return@withContext cachedData
                }
            } catch (e: Exception) {
                Log.w("SpellingSequence", "Failed to load from CacheManager", e)
            }
        }
        
        // Fallback to assets
        return@withContext try {
            val jsonString = loadFromAssets(assetPath)
            if (jsonString != null) {
                levelCache[levelId] = jsonString
                // Cache assets data for future offline use
                CacheManager.cacheData(this@SpellingSequenceActivity, "minigames", "spelling_sequence_$levelId", jsonString)
                Log.d("SpellingSequence", "Loaded level from assets and cached: $levelId")
            }
            jsonString
        } catch (e: Exception) {
            Log.e("SpellingSequence", "Failed to load level from assets: $levelId", e)
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
            onRetry = { loadLevelJson() },
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

    private fun createLetterSlotsPrefilled(answer: String) {
        binding.containerLetterSlots.removeAllViews()
        successfulSlots.clear()
        // Build rows similar to PictureQuiz but prefilled and non-clickable
        val density = resources.displayMetrics.density
        val slotSizePx = (48f * density).toInt() // fixed square size
        val horizontalMargin = (2f * density).toInt()
        val slotTotalWidth = slotSizePx + horizontalMargin * 2

        binding.containerLetterSlots.post {
            val containerWidth = binding.containerLetterSlots.width.coerceAtLeast(1)
            var currentRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            binding.containerLetterSlots.addView(currentRow)
            var usedWidth = 0
            var slotIndex = 0

            var i = 0
            while (i < answer.length) {
                val ch = answer[i]
                if (ch == ' ') {
                    if (usedWidth > 0) {
                        currentRow = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        }
                        binding.containerLetterSlots.addView(currentRow)
                        usedWidth = 0
                    }
                    i++
                    continue
                }

                if (usedWidth > 0 && usedWidth + slotTotalWidth > containerWidth) {
                    currentRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    binding.containerLetterSlots.addView(currentRow)
                    usedWidth = 0
                }

                val slot = buildPrefilledSlotTextView(slotIndex, answer[slotIndex].toString().uppercase())
                currentRow.addView(slot)
                slotIndex++
                usedWidth += slotTotalWidth
                i++
            }

            // Auto-select the first slot
            currentSlotIndex = findNextPendingSlot(0)
            highlightCurrentSlot()
        }
    }

    private fun buildPrefilledSlotTextView(index: Int, letter: String): TextView {
        val tv = TextView(this)
        tv.text = letter
        tv.textSize = 20f
        val density = resources.displayMetrics.density
        val sizePx = (36f * density).toInt()
        val padV = (2f * density).toInt()
        val padH = (padV / 2).coerceAtLeast(0)
        tv.setPadding(padH, padV, padH, padV)
        tv.setTextColor(resources.getColor(com.example.kamaynikasyon.R.color.color_black, null))
        tv.gravity = android.view.Gravity.CENTER
        try {
            val tf = androidx.core.content.res.ResourcesCompat.getFont(this, com.example.kamaynikasyon.R.font.fredoka)
            if (tf != null) tv.typeface = tf
        } catch (_: Exception) {}
        tv.includeFontPadding = false
        tv.setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_background)
        val params = LinearLayout.LayoutParams(sizePx, sizePx)
        val m = (2f * density).toInt()
        val v = (2f * density).toInt()
        params.setMargins(m, v, m, v)
        tv.layoutParams = params
        tv.tag = index
        tv.isClickable = false
        tv.isFocusable = false
        return tv
    }

    private fun highlightCurrentSlot() {
        // Reset all to default background
        forEachSlot { idx, view ->
            if (successfulSlots.contains(idx)) {
                view.setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_success_background)
            } else {
                view.setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_background)
            }
            view.setTextColor(resources.getColor(com.example.kamaynikasyon.R.color.color_black, null))
        }
        if (currentSlotIndex >= 0) {
            getSlotAt(currentSlotIndex)?.setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_selected_background)
        }
    }

    private fun markSlotSuccess(index: Int) {
        getSlotAt(index)?.apply {
            setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_success_background)
        }
        successfulSlots.add(index)
    }

    private fun advanceToNextSlot() {
        val next = findNextPendingSlot(startFrom = currentSlotIndex + 1)
        if (next >= 0) {
            currentSlotIndex = next
            highlightCurrentSlot()
        } else {
            currentSlotIndex = -1
            showCompletionDialog()
        }
    }

    private fun findNextPendingSlot(startFrom: Int = 0): Int {
        var logicalIndex = 0
        for (i in 0 until binding.containerLetterSlots.childCount) {
            val row = binding.containerLetterSlots.getChildAt(i)
            if (row is LinearLayout) {
                for (j in 0 until row.childCount) {
                    val child = row.getChildAt(j)
                    if (child is TextView) {
                        if (logicalIndex >= startFrom && !successfulSlots.contains(logicalIndex)) return logicalIndex
                        logicalIndex++
                    }
                }
            } else if (row is TextView) {
                if (logicalIndex >= startFrom && !successfulSlots.contains(logicalIndex)) return logicalIndex
                logicalIndex++
            }
        }
        return -1
    }

    private fun getSlotAt(index: Int): TextView? {
        var logicalIndex = 0
        for (i in 0 until binding.containerLetterSlots.childCount) {
            val row = binding.containerLetterSlots.getChildAt(i)
            if (row is LinearLayout) {
                for (j in 0 until row.childCount) {
                    val child = row.getChildAt(j)
                    if (child is TextView) {
                        if (logicalIndex == index) return child
                        logicalIndex++
                    }
                }
            } else if (row is TextView) {
                if (logicalIndex == index) return row
                logicalIndex++
            }
        }
        return null
    }

    private inline fun forEachSlot(block: (Int, TextView) -> Unit) {
        var logicalIndex = 0
        for (i in 0 until binding.containerLetterSlots.childCount) {
            val row = binding.containerLetterSlots.getChildAt(i)
            if (row is LinearLayout) {
                for (j in 0 until row.childCount) {
                    val child = row.getChildAt(j)
                    if (child is TextView) {
                        block(logicalIndex, child)
                        logicalIndex++
                    }
                }
            } else if (row is TextView) {
                block(logicalIndex, row)
                logicalIndex++
            }
        }
    }

    private fun showCompletionDialog() {
        stopVerification()
        stopTimer()

        val elapsedSec = ((System.currentTimeMillis() - levelStartTimeMs) / 1000L).toInt().coerceAtLeast(0)
        val lettersCount = correctAnswer.count { it != ' ' }
        val score = computeScore(elapsedSec)
        val threeStarThreshold = 65 * lettersCount
        val twoStarThreshold = 35 * lettersCount
        val stars = when {
            score >= threeStarThreshold -> 3
            score >= twoStarThreshold -> 2
            else -> 1
        }
        val starsStr = "⭐".repeat(stars) + "☆".repeat(3 - stars)
        binding.textScore.text = "Score: $score"

        val timeStr = formatElapsed(elapsedSec)

        // Save progress to Spelling Sequence table
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(applicationContext)
                val dao = db.spellingSequenceProgressDao()
                val existing = dao.get(levelId)
                val bestScore = maxOf(existing?.bestScore ?: 0, score)
                val bestStars = maxOf(existing?.bestStars ?: 0, stars)
                val prevBestTime = existing?.bestTimeSeconds ?: Int.MAX_VALUE
                val bestTime = if (prevBestTime <= 0) elapsedSec else minOf(prevBestTime, elapsedSec)
                dao.upsert(
                    com.example.kamaynikasyon.data.database.SpellingSequenceProgress(
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
                    gameType = "spelling_sequence",
                    levelId = levelId,
                    score = score,
                    stars = stars
                )
            } catch (_: Exception) {}
        }

        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_level_complete, null)
        // Haptic feedback on success (respects settings)
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateMedium(this)
        dialogView.findViewById<TextView>(com.example.kamaynikasyon.R.id.tv_time).text = "Time: $timeStr"
        dialogView.findViewById<TextView>(com.example.kamaynikasyon.R.id.tv_stars).text = "Stars: $starsStr"
        dialogView.findViewById<TextView>(com.example.kamaynikasyon.R.id.tv_score).text = "Score: $score"

        // Show confetti effect
        binding.root.post {
            val centerX = binding.root.width / 2f
            val centerY = binding.root.height / 2f
            ParticleSystem.playEffect(
                this@SpellingSequenceActivity,
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
        val hasNext = nextLevelId != null && levelAssetExists("minigames/spelling_sequence/${nextLevelId}.json")
        if (hasNext && nextLevelId != null) {
            nextBtn.setOnClickListener {
                alert.dismiss()
                // Navigate to selection activity and show next level dialog
                val selectionIntent = Intent(this, SpellingSequenceSelectionActivity::class.java)
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

    private fun startVerification(onComplete: () -> Unit) {
        if (isVerifying) return
        isVerifying = true
        verificationStartTime = System.currentTimeMillis()
        cameraFragment?.showVerificationProgress(true)
        cameraFragment?.setVerificationProgress(0)
        verificationRunnable = object : Runnable {
            override fun run() {
                if (!isVerifying) return
                val elapsed = System.currentTimeMillis() - verificationStartTime
                val progress = ((elapsed.toFloat() / VERIFICATION_DURATION) * 100).coerceIn(0f, 100f)
                cameraFragment?.setVerificationProgress(progress.toInt())
                if (elapsed >= VERIFICATION_DURATION) {
                    isVerifying = false
                    cameraFragment?.setVerificationProgress(100)
                    cameraFragment?.showVerificationProgress(false)
                    val completedAction = onComplete
                    currentVerifyingLetter = null
                    completedAction()
                } else {
                    verificationHandler.postDelayed(this, 16)
                }
            }
        }
        verificationHandler.post(verificationRunnable!!)
    }

    private fun stopVerification() {
        if (!isVerifying) return
        isVerifying = false
        verificationRunnable?.let { verificationHandler.removeCallbacks(it) }
        cameraFragment?.showVerificationProgress(false)
        cameraFragment?.setVerificationProgress(0)
        currentVerifyingLetter = null
    }

    private fun startTimer() {
        levelStartTimeMs = System.currentTimeMillis()
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsedSec = ((System.currentTimeMillis() - levelStartTimeMs) / 1000L).toInt().coerceAtLeast(0)
                binding.textTimer.text = formatElapsed(elapsedSec)
                binding.textScore.text = "Score: ${computeScore(elapsedSec)}"
                timerHandler.postDelayed(this, 500)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
    }

    private fun formatElapsed(totalSeconds: Int): String {
        val mm = (totalSeconds / 60).toString().padStart(2, '0')
        val ss = (totalSeconds % 60).toString().padStart(2, '0')
        return "$mm:$ss"
    }

    private fun computeScore(elapsedSeconds: Int): Int {
        val lettersCount = correctAnswer.count { it != ' ' }
        val baseScore = 100 * lettersCount
        val penalty = 5 * elapsedSeconds
        return (baseScore - penalty).coerceAtLeast(0)
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

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val info = binding.toolbar.findViewById<android.widget.ImageView>(R.id.btn_info)
        info?.setOnClickListener { showTutorial() }
    }
    
    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
    
    override fun onDestroy() {
        errorDialog?.dismiss()
        errorDialog = null
        stopVerification()
        stopTimer()
        verificationRunnable = null
        timerRunnable = null
        super.onDestroy()
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
        val pages = loadTutorialPagesFromAsset("spelling_sequence")
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
            Log.e("SpellingSequence", "Error loading tutorial", e)
        }
        return tutorialPages
    }
}



