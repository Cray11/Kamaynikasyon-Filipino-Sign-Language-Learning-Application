package com.example.kamaynikasyon.features.dictionary.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.features.dictionary.data.models.Word

class WordAdapter(
    private val words: MutableList<Word>,
    private val onWordClick: (Word) -> Unit
) : RecyclerView.Adapter<WordAdapter.WordViewHolder>() {

    // Track which positions have been animated to prevent re-animation on scroll
    private val animatedPositions = mutableSetOf<Int>()

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.wordName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val word = words[position]
        
        holder.name.text = word.name
        
        holder.itemView.setOnClickListener {
            onWordClick(word)
        }
        
        // Only animate if this position hasn't been animated before
        if (!animatedPositions.contains(position)) {
            animatedPositions.add(position)
            // Reset view state before animating
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 0f
            // Add admin-style staggered slideInUp animation
            com.example.kamaynikasyon.core.utils.AnimationHelper.animateListItem(
                holder.itemView,
                position,
                delayPerItem = 50,
                animationType = "slideInUp"
            )
        } else {
            // Already animated, just ensure it's visible
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
        }
    }

    override fun getItemCount(): Int = words.size

    fun updateWords(newWords: List<Word>) {
        words.clear()
        words.addAll(newWords)
        // Clear animated positions when data is updated
        animatedPositions.clear()
        notifyDataSetChanged()
    }
}
