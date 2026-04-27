package com.example.kamaynikasyon.features.dictionary.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.features.dictionary.data.repositories.DictionaryRepository
import com.example.kamaynikasyon.features.dictionary.data.models.Word
import com.example.kamaynikasyon.features.dictionary.adapters.WordAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchResultsFragment : Fragment() {

    private lateinit var backButton: ImageButton
    private lateinit var wordsRecyclerView: RecyclerView
    private lateinit var wordsSectionTitle: TextView
    private lateinit var noResultsText: TextView
    
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var wordAdapter: WordAdapter
    
    // Callback interfaces
    private var onBackClickListener: (() -> Unit)? = null
    private var onWordClickListener: ((String) -> Unit)? = null
    
    private val searchQuery: String by lazy {
        arguments?.getString("searchQuery") ?: ""
    }
    private var searchResults: List<Word> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRepository()
        setupRecyclerView()
        setupBackButton()
        performSearch(searchQuery)
    }

    private fun initializeViews(view: View) {
        backButton = view.findViewById(R.id.btnBack)
        wordsRecyclerView = view.findViewById(R.id.wordsRecyclerView)
        wordsSectionTitle = view.findViewById(R.id.wordsSectionTitle)
        noResultsText = view.findViewById(R.id.noResultsText)
    }

    private fun setupRepository() {
        dictionaryRepository = DictionaryRepository(requireContext())
    }

    private fun setupRecyclerView() {
        wordAdapter = WordAdapter(mutableListOf()) { word ->
            // Use callback to navigate to word detail
            onWordClickListener?.invoke(word.id)
        }
        
        wordsRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = wordAdapter
        }
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


    private fun performSearch(query: String) {
        if (query.isBlank()) {
            showNoResults()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            searchResults = withContext(Dispatchers.IO) {
                dictionaryRepository.searchWords(query)
            }
            wordsSectionTitle.text = "Search Results for \"$query\" (${searchResults.size} found)"
            
            if (searchResults.isNotEmpty()) {
                wordAdapter.updateWords(searchResults)
                wordsRecyclerView.visibility = View.VISIBLE
                noResultsText.visibility = View.GONE
            } else {
                showNoResults()
            }
        }
    }

    private fun showNoResults() {
        wordsRecyclerView.visibility = View.GONE
        noResultsText.visibility = View.VISIBLE
        noResultsText.text = "No results found for \"$searchQuery\""
    }
}
