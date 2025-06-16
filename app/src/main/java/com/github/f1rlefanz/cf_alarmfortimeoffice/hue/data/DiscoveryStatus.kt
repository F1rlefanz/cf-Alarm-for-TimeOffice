package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data

/**
 * Represents the current status of the bridge discovery process
 */
data class DiscoveryStatus(
    val currentMethod: DiscoveryMethod = DiscoveryMethod.IDLE,
    val message: String = "",
    val progress: Float? = null, // Optional progress 0.0 to 1.0
    val detailMessage: String? = null // Optional detail message (e.g., "Checking IP 192.168.1.42...")
)

/**
 * Discovery methods
 */
enum class DiscoveryMethod {
    IDLE,
    ONLINE_DISCOVERY,    // Using Philips discovery service
    LOCAL_NETWORK_SCAN,  // Scanning local network
    MDNS_DISCOVERY,      // Using mDNS (future implementation)
    UPNP_DISCOVERY,      // Using UPnP (future implementation)
    TESTING_CONNECTION,  // Testing a specific IP
    COMPLETE,
    FAILED
}
