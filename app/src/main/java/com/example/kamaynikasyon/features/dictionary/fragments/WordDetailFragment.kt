package com.example.kamaynikasyon.features.dictionary.fragments

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.kamaynikasyon.core.media.MediaResource
import com.example.kamaynikasyon.databinding.DialogVideoPreviewBinding
import com.example.kamaynikasyon.features.dictionary.data.repositories.DictionaryRepository
import com.example.kamaynikasyon.features.dictionary.data.models.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordDetailFragment : Fragment() {

    private lateinit var backButton: ImageButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var headerWordName: TextView
    private lateinit var wordName: TextView
    private lateinit var wordCategory: TextView
    private lateinit var wordDescription: TextView
    private lateinit var playerView: PlayerView
    private lateinit var imageView: ImageView
    private lateinit var btnMenuVideo: ImageButton
    private lateinit var videoLoadingSkeleton: View
    private lateinit var imageLoadingSkeleton: View
    private lateinit var wordNameSkeleton: View
    private lateinit var wordCategorySkeleton: View
    private lateinit var wordDescriptionSkeleton: View
    private var player: ExoPlayer? = null
    
    private lateinit var dictionaryRepository: DictionaryRepository
    private var word: Word? = null
    
    // Callback interfaces
    private var onBackClickListener: (() -> Unit)? = null
    private var onWordClickListener: ((String) -> Unit)? = null
    
    private val wordId: String by lazy {
        arguments?.getString("wordId") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_word_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRepository()
        setupSwipeRefresh()
        setupBackButton()
        loadWordDetails()
    }
    
    override fun onStart() {
        super.onStart()
        // Only initialize media if word is already loaded (e.g., on fragment recreation)
        if (word != null) {
            initializeMedia()
        }
    }

    override fun onResume() {
        super.onResume()
        if (player == null && word?.primaryMedia()?.isVideo() == true) {
            initializeMedia()
        }
    }
    
    override fun onPause() {
        super.onPause()
        releasePlayer()
    }
    
    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
    }

    private fun initializeViews(view: View) {
        backButton = view.findViewById(R.id.btnBack)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        headerWordName = view.findViewById(R.id.headerWordName)
        wordName = view.findViewById(R.id.wordName)
        wordCategory = view.findViewById(R.id.wordCategory)
        wordDescription = view.findViewById(R.id.wordDescription)
        playerView = view.findViewById(R.id.playerView)
        imageView = view.findViewById(R.id.imageView)
        btnMenuVideo = view.findViewById(R.id.btn_menu_video)
        videoLoadingSkeleton = view.findViewById(R.id.videoLoadingSkeleton)
        imageLoadingSkeleton = view.findViewById(R.id.imageLoadingSkeleton)
        wordNameSkeleton = view.findViewById(R.id.wordNameSkeleton)
        wordCategorySkeleton = view.findViewById(R.id.wordCategorySkeleton)
        wordDescriptionSkeleton = view.findViewById(R.id.wordDescriptionSkeleton)
        
        // Initialize views as hidden initially
        playerView.visibility = View.GONE
        imageView.visibility = View.GONE
        btnMenuVideo.visibility = View.GONE
        videoLoadingSkeleton.visibility = View.GONE
        imageLoadingSkeleton.visibility = View.GONE
        
        // Text views start hidden (skeletons visible)
        wordName.visibility = View.GONE
        wordCategory.visibility = View.GONE
        wordDescription.visibility = View.GONE
    }

    private fun setupRepository() {
        dictionaryRepository = DictionaryRepository(requireContext())
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadWordDetails()
        }
        swipeRefresh.setColorSchemeResources(
            R.color.primary_color,
            R.color.secondary_color
        )
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackClickListener?.invoke()
        }
    }
    
    // Callback setter methods
    fun setOnBackClickListener(listener: () -> Unit) {
        onBackClickListener = listener
    }
    
    fun setOnWordClickListener(listener: (String) -> Unit) {
        onWordClickListener = listener
    }

    private fun loadWordDetails() {
        // Show loading skeletons for text content
        showTextLoadingSkeletons()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val allWords = withContext(Dispatchers.IO) {
                    dictionaryRepository.getAllWords()
                }
                word = allWords.find { it.id == wordId }
                
                word?.let { wordData ->
                    // Update header and content with word name
                    headerWordName.text = wordData.name
                    wordName.text = wordData.name
                    wordCategory.text = wordData.category
                    wordDescription.text = wordData.description
                    
                    // Hide loading skeletons and show actual content
                    hideTextLoadingSkeletons()
                    
                    // Initialize media after word is loaded
                    initializeMedia()
                } ?: run {
                    android.util.Log.e("WordDetailFragment", "Word not found with ID: $wordId")
                    hideTextLoadingSkeletons()
                }
                swipeRefresh.isRefreshing = false
            } catch (e: Exception) {
                android.util.Log.e("WordDetailFragment", "Error loading word details", e)
                hideTextLoadingSkeletons()
                swipeRefresh.isRefreshing = false
            }
        }
    }
    
    private fun showTextLoadingSkeletons() {
        wordNameSkeleton.visibility = View.VISIBLE
        wordCategorySkeleton.visibility = View.VISIBLE
        wordDescriptionSkeleton.visibility = View.VISIBLE
        wordName.visibility = View.GONE
        wordCategory.visibility = View.GONE
        wordDescription.visibility = View.GONE
    }
    
    private fun hideTextLoadingSkeletons() {
        wordNameSkeleton.visibility = View.GONE
        wordCategorySkeleton.visibility = View.GONE
        wordDescriptionSkeleton.visibility = View.GONE
        wordName.visibility = View.VISIBLE
        wordCategory.visibility = View.VISIBLE
        wordDescription.visibility = View.VISIBLE
    }
    
    private fun initializeMedia() {
        word?.let { wordData ->
            when (val media = wordData.primaryMedia()) {
                null -> {
                    android.util.Log.d("WordDetailFragment", "No media found for word: ${wordData.name}")
                    playerView.visibility = View.GONE
                    imageView.visibility = View.GONE
                    btnMenuVideo.visibility = View.GONE
                    videoLoadingSkeleton.visibility = View.GONE
                    imageLoadingSkeleton.visibility = View.GONE
                }
                else -> {
                    android.util.Log.d("WordDetailFragment", "Media found - Type: ${media.type}, Path: ${media.path}")
                    if (media.isImage()) {
                        displayImage(media)
                    } else if (media.isVideo()) {
                        initializePlayer(media)
                } else {
                    android.util.Log.w("WordDetailFragment", "Unknown media type: ${media.type}")
                    playerView.visibility = View.GONE
                    imageView.visibility = View.GONE
                    btnMenuVideo.visibility = View.GONE
                    videoLoadingSkeleton.visibility = View.GONE
                    imageLoadingSkeleton.visibility = View.GONE
                }
                }
            }
        } ?: run {
            android.util.Log.w("WordDetailFragment", "Word data is null, cannot initialize media")
        }
    }
    
    private fun displayImage(media: MediaResource) {
        playerView.visibility = View.GONE
        videoLoadingSkeleton.visibility = View.GONE
        btnMenuVideo.visibility = View.GONE
        imageView.visibility = View.GONE
        
        // Show loading skeleton while image loads
        imageLoadingSkeleton.visibility = View.VISIBLE
        imageView.visibility = View.GONE

        // Use asUriWithSupabase to get Supabase URL if available, otherwise fallback to asset URI
        val imageUri = media.asUriWithSupabase(requireContext())
        
        // Load image with Glide using CustomTarget to detect when real image is loaded
        Glide.with(this)
            .load(imageUri)
            .placeholder(R.drawable.default_image)
            .error(R.drawable.default_image)
            .into(object : CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                    // optional - called when the resource is cleared
                }

                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    // Image is fully loaded (not placeholder) - hide skeleton and show image
                    imageLoadingSkeleton.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    imageView.setImageDrawable(resource)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    // On error, hide skeleton and show error placeholder
                    imageLoadingSkeleton.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    if (errorDrawable != null) {
                        imageView.setImageDrawable(errorDrawable)
                    }
                }
            })
    }
    
    private fun initializePlayer(media: MediaResource) {
        // Hide image view and show loading skeleton
        imageView.visibility = View.GONE
        imageLoadingSkeleton.visibility = View.GONE
        playerView.visibility = View.GONE
        btnMenuVideo.visibility = View.GONE
        
        // Show loading skeleton while video loads
        videoLoadingSkeleton.visibility = View.VISIBLE
        
        // Setup menu button click listener
        btnMenuVideo.setOnClickListener {
            // Track if player was playing before pausing
            val wasPlaying = player?.isPlaying == true
            player?.pause()
            showVideoDialog(media, word?.name, wasPlaying)
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Try Supabase first, then fallback to asset URI
                val videoUri = trySupabaseThenAsset(media)
                
                // Log the URI for debugging
                android.util.Log.d("WordDetailFragment", "Loading video - Path: ${media.path}, URI: $videoUri")
                
                // Validate URI
                if (videoUri == null || videoUri.toString().isEmpty()) {
                    android.util.Log.w("WordDetailFragment", "Invalid video URI for path: ${media.path}, falling back to default video")
                    loadDefaultVideo()
                    return@launch
                }
                
                // videoUri is guaranteed to be non-null here, but we need to help the compiler
                val finalVideoUri = videoUri!!
                
                val exoPlayer = ExoPlayer.Builder(requireContext()).build()
                player = exoPlayer
                playerView.player = exoPlayer
                
                // Configure player
                exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                exoPlayer.playWhenReady = true
                
                // Store original media for fallback
                val originalMedia = media
                
                // Add error listener to catch playback errors and fallback to asset or default video
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e("WordDetailFragment", "ExoPlayer error: ${error.message}", error)
                        android.util.Log.e("WordDetailFragment", "Error type: ${error.errorCode}, cause: ${error.cause?.message}")
                        android.util.Log.e("WordDetailFragment", "Video URI was: $finalVideoUri")
                        
                        // Release the failed player
                        releasePlayer()
                        
                        // Show loading skeleton again while trying fallback
                        videoLoadingSkeleton.visibility = View.VISIBLE
                        playerView.visibility = View.GONE
                        
                        // If we tried Supabase and it failed, try asset URI
                        val currentUri = finalVideoUri.toString()
                        if (currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
                            android.util.Log.w("WordDetailFragment", "Supabase video failed, trying asset URI")
                            val assetUri = originalMedia.asUri()
                            if (assetUri.toString() != currentUri) {
                                tryLoadVideoUri(assetUri, originalMedia)
                            } else {
                                android.util.Log.w("WordDetailFragment", "Asset URI same, falling back to default video")
                                loadDefaultVideo()
                            }
                        } else {
                            // Already tried asset, fallback to default video
                            android.util.Log.w("WordDetailFragment", "Asset video also failed, falling back to default video")
                            loadDefaultVideo()
                        }
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        android.util.Log.d("WordDetailFragment", "Video playing state changed: $isPlaying")
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Hide loading skeleton and show player when video is ready
                        if (playbackState == Player.STATE_READY) {
                            videoLoadingSkeleton.visibility = View.GONE
                            playerView.visibility = View.VISIBLE
                            btnMenuVideo.visibility = View.VISIBLE
                        } else if (playbackState == Player.STATE_BUFFERING) {
                            // Keep loading skeleton visible while buffering
                            videoLoadingSkeleton.visibility = View.VISIBLE
                            playerView.visibility = View.GONE
                        }
                    }
                })
                
                val mediaItem = MediaItem.Builder()
                    .setUri(finalVideoUri)
                    .build()
                
                // Set the media item to be played
                exoPlayer.setMediaItem(mediaItem)
                
                // Prepare the player
                exoPlayer.prepare()
                
                android.util.Log.d("WordDetailFragment", "ExoPlayer prepared successfully for: ${media.path}")
            } catch (e: IllegalStateException) {
                android.util.Log.w("WordDetailFragment", "Fragment not attached, skipping player creation")
                playerView.visibility = View.GONE
                videoLoadingSkeleton.visibility = View.GONE
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("WordDetailFragment", "Error initializing player: ${e.message}", e)
                android.util.Log.e("WordDetailFragment", "Media path: ${media.path}")
                android.util.Log.w("WordDetailFragment", "Falling back to asset URI or default video")
                releasePlayer()
                // Try asset URI first, then default video
                val assetUri = media.asUri()
                tryLoadVideoUri(assetUri, media)
            }
        }
    }
    
    /**
     * Tries to get Supabase URL first, falls back to asset URI if Supabase is not available
     * For videos, downloads from Supabase first if available
     */
    private suspend fun trySupabaseThenAsset(media: MediaResource): Uri? = withContext(Dispatchers.IO) {
        // For videos, try to download from Supabase first if available
        if (media.isVideo() && SupabaseConfig.isInitialized()) {
            try {
                val bucket = SupabaseConfig.getBucket(requireContext(), SupabaseConfig.BUCKET_MEDIA)
                val cachedPath = SupabaseStorage.downloadFile(requireContext(), bucket, media.path)
                if (cachedPath != null) {
                    val fileUri = Uri.fromFile(java.io.File(cachedPath))
                    android.util.Log.d("WordDetailFragment", "Using downloaded Supabase video: $fileUri")
                    return@withContext fileUri
                }
            } catch (e: Exception) {
                android.util.Log.w("WordDetailFragment", "Failed to download video from Supabase, trying URL or asset", e)
            }
        }
        
        // Try Supabase URL first if available (for images or if download failed)
        val supabaseUri = media.asUriWithSupabase(requireContext())
        if (supabaseUri != null) {
            val uriString = supabaseUri.toString()
            // If it's a Supabase URL (http/https), use it
            if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                android.util.Log.d("WordDetailFragment", "Using Supabase URL: $uriString")
                return@withContext supabaseUri
            }
        }
        
        // Fallback to asset URI
        val assetUri = media.asUri()
        android.util.Log.d("WordDetailFragment", "Using asset URI: $assetUri")
        return@withContext assetUri
    }
    
    /**
     * Attempts to load a video from a specific URI
     */
    private fun tryLoadVideoUri(videoUri: Uri, media: MediaResource) {
        try {
            // Show loading skeleton
            videoLoadingSkeleton.visibility = View.VISIBLE
            playerView.visibility = View.GONE
            
            val exoPlayer = ExoPlayer.Builder(requireContext()).build()
            player = exoPlayer
            playerView.player = exoPlayer
            
            // Configure player
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.playWhenReady = true
            
            // Add error listener - if this also fails, use default video
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("WordDetailFragment", "Asset video also failed: ${error.message}")
                    releasePlayer()
                    loadDefaultVideo()
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    android.util.Log.d("WordDetailFragment", "Video playing state changed: $isPlaying")
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Hide loading skeleton and show player when video is ready
                    if (playbackState == Player.STATE_READY) {
                        videoLoadingSkeleton.visibility = View.GONE
                        playerView.visibility = View.VISIBLE
                        btnMenuVideo.visibility = View.VISIBLE
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        // Keep loading skeleton visible while buffering
                        videoLoadingSkeleton.visibility = View.VISIBLE
                        playerView.visibility = View.GONE
                    }
                }
            })
            
            val mediaItem = MediaItem.Builder()
                .setUri(videoUri)
                .build()
            
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            
            android.util.Log.d("WordDetailFragment", "Successfully loaded video from URI: $videoUri")
        } catch (e: Exception) {
            android.util.Log.e("WordDetailFragment", "Error loading video from URI: ${e.message}", e)
            releasePlayer()
            loadDefaultVideo()
        }
    }
    
    private fun loadDefaultVideo() {
        try {
            // Show loading skeleton
            videoLoadingSkeleton.visibility = View.VISIBLE
            playerView.visibility = View.GONE
            
            // Try to find a default video in assets or raw resources
            // First, try to find a default video in assets
            val defaultVideoPaths = listOf(
                "vid/default.mp4",
                "vid/default_video.mp4",
                "default.mp4",
                "default_video.mp4"
            )
            
            var defaultVideoUri: Uri? = null
            
            // Try to find default video in assets
            for (path in defaultVideoPaths) {
                try {
                    val assetUri = Uri.parse("file:///android_asset/$path")
                    // Check if file exists by trying to open it
                    requireContext().assets.open(path).use {
                        defaultVideoUri = assetUri
                        android.util.Log.d("WordDetailFragment", "Found default video at: $path")
                    }
                    // If we found it, break out of the loop
                    if (defaultVideoUri != null) {
                        break
                    }
                } catch (e: Exception) {
                    // File doesn't exist, try next path
                    continue
                }
            }
            
            // If no default video found in assets, try raw resource
            if (defaultVideoUri == null) {
                try {
                    // Try to use a video from raw resources if available
                    val rawResourceId = resources.getIdentifier("default_video", "raw", requireContext().packageName)
                    if (rawResourceId != 0) {
                        defaultVideoUri = Uri.parse("android.resource://${requireContext().packageName}/$rawResourceId")
                        android.util.Log.d("WordDetailFragment", "Using default video from raw resources")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WordDetailFragment", "No default video found in raw resources", e)
                }
            }
            
            if (defaultVideoUri == null) {
                android.util.Log.w("WordDetailFragment", "No default video found, hiding player")
                playerView.visibility = View.GONE
                videoLoadingSkeleton.visibility = View.GONE
                return
            }
            
            // Initialize player with default video
            val exoPlayer = ExoPlayer.Builder(requireContext()).build()
            player = exoPlayer
            playerView.player = exoPlayer
            
            // Configure player
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.playWhenReady = true
            
            // Add listener to handle loading state
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Hide loading skeleton and show player when video is ready
                    if (playbackState == Player.STATE_READY) {
                        videoLoadingSkeleton.visibility = View.GONE
                        playerView.visibility = View.VISIBLE
                        btnMenuVideo.visibility = View.VISIBLE
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        // Keep loading skeleton visible while buffering
                        videoLoadingSkeleton.visibility = View.VISIBLE
                        playerView.visibility = View.GONE
                    }
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("WordDetailFragment", "Default video playback error: ${error.message}")
                    videoLoadingSkeleton.visibility = View.GONE
                    playerView.visibility = View.GONE
                }
            })
            
            val mediaItem = MediaItem.Builder()
                .setUri(defaultVideoUri)
                .build()
            
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            
            android.util.Log.d("WordDetailFragment", "Default video loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e("WordDetailFragment", "Error loading default video: ${e.message}", e)
            playerView.visibility = View.GONE
            videoLoadingSkeleton.visibility = View.GONE
            releasePlayer()
        }
    }
    
    private fun releasePlayer() {
        player?.let { exoPlayer ->
            exoPlayer.release()
        }
        player = null
    }
    
    /**
     * Shows a dialog with an expanded video player
     * @param media The media resource to display
     * @param text Optional text to display (word name)
     * @param resumeMainPlayer Whether to resume the main player when dialog closes
     */
    private fun showVideoDialog(media: MediaResource, text: String?, resumeMainPlayer: Boolean = false) {
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
        
        // For dialog, use simple approach like lessons/quizzes - try Supabase URL first (synchronous), then asset
        // This ensures the dialog shows immediately and video loads quickly
        val videoUri = try {
            // Try Supabase URL first (synchronous check)
            val supabaseUri = media.asUriWithSupabase(context)
            if (supabaseUri != null) {
                val uriString = supabaseUri.toString()
                // If it's a Supabase URL (http/https), use it directly
                if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                    supabaseUri
                } else {
                    media.asUri()
                }
            } else {
                media.asUri()
            }
        } catch (e: Exception) {
            android.util.Log.w("WordDetailFragment", "Error getting video URI for dialog, using asset: ${e.message}")
            media.asUri()
        }
        
        val mediaItem = MediaItem.Builder().setUri(videoUri).build()
        dialogPlayer.setMediaItem(mediaItem)
        dialogPlayer.repeatMode = Player.REPEAT_MODE_ALL
        dialogPlayer.volume = 0f
        dialogPlayer.prepare()
        dialogPlayer.playWhenReady = true
        
        // Setup text if provided
        if (text != null && text.isNotEmpty()) {
            dialogBinding.dialogVideoText.text = text
            dialogBinding.dialogVideoText.visibility = View.VISIBLE
        } else {
            dialogBinding.dialogVideoText.visibility = View.GONE
        }
        
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
            // Resume main player if it was playing before
            if (resumeMainPlayer) {
                player?.play()
            }
        }
        
        dialog.show()
    }

}
