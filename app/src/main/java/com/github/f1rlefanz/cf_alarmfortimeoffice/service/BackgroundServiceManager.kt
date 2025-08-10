package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.content.Context
import android.os.Build
import androidx.work.*
import androidx.work.CoroutineWorker
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.worker.BackgroundTokenRefreshWorker
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Simplified Background Service Manager - Focus on reliable core functionality.
 * 
 * REFACTORED: Removed OnePlus-specific monitoring and complex configuration tracking.
 * This version focuses on the essential token refresh service that already works perfectly.
 * 
 * Core Features:
 * - Background token refresh worker management
 * - Clean service lifecycle management
 * - Simple alarm failure handling
 * 
 * Philosophy: If the service works (and it does!), keep it simple.
 */
class BackgroundServiceManager(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var instance: BackgroundServiceManager? = null
        
        fun getInstance(context: Context): BackgroundServiceManager {
            return instance ?: synchronized(this) {
                instance ?: BackgroundServiceManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val preferences = context.getSharedPreferences("background_services", Context.MODE_PRIVATE)
    
    /**
     * Initializes background services
     */
    fun initializeBackgroundServices() {
        Logger.business(LogTags.TOKEN, "🚀 Initializing background services")
        
        try {
            // Start token refresh service
            BackgroundTokenRefreshWorker.scheduleTokenRefresh(context)
            
            // Mark services as started
            preferences.edit()
                .putLong("services_started_at", System.currentTimeMillis())
                .putString("device_info", "${Build.MANUFACTURER} ${Build.MODEL}")
                .apply()
            
            Logger.business(LogTags.TOKEN, "✅ Background services initialized successfully - Simple and reliable!")
            
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "❌ Failed to initialize background services", e)
        }
    }
    
    /**
     * Triggers urgent token refresh
     */
    fun triggerUrgentTokenRefresh() {
        try {
            BackgroundTokenRefreshWorker.scheduleUrgentTokenRefresh(context)
            Logger.business(LogTags.TOKEN, "⚡ Urgent token refresh triggered")
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "❌ Failed to trigger urgent token refresh", e)
        }
    }
    
    /**
     * Handles alarm failure events
     */
    fun onAlarmFailureDetected(alarmId: Int, failureReason: String) {
        Logger.business(LogTags.ALARM, "🚨 Alarm failure detected", "ID: $alarmId, Reason: $failureReason")
        
        // Trigger urgent token refresh in case it was an auth issue
        triggerUrgentTokenRefresh()
        
        // Log failure for tracking
        preferences.edit()
            .putLong("last_alarm_failure", System.currentTimeMillis())
            .putString("last_failure_reason", failureReason)
            .apply()
    }
    
    /**
     * Stops all background services
     */
    fun stopAllBackgroundServices() {
        try {
            BackgroundTokenRefreshWorker.cancelTokenRefresh(context)
            
            Logger.business(LogTags.TOKEN, "🛑 All background services stopped")
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "❌ Error stopping background services", e)
        }
    }
}
