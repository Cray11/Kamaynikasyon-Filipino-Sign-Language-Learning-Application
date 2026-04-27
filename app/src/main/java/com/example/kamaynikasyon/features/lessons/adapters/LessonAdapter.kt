package com.example.kamaynikasyon.features.lessons.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.ItemLessonBinding
import com.example.kamaynikasyon.features.lessons.data.models.Lesson

class LessonAdapter(
    private val onLessonClick: (Lesson) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {
    
    private var lessons = mutableListOf<Lesson>()
    private var completedLessonIds: Set<String> = emptySet()
    
    fun updateLessons(newLessons: List<Lesson>) {
        lessons.clear()
        lessons.addAll(newLessons)
        notifyDataSetChanged()
    }
    
    fun updateCompleted(completedIds: Set<String>) {
        completedLessonIds = completedIds
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val binding = ItemLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LessonViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(lessons[position])
        // Add admin-style staggered slideInUp animation
        com.example.kamaynikasyon.core.utils.AnimationHelper.animateListItem(
            holder.itemView,
            position,
            delayPerItem = 50,
            animationType = "slideInUp"
        )
    }
    
    override fun getItemCount(): Int = lessons.size
    
    inner class LessonViewHolder(private val binding: ItemLessonBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(lesson: Lesson) {
            binding.apply {
                tvLessonTitle.text = lesson.title
                tvLessonDescription.text = lesson.description
                
                // Normalize difficulty text (capitalize first letter)
                val difficulty = lesson.difficulty.lowercase().replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase() else it.toString() 
                }
                tvLessonDifficulty.text = difficulty
                
                // Set background color based on difficulty
                val backgroundRes = when (difficulty.lowercase()) {
                    "easy" -> R.drawable.difficulty_badge_easy
                    "medium" -> R.drawable.difficulty_badge_medium
                    "hard" -> R.drawable.difficulty_badge_hard
                    else -> R.drawable.difficulty_badge_medium // Default to medium
                }
                tvLessonDifficulty.setBackgroundResource(backgroundRes)
                
                tvCompletedBadge.visibility = if (completedLessonIds.contains(lesson.id)) android.view.View.VISIBLE else android.view.View.GONE
                
                root.setOnClickListener {
                    onLessonClick(lesson)
                }
            }
        }
    }
}

