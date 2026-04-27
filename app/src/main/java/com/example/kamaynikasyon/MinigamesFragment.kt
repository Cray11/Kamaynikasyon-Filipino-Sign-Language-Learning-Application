package com.example.kamaynikasyon

import android.content.Intent
import com.example.kamaynikasyon.core.base.BaseFragment
import com.example.kamaynikasyon.databinding.FragmentMinigamesBinding
import com.example.kamaynikasyon.minigames.picturequiz.PictureQuizSelectionActivity
import com.example.kamaynikasyon.minigames.spellingsequence.SpellingSequenceSelectionActivity
import com.example.kamaynikasyon.minigames.bubbleshooter.BubbleShooterSelectionActivity
import com.example.kamaynikasyon.minigames.gesturematch.GestureMatchSelectionActivity
import com.example.kamaynikasyon.core.utils.EmptyStateView
import com.example.kamaynikasyon.core.utils.VibratorHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View

class MinigamesFragment : BaseFragment<FragmentMinigamesBinding>() {
    
    override fun getViewBinding(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?): FragmentMinigamesBinding {
        return FragmentMinigamesBinding.inflate(inflater, container, false)
    }
    
    override fun setupUI() {
        binding.cardPictureQuiz.setOnClickListener { 
            VibratorHelper.vibrateLight(requireContext())
            openPictureQuizSelection() 
        }
        binding.cardSpellingSequence.setOnClickListener { 
            VibratorHelper.vibrateLight(requireContext())
            openSpellingSequenceSelection() 
        }
        binding.cardBubbleShooter.setOnClickListener { 
            VibratorHelper.vibrateLight(requireContext())
            openBubbleShooterSelection() 
        }
        binding.cardGestureMatch.setOnClickListener { 
            VibratorHelper.vibrateLight(requireContext())
            openGestureMatchSelection() 
        }
        
        // Check if minigames are available
        checkMinigamesAvailability()

        // Images already contain titles, no need to set text
    }

    data class MinigameMeta(val icon: String, val title: String)

    private fun setCardMeta(card: android.view.View, iconId: Int, titleId: Int, meta: MinigameMeta?) {
        if (meta == null) return
        card.findViewById<android.widget.TextView>(iconId)?.text = meta.icon
        card.findViewById<android.widget.TextView>(titleId)?.text = meta.title
    }

    private fun loadMinigameMeta(assetPath: String): MinigameMeta? {
        return try {
            val ctx = requireContext().applicationContext
            val input = ctx.assets.open(assetPath)
            val text = String(input.readBytes(), java.nio.charset.Charset.forName("UTF-8"))
            input.close()
            val root = org.json.JSONObject(text)
            val minigame = root.optJSONObject("minigame")
            val icon = (minigame?.optString("icon", "") ?: "").trim()
            val title = (minigame?.optString("title", "") ?: "").trim()
            MinigameMeta(icon = icon, title = title)
        } catch (_: Exception) { null }
    }

    private fun openPictureQuizSelection() {
        val ctx = requireContext()
        val intent = Intent(ctx, PictureQuizSelectionActivity::class.java)
        startActivity(intent)
    }

    private fun openSpellingSequenceSelection() {
        val ctx = requireContext()
        val intent = Intent(ctx, SpellingSequenceSelectionActivity::class.java)
        startActivity(intent)
    }

    private fun openBubbleShooterSelection() {
        val ctx = requireContext()
        val intent = Intent(ctx, BubbleShooterSelectionActivity::class.java)
        startActivity(intent)
    }

    private fun openGestureMatchSelection() {
        val ctx = requireContext()
        val intent = Intent(ctx, GestureMatchSelectionActivity::class.java)
        startActivity(intent)
    }
    
    override fun setupObservers() {
        // No observers
    }

    override fun onStart() {
        super.onStart()
        updatePictureQuizStars()
    }

    private fun updatePictureQuizStars() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val db = com.example.kamaynikasyon.data.database.AppDatabase.getInstance(ctx)
            val progress = withContext(Dispatchers.IO) {
                db.pictureQuizProgressDao().getAll()
            }
            val totalStars = progress.sumOf { it.bestStars }
        // Card layout no longer has a dedicated stars TextView; omit display to avoid resource errors
        }
    }
    
    private fun checkMinigamesAvailability() {
        // Hide container and empty state initially while checking
        binding.minigamesContainer.visibility = View.GONE
        val emptyStateView = binding.root.findViewById<View>(R.id.empty_state_view)
        val progressBar = binding.root.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progress_loading)
        
        emptyStateView?.let { EmptyStateView.hide(it) }
        
        // Show loading indicator with animation
        progressBar?.let {
            it.visibility = View.VISIBLE
            com.example.kamaynikasyon.core.utils.AnimationHelper.fadeIn(it, 200)
            com.example.kamaynikasyon.core.utils.AnimationHelper.startRotateAnimation(it)
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                val ctx = requireContext().applicationContext
                try {
                    // Check if at least one minigame index.json exists
                    val assets = ctx.assets
                    listOf(
                        "minigames/picture_quiz/index.json",
                        "minigames/spelling_sequence/index.json",
                        "minigames/bubble_shooter/index.json",
                        "minigames/gesture_match/index.json"
                    ).any { path ->
                        try {
                            assets.open(path).use { true }
                        } catch (_: Exception) { false }
                    }
                } catch (_: Exception) { false }
            }
            
            // Hide loading indicator with animation
            progressBar?.let {
                com.example.kamaynikasyon.core.utils.AnimationHelper.stopAnimation(it)
                com.example.kamaynikasyon.core.utils.AnimationHelper.fadeOut(it, 150) {
                    it.visibility = View.GONE
                }
            }
            
            if (!available) {
                // Show empty state
                emptyStateView?.let {
                    EmptyStateView.showEmptyMinigames(it) {
                        // Retry action
                        checkMinigamesAvailability()
                    }
                }
                // Hide minigames container
                binding.minigamesContainer.visibility = View.GONE
            } else {
                // Hide empty state
                emptyStateView?.let { EmptyStateView.hide(it) }
                // Show minigames container
                binding.minigamesContainer.visibility = View.VISIBLE
            }
        }
    }
}
