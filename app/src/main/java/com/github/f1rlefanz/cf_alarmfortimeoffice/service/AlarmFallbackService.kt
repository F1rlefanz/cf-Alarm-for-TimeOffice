package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.github.f1rlefanz.cf_alarmfortimeoffice.AlarmFullScreenActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.*

/**
 * 🚀 PHASE 3: Hybrid Alarm Fallback Service
 * 
 * This service acts as an ultimate fallback when the AlarmFullScreenActivity
 * fails to start or gets killed by aggressive power management.
 * 
 * Features:
 * - Foreground service for maximum survival
 * - Independent MediaPlayer for reliable sound
 * - Persistent notification for user interaction
 * - Wake lock management for extreme cases
 * - Automatic escalation strategies
 * 
 * Activation Scenarios:
 * - AlarmFullScreenActivity fails to start
 * - Activity gets killed by system/OEM power management
 * - OnePlus/aggressive OEM interference detected
 * - User-requested fallback mode
 */
class AlarmFallbackService : Service() {
    
    companion object {
        private const val SERVICE_ID = 4001
        private const val CHANNEL_ID = "alarm_fallback_channel"
        private const val WAKE_LOCK_TAG = "CFAlarm:FallbackService"
        private const val WAKE_LOCK_TIMEOUT = 15 * 60 * 1000L // 15 minutes max
        
        // Intent actions
        const val ACTION_START_FALLBACK_ALARM = "start_fallback_alarm"
        const val ACTION_STOP_FALLBACK_ALARM = "stop_fallback_alarm"
        const val ACTION_SNOOZE_FALLBACK_ALARM = "snooze_fallback_alarm"
        
        // Intent extras
        const val EXTRA_SHIFT_NAME = "shift_name"
        const val EXTRA_ALARM_TIME = "alarm_time"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ACTIVATION_REASON = "activation_reason"
        const val EXTRA_ESCALATION_LEVEL = "escalation_level"
        
        /**
         * 🎯 Start the fallback alarm service
         */
        fun startFallbackAlarm(
            context: Context,
            shiftName: String,
            alarmTime: String,
            alarmId: Int,
            activationReason: FallbackActivationReason = FallbackActivationReason.ACTIVITY_FAILURE,
            escalationLevel: EscalationLevel = EscalationLevel.STANDARD
        ) {
            val intent = Intent(context, AlarmFallbackService::class.java).apply {
                action = ACTION_START_FALLBACK_ALARM
                putExtra(EXTRA_SHIFT_NAME, shiftName)
                putExtra(EXTRA_ALARM_TIME, alarmTime)
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ACTIVATION_REASON, activationReason.name)
                putExtra(EXTRA_ESCALATION_LEVEL, escalationLevel.name)
            }
            
            try {
                context.startForegroundService(intent)
                Logger.business(LogTags.ALARM, "🆘 Fallback alarm service started: $activationReason")
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "❌ Failed to start fallback service", e)
            }
        }
        
        /**
         * 🛑 Stop the fallback alarm service
         */
        fun stopFallbackAlarm(context: Context) {
            val intent = Intent(context, AlarmFallbackService::class.java).apply {
                action = ACTION_STOP_FALLBACK_ALARM
            }
            context.startService(intent)
        }
    }
    
    // Service state
    private var isAlarmActive = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    
    // Alarm data
    private var currentShiftName: String = ""
    private var currentAlarmTime: String = ""
    private var currentAlarmId: Int = -1
    private var activationReason: FallbackActivationReason = FallbackActivationReason.ACTIVITY_FAILURE
    private var escalationLevel: EscalationLevel = EscalationLevel.STANDARD
    
    // Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var escalationJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        Logger.business(LogTags.ALARM, "🆘 AlarmFallbackService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FALLBACK_ALARM -> handleStartFallbackAlarm(intent)
            ACTION_STOP_FALLBACK_ALARM -> handleStopFallbackAlarm()
            ACTION_SNOOZE_FALLBACK_ALARM -> handleSnoozeAlarm()
            else -> {
                Logger.w(LogTags.ALARM, "⚠️ Unknown action in fallback service: ${intent?.action}")
            }
        }
        
        // Return START_NOT_STICKY to prevent automatic restart after system kill
        // We want explicit control over service lifecycle
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun handleStartFallbackAlarm(intent: Intent) {
        if (isAlarmActive) {
            Logger.w(LogTags.ALARM, "⚠️ Fallback alarm already active, ignoring duplicate start")
            return
        }
        
        // Extract alarm data
        currentShiftName = intent.getStringExtra(EXTRA_SHIFT_NAME) ?: "Fallback Alarm"
        currentAlarmTime = intent.getStringExtra(EXTRA_ALARM_TIME) ?: "Jetzt"
        currentAlarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        activationReason = FallbackActivationReason.valueOf(
            intent.getStringExtra(EXTRA_ACTIVATION_REASON) ?: FallbackActivationReason.ACTIVITY_FAILURE.name
        )
        escalationLevel = EscalationLevel.valueOf(
            intent.getStringExtra(EXTRA_ESCALATION_LEVEL) ?: EscalationLevel.STANDARD.name
        )
        
        Logger.business(
            LogTags.ALARM,
            "🆘 FALLBACK ALARM ACTIVATED",
            "Shift: $currentShiftName, Reason: $activationReason, Level: $escalationLevel"
        )
        
        // Acquire wake lock for extreme reliability
        acquireWakeLock()
        
        // Start foreground service
        startForeground(SERVICE_ID, createFallbackNotification())
        
        // Start alarm effects
        startFallbackAlarmEffects()
        
        // Start escalation strategy
        startEscalationStrategy()
        
        isAlarmActive = true
    }
    
    private fun handleStopFallbackAlarm() {
        Logger.business(LogTags.ALARM, "🛑 Stopping fallback alarm service")
        
        stopFallbackAlarmEffects()
        releaseWakeLock()
        escalationJob?.cancel()
        
        isAlarmActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun handleSnoozeAlarm() {
        Logger.business(LogTags.ALARM, "😴 Snoozing fallback alarm for 5 minutes")
        
        stopFallbackAlarmEffects()
        
        // Schedule restart in 5 minutes
        serviceScope.launch {
            delay(5 * 60 * 1000L) // 5 minutes
            if (isAlarmActive) {
                startFallbackAlarmEffects()
                updateFallbackNotification("Alarm wieder aktiv nach Snooze")
            }
        }
        
        updateFallbackNotification("Alarm pausiert für 5 Minuten")
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT)
            }
            Logger.business(LogTags.ALARM, "✅ Fallback service wake lock acquired")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Failed to acquire fallback wake lock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Logger.d(LogTags.ALARM, "✅ Fallback service wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error releasing fallback wake lock", e)
        }
    }
    
    private fun startFallbackAlarmEffects() {
        Logger.business(LogTags.ALARM, "🔊 Starting fallback alarm effects with escalation level: $escalationLevel")
        
        startFallbackSound()
        startFallbackVibration()
    }
    
    private fun startFallbackSound() {
        try {
            // Use MediaPlayer for more control than RingtoneManager
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                
                setDataSource(this@AlarmFallbackService, alarmUri)
                isLooping = true
                
                // Set volume based on escalation level
                val volume = when (escalationLevel) {
                    EscalationLevel.GENTLE -> 0.7f
                    EscalationLevel.STANDARD -> 0.9f
                    EscalationLevel.AGGRESSIVE -> 1.0f
                    EscalationLevel.MAXIMUM -> 1.0f
                }
                setVolume(volume, volume)
                
                prepareAsync()
                setOnPreparedListener { player ->
                    player.start()
                    Logger.business(LogTags.ALARM, "✅ Fallback MediaPlayer started with volume: $volume")
                }
                
                setOnErrorListener { _, what, extra ->
                    Logger.e(LogTags.ALARM, "❌ MediaPlayer error: what=$what, extra=$extra")
                    true // Handle error
                }
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Failed to start fallback sound", e)
        }
    }
    
    private fun startFallbackVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    // Escalated vibration patterns
                    val pattern = when (escalationLevel) {
                        EscalationLevel.GENTLE -> longArrayOf(0, 800, 400, 800)
                        EscalationLevel.STANDARD -> longArrayOf(0, 1000, 300, 800, 300, 1000)
                        EscalationLevel.AGGRESSIVE -> longArrayOf(0, 1200, 200, 1000, 200, 1200, 200, 800)
                        EscalationLevel.MAXIMUM -> longArrayOf(0, 1500, 100, 1200, 100, 1500, 100, 1000, 100, 800)
                    }
                    
                    val vibrationEffect = VibrationEffect.createWaveform(pattern, 1) // Repeat from index 1
                    vib.vibrate(vibrationEffect)
                    
                    Logger.business(LogTags.ALARM, "✅ Fallback vibration started with level: $escalationLevel")
                }
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Failed to start fallback vibration", e)
        }
    }
    
    private fun stopFallbackAlarmEffects() {
        // Stop sound
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            mediaPlayer = null
            Logger.d(LogTags.ALARM, "🔇 Fallback sound stopped")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error stopping fallback sound", e)
        }
        
        // Stop vibration
        try {
            vibrator?.cancel()
            vibrator = null
            Logger.d(LogTags.ALARM, "📴 Fallback vibration stopped")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error stopping fallback vibration", e)
        }
    }
    
    private fun startEscalationStrategy() {
        if (escalationLevel == EscalationLevel.MAXIMUM) {
            return // Already at maximum
        }
        
        escalationJob = serviceScope.launch {
            delay(2 * 60 * 1000L) // Wait 2 minutes before escalating
            
            if (isAlarmActive) {
                val newLevel = when (escalationLevel) {
                    EscalationLevel.GENTLE -> EscalationLevel.STANDARD
                    EscalationLevel.STANDARD -> EscalationLevel.AGGRESSIVE
                    EscalationLevel.AGGRESSIVE -> EscalationLevel.MAXIMUM
                    EscalationLevel.MAXIMUM -> EscalationLevel.MAXIMUM
                }
                
                if (newLevel != escalationLevel) {
                    Logger.w(LogTags.ALARM, "📈 ESCALATING fallback alarm: $escalationLevel → $newLevel")
                    escalationLevel = newLevel
                    
                    // Restart effects with new escalation level
                    stopFallbackAlarmEffects()
                    startFallbackAlarmEffects()
                    updateFallbackNotification("Alarm verstärkt - Level: ${newLevel.name}")
                    
                    // Schedule next escalation
                    startEscalationStrategy()
                }
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Fallback Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fallback-Service für kritische Alarm-Situationen"
                enableVibration(false) // We handle vibration ourselves
                setSound(null, null) // We handle sound ourselves
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createFallbackNotification(): android.app.Notification {
        // Intent to open full screen activity (if possible)
        val fullScreenIntent = Intent(this, AlarmFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("shift_name", currentShiftName)
            putExtra("alarm_time", currentAlarmTime)
            putExtra("alarm_id", currentAlarmId)
            putExtra("from_fallback_service", true)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            currentAlarmId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop action
        val stopIntent = Intent(this, AlarmFallbackService::class.java).apply {
            action = ACTION_STOP_FALLBACK_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            currentAlarmId + 1000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Snooze action
        val snoozeIntent = Intent(this, AlarmFallbackService::class.java).apply {
            action = ACTION_SNOOZE_FALLBACK_ALARM
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            currentAlarmId + 2000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🆘 FALLBACK ALARM: $currentShiftName")
            .setContentText("Zeit: $currentAlarmTime • Grund: ${activationReason.displayName}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_media_pause, "STOPPEN", stopPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "5 MIN", snoozePendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildString {
                    appendLine("🆘 FALLBACK ALARM AKTIV")
                    appendLine("⏰ Schicht: $currentShiftName")
                    appendLine("🕐 Zeit: $currentAlarmTime")
                    appendLine("🔍 Grund: ${activationReason.displayName}")
                    appendLine("📊 Level: ${escalationLevel.name}")
                    if (activationReason == FallbackActivationReason.ONEPLUS_INTERFERENCE) {
                        appendLine("\n🔴 OnePlus Power Management erkannt!")
                    }
                })
            )
            .build()
    }
    
    private fun updateFallbackNotification(statusMessage: String) {
        val notification = createFallbackNotification()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        stopFallbackAlarmEffects()
        releaseWakeLock()
        escalationJob?.cancel()
        serviceScope.cancel()
        
        Logger.business(LogTags.ALARM, "🆘 AlarmFallbackService destroyed")
    }
}

/**
 * 🎯 Reasons why the fallback service was activated
 */
enum class FallbackActivationReason(val displayName: String) {
    ACTIVITY_FAILURE("Activity konnte nicht gestartet werden"),
    ACTIVITY_KILLED("Activity wurde vom System beendet"),
    ONEPLUS_INTERFERENCE("OnePlus Power Management Interferenz"),
    USER_REQUESTED("Benutzer-angefordert"),
    EXTREME_RELIABILITY("Extreme Zuverlässigkeits-Modus"),
    TESTING("Test-Modus")
}

/**
 * 📈 Escalation levels for progressive alarm intensity
 */
enum class EscalationLevel {
    GENTLE,     // Start quietly for false alarms
    STANDARD,   // Normal alarm intensity
    AGGRESSIVE, // Higher intensity for heavy sleepers
    MAXIMUM     // Maximum intensity for extreme cases
}
