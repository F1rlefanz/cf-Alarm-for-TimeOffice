package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Repository for Hue Bridge operations
 */
class HueBridgeRepository {
    
    private var bridgeService: HueApiService? = null
    private var currentBridgeIp: String? = null
    private var currentUsername: String? = null
    
    /**
     * Discover Hue Bridges on the network
     */
    suspend fun discoverBridges(): Result<List<BridgeDiscoveryResponse>> = withContext(Dispatchers.IO) {
        try {
            val discoveryService = HueApiClient.createDiscoveryService()
            val response = discoveryService.discoverBridges()
            
            if (response.isSuccessful) {
                val bridges = response.body() ?: emptyList()
                Timber.d("Discovered ${bridges.size} Hue bridges")
                Result.success(bridges)
            } else {
                Timber.e("Bridge discovery failed: ${response.code()}")
                Result.failure(Exception("Bridge discovery failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error discovering bridges")
            Result.failure(e)
        }
    }
    
    /**
     * Connect to a specific bridge
     */
    fun connectToBridge(bridgeIp: String) {
        currentBridgeIp = bridgeIp
        bridgeService = HueApiClient.createBridgeService(bridgeIp)
        Timber.d("Connected to bridge at $bridgeIp")
    }
    
    /**
     * Create a new user on the bridge
     * User must press the link button on the bridge before calling this
     */
    suspend fun createUser(appName: String, deviceName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            requireNotNull(bridgeService) { "Not connected to bridge" }
            
            val request = CreateUserRequest(
                devicetype = "$appName#$deviceName"
            )
            
            val response = bridgeService!!.createUser(request)
            
            if (response.isSuccessful) {
                val results = response.body() ?: emptyList()
                
                // Check for success response
                val success = results.firstOrNull { it.success != null }
                if (success != null) {
                    currentUsername = success.success!!.username
                    Timber.d("Created user: $currentUsername")
                    Result.success(currentUsername!!)
                } else {
                    // Check for error response
                    val error = results.firstOrNull { it.error != null }
                    val errorMsg = error?.error?.description ?: "Unknown error"
                    Timber.e("User creation failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                Result.failure(Exception("User creation failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating user")
            Result.failure(e)
        }
    }
    
    /**
     * Set username for authenticated requests
     */
    fun setUsername(username: String) {
        currentUsername = username
    }
    
    /**
     * Get bridge configuration
     */
    suspend fun getBridgeConfig(): Result<HueBridgeConfig> = withContext(Dispatchers.IO) {
        try {
            requireNotNull(bridgeService) { "Not connected to bridge" }
            requireNotNull(currentUsername) { "Username not set" }
            
            val response = bridgeService!!.getBridgeConfig(currentUsername!!)
            
            if (response.isSuccessful) {
                val config = response.body()!!
                Timber.d("Bridge config: ${config.name} v${config.swversion}")
                Result.success(config)
            } else {
                Result.failure(Exception("Failed to get bridge config: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting bridge config")
            Result.failure(e)
        }
    }
    
    /**
     * Get all lights
     */
    suspend fun getAllLights(): Result<Map<String, HueLight>> = withContext(Dispatchers.IO) {
        try {
            requireNotNull(bridgeService) { "Not connected to bridge" }
            requireNotNull(currentUsername) { "Username not set" }
            
            val response = bridgeService!!.getAllLights(currentUsername!!)
            
            if (response.isSuccessful) {
                val lights = response.body() ?: emptyMap()
                Timber.d("Found ${lights.size} lights")
                Result.success(lights)
            } else {
                Result.failure(Exception("Failed to get lights: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting lights")
            Result.failure(e)
        }
    }
    
    /**
     * Get all groups
     */
    suspend fun getAllGroups(): Result<Map<String, HueGroup>> = withContext(Dispatchers.IO) {
        try {
            requireNotNull(bridgeService) { "Not connected to bridge" }
            requireNotNull(currentUsername) { "Username not set" }
            
            val response = bridgeService!!.getAllGroups(currentUsername!!)
            
            if (response.isSuccessful) {
                val groups = response.body() ?: emptyMap()
                Timber.d("Found ${groups.size} groups")
                Result.success(groups)
            } else {
                Result.failure(Exception("Failed to get groups: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting groups")
            Result.failure(e)
        }
    }
    
    /**
     * Update light state
     */
    suspend fun updateLightState(lightId: String, state: LightStateUpdate): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireNotNull(bridgeService) { "Not connected to bridge" }
            requireNotNull(currentUsername) { "Username not set" }
            
            val response = bridgeService!!.updateLightState(currentUsername!!, lightId, state)
            
            if (response.isSuccessful) {
                Timber.d("Updated light $lightId state")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update light state: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating light state")
            Result.failure(e)
        }
    }
    
    /**
     * Update group action
     */
    suspend fun updateGroupAction(groupId: String, action: LightStateUpdate): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireNotNull(bridgeService) { "Not connected to bridge" }
            requireNotNull(currentUsername) { "Username not set" }
            
            val response = bridgeService!!.updateGroupAction(currentUsername!!, groupId, action)
            
            if (response.isSuccessful) {
                Timber.d("Updated group $groupId action")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update group action: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating group action")
            Result.failure(e)
        }
    }
    
    fun getCurrentBridgeIp(): String? = currentBridgeIp
    fun getCurrentUsername(): String? = currentUsername
}
