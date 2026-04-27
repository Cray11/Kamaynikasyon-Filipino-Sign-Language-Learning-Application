package com.example.kamaynikasyon.features.quizzes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.ItemQuizBinding
import com.example.kamaynikasyon.features.quizzes.data.models.Quiz

class QuizAdapter(
    private val onQuizClick: (Quiz) -> Unit
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {
    
    private var quizzes = mutableListOf<Quiz>()
    private var quizIdToScore: Map<String, Pair<Int, Int>> = emptyMap()
    private var completedQuizIds: Set<String> = emptySet()
    
    fun updateQuizzes(newQuizzes: List<Quiz>) {
        quizzes.clear()
        quizzes.addAll(newQuizzes)
        notifyDataSetChanged()
    }
    
    fun updateScores(scores: Map<String, Pair<Int, Int>>) {
        quizIdToScore = scores
        notifyDataSetChanged()
    }
    
    fun updateCompleted(ids: Set<String>) {
        completedQuizIds = ids
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemQuizBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuizViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        holder.bind(quizzes[position])
        // Add admin-style staggered slideInUp animation
        com.example.kamaynikasyon.core.utils.AnimationHelper.animateListItem(
            holder.itemView,
            position,
            delayPerItem = 50,
            animationType = "slideInUp"
        )
    }
    
    override fun getItemCount(): Int = quizzes.size
    
    inner class QuizViewHolder(private val binding: ItemQuizBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(quiz: Quiz) {
            binding.apply {
                tvQuizTitle.text = quiz.title
                tvQuizDescription.text = quiz.description
                
                // Normalize difficulty text (capitalize first letter)
                val difficulty = quiz.difficulty.lowercase().replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase() else it.toString() 
                }
                tvQuizDifficulty.text = difficulty
                
                // Set background color based on difficulty
                val backgroundRes = when (difficulty.lowercase()) {
                    "easy" -> R.drawable.difficulty_badge_easy
                    "medium" -> R.drawable.difficulty_badge_medium
                    "hard" -> R.drawable.difficulty_badge_hard
                    else -> R.drawable.difficulty_badge_medium // Default to medium
                }
                tvQuizDifficulty.setBackgroundResource(backgroundRes)
                
                tvQuestionCount.text = "${quiz.questions.size} questions"
                val scorePair = quizIdToScore[quiz.id]
                tvQuizScore.text = if (scorePair != null) "Score: ${scorePair.first}/${scorePair.second}" else "Score: -"
                tvQuizCompletedBadge.visibility = if (completedQuizIds.contains(quiz.id)) android.view.View.VISIBLE else android.view.View.GONE
                
                root.setOnClickListener {
                    onQuizClick(quiz)
                }
            }
        }
    }
}

