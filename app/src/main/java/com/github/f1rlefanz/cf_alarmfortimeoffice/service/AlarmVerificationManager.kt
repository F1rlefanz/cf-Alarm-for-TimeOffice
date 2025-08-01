package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 🚀 PHASE 3: Alarm Verification & Auto-Fallback Manager
 * 
 * Monitors alarm execution and automatically activates fallback strategies
 * when primary alarm methods fail or are unreliable.
 */
class AlarmVerificationManager(
    private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "alarm_verification"
        private const val VERIFICATION_TIMEOUT_MS = 30_000L // 30 seconds
        private const val FAILURE_THRESHOLD_FOR_FALLBACK = 2 // Failures before auto-fallback
        private const val STATISTICS_RETENTION_DAYS = 30
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val verificationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Active alarm monitoring
    private val activeAlarmMonitors = ConcurrentHashMap<Int, AlarmMonitor>()
    
    /**
     * 🎯 Start monitoring an alarm for verification
     */
    fun startAlarmVerification(
        alarmId: Int,
        shiftName: String,
        alarmTime: String,
        expectedStartTime: LocalDateTime = LocalDateTime.now()
    ) {
        Logger.business(LogTags.ALARM, "🔍 Starting alarm verification for ID: $alarmId")
        
        val monitor = AlarmMonitor(
            alarmId = alarmId,
            shiftName = shiftName,
            alarmTime = alarmTime,
            expectedStartTime = expectedStartTime,
            verificationStartTime = LocalDateTime.now()
        )
        
        activeAlarmMonitors[alarmId] = monitor
        
        // Start verification timeout
        verificationScope.launch {
            delay(VERIFICATION_TIMEOUT_MS)
            
            // Check if alarm was verified within timeout
            activeAlarmMonitors[alarmId]?.let { currentMonitor ->
                if (!currentMonitor.isVerified) {
                    handleAlarmVerificationFailure(currentMonitor, VerificationFailureReason.TIMEOUT)
                }
            }
        }
    }
    
    /**
     * ✅ Mark an alarm as successfully verified (called from AlarmFullScreenActivity)
     */
    fun verifyAlarmSuccess(alarmId: Int, verificationSource: VerificationSource) {
        val monitor = activeAlarmMonitors[alarmId]
        
        if (monitor != null) {
            monitor.isVerified = true
            monitor.verificationTime = LocalDateTime.now()
            monitor.verificationSource = verificationSource
            
            val responseTime = java.time.Duration.between(monitor.verificationStartTime, monitor.verificationTime).toMillis()
            
            Logger.business(
                LogTags.ALARM,
                "✅ ALARM VERIFIED",
                "ID: $alarmId, Source: $verificationSource, Response time: ${responseTime}ms"
            )
            
            // Record successful alarm
            recordAlarmStatistics(monitor, AlarmOutcome.SUCCESS, responseTime)
            
            // Clean up monitor
            activeAlarmMonitors.remove(alarmId)
            
        } else {
            Logger.w(LogTags.ALARM, "⚠️ Verification attempted for unknown alarm ID: $alarmId")
        }
    }
    
    /**
     * ❌ Handle alarm verification failure
     */
    private fun handleAlarmVerificationFailure(
        monitor: AlarmMonitor,
        failureReason: VerificationFailureReason
    ) {
        Logger.e(
            LogTags.ALARM,
            "❌ ALARM VERIFICATION FAILED: ID: ${monitor.alarmId}, Reason: $failureReason, Shift: ${monitor.shiftName}"
        )
        
        // Record failed alarm
        recordAlarmStatistics(monitor, AlarmOutcome.FAILURE, -1)
        
        // Determine if fallback should be activated
        val shouldActivateFallback = shouldActivateAutomaticFallback(monitor, failureReason)
        
        if (shouldActivateFallback) {
            activateAutomaticFallback(monitor, failureReason)
        }
        
        // Clean up monitor
        activeAlarmMonitors.remove(monitor.alarmId)
    }
    
    /**
     * 🤖 Intelligent decision making for automatic fallback activation
     */
    private fun shouldActivateAutomaticFallback(
        monitor: AlarmMonitor,
        failureReason: VerificationFailureReason
    ): Boolean {
        val deviceProfile = getDeviceReliabilityProfile()
        val recentFailures = getRecentFailureCount()
        
        return when {
            // Always activate fallback for OnePlus devices
            BatteryOptimizationManager.isOnePlusDevice() -> {
                Logger.w(LogTags.ALARM, "🔴 OnePlus device - automatic fallback activated")
                true
            }
            
            // Activate if multiple recent failures
            recentFailures >= FAILURE_THRESHOLD_FOR_FALLBACK -> {
                Logger.w(LogTags.ALARM, "⚠️ Multiple recent failures ($recentFailures) - activating fallback")
                true
            }
            
            // Activate for timeout failures (most critical)
            failureReason == VerificationFailureReason.TIMEOUT -> {
                Logger.w(LogTags.ALARM, "⏰ Timeout failure - activating fallback")
                true
            }
            
            // Activate for devices with poor reliability profile
            deviceProfile.successRate < 0.8 -> {
                Logger.w(LogTags.ALARM, "📊 Poor device reliability (${deviceProfile.successRate}) - activating fallback")
                true
            }
            
            // Don't activate for isolated failures on reliable devices
            else -> {
                Logger.d(LogTags.ALARM, "✅ Isolated failure on reliable device - no fallback needed")
                false
            }
        }
    }
    
    /**
     * 🆘 Activate automatic fallback alarm
     */
    private fun activateAutomaticFallback(
        monitor: AlarmMonitor,
        failureReason: VerificationFailureReason
    ) {
        val activationReason = when (failureReason) {
            VerificationFailureReason.TIMEOUT -> FallbackActivationReason.ACTIVITY_FAILURE
            VerificationFailureReason.ACTIVITY_KILLED -> FallbackActivationReason.ACTIVITY_KILLED
            VerificationFailureReason.ONEPLUS_INTERFERENCE -> FallbackActivationReason.ONEPLUS_INTERFERENCE
            VerificationFailureReason.SYSTEM_INTERFERENCE -> FallbackActivationReason.ACTIVITY_KILLED
        }
        
        val escalationLevel = determineEscalationLevel(monitor, failureReason)
        
        Logger.business(
            LogTags.ALARM,
            "🆘 ACTIVATING AUTOMATIC FALLBACK",
            "Alarm: ${monitor.alarmId}, Reason: $activationReason, Level: $escalationLevel"
        )
        
        AlarmFallbackService.startFallbackAlarm(
            context = context,
            shiftName = monitor.shiftName,
            alarmTime = monitor.alarmTime,
            alarmId = monitor.alarmId,
            activationReason = activationReason,
            escalationLevel = escalationLevel
        )
        
        // Record fallback activation
        recordFallbackActivation(monitor, activationReason, escalationLevel)
    }
    
    /**
     * 📈 Determine appropriate escalation level based on context
     */
    private fun determineEscalationLevel(
        monitor: AlarmMonitor,
        failureReason: VerificationFailureReason
    ): EscalationLevel {
        val deviceProfile = getDeviceReliabilityProfile()
        val timeOfDay = monitor.expectedStartTime.hour
        
        return when {
            // OnePlus devices get aggressive escalation
            BatteryOptimizationManager.isOnePlusDevice() -> EscalationLevel.AGGRESSIVE
            
            // Poor reliability devices get aggressive escalation
            deviceProfile.successRate < 0.6 -> EscalationLevel.AGGRESSIVE
            
            // Early morning alarms (work shifts) get standard escalation
            timeOfDay in 4..8 -> EscalationLevel.STANDARD
            
            // Late night alarms might be false alarms - start gentle
            timeOfDay in 22..23 || timeOfDay in 0..3 -> EscalationLevel.GENTLE
            
            // Default standard escalation
            else -> EscalationLevel.STANDARD
        }
    }
    
    /**
     * 📊 Record alarm statistics for analysis
     */
    private fun recordAlarmStatistics(
        monitor: AlarmMonitor,
        outcome: AlarmOutcome,
        responseTimeMs: Long
    ) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = Build.VERSION.SDK_INT
        
        val statisticsEntry = AlarmStatisticsEntry(
            timestamp = timestamp,
            alarmId = monitor.alarmId,
            shiftName = monitor.shiftName,
            outcome = outcome,
            responseTimeMs = responseTimeMs,
            manufacturer = manufacturer,
            model = model,
            androidVersion = androidVersion,
            expectedStartTime = monitor.expectedStartTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            verificationSource = monitor.verificationSource?.name
        )
        
        // Store in preferences (simplified storage)
        val key = "alarm_stat_${System.currentTimeMillis()}"
        preferences.edit()
            .putString(key, statisticsEntry.toJsonString())
            .apply()
        
        // Clean up old statistics
        cleanupOldStatistics()
        
        Logger.d(LogTags.ALARM, "📊 Alarm statistics recorded: $outcome in ${responseTimeMs}ms")
    }
    
    /**
     * 🆘 Record fallback activation for analysis
     */
    private fun recordFallbackActivation(
        monitor: AlarmMonitor,
        activationReason: FallbackActivationReason,
        escalationLevel: EscalationLevel
    ) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        
        val fallbackEntry = FallbackActivationEntry(
            timestamp = timestamp,
            alarmId = monitor.alarmId,
            shiftName = monitor.shiftName,
            activationReason = activationReason.name,
            escalationLevel = escalationLevel.name,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL
        )
        
        val key = "fallback_${System.currentTimeMillis()}"
        preferences.edit()
            .putString(key, fallbackEntry.toJsonString())
            .apply()
        
        Logger.business(LogTags.ALARM, "🆘 Fallback activation recorded: $activationReason at $escalationLevel")
    }
    
    /**
     * 📊 Get device reliability profile based on historical data
     */
    private fun getDeviceReliabilityProfile(): DeviceReliabilityProfile {
        val allStats = getAllStoredStatistics()
        
        if (allStats.isEmpty()) {
            return DeviceReliabilityProfile(
                successRate = 1.0, // Optimistic default for new devices
                averageResponseTimeMs = 2000,
                totalAlarms = 0,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL
            )
        }
        
        val successfulAlarms = allStats.count { it.outcome == AlarmOutcome.SUCCESS }
        val successRate = successfulAlarms.toDouble() / allStats.size
        val averageResponseTime = allStats
            .filter { it.outcome == AlarmOutcome.SUCCESS && it.responseTimeMs > 0 }
            .map { it.responseTimeMs }
            .average()
            .takeIf { !it.isNaN() } ?: 2000.0
        
        return DeviceReliabilityProfile(
            successRate = successRate,
            averageResponseTimeMs = averageResponseTime.toLong(),
            totalAlarms = allStats.size,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL
        )
    }
    
    /**
     * 📈 Get count of recent failures (last 7 days)
     */
    private fun getRecentFailureCount(): Int {
        val cutoffTime = LocalDateTime.now().minusDays(7)
        
        return getAllStoredStatistics()
            .filter { 
                it.outcome == AlarmOutcome.FAILURE && 
                LocalDateTime.parse(it.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).isAfter(cutoffTime)
            }
            .size
    }
    
    /**
     * 📊 Get all stored alarm statistics
     */
    private fun getAllStoredStatistics(): List<AlarmStatisticsEntry> {
        return preferences.all
            .filterKeys { it.startsWith("alarm_stat_") }
            .mapNotNull { (_, value) ->
                try {
                    AlarmStatisticsEntry.fromJsonString(value as String)
                } catch (e: Exception) {
                    Logger.w(LogTags.ALARM, "Failed to parse alarm statistics entry: ${e.message}", e)
                    null
                }
            }
    }
    
    /**
     * 📊 Get comprehensive alarm reliability report
     */
    fun getReliabilityReport(): AlarmReliabilityReport {
        val allStats = getAllStoredStatistics()
        val deviceProfile = getDeviceReliabilityProfile()
        val recentFailures = getRecentFailureCount()
        
        val fallbackActivations = preferences.all
            .filterKeys { it.startsWith("fallback_") }
            .mapNotNull { (_, value) ->
                try {
                    FallbackActivationEntry.fromJsonString(value as String)
                } catch (e: Exception) {
                    null
                }
            }
        
        return AlarmReliabilityReport(
            deviceProfile = deviceProfile,
            totalAlarms = allStats.size,
            successfulAlarms = allStats.count { it.outcome == AlarmOutcome.SUCCESS },
            failedAlarms = allStats.count { it.outcome == AlarmOutcome.FAILURE },
            recentFailures = recentFailures,
            totalFallbackActivations = fallbackActivations.size,
            averageResponseTimeMs = deviceProfile.averageResponseTimeMs,
            isOnePlusDevice = BatteryOptimizationManager.isOnePlusDevice(),
            activeMonitors = activeAlarmMonitors.size,
            recommendedFallbackThreshold = calculateRecommendedFallbackThreshold(deviceProfile)
        )
    }
    
    private fun calculateRecommendedFallbackThreshold(profile: DeviceReliabilityProfile): Int {
        return when {
            BatteryOptimizationManager.isOnePlusDevice() -> 1 // Immediate fallback for OnePlus
            profile.successRate < 0.7 -> 1 // Immediate fallback for unreliable devices
            profile.successRate < 0.9 -> 2 // Standard threshold
            else -> 3 // Higher threshold for reliable devices
        }
    }
    
    /**
     * 🧹 Clean up old statistics to prevent unbounded growth
     */
    private fun cleanupOldStatistics() {
        val cutoffTime = LocalDateTime.now().minusDays(STATISTICS_RETENTION_DAYS.toLong())
        val keysToRemove = mutableListOf<String>()
        
        preferences.all.forEach { (key, value) ->
            if (key.startsWith("alarm_stat_") || key.startsWith("fallback_")) {
                try {
                    val timestamp = if (key.startsWith("alarm_stat_")) {
                        AlarmStatisticsEntry.fromJsonString(value as String).timestamp
                    } else {
                        FallbackActivationEntry.fromJsonString(value as String).timestamp
                    }
                    
                    val entryTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    if (entryTime.isBefore(cutoffTime)) {
                        keysToRemove.add(key)
                    }
                } catch (e: Exception) {
                    // Remove malformed entries
                    keysToRemove.add(key)
                }
            }
        }
        
        if (keysToRemove.isNotEmpty()) {
            val editor = preferences.edit()
            keysToRemove.forEach { key -> editor.remove(key) }
            editor.apply()
            
            Logger.d(LogTags.ALARM, "🧹 Cleaned up ${keysToRemove.size} old statistics entries")
        }
    }
    
    /**
     * 🔄 Reset all verification data (for testing/troubleshooting)
     */
    fun resetVerificationData() {
        preferences.edit().clear().apply()
        activeAlarmMonitors.clear()
        Logger.w(LogTags.ALARM, "🔄 All alarm verification data reset")
    }
    
    /**
     * 🛑 Stop monitoring a specific alarm (called when user dismisses alarm)
     */
    fun stopAlarmMonitoring(alarmId: Int) {
        activeAlarmMonitors.remove(alarmId)
        Logger.d(LogTags.ALARM, "🛑 Stopped monitoring alarm ID: $alarmId")
    }
    
    /**
     * 🧹 Cleanup when service is destroyed
     */
    fun cleanup() {
        verificationScope.cancel()
        activeAlarmMonitors.clear()
    }
}

// Data classes and enums
data class AlarmMonitor(
    val alarmId: Int,
    val shiftName: String,
    val alarmTime: String,
    val expectedStartTime: LocalDateTime,
    val verificationStartTime: LocalDateTime,
    var isVerified: Boolean = false,
    var verificationTime: LocalDateTime? = null,
    var verificationSource: VerificationSource? = null
)

enum class VerificationSource { FULL_SCREEN_ACTIVITY, FALLBACK_SERVICE, NOTIFICATION_ACTION, USER_INTERACTION }
enum class VerificationFailureReason { TIMEOUT, ACTIVITY_KILLED, ONEPLUS_INTERFERENCE, SYSTEM_INTERFERENCE }
enum class AlarmOutcome { SUCCESS, FAILURE }

data class AlarmStatisticsEntry(
    val timestamp: String, val alarmId: Int, val shiftName: String, val outcome: AlarmOutcome,
    val responseTimeMs: Long, val manufacturer: String, val model: String, val androidVersion: Int,
    val expectedStartTime: String, val verificationSource: String?
) {
    fun toJsonString(): String = "$timestamp|$alarmId|$shiftName|${outcome.name}|$responseTimeMs|$manufacturer|$model|$androidVersion|$expectedStartTime|$verificationSource"
    companion object {
        fun fromJsonString(json: String): AlarmStatisticsEntry {
            val parts = json.split("|")
            return AlarmStatisticsEntry(parts[0], parts[1].toInt(), parts[2], AlarmOutcome.valueOf(parts[3]),
                parts[4].toLong(), parts[5], parts[6], parts[7].toInt(), parts[8], parts.getOrNull(9))
        }
    }
}

data class FallbackActivationEntry(
    val timestamp: String, val alarmId: Int, val shiftName: String, val activationReason: String,
    val escalationLevel: String, val manufacturer: String, val model: String
) {
    fun toJsonString(): String = "$timestamp|$alarmId|$shiftName|$activationReason|$escalationLevel|$manufacturer|$model"
    companion object {
        fun fromJsonString(json: String): FallbackActivationEntry {
            val parts = json.split("|")
            return FallbackActivationEntry(parts[0], parts[1].toInt(), parts[2], parts[3], parts[4], parts[5], parts[6])
        }
    }
}

data class DeviceReliabilityProfile(val successRate: Double, val averageResponseTimeMs: Long, val totalAlarms: Int, val manufacturer: String, val model: String)

data class AlarmReliabilityReport(
    val deviceProfile: DeviceReliabilityProfile, val totalAlarms: Int, val successfulAlarms: Int, val failedAlarms: Int,
    val recentFailures: Int, val totalFallbackActivations: Int, val averageResponseTimeMs: Long,
    val isOnePlusDevice: Boolean, val activeMonitors: Int, val recommendedFallbackThreshold: Int
)
