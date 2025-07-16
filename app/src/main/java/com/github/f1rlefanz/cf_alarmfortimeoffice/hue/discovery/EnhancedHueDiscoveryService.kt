package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.discovery

import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api.HueApiClientV2
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.DiscoveryMethod
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.DiscoveryStatus
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueBridge
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.security.HueBridgeSecurityValidator
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enhanced Hue Bridge Discovery Service (2025 Edition)
 * 
 * Implementiert bleeding-edge Discovery mit HTTPS-First Ansatz:
 * 1. N-UPnP Discovery (HTTPS) - Primary method
 * 2. mDNS Discovery (_hue._tcp.local) - Modern fallback
 * 3. Protocol Capability Detection für jede gefundene Bridge
 * 4. Security Validation (RFC 1918 Private Networks)
 * 
 * Key Improvements:
 * - HTTPS-First Protocol Detection
 * - Bridge Capability Caching
 * - Enhanced Security Validation
 * - Modern Async Discovery Pattern
 */
class EnhancedHueDiscoveryService(private val context: Context) {
    
    companion object {
        private const val TOTAL_DISCOVERY_TIMEOUT_MS = 45000L // 45 seconds total
        private const val NUPNP_TIMEOUT_MS = 15000L // 15 seconds for N-UPnP
        private const val MDNS_TIMEOUT_MS = 25000L // 25 seconds for mDNS
        private const val CONNECTIVITY_TEST_TIMEOUT_MS = 10000L // 10 seconds per bridge
    }
    
    private val apiClientV2 = HueApiClientV2()
    private val nUpnpService = HueNUpnpDiscoveryService()
    private val mdnsService = HueMdnsDiscoveryService(context)
    
    // Discovery status flow
    private val _discoveryStatus = MutableSharedFlow<DiscoveryStatus>(replay = 1)
    
    fun getDiscoveryStatus(): Flow<DiscoveryStatus> = _discoveryStatus.asSharedFlow()
    
    /**
     * Enhanced Bridge Discovery mit HTTPS-First Protocol Detection
     * 
     * Discovery Workflow:
     * 1. N-UPnP Discovery (HTTPS) - Schnell und zuverlässig
     * 2. mDNS Discovery - Lokale Netzwerk-Suche
     * 3. Security Validation - Nur private IPs
     * 4. Protocol Capability Detection - HTTPS/HTTP Support testen
     * 5. Bridge Configuration Abruf - Metadata sammeln
     */
    suspend fun discoverBridgesEnhanced(): Result<List<HueBridge>> = withContext(Dispatchers.IO) {
        Logger.business(LogTags.HUE_DISCOVERY, "🚀 Starting ENHANCED bridge discovery with HTTPS-first protocol detection")
        
        try {
            _discoveryStatus.emit(DiscoveryStatus.STARTING)
            
            val discoveredBridges = mutableListOf<HueBridge>()
            val processedIPs = mutableSetOf<String>()
            
            // === PHASE 1: N-UPnP Discovery (HTTPS) ===
            Logger.i(LogTags.HUE_DISCOVERY, "📡 PHASE 1: N-UPnP discovery via HTTPS")
            _discoveryStatus.emit(DiscoveryStatus.N_UPNP_SEARCH)
            
            val nUpnpResult = withTimeoutOrNull(NUPNP_TIMEOUT_MS) {
                async { runNUpnpDiscovery() }
            }?.await()
            
            if (nUpnpResult != null && nUpnpResult.isSuccess) {
                val nUpnpBridges = nUpnpResult.getOrThrow()
                Logger.business(LogTags.HUE_DISCOVERY, "✅ N-UPnP found ${nUpnpBridges.size} bridges")
                
                discoveredBridges.addAll(nUpnpBridges)
                processedIPs.addAll(nUpnpBridges.map { it.ipAddress })
            } else {
                Logger.w(LogTags.HUE_DISCOVERY, "⚠️ N-UPnP discovery failed or timed out")
            }
            
            // === PHASE 2: mDNS Discovery (Local Network) ===
            Logger.i(LogTags.HUE_DISCOVERY, "📡 PHASE 2: mDNS discovery for local network")
            _discoveryStatus.emit(DiscoveryStatus.MDNS_SEARCH)
            
            val mdnsResult = withTimeoutOrNull(MDNS_TIMEOUT_MS) {
                async { runMdnsDiscovery() }
            }?.await()
            
            if (mdnsResult != null && mdnsResult.isSuccess) {
                val mdnsBridges = mdnsResult.getOrThrow()
                val newMdnsBridges = mdnsBridges.filter { bridge ->
                    !processedIPs.contains(bridge.ipAddress)
                }
                
                Logger.business(LogTags.HUE_DISCOVERY, "✅ mDNS found ${newMdnsBridges.size} additional bridges")
                discoveredBridges.addAll(newMdnsBridges)
                processedIPs.addAll(newMdnsBridges.map { it.ipAddress })
            } else {
                Logger.w(LogTags.HUE_DISCOVERY, "⚠️ mDNS discovery failed or timed out")
            }
            
            // === PHASE 3: Enhanced Protocol Detection & Validation ===
            Logger.business(LogTags.HUE_DISCOVERY, "🔍 PHASE 3: Running enhanced validation and protocol detection")
            _discoveryStatus.emit(DiscoveryStatus.VALIDATING)
            
            val enhancedBridges = enhanceBridgesWithProtocolDetection(discoveredBridges)
            
            _discoveryStatus.emit(DiscoveryStatus.COMPLETED)
            
            Logger.business(LogTags.HUE_DISCOVERY, "🎯 ENHANCED discovery completed: ${enhancedBridges.size} verified bridges")
            enhancedBridges.forEach { bridge ->
                Logger.business(LogTags.HUE_DISCOVERY, "  📍 Bridge: ${bridge.ipAddress} | Protocol: ${bridge.preferredProtocol?.uppercase() ?: "UNKNOWN"} | Reachable: ${bridge.isReachable}")
            }
            
            return@withContext Result.success(enhancedBridges)
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_DISCOVERY, "❌ Enhanced bridge discovery failed", e)
            _discoveryStatus.emit(DiscoveryStatus.ERROR)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * N-UPnP Discovery über HTTPS API
     */
    private suspend fun runNUpnpDiscovery(): Result<List<HueBridge>> = withContext(Dispatchers.IO) {
        try {
            val bridgeResponses = apiClientV2.discoverBridgesOnline()
            val bridges = bridgeResponses.map { response ->
                HueBridge(
                    id = response.id,
                    ipAddress = response.internalipaddress,
                    port = response.port ?: 80,
                    macAddress = null,
                    name = "Hue Bridge",
                    discoveryMethod = DiscoveryMethod.N_UPNP,
                    isReachable = false, // Wird in Phase 3 getestet
                    preferredProtocol = null // Wird in Phase 3 bestimmt
                )
            }
            
            Logger.i(LogTags.HUE_DISCOVERY, "N-UPnP discovery successful: ${bridges.size} bridges found")
            return@withContext Result.success(bridges)
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_DISCOVERY, "N-UPnP discovery failed", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * mDNS Discovery für lokales Netzwerk
     */
    private suspend fun runMdnsDiscovery(): Result<List<HueBridge>> = withContext(Dispatchers.IO) {
        try {
            val mdnsResult = mdnsService.discoverBridges()
            if (mdnsResult.isSuccess) {
                val bridges = mdnsResult.getOrThrow()
                Logger.i(LogTags.HUE_DISCOVERY, "mDNS discovery successful: ${bridges.size} bridges found")
                return@withContext Result.success(bridges)
            } else {
                throw mdnsResult.exceptionOrNull() ?: Exception("mDNS discovery failed")
            }
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_DISCOVERY, "mDNS discovery failed", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Enhanced Bridge Validation mit Protocol Detection
     * 
     * Für jede gefundene Bridge:
     * 1. Security Validation (RFC 1918 Private Networks)
     * 2. HTTPS/HTTP Capability Detection
     * 3. Bridge Configuration Abruf
     * 4. Reachability Test
     */
    private suspend fun enhanceBridgesWithProtocolDetection(bridges: List<HueBridge>): List<HueBridge> = withContext(Dispatchers.IO) {
        Logger.i(LogTags.HUE_PROTOCOL, "🔍 Enhancing ${bridges.size} bridges with protocol detection")
        
        val enhancedBridges = bridges.mapNotNull { bridge ->
            try {
                // === SECURITY VALIDATION ===
                if (!HueBridgeSecurityValidator.isPrivateNetworkAddress(bridge.ipAddress)) {
                    Logger.w(LogTags.HUE_SECURITY, "🚫 Bridge ${bridge.ipAddress} rejected: Not in private network")
                    return@mapNotNull null
                }
                
                if (!HueBridgeSecurityValidator.validateBridgeHostname(bridge.ipAddress)) {
                    Logger.w(LogTags.HUE_SECURITY, "🚫 Bridge ${bridge.ipAddress} rejected: Invalid hostname pattern")
                    return@mapNotNull null
                }
                
                Logger.d(LogTags.HUE_SECURITY, "✅ Bridge ${bridge.ipAddress} passed security validation")
                
                // === PROTOCOL CAPABILITY DETECTION ===
                val protocolTest = withTimeoutOrNull(CONNECTIVITY_TEST_TIMEOUT_MS) {
                    apiClientV2.testBridgeConnectivity(bridge.ipAddress)
                }
                
                if (protocolTest != null) {
                    // Bridge ist erreichbar - erweiterte Informationen sammeln
                    val enhancedBridge = bridge.copy(
                        isReachable = true,
                        preferredProtocol = protocolTest,
                        lastSeen = System.currentTimeMillis()
                    )
                    
                    Logger.business(LogTags.HUE_PROTOCOL, "✅ Bridge ${bridge.ipAddress} enhanced: ${protocolTest.uppercase()} protocol")
                    enhancedBridge
                } else {
                    // Bridge nicht erreichbar - trotzdem behalten für spätere Versuche
                    val unreachableBridge = bridge.copy(
                        isReachable = false,
                        preferredProtocol = null,
                        lastSeen = System.currentTimeMillis()
                    )
                    
                    Logger.w(LogTags.HUE_PROTOCOL, "⚠️ Bridge ${bridge.ipAddress} unreachable - keeping for later retry")
                    unreachableBridge
                }
                
            } catch (e: Exception) {
                Logger.e(LogTags.HUE_PROTOCOL, "❌ Failed to enhance bridge ${bridge.ipAddress}", e)
                null
            }
        }
        
        Logger.business(LogTags.HUE_PROTOCOL, "🎯 Enhanced ${enhancedBridges.size}/${bridges.size} bridges successfully")
        return@withContext enhancedBridges
    }
    
    /**
     * Legacy compatibility method - calls enhanced discovery
     */
    suspend fun discoverBridges(): Result<List<HueBridge>> {
        return discoverBridgesEnhanced()
    }
    
    /**
     * Quick connectivity test für eine spezifische Bridge
     */
    suspend fun testBridgeConnectivity(bridgeIp: String): String? {
        return if (HueBridgeSecurityValidator.isPrivateNetworkAddress(bridgeIp)) {
            apiClientV2.testBridgeConnectivity(bridgeIp)
        } else {
            Logger.w(LogTags.HUE_SECURITY, "Bridge connectivity test rejected: $bridgeIp not in private network")
            null
        }
    }
}
