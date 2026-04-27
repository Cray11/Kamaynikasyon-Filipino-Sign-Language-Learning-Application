package com.example.kamaynikasyon.features.lessons.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.media.MediaResource
import com.example.kamaynikasyon.core.media.MediaType
import com.example.kamaynikasyon.core.supabase.SupabaseConfig
import com.example.kamaynikasyon.core.supabase.SupabaseStorage
import com.example.kamaynikasyon.core.utils.showError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.example.kamaynikasyon.databinding.FragmentLessonTextBinding
import com.example.kamaynikasyon.databinding.DialogVideoPreviewBinding
import com.example.kamaynikasyon.features.lessons.data.models.LessonPage
import com.example.kamaynikasyon.features.lessons.data.models.primaryMedia
import android.app.Dialog

class LessonTextFragment : Fragment() {
    
    private var _binding: FragmentLessonTextBinding? = null
    private val binding get() = _binding!!
    private var currentPage: LessonPage? = null
    private var player: ExoPlayer? = null
    
    companion object {
        private const val ARG_PAGE = "page"
        
        fun newInstance(page: LessonPage): LessonTextFragment {
            val fragment = LessonTextFragment()
            val args = Bundle()
            args.putParcelable(ARG_PAGE, page)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLessonTextBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val page = arguments?.let { BundleCompat.getParcelable(it, ARG_PAGE, LessonPage::class.java) }
        page?.let { setupPage(it) }
    }
    
    private fun setupPage(page: LessonPage) {
        currentPage = page
        binding.tvTitle.text = page.title
        binding.tvContent.text = page.content
        
        // Release previous player if any
        releasePlayer()
        
        // Handle media if present
        val media = page.primaryMedia()
        if (media != null) {
            displayMedia(media)
        } else {
            binding.frameLayoutVideo.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.imageView.visibility = View.GONE
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
                    android.util.Log.d("LessonTextFragment", "Using downloaded Supabase video: $fileUri")
                    return@withContext fileUri
                }
            } catch (e: Exception) {
                android.util.Log.w("LessonTextFragment", "Failed to download video from Supabase, trying URL or asset", e)
            }
        }
        
        // Try Supabase URL first if available (for images or if download failed)
        val supabaseUri = media.asUriWithSupabase(requireContext())
        if (supabaseUri != null) {
            val uriString = supabaseUri.toString()
            // If it's a Supabase URL (http/https), use it
            if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                android.util.Log.d("LessonTextFragment", "Using Supabase URL: $uriString")
                return@withContext supabaseUri
            }
        }
        
        // Fallback to asset URI
        val assetUri = media.asUri()
        android.util.Log.d("LessonTextFragment", "Using asset URI: $assetUri")
        return@withContext assetUri
    }
    
    private fun displayMedia(media: MediaResource) {
        try {
            when (media.type) {
                MediaType.VIDEO -> {
                    binding.frameLayoutVideo.visibility = View.VISIBLE
                    binding.videoView.visibility = View.VISIBLE
                    binding.btnMenuVideo.visibility = View.VISIBLE
                    binding.imageView.visibility = View.GONE
                    initializePlayer(media)
                    binding.btnMenuVideo.setOnClickListener {
                        player?.pause()
                        showVideoDialog(media, null)
                    }
                }
                MediaType.IMAGE -> {
                    binding.frameLayoutVideo.visibility = View.GONE
                    binding.videoView.visibility = View.GONE
                    binding.imageView.visibility = View.VISIBLE
                    // Use asUriWithSupabase to support both Supabase URLs and asset URIs
                    val imageUri = media.asUriWithSupabase(requireContext())
                    binding.imageView.setImageURI(imageUri)
                }
                else -> {
                    binding.frameLayoutVideo.visibility = View.GONE
                    binding.videoView.visibility = View.GONE
                    binding.imageView.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            showError(R.string.error_loading_media)
            binding.frameLayoutVideo.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.imageView.visibility = View.GONE
        }
    }
    
    private fun initializePlayer(media: MediaResource) {
        try {
            val context = view?.context ?: requireContext()
            val exoPlayer = ExoPlayer.Builder(context).build()
            player = exoPlayer
            binding.videoView.player = exoPlayer
            
            // Configure player
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.playWhenReady = true
            
            // Load video URI asynchronously (Supabase download/cache or URL/asset)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val videoUri = trySupabaseThenAsset(media)
                    
                    if (videoUri == null || videoUri.toString().isEmpty()) {
                        android.util.Log.w("LessonTextFragment", "Invalid video URI for path: ${media.path}")
                        binding.frameLayoutVideo.visibility = View.GONE
                        binding.videoView.visibility = View.GONE
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
                            android.util.Log.e("LessonTextFragment", "ExoPlayer error: ${error.message}", error)
                            // Try asset URI as fallback if Supabase URL failed
                            val currentUri = videoUri.toString()
                            if (currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
                                android.util.Log.w("LessonTextFragment", "Supabase video failed, trying asset URI")
                                val assetUri = media.asUri()
                                if (assetUri.toString() != currentUri) {
                                    try {
                                        exoPlayer.setMediaItem(MediaItem.Builder().setUri(assetUri).build())
                                        exoPlayer.prepare()
                                    } catch (e: Exception) {
                                        android.util.Log.e("LessonTextFragment", "Error loading asset video", e)
                                        binding.frameLayoutVideo.visibility = View.GONE
                                        binding.videoView.visibility = View.GONE
                                    }
                                } else {
                                    binding.frameLayoutVideo.visibility = View.GONE
                                    binding.videoView.visibility = View.GONE
                                }
                            } else {
                                binding.frameLayoutVideo.visibility = View.GONE
                                binding.videoView.visibility = View.GONE
                            }
                        }
                    })
                    
                    // Prepare the player
                    exoPlayer.prepare()
                } catch (e: Exception) {
                    android.util.Log.e("LessonTextFragment", "Error loading video URI: ${e.message}", e)
                    // Fallback to asset URI
                    try {
                        val assetUri = media.asUri()
                        val mediaItem = MediaItem.Builder().setUri(assetUri).build()
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                    } catch (fallbackError: Exception) {
                        android.util.Log.e("LessonTextFragment", "Error loading asset video (fallback)", fallbackError)
                        binding.frameLayoutVideo.visibility = View.GONE
                        binding.videoView.visibility = View.GONE
                    }
                }
            }
        } catch (e: IllegalStateException) {
            android.util.Log.w("ExoPlayer", "Fragment not attached, skipping player creation")
            binding.frameLayoutVideo.visibility = View.GONE
            binding.videoView.visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ExoPlayer", "Error initializing player: ${e.message}", e)
            binding.frameLayoutVideo.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            releasePlayer()
        }
    }
    
    /**
     * Shows a dialog with the video playing in a larger size
     * @param media The media resource to play
     * @param text Optional text to display
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
                    android.util.Log.w("LessonTextFragment", "Invalid video URI for dialog: ${media.path}")
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
                        android.util.Log.e("LessonTextFragment", "Dialog ExoPlayer error: ${error.message}", error)
                        // Try asset URI as fallback if Supabase URL failed
                        val currentUri = videoUri.toString()
                        if (currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
                            android.util.Log.w("LessonTextFragment", "Supabase video failed in dialog, trying asset URI")
                            val assetUri = media.asUri()
                            if (assetUri.toString() != currentUri) {
                                try {
                                    dialogPlayer.setMediaItem(MediaItem.Builder().setUri(assetUri).build())
                                    dialogPlayer.prepare()
                                } catch (e: Exception) {
                                    android.util.Log.e("LessonTextFragment", "Error loading asset video in dialog", e)
                                }
                            }
                        }
                    }
                })
                
                dialogPlayer.prepare()
                dialogPlayer.playWhenReady = true
            } catch (e: Exception) {
                android.util.Log.e("LessonTextFragment", "Error loading video URI for dialog: ${e.message}", e)
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
                    android.util.Log.e("LessonTextFragment", "Error loading asset video in dialog (fallback)", fallbackError)
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
            // Resume the main video player if it was playing
            player?.let { exoPlayer ->
                try {
                    if (exoPlayer.playWhenReady) {
                        exoPlayer.play()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error resuming video: ${e.message}")
                }
            }
        }
        
        dialog.show()
    }
    
    private fun releasePlayer() {
        try {
            player?.let { exoPlayer ->
                try {
                    exoPlayer.stop()
                    exoPlayer.release()
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error releasing player: ${e.message}")
                }
            }
            player = null
            binding.videoView.player = null
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error in releasePlayer: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            player?.pause()
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error pausing player: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            player?.let { exoPlayer ->
                if (exoPlayer.playWhenReady) {
                    exoPlayer.play()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExoPlayer", "Error resuming player: ${e.message}")
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        _binding = null
    }
}

