package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository

import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api.HueApiClient
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.discovery.OfficialHueDiscoveryService
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository.interfaces.IHueBridgeRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for Hue Bridge operations using OFFICIAL Philips Discovery Methods
 * 
 * REPLACED: Primitive IP scanning approach
 * WITH: Official N-UPnP and mDNS discovery methods
 * 
 * Implements Clean Architecture with Interface-based DI and Logger integration
 */
class HueBridgeRepository(private val context: Context) : IHueBridgeRepository {
    
    companion object {
        private const val APP_NAME = "CFAlarmForTimeOffice"
        private const val CONNECTION_TIMEOUT_MS = 10000L
    }
    
    // API Client for Hue communication
    private val apiClient = HueApiClient()
    
    // Official Discovery Service (replaces primitive IP scanning)
    private val officialDiscoveryService = OfficialHueDiscoveryService(context)
    
    // Current connection state
    private var currentBridgeIp: String? = null
    private var currentUsername: String? = null
    
    override fun getDiscoveryStatus(): Flow<DiscoveryStatus> = 
        officialDiscoveryService.getDiscoveryStatus()
    
    /**
     * IMPROVED: Uses official Philips discovery methods instead of IP scanning
     */
    override suspend fun discoverBridges(): Result<List<HueBridge>> = withContext(Dispatchers.IO) {
        Logger.i(LogTags.HUE_DISCOVERY, "Starting official Hue bridge discovery")
        
        try {
            // Use official discovery service (N-UPnP + mDNS)
            val discoveryResult = officialDiscoveryService.discoverBridges()
            
            if (discoveryResult.isSuccess) {
                val bridges = discoveryResult.getOrNull() ?: emptyList()
                Logger.i(LogTags.HUE_DISCOVERY, "Official discovery completed: ${bridges.size} bridges found")
                
                // Test each discovered bridge for actual connectivity
                val validBridges = bridges.filter { bridge ->
                    val testResult = testBridgeConnection(bridge)
                    testResult.isSuccess && testResult.getOrDefault(false)
                }
                
                Logger.i(LogTags.HUE_DISCOVERY, "Bridge connectivity test: ${validBridges.size}/${bridges.size} bridges reachable")
                Result.success(validBridges)
            } else {
                Logger.w(LogTags.HUE_DISCOVERY, "Official discovery failed", discoveryResult.exceptionOrNull())
                discoveryResult
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_DISCOVERY, "Bridge discovery failed with exception", e)
            Result.failure(e)
        }
    }
    
    override suspend fun testBridgeConnection(bridge: HueBridge): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                val config = apiClient.getBridgeConfig(bridge.internalipaddress)
                Logger.d(LogTags.HUE_BRIDGE, "Bridge ${bridge.internalipaddress} test successful")
                Result.success(true)
            }
        } catch (e: Exception) {
            Logger.d(LogTags.HUE_BRIDGE, "Bridge ${bridge.internalipaddress} test failed: ${e.message}")
            Result.success(false)
        }
    }
    
    override suspend fun connectToBridge(bridge: HueBridge): Result<String> = withContext(Dispatchers.IO) {
        Logger.i(LogTags.HUE_BRIDGE, "Attempting to connect to bridge ${bridge.internalipaddress}")
        
        try {
            val username = apiClient.createUser(bridge.internalipaddress, APP_NAME)
            
            // Store connection details
            currentBridgeIp = bridge.internalipaddress
            currentUsername = username
            
            Logger.i(LogTags.HUE_BRIDGE, "Successfully connected to bridge, username created")
            Result.success(username)
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_BRIDGE, "Failed to connect to bridge", e)
            Result.failure(e)
        }
    }
    
    override fun setUsername(username: String) {
        currentUsername = username
        Logger.d(LogTags.HUE_BRIDGE, "Username set for API requests")
    }
    
    override fun setBridgeIp(bridgeIp: String) {
        currentBridgeIp = bridgeIp
        Logger.d(LogTags.HUE_BRIDGE, "Bridge IP set for API requests")
    }
    
    override fun getCurrentBridgeIp(): String? = currentBridgeIp
    
    override fun getCurrentUsername(): String? = currentUsername
    
    override suspend fun validateConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val bridgeIp = currentBridgeIp
            val username = currentUsername
            
            if (bridgeIp == null || username == null) {
                Logger.w(LogTags.HUE_BRIDGE, "Cannot validate connection: missing bridge IP or username")
                return@withContext Result.success(false)
            }
            
            // Try to get bridge config with authenticated request
            val config = apiClient.getBridgeConfig(bridgeIp, username)
            Logger.d(LogTags.HUE_BRIDGE, "Connection validation successful")
            Result.success(true)
            
        } catch (e: Exception) {
            Logger.w(LogTags.HUE_BRIDGE, "Connection validation failed", e)
            Result.success(false)
        }
    }
}
