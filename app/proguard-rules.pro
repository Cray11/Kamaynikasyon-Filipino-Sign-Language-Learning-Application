# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================
# Keep attributes for debugging
# ============================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ============================================
# Kotlin
# ============================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# ============================================
# Firebase
# ============================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# Google Sign-In (explicit rules)
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-keepclassmembers class com.google.android.gms.auth.api.signin.GoogleSignInAccount { *; }
-keepclassmembers class com.google.android.gms.auth.api.signin.GoogleSignInOptions { *; }

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class com.google.firebase.firestore.** {
    *;
}

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }

# Firebase Crashlytics
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Keep Firebase model classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ============================================
# Supabase
# ============================================
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.github.jan.supabase.**
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# Keep Supabase storage classes
-keep class io.github.jan.supabase.storage.** { *; }
-keep class io.github.jan.supabase.postgrest.** { *; }

# ============================================
# MediaPipe
# ============================================
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-dontwarn com.google.mediapipe.**

# ============================================
# TensorFlow Lite
# ============================================
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn org.tensorflow.lite.support.**
-dontwarn org.tensorflow.lite.gpu.**

# Keep TensorFlow Lite native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================
# Gson
# ============================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# Keep TypeToken and its generic type information
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.reflect.TypeToken <methods>;
}

# Keep Gson model classes with constructors and fields
-keep class com.example.kamaynikasyon.features.lessons.data.models.** { 
    <fields>;
    <init>(...);
}
-keep class com.example.kamaynikasyon.features.quizzes.data.models.** { 
    <fields>;
    <init>(...);
}
-keep class com.example.kamaynikasyon.features.dictionary.data.models.** { 
    <fields>;
    <init>(...);
}
-keep class com.example.kamaynikasyon.core.media.** { 
    <fields>;
    <init>(...);
}
-keep class com.example.kamaynikasyon.core.TFLiteModelConfig.** { 
    <fields>;
    <init>(...);
}

# Keep all data classes used with Gson (Kotlin data classes)
-keepclassmembers class com.example.kamaynikasyon.** {
    <fields>;
    <init>(...);
}

# Keep classes with @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep enum classes used with Gson
-keepclassmembers enum * {
    @com.google.gson.annotations.SerializedName <methods>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep generic signatures for Gson
-keepattributes Signature
-keep class * extends java.lang.reflect.Type

# ============================================
# Room Database
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep Room entities
-keep class com.example.kamaynikasyon.data.database.** { *; }
-keep interface com.example.kamaynikasyon.data.database.** { *; }

# Keep Room DAOs
-keep @androidx.room.Dao interface *
-keepclassmembers interface * {
    @androidx.room.* <methods>;
}

# ============================================
# AndroidX
# ============================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Keep ViewBinding
-keep class * extends androidx.viewbinding.ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding

# Keep Lifecycle
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }

# Keep Navigation
-keep class androidx.navigation.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Media3 (ExoPlayer)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================
# Glide
# ============================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# ============================================
# Application classes
# ============================================
-keep class com.example.kamaynikasyon.KamaynikasyonApplication { *; }
-keep class com.example.kamaynikasyon.MainActivity { *; }

# ============================================
# Keep data classes used in Parcelable
# ============================================
-keep class com.example.kamaynikasyon.** implements android.os.Parcelable { *; }

# ============================================
# Keep reflection-based classes
# ============================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================
# Keep custom views
# ============================================
-keep class com.example.kamaynikasyon.core.** { *; }
-keep class com.example.kamaynikasyon.minigames.** { *; }

# ============================================
# Remove logging in release (optional)
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============================================
# Keep Crashlytics mapping
# ============================================
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Keep Crashlytics custom keys
-keep class com.google.firebase.crashlytics.** { *; }

# ============================================
# Keep JSON parsing classes
# ============================================
# Keep org.json classes (used for JSONObject/JSONArray parsing)
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

# Keep repositories that use Gson and TypeToken
-keep class com.example.kamaynikasyon.features.lessons.data.repositories.** { *; }
-keep class com.example.kamaynikasyon.features.quizzes.data.repositories.** { *; }
-keep class com.example.kamaynikasyon.features.dictionary.data.repositories.** { *; }

# Keep core utilities that read JSON from assets
-keep class com.example.kamaynikasyon.core.TFLiteModelConfig { *; }
-keep class com.example.kamaynikasyon.core.TFLiteModelConfig$** { *; }
-keep class com.example.kamaynikasyon.core.supabase.ContentSyncManager { *; }
-keep class com.example.kamaynikasyon.core.utils.CacheManager { *; }

# Keep minigame model classes
-keep class com.example.kamaynikasyon.minigames.bubbleshooter.** { 
    <fields>;
    <init>(...);
}
-keep class com.example.kamaynikasyon.minigames.gesturematch.** { 
    <fields>;
    <init>(...);
}
-keep class com.example.kamaynikasyon.minigames.picturequiz.** { 
    <fields>;
    <init>(...);
}
-keep class com.example.kamaynikasyon.minigames.spellingsequence.** { 
    <fields>;
    <init>(...);
}

# Keep classes with @SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================
# Keep ViewBinding generated classes
# ============================================
-keep class * extends androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}

# ============================================
# Keep BuildConfig
# ============================================
-keep class com.example.kamaynikasyon.BuildConfig { *; }

# ============================================
# Warnings suppression
# ============================================
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn kotlin.Unit
-dontwarn kotlin.jvm.internal.**
-dontwarn org.jetbrains.annotations.**
