/**
 * OnePlus Configuration Constants
 * 
 * Centralized constants for OnePlus-specific configuration and validation.
 * Eliminates magic numbers and improves maintainability.
 * 
 * @author CF-Alarm Team
 * @since 2025.1.0
 */
package com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.util

/**
 * Confidence thresholds for device validation and heuristics
 */
object OnePlusConfidenceThresholds {
    const val DEVICE_VALIDATION_THRESHOLD = 0.7f        // 70% confidence for OnePlus device detection
    const val HIGH_CONFIDENCE = 0.9f                    // High confidence detection
    const val MEDIUM_CONFIDENCE = 0.7f                  // Medium confidence detection
    const val LOW_CONFIDENCE = 0.5f                     // Low confidence detection
    
    // Heuristic confidence values
    const val AUTO_START_HEURISTIC = 0.8f               // Auto-start detection confidence
    const val BACKGROUND_RUNNING_HEURISTIC = 0.7f       // Background running detection confidence
    const val ENHANCED_OPTIMIZATION_HEURISTIC = 0.6f    // Enhanced optimization detection confidence
}

/**
 * Detection method reliability scores
 */
object OnePlusDetectionReliability {
    const val API_RELIABLE = 0.95f                      // API-based detection reliability
    const val HEURISTIC_USER_CONFIRMED = 0.85f          // Heuristic + user confirmation reliability
    const val USER_CONFIRMED_ONLY = 0.80f               // User confirmation only reliability
}

/**
 * Configuration check intervals and timeouts
 */
object OnePlusTimingConstants {
    const val CHECK_INTERVAL_HOURS = 24L                // Configuration check interval
    const val SETUP_REMINDER_INTERVAL_DAYS = 7L         // Setup reminder interval
    const val NOTIFICATION_TIMEOUT_MILLIS = 5000L       // Notification display timeout
}

/**
 * Device capability thresholds
 */
object OnePlusDeviceThresholds {
    const val MIN_MODEL_FOR_AUTO_START = 7              // OnePlus 7+ supports auto-start
    const val MIN_MODEL_FOR_POWER_SAVER = 5             // OnePlus 5+ has power saver settings
    const val MIN_ANDROID_FOR_ADVANCED_BATTERY = 12     // Android 12+ has advanced battery management
    const val BASE_RESET_RISK = 0.75f                   // Base probability of settings reset
    const val OXYGENOS_15_RESET_BONUS = 0.15f           // Additional reset risk for OxygenOS 15+
    const val NEWER_DEVICE_RESET_BONUS = 0.1f           // Additional reset risk for newer devices
    const val MAX_RESET_RISK = 0.95f                    // Maximum reset risk (95%)
}

/**
 * Success rates based on community research
 */
object OnePlusSuccessRates {
    const val RECENT_APPS_LOCK_SUCCESS_RATE = 0.7f      // 70% success rate from research
    const val BATTERY_OPTIMIZATION_SUCCESS_RATE = 0.95f  // 95% success rate for battery optimization
    const val ENHANCED_OPTIMIZATION_SUCCESS_RATE = 0.85f // 85% success rate for enhanced optimization
}

/**
 * UI and notification constants
 */
object OnePlusUIConstants {
    const val NOTIFICATION_CHANNEL_ID = "oneplus_config_alerts"
    const val NOTIFICATION_ID_CONFIG_RESET = 3001
    const val NOTIFICATION_ID_SETUP_REMINDER = 3002
    
    // Progress and reliability thresholds
    const val RELIABILITY_EXCELLENT_THRESHOLD = 90      // 90%+ reliability is excellent
    const val RELIABILITY_GOOD_THRESHOLD = 75           // 75%+ reliability is good
    const val RELIABILITY_FAIR_THRESHOLD = 60           // 60%+ reliability is fair
}
