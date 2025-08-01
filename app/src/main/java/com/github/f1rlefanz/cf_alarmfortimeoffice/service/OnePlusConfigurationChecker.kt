package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.EnhancedOnePlusConfigStatus
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.OnePlusReliabilityLevel
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus.OnePlusSetupActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 🚀 PHASE 2: OnePlus Configuration Monitoring Service
 * 
 * Monitors OnePlus device configuration and alerts users when
 * settings have been reset (common after OnePlus updates).
 * 
 * Features:
 * - Periodic configuration checks
 * - Smart notifications when settings reset
 * - Integration with OnePlus Setup Activity
 * - Configuration history tracking
 */
class OnePlusConfigurationChecker(
    private val context: Context,
    private val batteryOptimizationManager: BatteryOptimizationManager
) {
    
    companion object {
        private const val PREFS_NAME = "oneplus_config_checker"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_LAST_BATTERY_OPTIMIZATION_STATUS = "last_battery_status"
        private const val KEY_CONFIGURATION_RESETS_COUNT = "config_resets_count"
        private const val KEY_LAST_FIRMWARE_VERSION = "last_firmware_version"
        
        private const val NOTIFICATION_CHANNEL_ID = "oneplus_config_alerts"
        private const val NOTIFICATION_ID_CONFIG_RESET = 3001
        private const val NOTIFICATION_ID_SETUP_REMINDER = 3002
        
        // Check frequency: every 24 hours
        private const val CHECK_INTERVAL_HOURS = 24
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 🔍 Performs comprehensive OnePlus configuration check
     */
    fun performConfigurationCheck() {
        if (!BatteryOptimizationManager.isOnePlusDevice()) {
            return // Not a OnePlus device
        }
        
        coroutineScope.launch {
            try {
                Logger.business(LogTags.BATTERY_OPTIMIZATION, "🔍 Starting OnePlus configuration check")
                
                val configStatus = batteryOptimizationManager.getEnhancedOnePlusConfigurationStatus()
                    ?: return@launch  // Enhanced status not available, skip
                
                val currentTime = LocalDateTime.now()
                val lastCheckTime = getLastCheckTime()
                val shouldCheck = lastCheckTime == null || 
                    currentTime.isAfter(lastCheckTime.plusHours(CHECK_INTERVAL_HOURS.toLong()))
                
                if (!shouldCheck) {
                    Logger.d(LogTags.BATTERY_OPTIMIZATION, "⏭️ OnePlus check skipped - too recent")
                    return@launch
                }
                
                // Check for configuration changes
                val previousBatteryStatus = getPreviousBatteryOptimizationStatus()
                val currentBatteryStatus = configStatus.batteryOptimizationExempt
                val firmwareChanged = hasFirmwareChanged()
                
                // Detect configuration reset
                if (previousBatteryStatus && !currentBatteryStatus) {
                    handleConfigurationReset(firmwareChanged)
                }
                
                // Check if setup is needed
                if (configStatus.estimatedReliability.reliabilityLevel == OnePlusReliabilityLevel.POOR) {
                    handleSetupReminder(configStatus)
                }
                
                // Update stored values
                updateCheckHistory(currentTime, currentBatteryStatus)
                
                Logger.business(
                    LogTags.BATTERY_OPTIMIZATION,
                    "✅ OnePlus configuration check completed",
                    "Reliability: ${configStatus.estimatedReliability.currentReliability}%"
                )
                
            } catch (e: Exception) {
                Logger.e(LogTags.BATTERY_OPTIMIZATION, "❌ OnePlus configuration check failed", e)
            }
        }
    }
    
    private fun handleConfigurationReset(firmwareChanged: Boolean) {
        val resetsCount = incrementConfigurationResetsCount()
        
        Logger.w(
            LogTags.BATTERY_OPTIMIZATION,
            "🔴 OnePlus configuration reset detected! Count: $resetsCount, Firmware changed: $firmwareChanged"
        )
        
        createNotificationChannel()
        
        val setupIntent = Intent(context, OnePlusSetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(OnePlusSetupActivity.EXTRA_FROM_ALARM_FAILURE, true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_CONFIG_RESET,
            setupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🔴 OnePlus Einstellungen zurückgesetzt!")
            .setContentText(
                if (firmwareChanged) 
                    "System-Update hat Alarm-Einstellungen zurückgesetzt" 
                else 
                    "OnePlus hat deine Alarm-Einstellungen zurückgesetzt"
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_preferences,
                "Jetzt konfigurieren",
                pendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildString {
                    if (firmwareChanged) {
                        appendLine("🔄 OnePlus System-Update erkannt!")
                    }
                    appendLine("⚠️ Deine Alarm-Einstellungen wurden zurückgesetzt.")
                    appendLine("🔧 Tippe hier, um sie wieder zu konfigurieren.")
                    if (resetsCount > 1) {
                        appendLine("\n📊 Dies ist Reset #$resetsCount")
                    }
                })
            )
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_CONFIG_RESET, notification)
    }
    
    private fun handleSetupReminder(configStatus: EnhancedOnePlusConfigStatus) {
        // Only show reminder if reliability is poor and user hasn't been reminded recently
        val lastReminderTime = preferences.getLong("last_setup_reminder", 0)
        val currentTime = System.currentTimeMillis()
        val reminderInterval = 7 * 24 * 60 * 60 * 1000L // 7 days
        
        if (currentTime - lastReminderTime < reminderInterval) {
            return // Too recent
        }
        
        createNotificationChannel()
        
        val setupIntent = Intent(context, OnePlusSetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_SETUP_REMINDER,
            setupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💡 OnePlus Alarm-Optimierung")
            .setContentText("Verbessere deine Alarm-Zuverlässigkeit auf ${configStatus.estimatedReliability.maxPossibleReliability}%")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildString {
                    appendLine("📊 Aktuelle Zuverlässigkeit: ${configStatus.estimatedReliability.currentReliability}%")
                    appendLine("🎯 Mögliche Verbesserung: ${configStatus.estimatedReliability.maxPossibleReliability}%")
                    appendLine("⚙️ ${configStatus.configurationSteps.count { !it.isCompleted }} Schritte fehlen noch")
                    appendLine("\n🔧 Tippe hier für die Schritt-für-Schritt Anleitung.")
                })
            )
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_SETUP_REMINDER, notification)
        
        // Update reminder time
        preferences.edit()
            .putLong("last_setup_reminder", currentTime)
            .apply()
        
        Logger.business(LogTags.BATTERY_OPTIMIZATION, "💡 OnePlus setup reminder sent")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "OnePlus Konfiguration",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Benachrichtigungen über OnePlus Konfigurationsänderungen"
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun getLastCheckTime(): LocalDateTime? {
        val timeString = preferences.getString(KEY_LAST_CHECK_TIME, null)
        return timeString?.let { 
            try {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun getPreviousBatteryOptimizationStatus(): Boolean {
        return preferences.getBoolean(KEY_LAST_BATTERY_OPTIMIZATION_STATUS, false)
    }
    
    private fun hasFirmwareChanged(): Boolean {
        val currentFirmware = Build.DISPLAY
        val lastFirmware = preferences.getString(KEY_LAST_FIRMWARE_VERSION, null)
        
        if (lastFirmware == null) {
            // First run - store current firmware
            preferences.edit()
                .putString(KEY_LAST_FIRMWARE_VERSION, currentFirmware)
                .apply()
            return false
        }
        
        val changed = currentFirmware != lastFirmware
        if (changed) {
            preferences.edit()
                .putString(KEY_LAST_FIRMWARE_VERSION, currentFirmware)
                .apply()
            Logger.business(LogTags.BATTERY_OPTIMIZATION, "🔄 OnePlus firmware change detected: $lastFirmware → $currentFirmware")
        }
        
        return changed
    }
    
    private fun incrementConfigurationResetsCount(): Int {
        val current = preferences.getInt(KEY_CONFIGURATION_RESETS_COUNT, 0)
        val new = current + 1
        preferences.edit()
            .putInt(KEY_CONFIGURATION_RESETS_COUNT, new)
            .apply()
        return new
    }
    
    private fun updateCheckHistory(checkTime: LocalDateTime, batteryStatus: Boolean) {
        preferences.edit()
            .putString(KEY_LAST_CHECK_TIME, checkTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .putBoolean(KEY_LAST_BATTERY_OPTIMIZATION_STATUS, batteryStatus)
            .apply()
    }
    
    /**
     * 🎯 Public API for triggering immediate check
     */
    fun triggerImmediateCheck() {
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "🔍 Triggering immediate OnePlus configuration check")
        performConfigurationCheck()
    }
    
    /**
     * 📊 Get configuration check statistics
     */
    fun getCheckStatistics(): OnePlusCheckStats {
        return OnePlusCheckStats(
            lastCheckTime = getLastCheckTime(),
            configurationResetsCount = preferences.getInt(KEY_CONFIGURATION_RESETS_COUNT, 0),
            lastKnownFirmware = preferences.getString(KEY_LAST_FIRMWARE_VERSION, "Unknown"),
            isMonitoringActive = BatteryOptimizationManager.isOnePlusDevice()
        )
    }
    
    /**
     * 🧹 Reset all tracking data (for testing or troubleshooting)
     */
    fun resetTrackingData() {
        preferences.edit().clear().apply()
        Logger.w(LogTags.BATTERY_OPTIMIZATION, "🧹 OnePlus configuration tracking data reset")
    }
}

/**
 * Statistics about OnePlus configuration monitoring
 */
data class OnePlusCheckStats(
    val lastCheckTime: LocalDateTime?,
    val configurationResetsCount: Int,
    val lastKnownFirmware: String?,
    val isMonitoringActive: Boolean
)
