/**
 * Modern Detection Strategies for OnePlus Configuration
 * 
 * Implements Strategy pattern for different detection methods following SOLID principles:
 * - Single Responsibility: Each strategy handles one detection method
 * - Open/Closed: New strategies can be added without modifying existing code
 * - Dependency Inversion: Strategies depend on abstractions
 * 
 * @author CF-Alarm Team
 * @since 2025.1.0
 */
package com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.detection

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.alarm.receiver.BootReceiver
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.BatteryOptimizationManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.domain.OnePlusConfigurationRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.util.OnePlusConfidenceThresholds

/**
 * Abstract detection strategy following Strategy pattern
 */
abstract class DetectionStrategy {
    /**
     * Detect configuration step status
     * 
     * @param stepId The configuration step to detect
     * @param deviceInfo OnePlus device information for context
     * @return Configured OnePlusConfigurationStep
     */
    abstract suspend fun detectStep(
        stepId: OnePlusConfigurationStepId, 
        deviceInfo: OnePlusDeviceInfo
    ): OnePlusConfigurationStep
    
    /**
     * Template method for creating configuration steps
     */
    protected fun createConfigurationStep(
        stepId: OnePlusConfigurationStepId,
        isCompleted: Boolean,
        deviceInfo: OnePlusDeviceInfo,
        detectionMethod: ConfigDetectionMethod
    ): OnePlusConfigurationStep {
        return when (stepId) {
            OnePlusConfigurationStepId.BATTERY_OPTIMIZATION -> OnePlusConfigurationStep(
                id = stepId,
                title = "Battery Optimization deaktivieren",
                description = "Verhindert aggressive OnePlus Background-Limits",
                settingsPath = "Settings > Battery > Battery optimization > CF-Alarm > Don't optimize",
                alternativePaths = listOf(
                    "Settings > Apps > Gear Icon > Special Access > Battery Optimization",
                    "Settings > Battery > Advanced optimization > Deep optimization (OFF)"
                ),
                isCompleted = isCompleted,
                priority = OnePlusConfigPriority.CRITICAL,
                estimatedImpact = 40,
                detectionMethod = detectionMethod,
                resetsWithUpdates = true,
                oxygenOS15Changes = if (deviceInfo.isOxygenOS15OrHigher) 
                    "Enhanced optimization ersetzt einfache Battery optimization" else null
            )
            
            OnePlusConfigurationStepId.ENHANCED_OPTIMIZATION -> OnePlusConfigurationStep(
                id = stepId,
                title = "Enhanced/Advanced Optimization deaktivieren",
                description = "KRITISCH: Haupt-App-Killer in modernen OnePlus-Geräten (Research-bestätigt)",
                settingsPath = "Settings > Battery > Battery optimization > ⋮ > Advanced optimization > OFF",
                alternativePaths = listOf(
                    "Settings > Battery > Deep optimization > OFF",
                    "Settings > Apps > Special app access > Battery optimization > Advanced"
                ),
                isCompleted = isCompleted,
                priority = OnePlusConfigPriority.CRITICAL,
                estimatedImpact = 35,
                detectionMethod = detectionMethod,
                resetsWithUpdates = true,
                oxygenOS15Changes = if (deviceInfo.isOxygenOS15OrHigher) 
                    "Neue 'Deep optimization' Option hinzugefügt" else null,
                warning = "⚠️ Research: Hauptursache für App-Kills auf OnePlus"
            )
            
            OnePlusConfigurationStepId.SLEEP_STANDBY_OPTIMIZATION -> OnePlusConfigurationStep(
                id = stepId,
                title = "Sleep Standby Optimization deaktivieren",
                description = "Verhindert Netzwerk-Abschaltung während erkannter Schlafzeiten",
                settingsPath = "Settings > Battery > Battery optimization > ⋮ > Advanced optimization > Sleep standby optimization (OFF)",
                alternativePaths = emptyList(),
                isCompleted = isCompleted,
                priority = OnePlusConfigPriority.HIGH,
                estimatedImpact = 20,
                detectionMethod = detectionMethod,
                resetsWithUpdates = true,
                oxygenOS15Changes = if (deviceInfo.isOxygenOS15OrHigher) 
                    "Intelligentere Schlafzeit-Erkennung" else null
            )
            
            OnePlusConfigurationStepId.AUTO_STARTUP -> OnePlusConfigurationStep(
                id = stepId,
                title = "Auto-Start aktivieren",
                description = "Erlaubt App-Start nach Neustart (wird oft zurückgesetzt)",
                settingsPath = "Settings > App Management > CF-Alarm > Allow Auto Startup",
                alternativePaths = listOf(
                    "Settings > Privacy > Special app access > Auto-start",
                    "Settings > Battery > App Auto-start",
                    "Settings > Apps > CF-Alarm > Auto-start"
                ),
                isCompleted = isCompleted,
                priority = OnePlusConfigPriority.HIGH,
                estimatedImpact = 25,
                detectionMethod = detectionMethod,
                resetsWithUpdates = true,
                oxygenOS15Changes = if (deviceInfo.isOxygenOS15OrHigher) 
                    "Auto-start Einstellungen neu organisiert" else null
            )
            
            OnePlusConfigurationStepId.BACKGROUND_RUNNING -> OnePlusConfigurationStep(
                id = stepId,
                title = "Hintergrund-Ausführung erlauben",
                description = "Verhindert aggressive Power Management",
                settingsPath = "Settings > App Management > CF-Alarm > Power Saver > Allow Background Running",
                alternativePaths = listOf(
                    "Settings > Battery > Background app refresh",
                    "Settings > Apps > CF-Alarm > Background activity"
                ),
                isCompleted = isCompleted,
                priority = OnePlusConfigPriority.HIGH,
                estimatedImpact = 20,
                detectionMethod = detectionMethod,
                resetsWithUpdates = true,
                oxygenOS15Changes = if (deviceInfo.isOxygenOS15OrHigher) 
                    "Neue Background management Kategorien" else null
            )
            
            OnePlusConfigurationStepId.RECENT_APPS_LOCK -> OnePlusConfigurationStep(
                id = stepId,
                title = "App in Recent Apps sperren",
                description = "Nur 70% zuverlässig - wird oft automatisch entsperrt (Research-bestätigt)",
                settingsPath = "Recent Apps > CF-Alarm > Tap Lock icon 🔒",
                alternativePaths = emptyList(),
                isCompleted = isCompleted,
                priority = OnePlusConfigPriority.MEDIUM,
                estimatedImpact = 10,
                detectionMethod = detectionMethod,
                resetsWithUpdates = true,
                oxygenOS15Changes = if (deviceInfo.isOxygenOS15OrHigher) 
                    "Recent apps clearing behaviour neu konfigurierbar" else null,
                warning = "⚠️ Research: Nur 70% Erfolgsrate laut Community-Reports",
                successRate = 0.7f // Research-based success rate
            )
        }
    }
}

/**
 * API-based detection for reliable system settings
 */
class ApiBasedDetectionStrategy(
    private val batteryOptimizationManager: BatteryOptimizationManager
) : DetectionStrategy() {
    
    override suspend fun detectStep(
        stepId: OnePlusConfigurationStepId, 
        deviceInfo: OnePlusDeviceInfo
    ): OnePlusConfigurationStep {
        val isCompleted = when (stepId) {
            OnePlusConfigurationStepId.BATTERY_OPTIMIZATION -> {
                batteryOptimizationManager.isIgnoringBatteryOptimizations()
            }
            else -> throw IllegalArgumentException("API detection not supported for $stepId")
        }
        
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "API detection for $stepId: $isCompleted")
        
        return createConfigurationStep(
            stepId, 
            isCompleted, 
            deviceInfo, 
            ConfigDetectionMethod.API_RELIABLE
        )
    }
}

/**
 * Heuristic detection for settings without direct API access
 */
class HeuristicDetectionStrategy(
    private val repository: OnePlusConfigurationRepository,
    private val context: Context
) : DetectionStrategy() {
    
    override suspend fun detectStep(
        stepId: OnePlusConfigurationStepId, 
        deviceInfo: OnePlusDeviceInfo
    ): OnePlusConfigurationStep {
        val isCompleted = when (stepId) {
            OnePlusConfigurationStepId.ENHANCED_OPTIMIZATION -> {
                detectEnhancedOptimizationStatus(deviceInfo)
            }
            else -> throw IllegalArgumentException("Heuristic detection not supported for $stepId")
        }
        
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "Heuristic detection for $stepId: $isCompleted")
        
        return createConfigurationStep(
            stepId, 
            isCompleted, 
            deviceInfo, 
            ConfigDetectionMethod.HEURISTIC_USER_CONFIRMED
        )
    }
    
    /**
     * Research-based heuristic for Enhanced Optimization detection
     */
    private suspend fun detectEnhancedOptimizationStatus(deviceInfo: OnePlusDeviceInfo): Boolean {
        val userConfirmed = repository.getUserConfirmation("enhanced_optimization")
        if (userConfirmed) return true
        
        // Heuristic: If battery optimization is disabled and device is modern OnePlus,
        // Enhanced Optimization might also be disabled
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOptimizationDisabled = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        
        return if (batteryOptimizationDisabled && deviceInfo.hasEnhancedOptimization) {
            // Store heuristic confidence
            repository.setHeuristicScore("enhanced_optimization", OnePlusConfidenceThresholds.ENHANCED_OPTIMIZATION_HEURISTIC)
            true
        } else {
            false
        }
    }
}

/**
 * User confirmation detection for settings requiring manual verification
 */
class UserConfirmationDetectionStrategy(
    private val repository: OnePlusConfigurationRepository
) : DetectionStrategy() {
    
    override suspend fun detectStep(
        stepId: OnePlusConfigurationStepId, 
        deviceInfo: OnePlusDeviceInfo
    ): OnePlusConfigurationStep {
        val isCompleted = repository.getUserConfirmation(stepId.name.lowercase())
        
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "User confirmation for $stepId: $isCompleted")
        
        return createConfigurationStep(
            stepId, 
            isCompleted, 
            deviceInfo, 
            ConfigDetectionMethod.USER_CONFIRMED_ONLY
        )
    }
}

/**
 * Combined heuristic and user confirmation detection
 */
class HeuristicUserConfirmationDetectionStrategy(
    private val repository: OnePlusConfigurationRepository,
    private val context: Context
) : DetectionStrategy() {
    
    override suspend fun detectStep(
        stepId: OnePlusConfigurationStepId, 
        deviceInfo: OnePlusDeviceInfo
    ): OnePlusConfigurationStep {
        val userConfirmed = repository.getUserConfirmation(stepId.name.lowercase())
        if (userConfirmed) {
            return createConfigurationStep(
                stepId, 
                true, 
                deviceInfo, 
                ConfigDetectionMethod.HEURISTIC_USER_CONFIRMED
            )
        }
        
        // Apply heuristics
        val heuristicResult = when (stepId) {
            OnePlusConfigurationStepId.AUTO_STARTUP -> detectAutoStartWithHeuristics()
            OnePlusConfigurationStepId.BACKGROUND_RUNNING -> detectBackgroundRunningWithHeuristics()
            else -> false
        }
        
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "Heuristic + User detection for $stepId: $heuristicResult")
        
        return createConfigurationStep(
            stepId, 
            heuristicResult, 
            deviceInfo, 
            ConfigDetectionMethod.HEURISTIC_USER_CONFIRMED
        )
    }
    
    /**
     * Enhanced auto-start heuristic with boot detection
     */
    private suspend fun detectAutoStartWithHeuristics(): Boolean {
        try {
            // Check if Boot Receiver is enabled
            val bootReceiver = ComponentName(context, BootReceiver::class.java)
            val pm = context.packageManager
            val state = pm.getComponentEnabledSetting(bootReceiver)
            
            // Check battery optimization status
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptimizationDisabled = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            
            // Heuristic: If Boot Receiver is enabled AND battery optimization is disabled,
            // Auto-Start is likely enabled
            val isLikelyEnabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED && 
                                 batteryOptimizationDisabled
            
            if (isLikelyEnabled) {
                repository.setHeuristicScore("auto_startup", 0.8f)
            }
            
            return isLikelyEnabled
        } catch (e: Exception) {
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "Auto-start heuristic failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Enhanced background running heuristic
     */
    private suspend fun detectBackgroundRunningWithHeuristics(): Boolean {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptimizationDisabled = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            
            // Simple heuristic: If battery optimization is disabled, background running is likely allowed
            if (batteryOptimizationDisabled) {
                repository.setHeuristicScore("background_running", 0.7f)
                return true
            }
            
            return false
        } catch (e: Exception) {
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "Background running heuristic failed: ${e.message}")
            return false
        }
    }
}
