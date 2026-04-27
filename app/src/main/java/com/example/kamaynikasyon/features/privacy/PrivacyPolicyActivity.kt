package com.example.kamaynikasyon.features.privacy

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.kamaynikasyon.R
import com.google.android.material.appbar.MaterialToolbar

class PrivacyPolicyActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.privacy_policy_title)
        
        toolbar.setNavigationOnClickListener { finish() }
        
        val privacyText = findViewById<TextView>(R.id.tv_privacy_content)
        privacyText.text = getString(R.string.privacy_policy_content)
        privacyText.movementMethod = LinkMovementMethod.getInstance()
    }
}

