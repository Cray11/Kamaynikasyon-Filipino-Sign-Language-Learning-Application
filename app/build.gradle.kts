plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

import java.util.Properties
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date

// Generate date string in format M/d/yy (e.g., "1/1/25")
val dateFormat = SimpleDateFormat("M/d/yy")
val buildDate = dateFormat.format(Date())
val versionNameWithDate = "1.2_$buildDate"

// Read local.properties file
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use {
        localProperties.load(it)
    }
}

android {
    namespace = "com.example.kamaynikasyon"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kamaynikasyon"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = versionNameWithDate

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Inject Supabase credentials from local.properties into BuildConfig
        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\"")
        
        // Inject Google Sign-In Client ID from local.properties into BuildConfig
        buildConfigField("String", "GOOGLE_SIGN_IN_CLIENT_ID", "\"${localProperties.getProperty("GOOGLE_SIGN_IN_CLIENT_ID", "")}\"")
    }
    
    configurations.all {
        resolutionStrategy {
            // Force consistent versions to avoid duplicate class errors
            force("androidx.core:core:1.17.0")
            force("androidx.core:core-ktx:1.17.0")
        }
    }

    // Signing configurations
    signingConfigs {
        create("release") {
            // Read from local.properties for security (not committed to git)
            val keystorePath = localProperties.getProperty("RELEASE_KEYSTORE_PATH")
            val keystorePassword = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD")
            val keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
            val keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            
            if (keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
                val resolvedStoreFile = rootProject.file(keystorePath)
                
                if (resolvedStoreFile.exists()) {
                    storeFile = resolvedStoreFile
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                    println("Release signing config initialized: ${resolvedStoreFile.absolutePath}")
                } else {
                    println("ERROR: Release keystore not found at path: ${resolvedStoreFile.absolutePath}")
                }
            } else {
                println("ERROR: Release keystore properties missing in local.properties")
            }
        }
    }

    buildTypes {
        release {
            // Use release signing config if configured
            signingConfigs.findByName("release")?.let { releaseConfig ->
                val storeFile = releaseConfig.storeFile
                if (storeFile != null && storeFile.exists()) {
                    signingConfig = releaseConfig
                    println("Release signing config applied: ${storeFile.absolutePath}")
                } else {
                    println("WARNING: Release signing config found but keystore file doesn't exist!")
                }
            } ?: println("WARNING: Release signing config not found!")

            isMinifyEnabled = false
            isShrinkResources = false
            // ProGuard disabled for now
            // proguardFiles(
            //     getDefaultProguardFile("proguard-android-optimize.txt"),
            //     "proguard-rules.pro"
            // )
            // Enable Crashlytics mapping file upload
            isDebuggable = false
        }
        debug {
            // Keep debug builds readable
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Paging library for lazy loading
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    
    // Gson for JSON parsing
    implementation(libs.gson)
    
    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    
    // Room (simple local database for user profile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    // CameraX dependencies for sign language detection
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    
    // MediaPipe Library for hand landmark detection
    implementation("com.google.mediapipe:tasks-vision:0.10.26")
    
    // TensorFlow Lite dependencies for sign language recognition
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    
    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Supabase for storage
    implementation(platform("io.github.jan-tennert.supabase:bom:2.0.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:2.3.5")
    
    // Image loading for profile pictures
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    
    // Material Calendar View for displaying progress (AndroidX compatible)
    // Using JitPack format for applandeo library
    implementation("com.github.applandeo:Material-Calendar-View:1.9.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("org.slf4j:slf4j-simple:2.0.12")
}