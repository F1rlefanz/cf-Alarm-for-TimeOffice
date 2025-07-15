package com.github.f1rlefanz.cf_alarmfortimeoffice.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PHASE 2: Security Manager for Comprehensive Security Validation
 * 
 * Central coordinator for all security-related checks and validations.
 * Implements defense-in-depth security strategy with multiple validation layers.
 * 
 * ARCHITECTURE:
 * - Single Responsibility: Security orchestration and validation
 * - Composition over Inheritance: Uses specialized security classes
 * - Defensive Programming: Multiple validation layers
 * 
 * SECURITY LAYERS:
 * 1. Runtime Environment Validation
 * 2. App Integrity Checks
 * 3. Root Detection
 * 4. Encryption Capability Validation
 * 5. Network Security Configuration
 * 
 * @author CF-Alarm Development Team
 * @since Security Phase 2
 */
class SecurityManager(private val context: Context) {
    
    /**
     * Performs comprehensive security assessment of the current environment.
     * This is the main entry point for security validation.
     * 
     * @return SecurityAssessment with detailed security status
     */
    suspend fun performSecurityAssessment(): SecurityAssessment = withContext(Dispatchers.IO) {
        Logger.business(LogTags.SECURITY, "🔒 SECURITY-MANAGER: Starting comprehensive security assessment")
        
        val startTime = System.currentTimeMillis()
        
        // Layer 1: Runtime Environment Validation
        val runtimeSecurity = validateRuntimeEnvironment()
        
        // Layer 2: App Integrity Checks
        val appIntegrity = validateAppIntegrity()
        
        // Layer 3: Root Detection
        val rootDetection = RootDetection.performRootDetection(context)
        
        // Layer 4: Encryption Capability Validation
        val encryptionValidation = validateEncryptionCapabilities()
        
        // Layer 5: Network Security Configuration
        val networkSecurity = validateNetworkSecurity()
        
        val assessmentTime = System.currentTimeMillis() - startTime
        
        val assessment = SecurityAssessment(
            runtimeSecurity = runtimeSecurity,
            appIntegrity = appIntegrity,
            rootDetection = rootDetection,
            encryptionValidation = encryptionValidation,
            networkSecurity = networkSecurity,
            overallSecurityLevel = calculateOverallSecurityLevel(
                runtimeSecurity, appIntegrity, rootDetection, 
                encryptionValidation, networkSecurity
            ),
            assessmentTimeMs = assessmentTime
        )
        
        logSecurityAssessment(assessment)
        return@withContext assessment
    }
    
    /**
     * Validates the runtime environment for security threats
     */
    private fun validateRuntimeEnvironment(): RuntimeSecurityValidation {
        val issues = mutableListOf<RuntimeSecurityIssue>()
        
        // Check if app is debuggable in production
        if (isAppDebuggable() && !isDebugBuild()) {
            issues.add(RuntimeSecurityIssue.DEBUGGABLE_IN_PRODUCTION)
            Logger.w(LogTags.SECURITY, "⚠️  RUNTIME-SECURITY: App is debuggable in production build")
        }
        
        // Check Android version security
        if (Build.VERSION.SDK_INT < 26) {
            issues.add(RuntimeSecurityIssue.OUTDATED_ANDROID_VERSION)
            Logger.w(LogTags.SECURITY, "⚠️  RUNTIME-SECURITY: Outdated Android version: ${Build.VERSION.RELEASE}")
        }
        
        // Check for development/emulator environment
        if (isEmulator()) {
            issues.add(RuntimeSecurityIssue.EMULATOR_DETECTED)
            Logger.d(LogTags.SECURITY, "📱 RUNTIME-SECURITY: Emulator environment detected")
        }
        
        return RuntimeSecurityValidation(
            isSecure = issues.isEmpty(),
            issues = issues,
            androidVersion = Build.VERSION.RELEASE,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH
        )
    }
    
    /**
     * Validates app integrity and signing
     */
    private fun validateAppIntegrity(): AppIntegrityValidation {
        val issues = mutableListOf<AppIntegrityIssue>()
        
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            
            // Check if app is signed
            val hasSignature = packageInfo.signatures?.isNotEmpty() == true
            if (!hasSignature) {
                issues.add(AppIntegrityIssue.UNSIGNED_APK)
                Logger.w(LogTags.SECURITY, "⚠️  APP-INTEGRITY: App appears to be unsigned")
            }
            
            // Check for test/debug signatures
            if (isDebugSigned()) {
                issues.add(AppIntegrityIssue.DEBUG_SIGNED)
                Logger.d(LogTags.SECURITY, "🔧 APP-INTEGRITY: App signed with debug certificate")
            }
            
        } catch (e: Exception) {
            issues.add(AppIntegrityIssue.INTEGRITY_CHECK_FAILED)
            Logger.e(LogTags.SECURITY, "❌ APP-INTEGRITY: Failed to validate app integrity", e)
        }
        
        return AppIntegrityValidation(
            isIntact = issues.none { it == AppIntegrityIssue.UNSIGNED_APK || it == AppIntegrityIssue.INTEGRITY_CHECK_FAILED },
            issues = issues
        )
    }
    
    /**
     * Validates encryption capabilities
     */
    private fun validateEncryptionCapabilities(): EncryptionValidation {
        val issues = mutableListOf<EncryptionIssue>()
        
        try {
            // Test EncryptedSharedPreferences creation
            val testPrefs = EncryptedSharedPreferences.create(
                context,
                "security_test",
                androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            // Test encryption/decryption
            testPrefs.edit().putString("test", "value").apply()
            val retrieved = testPrefs.getString("test", null)
            
            if (retrieved != "value") {
                issues.add(EncryptionIssue.ENCRYPTION_TEST_FAILED)
                Logger.w(LogTags.SECURITY, "⚠️  ENCRYPTION: Encryption test failed")
            } else {
                Logger.d(LogTags.SECURITY, "✅ ENCRYPTION: Encryption capabilities validated")
            }
            
            // Clean up test
            testPrefs.edit().clear().apply()
            
        } catch (e: Exception) {
            issues.add(EncryptionIssue.ENCRYPTION_UNAVAILABLE)
            Logger.e(LogTags.SECURITY, "❌ ENCRYPTION: Encryption validation failed", e)
        }
        
        return EncryptionValidation(
            isAvailable = issues.isEmpty(),
            issues = issues
        )
    }
    
    /**
     * Validates network security configuration
     */
    private fun validateNetworkSecurity(): NetworkSecurityValidation {
        val issues = mutableListOf<NetworkSecurityIssue>()
        
        // Check if cleartext traffic is allowed globally
        val applicationInfo = context.applicationInfo
        val usesCleartextTraffic = applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0
        
        if (usesCleartextTraffic) {
            Logger.d(LogTags.SECURITY, "📡 NETWORK-SECURITY: Cleartext traffic allowed (for Philips Hue)")
            // This is expected for Philips Hue integration
        }
        
        return NetworkSecurityValidation(
            isSecure = issues.isEmpty(),
            issues = issues,
            cleartextTrafficAllowed = usesCleartextTraffic
        )
    }
    
    /**
     * Calculates overall security level based on all validations
     */
    private fun calculateOverallSecurityLevel(
        runtime: RuntimeSecurityValidation,
        integrity: AppIntegrityValidation,
        root: RootDetectionResult,
        encryption: EncryptionValidation,
        network: NetworkSecurityValidation
    ): OverallSecurityLevel {
        val criticalIssues = listOf(
            !integrity.isIntact,
            root.riskLevel == RiskLevel.HIGH,
            !encryption.isAvailable
        ).count { it }
        
        val mediumIssues = listOf(
            runtime.issues.isNotEmpty(),
            root.riskLevel == RiskLevel.MEDIUM,
            !network.isSecure
        ).count { it }
        
        return when {
            criticalIssues > 0 -> OverallSecurityLevel.HIGH_RISK
            mediumIssues > 1 -> OverallSecurityLevel.MEDIUM_RISK
            mediumIssues == 1 -> OverallSecurityLevel.LOW_RISK
            else -> OverallSecurityLevel.SECURE
        }
    }
    
    /**
     * Logs comprehensive security assessment results
     */
    private fun logSecurityAssessment(assessment: SecurityAssessment) {
        Logger.business(LogTags.SECURITY, "🏁 SECURITY-ASSESSMENT: Completed in ${assessment.assessmentTimeMs}ms")
        Logger.business(LogTags.SECURITY, "📊 OVERALL-SECURITY: ${assessment.overallSecurityLevel}")
        
        when (assessment.overallSecurityLevel) {
            OverallSecurityLevel.SECURE -> {
                Logger.business(LogTags.SECURITY, "✅ SECURITY-STATUS: All security layers validated successfully")
            }
            OverallSecurityLevel.LOW_RISK -> {
                Logger.w(LogTags.SECURITY, "⚠️  SECURITY-STATUS: Minor security concerns detected")
            }
            OverallSecurityLevel.MEDIUM_RISK -> {
                Logger.w(LogTags.SECURITY, "⚠️  SECURITY-STATUS: Moderate security risks detected")
            }
            OverallSecurityLevel.HIGH_RISK -> {
                Logger.e(LogTags.SECURITY, "🚨 SECURITY-STATUS: HIGH RISK - Critical security issues detected")
            }
        }
        
        // Log specific findings
        if (assessment.rootDetection.isRooted) {
            Logger.w(LogTags.SECURITY, "🔓 ROOT-STATUS: Device is rooted (${assessment.rootDetection.riskLevel})")
        }
        
        if (!assessment.encryptionValidation.isAvailable) {
            Logger.e(LogTags.SECURITY, "🔐 ENCRYPTION-STATUS: Encryption capabilities compromised")
        }
        
        if (!assessment.appIntegrity.isIntact) {
            Logger.e(LogTags.SECURITY, "📦 INTEGRITY-STATUS: App integrity compromised")
        }
    }
    
    // Helper methods
    private fun isAppDebuggable(): Boolean {
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    
    private fun isDebugBuild(): Boolean {
        return com.github.f1rlefanz.cf_alarmfortimeoffice.BuildConfig.DEBUG
    }
    
    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT
    }
    
    private fun isDebugSigned(): Boolean {
        // Simplified check - in a real app you might want to check actual certificate details
        return isDebugBuild()
    }
}

/**
 * Comprehensive security assessment result
 */
data class SecurityAssessment(
    val runtimeSecurity: RuntimeSecurityValidation,
    val appIntegrity: AppIntegrityValidation,
    val rootDetection: RootDetectionResult,
    val encryptionValidation: EncryptionValidation,
    val networkSecurity: NetworkSecurityValidation,
    val overallSecurityLevel: OverallSecurityLevel,
    val assessmentTimeMs: Long
)

/**
 * Runtime security validation result
 */
data class RuntimeSecurityValidation(
    val isSecure: Boolean,
    val issues: List<RuntimeSecurityIssue>,
    val androidVersion: String,
    val securityPatchLevel: String
)

/**
 * App integrity validation result
 */
data class AppIntegrityValidation(
    val isIntact: Boolean,
    val issues: List<AppIntegrityIssue>
)

/**
 * Encryption validation result
 */
data class EncryptionValidation(
    val isAvailable: Boolean,
    val issues: List<EncryptionIssue>
)

/**
 * Network security validation result
 */
data class NetworkSecurityValidation(
    val isSecure: Boolean,
    val issues: List<NetworkSecurityIssue>,
    val cleartextTrafficAllowed: Boolean
)

/**
 * Overall security levels
 */
enum class OverallSecurityLevel {
    SECURE,      // All security measures are optimal
    LOW_RISK,    // Minor issues that don't compromise security
    MEDIUM_RISK, // Some security concerns that should be addressed
    HIGH_RISK    // Critical security issues detected
}

/**
 * Runtime security issues
 */
enum class RuntimeSecurityIssue {
    DEBUGGABLE_IN_PRODUCTION,
    OUTDATED_ANDROID_VERSION,
    EMULATOR_DETECTED
}

/**
 * App integrity issues
 */
enum class AppIntegrityIssue {
    UNSIGNED_APK,
    DEBUG_SIGNED,
    INTEGRITY_CHECK_FAILED
}

/**
 * Encryption issues
 */
enum class EncryptionIssue {
    ENCRYPTION_UNAVAILABLE,
    ENCRYPTION_TEST_FAILED
}

/**
 * Network security issues
 */
enum class NetworkSecurityIssue {
    GLOBAL_CLEARTEXT_ALLOWED,
    INSECURE_NETWORK_CONFIG
}
