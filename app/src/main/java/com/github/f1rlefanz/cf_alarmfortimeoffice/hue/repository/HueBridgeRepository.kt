package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api.HueApiClient
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository.interfaces.IHueBridgeRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Repository for Hue Bridge operations
 * Implements Clean Architecture with Interface-based DI and Logger integration
 */
class HueBridgeRepository : IHueBridgeRepository {
    
    companion object {
        private const val APP_NAME = "CFAlarmForTimeOffice"
        private const val DISCOVERY_TIMEOUT_MS = 30000L
        private const val CONNECTION_TIMEOUT_MS = 10000L
    }
    
    // API Client for Hue communication
    private val apiClient = HueApiClient()
    
    // Current connection state
    private var currentBridgeIp: String? = null
    private var currentUsername: String? = null
    
    // Discovery status flow
    private val _discoveryStatus = MutableSharedFlow<DiscoveryStatus>(replay = 1)
    
    override fun getDiscoveryStatus(): Flow<DiscoveryStatus> = _discoveryStatus.asSharedFlow()
    
    override suspend fun discoverBridges(): Result<List<HueBridge>> = withContext(Dispatchers.IO) {
        Logger.d(LogTags.HUE_DISCOVERY, "Starting bridge discovery")
        
        try {
            // Emit starting status
            _discoveryStatus.emit(DiscoveryStatus(
                method = DiscoveryMethod.ONLINE_DISCOVERY,
                stage = DiscoveryStage.STARTING,
                message = "Starting online bridge discovery..."
            ))
            
            // Try online discovery first
            val onlineResult = discoverBridgesOnline()
            
            if (onlineResult.isSuccess && onlineResult.getOrNull()?.isNotEmpty() == true) {
                Logger.i(LogTags.HUE_DISCOVERY, "Online discovery successful: ${onlineResult.getOrNull()?.size} bridges found")
                
                _discoveryStatus.emit(DiscoveryStatus(
                    method = DiscoveryMethod.ONLINE_DISCOVERY,
                    stage = DiscoveryStage.COMPLETED,
                    message = "Found ${onlineResult.getOrNull()?.size} bridges online",
                    progress = 1.0f,
                    isComplete = true
                ))
                
                return@withContext onlineResult
            }
            
            // Fallback to local network discovery
            Logger.d(LogTags.HUE_DISCOVERY, "Online discovery failed, trying local network scan")
            
            _discoveryStatus.emit(DiscoveryStatus(
                method = DiscoveryMethod.LOCAL_NETWORK,
                stage = DiscoveryStage.STARTING,
                message = "Scanning local network for bridges..."
            ))
            
            val localResult = discoverBridgesLocal()
            
            if (localResult.isSuccess) {
                val bridges = localResult.getOrNull() ?: emptyList()
                Logger.i(LogTags.HUE_DISCOVERY, "Local discovery completed: ${bridges.size} bridges found")
                
                _discoveryStatus.emit(DiscoveryStatus(
                    method = DiscoveryMethod.LOCAL_NETWORK,
                    stage = DiscoveryStage.COMPLETED,
                    message = if (bridges.isNotEmpty()) "Found ${bridges.size} bridges on local network" else "No bridges found",
                    progress = 1.0f,
                    isComplete = true
                ))
                
                return@withContext Result.success(bridges)
            }
            
            // Both methods failed
            Logger.w(LogTags.HUE_DISCOVERY, "Both online and local discovery failed")
            
            _discoveryStatus.emit(DiscoveryStatus(
                method = DiscoveryMethod.LOCAL_NETWORK,
                stage = DiscoveryStage.FAILED,
                message = "No bridges found. Make sure bridge is connected and press link button.",
                isError = true,
                isComplete = true
            ))
            
            return@withContext Result.success(emptyList())
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_DISCOVERY, "Discovery failed with exception", e)
            
            _discoveryStatus.emit(DiscoveryStatus(
                method = DiscoveryMethod.LOCAL_NETWORK,
                stage = DiscoveryStage.FAILED,
                message = "Discovery failed: ${e.message}",
                isError = true,
                isComplete = true
            ))
            
            return@withContext Result.failure(e)
        }
    }
    
    private suspend fun discoverBridgesOnline(): Result<List<HueBridge>> {
        return try {
            // Update progress
            _discoveryStatus.emit(DiscoveryStatus(
                method = DiscoveryMethod.ONLINE_DISCOVERY,
                stage = DiscoveryStage.IN_PROGRESS,
                message = "Contacting Philips discovery service...",
                progress = 0.3f
            ))
            
            val discoveryResponse = apiClient.discoverBridgesOnline()
            val bridges = discoveryResponse.map { response ->
                HueBridge(
                    id = response.id,
                    internalipaddress = response.internalipaddress,
                    name = "Philips Hue Bridge"
                )
            }
            
            // Test each bridge connection
            val validBridges = mutableListOf<HueBridge>()
            bridges.forEachIndexed { index, bridge ->
                _discoveryStatus.emit(DiscoveryStatus(
                    method = DiscoveryMethod.ONLINE_DISCOVERY,
                    stage = DiscoveryStage.TESTING_CONNECTION,
                    message = "Testing bridge ${index + 1}/${bridges.size}...",
                    progress = 0.5f + (index.toFloat() / bridges.size) * 0.4f
                ))
                
                if (coroutineContext.isActive) {
                    val testResult = testBridgeConnection(bridge)
                    if (testResult.isSuccess && testResult.getOrDefault(false)) {
                        validBridges.add(bridge)
                        Logger.d(LogTags.HUE_BRIDGE, "Bridge ${bridge.internalipaddress} connection test passed")
                    } else {
                        Logger.w(LogTags.HUE_BRIDGE, "Bridge ${bridge.internalipaddress} connection test failed")
                    }
                }
            }
            
            Result.success(validBridges)
            
        } catch (e: Exception) {
            Logger.w(LogTags.HUE_DISCOVERY, "Online discovery failed", e)
            Result.failure(e)
        }
    }
    
    private suspend fun discoverBridgesLocal(): Result<List<HueBridge>> {
        return try {
            _discoveryStatus.emit(DiscoveryStatus(
                method = DiscoveryMethod.LOCAL_NETWORK,
                stage = DiscoveryStage.IN_PROGRESS,
                message = "Scanning IP addresses...",
                progress = 0.2f
            ))
            
            // Simple local network scan (this is a placeholder - real implementation would scan network)
            val commonIPs = listOf(
                "192.168.1.2", "192.168.1.3", "192.168.1.4", "192.168.1.5",
                "192.168.0.2", "192.168.0.3", "192.168.0.4", "192.168.0.5"
            )
            
            val foundBridges = mutableListOf<HueBridge>()
            
            commonIPs.forEachIndexed { index, ip ->
                if (coroutineContext.isActive) {
                    _discoveryStatus.emit(DiscoveryStatus(
                        method = DiscoveryMethod.IP_TEST,
                        stage = DiscoveryStage.TESTING_CONNECTION,
                        message = "Testing $ip...",
                        progress = 0.3f + (index.toFloat() / commonIPs.size) * 0.6f
                    ))
                    
                    val bridge = HueBridge(
                        id = "local_$ip",
                        internalipaddress = ip,
                        name = "Local Hue Bridge"
                    )
                    
                    val testResult = testBridgeConnection(bridge)
                    if (testResult.isSuccess && testResult.getOrDefault(false)) {
                        foundBridges.add(bridge)
                        Logger.i(LogTags.HUE_BRIDGE, "Found bridge at $ip")
                    }
                }
            }
            
            Result.success(foundBridges)
            
        } catch (e: Exception) {
            Logger.w(LogTags.HUE_DISCOVERY, "Local discovery failed", e)
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
