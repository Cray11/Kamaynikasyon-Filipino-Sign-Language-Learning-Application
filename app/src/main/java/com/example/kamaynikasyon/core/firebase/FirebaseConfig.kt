package com.example.kamaynikasyon.core.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseConfig {
    
    lateinit var auth: FirebaseAuth
    lateinit var firestore: FirebaseFirestore
    lateinit var analytics: FirebaseAnalytics
    
    fun initialize() {
        if (!::auth.isInitialized) {
            auth = FirebaseAuth.getInstance()
        }
        if (!::firestore.isInitialized) {
            firestore = FirebaseFirestore.getInstance()
        }
    }
    
    fun initializeAnalytics(analytics: FirebaseAnalytics) {
        this.analytics = analytics
    }
}
