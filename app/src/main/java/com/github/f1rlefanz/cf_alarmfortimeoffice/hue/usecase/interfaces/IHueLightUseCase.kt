package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase.interfaces

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueGroup
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueLight

/**
 * Interface for Hue Light UseCase operations
 * Business logic layer following Clean Architecture
 */
interface IHueLightUseCase {
    
    /**
     * Get all available lights and groups
     * Combines lights and groups for UI display
     */
    suspend fun getAllLightTargets(): Result<LightTargets>
    
    /**
     * Execute light action with business logic validation
     */
    suspend fun executeLightAction(action: LightAction): Result<LightActionResult>
    
    /**
     * Execute multiple light actions as batch
     * Used for rule execution and alarm triggers
     */
    suspend fun executeBatchLightActions(actions: List<LightAction>): Result<BatchActionResult>
    
    /**
     * Test light/group connectivity
     */
    suspend fun testLightConnection(targetId: String, isGroup: Boolean): Result<Boolean>
    
    /**
     * Get current state of all lights for UI updates
     */
    suspend fun refreshLightStates(): Result<LightTargets>
}

/**
 * Combined light targets for UI
 */
data class LightTargets(
    val lights: List<HueLight> = emptyList(),
    val groups: List<HueGroup> = emptyList()
)

/**
 * Light action definition
 */
data class LightAction(
    val targetId: String,
    val isGroup: Boolean,
    val on: Boolean? = null,
    val brightness: Int? = null,
    val hue: Int? = null,
    val saturation: Int? = null,
    val actionDescription: String? = null
)

/**
 * Result of light action execution
 */
data class LightActionResult(
    val success: Boolean,
    val targetId: String,
    val error: String? = null
)

/**
 * Result of batch action execution
 */
data class BatchActionResult(
    val totalActions: Int,
    val successfulActions: Int,
    val failedActions: List<LightActionResult>,
    val overallSuccess: Boolean
)
