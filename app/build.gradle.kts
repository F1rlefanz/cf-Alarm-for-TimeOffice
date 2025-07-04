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
    }

    buildTypes {
        release {
            isMinifyEnabled = true // PERFORMANCE: ProGuard/R8 aktiviert für kleiner APK
            isShrinkResources = true // Entfernt ungenutzte Ressourcen
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // applicationIdSuffix = ".debug" // ENTFERNT - verursacht Google Sign-In Probleme
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/DEPENDENCIES")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Material Icons Extended for better icon support
    implementation(libs.material.icons.extended)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.gpsAuth)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Google Sign-In
    implementation(libs.play.services.auth)

    implementation(libs.timber)
    implementation(libs.googleid)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.google.auth.library.credentials)
    implementation(libs.gson)
    
    // Security for encrypted token storage
    implementation(libs.androidx.security.crypto)



}
