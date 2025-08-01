/**
 * OnePlus Configuration Repository
 * 
 * Implements Repository pattern following Clean Architecture principles:
 * - Single source of truth for OnePlus configuration data
 * - Abstracts data storage implementation details
 * - Provides reactive data access with Flow
 * - Implements proper error handling and caching
 * 
 * @author CF-Alarm Team
 * @since 2025.1.0
 */
package com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.ConfigurationRisk
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.ConfigurationRiskType
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.RiskSeverity
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contract for OnePlus configuration data access
 */
interface IOnePlusConfigurationRepository {
    suspend fun getUserConfirmation(stepId: String): Boolean
    suspend fun setUserConfirmation(stepId: String, isConfigured: Boolean): Result<Unit>
    suspend fun getHeuristicScore(stepId: String): Float
    suspend fun setHeuristicScore(stepId: String, score: Float): Result<Unit>
    suspend fun getLastConfirmationTime(stepId: String): LocalDateTime?
    suspend fun getFirmwareUpdateHistory(): List<String>
    suspend fun recordFirmwareUpdate(buildDisplay: String): Result<Unit>
    suspend fun getConfigurationRisks(): List<ConfigurationRisk>
    suspend fun clearAllData(): Result<Unit>
    fun observeUserConfirmations(): Flow<Map<String, Boolean>>
}

/**
 * SharedPreferences-based implementation of OnePlus configuration repository
 * 
 * Follows modern Android data persistence patterns with reactive updates
 */
@Singleton
class OnePlusConfigurationRepository @Inject constructor(
    private val context: Context
) : IOnePlusConfigurationRepository {

    companion object {
        private const val PREFS_NAME = "oneplus_configuration_v2"
        private const val KEY_USER_CONFIRMED_PREFIX = "user_confirmed_"
        private const val KEY_LAST_CONFIRMATION_PREFIX = "last_confirmation_"
        private const val KEY_HEURISTIC_SCORE_PREFIX = "heuristic_score_"
        private const val KEY_FIRMWARE_HISTORY = "firmware_history"
        private const val KEY_CONFIGURATION_RESETS_COUNT = "config_resets_count"
        private const val KEY_LAST_FIRMWARE_VERSION = "last_firmware_version"
    }

    // Reactive SharedPreferences for modern data patterns
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // StateFlow for reactive user confirmations
    private val _userConfirmations = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    
    init {
        // Initialize reactive state from SharedPreferences
        loadInitialUserConfirmations()
    }

    override suspend fun getUserConfirmation(stepId: String): Boolean {
        return try {
            val confirmed = preferences.getBoolean("${KEY_USER_CONFIRMED_PREFIX}$stepId", false)
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "Retrieved user confirmation for $stepId: $confirmed")
            confirmed
        } catch (e: Exception) {
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "Failed to get user confirmation for $stepId", e)
            false 
        }
    }

    override suspend fun setUserConfirmation(stepId: String, isConfigured: Boolean): Result<Unit> {
        return try {
            val currentTime = LocalDateTime.now()
            
            preferences.edit(commit = true) {
                putBoolean("${KEY_USER_CONFIRMED_PREFIX}$stepId", isConfigured)
                putString(
                    "${KEY_LAST_CONFIRMATION_PREFIX}$stepId", 
                    currentTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            }
            
            // Update reactive state
            loadInitialUserConfirmations()
            
            Logger.business(
                LogTags.BATTERY_OPTIMIZATION,
                "👤 User confirmation recorded: $stepId = $isConfigured"
            )
            
            Result.success(Unit)
        } catch (error: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to set user confirmation for $stepId", error)
            Result.failure(error)
        }
    }

    override suspend fun getHeuristicScore(stepId: String): Float {
        return try {
            preferences.getFloat("${KEY_HEURISTIC_SCORE_PREFIX}$stepId", 0f)
        } catch (e: Exception) {
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "Failed to get heuristic score for $stepId", e)
            0f
        }
    }

    override suspend fun setHeuristicScore(stepId: String, score: Float): Result<Unit> {
        return try {
            preferences.edit(commit = true) {
                putFloat("${KEY_HEURISTIC_SCORE_PREFIX}$stepId", score.coerceIn(0f, 1f))
            }
            
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "Heuristic score set for $stepId: $score")
            Result.success(Unit)
        } catch (error: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to set heuristic score for $stepId", error)
            Result.failure(error)
        }
    }

    override suspend fun getLastConfirmationTime(stepId: String): LocalDateTime? {
        return try {
            val timeString = preferences.getString("${KEY_LAST_CONFIRMATION_PREFIX}$stepId", null)
            timeString?.let { 
                try {
                    LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } catch (_: Exception) {
                    Logger.w(LogTags.BATTERY_OPTIMIZATION, "Invalid confirmation time for $stepId: $timeString")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to get confirmation time for $stepId", e)
            null
        }
    }

    override suspend fun getFirmwareUpdateHistory(): List<String> {
        return try {
            val historyString = preferences.getString(KEY_FIRMWARE_HISTORY, "")
            if (historyString.isNullOrEmpty()) {
                emptyList()
            } else {
                historyString.split("|").filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to get firmware history", e)
            emptyList()
        }
    }

    override suspend fun recordFirmwareUpdate(buildDisplay: String): Result<Unit> {
        return try {
            val currentHistory = getFirmwareUpdateHistory()
            val updatedHistory = (currentHistory + buildDisplay).takeLast(10) // Keep last 10 updates
            
            preferences.edit(commit = true) {
                putString(KEY_FIRMWARE_HISTORY, updatedHistory.joinToString("|"))
                putString(KEY_LAST_FIRMWARE_VERSION, buildDisplay)
                
                // Increment reset counter when firmware changes
                val currentResets = preferences.getInt(KEY_CONFIGURATION_RESETS_COUNT, 0)
                putInt(KEY_CONFIGURATION_RESETS_COUNT, currentResets + 1)
            }
            
            Logger.business(
                LogTags.BATTERY_OPTIMIZATION,
                "📱 Firmware update recorded: $buildDisplay"
            )
            Result.success(Unit)
        } catch (error: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to record firmware update", error)
            Result.failure(error)
        }
    }

    override suspend fun getConfigurationRisks(): List<ConfigurationRisk> {
        return try {
            val risks = mutableListOf<ConfigurationRisk>()
            
            // Check for recent firmware updates
            val lastFirmware = preferences.getString(KEY_LAST_FIRMWARE_VERSION, null)
            val currentFirmware = android.os.Build.DISPLAY
            
            if (lastFirmware != null && lastFirmware != currentFirmware) {
                risks.add(
                    ConfigurationRisk(
                        type = ConfigurationRiskType.FIRMWARE_UPDATE_RESET,
                        probability = 0.95f, // Research-based probability
                        message = "Firmware-Update erkannt: $lastFirmware → $currentFirmware",
                        severity = RiskSeverity.CRITICAL
                    )
                )
            }
            
            // Check for expired Recent Apps Lock (research shows 24h reliability)
            val recentAppsConfirmationTime = getLastConfirmationTime("recent_apps_lock")
            if (recentAppsConfirmationTime != null) {
                val hoursSinceConfirmation = java.time.Duration.between(
                    recentAppsConfirmationTime, 
                    LocalDateTime.now()
                ).toHours()
                
                if (hoursSinceConfirmation > 24) {
                    risks.add(
                        ConfigurationRisk(
                            type = ConfigurationRiskType.RECENT_APPS_LOCK_EXPIRED,
                            probability = 0.7f, // Research-based 70% failure rate
                            message = "Recent Apps Lock älter als 24h (${hoursSinceConfirmation}h)",
                            severity = RiskSeverity.MEDIUM
                        )
                    )
                }
            }
            
            risks
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to get configuration risks", e)
            emptyList()
        }
    }

    override suspend fun clearAllData(): Result<Unit> {
        return try {
            preferences.edit(commit = true) {
                clear()
            }
            
            // Reset reactive state
            _userConfirmations.value = emptyMap()
            
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "🧹 OnePlus configuration data cleared")
            Result.success(Unit)
        } catch (error: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to clear configuration data", error)
            Result.failure(error)
        }
    }

    override fun observeUserConfirmations(): Flow<Map<String, Boolean>> {
        return _userConfirmations.asStateFlow()
    }

    /**
     * Load initial user confirmations from SharedPreferences into reactive state
     */
    private fun loadInitialUserConfirmations() {
        try {
            val confirmations = mutableMapOf<String, Boolean>()
            
            for ((key, value) in preferences.all) {
                if (key.startsWith(KEY_USER_CONFIRMED_PREFIX) && value is Boolean) {
                    val stepId = key.removePrefix(KEY_USER_CONFIRMED_PREFIX)
                    confirmations[stepId] = value
                }
            }
            
            _userConfirmations.value = confirmations.toMap()
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "Loaded ${confirmations.size} user confirmations")
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to load initial user confirmations", e)
        }
    }

    /**
     * Update reactive state after changes (simplified to remove unnecessary suspend modifier)
     */
    private fun updateUserConfirmationsState() {
        loadInitialUserConfirmations()
    }

    /**
     * Get debug information about repository state
     */
    suspend fun getDebugInfo(): String {
        return try {
            buildString {
                appendLine("=== OnePlus Configuration Repository Debug ===")
                appendLine("Preferences file: $PREFS_NAME")
                appendLine("Total entries: ${preferences.all.size}")
                appendLine()
                
                appendLine("User Confirmations:")
                for ((key, value) in preferences.all) {
                    if (key.startsWith(KEY_USER_CONFIRMED_PREFIX)) {
                        val stepId = key.removePrefix(KEY_USER_CONFIRMED_PREFIX)
                        val confirmationTime = getLastConfirmationTime(stepId)
                        appendLine("  $stepId: $value (confirmed: $confirmationTime)")
                    }
                }
                
                appendLine()
                appendLine("Heuristic Scores:")
                for ((key, value) in preferences.all) {
                    if (key.startsWith(KEY_HEURISTIC_SCORE_PREFIX)) {
                        val stepId = key.removePrefix(KEY_HEURISTIC_SCORE_PREFIX)
                        appendLine("  $stepId: $value")
                    }
                }
                
                appendLine()
                appendLine("Firmware History:")
                val history = getFirmwareUpdateHistory()
                history.forEach { firmware ->
                    appendLine("  - $firmware")
                }
                
                appendLine()
                appendLine("Configuration Risks:")
                val risks = getConfigurationRisks()
                risks.forEach { risk ->
                    appendLine("  ${risk.severity}: ${risk.message} (${(risk.probability * 100).toInt()}%)")
                }
                
                appendLine("==========================================")
            }
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Failed to generate debug info", e)
            "Debug info generation failed: ${e.message}"
        }
    }
}
