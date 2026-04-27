package com.example.kamaynikasyon.features.dictionary.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kamaynikasyon.MainActivity
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.features.dictionary.data.repositories.DictionaryRepository
import com.example.kamaynikasyon.features.dictionary.data.models.Word
import com.example.kamaynikasyon.features.dictionary.adapters.CategoryAdapter
import com.example.kamaynikasyon.core.utils.EmptyStateView
import com.example.kamaynikasyon.core.utils.AnimationHelper
import com.example.kamaynikasyon.core.ui.DataLoadErrorDialog
import com.example.kamaynikasyon.core.utils.AnalyticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryFragment : Fragment() {

    private lateinit var searchEditText: com.google.android.material.textfield.TextInputEditText
    private lateinit var searchInputLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var dictionaryContentContainer: FrameLayout
    private lateinit var categoriesContent: LinearLayout
    
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var skeletonAdapter: com.example.kamaynikasyon.features.dictionary.adapters.CategorySkeletonAdapter
    
    private var currentChildFragment: Fragment? = null
    private var errorDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dictionary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRepository()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearchFunctionality()
        loadCategories()
        
        // Reset to default layout when returning to dictionary
        showCategoriesContent()
        
        // Log screen view
        AnalyticsLogger.logScreenView("Dictionary", "DictionaryFragment")

        // Hide search suggestions and clear focus when user taps outside the search area
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val touchedInsideSearch = isTouchInsideView(event, searchInputLayout)
                val touchedInsideResults = isTouchInsideView(event, searchResultsContainer)
                if (!touchedInsideSearch && !touchedInsideResults) {
                    // Clear focus and hide suggestions
                    searchEditText.clearFocus()
                    hideSearchResults()
                    hideKeyboard()
                }
            }
            // Allow other touch handling to proceed
            false
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Always reset to default layout when returning to dictionary fragment
        showCategoriesContent()
    }

    private fun initializeViews(view: View) {
        searchInputLayout = view.findViewById(R.id.til_search)
        searchEditText = view.findViewById(R.id.searchEditText)
        searchResultsContainer = view.findViewById(R.id.searchResultsContainer)
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        dictionaryContentContainer = view.findViewById(R.id.dictionaryContentContainer)
        categoriesContent = view.findViewById(R.id.categoriesContent)
    }

    private fun setupRepository() {
        dictionaryRepository = DictionaryRepository(requireContext())
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(mutableListOf()) { category ->
            // Navigate to words fragment as child fragment (use file name instead of id)
            navigateToWordsFragment(category.file, category.name)
        }
        
        skeletonAdapter = com.example.kamaynikasyon.features.dictionary.adapters.CategorySkeletonAdapter(itemCount = 6)
        
        categoriesRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = skeletonAdapter // Start with skeleton adapter
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadCategories()
        }
        swipeRefresh.setColorSchemeResources(
            R.color.primary_color,
            R.color.secondary_color
        )
    }

    private fun setupSearchFunctionality() {
        // Clear functionality is handled automatically by TextInputLayout's endIconMode="clear_text"

        // Real-time search as user types
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isBlank()) {
                    hideSearchResults()
                } else {
                    showSearchSuggestions(query)
                }
            }
        })

        // Handle search action from keyboard
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun loadCategories() {
        // Show skeleton loader and loading indicator during refresh
        val progressBar = view?.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progress_loading)
        val emptyStateView = view?.findViewById<View>(R.id.empty_state_view)
        
        // Show loading indicator if not already refreshing (swipe refresh shows its own indicator)
        if (!swipeRefresh.isRefreshing) {
            progressBar?.visibility = View.VISIBLE
        }
        
        emptyStateView?.let { EmptyStateView.hide(it) }
        
        // Show skeleton with fade-in animation
        categoriesRecyclerView.adapter = skeletonAdapter
        AnimationHelper.fadeIn(categoriesRecyclerView, 200)
        
        lifecycleScope.launch {
            try {
                val categories = withContext(Dispatchers.IO) {
                    dictionaryRepository.getAllCategories()
                }
                categoryAdapter.updateCategories(categories)
                
                // Switch to real adapter
                categoriesRecyclerView.adapter = categoryAdapter
                
                // Hide loading indicators
                progressBar?.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                
                // Show content or empty state with animations
                if (categories.isEmpty()) {
                    AnimationHelper.fadeOut(categoriesRecyclerView, 150) {
                        emptyStateView?.let {
                            EmptyStateView.showEmptyDictionary(it) {
                                loadCategories()
                            }
                            AnimationHelper.fadeIn(it, 300)
                        }
                    }
                } else {
                    emptyStateView?.let { EmptyStateView.hide(it) }
                    // Fade in content (already visible from skeleton, just ensure alpha is 1)
                    categoriesRecyclerView.alpha = 1f
                    categoriesRecyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                // Hide loading indicators
                progressBar?.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                
                AnimationHelper.fadeOut(categoriesRecyclerView, 150) {
                    showDictionaryErrorDialog()
                }
            }
        }
    }

    private fun performSearch() {
        val query = searchEditText.text.toString().trim()
        if (query.isBlank()) return

        // Log dictionary search event
        AnalyticsLogger.logDictionaryEvent("search", searchTerm = query)

        // Navigate to search results as child fragment
        navigateToSearchResultsFragment(query)
    }

    private fun showSearchSuggestions(query: String) {
        if (query.length < 2) {
            hideSearchResults()
            return
        }

        lifecycleScope.launch {
            val suggestions = withContext(Dispatchers.IO) {
                dictionaryRepository.getTopSearchResults(query, 5)
            }
            if (suggestions.isNotEmpty()) {
                displaySearchSuggestions(suggestions)
            } else {
                hideSearchResults()
            }
        }
    }

    private fun displaySearchSuggestions(suggestions: List<Word>) {
        searchResultsContainer.removeAllViews()
        
        suggestions.forEach { word ->
            val suggestionView = createSuggestionView(word)
            searchResultsContainer.addView(suggestionView)
        }
        
        // Wait for layout pass to complete
        searchEditText.post {
            // Position overlay before making visible
            searchResultsContainer.alpha = 0f
            searchResultsContainer.visibility = View.VISIBLE
            positionOverlayBelow(searchEditText)
            
            // Fade in animation
            searchResultsContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun positionOverlayBelow(anchor: View) {
        try {
            val root = requireView() as View
            val anchorLoc = IntArray(2)
            searchInputLayout.getLocationOnScreen(anchorLoc)
            val rootLoc = IntArray(2)
            root.getLocationOnScreen(rootLoc)
            val top = anchorLoc[1] - rootLoc[1] + searchInputLayout.height - 8 // Adjust to be closer to search bar

            val params = (searchResultsContainer.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )

            val marginHorizontal = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16f,
                resources.displayMetrics
            ).toInt()

            params.setMargins(marginHorizontal, top, marginHorizontal, 0)
            params.gravity = android.view.Gravity.TOP
            searchResultsContainer.layoutParams = params
            searchResultsContainer.requestLayout()
        } catch (_: Exception) {
        }
    }

    private fun createSuggestionView(word: Word): View {
        val suggestionView = TextView(context).apply {
            text = "${word.name} (${word.category})"
            setPadding(16, 12, 16, 12)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            background = resources.getDrawable(R.drawable.bg_search_result, null)
            setOnClickListener {
                navigateToWordDetailFragment(word.id)
                hideSearchResults()
                searchEditText.setText(word.name)
            }
        }
        return suggestionView
    }

    private fun hideSearchResults() {
        searchResultsContainer.visibility = View.GONE
    }

    private fun isTouchInsideView(event: MotionEvent, target: View): Boolean {
        try {
            val loc = IntArray(2)
            target.getLocationOnScreen(loc)
            val left = loc[0]
            val top = loc[1]
            val right = left + target.width
            val bottom = top + target.height

            val x = event.rawX.toInt()
            val y = event.rawY.toInt()

            return x in left..right && y in top..bottom
        } catch (_: Exception) {
        }
        return false
    }

    private fun hideKeyboard() {
        try {
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val token = requireActivity().currentFocus?.windowToken ?: view?.windowToken
            token?.let { imm?.hideSoftInputFromWindow(it, 0) }
        } catch (_: Exception) {
        }
    }

    private fun showDictionaryErrorDialog() {
        if (!isAdded) return
        errorDialog?.dismiss()
        errorDialog = DataLoadErrorDialog.create(
            context = requireContext(),
            messageRes = R.string.error_loading_dictionary,
            onRetry = { loadCategories() },
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
    
    // Navigation methods for child fragments
    private fun navigateToWordsFragment(categoryFileName: String, categoryName: String) {
        val wordsFragment = WordsFragment().apply {
            arguments = Bundle().apply {
                putString("categoryId", categoryFileName) // Using categoryId parameter name for compatibility, but now stores file name
                putString("categoryName", categoryName)
            }
        }
        
        // Set up back navigation callback
        wordsFragment.setOnBackClickListener {
            showCategoriesContent()
        }
        
        // Set up word selection callback
        wordsFragment.setOnWordClickListener { wordId ->
            navigateToWordDetailFragment(wordId)
        }
        
        replaceChildFragment(wordsFragment)
    }
    
    private fun navigateToSearchResultsFragment(query: String) {
        val searchResultsFragment = SearchResultsFragment().apply {
            arguments = Bundle().apply {
                putString("searchQuery", query)
            }
        }
        
        // Set up back navigation callback
        searchResultsFragment.setOnBackClickListener {
            showCategoriesContent()
        }
        
        // Set up word selection callback
        searchResultsFragment.setOnWordClickListener { wordId ->
            navigateToWordDetailFragment(wordId)
        }
        
        replaceChildFragment(searchResultsFragment)
    }
    
    private fun navigateToWordDetailFragment(wordId: String) {
        val previousChild = currentChildFragment

        val wordDetailFragment = WordDetailFragment().apply {
            arguments = Bundle().apply {
                putString("wordId", wordId)
            }
        }
        
        // Set up back navigation callback
        // If the previous child was a WordsFragment, return to it; otherwise return to categories
        wordDetailFragment.setOnBackClickListener {
            if (previousChild is WordsFragment) {
                // Restore the previous WordsFragment instance
                replaceChildFragment(previousChild)
            } else {
                showCategoriesContent()
            }
        }
        
        // Set up word selection callback for related words
        wordDetailFragment.setOnWordClickListener { relatedWordId ->
            navigateToWordDetailFragment(relatedWordId)
        }
        
        replaceChildFragment(wordDetailFragment)
    }
    
    private fun replaceChildFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.dictionaryContentContainer, fragment)
            .commitAllowingStateLoss()
        
        currentChildFragment = fragment
        categoriesContent.visibility = View.GONE
    }
    
    private fun showCategoriesContent() {
        // Remove any existing child fragments to ensure the container is clean.
        val childFragments = childFragmentManager.fragments
        if (childFragments.isNotEmpty()) {
            val tx = childFragmentManager.beginTransaction()
            childFragments.forEach { frag ->
                try {
                    tx.remove(frag)
                } catch (_: Exception) {
                }
            }
            tx.commitAllowingStateLoss()
        }

        currentChildFragment = null
    // Make sure categories (the default/first page) are visible
    categoriesContent.visibility = View.VISIBLE
    }
}
