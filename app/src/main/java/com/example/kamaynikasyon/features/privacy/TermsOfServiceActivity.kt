package com.example.kamaynikasyon.features.privacy

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.kamaynikasyon.R
import com.google.android.material.appbar.MaterialToolbar

class TermsOfServiceActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_of_service)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.terms_of_service_title)
        
        toolbar.setNavigationOnClickListener { finish() }
        
        val termsText = findViewById<TextView>(R.id.tv_terms_content)
        termsText.text = getString(R.string.terms_of_service_content)
        termsText.movementMethod = LinkMovementMethod.getInstance()
    }
}

