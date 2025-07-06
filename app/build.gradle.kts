@file:Suppress("UnstableApiUsage")

import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

android {
    namespace = "com.github.f1rlefanz.cf_alarmfortimeoffice"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.f1rlefanz.cf_alarmfortimeoffice"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Google Sign-In Web Client ID als BuildConfig
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"931091152160-8s3nd7os2p61ac6ecm799gjhekkf0b4i.apps.googleusercontent.com\"")
        
        // PERFORMANCE: Vector drawable optimization
        vectorDrawables.useSupportLibrary = true
        
        // PERFORMANCE: ProGuard optimization hints
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true // PERFORMANCE: R8 aktiviert für optimierte APK
            isShrinkResources = true // PERFORMANCE: Entfernt ungenutzte Ressourcen
            isDebuggable = false // PERFORMANCE: Debug-Code entfernen
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // PERFORMANCE: Additional R8 optimizations
            packaging {
                resources {
                    excludes += setOf(
                        "META-INF/INDEX.LIST",
                        "META-INF/DEPENDENCIES",
                        "META-INF/LICENSE",
                        "META-INF/LICENSE.txt",
                        "META-INF/NOTICE",
                        "META-INF/NOTICE.txt",
                        "META-INF/*.version",
                        "META-INF/*.kotlin_module",
                        "**/*.pro",
                        "**/*.properties"
                    )
                }
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = null // REMOVED - verursacht Google Sign-In Probleme
            
            // PERFORMANCE: Debug build optimizations
            packaging {
                resources {
                    excludes += setOf(
                        "META-INF/INDEX.LIST",
                        "META-INF/DEPENDENCIES"
                    )
                }
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjsr305=strict" // PERFORMANCE: Strict null safety
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // PERFORMANCE: Disable unused features
        aidl = false
        renderScript = false
        shaders = false
        resValues = false
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    
    // PERFORMANCE: Consolidated packaging configuration
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/ASL2.0",
                "META-INF/gradle/**",
                "**/kotlin-tooling-metadata.json"
            )
        }
    }
    
    // PERFORMANCE: Configure lint for better build times
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        checkDependencies = false
    }
    
    // PERFORMANCE: Configure test options
    testOptions {
        unitTests {
            isIncludeAndroidResources = false // Speed up unit tests
        }
    }
}

dependencies {
    // CORE ANDROID
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // COMPOSE BOM - ensures compatible versions
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material.icons.extended)
    
    // LIFECYCLE & VIEWMODEL
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // AUTHENTICATION & CREDENTIALS
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.gpsAuth)
    implementation(libs.play.services.auth)
    implementation(libs.googleid)
    
    // DATA STORAGE & SERIALIZATION
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    
    // GOOGLE API SERVICES
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.google.auth.library.credentials)
    implementation(libs.gson)
    
    // NETWORK - HUE INTEGRATION
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    
    // UTILITIES
    implementation(libs.timber)
    
    // DESUGARING
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    // TESTING
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    // DEBUG TOOLS
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
