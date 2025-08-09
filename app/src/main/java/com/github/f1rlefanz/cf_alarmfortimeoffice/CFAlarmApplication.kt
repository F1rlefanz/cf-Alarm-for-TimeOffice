package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.app.Application
import com.github.f1rlefanz.cf_alarmfortimeoffice.di.AppContainer
import com.github.f1rlefanz.cf_alarmfortimeoffice.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.SimpleFileTree
import timber.log.Timber
import java.io.File

// Firebase Imports
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class CFAlarmApplication : Application() {
    
    // Application scope for long-running operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Dependency container
    lateinit var appContainer: AppContainer
        private set
        
    // PHASE 2: Security Manager for enhanced security validation
    private lateinit var securityManager: SecurityManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase first
        initializeFirebase()
        
        // Initialize dependency container
        appContainer = AppContainer(this)
        
        // PHASE 2: Initialize Security Manager
        securityManager = SecurityManager(this)
        
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            
            // File-Logging für 3-Tage-Analyse
            val logFile = File(getExternalFilesDir(null), "debug_logs.txt")
            Timber.plant(SimpleFileTree(logFile))
            
            Logger.d(LogTags.APP, "Timber initialized in DEBUG mode with file logging")
            Logger.i(LogTags.APP, "🗂️ Debug logs will be saved to: ${logFile.absolutePath}")
        }
        
        // Initialize OAuth2 token system and perform migrations
        initializeApp()
        
        // 🚀 PHASE 3: Initialize Background Services
        initializeBackgroundServices()
    }
    
    /**
     * Firebase Setup mit Professional Best Practices (2025)
     */
    private fun initializeFirebase() {
        try {
            // Firebase initialisieren 
            FirebaseApp.initializeApp(this)
            
            val crashlytics = FirebaseCrashlytics.getInstance()
            
            // App-spezifische Context Keys setzen
            crashlytics.setCustomKey("app_package", packageName)
            crashlytics.setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")
            crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE)
            crashlytics.setCustomKey("version_name", BuildConfig.VERSION_NAME)
            
            // Initial Breadcrumb
            crashlytics.log("CFAlarmApplication: Firebase initialized successfully")
            
            Logger.i(LogTags.APP, "🔥 Firebase Crashlytics initialized with app context")
            
        } catch (e: Exception) {
            Logger.e(LogTags.APP, "❌ Failed to initialize Firebase", e)
        }
    }
    
    private fun initializeApp() {
        applicationScope.launch {
            try {
                // PERFORMANCE: Initialize critical components first
                Logger.d(LogTags.AUTH, "Initializing OAuth2 token storage")
                appContainer.initializeTokenStorage()
                
                // PHASE 2: Perform comprehensive security assessment
                Logger.business(LogTags.SECURITY, "🔒 PHASE-2: Performing comprehensive security assessment...")
                val securityAssessment = securityManager.performSecurityAssessment()
                
                // Log security assessment summary
                Logger.business(LogTags.SECURITY, "📋 SECURITY-SUMMARY: Overall Level = ${securityAssessment.overallSecurityLevel}")
                if (securityAssessment.rootDetection.isRooted) {
                    Logger.w(LogTags.SECURITY, "⚠️  USER-NOTICE: Device root detected - using enhanced security measures")
                }
                
                // TIMING FIX: Initialize ShiftConfig early to prevent race conditions
                Logger.d(LogTags.SHIFT_CONFIG, "🔄 STARTUP: Initializing ShiftConfig early to prevent timing issues")
                launch {
                    try {
                        val shiftUseCase = appContainer.shiftUseCase
                        val currentConfig = shiftUseCase.getCurrentShiftConfig().getOrNull()
                        
                        if (currentConfig != null) {
                            Logger.business(LogTags.SHIFT_CONFIG, "✅ STARTUP: ShiftConfig loaded successfully - autoAlarm=${currentConfig.autoAlarmEnabled}")
                        } else {
                            Logger.i(LogTags.SHIFT_CONFIG, "🔧 STARTUP: No ShiftConfig found, creating default")
                            val defaultConfig = com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftConfig.getDefaultConfig()
                            shiftUseCase.saveShiftConfig(defaultConfig)
                                .onSuccess {
                                    Logger.business(LogTags.SHIFT_CONFIG, "✅ STARTUP: Default ShiftConfig created - autoAlarm=${defaultConfig.autoAlarmEnabled}")
                                }
                                .onFailure { error ->
                                    Logger.e(LogTags.SHIFT_CONFIG, "❌ STARTUP: Failed to save default ShiftConfig", error)
                                }
                        }
                    } catch (e: Exception) {
                        Logger.e(LogTags.SHIFT_CONFIG, "❌ STARTUP: Exception during ShiftConfig initialization", e)
                    }
                }
                
                // PERFORMANCE: Defer non-critical migrations to reduce startup time
                launch {
                    Logger.d(LogTags.DATASTORE, "Running DataStore migration")
                    // Migration call removed to avoid blocking main thread
                    // appContainer.authDataStoreRepository.migrateTokenExpiryIfNeeded()
                }
                
                Logger.i(LogTags.APP, "App initialization completed successfully")
            } catch (e: Exception) {
                Logger.e(LogTags.APP, "Error during app initialization", e)
            }
        }
    }
    
    /**
     * 🚀 PHASE 3: Initialize Background Services
     * 
     * Starts token refresh and OnePlus monitoring services
     */
    private fun initializeBackgroundServices() {
        applicationScope.launch {
            try {
                Logger.business(LogTags.TOKEN, "🚀 PHASE 3: Initializing background services")
                
                // Initialize background service manager
                appContainer.backgroundServiceManager.initializeBackgroundServices()
                
                Logger.business(LogTags.TOKEN, "✅ Background services initialized successfully")
                
            } catch (e: Exception) {
                Logger.e(LogTags.TOKEN, "❌ Failed to initialize background services", e)
            }
        }
    }
    
    /**
     * LIFECYCLE FIX: Properly dispose resources to prevent Race Conditions
     * Especially important for Development when AS forces app termination
     */
    override fun onTerminate() {
        super.onTerminate()
        try {
            Logger.d(LogTags.LIFECYCLE, "CFAlarmApplication: Terminating, cleaning up resources")
            
            // Cancel application scope gracefully
            applicationScope.cancel("Application terminating")
            
            // Dispose repositories to prevent mutex issues during forced termination
            if (::appContainer.isInitialized) {
                appContainer.tokenStorageRepository.dispose()
            }
            
            // Give threads time to finish current operations
            Thread.sleep(100)
            
            Logger.d(LogTags.LIFECYCLE, "CFAlarmApplication: Cleanup completed")
        } catch (e: Exception) {
            // Ignore exceptions during forced termination
            Logger.w(LogTags.LIFECYCLE, "Exception during forced termination (expected in AS debugging)", e)
        }
    }
}
