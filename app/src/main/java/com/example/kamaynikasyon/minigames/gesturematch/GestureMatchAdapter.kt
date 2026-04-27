package com.example.kamaynikasyon.minigames.gesturematch

import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.media.MediaResource
import com.example.kamaynikasyon.databinding.DialogVideoPreviewBinding
import kotlinx.coroutines.launch

data class GestureCard(
    val id: String,
    val isMedia: Boolean,
    val label: String,
    val media: MediaResource? = null,
    var isRevealed: Boolean = false,
    var isMatched: Boolean = false,
    var isMismatch: Boolean = false
)

class GestureMatchAdapter(
    private val items: MutableList<GestureCard>,
    private val onCardClicked: (position: Int, card: GestureCard) -> Unit,
    private val playerProvider: ((MediaResource) -> ExoPlayer?)? = null,
    private val videoUriProvider: (suspend (MediaResource) -> android.net.Uri?)? = null,
    private val lifecycleOwner: LifecycleOwner? = null
) : RecyclerView.Adapter<GestureMatchAdapter.CardVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gesture_card, parent, false)
        return CardVH(view)
    }

    override fun onBindViewHolder(holder: CardVH, position: Int) {
        holder.bind(items[position]) { onCardClicked(position, items[position]) }
    }

    override fun getItemCount(): Int = items.size

    fun updateCard(position: Int, updater: (GestureCard) -> Unit) {
        updater(items[position])
        notifyItemChanged(position)
    }

    inner class CardVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageMedia: ImageView = itemView.findViewById(R.id.image_media)
        private val textLabel: TextView = itemView.findViewById(R.id.text_label)
        private val textHidden: TextView = itemView.findViewById(R.id.text_hidden)
        private val playerView: PlayerView = itemView.findViewById(R.id.player_media)
        private val btnMenuVideo: ImageButton = itemView.findViewById(R.id.btn_menu_video)
        private val videoLoadingIndicator: com.google.android.material.progressindicator.CircularProgressIndicator = itemView.findViewById(R.id.videoLoadingIndicator)
        private var player: ExoPlayer? = null
        private var isUsingProvidedPlayer: Boolean = false

        fun bind(card: GestureCard, onClick: () -> Unit) {
            imageMedia.visibility = View.GONE
            playerView.visibility = View.GONE
            btnMenuVideo.visibility = View.GONE
            videoLoadingIndicator.visibility = View.GONE
            textLabel.visibility = View.GONE
            textHidden.visibility = View.GONE

            setStrokeColor(
                when {
                    card.isMatched -> itemView.resources.getColor(R.color.correct_color, null)
                    card.isMismatch -> itemView.resources.getColor(R.color.wrong_color, null)
                    card.isRevealed -> itemView.resources.getColor(R.color.primary_color, null)
                    else -> itemView.resources.getColor(R.color.gray_300, null)
                }
            )

            if (card.isMatched || card.isRevealed) {
                if (card.isMedia) {
                    val resource = card.media
                    when {
                        resource == null -> {
                            imageMedia.visibility = View.VISIBLE
                            imageMedia.setImageResource(R.drawable.default_image)
                            releasePlayer()
                        }
                        resource.isImage() -> {
                            imageMedia.visibility = View.VISIBLE
                            // Use asUriWithSupabase to support both Supabase URLs and asset URIs
                            val imageUri = resource.asUriWithSupabase(itemView.context)
                            val uriString = imageUri.toString()
                            
                            // Check if it's an asset URI (file:///android_asset/)
                            if (uriString.startsWith("file:///android_asset/")) {
                                // Load directly from assets using BitmapFactory (Glide can't handle asset URIs)
                                try {
                                    val normalizedPath = uriString.removePrefix("file:///android_asset/").replace("%20", " ")
                                    itemView.context.assets.open(normalizedPath).use { inputStream ->
                                        val bitmap = BitmapFactory.decodeStream(inputStream)
                                        if (bitmap != null) {
                                            imageMedia.setImageBitmap(bitmap)
                                        } else {
                                            imageMedia.setImageResource(R.drawable.default_image)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("GestureMatchAdapter", "Failed to load image from assets: ${resource.path}", e)
                                    imageMedia.setImageResource(R.drawable.default_image)
                                }
                            } else {
                                // Use Glide for Supabase URLs or other HTTP/HTTPS URLs
                                Glide.with(itemView)
                                    .load(imageUri)
                                    .placeholder(R.drawable.default_image)
                                    .error(R.drawable.default_image)
                                    .into(imageMedia)
                            }
                            releasePlayer()
                        }
                        resource.isVideo() -> {
                            // Hide image and show video container
                            imageMedia.visibility = View.GONE
                            playerView.visibility = View.GONE
                            btnMenuVideo.visibility = View.GONE
                            videoLoadingIndicator.visibility = View.GONE
                            
                            val provided = playerProvider?.invoke(resource)
                            if (provided != null) {
                                isUsingProvidedPlayer = true
                                player = provided
                                playerView.player = provided
                                provided.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                provided.volume = 0f
                                provided.seekTo(0)
                                provided.playWhenReady = true
                                provided.play()
                                // Preloaded player is ready, show it
                                videoLoadingIndicator.visibility = View.GONE
                                playerView.visibility = View.VISIBLE
                                btnMenuVideo.visibility = View.VISIBLE
                            } else {
                                isUsingProvidedPlayer = false
                                ensurePlayer()
                                
                                // Show loading indicator while video loads
                                videoLoadingIndicator.visibility = View.VISIBLE
                                playerView.visibility = View.GONE
                                btnMenuVideo.visibility = View.GONE
                                
                                // Load video URI asynchronously using trySupabaseThenAsset (like Dictionary)
                                if (videoUriProvider != null && lifecycleOwner != null) {
                                    lifecycleOwner.lifecycleScope.launch {
                                        try {
                                            val videoUri = videoUriProvider.invoke(resource)
                                            
                                            if (videoUri == null || videoUri.toString().isEmpty()) {
                                                Log.w("GestureMatchAdapter", "Invalid video URI for path: ${resource.path}")
                                                videoLoadingIndicator.visibility = View.GONE
                                                return@launch
                                            }
                                            
                                            val mediaItem = MediaItem.fromUri(videoUri)
                                            player?.setMediaItem(mediaItem)
                                            player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                            player?.volume = 0f
                                            player?.playWhenReady = true
                                            
                                            // Add error listener and playback state listener (similar to Dictionary)
                                            player?.addListener(object : androidx.media3.common.Player.Listener {
                                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                                    Log.e("GestureMatchAdapter", "ExoPlayer error: ${error.message}", error)
                                                    
                                                    // Check if it's an HTTP error (400, 404, etc.)
                                                    val isHttpError = error.cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException
                                                    val isInvalidResponse = error.cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                                                    
                                                    // Try asset URI as fallback if Supabase URL failed
                                                    val currentUri = videoUri.toString()
                                                    if ((currentUri.startsWith("http://") || currentUri.startsWith("https://")) || isHttpError || isInvalidResponse) {
                                                        Log.w("GestureMatchAdapter", "Supabase video failed (HTTP error or invalid URL), trying asset URI")
                                                        val assetUri = resource.asUri()
                                                        if (assetUri.toString() != currentUri) {
                                                            try {
                                                                // Remove old listener to avoid recursion
                                                                player?.removeListener(this)
                                                                player?.setMediaItem(MediaItem.fromUri(assetUri))
                                                                player?.prepare()
                                                                Log.d("GestureMatchAdapter", "Successfully switched to asset URI")
                                                            } catch (e: Exception) {
                                                                Log.e("GestureMatchAdapter", "Error loading asset video", e)
                                                                videoLoadingIndicator.visibility = View.GONE
                                                            }
                                                        } else {
                                                            // Already using asset URI, hide loading indicator
                                                            videoLoadingIndicator.visibility = View.GONE
                                                        }
                                                    } else {
                                                        // Not an HTTP error, just hide loading indicator
                                                        videoLoadingIndicator.visibility = View.GONE
                                                    }
                                                }
                                                
                                                override fun onPlaybackStateChanged(playbackState: Int) {
                                                    // Hide loading indicator and show player when video is ready (like Dictionary)
                                                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                                        videoLoadingIndicator.visibility = View.GONE
                                                        playerView.visibility = View.VISIBLE
                                                        btnMenuVideo.visibility = View.VISIBLE
                                                    } else if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                                                        // Keep loading indicator visible while buffering
                                                        videoLoadingIndicator.visibility = View.VISIBLE
                                                        playerView.visibility = View.GONE
                                                    }
                                                }
                                            })
                                            
                                            player?.prepare()
                                        } catch (e: Exception) {
                                            Log.e("GestureMatchAdapter", "Error loading video URI: ${e.message}", e)
                                            // Fallback to asset URI
                                            try {
                                                val assetUri = resource.asUri()
                                                val mediaItem = MediaItem.fromUri(assetUri)
                                                player?.setMediaItem(mediaItem)
                                                player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                                player?.volume = 0f
                                                player?.playWhenReady = true
                                                player?.prepare()
                                            } catch (fallbackError: Exception) {
                                                Log.e("GestureMatchAdapter", "Error loading asset video (fallback)", fallbackError)
                                                videoLoadingIndicator.visibility = View.GONE
                                            }
                                        }
                                    }
                                } else {
                                    // Fallback: Use asUriWithSupabase directly (no download/cache)
                                    videoLoadingIndicator.visibility = View.VISIBLE
                                    playerView.visibility = View.GONE
                                    
                                    val videoUri = resource.asUriWithSupabase(itemView.context)
                                    val mediaItem = MediaItem.fromUri(videoUri)
                                    player?.setMediaItem(mediaItem)
                                    player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                    player?.volume = 0f
                                    player?.playWhenReady = true
                                    
                                    // Add error listener and playback state listener for fallback
                                    player?.addListener(object : androidx.media3.common.Player.Listener {
                                        override fun onPlayerError(error: PlaybackException) {
                                            Log.e("GestureMatchAdapter", "ExoPlayer error: ${error.message}", error)
                                            
                                            // Check if it's an HTTP error (400, 404, etc.)
                                            val isHttpError = error.cause is HttpDataSource.HttpDataSourceException
                                            val isInvalidResponse = error.cause is HttpDataSource.InvalidResponseCodeException
                                            
                                            val currentUri = videoUri.toString()
                                            if ((currentUri.startsWith("http://") || currentUri.startsWith("https://")) || isHttpError || isInvalidResponse) {
                                                Log.w("GestureMatchAdapter", "Supabase video failed (HTTP error or invalid URL), trying asset URI")
                                                val assetUri = resource.asUri()
                                                if (assetUri.toString() != currentUri) {
                                                    try {
                                                        // Remove old listener to avoid recursion
                                                        player?.removeListener(this)
                                                        player?.setMediaItem(MediaItem.fromUri(assetUri))
                                                        player?.prepare()
                                                        Log.d("GestureMatchAdapter", "Successfully switched to asset URI (fallback)")
                                                    } catch (e: Exception) {
                                                        Log.e("GestureMatchAdapter", "Error loading asset video", e)
                                                        videoLoadingIndicator.visibility = View.GONE
                                                    }
                                                } else {
                                                    // Already using asset URI, hide loading indicator
                                                    videoLoadingIndicator.visibility = View.GONE
                                                }
                                            } else {
                                                // Not an HTTP error, just hide loading indicator
                                                videoLoadingIndicator.visibility = View.GONE
                                            }
                                        }
                                        
                                        override fun onPlaybackStateChanged(playbackState: Int) {
                                            // Hide loading indicator and show player when video is ready
                                            if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                                videoLoadingIndicator.visibility = View.GONE
                                                playerView.visibility = View.VISIBLE
                                                btnMenuVideo.visibility = View.VISIBLE
                                            } else if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                                                // Keep loading indicator visible while buffering
                                                videoLoadingIndicator.visibility = View.VISIBLE
                                                playerView.visibility = View.GONE
                                            }
                                        }
                                    })
                                    
                                    player?.prepare()
                                }
                            }
                            playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            
                            // Setup menu button click listener
                            btnMenuVideo.setOnClickListener {
                                // Track if player was playing before pausing
                                val wasPlaying = player?.isPlaying == true
                                pauseAllVideos()
                                showVideoDialog(resource, card.label, wasPlaying)
                            }
                        }
                        else -> {
                            imageMedia.visibility = View.VISIBLE
                            imageMedia.setImageResource(R.drawable.default_image)
                            releasePlayer()
                        }
                    }
                } else {
                    textLabel.visibility = View.VISIBLE
                    textLabel.text = card.label
                    textHidden.visibility = View.GONE
                    releasePlayer()
                }
            } else {
                textHidden.visibility = View.VISIBLE
                textHidden.text = "Tap to reveal"
                textLabel.visibility = View.GONE
                releasePlayer()
            }

            itemView.isEnabled = !card.isMatched
            itemView.alpha = if (card.isMatched) 0.6f else 1f
            itemView.setOnClickListener { if (!card.isMatched) onClick() }
        }

        private fun setStrokeColor(color: Int) {
            val bg = itemView.background
            if (bg is GradientDrawable) {
                bg.mutate()
                val density = itemView.resources.displayMetrics.density
                val strokePx = (3f * density).toInt().coerceAtLeast(1)
                bg.setStroke(strokePx, color)
            }
        }

        private fun ensurePlayer() {
            if (player == null) {
                player = ExoPlayer.Builder(itemView.context).build()
                playerView.player = player
            }
        }

        private fun releasePlayer() {
            playerView.player = null
            if (!isUsingProvidedPlayer) {
                player?.release()
            }
            player = null
            isUsingProvidedPlayer = false
            videoLoadingIndicator.visibility = View.GONE
        }
        
        /**
         * Pauses all currently playing videos in the adapter
         */
        private fun pauseAllVideos() {
            // Pause the current player if it exists
            player?.let { p ->
                try {
                    p.pause()
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error pausing video: ${e.message}")
                }
            }
        }
        
        /**
         * Resumes the current player if it was set to play when ready
         */
        private fun resumeVideo() {
            player?.let { p ->
                try {
                    if (p.playWhenReady) {
                        p.play()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ExoPlayer", "Error resuming video: ${e.message}")
                }
            }
        }
        
        /**
         * Shows a dialog with the video playing in a larger size
         * @param media The media resource to play
         * @param label The label text to display
         * @param resumePlayer Whether to resume the player when dialog closes
         */
        private fun showVideoDialog(media: MediaResource, label: String?, resumePlayer: Boolean = false) {
            val context = itemView.context
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
            
            // Load video URI asynchronously using trySupabaseThenAsset (like Dictionary)
            if (videoUriProvider != null && lifecycleOwner != null) {
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val videoUri = videoUriProvider.invoke(media)
                        
                        if (videoUri == null || videoUri.toString().isEmpty()) {
                            Log.w("GestureMatchAdapter", "Invalid video URI for dialog: ${media.path}")
                            dialog.dismiss()
                            return@launch
                        }
                        
                        val mediaItem = MediaItem.Builder().setUri(videoUri).build()
                        dialogPlayer.setMediaItem(mediaItem)
                        dialogPlayer.repeatMode = Player.REPEAT_MODE_ALL
                        dialogPlayer.volume = 0f
                        
                        // Add error listener for fallback (similar to Dictionary)
                        dialogPlayer.addListener(object : Player.Listener {
                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                Log.e("GestureMatchAdapter", "Dialog ExoPlayer error: ${error.message}", error)
                                // Try asset URI as fallback if Supabase URL failed
                                val currentUri = videoUri.toString()
                                if (currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
                                    Log.w("GestureMatchAdapter", "Supabase video failed in dialog, trying asset URI")
                                    val assetUri = media.asUri()
                                    if (assetUri.toString() != currentUri) {
                                        try {
                                            dialogPlayer.setMediaItem(MediaItem.Builder().setUri(assetUri).build())
                                            dialogPlayer.prepare()
                                        } catch (e: Exception) {
                                            Log.e("GestureMatchAdapter", "Error loading asset video in dialog", e)
                                        }
                                    }
                                }
                            }
                        })
                        
                        dialogPlayer.prepare()
                        dialogPlayer.playWhenReady = true
                    } catch (e: Exception) {
                        Log.e("GestureMatchAdapter", "Error loading video URI for dialog: ${e.message}", e)
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
                            Log.e("GestureMatchAdapter", "Error loading asset video in dialog (fallback)", fallbackError)
                            dialog.dismiss()
                        }
                    }
                }
            } else {
                // Fallback: Use asUriWithSupabase directly
                val videoUri = media.asUriWithSupabase(context)
                val mediaItem = MediaItem.Builder().setUri(videoUri).build()
                dialogPlayer.setMediaItem(mediaItem)
                dialogPlayer.repeatMode = Player.REPEAT_MODE_ALL
                dialogPlayer.volume = 0f
                
                // Add error listener for fallback
                dialogPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("GestureMatchAdapter", "Dialog ExoPlayer error: ${error.message}", error)
                        val currentUri = videoUri.toString()
                        if (currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
                            Log.w("GestureMatchAdapter", "Supabase video failed in dialog, trying asset URI")
                            val assetUri = media.asUri()
                            if (assetUri.toString() != currentUri) {
                                try {
                                    dialogPlayer.setMediaItem(MediaItem.Builder().setUri(assetUri).build())
                                    dialogPlayer.prepare()
                                } catch (e: Exception) {
                                    Log.e("GestureMatchAdapter", "Error loading asset video in dialog", e)
                                }
                            }
                        }
                    }
                })
                
                dialogPlayer.prepare()
                dialogPlayer.playWhenReady = true
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
                // Resume player if it was playing before
                if (resumePlayer) {
                    resumeVideo()
                }
            }
            
            dialog.show()
        }
    }

    override fun onViewRecycled(holder: CardVH) {
        super.onViewRecycled(holder)
        // Player release handled within the view holder lifecycle
    }
}

