package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmVerificationManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation.OnePlusDeviceValidator
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation.OnePlusDeviceValidationResult
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus.OnePlusSetupActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enhanced BroadcastReceiver for alarms with Android 14+ compatibility.
 * 
 * Features:
 * - Android 14+ Full-Screen Intent APIs with ActivityOptions
 * - Enhanced notification reliability for all Android versions
 * - Optimized wake lock management
 * - Comprehensive error handling and logging
 * - OnePlus device detection and failure monitoring
 * 
 * Based on the working backup implementation but modernized for 2025.
 */
class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_SHIFT_NAME = "shift_name"
        const val EXTRA_SHIFT_TIME = "shift_time"
        const val EXTRA_ALARM_ID = "alarm_id"
        
        private const val CHANNEL_ID = "shift_alarm_channel"
        private const val NOTIFICATION_ID = 2001
        private const val WAKE_LOCK_TAG = "CFAlarm:WakeLock"
        private const val WAKE_LOCK_TIMEOUT = 60000L // 1 Minute
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.business(LogTags.ALARM_RECEIVER, "📱 ALARM TRIGGERED! Shift: ${intent.getStringExtra(EXTRA_SHIFT_NAME)}")
        
        // Wake Lock to ensure device wakes up
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(WAKE_LOCK_TIMEOUT)
        }
        
        try {
            val shiftName = intent.getStringExtra(EXTRA_SHIFT_NAME) ?: "Schicht"
            val shiftTime = intent.getStringExtra(EXTRA_SHIFT_TIME) ?: ""
            val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
            
            // Start alarm verification monitoring
            val verificationManager = AlarmVerificationManager(context)
            verificationManager.startAlarmVerification(
                alarmId = alarmId,
                shiftName = shiftName,
                alarmTime = shiftTime
            )
            
            // Start alarm failure detection for OnePlus devices
            startAlarmFailureDetection(context, alarmId, shiftName)
            
            // Create notification channel (only needed once)
            createNotificationChannel(context)
            
            // Show alarm notification with sound
            showAlarmNotification(context, shiftName, shiftTime, alarmId)
            
            // Start full-screen alarm activity
            showFullScreenAlarm(context, shiftName, shiftTime, alarmId)
            
            Logger.business(LogTags.ALARM_RECEIVER, "✅ Alarm $alarmId for $shiftName triggered successfully with verification")
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error handling alarm", e)
        } finally {
            // Release wake lock
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    /**
     * Start alarm failure detection for OnePlus-specific problems
     */
    private fun startAlarmFailureDetection(context: Context, alarmId: Int, shiftName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Logger.business(LogTags.ALARM_RECEIVER, "🔍 FAILURE-DETECTION: Starting monitoring for alarm $alarmId")
                
                // Wait for verification completion or failure
                delay(30000) // Wait 30 seconds for normal verification
                
                // Check if alarm verification succeeded
                val verificationManager = AlarmVerificationManager(context)
                val verificationStatus = verificationManager.getVerificationStatus(alarmId)
                
                when (verificationStatus) {
                    "SUCCESS" -> {
                        Logger.business(LogTags.ALARM_RECEIVER, "✅ FAILURE-DETECTION: Alarm $alarmId verified successfully")
                        return@launch
                    }
                    "TIMEOUT", "UNKNOWN", "FAILED" -> {
                        Logger.w(LogTags.ALARM_RECEIVER, "⚠️ FAILURE-DETECTION: Alarm $alarmId failed verification - Status: $verificationStatus")
                        handleAlarmFailure(context, alarmId, shiftName, verificationStatus)
                    }
                    else -> {
                        Logger.w(LogTags.ALARM_RECEIVER, "⚠️ FAILURE-DETECTION: Alarm $alarmId has unknown status: $verificationStatus")
                        handleAlarmFailure(context, alarmId, shiftName, "UNKNOWN_STATUS")
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM_RECEIVER, "❌ FAILURE-DETECTION: Error monitoring alarm $alarmId", e)
                // Defensive: Even if monitoring fails, try to detect OnePlus issues
                if (isOnePlusDevice(context)) {
                    Logger.business(LogTags.ALARM_RECEIVER, "🔧 FAILURE-DETECTION: OnePlus device detected, recommending configuration check")
                    triggerOnePlusSetupIfNeeded(context, "MONITORING_ERROR")
                }
            }
        }
    }
    
    /**
     * Handle detected alarm failures with OnePlus-specific logic
     */
    private suspend fun handleAlarmFailure(context: Context, alarmId: Int, shiftName: String, failureReason: String) {
        Logger.business(LogTags.ALARM_RECEIVER, "🚨 ALARM-FAILURE: Handling failure for alarm $alarmId - Reason: $failureReason")
        
        // Check if this is a OnePlus device
        if (isOnePlusDevice(context)) {
            Logger.business(LogTags.ALARM_RECEIVER, "📱 OnePlus device detected - analyzing failure pattern")
            
            // Track failure in SharedPreferences for pattern analysis
            trackAlarmFailure(context, alarmId, shiftName, failureReason)
            
            // Check failure frequency
            val recentFailures = getRecentAlarmFailures(context)
            
            when {
                recentFailures >= 2 -> {
                    Logger.business(LogTags.ALARM_RECEIVER, "🔥 CRITICAL: $recentFailures recent alarm failures detected - Triggering OnePlus setup")
                    triggerOnePlusSetupIfNeeded(context, "MULTIPLE_FAILURES")
                }
                failureReason == "TIMEOUT" -> {
                    Logger.w(LogTags.ALARM_RECEIVER, "⏱️ TIMEOUT detected - Likely OnePlus power management interference")
                    delay(5000) // Wait 5 seconds, then check setup need
                    triggerOnePlusSetupIfNeeded(context, "TIMEOUT_PATTERN")
                }
                failureReason == "UNKNOWN" -> {
                    Logger.w(LogTags.ALARM_RECEIVER, "❓ Unknown failure - Could be OnePlus lifecycle interruption")
                    triggerOnePlusSetupIfNeeded(context, "UNKNOWN_FAILURE")
                }
            }
        } else {
            Logger.i(LogTags.ALARM_RECEIVER, "📱 Non-OnePlus device - Using standard failure handling")
            // For non-OnePlus devices, log the failure but don't trigger OnePlus setup
            trackAlarmFailure(context, alarmId, shiftName, failureReason)
        }
    }
    
    /**
     * Track alarm failures in SharedPreferences for pattern analysis
     */
    private fun trackAlarmFailure(context: Context, alarmId: Int, shiftName: String, reason: String) {
        try {
            val prefs = context.getSharedPreferences("alarm_failures", Context.MODE_PRIVATE)
            val currentTime = System.currentTimeMillis()
            
            // Record this failure
            val failureKey = "failure_${currentTime}"
            prefs.edit()
                .putString(failureKey, "$alarmId|$shiftName|$reason|$currentTime")
                .putLong("last_failure_time", currentTime)
                .apply()
                
            Logger.business(LogTags.ALARM_RECEIVER, "📊 TRACKING: Alarm failure recorded - ID:$alarmId, Reason:$reason")
            
            // Cleanup old failures (older than 7 days)
            cleanupOldFailures(prefs, currentTime)
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error tracking alarm failure", e)
        }
    }
    
    /**
     * Analyze recent alarm failures (last 24h)
     */
    private fun getRecentAlarmFailures(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences("alarm_failures", Context.MODE_PRIVATE)
            val currentTime = System.currentTimeMillis()
            val oneDayAgo = currentTime - (24 * 60 * 60 * 1000) // 24 hours ago
            
            var recentFailures = 0
            
            for ((key, value) in prefs.all) {
                if (key.startsWith("failure_") && value is String) {
                    val parts = value.split("|")
                    if (parts.size >= 4) {
                        val failureTime = parts[3].toLongOrNull()
                        if (failureTime != null && failureTime > oneDayAgo) {
                            recentFailures++
                        }
                    }
                }
            }
            
            Logger.business(LogTags.ALARM_RECEIVER, "📈 ANALYSIS: $recentFailures recent alarm failures in last 24h")
            recentFailures
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error analyzing recent failures", e)
            0
        }
    }
    
    /**
     * Clean up old failure records (older than 7 days)
     */
    private fun cleanupOldFailures(prefs: android.content.SharedPreferences, currentTime: Long) {
        try {
            val sevenDaysAgo = currentTime - (7 * 24 * 60 * 60 * 1000) // 7 days ago
            val editor = prefs.edit()
            var cleanedCount = 0
            
            for ((key, value) in prefs.all) {
                if (key.startsWith("failure_") && value is String) {
                    val parts = value.split("|")
                    if (parts.size >= 4) {
                        val failureTime = parts[3].toLongOrNull()
                        if (failureTime != null && failureTime < sevenDaysAgo) {
                            editor.remove(key)
                            cleanedCount++
                        }
                    }
                }
            }
            
            if (cleanedCount > 0) {
                editor.apply()
                Logger.d(LogTags.ALARM_RECEIVER, "🧹 CLEANUP: Removed $cleanedCount old alarm failure records")
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error during failure cleanup", e)
        }
    }
    
    /**
     * Trigger OnePlus Setup Activity for detected problems
     */
    private fun triggerOnePlusSetupIfNeeded(context: Context, trigger: String) {
        try {
            // Check if OnePlus setup was recently shown (avoid spam)
            val prefs = context.getSharedPreferences("oneplus_setup", Context.MODE_PRIVATE)
            val lastShown = prefs.getLong("last_shown_time", 0)
            val currentTime = System.currentTimeMillis()
            val sixHoursAgo = currentTime - (6 * 60 * 60 * 1000) // 6 hours
            
            if (lastShown > sixHoursAgo) {
                Logger.d(LogTags.ALARM_RECEIVER, "⏭️ SETUP-THROTTLE: OnePlus setup shown recently, skipping")
                return
            }
            
            // Update last shown time
            prefs.edit()
                .putLong("last_shown_time", currentTime)
                .putString("last_trigger_reason", trigger)
                .apply()
            
            // Launch OnePlus Setup Activity
            val setupIntent = Intent(context, OnePlusSetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("trigger_reason", trigger)
                putExtra("triggered_by", "alarm_failure_detection")
                putExtra("trigger_time", currentTime)
            }
            
            context.startActivity(setupIntent)
            Logger.business(LogTags.ALARM_RECEIVER, "🚀 SETUP-TRIGGER: OnePlus setup launched due to: $trigger")
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error triggering OnePlus setup", e)
        }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Schicht-Wecker",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Benachrichtigungen für Schicht-Alarme"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setBypassDnd(true) // Bypass "Do Not Disturb" mode
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Logger.d(LogTags.ALARM_RECEIVER, "✅ Notification channel created")
        }
    }
    
    private fun showAlarmNotification(context: Context, shiftName: String, shiftTime: String, alarmId: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Intent to open the full-screen activity
            val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("shift_name", shiftName)
                putExtra("alarm_time", shiftTime)
                putExtra("alarm_id", alarmId)
                putExtra("triggered_via", "full_screen_notification") // Track trigger source
            }
            
            // Android 14+ ENHANCEMENT: Modern Full-Screen Intent with ActivityOptions
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+): Use ActivityOptions for enhanced reliability
                val activityOptions = ActivityOptions.makeBasic().apply {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                
                PendingIntent.getActivity(
                    context,
                    alarmId,
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    activityOptions.toBundle()
                )
            } else {
                // Pre-Android 14: Traditional approach
                PendingIntent.getActivity(
                    context,
                    alarmId,
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            
            // Enhanced alarm sound configuration
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            // Enhanced notification: Maximum priority and visibility for alarm reliability
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Zeit für $shiftName!")
                .setContentText("Deine Schicht beginnt um $shiftTime")
                .setPriority(NotificationCompat.PRIORITY_MAX) // Highest priority
                .setCategory(NotificationCompat.CATEGORY_ALARM) // Alarm category for system recognition
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(alarmSound)
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setFullScreenIntent(pendingIntent, true) // Critical: Full-screen notification
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
                .setOngoing(true) // Can't be dismissed by swiping
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Use all system defaults
                .setTimeoutAfter(5 * 60 * 1000) // Auto-dismiss after 5 minutes
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
            Logger.business(LogTags.ALARM_RECEIVER, "✅ Enhanced alarm notification displayed with Android ${Build.VERSION.SDK_INT} compatibility")
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error showing notification", e)
            // Fallback: Try to start activity directly if notification fails
            showFullScreenAlarm(context, shiftName, shiftTime, alarmId)
        }
    }
    
    private fun showFullScreenAlarm(context: Context, shiftName: String, shiftTime: String, alarmId: Int) {
        try {
            val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS // Exclude from recent apps
                putExtra("shift_name", shiftName)
                putExtra("alarm_time", shiftTime)
                putExtra("alarm_id", alarmId)
                putExtra("triggered_via", "direct_activity_start") // Track trigger source
                setPackage(context.packageName)
            }
            
            // Enhanced: Try to start activity with modern approach
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: Enhanced activity start options
                val activityOptions = ActivityOptions.makeBasic().apply {
                    // These options improve reliability on Android 14+
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                context.startActivity(fullScreenIntent, activityOptions.toBundle())
                Logger.business(LogTags.ALARM_RECEIVER, "✅ Full-screen alarm activity started with Android 14+ enhancements")
            } else {
                context.startActivity(fullScreenIntent)
                Logger.business(LogTags.ALARM_RECEIVER, "✅ Full-screen alarm activity started")
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error starting full-screen activity", e)
        }
    }
    
    /**
     * Helper method to check if device is OnePlus
     */
    private fun isOnePlusDevice(context: Context): Boolean {
        return try {
            val validator = OnePlusDeviceValidator(context)
            val result = validator.validateOnePlusDevice()
            result is OnePlusDeviceValidationResult.Valid
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error checking OnePlus device", e)
            false
        }
    }
}