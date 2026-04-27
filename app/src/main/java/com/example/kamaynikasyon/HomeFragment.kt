package com.example.kamaynikasyon

import android.content.Intent
import com.example.kamaynikasyon.core.base.BaseFragment
import com.example.kamaynikasyon.core.utils.startActivityWithTransition
import com.example.kamaynikasyon.databinding.FragmentHomeBinding
import com.example.kamaynikasyon.features.lessons.activities.LessonsActivity
import com.example.kamaynikasyon.features.quizzes.activities.DailyStreakActivity
import com.example.kamaynikasyon.features.quizzes.activities.QuizzesActivity

class HomeFragment : BaseFragment<FragmentHomeBinding>() {
    
    override fun getViewBinding(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?): FragmentHomeBinding {
        return FragmentHomeBinding.inflate(inflater, container, false)
    }
    
    override fun setupUI() {
        setupNavigationCards()
    }
    
    private fun setupNavigationCards() {
        // Daily Streak Card
        binding.cardDailyQuiz.setOnClickListener {
            startActivityWithTransition(Intent(requireContext(), DailyStreakActivity::class.java))
        }

        // Lessons Card
        binding.cardLessons.setOnClickListener {
            startActivityWithTransition(Intent(requireContext(), LessonsActivity::class.java))
        }

        // Quizzes Card
        binding.cardQuizzes.setOnClickListener {
            startActivityWithTransition(Intent(requireContext(), QuizzesActivity::class.java))
        }
    }
    
    override fun setupObservers() {
        // Setup observers here
    }
}
