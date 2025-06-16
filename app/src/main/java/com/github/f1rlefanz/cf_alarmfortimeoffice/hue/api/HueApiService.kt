package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Philips Hue API v1 Interface
 * Note: v2 API requires different endpoints and data structures
 */
interface HueApiService {
    
    // Bridge Discovery & Connection
    @POST("api")
    suspend fun createUser(@Body request: CreateUserRequest): Response<List<CreateUserResponse>>
    
    @GET("api/{username}/config")
    suspend fun getBridgeConfig(@Path("username") username: String): Response<HueBridgeConfig>
    
    // Lights
    @GET("api/{username}/lights")
    suspend fun getAllLights(@Path("username") username: String): Response<Map<String, HueLight>>
    
    @GET("api/{username}/lights/{id}")
    suspend fun getLight(
        @Path("username") username: String,
        @Path("id") lightId: String
    ): Response<HueLight>
    
    @PUT("api/{username}/lights/{id}/state")
    suspend fun updateLightState(
        @Path("username") username: String,
        @Path("id") lightId: String,
        @Body state: LightStateUpdate
    ): Response<List<Map<String, Any>>>
    
    // Groups
    @GET("api/{username}/groups")
    suspend fun getAllGroups(@Path("username") username: String): Response<Map<String, HueGroup>>
    
    @GET("api/{username}/groups/{id}")
    suspend fun getGroup(
        @Path("username") username: String,
        @Path("id") groupId: String
    ): Response<HueGroup>
    
    @PUT("api/{username}/groups/{id}/action")
    suspend fun updateGroupAction(
        @Path("username") username: String,
        @Path("id") groupId: String,
        @Body action: LightStateUpdate
    ): Response<List<Map<String, Any>>>
    
    @POST("api/{username}/groups")
    suspend fun createGroup(
        @Path("username") username: String,
        @Body group: GroupUpdate
    ): Response<List<Map<String, String>>>
}

/**
 * Request/Response models
 */
data class CreateUserRequest(
    val devicetype: String,
    val generateclientkey: Boolean = true
)

data class CreateUserResponse(
    val success: Success? = null,
    val error: Error? = null
) {
    data class Success(
        val username: String,
        val clientkey: String? = null
    )
    
    data class Error(
        val type: Int,
        val address: String,
        val description: String
    )
}

/**
 * Hue Bridge Discovery API
 * Note: The /api/nupnp endpoint has been deprecated. 
 * Alternative: Use mDNS discovery or manual IP entry
 */
interface HueBridgeDiscoveryService {
    // Original endpoint - may return 404
    @GET("api/nupnp")
    suspend fun discoverBridges(): Response<List<BridgeDiscoveryResponse>>
    
    // Alternative endpoint without /api prefix
    @GET("nupnp")
    suspend fun discoverBridgesAlternative(): Response<List<BridgeDiscoveryResponse>>
}
