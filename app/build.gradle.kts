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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.f1rlefanz.cf_alarmfortimeoffice"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
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
        implementation(libs.androidx.lifecycle.viewmodel.compose)

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

        // Philips Hue Integration
        implementation(libs.retrofit)
        implementation(libs.converter.gson)
        implementation(libs.okhttp)
        implementation(libs.logging.interceptor)
        implementation(libs.kotlinx.coroutines.android)

    }
}
