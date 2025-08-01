/**
 * OnePlus Validation Manager
 * 
 * Central coordinator for OnePlus device validation and configuration management.
 * Orchestrates the validation process and provides a clean interface for other components.
 * 
 * Key Responsibilities:
 * - Coordinate device validation workflow
 * - Cache validation results for performance
 * - Provide unified interface for OnePlus detection
 * - Integrate with existing BatteryOptimizationManager
 * 
 * @author CF-Alarm Team
 * @since 2025.1.0
 */
package com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation

import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Manages OnePlus device validation with caching and coordination
 */
class OnePlusValidationManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    private val deviceValidator = OnePlusDeviceValidator(context)
    
    // Cache validation results to avoid repeated expensive operations
    private var cachedValidationResult: OnePlusDeviceValidationResult? = null
    private var lastValidationTime: LocalDateTime? = null
    
    companion object {
        private const val CACHE_VALIDITY_HOURS = 24L // Cache validation for 24 hours
        private const val VALIDATION_TIMEOUT_MS = 10_000L // 10 second timeout
    }
    
    /**
     * Get OnePlus device validation result with caching
     */
    suspend fun getDeviceValidation(forceRefresh: Boolean = false): OnePlusDeviceValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache validity
                if (!forceRefresh && isCacheValid()) {
                    Logger.d(LogTags.BATTERY_OPTIMIZATION, "📄 Using cached OnePlus validation result")
                    return@withContext cachedValidationResult!!
                }
                
                Logger.i(LogTags.BATTERY_OPTIMIZATION, "🔍 Performing OnePlus device validation...")
                
                // Perform validation with timeout
                val result = withTimeout(VALIDATION_TIMEOUT_MS) {
                    deviceValidator.validateOnePlusDevice()
                }
                
                // Cache successful results
                when (result) {
                    is OnePlusDeviceValidationResult.Valid,
                    is OnePlusDeviceValidationResult.NotOnePlus -> {
                        cachedValidationResult = result
                        lastValidationTime = LocalDateTime.now()
                        Logger.i(LogTags.BATTERY_OPTIMIZATION, "✅ OnePlus validation completed and cached")
                    }
                    is OnePlusDeviceValidationResult.ValidationError -> {
                        Logger.w(LogTags.BATTERY_OPTIMIZATION, "⚠️ OnePlus validation error: ${result.error.message}")
                    }
                }
                
                result
                
            } catch (e: TimeoutCancellationException) {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "⏱️ OnePlus validation timed out")
                OnePlusDeviceValidationResult.ValidationError(
                    error = RuntimeException("Validation timed out", e),
                    canRetry = true
                )
            } catch (e: Exception) {
                Logger.e(LogTags.BATTERY_OPTIMIZATION, "💥 OnePlus validation failed", e)
                OnePlusDeviceValidationResult.ValidationError(
                    error = e,
                    canRetry = true
                )
            }
        }
    }
    
    /**
     * Check if this is a validated OnePlus device
     */
    suspend fun isOnePlusDevice(): Boolean {
        return when (getDeviceValidation()) {
            is OnePlusDeviceValidationResult.Valid -> true
            else -> false
        }
    }
    
    /**
     * Get OnePlus device information if validated
     */
    suspend fun getOnePlusDeviceInfo(): OnePlusDeviceInfo? {
        return when (val result = getDeviceValidation()) {
            is OnePlusDeviceValidationResult.Valid -> result.deviceInfo
            else -> null
        }
    }
    
    /**
     * Get OnePlus device capabilities if validated
     */
    suspend fun getOnePlusCapabilities(): OnePlusDeviceCapabilities? {
        return when (val result = getDeviceValidation()) {
            is OnePlusDeviceValidationResult.Valid -> result.capabilities
            else -> null
        }
    }
    
    /**
     * Get validation confidence score
     */
    suspend fun getValidationConfidence(): Float {
        return when (val result = getDeviceValidation()) {
            is OnePlusDeviceValidationResult.Valid -> result.confidence
            is OnePlusDeviceValidationResult.NotOnePlus -> result.confidence
            is OnePlusDeviceValidationResult.ValidationError -> 0.0f
        }
    }
    
    /**
     * Get detailed validation information for debugging
     */
    suspend fun getValidationDetails(): String {
        return when (val result = getDeviceValidation()) {
            is OnePlusDeviceValidationResult.Valid -> buildString {
                appendLine("=== OnePlus Device Validation Details ===")
                appendLine("✅ VALIDATED ONEPLUS DEVICE")
                appendLine("Confidence: ${String.format("%.1f%%", result.confidence * 100)}")
                appendLine()
                appendLine("📱 Device Information:")
                appendLine("  Model: ${result.deviceInfo.model}")
                appendLine("  Manufacturer: ${result.deviceInfo.manufacturer}")
                appendLine("  Android: ${result.deviceInfo.androidVersion}")
                appendLine("  OxygenOS: ${result.deviceInfo.oxygenOSVersion ?: "Not detected"}")
                appendLine("  OxygenOS 15+: ${result.deviceInfo.isOxygenOS15OrHigher}")
                appendLine()
                appendLine("🎯 Device Capabilities:")
                appendLine("  Enhanced Optimization: ${result.capabilities.hasEnhancedOptimization}")
                appendLine("  Advanced Battery Mgmt: ${result.capabilities.hasAdvancedBatteryManagement}")
                appendLine("  Accessibility Bypass: ${result.capabilities.supportsAccessibilityBypass}")
                appendLine("  Auto-Start Manager: ${result.capabilities.hasAutoStartManager}")
                appendLine("  Power Saver Settings: ${result.capabilities.hasPowerSaverSettings}")
                appendLine("  App Locking: ${result.capabilities.supportsAppLocking}")
                appendLine("  Reset Risk: ${String.format("%.1f%%", result.capabilities.batteryOptimizationResetRisk * 100)}")
                appendLine()
                appendLine("🔍 Validation Steps:")
                result.validationSteps.forEach { step ->
                    val status = if (step.passed) "✅" else "❌"
                    appendLine("  $status ${step.name}: ${step.details} (${String.format("%.1f%%", step.confidence * 100)})")
                }
                appendLine()
                appendLine("📋 Recommended Config Steps:")
                result.capabilities.recommendedConfigSteps.forEach { stepId ->
                    appendLine("  • ${stepId.name}")
                }
                appendLine("==========================================")
            }
            
            is OnePlusDeviceValidationResult.NotOnePlus -> buildString {
                appendLine("=== OnePlus Device Validation Details ===")
                appendLine("❌ NOT A ONEPLUS DEVICE")
                appendLine("Confidence: ${String.format("%.1f%%", result.confidence * 100)}")
                appendLine("Reason: ${result.reason}")
                appendLine()
                appendLine("🔍 Validation Steps:")
                result.validationSteps.forEach { step ->
                    val status = if (step.passed) "✅" else "❌"
                    appendLine("  $status ${step.name}: ${step.details} (${String.format("%.1f%%", step.confidence * 100)})")
                }
                appendLine("==========================================")
            }
            
            is OnePlusDeviceValidationResult.ValidationError -> buildString {
                appendLine("=== OnePlus Device Validation Details ===")
                appendLine("💥 VALIDATION ERROR")
                appendLine("Error: ${result.error.message}")
                appendLine("Can Retry: ${result.canRetry}")
                appendLine("==========================================")
            }
        }
    }
    
    /**
     * Force refresh of validation cache
     */
    suspend fun refreshValidation(): OnePlusDeviceValidationResult {
        Logger.i(LogTags.BATTERY_OPTIMIZATION, "🔄 Forcing OnePlus validation refresh...")
        return getDeviceValidation(forceRefresh = true)
    }
    
    /**
     * Clear validation cache
     */
    fun clearCache() {
        cachedValidationResult = null
        lastValidationTime = null
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "🗑️ OnePlus validation cache cleared")
    }
    
    /**
     * Check if cached validation result is still valid
     */
    private fun isCacheValid(): Boolean {
        val result = cachedValidationResult
        val lastValidation = lastValidationTime
        
        if (result == null || lastValidation == null) {
            return false
        }
        
        val hoursSinceValidation = ChronoUnit.HOURS.between(lastValidation, LocalDateTime.now())
        return hoursSinceValidation < CACHE_VALIDITY_HOURS
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        coroutineScope.cancel()
        clearCache()
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "🧹 OnePlus validation manager cleaned up")
    }
}
