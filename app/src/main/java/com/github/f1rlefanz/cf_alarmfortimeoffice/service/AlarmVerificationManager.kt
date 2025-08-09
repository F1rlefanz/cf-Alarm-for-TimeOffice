package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.FallbackActivationReason
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.EscalationLevel
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.VerificationSource
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.VerificationFailureReason
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmOutcome
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.BatteryOptimizationManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmFallbackService
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        private const val VERIFICATION_TIMEOUT_MS = 30_000L // 30 seconds standard
        private const val ONEPLUS_VERIFICATION_TIMEOUT_MS = 60_000L // Extended for OnePlus aggressive power management
        private const val FAILURE_THRESHOLD_FOR_FALLBACK = 2 // Failures before auto-fallback
        private const val STATISTICS_RETENTION_DAYS = 30
    }

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val verificationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active alarm monitoring with thread-safe access
    private val activeAlarmMonitors = ConcurrentHashMap<Int, AlarmMonitor>()
    private val verificationMutex = Mutex() // Thread-safe access to verification operations
    private val timeoutJobs = ConcurrentHashMap<Int, Job>() // Track timeout jobs for cancellation

    /**
     * 🎯 Start monitoring an alarm for verification (Thread-Safe with Race Condition Prevention)
     */
    fun startAlarmVerification(
        alarmId: Int,
        shiftName: String,
        alarmTime: String,
        expectedStartTime: LocalDateTime = LocalDateTime.now()
    ) {
        Logger.business(
            LogTags.ALARM,
            "🔍 Starting alarm verification for ID: $alarmId, Shift: $shiftName"
        )

        verificationScope.launch {
            verificationMutex.withLock {
                // Cancel any existing timeout job for this alarm ID
                timeoutJobs[alarmId]?.cancel()

                val monitor = AlarmMonitor(
                    alarmId = alarmId,
                    shiftName = shiftName,
                    alarmTime = alarmTime,
                    expectedStartTime = expectedStartTime,
                    verificationStartTime = LocalDateTime.now()
                )

                activeAlarmMonitors[alarmId] = monitor
                Logger.d(
                    LogTags.ALARM,
                    "📊 Alarm monitor registered: ID=$alarmId, Active monitors: ${activeAlarmMonitors.size}"
                )

                // Start verification timeout - adaptive for OnePlus devices
                val timeoutJob = verificationScope.launch {
                    val timeoutMs = if (BatteryOptimizationManager.isOnePlusDevice()) {
                        Logger.d(
                            LogTags.ALARM,
                            "🔴 OnePlus device detected - using extended verification timeout"
                        )
                        ONEPLUS_VERIFICATION_TIMEOUT_MS
                    } else {
                        VERIFICATION_TIMEOUT_MS
                    }

                    Logger.d(
                        LogTags.ALARM,
                        "⏰ Starting verification timeout: ${timeoutMs}ms for alarm $alarmId"
                    )
                    delay(timeoutMs)

                    // Thread-safe timeout handling
                    verificationMutex.withLock {
                        activeAlarmMonitors[alarmId]?.let { currentMonitor ->
                            if (!currentMonitor.isVerified) {
                                val failureReason =
                                    if (BatteryOptimizationManager.isOnePlusDevice()) {
                                        VerificationFailureReason.ONEPLUS_INTERFERENCE
                                    } else {
                                        VerificationFailureReason.TIMEOUT
                                    }
                                Logger.w(
                                    LogTags.ALARM,
                                    "⏰ Verification timeout reached for alarm $alarmId after ${timeoutMs}ms"
                                )
                                handleAlarmVerificationFailure(currentMonitor, failureReason)
                            } else {
                                Logger.d(
                                    LogTags.ALARM,
                                    "✅ Timeout check passed - alarm $alarmId already verified"
                                )
                            }
                        } ?: run {
                            Logger.d(
                                LogTags.ALARM,
                                "⏰ Timeout check: alarm $alarmId already processed/removed"
                            )
                        }

                        // Clean up timeout job reference
                        timeoutJobs.remove(alarmId)
                    }
                }

                // Store timeout job reference for potential cancellation
                timeoutJobs[alarmId] = timeoutJob
            }
        }
    }

    /**
     * ✅ Mark an alarm as successfully verified (Thread-Safe with Enhanced Race Condition Handling)
     */
    fun verifyAlarmSuccess(alarmId: Int, verificationSource: VerificationSource) {
        Logger.d(
            LogTags.ALARM,
            "🔍 Attempting verification for alarm $alarmId at ${System.currentTimeMillis()}"
        )

        verificationScope.launch {
            verificationMutex.withLock {
                val monitor = activeAlarmMonitors[alarmId]

                if (monitor != null) {
                    // Cancel timeout job since verification succeeded
                    timeoutJobs[alarmId]?.cancel()
                    timeoutJobs.remove(alarmId)

                    monitor.isVerified = true
                    monitor.verificationTime = LocalDateTime.now()
                    monitor.verificationSource = verificationSource

                    val responseTime = java.time.Duration.between(
                        monitor.verificationStartTime,
                        monitor.verificationTime
                    ).toMillis()

                    Logger.business(
                        LogTags.ALARM,
                        "✅ ALARM VERIFIED: ID: $alarmId, Source: $verificationSource, Response time: ${responseTime}ms"
                    )

                    // Record successful alarm
                    recordAlarmStatistics(monitor, AlarmOutcome.SUCCESS, responseTime)

                    // Clean up monitor
                    activeAlarmMonitors.remove(alarmId)

                } else {
                    Logger.w(
                        LogTags.ALARM,
                        "⚠️ Monitor not found for alarm ID: $alarmId - attempting enhanced recovery"
                    )

                    // Enhanced race condition recovery with progressive backoff
                    var retryCount = 0
                    val maxRetries = 3
                    val retryDelays = arrayOf(50L, 200L, 500L)

                    while (retryCount < maxRetries) {
                        delay(retryDelays[retryCount])
                        retryCount++

                        activeAlarmMonitors[alarmId]?.let { retryMonitor ->
                            // Cancel timeout job since verification succeeded
                            timeoutJobs[alarmId]?.cancel()
                            timeoutJobs.remove(alarmId)

                            retryMonitor.isVerified = true
                            retryMonitor.verificationTime = LocalDateTime.now()
                            retryMonitor.verificationSource = verificationSource

                            val responseTime = java.time.Duration.between(
                                retryMonitor.verificationStartTime,
                                retryMonitor.verificationTime
                            ).toMillis()

                            Logger.business(
                                LogTags.ALARM,
                                "✅ ALARM VERIFIED (retry $retryCount): ID: $alarmId, Response time: ${responseTime}ms"
                            )

                            recordAlarmStatistics(retryMonitor, AlarmOutcome.SUCCESS, responseTime)
                            activeAlarmMonitors.remove(alarmId)
                            return@launch
                        }
                    }

                    // Record late verification attempt for analytics
                    recordFallbackVerificationAttempt(alarmId, verificationSource)
                }
            }
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
                Logger.w(
                    LogTags.ALARM,
                    "⚠️ Multiple recent failures ($recentFailures) - activating fallback"
                )
                true
            }

            // Activate for timeout failures (most critical)
            failureReason == VerificationFailureReason.TIMEOUT -> {
                Logger.w(LogTags.ALARM, "⏰ Timeout failure - activating fallback")
                true
            }

            // Activate for devices with poor reliability profile
            deviceProfile.successRate < 0.8 -> {
                Logger.w(
                    LogTags.ALARM,
                    "📊 Poor device reliability (${deviceProfile.successRate}) - activating fallback"
                )
                true
            }

            // Don't activate for isolated failures on reliable devices
            else -> {
                Logger.d(
                    LogTags.ALARM,
                    "✅ Isolated failure on reliable device - no fallback needed"
                )
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
            "🆘 ACTIVATING AUTOMATIC FALLBACK: Alarm: ${monitor.alarmId}, Reason: $activationReason, Level: $escalationLevel"
        )

        try {
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
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error activating fallback alarm", e)
        }
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
        try {
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

            // Store in preferences
            val key = "alarm_stat_${System.currentTimeMillis()}"
            preferences.edit()
                .putString(key, statisticsEntry.toJsonString())
                .apply()

            // Clean up old statistics periodically
            if (Math.random() < 0.1) { // 10% chance to cleanup on each record
                cleanupOldStatistics()
            }

            Logger.d(LogTags.ALARM, "📊 Alarm statistics recorded: $outcome in ${responseTimeMs}ms")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error recording alarm statistics", e)
        }
    }

    /**
     * 🆘 Record fallback activation for analysis
     */
    private fun recordFallbackActivation(
        monitor: AlarmMonitor,
        activationReason: FallbackActivationReason,
        escalationLevel: EscalationLevel
    ) {
        try {
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

            Logger.business(
                LogTags.ALARM,
                "🆘 Fallback activation recorded: $activationReason at $escalationLevel"
            )
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error recording fallback activation", e)
        }
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
                        LocalDateTime.parse(it.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            .isAfter(cutoffTime)
            }
            .size
    }

    /**
     * 📊 Get all stored alarm statistics
     */
    private fun getAllStoredStatistics(): List<AlarmStatisticsEntry> {
        return try {
            preferences.all
                .filterKeys { it.startsWith("alarm_stat_") }
                .mapNotNull { (_, value) ->
                    try {
                        AlarmStatisticsEntry.fromJsonString(value as String)
                    } catch (e: Exception) {
                        Logger.w(LogTags.ALARM, "Failed to parse alarm statistics entry: ${e.message}")
                        null
                    }
                }
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error getting stored statistics", e)
            emptyList()
        }
    }

    /**
     * 🧹 Clean up old statistics to prevent unbounded growth
     */
    private fun cleanupOldStatistics() {
        try {
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
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error during statistics cleanup", e)
        }
    }

    /**
     * 🔍 Get verification status for Alarm Failure Detection
     */
    fun getVerificationStatus(alarmId: Int): String {
        return try {
            activeAlarmMonitors[alarmId]?.let { monitor ->
                when {
                    monitor.isVerified -> "SUCCESS"
                    else -> "PENDING"
                }
            } ?: run {
                // Check if this was a recent failure by looking at statistics
                val recentStats = getAllStoredStatistics()
                    .filter { it.alarmId == alarmId }
                    .maxByOrNull { LocalDateTime.parse(it.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
                
                when (recentStats?.outcome) {
                    AlarmOutcome.SUCCESS -> "SUCCESS"
                    AlarmOutcome.FAILURE -> "FAILED"
                    else -> "UNKNOWN"
                }
            }
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Error getting verification status", e)
            "UNKNOWN"
        }
    }

    /**
     * 📝 Record late verification attempt for analytics
     */
    private fun recordFallbackVerificationAttempt(
        alarmId: Int,
        verificationSource: VerificationSource
    ) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val key = "late_verification_${System.currentTimeMillis()}"

            val lateVerificationData =
                "$timestamp|$alarmId|${verificationSource.name}|${Build.MANUFACTURER}|${Build.MODEL}"

            preferences.edit()
                .putString(key, lateVerificationData)
                .apply()

            Logger.w(LogTags.ALARM, "📝 Late verification attempt recorded for alarm $alarmId")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Failed to record late verification attempt", e)
        }
    }

    /**
     * 🛑 Stop monitoring a specific alarm
     */
    fun stopAlarmMonitoring(alarmId: Int) {
        verificationScope.launch {
            verificationMutex.withLock {
                // Cancel any running timeout job
                timeoutJobs[alarmId]?.cancel()
                timeoutJobs.remove(alarmId)

                // Remove monitor
                activeAlarmMonitors.remove(alarmId)
                Logger.d(LogTags.ALARM, "🛑 Stopped monitoring alarm ID: $alarmId")
            }
        }
    }

    /**
     * 🧹 Cleanup when service is destroyed
     */
    fun cleanup() {
        verificationScope.launch {
            verificationMutex.withLock {
                // Cancel all running timeout jobs
                timeoutJobs.values.forEach { job -> job.cancel() }
                timeoutJobs.clear()

                // Clear monitors
                activeAlarmMonitors.clear()

                Logger.d(LogTags.ALARM, "🧹 AlarmVerificationManager cleanup completed")
            }
        }

        // Cancel the verification scope
        verificationScope.cancel()
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



data class AlarmStatisticsEntry(
    val timestamp: String, 
    val alarmId: Int, 
    val shiftName: String, 
    val outcome: AlarmOutcome,
    val responseTimeMs: Long, 
    val manufacturer: String, 
    val model: String, 
    val androidVersion: Int,
    val expectedStartTime: String, 
    val verificationSource: String?
) {
    fun toJsonString(): String =
        "$timestamp|$alarmId|$shiftName|${outcome.name}|$responseTimeMs|$manufacturer|$model|$androidVersion|$expectedStartTime|$verificationSource"

    companion object {
        fun fromJsonString(json: String): AlarmStatisticsEntry {
            val parts = json.split("|")
            return AlarmStatisticsEntry(
                parts[0],
                parts[1].toInt(),
                parts[2],
                AlarmOutcome.valueOf(parts[3]),
                parts[4].toLong(),
                parts[5],
                parts[6],
                parts[7].toInt(),
                parts[8],
                parts.getOrNull(9)
            )
        }
    }
}

data class FallbackActivationEntry(
    val timestamp: String, 
    val alarmId: Int, 
    val shiftName: String, 
    val activationReason: String,
    val escalationLevel: String, 
    val manufacturer: String, 
    val model: String
) {
    fun toJsonString(): String =
        "$timestamp|$alarmId|$shiftName|$activationReason|$escalationLevel|$manufacturer|$model"

    companion object {
        fun fromJsonString(json: String): FallbackActivationEntry {
            val parts = json.split("|")
            return FallbackActivationEntry(
                parts[0],
                parts[1].toInt(),
                parts[2],
                parts[3],
                parts[4],
                parts[5],
                parts[6]
            )
        }
    }
}

data class DeviceReliabilityProfile(
    val successRate: Double,
    val averageResponseTimeMs: Long,
    val totalAlarms: Int,
    val manufacturer: String,
    val model: String
)

data class AlarmReliabilityReport(
    val deviceProfile: DeviceReliabilityProfile,
    val totalAlarms: Int,
    val successfulAlarms: Int,
    val failedAlarms: Int,
    val recentFailures: Int,
    val totalFallbackActivations: Int,
    val averageResponseTimeMs: Long,
    val isOnePlusDevice: Boolean,
    val activeMonitors: Int,
    val recommendedFallbackThreshold: Int
)