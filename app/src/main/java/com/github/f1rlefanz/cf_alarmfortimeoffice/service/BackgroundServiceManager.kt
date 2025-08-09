package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.content.Context
import android.os.Build
import androidx.work.*
import androidx.work.CoroutineWorker
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.worker.BackgroundTokenRefreshWorker
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.worker.OnePlusConfigurationMonitorWorker
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 🚀 PHASE 3: Background Service Manager
 * 
 * Centralized manager for all background services including token refresh
 * and OnePlus configuration monitoring.
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
     * Initializes all background services
     */
    fun initializeBackgroundServices() {
        Logger.business(LogTags.TOKEN, "🚀 Initializing background services")
        
        try {
            // Always start token refresh service
            BackgroundTokenRefreshWorker.scheduleTokenRefresh(context)
            
            // Start OnePlus monitoring only on OnePlus devices
            if (BatteryOptimizationManager.isOnePlusDevice()) {
                OnePlusConfigurationMonitorWorker.startMonitoring(context)
                Logger.business(LogTags.BATTERY_OPTIMIZATION, "🔴 OnePlus monitoring started")
            }
            
            // Mark services as started
            preferences.edit()
                .putLong("services_started_at", System.currentTimeMillis())
                .putString("device_info", "${Build.MANUFACTURER} ${Build.MODEL}")
                .apply()
            
            Logger.business(LogTags.TOKEN, "✅ Background services initialized successfully")
            
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
        
        // Log failure for OnePlus monitoring
        if (BatteryOptimizationManager.isOnePlusDevice()) {
            preferences.edit()
                .putLong("last_alarm_failure", System.currentTimeMillis())
                .putString("last_failure_reason", failureReason)
                .apply()
        }
    }
    
    /**
     * Stops all background services
     */
    fun stopAllBackgroundServices() {
        try {
            BackgroundTokenRefreshWorker.cancelTokenRefresh(context)
            OnePlusConfigurationMonitorWorker.stopMonitoring(context)
            
            Logger.business(LogTags.TOKEN, "🛑 All background services stopped")
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "❌ Error stopping background services", e)
        }
    }
}
