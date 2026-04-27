package com.example.kamaynikasyon.features.auth.activities

import android.content.Intent
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.base.BaseActivity
import com.example.kamaynikasyon.databinding.ActivityGetStartedBinding
import com.example.kamaynikasyon.features.auth.adapters.OnboardingAdapter
import org.json.JSONObject
import java.io.IOException

class GetStartedActivity : BaseActivity<ActivityGetStartedBinding>() {
    
    private lateinit var onboardingAdapter: OnboardingAdapter
    private val onboardingItems = mutableListOf<OnboardingItem>()
    
    override fun getViewBinding(): ActivityGetStartedBinding {
        return ActivityGetStartedBinding.inflate(layoutInflater)
    }
    
    override fun setupUI() {
        loadOnboardingItems()
        setupViewPager()
        setupClickListeners()
        updateButtonText()
    }
    
    private fun loadOnboardingItems() {
        try {
            val jsonString = assets.open("index.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val itemsArray = jsonObject.getJSONArray("onboardingItems")
            
            onboardingItems.clear()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val title = itemObj.getString("title")
                val description = itemObj.getString("description")
                // Map position to drawable resource (onboarding_1, onboarding_2, onboarding_3, onboarding_4)
                val imageResId = when (i) {
                    0 -> R.drawable.onboarding_1
                    1 -> R.drawable.onboarding_2
                    2 -> R.drawable.onboarding_3
                    3 -> R.drawable.onboarding_4
                    else -> R.drawable.onboarding_1 // Default fallback
                }
                
                onboardingItems.add(
                    OnboardingItem(
                        title = title,
                        description = description,
                        imageResId = imageResId
                    )
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // Fallback to default items if JSON loading fails
            onboardingItems.addAll(getDefaultOnboardingItems())
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to default items if parsing fails
            onboardingItems.addAll(getDefaultOnboardingItems())
        }
    }
    
    private fun getDefaultOnboardingItems(): List<OnboardingItem> {
        return listOf(
            OnboardingItem(
                title = "Learn Sign Language",
                description = "Take lessons, quizzes and challenge yourself daily!",
                imageResId = R.drawable.onboarding_1
            ),
            OnboardingItem(
                title = "In App Dictionary",
                description = "Learn new signs and words with the in app dictionary!",
                imageResId = R.drawable.onboarding_2
            ),
            OnboardingItem(
                title = "Play Minigames",
                description = "Play minigames to test your skills and have fun!",
                imageResId = R.drawable.onboarding_3
            ),
            OnboardingItem(
                title = "Track Your Progress",
                description = "Track your progress and see your achievements!",
                imageResId = R.drawable.onboarding_4
            )
        )
    }
    
    private fun setupViewPager() {
        onboardingAdapter = OnboardingAdapter(onboardingItems)
        binding.viewPager.adapter = onboardingAdapter
        
        // Setup progress bar
        setupProgressBar()
        
        // Listen to page changes
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateProgressBar(position)
                updateButtonText()
            }
        })
    }
    
    private fun setupProgressBar() {
        binding.progressBar.max = 100
        updateProgressBar(0)
    }
    
    private fun updateProgressBar(position: Int) {
        val totalPages = onboardingItems.size
        if (totalPages > 0) {
            val progress = ((position + 1) * 100) / totalPages
            binding.progressBar.progress = progress
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSkip.setOnClickListener {
            goToLogin()
        }
        
        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < onboardingItems.size - 1) {
                binding.viewPager.currentItem = binding.viewPager.currentItem + 1
            } else {
                goToLogin()
            }
        }
        
        binding.btnPrevious.setOnClickListener {
            if (binding.viewPager.currentItem > 0) {
                binding.viewPager.currentItem = binding.viewPager.currentItem - 1
            }
        }
    }
    
    private fun updateButtonText() {
        val isLastPage = binding.viewPager.currentItem == onboardingItems.size - 1
        
        if (isLastPage) {
            binding.btnNext.text = "Now Get Started"
        } else {
            binding.btnNext.text = "Next"
        }
        binding.btnPrevious.visibility = if (binding.viewPager.currentItem == 0) android.view.View.GONE else android.view.View.VISIBLE
    }
    
    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
    
    override fun setupObservers() {
        // Setup any observers here
    }
}

data class OnboardingItem(
    val title: String,
    val description: String,
    val imageResId: Int
)
