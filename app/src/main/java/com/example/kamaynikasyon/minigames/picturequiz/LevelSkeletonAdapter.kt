package com.example.kamaynikasyon.minigames.picturequiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.R

/**
 * Skeleton adapter for showing loading placeholders in minigame level selection lists.
 */
class LevelSkeletonAdapter(
    private val itemCount: Int = 9
) : RecyclerView.Adapter<LevelSkeletonAdapter.SkeletonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_level_skeleton, parent, false)
        return SkeletonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
        // Skeleton items don't need binding, they're static placeholders
    }

    override fun getItemCount(): Int = itemCount

    inner class SkeletonViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView)
}

