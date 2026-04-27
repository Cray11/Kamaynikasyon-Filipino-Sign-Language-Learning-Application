package com.example.kamaynikasyon.core.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.utils.TransitionHelper

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {
    
    protected lateinit var binding: VB
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = getViewBinding()
        setContentView(binding.root)
        setupUI()
        setupObservers()
    }
    
    abstract fun getViewBinding(): VB
    abstract fun setupUI()
    open fun setupObservers() {}
    
    /**
     * Finish activity with smooth transition.
     * Override this to customize transition behavior.
     */
    override fun finish() {
        super.finish()
        // Set transition animation after finish (to avoid recursion)
        overridePendingTransition(
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
    }
}
