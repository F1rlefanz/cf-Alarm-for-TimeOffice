package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data

import androidx.compose.runtime.Immutable

/**
 * Discovery status for Hue bridge discovery process
 * @Immutable annotation optimizes Compose performance
 */
@Immutable
data class DiscoveryStatus(
    val method: DiscoveryMethod,
    val stage: DiscoveryStage,
    val message: String,
    val progress: Float = 0f, // 0.0 to 1.0
    val isComplete: Boolean = false,
    val isError: Boolean = false
)

/**
 * Discovery method being used
 */
enum class DiscoveryMethod {
    ONLINE_DISCOVERY, // Using Philips cloud service
    LOCAL_NETWORK,    // Scanning local network
    IP_TEST          // Testing specific IP addresses
}

/**
 * Stage of discovery process
 */
enum class DiscoveryStage {
    STARTING,
    IN_PROGRESS,
    TESTING_CONNECTION,
    COMPLETED,
    FAILED
}
