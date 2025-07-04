package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueSchedule
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository.interfaces.IHueConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase.interfaces.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import java.time.LocalTime
import java.util.*
import kotlin.math.abs

/**
 * UseCase for Hue Rule operations with shift integration
 * Implements business logic layer with validation and rule engine
 */
class HueRuleUseCase(
    private val configRepository: IHueConfigRepository,
    private val lightUseCase: IHueLightUseCase
) : IHueRuleUseCase {
    
    companion object {
        private const val MAX_RULES_PER_SHIFT = 10
        private const val MIN_RULE_NAME_LENGTH = 3
        private const val MAX_RULE_NAME_LENGTH = 50
        private const val MIN_TIME_OFFSET_MINUTES = -60
        private const val MAX_TIME_OFFSET_MINUTES = 60
    }
    
    override suspend fun getAllRules(): Result<List<HueSchedule>> {
        Logger.d(LogTags.HUE_USECASE, "Getting all schedule rules")
        
        return try {
            val rulesResult = configRepository.getScheduleRules()
            
            if (rulesResult.isSuccess) {
                val rules = rulesResult.getOrNull() ?: emptyList()
                Logger.i(LogTags.HUE_USECASE, "Retrieved ${rules.size} schedule rules")
                Result.success(rules)
            } else {
                Logger.w(LogTags.HUE_USECASE, "Failed to get schedule rules", rulesResult.exceptionOrNull())
                rulesResult
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_USECASE, "Failed to get all rules", e)
            Result.failure(Exception("Failed to retrieve schedule rules: ${e.message}", e))
        }
    }
    
    override suspend fun createRule(rule: HueSchedule): Result<HueSchedule> {
        Logger.i(LogTags.HUE_USECASE, "Creating new schedule rule: ${rule.name}")
        
        return try {
            // Validate rule
            val validationResult = validateRule(rule)
            if (validationResult.isFailure) {
                Logger.w(LogTags.HUE_USECASE, "Rule validation failed for ${rule.name}")
                return Result.failure(validationResult.exceptionOrNull() ?: Exception("Validation failed"))
            }
            
            // Check for duplicate IDs
            val existingRules = configRepository.getScheduleRules().getOrNull() ?: emptyList()
            if (existingRules.any { it.id == rule.id }) {
                Logger.w(LogTags.HUE_USECASE, "Rule with ID ${rule.id} already exists")
                return Result.failure(IllegalArgumentException("Rule with ID ${rule.id} already exists"))
            }
            
            // Check rule count limit per shift
            val shiftRuleCount = existingRules.count { it.shiftPattern == rule.shiftPattern }
            if (shiftRuleCount >= MAX_RULES_PER_SHIFT) {
                Logger.w(LogTags.HUE_USECASE, "Maximum rules per shift exceeded for ${rule.shiftPattern}")
                return Result.failure(
                    IllegalArgumentException("Maximum of $MAX_RULES_PER_SHIFT rules per shift pattern allowed")
                )
            }
            
            // Create rule with generated ID if needed
            val ruleToSave = if (rule.id.isBlank()) {
                rule.copy(id = generateRuleId())
            } else {
                rule
            }
            
            // Save rule
            val saveResult = configRepository.saveScheduleRule(ruleToSave)
            
            if (saveResult.isSuccess) {
                Logger.i(LogTags.HUE_USECASE, "Successfully created rule: ${ruleToSave.id}")
                Result.success(ruleToSave)
            } else {
                Logger.w(LogTags.HUE_USECASE, "Failed to save rule: ${ruleToSave.id}", saveResult.exceptionOrNull())
                Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save rule"))
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_USECASE, "Failed to create rule", e)
            Result.failure(Exception("Failed to create rule: ${e.message}", e))
        }
    }
    
    // Weitere Methoden folgen...
    override suspend fun updateRule(rule: HueSchedule): Result<HueSchedule> = TODO()
    override suspend fun deleteRule(ruleId: String): Result<Unit> = TODO()
    override suspend fun getRule(ruleId: String): Result<HueSchedule> = TODO()
    override suspend fun findApplicableRules(shift: ShiftMatch, currentTime: LocalTime): Result<List<HueSchedule>> = TODO()
    override suspend fun executeRulesForAlarm(shift: ShiftMatch, alarmTime: LocalTime): Result<RuleExecutionResult> = TODO()
    override suspend fun validateRule(rule: HueSchedule): Result<RuleValidationResult> = TODO()
    override suspend fun testRuleExecution(rule: HueSchedule): Result<List<LightAction>> = TODO()
    
    /**
     * Generates a unique rule ID
     */
    private fun generateRuleId(): String {
        return "rule_${UUID.randomUUID().toString().take(8)}_${System.currentTimeMillis()}"
    }
}
