package com.example.kamaynikasyon.features.quizzes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.databinding.ItemQuizSkeletonBinding

/**
 * Skeleton adapter for showing loading placeholders in quizzes list.
 */
class QuizSkeletonAdapter(
    private val itemCount: Int = 5
) : RecyclerView.Adapter<QuizSkeletonAdapter.SkeletonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
        val binding = ItemQuizSkeletonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SkeletonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
        // Skeleton items don't need binding, they're static placeholders
    }

    override fun getItemCount(): Int = itemCount

    inner class SkeletonViewHolder(
        binding: ItemQuizSkeletonBinding
    ) : RecyclerView.ViewHolder(binding.root)
}

