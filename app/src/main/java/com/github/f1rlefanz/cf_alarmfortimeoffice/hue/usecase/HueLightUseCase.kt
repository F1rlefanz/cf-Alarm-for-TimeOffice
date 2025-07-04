package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository.interfaces.IHueLightRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase.interfaces.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UseCase for Hue Light operations
 * Implements business logic layer with validation, batch operations, and error handling
 */
class HueLightUseCase(
    private val lightRepository: IHueLightRepository
) : IHueLightUseCase {
    
    companion object {
        private const val LIGHT_OPERATION_TIMEOUT_MS = 10000L
        private const val BATCH_OPERATION_TIMEOUT_MS = 30000L
        private const val MAX_BATCH_SIZE = 20
        private const val BRIGHTNESS_MIN = 0
        private const val BRIGHTNESS_MAX = 254
        private const val HUE_MIN = 0
        private const val HUE_MAX = 65535
        private const val SATURATION_MIN = 0
        private const val SATURATION_MAX = 254
    }
    
    override suspend fun getAllLightTargets(): Result<LightTargets> {
        Logger.d(LogTags.HUE_USECASE, "Getting all light targets with business logic")
        
        return try {
            coroutineScope {
                // Execute both operations concurrently
                val lightsDeferred = async { lightRepository.getLights() }
                val groupsDeferred = async { lightRepository.getGroups() }
                
                val lightsResult = lightsDeferred.await()
                val groupsResult = groupsDeferred.await()
                
                // Handle partial failures gracefully
                val lights = if (lightsResult.isSuccess) {
                    lightsResult.getOrNull() ?: emptyList()
                } else {
                    Logger.w(LogTags.HUE_USECASE, "Failed to get lights", lightsResult.exceptionOrNull())
                    emptyList()
                }
                
                val groups = if (groupsResult.isSuccess) {
                    groupsResult.getOrNull() ?: emptyList()
                } else {
                    Logger.w(LogTags.HUE_USECASE, "Failed to get groups", groupsResult.exceptionOrNull())
                    emptyList()
                }
                
                // Return combined result
                val lightTargets = LightTargets(
                    lights = lights,
                    groups = groups
                )
                
                Logger.i(LogTags.HUE_USECASE, "Retrieved ${lights.size} lights and ${groups.size} groups")
                Result.success(lightTargets)
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_USECASE, "Failed to get light targets", e)
            Result.failure(Exception("Failed to retrieve lights and groups: ${e.message}", e))
        }
    }
    
    override suspend fun executeLightAction(action: LightAction): Result<LightActionResult> {
        Logger.d(LogTags.HUE_USECASE, "Executing light action for ${action.targetId}")
        
        return try {
            // Validate action parameters
            val validationResult = validateLightAction(action)
            if (validationResult.isFailure) {
                val error = validationResult.exceptionOrNull()?.message ?: "Invalid action"
                return Result.success(
                    LightActionResult(
                        success = false,
                        targetId = action.targetId,
                        error = error
                    )
                )
            }
            
            // Execute action with timeout
            val result = withTimeoutOrNull(LIGHT_OPERATION_TIMEOUT_MS) {
                if (action.isGroup) {
                    lightRepository.controlGroup(
                        groupId = action.targetId,
                        on = action.on,
                        brightness = action.brightness,
                        hue = action.hue,
                        saturation = action.saturation
                    )
                } else {
                    lightRepository.controlLight(
                        lightId = action.targetId,
                        on = action.on,
                        brightness = action.brightness,
                        hue = action.hue,
                        saturation = action.saturation
                    )
                }
            }
            
            if (result == null) {
                Logger.w(LogTags.HUE_USECASE, "Light action timed out for ${action.targetId}")
                return Result.success(
                    LightActionResult(
                        success = false,
                        targetId = action.targetId,
                        error = "Operation timed out"
                    )
                )
            }
            
            val actionResult = if (result.isSuccess) {
                Logger.i(LogTags.HUE_USECASE, "Light action successful for ${action.targetId}")
                LightActionResult(
                    success = true,
                    targetId = action.targetId
                )
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Logger.w(LogTags.HUE_USECASE, "Light action failed for ${action.targetId}: $error")
                LightActionResult(
                    success = false,
                    targetId = action.targetId,
                    error = error
                )
            }
            
            Result.success(actionResult)
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_USECASE, "Light action execution failed", e)
            Result.success(
                LightActionResult(
                    success = false,
                    targetId = action.targetId,
                    error = "Execution failed: ${e.message}"
                )
            )
        }
    }
    
    override suspend fun executeBatchLightActions(actions: List<LightAction>): Result<BatchActionResult> {
        Logger.i(LogTags.HUE_USECASE, "Executing batch light actions: ${actions.size} actions")
        
        return try {
            // Validate batch size
            if (actions.size > MAX_BATCH_SIZE) {
                Logger.w(LogTags.HUE_USECASE, "Batch size ${actions.size} exceeds maximum $MAX_BATCH_SIZE")
                return Result.failure(IllegalArgumentException("Batch size exceeds maximum of $MAX_BATCH_SIZE"))
            }
            
            if (actions.isEmpty()) {
                Logger.w(LogTags.HUE_USECASE, "Empty batch action list provided")
                return Result.success(
                    BatchActionResult(
                        totalActions = 0,
                        successfulActions = 0,
                        failedActions = emptyList(),
                        overallSuccess = true
                    )
                )
            }
            
            // Execute all actions concurrently with overall timeout
            val results = withTimeoutOrNull(BATCH_OPERATION_TIMEOUT_MS) {
                coroutineScope {
                    actions.map { action ->
                        async { executeLightAction(action) }
                    }.awaitAll()
                }
            }
            
            if (results == null) {
                Logger.w(LogTags.HUE_USECASE, "Batch operation timed out")
                return Result.failure(Exception("Batch operation timed out after ${BATCH_OPERATION_TIMEOUT_MS}ms"))
            }
            
            // Process results
            val actionResults = results.mapNotNull { it.getOrNull() }
            val successfulActions = actionResults.count { it.success }
            val failedActions = actionResults.filter { !it.success }
            val overallSuccess = failedActions.isEmpty()
            
            val batchResult = BatchActionResult(
                totalActions = actions.size,
                successfulActions = successfulActions,
                failedActions = failedActions,
                overallSuccess = overallSuccess
            )
            
            Logger.i(
                LogTags.HUE_USECASE, 
                "Batch operation completed: $successfulActions/${actions.size} successful"
            )
            
            Result.success(batchResult)
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_USECASE, "Batch operation failed", e)
            Result.failure(Exception("Batch operation failed: ${e.message}", e))
        }
    }
    
    override suspend fun testLightConnection(targetId: String, isGroup: Boolean): Result<Boolean> {
        Logger.d(LogTags.HUE_USECASE, "Testing connection for ${if (isGroup) "group" else "light"} $targetId")
        
        return try {
            val result = withTimeoutOrNull(LIGHT_OPERATION_TIMEOUT_MS) {
                if (isGroup) {
                    lightRepository.getGroupState(targetId)
                } else {
                    lightRepository.getLightState(targetId)
                }
            }
            
            if (result == null) {
                Logger.w(LogTags.HUE_USECASE, "Connection test timed out for $targetId")
                Result.success(false)
            } else {
                val isConnected = result.isSuccess
                Logger.d(LogTags.HUE_USECASE, "Connection test result for $targetId: $isConnected")
                Result.success(isConnected)
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_USECASE, "Connection test failed for $targetId", e)
            Result.success(false)
        }
    }
    
    override suspend fun refreshLightStates(): Result<LightTargets> {
        Logger.d(LogTags.HUE_USECASE, "Refreshing all light states")
        
        // This is the same as getAllLightTargets, but semantically different
        // Could be extended to include caching logic in the future
        return getAllLightTargets()
    }
    
    /**
     * Validates a light action for business logic compliance
     */
    private fun validateLightAction(action: LightAction): Result<Unit> {
        return try {
            // Validate target ID
            if (action.targetId.isBlank()) {
                return Result.failure(IllegalArgumentException("Target ID cannot be empty"))
            }
            
            // Validate brightness range
            action.brightness?.let { brightness ->
                if (brightness !in BRIGHTNESS_MIN..BRIGHTNESS_MAX) {
                    return Result.failure(
                        IllegalArgumentException("Brightness must be between $BRIGHTNESS_MIN and $BRIGHTNESS_MAX")
                    )
                }
            }
            
            // Validate hue range
            action.hue?.let { hue ->
                if (hue !in HUE_MIN..HUE_MAX) {
                    return Result.failure(
                        IllegalArgumentException("Hue must be between $HUE_MIN and $HUE_MAX")
                    )
                }
            }
            
            // Validate saturation range
            action.saturation?.let { saturation ->
                if (saturation !in SATURATION_MIN..SATURATION_MAX) {
                    return Result.failure(
                        IllegalArgumentException("Saturation must be between $SATURATION_MIN and $SATURATION_MAX")
                    )
                }
            }
            
            // Ensure at least one action is specified
            if (action.on == null && action.brightness == null && action.hue == null && action.saturation == null) {
                return Result.failure(
                    IllegalArgumentException("At least one light property must be specified")
                )
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
