package com.github.f1rlefanz.cf_alarmfortimeoffice.service.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.BatteryOptimizationManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus.OnePlusSetupActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 🔴 PHASE 3: OnePlus Configuration Monitor Worker
 * 
 * Continuously monitors OnePlus device configuration settings to detect
 * when system updates or user actions have reset critical alarm settings.
 */
class OnePlusConfigurationMonitorWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORKER_TAG = "oneplus_config_monitor"
        const val UNIQUE_WORK_NAME = "oneplus_monitor_periodic"
        
        // Monitoring intervals
        private const val DEFAULT_MONITOR_INTERVAL_HOURS = 12L
        private const val CRITICAL_FAILURE_MONITOR_HOURS = 4L
        
        // Notification constants
        private const val NOTIFICATION_CHANNEL_ID = "oneplus_config_alerts"
        private const val NOTIFICATION_ID_CONFIG_RESET = 3001
        private const val NOTIFICATION_ID_LOW_RELIABILITY = 3002
        
        // Configuration change detection
        private const val CRITICAL_RELIABILITY_THRESHOLD = 50 // Below 50% is critical
        
        /**
         * Starts OnePlus configuration monitoring
         */
        fun startMonitoring(context: Context) {
            // Only start on OnePlus devices
            if (!BatteryOptimizationManager.isOnePlusDevice()) {
                Logger.d(LogTags.BATTERY_OPTIMIZATION, "Not a OnePlus device - skipping monitoring")
                return
            }
            
            val workManager = WorkManager.getInstance(context)
            
            // Cancel any existing monitoring
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            
            val monitorRequest = PeriodicWorkRequestBuilder<OnePlusConfigurationMonitorWorker>(
                DEFAULT_MONITOR_INTERVAL_HOURS, TimeUnit.HOURS,
                2, TimeUnit.HOURS // Flexible execution window
            )
                .setConstraints(createMonitoringConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofHours(1))
                .addTag(WORKER_TAG)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                monitorRequest
            )
            
            Logger.business(
                LogTags.BATTERY_OPTIMIZATION, 
                "🔴 OnePlus configuration monitoring started", 
                "Device: ${Build.MODEL}, Interval: ${DEFAULT_MONITOR_INTERVAL_HOURS}h"
            )
        }
        
        /**
         * Stops all OnePlus monitoring
         */
        fun stopMonitoring(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            workManager.cancelAllWorkByTag(WORKER_TAG)
            
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "🛑 OnePlus configuration monitoring stopped")
        }
        
        private fun createMonitoringConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Config check doesn't need network
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(false)
                .build()
        }
    }
    
    private val batteryOptimizationManager by lazy {
        BatteryOptimizationManager(applicationContext)
    }
    
    private val preferences by lazy {
        applicationContext.getSharedPreferences("oneplus_monitor", Context.MODE_PRIVATE)
    }
    
    /**
     * Helper method to check if device is OnePlus
     */
    private fun isOnePlusDevice(): Boolean {
        return Build.MANUFACTURER.equals("OnePlus", ignoreCase = true)
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Logger.business(LogTags.BATTERY_OPTIMIZATION, "🔴 OnePlus configuration monitor started")
        
        try {
            // Verify this is still a OnePlus device
            if (!isOnePlusDevice()) {
                Logger.d(LogTags.BATTERY_OPTIMIZATION, "No longer OnePlus device - stopping monitoring")
                stopMonitoring(applicationContext)
                return@withContext Result.success()
            }
            
            val monitorStartTime = System.currentTimeMillis()
            
            // Get current configuration status
            val currentStatus = batteryOptimizationManager.getOnePlusConfigurationStatus()
            if (currentStatus == null) {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "❌ Could not retrieve OnePlus configuration status")
                return@withContext Result.retry()
            }
            
            // Check for critical reliability issues
            val currentReliability = currentStatus.estimatedReliability.currentReliability
            val previousReliability = preferences.getInt("last_reliability", 100)
            
            // Detect significant reliability drops
            val reliabilityDrop = previousReliability - currentReliability
            val needsAlert = when {
                currentReliability < CRITICAL_RELIABILITY_THRESHOLD -> true
                reliabilityDrop > 20 -> true // 20% drop
                !currentStatus.batteryOptimizationExempt && preferences.getBoolean("was_battery_exempt", false) -> true
                else -> false
            }
            
            if (needsAlert) {
                sendConfigurationAlert(currentReliability, reliabilityDrop)
            }
            
            // Store current status
            preferences.edit()
                .putInt("last_reliability", currentReliability)
                .putBoolean("was_battery_exempt", currentStatus.batteryOptimizationExempt)
                .putLong("last_check_timestamp", System.currentTimeMillis())
                .putString("last_build_display", Build.DISPLAY)
                .apply()
            
            val monitorDuration = System.currentTimeMillis() - monitorStartTime
            
            Logger.business(
                LogTags.BATTERY_OPTIMIZATION, 
                "✅ OnePlus configuration monitoring completed", 
                "Duration: ${monitorDuration}ms, Reliability: ${currentReliability}%, Alert: $needsAlert"
            )
            
            Result.success(createMonitoringOutputData(currentReliability, needsAlert, monitorDuration))
            
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "💥 OnePlus configuration monitoring failed", e)
            Result.failure(createFailureOutputData(e))
        }
    }
    
    /**
     * Sends configuration alert notification
     */
    private suspend fun sendConfigurationAlert(currentReliability: Int, reliabilityDrop: Int) {
        createNotificationChannel()
        
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val (title, description, priority) = when {
            currentReliability < 30 -> Triple(
                "🔴 Kritische Alarm-Zuverlässigkeit",
                "Nur ${currentReliability}% Zuverlässigkeit - Sofortige Konfiguration erforderlich",
                NotificationCompat.PRIORITY_HIGH
            )
            
            currentReliability < CRITICAL_RELIABILITY_THRESHOLD -> Triple(
                "⚠️ Niedrige Alarm-Zuverlässigkeit", 
                "Aktuelle Zuverlässigkeit: ${currentReliability}% - Konfiguration empfohlen",
                NotificationCompat.PRIORITY_DEFAULT
            )
            
            reliabilityDrop > 20 -> Triple(
                "📉 Zuverlässigkeit gesunken",
                "Alarm-Zuverlässigkeit um ${reliabilityDrop}% gesunken (jetzt ${currentReliability}%)",
                NotificationCompat.PRIORITY_DEFAULT
            )
            
            else -> Triple(
                "🔧 OnePlus-Konfiguration prüfen",
                "Einstellungen wurden möglicherweise zurückgesetzt",
                NotificationCompat.PRIORITY_DEFAULT
            )
        }
        
        val setupIntent = createSetupIntent()
        
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$description\n\n" +
                "Ohne korrekte OnePlus-Konfiguration können Alarme ausfallen. " +
                "Tippen Sie hier, um die Einstellungen zu überprüfen."
            ))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(setupIntent)
            .addAction(
                android.R.drawable.ic_menu_preferences,
                "Konfigurieren",
                setupIntent
            )
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_LOW_RELIABILITY, notification)
        
        Logger.business(
            LogTags.BATTERY_OPTIMIZATION,
            "🚨 OnePlus configuration alert sent",
            "Reliability: ${currentReliability}%, Drop: ${reliabilityDrop}%"
        )
    }
    
    /**
     * Creates notification channel for OnePlus alerts
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "OnePlus Konfigurationshinweise",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Benachrichtigungen über OnePlus-spezifische Konfigurationsprobleme"
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createSetupIntent(): PendingIntent {
        val intent = Intent(applicationContext, OnePlusSetupActivity::class.java).apply {
            putExtra(OnePlusSetupActivity.EXTRA_FROM_ALARM_FAILURE, false)
            putExtra("from_monitoring", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        return PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID_CONFIG_RESET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Output data creation
     */
    private fun createMonitoringOutputData(
        currentReliability: Int,
        alertSent: Boolean,
        monitorDuration: Long
    ): Data {
        return Data.Builder()
            .putLong("monitor_duration_ms", monitorDuration)
            .putLong("monitor_timestamp", System.currentTimeMillis())
            .putInt("current_reliability", currentReliability)
            .putBoolean("alert_sent", alertSent)
            .putString("device_model", Build.MODEL)
            .putString("result", "success")
            .build()
    }
    
    private fun createFailureOutputData(error: Throwable): Data {
        return Data.Builder()
            .putLong("monitor_timestamp", System.currentTimeMillis())
            .putString("result", "failure")
            .putString("error_message", error.message ?: "Unknown error")
            .putString("error_type", error.javaClass.simpleName)
            .putString("device_model", Build.MODEL)
            .build()
    }
}
