package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation.OnePlusValidationManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation.OnePlusDeviceValidationResult
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags


/**
 * Manages battery optimization settings and doze mode compatibility
 * for reliable alarm functionality.
 * 
 * Features:
 * - Battery optimization exemption checks
 * - User-friendly exemption requests
 * - Doze mode detection
 * - Manufacturer-specific battery settings handling
 */
interface IBatteryOptimizationManager {
    /**
     * Checks if the app is exempt from battery optimizations
     */
    fun isIgnoringBatteryOptimizations(): Boolean
    
    /**
     * Requests battery optimization exemption from user
     * @param fromActivity Whether request comes from an Activity context
     * @return Intent to start settings activity, or null if not needed
     */
    fun requestBatteryOptimizationExemption(fromActivity: Boolean = false): Intent?
    
    /**
     * Gets comprehensive battery optimization status
     */
    fun getBatteryOptimizationStatus(): BatteryOptimizationStatus
    
    /**
     * Gets debug information about battery settings
     */
    fun getDebugInfo(): String
}

data class BatteryOptimizationStatus(
    val isExempt: Boolean,
    val canRequestExemption: Boolean,
    val recommendedAction: String?,
    val manufacturerSpecificNote: String?
)

class BatteryOptimizationManager(
    private val context: Context
) : IBatteryOptimizationManager {
    
    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    
    // 🚀 Enhanced OnePlus Integration with Device Validator
    private val onePlusValidationManager: OnePlusValidationManager by lazy {
        OnePlusValidationManager(context)
    }
    
    override fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "Battery optimization exempt: $isIgnoring")
            isIgnoring
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Error checking battery optimization status", e)
            false
        }
    }
    
    override fun requestBatteryOptimizationExemption(fromActivity: Boolean): Intent? {
        if (isIgnoringBatteryOptimizations()) {
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "Already exempt from battery optimization")
            return null
        }
        
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                if (!fromActivity) {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            
            // Verify the intent can be handled
            if (intent.resolveActivity(context.packageManager) != null) {
                Logger.i(LogTags.BATTERY_OPTIMIZATION, "🔋 Creating battery optimization exemption request")
                intent
            } else {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "Battery optimization settings not available, trying general settings")
                createFallbackBatterySettingsIntent(fromActivity)
            }
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Error creating battery optimization intent", e)
            createFallbackBatterySettingsIntent(fromActivity)
        }
    }
    
    private fun createFallbackBatterySettingsIntent(fromActivity: Boolean): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            if (!fromActivity) {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
    
    override fun getBatteryOptimizationStatus(): BatteryOptimizationStatus {
        val isExempt = isIgnoringBatteryOptimizations()
        val canRequest = !isExempt // minSdk=26, also immer >= M
        
        val recommendedAction = when {
            isExempt -> null
            canRequest -> "Aktiviere Akkuoptimierung-Ausnahme für zuverlässige Alarme"
            else -> "Prüfe Geräte-spezifische Energieeinstellungen"
        }
        
        val manufacturerNote = getManufacturerSpecificNote()
        
        return BatteryOptimizationStatus(
            isExempt = isExempt,
            canRequestExemption = canRequest,
            recommendedAction = recommendedAction,
            manufacturerSpecificNote = manufacturerNote
        )
    }
    
    private fun getManufacturerSpecificNote(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("huawei") -> {
                "Huawei-Geräte: Zusätzlich 'Geschützte Apps' in den Einstellungen prüfen"
            }
            manufacturer.contains("xiaomi") -> {
                "Xiaomi-Geräte: 'Autostart' und 'Akku-Saver' Einstellungen prüfen"
            }
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") -> {
                // 🚀 ENHANCED OnePlus Detection with specific steps
                buildOnePlusSpecificGuidance()
            }
            manufacturer.contains("samsung") -> {
                "Samsung-Geräte: 'Sleeping apps' Liste und 'Adaptive battery' prüfen"
            }
            manufacturer.contains("vivo") -> {
                "Vivo-Geräte: 'Hintergrund-App-Aktualisierung' Einstellungen prüfen"
            }
            else -> null
        }
    }
    
    /**
     * 🔴 OnePlus-specific multi-step configuration guidance
     */
    private fun buildOnePlusSpecificGuidance(): String {
        return buildString {
            appendLine("🔴 OnePlus erfordert MEHRERE Schritte:")
            appendLine("1. Battery Optimization: Settings > Battery > Battery optimization > CF-Alarm > Don't optimize")
            appendLine("2. Auto-Start: Settings > App Management > CF-Alarm > Allow Auto Startup")
            appendLine("3. Background Running: Settings > App Management > CF-Alarm > Power Saver > Allow Background Running")
            appendLine("4. Recent Apps Lock: Recent Apps > CF-Alarm > Tap Lock icon")
            appendLine("⚠️ KRITISCH: OnePlus setzt diese Einstellungen bei Updates zurück!")
        }
    }
    
    override fun getDebugInfo(): String {
        val status = getBatteryOptimizationStatus()
        
        return buildString {
            appendLine("=== Battery Optimization Debug ===")
            appendLine("Android version: ${Build.VERSION.SDK_INT}")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Is exempt: ${status.isExempt}")
            appendLine("Can request exemption: ${status.canRequestExemption}")
            appendLine("Power manager: $powerManager")
            
            // minSdk=26 (>= M), alle battery optimization APIs sind verfügbar
            try {
                appendLine("Device idle mode: ${powerManager.isDeviceIdleMode}")
                // API 28+ (Android P) - Light device idle mode (using safe access)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        // Safe access to API 28+ method
                        val lightIdleMode = powerManager.javaClass.getMethod("isLightDeviceIdleMode").invoke(powerManager) as Boolean
                        appendLine("Light device idle mode: $lightIdleMode")
                    } catch (_: Exception) {
                        appendLine("Light device idle mode: Not available")
                    }
                }
                // API 29+ (Android Q) - Power save mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        appendLine("Power save mode: ${powerManager.isPowerSaveMode}")
                    } catch (_: Exception) {
                        appendLine("Power save mode: Not available")
                    }
                }
            } catch (e: Exception) {
                appendLine("Error getting power manager state: ${e.message}")
            }
            
            status.recommendedAction?.let { action ->
                appendLine("Recommended action: $action")
            }
            status.manufacturerSpecificNote?.let { note ->
                appendLine("Manufacturer note: $note")
            }
            appendLine("===================================")
        }
    }
    
    /**
     * Comprehensive battery optimization check with user guidance
     */
    fun performBatteryOptimizationCheck(): BatteryCheckResult {
        val status = getBatteryOptimizationStatus()
        
        return when {
            status.isExempt -> {
                BatteryCheckResult.Optimal("App ist von Akkuoptimierung ausgenommen")
            }
            status.canRequestExemption -> {
                BatteryCheckResult.NeedsAction(
                    message = "Akkuoptimierung-Ausnahme empfohlen für zuverlässige Alarme",
                    action = requestBatteryOptimizationExemption()
                )
            }
            else -> {
                BatteryCheckResult.Warning(
                    "Prüfe manuelle Energieeinstellungen: ${status.manufacturerSpecificNote ?: "Gerätespezifische Einstellungen"}"
                )
            }
        }
    }
    
    /**
     * 🚀 PHASE 2: OnePlus-specific configuration status check
     */
    fun getOnePlusConfigurationStatus(): OnePlusConfigStatus? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        if (!manufacturer.contains("oneplus") && !manufacturer.contains("oppo")) {
            return null // Not a OnePlus device
        }
        
        return OnePlusConfigStatus(
            isOnePlusDevice = true,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            batteryOptimizationExempt = isIgnoringBatteryOptimizations(),
            configurationSteps = getOnePlusConfigurationSteps(),
            criticalWarnings = getOnePlusCriticalWarnings(),
            estimatedReliability = calculateOnePlusReliability()
        )
    }
    
    /**
     * Get OnePlus configuration steps with current status and better detection
     */
    private fun getOnePlusConfigurationSteps(): List<OnePlusConfigurationStep> {
        val steps = mutableListOf<OnePlusConfigurationStep>()
        
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "🔧 Building OnePlus configuration steps...")
        
        // Battery Optimization Step (most critical)
        val batteryExempt = isIgnoringBatteryOptimizations()
        steps.add(
            OnePlusConfigurationStep(
                id = OnePlusConfigurationStepId.BATTERY_OPTIMIZATION,
                title = "Akkuoptimierung deaktivieren",
                description = "App von Akkuoptimierung ausnehmen",
                settingsPath = "Einstellungen > Akku > Akkuoptimierung > CF-Alarm > Nicht optimieren",
                isCompleted = batteryExempt,
                priority = OnePlusConfigPriority.CRITICAL,
                estimatedImpact = 40,
                detectionMethod = ConfigDetectionMethod.API_RELIABLE,
                resetsWithUpdates = true
            )
        )
        
        Logger.d(LogTags.BATTERY_OPTIMIZATION, "✅ Battery optimization step: completed=$batteryExempt")
        
        // Auto Startup Step (high priority but not detectable via API)
        steps.add(
            OnePlusConfigurationStep(
                id = OnePlusConfigurationStepId.AUTO_STARTUP,
                title = "Autostart aktivieren",
                description = "App-Autostart zulassen",
                settingsPath = "Einstellungen > App-Verwaltung > CF-Alarm > Autostart zulassen",
                isCompleted = false, // Cannot be reliably detected via API
                priority = OnePlusConfigPriority.HIGH,
                estimatedImpact = 25,
                detectionMethod = ConfigDetectionMethod.USER_CONFIRMED_ONLY,
                resetsWithUpdates = true,
                warning = "Nicht automatisch prüfbar - manuelle Bestätigung erforderlich"
            )
        )
        
        // Background Running Step (high priority)
        steps.add(
            OnePlusConfigurationStep(
                id = OnePlusConfigurationStepId.BACKGROUND_RUNNING,
                title = "Hintergrund-Ausführung",
                description = "Hintergrund-Ausführung zulassen",
                settingsPath = "Einstellungen > App-Verwaltung > CF-Alarm > Energiesparmodus > Hintergrund-Ausführung zulassen",
                isCompleted = false, // Cannot be reliably detected via API
                priority = OnePlusConfigPriority.HIGH,
                estimatedImpact = 25,
                detectionMethod = ConfigDetectionMethod.USER_CONFIRMED_ONLY,
                resetsWithUpdates = true,
                warning = "Nicht automatisch prüfbar - manuelle Bestätigung erforderlich"
            )
        )
        
        // Recent Apps Lock Step (medium priority, temporary)
        steps.add(
            OnePlusConfigurationStep(
                id = OnePlusConfigurationStepId.RECENT_APPS_LOCK,
                title = "Recent Apps sperren",
                description = "App in Recent Apps sperren",
                settingsPath = "Recent Apps > CF-Alarm > Sperr-Symbol tippen",
                isCompleted = false, // Cannot be detected via API
                priority = OnePlusConfigPriority.MEDIUM,
                estimatedImpact = 10,
                detectionMethod = ConfigDetectionMethod.USER_CONFIRMED_ONLY,
                resetsWithUpdates = false,
                warning = "Sperre läuft nach 24h automatisch ab und muss erneuert werden"
            )
        )
        
        Logger.business(LogTags.BATTERY_OPTIMIZATION, 
            "📋 OnePlus configuration steps created",
            "Total: ${steps.size}, Critical: ${steps.count { it.priority == OnePlusConfigPriority.CRITICAL }}, " +
            "Completed: ${steps.count { it.isCompleted }}"
        )
        
        return steps
    }
    
    /**
     * Get critical warnings for OnePlus devices
     */
    private fun getOnePlusCriticalWarnings(): List<String> {
        val warnings = mutableListOf<String>()
        
        // Check for common OnePlus issues
        if (!isIgnoringBatteryOptimizations()) {
            warnings.add("⚠️ KRITISCH: Akkuoptimierung ist aktiv - Alarme können ausfallen!")
        }
        
        // Firmware update warning
        warnings.add("🔄 WICHTIG: OnePlus setzt Einstellungen bei System-Updates zurück!")
        
        // OxygenOS specific warnings
        val oxygenVersion = getOxygenOSVersion()
        if (oxygenVersion != null && oxygenVersion >= 15) {
            warnings.add("🆕 OxygenOS 15+: Neue 'Enhanced Optimization' prüfen!")
        }
        
        return warnings
    }
    
    /**
     * Calculate OnePlus reliability metrics with improved accuracy
     */
    private fun calculateOnePlusReliability(): OnePlusReliabilityMetrics {
        val steps = getOnePlusConfigurationSteps()
        val completedSteps = steps.count { it.isCompleted }
        val totalSteps = steps.size
        
        Logger.d(LogTags.BATTERY_OPTIMIZATION, 
            "📊 Calculating reliability: $completedSteps/$totalSteps steps completed"
        )
        
        // Weight-based calculation for more accurate reliability assessment
        val totalWeight = steps.sumOf { it.estimatedImpact }
        val completedWeight = steps.filter { it.isCompleted }.sumOf { it.estimatedImpact }
        
        val currentReliability = if (totalWeight > 0) {
            ((completedWeight.toFloat() / totalWeight) * 100).toInt().coerceIn(0, 100)
        } else {
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "⚠️ Total weight is 0, defaulting to 0% reliability")
            0
        }
        
        val maxPossibleReliability = 100 // All steps completed
        
        val reliabilityLevel = OnePlusReliabilityLevel.fromReliability(currentReliability)
        
        val researchMetrics = ResearchMetrics(
            updateResetRisk = 0.95f, // 95% chance settings reset after update
            communitySuccessRate = 0.90f, // 90% success rate when properly configured
            deviceSpecificModifier = getDeviceSpecificModifier()
        )
        
        Logger.business(LogTags.BATTERY_OPTIMIZATION, 
            "📊 OnePlus Reliability Calculated",
            "Current: $currentReliability%, Level: ${reliabilityLevel.displayName}, " +
            "Steps: $completedSteps/$totalSteps, Weight: $completedWeight/$totalWeight"
        )
        
        return OnePlusReliabilityMetrics(
            currentReliability = currentReliability,
            maxPossibleReliability = maxPossibleReliability,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            reliabilityLevel = reliabilityLevel,
            lastCalculated = java.time.LocalDateTime.now(),
            researchMetrics = researchMetrics
        )
    }
    
    /**
     * Get device-specific reliability modifier
     */
    private fun getDeviceSpecificModifier(): Float {
        val model = Build.MODEL.lowercase()
        return when {
            model.contains("nord") -> 0.85f // Nord series has different optimization behavior
            model.contains("pro") -> 1.0f   // Pro models work well
            model.matches(Regex(".*1[0-9].*")) -> 0.95f // OnePlus 10+ series
            else -> 0.90f // Default for other models
        }
    }
    
    /**
     * Get OxygenOS version if detectable
     */
    private fun getOxygenOSVersion(): Int? {
        return try {
            // Try to extract OxygenOS version from build info
            val buildDisplay = Build.DISPLAY
            val versionRegex = Regex(".*[Oo]xygen.*?([0-9]+)")
            val match = versionRegex.find(buildDisplay)
            match?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Quick OnePlus device check using enhanced validation
     */
    suspend fun isEnhancedOnePlusDevice(): Boolean {
        return try {
            onePlusValidationManager.isOnePlusDevice()
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Error checking OnePlus device", e)
            isOnePlusDevice() // Fallback to simple check
        }
    }
    
    /**
     * Get enhanced OnePlus device information
     */
    suspend fun getEnhancedOnePlusDeviceInfo(): OnePlusDeviceInfo? {
        return try {
            onePlusValidationManager.getOnePlusDeviceInfo()
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "Error getting OnePlus device info", e)
            null
        }
    }
    
    /**
     * 🎯 Creates targeted OnePlus setup intents for each configuration step
     */
    fun createOnePlusSetupIntent(stepId: String): Intent? {
        return when (stepId) {
            "battery_optimization" -> {
                requestBatteryOptimizationExemption(fromActivity = false)
            }
            "auto_startup" -> {
                // OnePlus-specific app management intent
                createOnePlusAppManagementIntent()
            }
            "background_running" -> {
                // OnePlus-specific power saver intent
                createOnePlusPowerSaverIntent()
            }
            "recent_apps_lock" -> {
                // Cannot create direct intent - return instruction intent
                createOnePlusInstructionIntent(stepId)
            }
            else -> null
        }
    }
    
    private fun createOnePlusAppManagementIntent(): Intent {
        // Try OnePlus-specific app management
        return try {
            Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = android.net.Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            // Fallback to general app settings
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
    
    private fun createOnePlusPowerSaverIntent(): Intent {
        // OnePlus power management is usually in app details
        return createOnePlusAppManagementIntent()
    }
    
    private fun createOnePlusInstructionIntent(stepId: String): Intent {
        // Create an intent to show instructions since we can't directly open Recent Apps
        return Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    
    /**
     * Enhanced OnePlus configuration status with device validation
     */
    suspend fun getEnhancedOnePlusConfigurationStatus(): EnhancedOnePlusConfigStatus? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        if (!manufacturer.contains("oneplus") && !manufacturer.contains("oppo")) {
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "❌ Not a OnePlus device: $manufacturer")
            return null
        }
        
        return try {
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "🔍 Getting enhanced OnePlus configuration status...")
            
            // Get device info and validation with fallback
            val deviceInfo = try {
                getEnhancedOnePlusDeviceInfo() ?: createFallbackDeviceInfo()
            } catch (e: Exception) {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "Device info fallback due to error", e)
                createFallbackDeviceInfo()
            }
            
            val validationConfidence = try {
                onePlusValidationManager.getValidationConfidence()
            } catch (e: Exception) {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "Validation confidence fallback due to error", e)
                1.0f // Assume high confidence for basic detection
            }
            
            val capabilities = try {
                (onePlusValidationManager.getOnePlusCapabilities() as? OnePlusDeviceCapabilities) 
                    ?: createFallbackCapabilities()
            } catch (e: Exception) {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "Capabilities fallback due to error", e)
                createFallbackCapabilities()
            }
            
            val validationDetails = try {
                onePlusValidationManager.getValidationDetails()
            } catch (e: Exception) {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "Validation details fallback due to error", e)
                "Enhanced validation not available - using basic OnePlus detection"
            }
            
            // Get standard configuration status (this is working)
            val standardStatus = getOnePlusConfigurationStatus()
            if (standardStatus == null) {
                Logger.e(LogTags.BATTERY_OPTIMIZATION, "❌ Standard OnePlus status is null")
                return null
            }
            
            Logger.business(LogTags.BATTERY_OPTIMIZATION, 
                "✅ Enhanced OnePlus configuration status created successfully",
                "Device: ${deviceInfo.model}, Confidence: ${String.format("%.1f%%", validationConfidence * 100)}, " +
                "Reliability: ${standardStatus.estimatedReliability.currentReliability}%"
            )
            
            EnhancedOnePlusConfigStatus(
                isOnePlusDevice = standardStatus.isOnePlusDevice,
                deviceInfo = deviceInfo,
                capabilities = capabilities,
                validationConfidence = validationConfidence,
                batteryOptimizationExempt = standardStatus.batteryOptimizationExempt,
                configurationSteps = standardStatus.configurationSteps,
                criticalWarnings = standardStatus.criticalWarnings,
                estimatedReliability = standardStatus.estimatedReliability,
                validationDetails = validationDetails
            )
            
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "💥 Error getting enhanced OnePlus status", e)
            
            // Final fallback: Try to return basic status
            try {
                val basicStatus = getOnePlusConfigurationStatus()
                if (basicStatus != null) {
                    Logger.w(LogTags.BATTERY_OPTIMIZATION, "📝 Returning basic OnePlus status as fallback")
                    EnhancedOnePlusConfigStatus(
                        isOnePlusDevice = basicStatus.isOnePlusDevice,
                        deviceInfo = createFallbackDeviceInfo(),
                        capabilities = createFallbackCapabilities(),
                        validationConfidence = 0.8f, // Medium confidence for fallback
                        batteryOptimizationExempt = basicStatus.batteryOptimizationExempt,
                        configurationSteps = basicStatus.configurationSteps,
                        criticalWarnings = basicStatus.criticalWarnings,
                        estimatedReliability = basicStatus.estimatedReliability,
                        validationDetails = "Fallback mode - enhanced validation failed: ${e.message}"
                    )
                } else {
                    null
                }
            } catch (fallbackException: Exception) {
                Logger.e(LogTags.BATTERY_OPTIMIZATION, "💥 Even fallback failed", fallbackException)
                null
            }
        }
    }
    
    private fun createFallbackDeviceInfo(): OnePlusDeviceInfo {
        return OnePlusDeviceInfo(
            isOnePlusDevice = true,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            oxygenOSVersion = getOxygenOSVersion()?.toString(),
            buildDisplay = Build.DISPLAY
        )
    }
    
    private fun createFallbackCapabilities(): OnePlusDeviceCapabilities {
        val oxygenVersion = getOxygenOSVersion()
        val androidApiLevel = Build.VERSION.SDK_INT
        
        return OnePlusDeviceCapabilities(
            hasEnhancedOptimization = oxygenVersion?.let { it >= 15 } ?: false,
            hasAdvancedBatteryManagement = androidApiLevel >= 31, // Android 12+
            supportsMdnsDiscovery = false,
            hasOxygenOS15Features = oxygenVersion?.let { it >= 15 } ?: false,
            supportsRecentAppsLock = true,
            supportsAccessibilityBypass = androidApiLevel >= 23, // Android 6+
            hasAutoStartManager = true, // All OnePlus devices have this
            hasPowerSaverSettings = true, // All OnePlus devices have this
            supportsAppLocking = true, // All OnePlus devices support recent apps locking
            batteryOptimizationResetRisk = 0.95f, // 95% chance of reset after update
            recommendedConfigSteps = listOf(
                OnePlusConfigurationStepId.BATTERY_OPTIMIZATION,
                OnePlusConfigurationStepId.AUTO_STARTUP,
                OnePlusConfigurationStepId.BACKGROUND_RUNNING,
                OnePlusConfigurationStepId.RECENT_APPS_LOCK
            )
        )
    }
    
    companion object {
        /**
         * Quick check if device is in doze mode (minSdk=26, always available)
         */
        fun isDeviceInDozeMode(context: Context): Boolean {
            return try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isDeviceIdleMode
            } catch (e: Exception) {
                Logger.e(LogTags.BATTERY_OPTIMIZATION, "Error checking doze mode", e)
                false
            }
        }
        
        /**
         * 🔍 Quick OnePlus device detection
         */
        fun isOnePlusDevice(): Boolean {
            val manufacturer = Build.MANUFACTURER.lowercase()
            return manufacturer.contains("oneplus") || manufacturer.contains("oppo")
        }
    }
}

/**
 * Result of battery optimization check with actionable guidance
 */
sealed class BatteryCheckResult {
    data class Optimal(val message: String) : BatteryCheckResult()
    data class NeedsAction(val message: String, val action: Intent?) : BatteryCheckResult()
    data class Warning(val message: String) : BatteryCheckResult()
}


