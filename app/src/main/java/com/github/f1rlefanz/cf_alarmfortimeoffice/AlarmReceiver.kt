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
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * Enhanced BroadcastReceiver for alarms with Android 14+ compatibility.
 * 
 * Features:
 * - Android 14+ Full-Screen Intent APIs with ActivityOptions
 * - Enhanced notification reliability for all Android versions
 * - Optimized wake lock management
 * - Comprehensive error handling and logging
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
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKE_LOCK_TAG
        ).apply {
            acquire(WAKE_LOCK_TIMEOUT)
        }
        
        try {
            val shiftName = intent.getStringExtra(EXTRA_SHIFT_NAME) ?: "Schicht"
            val shiftTime = intent.getStringExtra(EXTRA_SHIFT_TIME) ?: ""
            val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
            
            // 🚀 PHASE 3: Start alarm verification monitoring
            val verificationManager = AlarmVerificationManager(context)
            verificationManager.startAlarmVerification(
                alarmId = alarmId,
                shiftName = shiftName,
                alarmTime = shiftTime
            )
            
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
            
            // 🚀 ANDROID 14+ ENHANCEMENT: Modern Full-Screen Intent with ActivityOptions
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
            
            // 📱 ENHANCED NOTIFICATION: Maximum priority and visibility for alarm reliability
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
                .setFullScreenIntent(pendingIntent, true) // 🔥 CRITICAL: Full-screen notification
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
            
            // 🚀 ENHANCED: Try to start activity with modern approach
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
                // Pre-Android 14: Traditional approach
                context.startActivity(fullScreenIntent)
                Logger.business(LogTags.ALARM_RECEIVER, "✅ Full-screen alarm activity started (legacy mode)")
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_RECEIVER, "❌ Error starting full-screen activity", e)
            // Critical fallback: If Activity fails, at least ensure notification sound
            try {
                val ringtone = RingtoneManager.getRingtone(
                    context, 
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                )
                ringtone?.play()
                Logger.w(LogTags.ALARM_RECEIVER, "⚠️ Fallback: Playing alarm sound directly")
            } catch (fallbackError: Exception) {
                Logger.e(LogTags.ALARM_RECEIVER, "❌ Even fallback sound failed", fallbackError)
            }
        }
    }
}
