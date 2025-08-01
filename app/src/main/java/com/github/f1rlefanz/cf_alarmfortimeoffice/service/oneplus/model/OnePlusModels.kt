/**
 * Modern OnePlus Data Models
 * 
 * Research-enhanced models following modern Android best practices:
 * - Immutable data classes
 * - Clear separation of concerns
 * - Comprehensive type safety
 * 
 * @author CF-Alarm Team
 * @since 2025.1.0
 */
package com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import java.time.LocalDateTime

/**
 * OnePlus device information based on research data
 */
data class OnePlusDeviceInfo(
    val isOnePlusDevice: Boolean,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val oxygenOSVersion: String?,
    val buildDisplay: String,
    val isOxygenOS15OrHigher: Boolean = oxygenOSVersion?.let { 
        it.startsWith("15") || it.toFloatOrNull()?.let { v -> v >= 15f } ?: false 
    } ?: false,
    val detectedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * Research-based device capability assessment
     */
    val hasEnhancedOptimization: Boolean
        get() = isOxygenOS15OrHigher || model.contains(Regex("1[0-9]|[2-9][0-9]")) // OnePlus 10+
    
    val hasAdvancedBatteryManagement: Boolean
        get() = androidVersion.toIntOrNull() ?: 0 >= 12
}

/**
 * Enhanced OnePlus configuration step with research data
 */
data class OnePlusConfigurationStep(
    val id: OnePlusConfigurationStepId,
    val title: String,
    val description: String,
    val settingsPath: String,
    val alternativePaths: List<String> = emptyList(),
    val isCompleted: Boolean,
    val priority: OnePlusConfigPriority,
    @IntRange(from = 0, to = 100)
    val estimatedImpact: Int,
    val detectionMethod: ConfigDetectionMethod,
    val resetsWithUpdates: Boolean = true,
    val oxygenOS15Changes: String? = null,
    val warning: String? = null,
    @FloatRange(from = 0.0, to = 1.0)
    val successRate: Float = 1.0f, // Research-based success rate
    val lastValidated: LocalDateTime = LocalDateTime.now()
)

/**
 * Configuration step identifiers - enum for type safety
 */
enum class OnePlusConfigurationStepId {
    BATTERY_OPTIMIZATION,
    ENHANCED_OPTIMIZATION,        // New from research
    SLEEP_STANDBY_OPTIMIZATION,   // New from research
    AUTO_STARTUP,
    BACKGROUND_RUNNING,
    RECENT_APPS_LOCK
}

/**
 * Configuration step priorities based on research impact
 */
enum class OnePlusConfigPriority {
    CRITICAL,    // Must-have for basic functionality
    HIGH,        // Significant impact on reliability
    MEDIUM,      // Useful but not essential
    LOW          // Nice-to-have
}

/**
 * Detection methods with modern type safety
 */
enum class ConfigDetectionMethod(
    val displayName: String,
    val description: String,
    val reliability: Float
) {
    API_RELIABLE(
        "Auto", 
        "Automatically detected via Android APIs", 
        0.95f
    ),
    HEURISTIC_USER_CONFIRMED(
        "Smart", 
        "Intelligent detection with user confirmation", 
        0.85f
    ),
    USER_CONFIRMED_ONLY(
        "Manual", 
        "User confirmation required", 
        0.80f
    )
}

/**
 * Research-enhanced reliability metrics
 */
data class OnePlusReliabilityMetrics(
    @IntRange(from = 0, to = 100)
    val currentReliability: Int,
    @IntRange(from = 0, to = 100)
    val maxPossibleReliability: Int,
    val completedSteps: Int,
    val totalSteps: Int,
    val reliabilityLevel: OnePlusReliabilityLevel,
    val lastCalculated: LocalDateTime,
    val researchMetrics: ResearchMetrics
)

/**
 * Research-based metrics for tracking real-world performance
 */
data class ResearchMetrics(
    val updateResetRisk: Float,           // Probability of settings reset after update
    val communitySuccessRate: Float,      // Success rate from community data
    val deviceSpecificModifier: Float,    // Device-specific reliability modifier
    val lastCommunityUpdate: LocalDateTime? = null
)

/**
 * Reliability levels with clear thresholds
 */
enum class OnePlusReliabilityLevel(
    val threshold: Int,
    val displayName: String,
    val description: String
) {
    EXCELLENT(90, "Exzellent", "Alarm-Zuverlässigkeit praktisch garantiert"),
    GOOD(75, "Gut", "Sehr zuverlässige Alarm-Funktion"),
    FAIR(60, "Ausreichend", "Grundlegende Alarm-Zuverlässigkeit"),
    POOR(0, "Schlecht", "Alarm-Ausfälle wahrscheinlich");
    
    companion object {
        fun fromReliability(reliability: Int): OnePlusReliabilityLevel {
            return values().first { reliability >= it.threshold }
        }
    }
}

/**
 * Configuration state using sealed classes for type-safe state management
 */
sealed class OnePlusConfigurationState {
    object Loading : OnePlusConfigurationState()
    
    data class NotSupported(
        val reason: String
    ) : OnePlusConfigurationState()
    
    data class Configured(
        val deviceInfo: OnePlusDeviceInfo,
        val steps: List<OnePlusConfigurationStep>,
        val reliability: OnePlusReliabilityMetrics,
        val warnings: List<String>,
        val lastUpdated: LocalDateTime
    ) : OnePlusConfigurationState()
    
    data class Error(
        val error: Throwable,
        val canRetry: Boolean
    ) : OnePlusConfigurationState()
}

/**
 * Research-based configuration risks
 */
data class ConfigurationRisk(
    val type: ConfigurationRiskType,
    @FloatRange(from = 0.0, to = 1.0)
    val probability: Float,
    val message: String,
    val severity: RiskSeverity,
    val detectedAt: LocalDateTime = LocalDateTime.now()
)

enum class ConfigurationRiskType {
    FIRMWARE_UPDATE_RESET,
    RECENT_APPS_LOCK_EXPIRED,
    ENHANCED_OPTIMIZATION_ACTIVE,
    SLEEP_STANDBY_INTERFERENCE,
    COMMUNITY_REPORTED_ISSUE
}

enum class RiskSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Legacy OnePlus configuration status for backward compatibility
 * 
 * @deprecated Use OnePlusConfigurationState.Configured instead
 */
@Deprecated("Use OnePlusConfigurationState.Configured for new implementations")
data class OnePlusConfigStatus(
    val isOnePlusDevice: Boolean,
    val deviceModel: String,
    val androidVersion: String,
    val batteryOptimizationExempt: Boolean,
    val configurationSteps: List<OnePlusConfigurationStep>,
    val criticalWarnings: List<String>,
    val estimatedReliability: OnePlusReliabilityMetrics
)

/**
 * Enhanced OnePlus configuration status with additional device validation
 */
data class EnhancedOnePlusConfigStatus(
    val isOnePlusDevice: Boolean,
    val deviceInfo: OnePlusDeviceInfo,
    val capabilities: OnePlusDeviceCapabilities,
    val validationConfidence: Float,
    val batteryOptimizationExempt: Boolean,
    val configurationSteps: List<OnePlusConfigurationStep>,
    val criticalWarnings: List<String>,
    val estimatedReliability: OnePlusReliabilityMetrics,
    val validationDetails: String
)

/**
 * OnePlus device capabilities based on research
 */
data class OnePlusDeviceCapabilities(
    val hasEnhancedOptimization: Boolean,
    val hasAdvancedBatteryManagement: Boolean,
    val supportsMdnsDiscovery: Boolean = false,
    val hasOxygenOS15Features: Boolean = false,
    val supportsRecentAppsLock: Boolean = true,
    val supportsAccessibilityBypass: Boolean = false,
    val hasAutoStartManager: Boolean = true,
    val hasPowerSaverSettings: Boolean = true,
    val supportsAppLocking: Boolean = true,
    @FloatRange(from = 0.0, to = 1.0)
    val batteryOptimizationResetRisk: Float = 0.95f, // 95% chance of reset after update
    val recommendedConfigSteps: List<OnePlusConfigurationStepId> = listOf(
        OnePlusConfigurationStepId.BATTERY_OPTIMIZATION,
        OnePlusConfigurationStepId.AUTO_STARTUP,
        OnePlusConfigurationStepId.BACKGROUND_RUNNING
    )
)
