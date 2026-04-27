package com.example.kamaynikasyon.features.dictionary.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kamaynikasyon.MainActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.ui.DataLoadErrorDialog
import com.example.kamaynikasyon.core.utils.EmptyStateView
import com.example.kamaynikasyon.core.utils.AnimationHelper
import com.example.kamaynikasyon.features.dictionary.data.repositories.DictionaryRepository
import com.example.kamaynikasyon.features.dictionary.data.models.Word
import com.example.kamaynikasyon.features.dictionary.adapters.WordAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordsFragment : Fragment() {

    private lateinit var backButton: ImageButton
    private lateinit var wordsRecyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var wordsSectionTitle: TextView
    
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var wordAdapter: WordAdapter
    private lateinit var skeletonAdapter: com.example.kamaynikasyon.features.dictionary.adapters.WordSkeletonAdapter
    
    // Callback interfaces
    private var onBackClickListener: (() -> Unit)? = null
    private var onWordClickListener: ((String) -> Unit)? = null
    private var errorDialog: AlertDialog? = null
    
    private val categoryId: String by lazy {
        arguments?.getString("categoryId") ?: ""
    }
    private val categoryName: String by lazy {
        arguments?.getString("categoryName") ?: "Words"
    }
    private var categoryWords: List<Word> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_words, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRepository()
        setupRecyclerView()
        setupSwipeRefresh()
        setupBackButton()
        loadWords()
    }

    private fun initializeViews(view: View) {
        backButton = view.findViewById(R.id.btnBack)
        wordsRecyclerView = view.findViewById(R.id.wordsRecyclerView)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        wordsSectionTitle = view.findViewById(R.id.wordsSectionTitle)
    }

    private fun setupRepository() {
        dictionaryRepository = DictionaryRepository(requireContext())
    }

    private fun setupRecyclerView() {
        wordAdapter = WordAdapter(mutableListOf()) { word ->
            // Use callback to navigate to word detail
            onWordClickListener?.invoke(word.id)
        }
        
        skeletonAdapter = com.example.kamaynikasyon.features.dictionary.adapters.WordSkeletonAdapter(itemCount = 6)
        
        wordsRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = skeletonAdapter // Start with skeleton
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadWords()
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

    private fun loadWords() {
        wordsSectionTitle.text = "$categoryName Words"
        
        // Show skeleton loader and hide empty state
        val emptyStateView = view?.findViewById<View>(R.id.empty_state_view)
        emptyStateView?.let { EmptyStateView.hide(it) }
        
        // Show skeleton with fade-in animation
        wordsRecyclerView.adapter = skeletonAdapter
        AnimationHelper.fadeIn(wordsRecyclerView, 200)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                categoryWords = withContext(Dispatchers.IO) {
                    dictionaryRepository.getWordsByCategory(categoryId)
                }
                wordAdapter.updateWords(categoryWords)
                
                // Switch to real adapter
                wordsRecyclerView.adapter = wordAdapter
                swipeRefresh.isRefreshing = false
                
                // Show content or empty state with animations
                if (categoryWords.isEmpty()) {
                    AnimationHelper.fadeOut(wordsRecyclerView, 150) {
                        emptyStateView?.let {
                            EmptyStateView.showEmptyWords(it) {
                                loadWords()
                            }
                            AnimationHelper.fadeIn(it, 300)
                        }
                    }
                } else {
                    emptyStateView?.let { EmptyStateView.hide(it) }
                    // Fade in content (already visible from skeleton, just ensure alpha is 1)
                    wordsRecyclerView.alpha = 1f
                    wordsRecyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                swipeRefresh.isRefreshing = false
                AnimationHelper.fadeOut(wordsRecyclerView, 150) {
                    showWordsErrorDialog()
                }
            }
        }
    }
    
    private fun showWordsErrorDialog() {
        if (!isAdded) return
        errorDialog?.dismiss()
        errorDialog = DataLoadErrorDialog.create(
            context = requireContext(),
            messageRes = R.string.error_loading_words,
            onRetry = { loadWords() },
            onGoHome = { navigateHome() }
        )
        errorDialog?.show()
    }
    
    private fun navigateHome() {
        if (!isAdded) return
        val hostActivity = requireActivity()
        val intent = Intent(hostActivity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        hostActivity.finish()
    }
    
    override fun onDestroyView() {
        errorDialog?.dismiss()
        errorDialog = null
        super.onDestroyView()
    }
}
