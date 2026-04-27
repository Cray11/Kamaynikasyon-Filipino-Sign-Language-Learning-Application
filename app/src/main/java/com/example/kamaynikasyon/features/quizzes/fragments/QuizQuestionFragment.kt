package com.example.kamaynikasyon.features.quizzes.fragments

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.media.MediaResource
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.utils.showError
import com.example.kamaynikasyon.core.utils.VibratorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.example.kamaynikasyon.databinding.DialogVideoPreviewBinding
import com.example.kamaynikasyon.databinding.FragmentQuizQuestionBinding
import com.example.kamaynikasyon.features.quizzes.data.models.*
import com.google.android.material.button.MaterialButton

class QuizQuestionFragment : Fragment() {
    
    private var _binding: FragmentQuizQuestionBinding? = null
    private val binding get() = _binding!!
    private var onAnswerSelectedListener: ((Int) -> Unit)? = null
    private var selectedAnswer: Int = -1
    private var currentQuestion: QuizQuestion? = null
    private var questionNumber: Int = 0
    private val players = mutableMapOf<View, ExoPlayer>()
    // Preloaded players keyed by media path (similar to GestureMatchActivity)
    private val preloadedPlayers = mutableMapOf<String, ExoPlayer>()
    
    companion object {
        private const val ARG_QUESTION = "question"
        private const val ARG_QUESTION_NUMBER = "question_number"
        
        fun newInstance(question: QuizQuestion, questionNumber: Int): QuizQuestionFragment {
            val fragment = QuizQuestionFragment()
            val args = Bundle()
            args.putParcelable(ARG_QUESTION, question)
            args.putInt(ARG_QUESTION_NUMBER, questionNumber)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuizQuestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val question = arguments?.let { BundleCompat.getParcelable(it, ARG_QUESTION, QuizQuestion::class.java) }
        val qNumber = arguments?.getInt(ARG_QUESTION_NUMBER, 0) ?: 0

        if (question != null) {
            setupQuestion(question, qNumber)
        }
    }
    
    private fun setupQuestion(question: QuizQuestion, questionNumber: Int) {
        currentQuestion = question
        this.questionNumber = questionNumber
        
        binding.tvQuestionNumber.text = "Question ${questionNumber + 1}"
        
        // Preload the question video ahead of time (reuse GestureMatchActivity approach)
        preloadVideos(question)
        
        // Setup question content (text, video, or image)
        setupQuestionContent(question.question)
        
        // Setup answer options
        setupAnswerOptions(question.options)
    }
    
    /**
     * Preloads videos to avoid MediaCodec resource exhaustion when multiple videos load simultaneously
     * Only preloads the question video immediately; option videos are loaded lazily to save memory
     */
    private fun preloadVideos(question: QuizQuestion) {
        val context = view?.context ?: requireContext()
        
        // Only preload the question video immediately (most important)
        // Option videos will be loaded lazily when needed to prevent OOM
        question.question.primaryMedia()
            ?.takeIf { it.isVideo() }
            ?.let { media ->
                val key = media.path
                if (!preloadedPlayers.containsKey(key)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            // Try Supabase first, then fallback to asset URI
                            val videoUri = trySupabaseThenAsset(media)
                            
                            if (videoUri == null || videoUri.toString().isEmpty()) {
                                android.util.Log.w("QuizQuestionFragment", "Invalid video URI for preload: ${media.path}")
                                return@launch
                            }
                            
                            val player = ExoPlayer.Builder(context).build()
                            val mediaItem = MediaItem.Builder().setUri(videoUri).build()
                            player.setMediaItem(mediaItem)
                            player.repeatMode = Player.REPEAT_MODE_ALL
                            player.volume = 0f
                            player.playWhenReady = false
                            
                            // Add error listener for fallback
                            player.addListener(object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    android.util.Log.e("QuizQuestionFragment", "ExoPlayer error during preload: ${error.message}", error)
                                    // Release failed player and try asset URI as fallback
                                    try {
                                        player.release()
                                        preloadedPlayers.remove(key)
                                        
                                        // Try asset URI as fallback
                                        val assetUri = media.asUri()
                                        if (assetUri.toString() != videoUri.toString()) {
                                            try {
                                                val fallbackPlayer = ExoPlayer.Builder(context).build()
                                                val fallbackMediaItem = MediaItem.Builder().setUri(assetUri).build()
                                                fallbackPlayer.setMediaItem(fallbackMediaItem)
                                                fallbackPlayer.repeatMode = Player.REPEAT_MODE_ALL
                                                fallbackPlayer.volume = 0f
                                                fallbackPlayer.playWhenReady = false
                                                fallbackPlayer.prepare()
                                                preloadedPlayers[key] = fallbackPlayer
                                                android.util.Log.d("QuizQuestionFragment", "Preloaded video from asset URI: ${media.path}")
                                            } catch (e: Exception) {
                                                android.util.Log.e("QuizQuestionFragment", "Error preloading video from asset: ${media.path}", e)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("QuizQuestionFragment", "Error handling player error", e)
                                    }
                                }
                            })
                            
                            player.prepare()
                            preloadedPlayers[key] = player
                            android.util.Log.d("QuizQuestionFragment", "Preloaded video: ${media.path} from URI: $videoUri")
                        } catch (e: OutOfMemoryError) {
                            android.util.Log.e("ExoPlayer", "OutOfMemoryError preloading question video: ${media.path}", e)
                        } catch (e: Exception) {
                            android.util.Log.e("ExoPlayer", "Error preloading question video: ${media.path}", e)
                            // Try asset URI as fallback
                            try {
                                val assetUri = media.asUri()
                                val player = ExoPlayer.Builder(context).build()
                                val mediaItem = MediaItem.Builder().setUri(assetUri).build()
                                player.setMediaItem(mediaItem)
                                player.repeatMode = Player.REPEAT_MODE_ALL
                                player.volume = 0f
                                player.playWhenReady = false
                                player.prepare()
                                preloadedPlayers[key] = player
                                android.util.Log.d("QuizQuestionFragment", "Preloaded video from asset (fallback): ${media.path}")
                            } catch (fallbackError: Exception) {
                                android.util.Log.e("QuizQuestionFragment", "Error preloading video from asset (fallback): ${media.path}", fallbackError)
                            }
                        }
                    }
                }
            }
        
        // Note: Option videos are loaded lazily in initializePlayerForView() to prevent OOM
    }
    
    private fun setupQuestionContent(questionContent: com.example.kamaynikasyon.features.quizzes.data.models.QuestionContent) {
        // Hide all question media initially
        binding.frameLayoutQuestionVideo.visibility = View.GONE
        binding.videoViewQuestion.visibility = View.GONE
        binding.imageViewQuestion.visibility = View.GONE
        
        // Release previous question player
        releasePlayerForView(binding.videoViewQuestion)
        
        // Set question text
        binding.tvQuestion.text = questionContent.text ?: "Question"
        
        // Handle different content types
        when (questionContent.type) {
            ContentType.VIDEO -> {
                questionContent.primaryMedia()
                    ?.takeIf { it.isVideo() }
                    ?.let { media ->
                        try {
                            binding.frameLayoutQuestionVideo.visibility = View.VISIBLE
                            binding.videoViewQuestion.visibility = View.VISIBLE
                            binding.btnMenuQuestionVideo.visibility = View.VISIBLE
                            initializePlayerForView(binding.videoViewQuestion, media)
                            binding.btnMenuQuestionVideo.setOnClickListener {
                                pauseAllVideos()
                                showVideoDialog(media, null)
                            }
                        } catch (e: Exception) {
                            showError(R.string.error_loading_video)
                            binding.frameLayoutQuestionVideo.visibility = View.GONE
                            binding.videoViewQuestion.visibility = View.GONE
                            binding.btnMenuQuestionVideo.visibility = View.GONE
                        }
                    }
            }
            ContentType.IMAGE -> {
                questionContent.primaryMedia()
                    ?.takeIf { it.isImage() }
                    ?.let { media ->
                        try {
                            binding.imageViewQuestion.visibility = View.VISIBLE
                            // Use asUriWithSupabase to support both Supabase URLs and asset URIs
                            val imageUri = media.asUriWithSupabase(requireContext())
                            binding.imageViewQuestion.setImageURI(imageUri)
                        } catch (e: Exception) {
                            showError(R.string.error_loading_image)
                            binding.imageViewQuestion.visibility = View.GONE
                        }
                    }
            }
            ContentType.TEXT -> {
                // Text only - no additional media needed
            }
        }
    }
    
    private fun setupAnswerOptions(options: List<com.example.kamaynikasyon.features.quizzes.data.models.AnswerOption>) {
        // Release all option players first
        releasePlayerForView(binding.videoOption1)
        releasePlayerForView(binding.videoOption2)
        releasePlayerForView(binding.videoOption3)
        releasePlayerForView(binding.videoOption4)
        releasePlayerForView(binding.gridVideoOption1)
        releasePlayerForView(binding.gridVideoOption2)
        releasePlayerForView(binding.gridVideoOption3)
        releasePlayerForView(binding.gridVideoOption4)
        
        // Check if any option is image or video to determine layout
        val hasMultimediaOptions = options.any { it.type == ContentType.IMAGE || it.type == ContentType.VIDEO }
        
        if (hasMultimediaOptions) {
            // Use 2x2 grid layout for multimedia options
            setupGridAnswerOptions(options)
        } else {
            // Use vertical list layout for text-only options
            setupVerticalAnswerOptions(options)
        }
    }
    
    private fun setupGridAnswerOptions(options: List<com.example.kamaynikasyon.features.quizzes.data.models.AnswerOption>) {
        // Hide vertical layout, show grid layout
        binding.answerOptionsContainer.visibility = View.GONE
        binding.gridOptionsContainer.visibility = View.VISIBLE
        
        val optionContainers = listOf(
            binding.gridOption1Container,
            binding.gridOption2Container,
            binding.gridOption3Container,
            binding.gridOption4Container
        )
        
        val videoFrameLayouts = listOf(
            binding.frameLayoutGridOption1Video,
            binding.frameLayoutGridOption2Video,
            binding.frameLayoutGridOption3Video,
            binding.frameLayoutGridOption4Video
        )
        
        val videoViews = listOf(
            binding.gridVideoOption1,
            binding.gridVideoOption2,
            binding.gridVideoOption3,
            binding.gridVideoOption4
        )
        
        val imageViews = listOf(
            binding.gridImageOption1,
            binding.gridImageOption2,
            binding.gridImageOption3,
            binding.gridImageOption4
        )
        
        val textViews = listOf(
            binding.gridTextOption1,
            binding.gridTextOption2,
            binding.gridTextOption3,
            binding.gridTextOption4
        )
        
        options.forEachIndexed { index, option ->
            if (index < optionContainers.size) {
                val container = optionContainers[index]
                val videoFrameLayout = videoFrameLayouts[index]
                val videoView = videoViews[index]
                val imageView = imageViews[index]
                val textView = textViews[index]
                
                container.visibility = View.VISIBLE
                
                // Hide all media containers initially
                videoFrameLayout.visibility = View.GONE
                videoView.visibility = View.GONE
                imageView.visibility = View.GONE
                
                // Setup content based on type
                when (option.type) {
                    ContentType.VIDEO -> {
                        option.primaryMedia()
                            ?.takeIf { it.isVideo() }
                            ?.let { media ->
                                try {
                                    videoFrameLayout.visibility = View.VISIBLE
                                    videoView.visibility = View.VISIBLE
                                    initializePlayerForView(videoView as PlayerView, media)
                                    // Show menu button and set click listener
                                    val menuButtonId = when (index) {
                                        0 -> R.id.btn_menu_grid_video_option_1
                                        1 -> R.id.btn_menu_grid_video_option_2
                                        2 -> R.id.btn_menu_grid_video_option_3
                                        3 -> R.id.btn_menu_grid_video_option_4
                                        else -> null
                                    }
                                    menuButtonId?.let { id ->
                                        val menuButton = binding.root.findViewById<ImageButton>(id)
                                        menuButton?.let { btn ->
                                            btn.visibility = View.VISIBLE
                                            btn.setOnClickListener {
                                                pauseAllVideos()
                                                showVideoDialog(media, option.text)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    showError(R.string.error_loading_video)
                                    videoFrameLayout.visibility = View.GONE
                                    videoView.visibility = View.GONE
                                }
                            }
                        textView.text = option.text ?: "Video Option ${index + 1}"
                    }
                    ContentType.IMAGE -> {
                        option.primaryMedia()
                            ?.takeIf { it.isImage() }
                            ?.let { media ->
                                try {
                                    imageView.visibility = View.VISIBLE
                                    // Use asUriWithSupabase to support both Supabase URLs and asset URIs
                                    val imageUri = media.asUriWithSupabase(requireContext())
                                    imageView.setImageURI(imageUri)
                                } catch (e: Exception) {
                                    showError(R.string.error_loading_image)
                                    imageView.visibility = View.GONE
                                }
                            }
                        textView.text = option.text ?: "Image Option ${index + 1}"
                    }
                    ContentType.TEXT -> {
                        textView.text = option.text ?: "Option ${index + 1}"
                    }
                }
                
                container.setOnClickListener {
                    selectAnswer(index)
                }
            }
        }
    }
    
    private fun setupVerticalAnswerOptions(options: List<com.example.kamaynikasyon.features.quizzes.data.models.AnswerOption>) {
        // Hide grid layout, show vertical layout
        binding.gridOptionsContainer.visibility = View.GONE
        binding.answerOptionsContainer.visibility = View.VISIBLE

        val optionContainers = listOf(
            binding.option1Container,
            binding.option2Container,
            binding.option3Container,
            binding.option4Container
        )
        
        val videoFrameLayouts = listOf(
            binding.frameLayoutOption1Video,
            binding.frameLayoutOption2Video,
            binding.frameLayoutOption3Video,
            binding.frameLayoutOption4Video
        )
        
        val videoViews = listOf(
            binding.videoOption1,
            binding.videoOption2,
            binding.videoOption3,
            binding.videoOption4
        )
        
        val imageViews = listOf(
            binding.imageOption1,
            binding.imageOption2,
            binding.imageOption3,
            binding.imageOption4
        )
        
        val textViews = listOf(
            binding.textOption1,
            binding.textOption2,
            binding.textOption3,
            binding.textOption4
        )
        
        options.forEachIndexed { index, option ->
            if (index < optionContainers.size) {
                val container = optionContainers[index]
                val videoFrameLayout = videoFrameLayouts[index]
                val videoView = videoViews[index]
                val imageView = imageViews[index]
                val textView = textViews[index]
                
                container.visibility = View.VISIBLE
                
                // Hide all media containers initially
                videoFrameLayout.visibility = View.GONE
                videoView.visibility = View.GONE
                imageView.visibility = View.GONE
                
                // Setup content based on type
                when (option.type) {
                    ContentType.VIDEO -> {
                        option.primaryMedia()
                            ?.takeIf { it.isVideo() }
                            ?.let { media ->
                                try {
                                    videoFrameLayout.visibility = View.VISIBLE
                                    videoView.visibility = View.VISIBLE
                                    initializePlayerForView(videoView as PlayerView, media)
                                    // Show menu button and set click listener
                                    val menuButtonId = when (index) {
                                        0 -> R.id.btn_menu_video_option_1
                                        1 -> R.id.btn_menu_video_option_2
                                        2 -> R.id.btn_menu_video_option_3
                                        3 -> R.id.btn_menu_video_option_4
                                        else -> null
                                    }
                                    menuButtonId?.let { id ->
                                        val menuButton = binding.root.findViewById<ImageButton>(id)
                                        menuButton?.let { btn ->
                                            btn.visibility = View.VISIBLE
                                            btn.setOnClickListener {
                                                pauseAllVideos()
                                                showVideoDialog(media, option.text)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    showError(R.string.error_loading_video)
                                    videoFrameLayout.visibility = View.GONE
                                    videoView.visibility = View.GONE
                                }
                            }
                        textView.text = option.text ?: "Video Option ${index + 1}"
                    }
                    ContentType.IMAGE -> {
                        option.primaryMedia()
                            ?.takeIf { it.isImage() }
                            ?.let { media ->
                                try {
                                    imageView.visibility = View.VISIBLE
                                    // Use asUriWithSupabase to support both Supabase URLs and asset URIs
                                    val imageUri = media.asUriWithSupabase(requireContext())
                                    imageView.setImageURI(imageUri)
                                } catch (e: Exception) {
                                    showError(R.string.error_loading_image)
                                    imageView.visibility = View.GONE
                                }
                            }
                        textView.text = option.text ?: "Image Option ${index + 1}"
                    }
                    ContentType.TEXT -> {
                        textView.text = option.text ?: "Option ${index + 1}"
                    }
                }
                
                container.setOnClickListener {
                    selectAnswer(index)
                }
            }
        }
    }
    
    private fun selectAnswer(answerIndex: Int) {
        selectedAnswer = answerIndex
        updateButtonStates()
        binding.tvStatus.text = "Answer selected! Hit next to proceed"
        
        // Haptic feedback on answer selection
        VibratorHelper.vibrateLight(requireContext())
        
        // Play the video for the selected option if it has one
        playSelectedOptionVideo(answerIndex)
        
        // Notify listener immediately to proceed
        onAnswerSelectedListener?.invoke(selectedAnswer)
    }
    
    /**
     * Plays the video for the selected option and pauses other option videos
     */
    private fun playSelectedOptionVideo(selectedIndex: Int) {
        try {
            // Get all option video views (both vertical and grid layouts)
            val verticalVideoViews = listOf(
                binding.videoOption1,
                binding.videoOption2,
                binding.videoOption3,
                binding.videoOption4
            )
            
            val gridVideoViews = listOf(
                binding.gridVideoOption1,
                binding.gridVideoOption2,
                binding.gridVideoOption3,
                binding.gridVideoOption4
            )
            
            // Determine which layout is visible
            val isGridLayout = binding.gridOptionsContainer.visibility == View.VISIBLE
            val videoViews = if (isGridLayout) gridVideoViews else verticalVideoViews
            
            // Pause all option videos first
            videoViews.forEachIndexed { index, videoView ->
                if (videoView.visibility == View.VISIBLE && videoView is PlayerView) {
                    val player = players[videoView] ?: videoView.player
                    player?.let { exoPlayer ->
                        try {
                            exoPlayer.pause()
                        } catch (e: Exception) {
                            android.util.Log.w("ExoPlayer", "Error pausing video: ${e.message}")
                        }
                    }
                }
            }
            
            // Play the selected option's video
            if (selectedIndex < videoViews.size) {
                val selectedVideoView = videoViews[selectedIndex]
                if (selectedVideoView.visibility == View.VISIBLE && selectedVideoView is PlayerView) {
                    var player = players[selectedVideoView] ?: selectedVideoView.player
                    
                    // If player doesn't exist, try to create it (might have been skipped due to memory)
                    if (player == null && currentQuestion != null && selectedIndex < currentQuestion!!.options.size) {
                        val option = currentQuestion!!.options[selectedIndex]
                        val media = option.primaryMedia()
                        if (option.type == ContentType.VIDEO && media?.isVideo() == true) {
                            try {
                                initializePlayerForView(selectedVideoView, media)
                                player = players[selectedVideoView] ?: selectedVideoView.player
                            } catch (e: Exception) {
                                android.util.Log.w("ExoPlayer", "Error creating player for selected option: ${e.message}")
                            }
                        }
                    }
                    
                    player?.let { exoPlayer ->
                        try {
                            exoPlayer.seekTo(0)
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        } catch (e: Exception) {
                            android.util.Log.w("ExoPlayer", "Error playing selected video: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error in playSelectedOptionVideo: ${e.message}")
        }
    }


    
    private fun updateButtonStates() {
        // Update vertical layout containers
        val verticalContainers = listOf(
            binding.option1Container,
            binding.option2Container,
            binding.option3Container,
            binding.option4Container
        )
        
        // Update grid layout containers
        val gridContainers = listOf(
            binding.gridOption1Container,
            binding.gridOption2Container,
            binding.gridOption3Container,
            binding.gridOption4Container
        )
        
        // Update both layouts
        updateContainerStates(verticalContainers)
        updateContainerStates(gridContainers)
    }
    
    private fun updateContainerStates(containers: List<View>) {
        containers.forEachIndexed { index, container ->
            if (index == selectedAnswer) {
                // Selected answer - swap to selected drawable (keeps corners + thicker primary stroke)
                container.setBackgroundResource(com.example.kamaynikasyon.R.drawable.bg_answer_btn_selected)
            } else {
                // Idle state - default rounded button drawable
                container.setBackgroundResource(com.example.kamaynikasyon.R.drawable.bg_answer_btn)
            }
            // Keep containers enabled for reselection (unlike lessons)
            container.isEnabled = true
        }
    }
    
    /**
     * Tries to get Supabase URL first, falls back to asset URI if Supabase is not available
     * For videos, downloads from Supabase first if available
     */
    private suspend fun trySupabaseThenAsset(media: MediaResource): android.net.Uri? = withContext(Dispatchers.IO) {
        // For videos, try to download from Supabase first if available
        if (media.isVideo() && SupabaseConfig.isInitialized()) {
            try {
                val bucket = SupabaseConfig.getBucket(requireContext(), SupabaseConfig.BUCKET_MEDIA)
                val cachedPath = SupabaseStorage.downloadFile(requireContext(), bucket, media.path)
                if (cachedPath != null) {
                    val fileUri = android.net.Uri.fromFile(java.io.File(cachedPath))
                    android.util.Log.d("QuizQuestionFragment", "Using downloaded Supabase video: $fileUri")
                    return@withContext fileUri
                }
            } catch (e: Exception) {
                android.util.Log.w("QuizQuestionFragment", "Failed to download video from Supabase, trying URL or asset", e)
            }
        }
        
        // Try Supabase URL first if available (for images or if download failed)
        val supabaseUri = media.asUriWithSupabase(requireContext())
        if (supabaseUri != null) {
            val uriString = supabaseUri.toString()
            // If it's a Supabase URL (http/https), use it
            if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                android.util.Log.d("QuizQuestionFragment", "Using Supabase URL: $uriString")
                return@withContext supabaseUri
            }
        }
        
        // Fallback to asset URI
        val assetUri = media.asUri()
        android.util.Log.d("QuizQuestionFragment", "Using asset URI: $assetUri")
        return@withContext assetUri
    }
    
    /**
     * Initializes an ExoPlayer for a PlayerView
     * Uses preloaded players when available (similar to GestureMatchAdapter pattern)
     */
    private fun initializePlayerForView(playerView: PlayerView, media: MediaResource) {
        try {
            val key = media.path
            
            // Release previous player for this view if any
            releasePlayerForView(playerView)
            
            // Try to use preloaded player first (keyed by media path)
            val preloadedPlayer = preloadedPlayers[key]
            
            if (preloadedPlayer != null) {
                // Use preloaded player (shared instance)
                players[playerView] = preloadedPlayer
                playerView.player = preloadedPlayer
                preloadedPlayer.repeatMode = Player.REPEAT_MODE_ALL
                preloadedPlayer.volume = 0f
                preloadedPlayer.seekTo(0)
                // Auto-play all videos (both question and option videos)
                preloadedPlayer.playWhenReady = true
                preloadedPlayer.play()
            } else {
                // Lazy load: create player only when needed (for option videos)
                // Check memory before creating new player
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val memoryUsagePercent = (usedMemory * 100) / maxMemory
                
                // If memory usage is too high (>80%), don't create new players
                if (memoryUsagePercent > 80) {
                    android.util.Log.w("ExoPlayer", "Memory usage too high ($memoryUsagePercent%), skipping player creation for: ${media.path}")
                    playerView.visibility = View.GONE
                    return
                }
                
                try {
                    val context = view?.context ?: requireContext()
                    val exoPlayer = ExoPlayer.Builder(context).build()
                    players[playerView] = exoPlayer
                    playerView.player = exoPlayer
                    
                    // Configure player
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                    // Auto-play all videos (both question and option videos)
                    // This allows users to see all answer options playing simultaneously
                    exoPlayer.playWhenReady = true
                    
                    // Load video URI asynchronously (Supabase download/cache or URL/asset)
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val videoUri = trySupabaseThenAsset(media)
                            
                            if (videoUri == null || videoUri.toString().isEmpty()) {
                                android.util.Log.w("QuizQuestionFragment", "Invalid video URI for path: ${media.path}")
                                playerView.visibility = View.GONE
                                return@launch
                            }
                            
                            // Create media item
                            val mediaItem = MediaItem.Builder()
                                .setUri(videoUri)
                                .build()
                            
                            // Set the media item to be played
                            exoPlayer.setMediaItem(mediaItem)
                            
                            // Add error listener with fallback (similar to Dictionary)
                            exoPlayer.addListener(object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    android.util.Log.e("QuizQuestionFragment", "ExoPlayer error: ${error.message}", error)
                                    // Try asset URI as fallback if Supabase URL failed
                                    val currentUri = videoUri.toString()
                                    if (currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
                                        android.util.Log.w("QuizQuestionFragment", "Supabase video failed, trying asset URI")
                                        val assetUri = media.asUri()
                                        if (assetUri.toString() != currentUri) {
                                            try {
                                                exoPlayer.setMediaItem(MediaItem.Builder().setUri(assetUri).build())
                                                exoPlayer.prepare()
                                            } catch (e: Exception) {
                                                android.util.Log.e("QuizQuestionFragment", "Error loading asset video", e)
                                                playerView.visibility = View.GONE
                                            }
                                        } else {
                                            playerView.visibility = View.GONE
                                        }
                                    } else {
                                        playerView.visibility = View.GONE
                                    }
                                }
                            })
                            
                            // Prepare the player
                            exoPlayer.prepare()
                        } catch (e: Exception) {
                            android.util.Log.e("QuizQuestionFragment", "Error loading video URI: ${e.message}", e)
                            // Fallback to asset URI
                            try {
                                val assetUri = media.asUri()
                                val mediaItem = MediaItem.Builder().setUri(assetUri).build()
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                            } catch (fallbackError: Exception) {
                                android.util.Log.e("QuizQuestionFragment", "Error loading asset video (fallback)", fallbackError)
                                playerView.visibility = View.GONE
                            }
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    android.util.Log.e("ExoPlayer", "OutOfMemoryError creating player for: ${media.path}", e)
                    playerView.visibility = View.GONE
                    // Clean up on OOM
                    releasePlayerForView(playerView)
                }
            }
        } catch (e: IllegalStateException) {
            // Handle case where player is being created after fragment is destroyed
            android.util.Log.w("ExoPlayer", "Fragment not attached, skipping player creation")
            playerView.visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ExoPlayer", "Error initializing player: ${e.message}", e)
            playerView.visibility = View.GONE
            // Clean up on error
            releasePlayerForView(playerView)
        }
    }
    
    /**
     * Releases the ExoPlayer for a specific view
     * Similar to GestureMatchAdapter - don't release preloaded players
     */
    private fun releasePlayerForView(playerView: View) {
        try {
            val exoPlayer = players[playerView]
            if (exoPlayer != null) {
                // Check if this is a preloaded player (shared instance)
                val isPreloaded = preloadedPlayers.values.contains(exoPlayer)
                
                if (!isPreloaded) {
                    // Only release if it's not a preloaded/shared player
                    try {
                        exoPlayer.stop()
                        exoPlayer.release()
                    } catch (e: Exception) {
                        android.util.Log.w("ExoPlayer", "Error releasing player: ${e.message}")
                    }
                } else {
                    // Just stop playback, don't release shared player
                    try {
                        exoPlayer.stop()
                    } catch (e: Exception) {
                        android.util.Log.w("ExoPlayer", "Error stopping preloaded player: ${e.message}")
                    }
                }
                players.remove(playerView)
            }
            if (playerView is PlayerView) {
                playerView.player = null
            }
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error in releasePlayerForView: ${e.message}")
        }
    }
    
    /**
     * Releases all ExoPlayer instances
     * Releases preloaded players (similar to GestureMatchActivity.onDestroy())
     */
    private fun releaseAllPlayers() {
        try {
            // Release non-preloaded players
            players.values.forEach { exoPlayer ->
                val isPreloaded = preloadedPlayers.values.contains(exoPlayer)
                if (!isPreloaded) {
                    try {
                        exoPlayer.stop()
                        exoPlayer.release()
                    } catch (e: Exception) {
                        android.util.Log.w("ExoPlayer", "Error releasing player: ${e.message}")
                    }
                }
            }
            players.clear()
            
            // Release preloaded players
            preloadedPlayers.values.forEach { player ->
                try {
                    player.stop()
                    player.release()
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error releasing preloaded player: ${e.message}")
                }
            }
            preloadedPlayers.clear()
            
            // Clear all player views safely
            try {
                binding.videoViewQuestion.player = null
                binding.videoOption1.player = null
                binding.videoOption2.player = null
                binding.videoOption3.player = null
                binding.videoOption4.player = null
                binding.gridVideoOption1.player = null
                binding.gridVideoOption2.player = null
                binding.gridVideoOption3.player = null
                binding.gridVideoOption4.player = null
            } catch (e: Exception) {
                android.util.Log.w("ExoPlayer", "Error clearing player views: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ExoPlayer", "Error in releaseAllPlayers: ${e.message}", e)
        }
    }
    
    fun setOnAnswerSelectedListener(listener: (Int) -> Unit) {
        onAnswerSelectedListener = listener
    }
    
    fun setSelectedAnswer(answerIndex: Int) {
        if (answerIndex >= 0 && answerIndex < (currentQuestion?.options?.size ?: 0)) {
            selectedAnswer = answerIndex
            updateButtonStates()
            binding.tvStatus.text = "Answer selected! Click Submit to proceed"
            // Play the video for the selected option if it has one
            playSelectedOptionVideo(answerIndex)
            // Notify listener
            onAnswerSelectedListener?.invoke(selectedAnswer)
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            players.values.forEach { exoPlayer ->
                try {
                    exoPlayer.pause()
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error pausing player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error in onPause: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            players.values.forEach { exoPlayer ->
                try {
                    if (exoPlayer.playWhenReady) {
                        exoPlayer.play()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error resuming player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error in onResume: ${e.message}")
        }
    }
    
    /**
     * Pauses all currently playing videos
     */
    private fun pauseAllVideos() {
        try {
            players.values.forEach { exoPlayer ->
                try {
                    exoPlayer.pause()
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error pausing video: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error in pauseAllVideos: ${e.message}")
        }
    }
    
    /**
     * Resumes all videos that were set to play when ready
     */
    private fun resumeAllVideos() {
        try {
            players.values.forEach { exoPlayer ->
                try {
                    if (exoPlayer.playWhenReady) {
                        exoPlayer.play()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error resuming video: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error in resumeAllVideos: ${e.message}")
        }
    }
    
    /**
     * Shows a dialog with the video playing in a larger size
     * @param media The media resource to play
     * @param text Optional text to display (for answer options)
     */
    private fun showVideoDialog(media: MediaResource, text: String?) {
        val context = requireContext()
        val dialog = Dialog(context)
        val dialogBinding = DialogVideoPreviewBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(dialogBinding.root)
        
        // Configure dialog window
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.7f)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Setup video player
        val dialogPlayer = ExoPlayer.Builder(context).build()
        dialogBinding.dialogVideoPlayer.player = dialogPlayer
        
        // Load video URI asynchronously (Supabase download/cache or URL/asset)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val videoUri = trySupabaseThenAsset(media)
                
                if (videoUri == null || videoUri.toString().isEmpty()) {
                    android.util.Log.w("QuizQuestionFragment", "Invalid video URI for dialog: ${media.path}")
                    dialog.dismiss()
                    return@launch
                }
                
                val mediaItem = MediaItem.Builder().setUri(videoUri).build()
                dialogPlayer.setMediaItem(mediaItem)
                dialogPlayer.repeatMode = Player.REPEAT_MODE_ALL
                dialogPlayer.volume = 0f
                
                // Add error listener with fallback (similar to Dictionary)
                dialogPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e("QuizQuestionFragment", "Dialog ExoPlayer error: ${error.message}", error)
                        // Try asset URI as fallback if Supabase URL failed
                        val currentUri = videoUri.toString()
                        if (currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
                            android.util.Log.w("QuizQuestionFragment", "Supabase video failed in dialog, trying asset URI")
                            val assetUri = media.asUri()
                            if (assetUri.toString() != currentUri) {
                                try {
                                    dialogPlayer.setMediaItem(MediaItem.Builder().setUri(assetUri).build())
                                    dialogPlayer.prepare()
                                } catch (e: Exception) {
                                    android.util.Log.e("QuizQuestionFragment", "Error loading asset video in dialog", e)
                                }
                            }
                        }
                    }
                })
                
                dialogPlayer.prepare()
                dialogPlayer.playWhenReady = true
            } catch (e: Exception) {
                android.util.Log.e("QuizQuestionFragment", "Error loading video URI for dialog: ${e.message}", e)
                // Fallback to asset URI
                try {
                    val assetUri = media.asUri()
                    val mediaItem = MediaItem.Builder().setUri(assetUri).build()
                    dialogPlayer.setMediaItem(mediaItem)
                    dialogPlayer.repeatMode = Player.REPEAT_MODE_ALL
                    dialogPlayer.volume = 0f
                    dialogPlayer.prepare()
                    dialogPlayer.playWhenReady = true
                } catch (fallbackError: Exception) {
                    android.util.Log.e("QuizQuestionFragment", "Error loading asset video in dialog (fallback)", fallbackError)
                    dialog.dismiss()
                }
            }
        }
        
        // Text is hidden in preview dialog (only shown in dictionary word details)
        dialogBinding.dialogVideoText.visibility = View.GONE
        
        // Close button
        dialogBinding.btnCloseDialog.setOnClickListener {
            dialogPlayer.stop()
            dialogPlayer.release()
            dialog.dismiss()
        }
        
        // Clean up when dialog is dismissed
        dialog.setOnDismissListener {
            dialogPlayer.stop()
            dialogPlayer.release()
            // Resume all videos that were playing before
            resumeAllVideos()
        }
        
        dialog.show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        releaseAllPlayers()
        _binding = null
    }
}




