# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# PERFORMANCE OPTIMIERUNGEN: ProGuard Rules für CFAlarmforTimeOffice

# Optimize aggressively for smaller APK
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Google API Client - Keep necessary classes
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.calendar.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**
-dontwarn com.google.android.gms.**

# Google Auth Library
-keep class com.google.auth.** { *; }
-dontwarn com.google.auth.**

# Keep model classes for serialization
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.model.** { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.*UiState { *; }
-keep class com.github.f1rlefanz.cf_alarmfortimeoffice.data.** { *; }

# DataStore - Keep preferences
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Compose - Keep for reflection
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Timber Logging - Remove in release
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keep class kotlinx.coroutines.** { *; }

# Keep DataStore generated classes
-keep class androidx.datastore.** { *; }
-keep class * implements androidx.datastore.core.Serializer { *; }

# Remove unused resources
-dontwarn com.google.errorprone.annotations.**

# Optimize enum usage
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*