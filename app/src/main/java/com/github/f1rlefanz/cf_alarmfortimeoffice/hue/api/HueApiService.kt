package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API Service for Philips Hue Bridge v1 API
 * 
 * Comprehensive implementation of Hue Bridge API endpoints following REST principles.
 * Covers all essential bridge operations: discovery, authentication, lights, groups, schedules.
 * 
 * API Documentation: https://developers.meethue.com/philips-hue-api/
 * 
 * Implementation principles:
 * - Single Responsibility: Each method handles one specific API operation
 * - Consistent error handling through Retrofit Response wrapper
 * - Type-safe API contracts with data classes
 * - Async operations with suspend functions
 * 
 * @author CF-Alarm Development Team
 * @since Hue Integration v2
 */
interface HueApiService {
    
    // =============================================================================
    // BRIDGE DISCOVERY & AUTHENTICATION
    // =============================================================================
    
    /**
     * Creates new user on Hue Bridge
     * Requires link button to be pressed on bridge within 30 seconds
     * 
     * @param request User creation request with device info
     * @return List containing success/error response
     */
    @POST("api")
    suspend fun createUser(@Body request: CreateUserRequest): Response<List<CreateUserResponse>>
    
    /**
     * Get bridge configuration and status
     * Requires authenticated username
     * 
     * @param username Authenticated bridge username
     * @return Bridge configuration details
     */
    @GET("api/{username}/config")
    suspend fun getBridgeConfig(@Path("username") username: String): Response<HueBridgeConfig>
    
    // =============================================================================
    // LIGHTS API
    // =============================================================================
    
    /**
     * Get all lights connected to the bridge
     * 
     * @param username Authenticated bridge username
     * @return Map of light ID to light details
     */
    @GET("api/{username}/lights")
    suspend fun getAllLights(@Path("username") username: String): Response<Map<String, HueLight>>
    
    /**
     * Get specific light details
     * 
     * @param username Authenticated bridge username  
     * @param lightId Light identifier
     * @return Light details
     */
    @GET("api/{username}/lights/{lightId}")
    suspend fun getLight(
        @Path("username") username: String,
        @Path("lightId") lightId: String
    ): Response<HueLight>
    
    /**
     * Update light state (on/off, brightness, color, etc.)
     * 
     * @param username Authenticated bridge username
     * @param lightId Light identifier
     * @param state New light state
     * @return API response with success/error indicators
     */
    @PUT("api/{username}/lights/{lightId}/state")
    suspend fun updateLightState(
        @Path("username") username: String,
        @Path("lightId") lightId: String,
        @Body state: LightState
    ): Response<List<HueApiResponse>>
    
    /**
     * Search for new lights
     * Bridge will scan for newly connected lights
     * 
     * @param username Authenticated bridge username
     * @return API response
     */
    @POST("api/{username}/lights")
    suspend fun searchForNewLights(@Path("username") username: String): Response<List<HueApiResponse>>
    
    // =============================================================================
    // GROUPS API
    // =============================================================================
    
    /**
     * Get all groups (rooms, zones) from the bridge
     * 
     * @param username Authenticated bridge username
     * @return Map of group ID to group details
     */
    @GET("api/{username}/groups")
    suspend fun getAllGroups(@Path("username") username: String): Response<Map<String, HueGroup>>
    
    /**
     * Get specific group details
     * 
     * @param username Authenticated bridge username
     * @param groupId Group identifier
     * @return Group details
     */
    @GET("api/{username}/groups/{groupId}")
    suspend fun getGroup(
        @Path("username") username: String,
        @Path("groupId") groupId: String
    ): Response<HueGroup>
    
    /**
     * Update group state (affects all lights in group)
     * 
     * @param username Authenticated bridge username
     * @param groupId Group identifier
     * @param action Group action to execute
     * @return API response with success/error indicators
     */
    @PUT("api/{username}/groups/{groupId}/action")
    suspend fun updateGroupAction(
        @Path("username") username: String,
        @Path("groupId") groupId: String,
        @Body action: GroupAction
    ): Response<List<HueApiResponse>>
    
    /**
     * Create new group
     * 
     * @param username Authenticated bridge username
     * @param group Group configuration
     * @return API response with new group ID
     */
    @POST("api/{username}/groups")
    suspend fun createGroup(
        @Path("username") username: String,
        @Body group: CreateGroupRequest
    ): Response<List<HueApiResponse>>
    
    /**
     * Delete group
     * 
     * @param username Authenticated bridge username
     * @param groupId Group identifier
     * @return API response
     */
    @DELETE("api/{username}/groups/{groupId}")
    suspend fun deleteGroup(
        @Path("username") username: String,
        @Path("groupId") groupId: String
    ): Response<List<HueApiResponse>>
    
    // =============================================================================
    // SCHEDULES API
    // =============================================================================
    
    /**
     * Get all schedules from the bridge
     * 
     * @param username Authenticated bridge username
     * @return Map of schedule ID to schedule details
     */
    @GET("api/{username}/schedules")
    suspend fun getAllSchedules(@Path("username") username: String): Response<Map<String, HueApiSchedule>>
    
    /**
     * Get specific schedule details
     * 
     * @param username Authenticated bridge username
     * @param scheduleId Schedule identifier
     * @return Schedule details
     */
    @GET("api/{username}/schedules/{scheduleId}")
    suspend fun getSchedule(
        @Path("username") username: String,
        @Path("scheduleId") scheduleId: String
    ): Response<HueApiSchedule>
    
    /**
     * Create new schedule
     * 
     * @param username Authenticated bridge username
     * @param schedule Schedule configuration
     * @return API response with new schedule ID
     */
    @POST("api/{username}/schedules")
    suspend fun createSchedule(
        @Path("username") username: String,
        @Body schedule: CreateScheduleRequest
    ): Response<List<HueApiResponse>>
    
    /**
     * Update existing schedule
     * 
     * @param username Authenticated bridge username
     * @param scheduleId Schedule identifier
     * @param schedule Updated schedule configuration
     * @return API response
     */
    @PUT("api/{username}/schedules/{scheduleId}")
    suspend fun updateSchedule(
        @Path("username") username: String,
        @Path("scheduleId") scheduleId: String,
        @Body schedule: UpdateScheduleRequest
    ): Response<List<HueApiResponse>>
    
    /**
     * Delete schedule
     * 
     * @param username Authenticated bridge username
     * @param scheduleId Schedule identifier
     * @return API response
     */
    @DELETE("api/{username}/schedules/{scheduleId}")
    suspend fun deleteSchedule(
        @Path("username") username: String,
        @Path("scheduleId") scheduleId: String
    ): Response<List<HueApiResponse>>
}

// =============================================================================
// API REQUEST/RESPONSE DATA CLASSES
// =============================================================================

/**
 * Request body for creating new user on bridge
 */
data class CreateUserRequest(
    val devicetype: String
)

/**
 * Response from user creation API
 */
data class CreateUserResponse(
    val success: UserSuccess? = null,
    val error: ApiError? = null
)

data class UserSuccess(
    val username: String
)

data class ApiError(
    val type: Int,
    val address: String,
    val description: String
)

/**
 * Generic Hue API response format
 */
data class HueApiResponse(
    val success: Map<String, Any>? = null,
    val error: ApiError? = null
)

/**
 * Light state update request
 */
data class LightState(
    val on: Boolean? = null,
    val bri: Int? = null,        // Brightness 1-254
    val hue: Int? = null,        // Hue 0-65535
    val sat: Int? = null,        // Saturation 0-254
    val xy: List<Float>? = null, // CIE color coordinates
    val ct: Int? = null,         // Color temperature 153-500
    val transitiontime: Int? = null // Transition time in 100ms increments
)

/**
 * Group action request
 */
data class GroupAction(
    val on: Boolean? = null,
    val bri: Int? = null,
    val hue: Int? = null,
    val sat: Int? = null,
    val xy: List<Float>? = null,
    val ct: Int? = null,
    val transitiontime: Int? = null
)

/**
 * Create group request
 */
data class CreateGroupRequest(
    val name: String,
    val type: String,           // "Room", "Zone", etc.
    val lights: List<String>,   // List of light IDs
    val roomClass: String? = null // Room class for type "Room"
)

/**
 * Hue API Schedule (different from our internal HueSchedule)
 */
data class HueApiSchedule(
    val name: String,
    val description: String? = null,
    val command: ScheduleCommand,
    val localtime: String,      // ISO 8601 format
    val status: String,         // "enabled" or "disabled"
    val autodelete: Boolean? = null,
    val starttime: String? = null
)

/**
 * Schedule command configuration
 */
data class ScheduleCommand(
    val address: String,        // API endpoint to call
    val method: String,         // HTTP method (PUT, POST, etc.)
    val body: Map<String, Any>  // Request body
)

/**
 * Create schedule request
 */
data class CreateScheduleRequest(
    val name: String,
    val description: String? = null,
    val command: ScheduleCommand,
    val localtime: String,
    val status: String = "enabled",
    val autodelete: Boolean? = null
)

/**
 * Update schedule request
 */
data class UpdateScheduleRequest(
    val name: String? = null,
    val description: String? = null,
    val command: ScheduleCommand? = null,
    val localtime: String? = null,
    val status: String? = null,
    val autodelete: Boolean? = null
)
