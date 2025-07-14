package com.github.f1rlefanz.cf_alarmfortimeoffice.usecase

import com.github.f1rlefanz.cf_alarmfortimeoffice.model.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftConfig
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftDefinition
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftType
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftRecognitionEngine
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IShiftUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.SafeExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import java.time.LocalDateTime

/**
 * UseCase für alle Shift-bezogenen Operationen - implementiert IShiftUseCase
 * 
 * REFACTORED:
 * ✅ Implementiert IShiftUseCase Interface für bessere Testbarkeit
 * ✅ Verwendet Repository-Interfaces statt konkrete Implementierungen
 * ✅ Erweiterte Business Logic für Shift-Management
 * ✅ Result-basierte API für konsistente Fehlerbehandlung
 * ✅ Integration mit ShiftRecognitionEngine für intelligente Shift-Erkennung
 */
class ShiftUseCase(
    private val shiftConfigRepository: IShiftConfigRepository,
    private val shiftRecognitionEngine: ShiftRecognitionEngine
) : IShiftUseCase {
    
    override val shiftConfig: Flow<ShiftConfig> = shiftConfigRepository.shiftConfig
    
    override suspend fun saveShiftConfig(config: ShiftConfig): Result<Unit> = 
        shiftConfigRepository.saveShiftConfig(config).also { result ->
            if (result.isSuccess) {
                // CRITICAL FIX: Clear recognition cache when shift config changes
                shiftRecognitionEngine.clearRecognitionCache()
            }
        }
    
    override suspend fun getCurrentShiftConfig(): Result<ShiftConfig> = 
        shiftConfigRepository.getCurrentShiftConfig()
    
    override suspend fun addShiftDefinition(definition: ShiftDefinition): Result<Unit> = 
        SafeExecutor.safeExecute("ShiftUseCase.addShiftDefinition") {
            val currentConfig = shiftConfigRepository.getCurrentShiftConfig().getOrThrow()
            val updatedDefinitions = currentConfig.definitions + definition
            val updatedConfig = currentConfig.copy(definitions = updatedDefinitions)
            
            shiftConfigRepository.saveShiftConfig(updatedConfig).getOrThrow()
            
            // CRITICAL FIX: Clear recognition cache when shift definitions change
            shiftRecognitionEngine.clearRecognitionCache()
        }
    
    override suspend fun updateShiftDefinition(definition: ShiftDefinition): Result<Unit> = 
        SafeExecutor.safeExecute("ShiftUseCase.updateShiftDefinition") {
            val currentConfig = shiftConfigRepository.getCurrentShiftConfig().getOrThrow()
            val updatedDefinitions = currentConfig.definitions.map { existing ->
                if (existing.id == definition.id) definition else existing
            }
            val updatedConfig = currentConfig.copy(definitions = updatedDefinitions)
            
            shiftConfigRepository.saveShiftConfig(updatedConfig).getOrThrow()
            
            // CRITICAL FIX: Clear recognition cache when shift definitions change
            shiftRecognitionEngine.clearRecognitionCache()
        }
    
    override suspend fun deleteShiftDefinition(definitionId: String): Result<Unit> = 
        SafeExecutor.safeExecute("ShiftUseCase.deleteShiftDefinition") {
            val currentConfig = shiftConfigRepository.getCurrentShiftConfig().getOrThrow()
            val updatedDefinitions = currentConfig.definitions.filter { it.id != definitionId }
            val updatedConfig = currentConfig.copy(definitions = updatedDefinitions)
            
            shiftConfigRepository.saveShiftConfig(updatedConfig).getOrThrow()
            
            // CRITICAL FIX: Clear recognition cache when shift definitions change
            shiftRecognitionEngine.clearRecognitionCache()
        }
    
    override suspend fun recognizeShiftsInEvents(events: List<CalendarEvent>): Result<List<ShiftMatch>> = 
        SafeExecutor.safeExecute("ShiftUseCase.recognizeShiftsInEvents") {
            val shiftMatches = shiftRecognitionEngine.getAllMatchingShifts(events)
            Logger.d(LogTags.SHIFT_RECOGNITION, "Recognized ${shiftMatches.size} shifts from ${events.size} events")
            shiftMatches
        }
    
    override suspend fun resetToDefaults(): Result<Unit> = 
        shiftConfigRepository.resetToDefaults().also { result ->
            if (result.isSuccess) {
                // CRITICAL FIX: Clear recognition cache when resetting to defaults
                shiftRecognitionEngine.clearRecognitionCache()
            }
        }
    
    override suspend fun hasValidConfig(): Result<Boolean> = 
        shiftConfigRepository.hasValidConfig()
    
    // Legacy methods für Kompatibilität mit bestehendem Code
    suspend fun getShiftConfig(): Result<ShiftConfig> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("ShiftUseCase.getShiftConfig") {
            shiftConfigRepository.getCurrentShiftConfig().getOrThrow()
        }
    }
    
    suspend fun updateShiftConfig(config: ShiftConfig): Result<Unit> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("ShiftUseCase.updateShiftConfig") {
            shiftConfigRepository.saveShiftConfig(config).getOrThrow()
            
            // CRITICAL FIX: Clear recognition cache when shift config changes
            shiftRecognitionEngine.clearRecognitionCache()
        }
    }
    
    suspend fun recognizeShifts(events: List<CalendarEvent>): Result<List<ShiftInfo>> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("ShiftUseCase.recognizeShifts") {
            val config = shiftConfigRepository.getCurrentShiftConfig().getOrThrow()
            val shifts = mutableListOf<ShiftInfo>()
            
            for (event in events) {
                for (definition in config.definitions) {
                    if (definition.keywords.any { keyword -> 
                        event.title.contains(keyword, ignoreCase = true) 
                    }) {
                        // Berechne Alarm basierend auf ShiftDefinition.alarmTime
                        val alarmDateTime = LocalDateTime.of(
                            event.startTime.toLocalDate(),
                            definition.alarmTime
                        )
                        
                        // Wenn Alarm nach Schichtbeginn liegt, einen Tag früher
                        val adjustedAlarmTime = if (alarmDateTime.isAfter(event.startTime)) {
                            alarmDateTime.minusDays(1)
                        } else {
                            alarmDateTime
                        }
                        
                        shifts.add(ShiftInfo(
                            id = event.id,
                            shiftType = ShiftType(
                                name = definition.id,
                                displayName = definition.name
                            ),
                            startTime = event.startTime,
                            endTime = event.endTime,
                            eventTitle = event.title,
                            alarmTime = adjustedAlarmTime
                        ))
                        break // Nur erste passende Definition verwenden
                    }
                }
            }
            
            Logger.d(LogTags.SHIFT_RECOGNITION, "Recognized ${shifts.size} shifts from ${events.size} events")
            shifts
        }
    }
    
    fun getUpcomingShift(shifts: List<ShiftInfo>): ShiftInfo? {
        val now = LocalDateTime.now()
        return shifts
            .filter { it.startTime.isAfter(now) }
            .minByOrNull { it.startTime }
    }
}
