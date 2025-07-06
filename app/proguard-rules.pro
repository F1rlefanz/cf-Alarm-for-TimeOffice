# ==============================
# CFALARMDEVELOPMENT PROGUARD RULES
# ==============================
# Optimized ProGuard configuration for CFAlarmforTimeOffice
# Focuses on aggressive optimization while preserving critical functionality

# ==============================
# GLOBAL OPTIMIZATION SETTINGS
# ==============================

# Aggressive optimization for smallest APK size
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Enable all safe optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ==============================
# DEBUGGING & CRASH REPORTING
# ==============================

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable,*Annotation*,EnclosingMethod,Signature,InnerClasses

# Rename source files for security
-renamesourcefileattribute SourceFile

# ==============================
# KOTLIN & COROUTINES
# ==============================

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Coroutines support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ==============================
# GOOGLE SERVICES & APIS
# ==============================

# Google API Client Library
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.calendar.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**

# Google Auth Library
-keep class com.google.auth.** { *; }
-dontwarn com.google.auth.**

# Google Sign-In & Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Credentials Manager
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

# ==============================
# APPLICATION DATA MODELS
# ==============================

# Keep all data models for serialization
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.model.** { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.data.** { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.auth.data.** { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.** { *; }

# Keep ViewModel UI States
-keep class **.*UiState { *; }
-keep class **.*State { *; }

# ==============================
# ANDROID ARCHITECTURE COMPONENTS
# ==============================

# DataStore
-keep class androidx.datastore.** { *; }
-keep class * implements androidx.datastore.core.Serializer { *; }
-dontwarn androidx.datastore.**

# ViewModels
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ==============================
# JETPACK COMPOSE
# ==============================

# Keep Compose runtime classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose compiler annotations
-keep class androidx.compose.runtime.Composable
-keep class androidx.compose.runtime.ComposableTarget
-keep class androidx.compose.runtime.ComposableTargetMarker

# ==============================
# SERIALIZATION LIBRARIES
# ==============================

# Gson
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ==============================
# NETWORK LIBRARIES
# ==============================

# OkHttp & Retrofit
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# ==============================
# LOGGING OPTIMIZATION
# ==============================

# Remove Timber logging in release builds
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Remove Log calls
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}

# ==============================
# SECURITY & ENCRYPTION
# ==============================

# Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ==============================
# SUPPRESS WARNINGS
# ==============================

# Common warnings that can be safely ignored
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==============================
# APPLICATION SPECIFIC RULES
# ==============================

# Keep Application class
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.CFAlarmApplication { *; }

# Keep Activities and Services
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.AlarmFullScreenActivity { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.service.** { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.AlarmReceiver { *; }

# Keep interface implementations for DI
-keep class * implements com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.**
-keep class * implements com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.**
-keep class * implements com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository.interfaces.**
-keep class * implements com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase.interfaces.**