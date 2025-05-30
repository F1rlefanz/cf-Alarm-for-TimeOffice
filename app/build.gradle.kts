import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    //alias(libs.plugins.kotlin.compose)
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
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true

        composeOptions {
            kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
        }
    }
    packaging {
        resources {
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/DEPENDENCIES")
            // Es ist auch üblich, weitere generische META-INF Dateien auszuschließen,
            // die oft Konflikte verursachen, falls weitere Fehler dieser Art auftreten:
            // excludes.add("META-INF/LICENSE")
            // excludes.add("META-INF/LICENSE.txt")
            // excludes.add("META-INF/license.txt")
            // excludes.add("META-INF/NOTICE")
            // excludes.add("META-INF/NOTICE.txt")
            // excludes.add("META-INF/notice.txt")
            // excludes.add("META-INF/ASL2.0")
            // excludes.add("META-INF/*.kotlin_module") // Falls Kotlin-Modul-Deskriptoren Probleme machen
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
        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.ui.test.junit4)
        debugImplementation(libs.androidx.ui.tooling)
        debugImplementation(libs.androidx.ui.test.manifest)

        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.ui)
        implementation(libs.androidx.ui.graphics)
        implementation(libs.androidx.ui.tooling.preview)
        implementation(libs.androidx.material3)


        // Credential Manager (Korrigierte Referenzen!)
        implementation(libs.androidx.credentials)
        implementation(libs.androidx.credentials.gpsAuth)

        // ViewModel
        implementation(libs.androidx.lifecycle.viewmodel.ktx)

        // Lifecycle Runtime Compose (wird oft mit ViewModel benötigt) - Prüfe, ob Alias existiert/nötig ist
        // implementation(libs.androidx.lifecycle.runtime.compose)

        // Alte Google Sign-In Bibliothek (ist korrekt auskommentiert)
        implementation(libs.play.services.auth)

        implementation(libs.timber)
        implementation(libs.googleid)

        implementation(libs.androidx.datastore.preferences)

        implementation(libs.google.api.client.android)
        implementation(libs.google.api.services.calendar)
        implementation(libs.google.auth.library.oauth2.http)
        implementation(libs.google.auth.library.credentials)
        implementation(libs.gson)

    }
}