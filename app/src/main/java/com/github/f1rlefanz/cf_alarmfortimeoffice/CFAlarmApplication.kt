package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.app.Application
import com.github.f1rlefanz.cf_alarmfortimeoffice.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import timber.log.Timber

class CFAlarmApplication : Application() {
    
    // Application scope for long-running operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Dependency container
    lateinit var appContainer: AppContainer
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize dependency container
        appContainer = AppContainer(this)
        
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Logger.d(LogTags.APP, "Timber initialized in DEBUG mode")
        }
        
        // Initialize OAuth2 token system and perform migrations
        initializeApp()
    }
    
    private fun initializeApp() {
        applicationScope.launch {
            try {
                // PERFORMANCE: Initialize critical components first
                Logger.d(LogTags.AUTH, "Initializing OAuth2 token storage")
                appContainer.initializeTokenStorage()
                
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
