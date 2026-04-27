package com.example.kamaynikasyon.features.lessons.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.databinding.ItemLessonSkeletonBinding

/**
 * Skeleton adapter for showing loading placeholders in lessons list.
 */
class LessonSkeletonAdapter(
    private val itemCount: Int = 5
) : RecyclerView.Adapter<LessonSkeletonAdapter.SkeletonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
        val binding = ItemLessonSkeletonBinding.inflate(
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
        binding: ItemLessonSkeletonBinding
    ) : RecyclerView.ViewHolder(binding.root)
}

