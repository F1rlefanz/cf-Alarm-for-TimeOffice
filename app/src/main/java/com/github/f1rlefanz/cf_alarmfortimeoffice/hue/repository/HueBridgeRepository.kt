package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Repository for Hue Bridge operations
 */
class HueBridgeRepository {
    
    companion object {
        private const val APP_NAME = "CFAlarmForTimeOffice"
    }
    
    private var bridgeService: HueApiService? = null
    private var currentBridgeIp: String? = null
    private var currentUsername: String? = null
    
    // Discovery status flow
    private val _discoveryStatus = MutableSharedFlow<DiscoveryStatus>()
    val discoveryStatus: Flow<DiscoveryStatus> = _discoveryStatus.asSharedFlow()
    
    /**
     * Discover Hue Bridges on the network
     * Tries multiple discovery methods
     */
    suspend fun discoverBridges(): Result<List<BridgeDiscoveryResponse>> = withContext(Dispatchers.IO) {
        try {
            Timber.i("HueBridgeRepository: Starte Bridge-Discovery-Prozess...")
            _discoveryStatus.emit(DiscoveryStatus(
                currentMethod = DiscoveryMethod.ONLINE_DISCOVERY,
                message = "Starte Bridge-Suche...",
                progress = 0.1f
            ))
            
            // Method 1: Try Philips discovery service
            Timber.d("HueBridgeRepository: Versuche Online-Discovery über Philips-Dienst...")
            _discoveryStatus.emit(DiscoveryStatus(
                currentMethod = DiscoveryMethod.ONLINE_DISCOVERY,
                message = "Suche über Philips Cloud-Dienst...",
                progress = 0.2f,
                detailMessage = "Verbinde mit meethue.com/api/nupnp"
            ))
            
            val onlineDiscovery = tryOnlineDiscovery()
            if (onlineDiscovery.isSuccess && onlineDiscovery.getOrNull()?.isNotEmpty() == true) {
                val bridges = onlineDiscovery.getOrNull()!!
                Timber.i("HueBridgeRepository: Online-Discovery erfolgreich - ${bridges.size} Bridges gefunden")
                bridges.forEachIndexed { index, bridge ->
                    Timber.d("HueBridgeRepository: Online Bridge [$index]: ID='${bridge.id}', IP='${bridge.internalipaddress}'")
                }
                
                _discoveryStatus.emit(DiscoveryStatus(
                    currentMethod = DiscoveryMethod.COMPLETE,
                    message = "${bridges.size} Bridge(s) gefunden!",
                    progress = 1.0f
                ))
                
                return@withContext onlineDiscovery
            } else {
                Timber.w("HueBridgeRepository: Online-Discovery fehlgeschlagen oder keine Bridges gefunden: ${onlineDiscovery.exceptionOrNull()?.message}")
                _discoveryStatus.emit(DiscoveryStatus(
                    currentMethod = DiscoveryMethod.LOCAL_NETWORK_SCAN,
                    message = "Online-Suche erfolglos, scanne lokales Netzwerk...",
                    progress = 0.4f
                ))
            }
            
            // Method 2: Try local network scan (simplified approach)
            Timber.d("HueBridgeRepository: Versuche lokale Netzwerk-Discovery...")
            val localDiscovery = tryLocalNetworkDiscovery()
            if (localDiscovery.isSuccess && localDiscovery.getOrNull()?.isNotEmpty() == true) {
                val bridges = localDiscovery.getOrNull()!!
                Timber.i("HueBridgeRepository: Lokale Discovery erfolgreich - ${bridges.size} Bridges gefunden")
                bridges.forEachIndexed { index, bridge ->
                    Timber.d("HueBridgeRepository: Lokale Bridge [$index]: ID='${bridge.id}', IP='${bridge.internalipaddress}'")
                }
                
                _discoveryStatus.emit(DiscoveryStatus(
                    currentMethod = DiscoveryMethod.COMPLETE,
                    message = "${bridges.size} Bridge(s) gefunden!",
                    progress = 1.0f
                ))
                
                return@withContext localDiscovery
            } else {
                Timber.w("HueBridgeRepository: Lokale Discovery fehlgeschlagen oder keine Bridges gefunden: ${localDiscovery.exceptionOrNull()?.message}")
            }
            
            // If no bridges found
            Timber.w("HueBridgeRepository: Alle Discovery-Methoden fehlgeschlagen - keine Bridges gefunden")
            _discoveryStatus.emit(DiscoveryStatus(
                currentMethod = DiscoveryMethod.FAILED,
                message = "Keine Bridges gefunden",
                progress = 1.0f,
                detailMessage = "Stellen Sie sicher, dass Sie im selben Netzwerk sind"
            ))
            
            Result.failure(Exception("Keine Hue Bridges gefunden. Stellen Sie sicher, dass Sie im selben Netzwerk sind und die Bridge eingeschaltet ist."))
        } catch (e: Exception) {
            Timber.e(e, "HueBridgeRepository: Kritischer Fehler im Discovery-Prozess")
            _discoveryStatus.emit(DiscoveryStatus(
                currentMethod = DiscoveryMethod.FAILED,
                message = "Fehler bei der Suche: ${e.message}",
                progress = 1.0f
            ))
            Result.failure(e)
        }
    }
    
    private suspend fun tryOnlineDiscovery(): Result<List<BridgeDiscoveryResponse>> {
        return try {
            Timber.d("HueBridgeRepository: Erstelle Discovery Service für Online-Suche...")
            val discoveryService = HueApiClient.createDiscoveryService()
            
            // Try the original endpoint first
            Timber.d("HueBridgeRepository: Versuche ursprünglichen Discovery-Endpoint...")
            _discoveryStatus.emit(DiscoveryStatus(
                currentMethod = DiscoveryMethod.ONLINE_DISCOVERY,
                message = "Verbinde mit Philips Cloud...",
                progress = 0.3f,
                detailMessage = "Versuche primären Endpoint"
            ))
            
            var response = discoveryService.discoverBridges()
            
            // If that fails with 404, try the alternative endpoint
            if (!response.isSuccessful && response.code() == 404) {
                Timber.w("HueBridgeRepository: Ursprünglicher Discovery-Endpoint fehlgeschlagen mit 404, versuche Alternative...")
                _discoveryStatus.emit(DiscoveryStatus(
                    currentMethod = DiscoveryMethod.ONLINE_DISCOVERY,
                    message = "Versuche alternativen Endpoint...",
                    progress = 0.35f,
                    detailMessage = "Primärer Endpoint nicht verfügbar"
                ))
                response = discoveryService.discoverBridgesAlternative()
            }
            
            if (response.isSuccessful) {
                val bridges = response.body() ?: emptyList()
                Timber.d("HueBridgeRepository: Online discovery Response erfolgreich - Raw body size: ${bridges.size}")
                if (bridges.isEmpty()) {
                    Timber.w("HueBridgeRepository: Online discovery Response war erfolgreich, aber Bridge-Liste ist leer")
                } else {
                    bridges.forEach { bridge ->
                        Timber.d("HueBridgeRepository: Online gefundene Bridge - ID: '${bridge.id}', IP: '${bridge.internalipaddress}'")
                    }
                }
                Result.success(bridges)
            } else {
                Timber.e("HueBridgeRepository: Online bridge discovery fehlgeschlagen - HTTP ${response.code()}: ${response.message()}")
                Timber.e("HueBridgeRepository: Response error body: ${response.errorBody()?.string()}")
                Result.failure(Exception("Online discovery failed: HTTP ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "HueBridgeRepository: Exception in online discovery - ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun tryLocalNetworkDiscovery(): Result<List<BridgeDiscoveryResponse>> {
        return try {
            // This is a simplified approach - in production, use proper mDNS/NSD
            Timber.d("HueBridgeRepository: Starte vereinfachte lokale Netzwerk-Discovery...")
            
            val localBridges = mutableListOf<BridgeDiscoveryResponse>()
            
            // Get device's IP to determine subnet
            Timber.d("HueBridgeRepository: Ermittle Geräte-IP für Subnetz-Bestimmung...")
            val wifi = java.net.NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress
            
            if (wifi != null) {
                val subnet = wifi.substringBeforeLast(".")
                Timber.d("HueBridgeRepository: Gefundene Geräte-IP: $wifi, Subnetz: $subnet.*")
                
                _discoveryStatus.emit(DiscoveryStatus(
                    currentMethod = DiscoveryMethod.LOCAL_NETWORK_SCAN,
                    message = "Scanne lokales Netzwerk...",
                    progress = 0.5f,
                    detailMessage = "Suche im Subnetz $subnet.*"
                ))
                
                // Quick scan of common IPs (this is a simplified approach)
                // In production, implement proper network discovery
                Timber.d("HueBridgeRepository: Scanne häufige IPs im Subnetz $subnet.*...")
                
                // Use coroutine scope to allow cancellation
                kotlinx.coroutines.coroutineScope {
                    for (i in 1..254) {
                        // Check if job is still active
                        if (!isActive) {
                            Timber.d("HueBridgeRepository: Discovery abgebrochen")
                            _discoveryStatus.emit(DiscoveryStatus(
                                currentMethod = DiscoveryMethod.FAILED,
                                message = "Suche abgebrochen",
                                progress = 1.0f
                            ))
                            break
                        }
                        
                        val testIp = "$subnet.$i"
                        val progress = 0.5f + (0.4f * (i.toFloat() / 254f))
                        
                        _discoveryStatus.emit(DiscoveryStatus(
                            currentMethod = DiscoveryMethod.TESTING_CONNECTION,
                            message = "Scanne lokales Netzwerk...",
                            progress = progress,
                            detailMessage = "Prüfe IP: $testIp"
                        ))
                        
                        Timber.v("HueBridgeRepository: Teste Bridge-Verbindung zu $testIp...")
                        if (testBridgeConnection(testIp)) {
                            Timber.d("HueBridgeRepository: Bridge-ähnliche Antwort von $testIp erhalten!")
                            localBridges.add(BridgeDiscoveryResponse(
                                id = "local-$i",
                                internalipaddress = testIp
                            ))
                            
                            _discoveryStatus.emit(DiscoveryStatus(
                                currentMethod = DiscoveryMethod.TESTING_CONNECTION,
                                message = "Bridge gefunden!",
                                progress = progress,
                                detailMessage = "Bridge bei $testIp gefunden"
                            ))
                            
                            // Found a bridge, we can stop scanning
                            break
                        }
                    }
                }
            } else {
                Timber.w("HueBridgeRepository: Konnte Geräte-IP nicht ermitteln - Lokale Discovery nicht möglich")
                _discoveryStatus.emit(DiscoveryStatus(
                    currentMethod = DiscoveryMethod.FAILED,
                    message = "Netzwerk-Scan fehlgeschlagen",
                    progress = 1.0f,
                    detailMessage = "Konnte lokale IP-Adresse nicht ermitteln"
                ))
            }
            
            Timber.d("HueBridgeRepository: Lokale Discovery abgeschlossen - ${localBridges.size} potenzielle Bridges gefunden")
            Result.success(localBridges)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.d("HueBridgeRepository: Lokale Discovery wurde abgebrochen")
            _discoveryStatus.emit(DiscoveryStatus(
                currentMethod = DiscoveryMethod.FAILED,
                message = "Suche abgebrochen",
                progress = 1.0f
            ))
            Result.success(emptyList())
        } catch (e: Exception) {
            Timber.e(e, "HueBridgeRepository: Fehler in lokaler Netzwerk-Discovery - ${e.javaClass.simpleName}: ${e.message}")
            _discoveryStatus.emit(DiscoveryStatus(
                currentMethod = DiscoveryMethod.FAILED,
                message = "Netzwerk-Scan fehlgeschlagen",
                progress = 1.0f,
                detailMessage = e.message
            ))
            Result.failure(e)
        }
    }
    
    private suspend fun testBridgeConnection(ip: String): Boolean {
        return try {
            Timber.v("HueBridgeRepository: Teste Bridge-Verbindung zu $ip...")
            val service = HueApiClient.createBridgeService(ip)
            val response = service.getBridgeConfig("test")
            
            // A Hue Bridge can respond with:
            // - 200: Success (with bridge config data)
            // - 401/403: Unauthorized (valid bridge but no auth)
            // Anything else is not a bridge
            val isValidBridgeResponse = when (response.code()) {
                200 -> {
                    // Check if response contains expected bridge fields
                    try {
                        val config = response.body()
                        config != null && !config.bridgeid.isNullOrEmpty()
                    } catch (e: Exception) {
                        false
                    }
                }
                401, 403 -> true
                else -> false
            }
            
            if (isValidBridgeResponse) {
                Timber.d("HueBridgeRepository: $ip reagiert wie eine Hue Bridge (HTTP ${response.code()})")
            } else {
                Timber.v("HueBridgeRepository: $ip reagiert nicht wie eine Hue Bridge (HTTP ${response.code()})")
            }
            isValidBridgeResponse
        } catch (e: Exception) {
            Timber.v("HueBridgeRepository: Verbindung zu $ip fehlgeschlagen: ${e.javaClass.simpleName}")
            false
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
    suspend fun createUser(deviceName: String = android.os.Build.MODEL): Result<String> = withContext(Dispatchers.IO) {
        try {
            requireNotNull(bridgeService) { "Not connected to bridge" }
            
            val request = CreateUserRequest(
                devicetype = "$APP_NAME#$deviceName"
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

}
