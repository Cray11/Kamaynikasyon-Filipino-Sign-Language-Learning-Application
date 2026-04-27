package com.example.kamaynikasyon.minigames.picturequiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.kamaynikasyon.MainActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.ActivityPictureQuizBinding
import com.example.kamaynikasyon.core.CameraDetectionFragment
import com.example.kamaynikasyon.core.ModelConfigFactory
import com.example.kamaynikasyon.core.TFLiteModelConfig
import com.example.kamaynikasyon.core.TutorialDialog
import com.example.kamaynikasyon.core.TutorialPage
import com.example.kamaynikasyon.core.media.MediaResource
import com.example.kamaynikasyon.core.media.MediaType
import com.example.kamaynikasyon.core.media.toMediaResource
import com.example.kamaynikasyon.core.particles.ParticleEffects
import com.example.kamaynikasyon.core.particles.ParticleSystem
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.supabase.ContentSyncManager
import com.example.kamaynikasyon.core.ui.DataLoadErrorDialog
import com.example.kamaynikasyon.core.utils.AnalyticsLogger
import com.example.kamaynikasyon.core.utils.CacheManager
import com.example.kamaynikasyon.core.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale

class PictureQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPictureQuizBinding

    private var correctAnswer: String = ""
    private var selectedSlotIndex: Int = -1

    // Camera fragment reference
    private var cameraFragment: CameraDetectionFragment? = null

    // Verification logic
    private var isVerifying: Boolean = false
    private var verificationStartTime: Long = 0L
    private val verificationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var verificationRunnable: Runnable? = null
    private val VERIFICATION_DURATION = 2000L
    private var currentVerifyingLetter: String? = null

    // Timer/scoring
    private var levelStartTimeMs: Long = 0L
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var errorDialog: AlertDialog? = null
    
    // In-memory cache for level data
    private val levelCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPictureQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBackPressedHandler()
        loadLevelJson()

        // Initialize timer UI
        binding.textTimer.text = "00:00"
        binding.textScore.text = "Score: 0"
        startTimer()

        // Embed the camera detection fragment (will handle live camera and hand sign detection)
        // Model config will be loaded from level JSON in loadLevelJson()
        cameraFragment = CameraDetectionFragment.newInstance(ModelConfigFactory.createFromMappingPath(this, "ml/alphabet_mapping.json"))
        supportFragmentManager.commit {
            replace(binding.contentPlaceholder.id, cameraFragment!!)
        }
        supportFragmentManager.executePendingTransactions()
        // Listen to prediction results: show predicted letter + confidence, and place into selected slot if confident
        cameraFragment?.setOnDetectionListener { resultBundle ->
            runOnUiThread {
                val rawPrediction = (resultBundle.predictedLetter ?: "").trim().uppercase()
                val confidence = resultBundle.tfLiteResult ?: 0f
                val confidencePercentage = (confidence * 100).toInt()

                val targetIndex = if (selectedSlotIndex >= 0) selectedSlotIndex else findNextEmptySlot(0)
                if (targetIndex < 0) return@runOnUiThread

                // Extract a single A-Z letter from prediction (supports outputs like "SIGN A")
                val predicted = when {
                    rawPrediction.length == 1 && rawPrediction[0].isLetter() -> rawPrediction
                    else -> rawPrediction.firstOrNull { it.isLetter() }?.toString()
                }

                Log.d("PictureQuiz", "Predicted=$predicted, Confidence=$confidence, Slot=$targetIndex")

                // Update prediction display with format "Predicting: A (100%)"
                if (predicted != null) {
                    cameraFragment?.updatePredictionWithConfidence(predicted, confidencePercentage)
                } else {
                    cameraFragment?.updatePredictionText("Predicting: -")
                }

                if (predicted != null && confidence > 0.6f) {
                    if (!isVerifying || currentVerifyingLetter != predicted) {
                        stopVerification()
                        currentVerifyingLetter = predicted
                        cameraFragment?.updateStatus("Status: Verifying")
                        startVerification {
                            // Place the letter captured when verification began
                            val letterToPlace = currentVerifyingLetter ?: predicted
                            placeLetterInSlot(targetIndex, letterToPlace)
                        }
                    }
                } else {
                    // Invalid/low-confidence -> cancel verification and hide bar
                    cameraFragment?.updateStatus("Status: Keep Trying")
                    stopVerification()
                }
            }
        }

        // Temporary placeholders; full scoring/timer are deferred
        binding.textTimer.text = "--:--"
        binding.textScore.text = ""

    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val info = binding.toolbar.findViewById<android.widget.ImageView>(R.id.btn_info)
        info?.setOnClickListener { showTutorial() }
    }

    private fun showTutorial() {
        val pages = loadTutorialPagesFromAsset("picture_quiz")
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
            Log.e("PictureQuiz", "Error loading tutorial", e)
        }
        return tutorialPages
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_exit_confirmation, null)
        
        // Update text for minigame
        dialogView.findViewById<TextView>(R.id.tv_dialog_title)?.text = "Exit Minigame"
        dialogView.findViewById<TextView>(R.id.tv_dialog_message)?.text = "Are you sure you want to exit? Your progress will be lost."
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_continue_lesson)
            .setOnClickListener {
                dialog.dismiss()
            }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_exit_lesson)
            .setOnClickListener {
                dialog.dismiss()
                finish()
            }
        
        dialog.show()
    }

    private fun loadLevelJson() {
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        // Load directly by id from index mapping; each level file is named <id>.json
        val assetPath = "minigames/picture_quiz/$levelId.json"
        val levelPath = "picture_quiz/$levelId.json"
        
        // Show loading indicator
        binding.progressLoading.visibility = View.VISIBLE
        binding.quizPanel.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                // Try Supabase first, then fall back to assets
                val jsonString = withContext(Dispatchers.IO) {
                    loadLevelData(levelId, levelPath, assetPath)
                }
                
                if (jsonString == null) {
                    binding.progressLoading.visibility = View.GONE
                    binding.quizPanel.visibility = View.GONE
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
                if (levelTitle.isNotEmpty()) binding.textLevelTitle.text = levelTitle
            }

            val levelObj = root.optJSONObject("level")
            val questionsArr = root.optJSONArray("questions")
            val question = questionsArr?.optJSONObject(0)

            correctAnswer = (root.optString("correctAnswer").takeIf { it.isNotBlank() }
                ?: levelObj?.optString("correctAnswer")?.takeIf { it.isNotBlank() }
                ?: question?.optString("correctAnswer")
                ?: "?")
            val hint = (root.optString("hints").takeIf { it.isNotBlank() }
                ?: levelObj?.optString("hints")?.takeIf { it.isNotBlank() }
                ?: question?.optString("hints")
                ?: "")
            val mediaResource = extractMedia(root, levelObj, question)

            // Show hint if available; otherwise leave blank or use title already set above
            binding.questionText.text = hint
            binding.answerText.text = correctAnswer

            // Load model config from JSON if specified
            val modelConfigJson = root.opt("modelConfig")
                ?: levelObj?.opt("modelConfig")
                ?: question?.opt("modelConfig")
            
            val modelConfig: TFLiteModelConfig = ModelConfigFactory.createFromJson(
                this@PictureQuizActivity,
                modelConfigJson
            )
            
            // Update camera fragment with the model config from JSON
            cameraFragment?.setModelConfig(modelConfig)
            
            displayQuestionMedia(mediaResource)

            // Build slots for the answer
            createLetterSlots(correctAnswer)
            
            // Hide loading indicator and show content
            binding.progressLoading.visibility = View.GONE
            binding.quizPanel.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("PictureQuiz", "Failed to load $assetPath", e)
            binding.progressLoading.visibility = View.GONE
            binding.quizPanel.visibility = View.GONE
            showLevelLoadErrorDialog()
        }
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
    
    private suspend fun loadLevelData(
        levelId: String,
        levelPath: String,
        assetPath: String
    ): String? = withContext(Dispatchers.IO) {
        // Check in-memory cache first
        levelCache[levelId]?.let {
            Log.d("PictureQuiz", "Loaded level from in-memory cache: $levelId")
            return@withContext it
        }
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(this@PictureQuizActivity)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = loadFromAssets(assetPath)
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    Log.d("PictureQuiz", "Loaded level from assets (offline assets only mode): $levelId")
                }
                jsonString
            } catch (e: Exception) {
                Log.e("PictureQuiz", "Failed to load level from assets: $levelId", e)
                null
            }
        }
        
        val isOnline = ErrorHandler.isOnline(this@PictureQuizActivity)
        
        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    this@PictureQuizActivity,
                    SupabaseConfig.BUCKET_MINIGAMES,
                    levelPath
                )
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    // Cache to persistent storage for offline use
                    CacheManager.cacheData(this@PictureQuizActivity, "minigames", "picture_quiz_$levelId", jsonString)
                    Log.d("PictureQuiz", "Loaded level from Supabase and cached: $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("PictureQuiz", "Failed to load level from Supabase: $levelId, trying cache", e)
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
                    Log.d("PictureQuiz", "Loaded level from SupabaseStorage cache (offline): $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("PictureQuiz", "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            // Try CacheManager cache
            try {
                val cachedData = CacheManager.getCachedData(
                    this@PictureQuizActivity,
                    "minigames",
                    "picture_quiz_$levelId",
                    String::class.java
                )
                if (cachedData != null) {
                    levelCache[levelId] = cachedData
                    Log.d("PictureQuiz", "Loaded level from CacheManager (offline): $levelId")
                    return@withContext cachedData
                }
            } catch (e: Exception) {
                Log.w("PictureQuiz", "Failed to load from CacheManager", e)
            }
        }
        
        // Fallback to assets
        return@withContext try {
            val jsonString = loadFromAssets(assetPath)
            if (jsonString != null) {
                levelCache[levelId] = jsonString
                // Cache assets data for future offline use
                CacheManager.cacheData(this@PictureQuizActivity, "minigames", "picture_quiz_$levelId", jsonString)
                Log.d("PictureQuiz", "Loaded level from assets and cached: $levelId")
            }
            jsonString
        } catch (e: Exception) {
            Log.e("PictureQuiz", "Failed to load level from assets: $levelId", e)
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

    private fun displayQuestionMedia(media: MediaResource?) {
        when {
            media != null && media.isImage() -> {
                loadImage(media)
            }
            media != null && media.isVideo() -> {
                Log.w("PictureQuiz", "Video media not yet supported in PictureQuiz. Showing placeholder for ${media.path}")
                binding.imageQuestion.setImageResource(R.drawable.default_image)
            }
            else -> {
                binding.imageQuestion.setImageResource(R.drawable.default_image)
            }
        }
    }

    /**
     * Loads image using Glide with Supabase support (similar to Dictionary)
     * Uses asUriWithSupabase to support both Supabase URLs and asset URIs
     */
    private fun loadImage(media: MediaResource) {
        if (media.path.isBlank()) {
            binding.imageQuestion.setImageResource(R.drawable.default_image)
            return
        }

        // Use asUriWithSupabase to get Supabase URL if available, otherwise fallback to asset URI
        val imageUri = media.asUriWithSupabase(this)
        val uriString = imageUri.toString()
        
        // Check if it's an asset URI (file:///android_asset/)
        if (uriString.startsWith("file:///android_asset/")) {
            // Load directly from assets using BitmapFactory (Glide can't handle asset URIs well)
            try {
                val normalizedPath = uriString.removePrefix("file:///android_asset/").replace("%20", " ")
                assets.open(normalizedPath).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        binding.imageQuestion.setImageBitmap(bitmap)
                    } else {
                        binding.imageQuestion.setImageResource(R.drawable.default_image)
                    }
                }
            } catch (e: Exception) {
                Log.e("PictureQuiz", "Failed to load image from assets: ${media.path}", e)
                binding.imageQuestion.setImageResource(R.drawable.default_image)
            }
        } else {
            // Use Glide for Supabase URLs or other HTTP/HTTPS URLs
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.default_image)
                .error(R.drawable.default_image)
                .into(object : CustomTarget<Drawable>() {
                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Optional - called when the resource is cleared
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        // Image is fully loaded (not placeholder) - show image
                        binding.imageQuestion.setImageDrawable(resource)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        // On error, show error placeholder
                        if (errorDrawable != null) {
                            binding.imageQuestion.setImageDrawable(errorDrawable)
                        } else {
                            binding.imageQuestion.setImageResource(R.drawable.default_image)
                        }
                    }
                })
        }
    }

    private fun extractMedia(vararg nodes: JSONObject?): MediaResource? {
        nodes.forEach { node ->
            node?.optJSONObject("media")?.let { mediaObj ->
                val path = mediaObj.optString("path", "").trim()
                if (path.isNotEmpty()) {
                    val type = parseMediaType(mediaObj.optString("type", ""))
                    val base = path.toMediaResource(type)
                    val thumb = mediaObj.optString("thumbnailPath", "").takeIf { it.isNotBlank() }
                    return base.copy(thumbnailPath = thumb)
                }
            }
        }
        return null
    }

    private fun parseMediaType(raw: String?): MediaType? {
        if (raw.isNullOrBlank()) return null
        return when (raw.lowercase(Locale.ROOT)) {
            "video" -> MediaType.VIDEO
            "image" -> MediaType.IMAGE
            "audio" -> MediaType.AUDIO
            else -> null
        }
    }

    

    private fun createLetterSlots(answer: String) {
        binding.containerLetterSlots.removeAllViews()
        selectedSlotIndex = -1

        // Build rows that wrap greedily; break at spaces when possible, otherwise wrap letters
        val density = resources.displayMetrics.density
        val slotSizePx = (48f * density).toInt() // fixed size square slots
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
                    // Prefer to break line at spaces if next word would overflow
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

                // Wrap if needed (even inside long words)
                if (usedWidth > 0 && usedWidth + slotTotalWidth > containerWidth) {
                    currentRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    binding.containerLetterSlots.addView(currentRow)
                    usedWidth = 0
                }

                val slot = buildSlotTextView(slotIndex)
                currentRow.addView(slot)
                slotIndex++
                usedWidth += slotTotalWidth
                i++
            }

            // Select first slot by default
            val first = findNextEmptySlot(0)
            if (first >= 0) selectSlot(first)
        }
    }

    private fun buildSlotTextView(index: Int): TextView {
        val tv = TextView(this)
        tv.text = "_"
        tv.textSize = 20f
        val density = resources.displayMetrics.density
        val sizePx = (36f * density).toInt()
        val padV = (2f * density).toInt()
        val padH = (padV / 2).coerceAtLeast(0)
        tv.setPadding(padH, padV, padH, padV)
        tv.setTextColor(resources.getColor(com.example.kamaynikasyon.R.color.color_black, null))
        tv.gravity = android.view.Gravity.CENTER
        // Use app default font (Fredoka)
        try {
            val tf = androidx.core.content.res.ResourcesCompat.getFont(this, com.example.kamaynikasyon.R.font.fredoka)
            if (tf != null) tv.typeface = tf
        } catch (_: Exception) {}
        tv.includeFontPadding = false
        tv.setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_background)
        tv.setOnClickListener { selectSlot(index) }
        val params = LinearLayout.LayoutParams(sizePx, sizePx)
        val m = (2f * density).toInt()
        val v = (2f * density).toInt()
        params.setMargins(m, v, m, v)
        tv.layoutParams = params
        tv.tag = index
        return tv
    }

    private fun selectSlot(index: Int) {
        // clear previous highlight
        forEachSlot { i, v ->
            v.setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_background)
            v.setTextColor(resources.getColor(com.example.kamaynikasyon.R.color.color_black, null))
        }
        // highlight new selection
        getSlotAt(index)?.apply {
            setBackgroundResource(com.example.kamaynikasyon.R.drawable.letter_slot_selected_background)
            setTextColor(resources.getColor(com.example.kamaynikasyon.R.color.color_black, null))
        }
        selectedSlotIndex = index
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

    override fun onDestroy() {
        errorDialog?.dismiss()
        errorDialog = null
        super.onDestroy()
        stopVerification()
        stopTimer()
    }

    private fun placeLetterInSlot(index: Int, letter: String) {
        val slot = getSlotAt(index) ?: return
        // Light haptic on each verified letter
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateLight(this)
        slot.text = letter
        // auto-advance to next empty slot
        val next = findNextEmptySlot(startFrom = index + 1)
        if (next >= 0) {
            selectSlot(next)
        } else {
            selectedSlotIndex = -1
            checkCompletion()
        }
    }

    private fun findNextEmptySlot(startFrom: Int = 0): Int {
        var logicalIndex = 0
        for (i in 0 until binding.containerLetterSlots.childCount) {
            val row = binding.containerLetterSlots.getChildAt(i)
            if (row is LinearLayout) {
                for (j in 0 until row.childCount) {
                    val child = row.getChildAt(j)
                    if (child is TextView) {
                        if (logicalIndex >= startFrom && child.text.toString() == "_") return logicalIndex
                        logicalIndex++
                    }
                }
            } else if (row is TextView) {
                if (logicalIndex >= startFrom && row.text.toString() == "_") return logicalIndex
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

    private fun composedAnswer(): String {
        val sb = StringBuilder()
        var logicalIndex = 0
        for (ch in correctAnswer) {
            if (ch == ' ') {
                sb.append(' ')
            } else {
                val slot = getSlotAt(logicalIndex)
                sb.append(slot?.text?.toString()?.takeIf { it != "_" } ?: "_")
                logicalIndex++
            }
        }
        return sb.toString()
    }

    private fun isAllFilled(): Boolean {
        var filled = true
        forEachSlot { _, v -> if (v.text.toString() == "_") filled = false }
        return filled
    }

    private fun checkCompletion() {
        if (!isAllFilled()) return
        val answer = composedAnswer()
        if (answer.equals(correctAnswer, ignoreCase = true)) {
            showCompletionDialog()
        } else {
            // Show wrong dialog and reset after dismiss
            showWrongDialog()
        }
    }

    private fun showWrongDialog() {
        // Heavy haptic on lose condition
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateHeavy(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("Not Quite Right")
            .setMessage("That's not the correct answer. Try again.")
            .setPositiveButton("OK") { d, _ ->
                createLetterSlots(correctAnswer)
                cameraFragment?.updateStatus("Status: Try Again")
                d.dismiss()
            }
            .setCancelable(false)
            .show()
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

        // Persist progress (best score/stars/time) for this level
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(applicationContext)
                val dao = db.pictureQuizProgressDao()
                val existing = dao.get(levelId)
                val bestScore = maxOf(existing?.bestScore ?: 0, score)
                val bestStars = maxOf(existing?.bestStars ?: 0, stars)
                val prevBestTime = existing?.bestTimeSeconds ?: Int.MAX_VALUE
                val bestTime = if (prevBestTime <= 0) elapsedSec else minOf(prevBestTime, elapsedSec)
                dao.upsert(
                    com.example.kamaynikasyon.data.database.PictureQuizProgress(
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
                    gameType = "picture_quiz",
                    levelId = levelId,
                    score = score,
                    stars = stars
                )
            } catch (e: Exception) {
                android.util.Log.e("PictureQuiz", "Error saving progress", e)
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
                this@PictureQuizActivity,
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
        val hasNext = nextLevelId != null && levelAssetExists("minigames/picture_quiz/${nextLevelId}.json")
        if (hasNext && nextLevelId != null) {
            nextBtn.setOnClickListener {
                alert.dismiss()
                // Navigate to selection activity and show next level dialog
                val selectionIntent = Intent(this, PictureQuizSelectionActivity::class.java)
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
        timerRunnable = null
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
        val pqRegex = Regex("pq_(\\d+)")
        val nextNumber = when {
            levelRegex.matches(current) -> levelRegex.find(current)?.groupValues?.getOrNull(1)?.toIntOrNull()?.plus(1)
            pqRegex.matches(current) -> pqRegex.find(current)?.groupValues?.getOrNull(1)?.toIntOrNull()?.plus(1)
            else -> null
        } ?: return null
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
}

