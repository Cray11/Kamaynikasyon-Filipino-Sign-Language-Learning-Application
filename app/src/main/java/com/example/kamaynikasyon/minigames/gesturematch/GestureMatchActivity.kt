package com.example.kamaynikasyon.minigames.gesturematch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.example.kamaynikasyon.MainActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.ActivityGestureMatchBinding
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

class GestureMatchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGestureMatchBinding
    private var errorDialog: AlertDialog? = null
    
    // In-memory cache for level data
    private val levelCache = mutableMapOf<String, String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestureMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupBackPressedHandler()
        setupBoard()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val info = binding.toolbar.findViewById<android.widget.ImageView>(R.id.btn_info)
        info?.setOnClickListener { showTutorial() }
    }

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: GestureMatchAdapter
    private val cards: MutableList<GestureCard> = mutableListOf()
    private var firstSelectedIndex: Int = -1
    private var secondSelectedIndex: Int = -1
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isAwaitingDismiss: Boolean = false
    private var pendingMismatchFirst: Int = -1
    private var pendingMismatchSecond: Int = -1
    // Preloaded players for video assets (keyed by mediaPath)
    private val preloadedPlayers: MutableMap<String, androidx.media3.exoplayer.ExoPlayer> = mutableMapOf()
    
    // Prevent multiple video loads simultaneously
    private var isVideoLoading = false
    private var lastClickTime = 0L
    private val CLICK_DEBOUNCE_MS = 300L

    // Timer and scoring
    private var levelStartTimeMs: Long = 0L
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var attemptsCount: Int = 0 // number of attempt pairs checked
    private var scoreTotal: Int = 0

    private fun setupBoard() {
        // Inflate a RecyclerView into the placeholder
        recyclerView = androidx.recyclerview.widget.RecyclerView(this)
        recyclerView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        binding.contentPlaceholder.addView(recyclerView)

        // Load level data and build cards
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        val path = "minigames/gesture_match/${levelId}.json"
        
        // Show loading indicator
        binding.textLevelTitle.text = "Loading..."
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
                val pairs = loadPairs(path, levelId)
                
                if (pairs.isEmpty()) {
                    Log.e("GestureMatch", "No pairs loaded for level: $levelId")
                    showLevelLoadErrorDialog()
                    return@launch
                }

                // Use up to 4 pairs for 8 cards if available; otherwise use what's provided
                val selectedPairs = if (pairs.size >= 4) pairs.take(4) else pairs
                Log.d("GestureMatch", "Using ${selectedPairs.size} pairs for level $levelId")
                cards.clear()
                selectedPairs.forEachIndexed { index, p ->
                    // Media card
                    cards.add(
                        GestureCard(
                            id = "m_$index",
                            isMedia = true,
                            label = p.answer,
                            media = p.media
                        )
                    )
                    // Text card
                    cards.add(
                        GestureCard(
                            id = "t_$index",
                            isMedia = false,
                            label = p.answer
                        )
                    )
                }
                // Shuffle the cards for gameplay
                cards.shuffle()

                // Preload videos to avoid first-time decoder spikes when two videos are revealed at once
                preloadVideos(selectedPairs)

                adapter = GestureMatchAdapter(
                    cards,
                    onCardClicked = { position, _ -> onCardClicked(position) },
                    playerProvider = { media -> preloadedPlayers[media.path] },
                    videoUriProvider = { media -> trySupabaseThenAsset(media) },
                    lifecycleOwner = this@GestureMatchActivity
                )
                recyclerView.adapter = adapter

                // Set title if present
                val title = loadLevelTitle(path, levelId)
                if (title.isNotEmpty()) {
                    binding.textLevelTitle.text = title
                }

                // Reset score/timer and start
                attemptsCount = 0
                scoreTotal = 0
                updateScoreText()
                startTimer()
                
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
                Log.e("GestureMatch", "Error setting up board", e)
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

    private fun showLevelLoadErrorDialog() {
        if (isFinishing || isDestroyed) return
        errorDialog?.dismiss()
        errorDialog = DataLoadErrorDialog.create(
            context = this,
            messageRes = R.string.error_loading_minigame,
            onRetry = { setupBoard() },
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

    private fun preloadVideos(pairs: List<PairDef>) {
        // Create prepared, muted players for each unique video path
        val ctx = applicationContext
        val uniqueVideos = pairs
            .mapNotNull { pair -> pair.media?.takeIf { it.isVideo() } }
            .distinctBy { it.path }
        
        lifecycleScope.launch {
            for (media in uniqueVideos) {
                if (preloadedPlayers.containsKey(media.path)) continue
                try {
                    // Try Supabase first, then fallback to asset URI
                    val videoUri = trySupabaseThenAsset(media)
                    
                    if (videoUri == null || videoUri.toString().isEmpty()) {
                        Log.w("GestureMatch", "Invalid video URI for path: ${media.path}, skipping preload")
                        continue
                    }
                    
                    val player = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
                    val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUri)
                    player.setMediaItem(mediaItem)
                    player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                    player.volume = 0f
                    player.playWhenReady = false
                    
                    // Add error listener for fallback
                    player.addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("GestureMatch", "ExoPlayer error during preload: ${error.message}", error)
                            // Release failed player and try asset URI as fallback
                            try {
                                player.release()
                                preloadedPlayers.remove(media.path)
                                
                                // Try asset URI as fallback
                                val assetUri = media.asUri()
                                if (assetUri.toString() != videoUri.toString()) {
                                    try {
                                        val fallbackPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
                                        val fallbackMediaItem = androidx.media3.common.MediaItem.fromUri(assetUri)
                                        fallbackPlayer.setMediaItem(fallbackMediaItem)
                                        fallbackPlayer.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                        fallbackPlayer.volume = 0f
                                        fallbackPlayer.playWhenReady = false
                                        fallbackPlayer.prepare()
                                        preloadedPlayers[media.path] = fallbackPlayer
                                        Log.d("GestureMatch", "Preloaded video from asset URI: ${media.path}")
                                    } catch (e: Exception) {
                                        Log.e("GestureMatch", "Error preloading video from asset: ${media.path}", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("GestureMatch", "Error handling player error", e)
                            }
                        }
                    })
                    
                    player.prepare()
                    preloadedPlayers[media.path] = player
                    Log.d("GestureMatch", "Preloaded video: ${media.path} from URI: $videoUri")
                } catch (e: Exception) {
                    Log.e("GestureMatch", "Error preloading video: ${media.path}", e)
                    // Try asset URI as fallback
                    try {
                        val assetUri = media.asUri()
                        val player = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
                        val mediaItem = androidx.media3.common.MediaItem.fromUri(assetUri)
                        player.setMediaItem(mediaItem)
                        player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                        player.volume = 0f
                        player.playWhenReady = false
                        player.prepare()
                        preloadedPlayers[media.path] = player
                        Log.d("GestureMatch", "Preloaded video from asset (fallback): ${media.path}")
                    } catch (fallbackError: Exception) {
                        Log.e("GestureMatch", "Error preloading video from asset (fallback): ${media.path}", fallbackError)
                    }
                }
            }
        }
    }
    
    /**
     * Tries to get Supabase URL first, falls back to asset URI if Supabase is not available
     * For videos, downloads from Supabase first if available
     * For videos, if download fails, skips Supabase URL and goes straight to asset to avoid HTTP 400 errors
     */
    private suspend fun trySupabaseThenAsset(media: MediaResource): android.net.Uri? = withContext(Dispatchers.IO) {
        var downloadFailed = false
        
        // For videos, try to download from Supabase first if available
        if (media.isVideo() && SupabaseConfig.isInitialized()) {
            try {
                val bucket = SupabaseConfig.getBucket(this@GestureMatchActivity, SupabaseConfig.BUCKET_MEDIA)
                val cachedPath = SupabaseStorage.downloadFile(this@GestureMatchActivity, bucket, media.path)
                if (cachedPath != null) {
                    val fileUri = android.net.Uri.fromFile(java.io.File(cachedPath))
                    Log.d("GestureMatch", "Using downloaded Supabase video: $fileUri")
                    return@withContext fileUri
                } else {
                    // Download failed - mark it so we skip Supabase URL for videos
                    downloadFailed = true
                    Log.w("GestureMatch", "Failed to download video from Supabase, will use asset URI")
                }
            } catch (e: Exception) {
                // Download failed - mark it so we skip Supabase URL for videos
                downloadFailed = true
                Log.w("GestureMatch", "Failed to download video from Supabase, will use asset URI", e)
            }
        }
        
        // For videos, if download failed, skip Supabase URL to avoid HTTP 400 errors
        // For images, still try Supabase URL as Glide handles errors gracefully
        if (!media.isVideo() || !downloadFailed) {
            // Try Supabase URL first if available (for images or if download succeeded)
            val supabaseUri = media.asUriWithSupabase(this@GestureMatchActivity)
            if (supabaseUri != null) {
                val uriString = supabaseUri.toString()
                // If it's a Supabase URL (http/https), use it
                if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                    Log.d("GestureMatch", "Using Supabase URL: $uriString")
                    return@withContext supabaseUri
                }
            }
        }
        
        // Fallback to asset URI
        val assetUri = media.asUri()
        Log.d("GestureMatch", "Using asset URI: $assetUri")
        return@withContext assetUri
    }

    private data class PairDef(val media: MediaResource, val answer: String)

    private suspend fun loadPairs(assetPath: String, levelId: String): List<PairDef> = withContext(Dispatchers.IO) {
        try {
            // Try Supabase first, then fall back to assets
            val jsonString = loadLevelData(levelId, "gesture_match/$levelId.json", assetPath)
            
            if (jsonString == null) {
                Log.e("GestureMatch", "Failed to load level file: $assetPath")
                return@withContext emptyList()
            }
            
            val root = org.json.JSONObject(jsonString)
            
            // Try to get title from root or level object
            val titleFromRoot = root.optString("title", "")
            val levelObj = root.optJSONObject("level")
            val titleFromLevel = levelObj?.optString("title", "") ?: ""
            val title = if (titleFromRoot.isNotEmpty()) titleFromRoot else titleFromLevel
            if (title.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    binding.textLevelTitle.text = title
                }
            }
            
            // Get pairs array - check both root level and inside level object
            val arr = root.optJSONArray("pairs") ?: levelObj?.optJSONArray("pairs") ?: org.json.JSONArray()
            
            if (arr.length() == 0) {
                Log.w("GestureMatch", "No pairs found in level file: $assetPath")
                return@withContext emptyList()
            }
            
            val out = mutableListOf<PairDef>()
            for (i in 0 until arr.length()) {
                try {
                    val o = arr.getJSONObject(i)
                    val media = parseMediaResource(o)
                    if (media == null) {
                        Log.w("GestureMatch", "Failed to parse media resource at index $i in $assetPath")
                        continue
                    }
                    val answer = o.optString("answer", "")
                    if (answer.isNotBlank()) {
                        out.add(PairDef(media, answer))
                    } else {
                        Log.w("GestureMatch", "Empty answer at index $i in $assetPath")
                    }
                } catch (e: Exception) {
                    Log.e("GestureMatch", "Error parsing pair at index $i in $assetPath", e)
                }
            }
            
            if (out.isEmpty()) {
                Log.e("GestureMatch", "No valid pairs loaded from $assetPath")
            } else {
                Log.d("GestureMatch", "Successfully loaded ${out.size} pairs from $assetPath")
            }
            
            out
        } catch (e: Exception) {
            Log.e("GestureMatch", "Error loading pairs from $assetPath", e)
            emptyList()
        }
    }
    
    private suspend fun loadLevelData(
        levelId: String,
        levelPath: String,
        assetPath: String
    ): String? = withContext(Dispatchers.IO) {
        // Check in-memory cache first
        levelCache[levelId]?.let {
            Log.d("GestureMatch", "Loaded level from in-memory cache: $levelId")
            return@withContext it
        }
        
        // If user opted to use offline assets only, skip Supabase and cache
        val useOfflineAssetsOnly = ContentSyncManager.isUseOfflineAssetsOnly(this@GestureMatchActivity)
        if (useOfflineAssetsOnly) {
            // Go straight to assets
            return@withContext try {
                val jsonString = loadFromAssets(assetPath)
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    Log.d("GestureMatch", "Loaded level from assets (offline assets only mode): $levelId")
                }
                jsonString
            } catch (e: Exception) {
                Log.e("GestureMatch", "Failed to load level from assets: $levelId", e)
                null
            }
        }
        
        val isOnline = ErrorHandler.isOnline(this@GestureMatchActivity)
        
        // Try Supabase first if available and online
        if (SupabaseConfig.isInitialized() && isOnline) {
            try {
                val jsonString = SupabaseStorage.downloadTextFile(
                    this@GestureMatchActivity,
                    SupabaseConfig.BUCKET_MINIGAMES,
                    levelPath
                )
                if (jsonString != null) {
                    levelCache[levelId] = jsonString
                    // Cache to persistent storage for offline use
                    CacheManager.cacheData(this@GestureMatchActivity, "minigames", "gesture_match_$levelId", jsonString)
                    Log.d("GestureMatch", "Loaded level from Supabase and cached: $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("GestureMatch", "Failed to load level from Supabase: $levelId, trying cache", e)
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
                    Log.d("GestureMatch", "Loaded level from SupabaseStorage cache (offline): $levelId")
                    return@withContext jsonString
                }
            } catch (e: Exception) {
                Log.w("GestureMatch", "Failed to load from SupabaseStorage cache, trying CacheManager", e)
            }
            
            // Try CacheManager cache
            try {
                val cachedData = CacheManager.getCachedData(
                    this@GestureMatchActivity,
                    "minigames",
                    "gesture_match_$levelId",
                    String::class.java
                )
                if (cachedData != null) {
                    levelCache[levelId] = cachedData
                    Log.d("GestureMatch", "Loaded level from CacheManager (offline): $levelId")
                    return@withContext cachedData
                }
            } catch (e: Exception) {
                Log.w("GestureMatch", "Failed to load from CacheManager", e)
            }
        }
        
        // Fallback to assets
        return@withContext try {
            val jsonString = loadFromAssets(assetPath)
            if (jsonString != null) {
                levelCache[levelId] = jsonString
                // Cache assets data for future offline use
                CacheManager.cacheData(this@GestureMatchActivity, "minigames", "gesture_match_$levelId", jsonString)
                Log.d("GestureMatch", "Loaded level from assets and cached: $levelId")
            }
            jsonString
        } catch (e: Exception) {
            Log.e("GestureMatch", "Failed to load level from assets: $levelId", e)
            null
        }
    }
    
    private fun loadFromAssets(assetPath: String): String? {
        return try {
            assets.open(assetPath).use { String(it.readBytes(), Charset.forName("UTF-8")) }
        } catch (e: Exception) {
            Log.e("GestureMatch", "Error loading from assets: $assetPath", e)
            null
        }
    }

    private suspend fun loadLevelTitle(assetPath: String, levelId: String): String = withContext(Dispatchers.IO) {
        try {
            // Try Supabase first, then fall back to assets
            val jsonString = if (SupabaseConfig.isInitialized()) {
                SupabaseStorage.downloadTextFile(
                    this@GestureMatchActivity,
                    SupabaseConfig.BUCKET_MINIGAMES,
                    "gesture_match/$levelId.json"
                ) ?: loadFromAssets(assetPath)
            } else {
                loadFromAssets(assetPath)
            }
            
            if (jsonString == null) return@withContext ""
            
            val root = org.json.JSONObject(jsonString)
            val fromRoot = root.optString("title", "")
            if (fromRoot.isNotEmpty()) return@withContext fromRoot
            val levelObj = root.optJSONObject("level")
            levelObj?.optString("title", "") ?: ""
        } catch (e: Exception) {
            Log.e("GestureMatch", "Error loading level title from $assetPath", e)
            ""
        }
    }

    private fun parseMediaResource(obj: JSONObject): MediaResource? {
        val mediaObj = obj.optJSONObject("media") ?: return null
        val path = mediaObj.optString("path", "").trim()
        if (path.isEmpty()) return null
        val type = parseMediaType(mediaObj.optString("type", ""))
        val base = path.toMediaResource(type)
        val thumb = mediaObj.optString("thumbnailPath", "").takeIf { it.isNotBlank() }
        return base.copy(thumbnailPath = thumb)
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

    private fun onCardClicked(position: Int) {
        // Debounce protection - prevent rapid taps
        val now = System.currentTimeMillis()
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) {
            return
        }
        lastClickTime = now
        
        // Block if video is still loading
        if (isVideoLoading) {
            return
        }
        
        // If a mismatch is awaiting dismissal, any tap first hides the mismatched pair
        if (isAwaitingDismiss) {
            hidePendingMismatch()
            return
        }
        if (firstSelectedIndex != -1 && secondSelectedIndex != -1) return
        val card = cards[position]
        if (card.isMatched || card.isRevealed) return

        // Set loading flag if this card has a video
        if (card.isMedia && card.media?.isVideo() == true) {
            isVideoLoading = true
        }

        // Reveal this card
        adapter.updateCard(position) { it.isRevealed = true }
        
        // Reset loading flag after a short delay to allow video to start
        handler.postDelayed({
            isVideoLoading = false
        }, 500) // 500ms should be enough for video to start loading

        if (firstSelectedIndex == -1) {
            firstSelectedIndex = position
            return
        }

        secondSelectedIndex = position
        checkMatch()
    }

    private fun checkMatch() {
        val i = firstSelectedIndex
        val j = secondSelectedIndex
        if (i == -1 || j == -1) return
        val a = cards[i]
        val b = cards[j]

        val isMatch = a.label.equals(b.label, ignoreCase = true) && (a.isMedia != b.isMedia)

        // Count this as one attempt
        attemptsCount += 1

        if (isMatch) {
            // Light haptic on each match
            com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateLight(this)
            // Mark matched and lock them (green stroke)
            adapter.updateCard(i) { it.isMatched = true }
            adapter.updateCard(j) { it.isMatched = true }
            
            // Show "It's a Match" particle effects on both cards
            recyclerView.post {
                val viewHolder1 = recyclerView.findViewHolderForAdapterPosition(i)
                val viewHolder2 = recyclerView.findViewHolderForAdapterPosition(j)
                
                if (viewHolder1 != null && viewHolder1.itemView != null) {
                    val location = IntArray(2)
                    viewHolder1.itemView.getLocationOnScreen(location)
                    val screenX = location[0] + viewHolder1.itemView.width / 2f
                    val screenY = location[1] + viewHolder1.itemView.height / 2f
                    val contentView = findViewById<ViewGroup>(android.R.id.content)
                    val contentLocation = IntArray(2)
                    contentView.getLocationOnScreen(contentLocation)
                    val x = screenX - contentLocation[0]
                    val y = screenY - contentLocation[1]
                    
                    ParticleSystem.playEffect(
                        contentView,
                        ParticleEffects.textPopUp(
                            text = "It's a Match",
                            x = x,
                            y = y,
                            textSize = 36f,
                            color = Color.GREEN
                        )
                    )
                }
                
                if (viewHolder2 != null && viewHolder2.itemView != null) {
                    val location = IntArray(2)
                    viewHolder2.itemView.getLocationOnScreen(location)
                    val screenX = location[0] + viewHolder2.itemView.width / 2f
                    val screenY = location[1] + viewHolder2.itemView.height / 2f
                    val contentView = findViewById<ViewGroup>(android.R.id.content)
                    val contentLocation = IntArray(2)
                    contentView.getLocationOnScreen(contentLocation)
                    val x = screenX - contentLocation[0]
                    val y = screenY - contentLocation[1]
                    
                    ParticleSystem.playEffect(
                        contentView,
                        ParticleEffects.textPopUp(
                            text = "It's a Match",
                            x = x,
                            y = y,
                            textSize = 36f,
                            color = Color.GREEN
                        )
                    )
                }
            }
            
            // Scoring: +150 per match
            scoreTotal += 150
            updateScoreText()
            resetSelection()
            maybeFinish()
        } else {
            // Scoring: -25 per wrong attempt
            scoreTotal = (scoreTotal - 25).coerceAtLeast(0)
            updateScoreText()
            // Do not auto-hide; wait for a user tap anywhere (including a card) to dismiss
            isAwaitingDismiss = true
            pendingMismatchFirst = i
            pendingMismatchSecond = j
            adapter.updateCard(i) { it.isMismatch = true }
            adapter.updateCard(j) { it.isMismatch = true }
        }
    }

    private fun hidePendingMismatch() {
        if (!isAwaitingDismiss) return
        val i = pendingMismatchFirst
        val j = pendingMismatchSecond
        if (i >= 0) adapter.updateCard(i) { it.isRevealed = false }
        if (j >= 0) adapter.updateCard(j) { it.isRevealed = false }
        if (i >= 0) adapter.updateCard(i) { it.isMismatch = false }
        if (j >= 0) adapter.updateCard(j) { it.isMismatch = false }
        isAwaitingDismiss = false
        pendingMismatchFirst = -1
        pendingMismatchSecond = -1
        resetSelection()
    }

    private fun updateScoreText() {
        binding.textScore.text = "Score: $scoreTotal"
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

    private fun formatElapsed(totalSeconds: Int): String {
        val mm = (totalSeconds / 60).toString().padStart(2, '0')
        val ss = (totalSeconds % 60).toString().padStart(2, '0')
        return "$mm:$ss"
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun resetSelection() {
        firstSelectedIndex = -1
        secondSelectedIndex = -1
    }

    private fun maybeFinish() {
        val allMatched = cards.all { it.isMatched }
        if (!allMatched) return
        stopTimer()
        showCompletionDialog()
    }

    private fun showCompletionDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(com.example.kamaynikasyon.R.layout.dialog_level_complete, null)
        val elapsedSec = ((System.currentTimeMillis() - levelStartTimeMs) / 1000L).toInt().coerceAtLeast(0)
        val mm = (elapsedSec / 60).toString().padStart(2, '0')
        val ss = (elapsedSec % 60).toString().padStart(2, '0')
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_time).text = "Time: $mm:$ss"

        val itemsCount = cards.size
        val threeStarThreshold = 50 * itemsCount
        val twoStarThreshold = 25 * itemsCount
        val stars = when {
            scoreTotal >= threeStarThreshold -> 3
            scoreTotal >= twoStarThreshold -> 2
            else -> 1
        }
        val starsStr = "⭐".repeat(stars) + "☆".repeat(3 - stars)
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_stars).text = "Stars: $starsStr"
        dialogView.findViewById<android.widget.TextView>(com.example.kamaynikasyon.R.id.tv_score).text = "Score: $scoreTotal"

        // Save progress
        val levelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(applicationContext)
                val dao = db.gestureMatchProgressDao()
                val existing = dao.get(levelId)
                val bestScore = maxOf(existing?.bestScore ?: 0, scoreTotal)
                val bestStars = maxOf(existing?.bestStars ?: 0, stars)
                val prevBestTime = existing?.bestTimeSeconds ?: Int.MAX_VALUE
                val bestTime = if (prevBestTime <= 0) elapsedSec else minOf(prevBestTime, elapsedSec)
                val bestAttempts = minOf(existing?.bestAttempts ?: Int.MAX_VALUE, attemptsCount)
                dao.upsert(
                    com.example.kamaynikasyon.data.database.GestureMatchProgress(
                        levelId = levelId,
                        bestScore = bestScore,
                        bestStars = bestStars,
                        bestTimeSeconds = bestTime,
                        bestAttempts = bestAttempts,
                        completed = true
                    )
                )
                // Log analytics
                AnalyticsLogger.logMinigameEvent(
                    event = "completed",
                    gameType = "gesture_match",
                    levelId = levelId,
                    score = scoreTotal,
                    stars = stars
                )
            } catch (_: Exception) { }
        }
        // Haptic feedback on success (respects settings)
        com.example.kamaynikasyon.core.utils.VibratorHelper.vibrateMedium(this)
        
        // Show confetti effect
        binding.root.post {
            val centerX = binding.root.width / 2f
            val centerY = binding.root.height / 2f
            ParticleSystem.playEffect(
                this@GestureMatchActivity,
                ParticleEffects.confetti(
                    centerX = centerX,
                    centerY = centerY,
                    width = binding.root.width.toFloat(),
                    height = binding.root.height.toFloat()
                )
            )
        }
        
        val alert = builder.setView(dialogView).setCancelable(false).create()
        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_home).setOnClickListener {
            alert.dismiss(); finish()
        }
        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_retry).setOnClickListener {
            alert.dismiss(); recreate()
        }
        val nextBtn = dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_next)
        val currentLevelId = intent.getStringExtra("LEVEL_ID") ?: "level1"
        val nextLevelId = getNextLevelId(currentLevelId)
        val hasNext = nextLevelId != null && levelAssetExists("minigames/gesture_match/${nextLevelId}.json")
        if (hasNext && nextLevelId != null) {
            nextBtn.setOnClickListener {
                alert.dismiss()
                // Navigate to selection activity and show next level dialog
                val selectionIntent = Intent(this, GestureMatchSelectionActivity::class.java)
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

        dialogView.findViewById<android.widget.Button>(com.example.kamaynikasyon.R.id.btn_home).setOnClickListener {
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
        val pages = loadTutorialPagesFromAsset("gesture_match")
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
            Log.e("GestureMatch", "Error loading tutorial", e)
        }
        return tutorialPages
    }

    override fun onPause() {
        super.onPause()
        // Pause all preloaded players
        preloadedPlayers.values.forEach { p ->
            try {
                p.pause()
            } catch (e: Exception) {
                android.util.Log.w("ExoPlayer", "Error pausing preloaded player: ${e.message}")
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Pause all preloaded players
        preloadedPlayers.values.forEach { p ->
            try {
                p.pause()
            } catch (e: Exception) {
                android.util.Log.w("ExoPlayer", "Error pausing preloaded player in onStop: ${e.message}")
            }
        }
    }
    
    override fun onDestroy() {
        errorDialog?.dismiss()
        errorDialog = null
        // Reset loading state
        isVideoLoading = false
        // Cancel any pending handlers
        handler.removeCallbacksAndMessages(null)
        timerHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        // Release any preloaded players
        preloadedPlayers.values.forEach { p -> try { p.release() } catch (_: Exception) {} }
        preloadedPlayers.clear()
    }
}

