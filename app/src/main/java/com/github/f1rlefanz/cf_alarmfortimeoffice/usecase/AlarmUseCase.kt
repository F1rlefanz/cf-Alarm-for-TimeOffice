package com.github.f1rlefanz.cf_alarmfortimeoffice.usecase

import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AlarmInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftConfig
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftDefinition
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IAlarmRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmManagerService
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAlarmUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.DateTimeFormats
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.CalendarConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.AlarmConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftRecognitionEngine
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.SafeExecutor
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * UseCase für alle Alarm-bezogenen Operationen - implementiert IAlarmUseCase
 * 
 * REFACTORED:
 * ✅ Implementiert IAlarmUseCase Interface für bessere Testbarkeit
 * ✅ Verwendet Repository-Interfaces statt konkrete Implementierungen
 * ✅ Erweiterte Business Logic für Event-zu-Alarm Transformation
 * ✅ Result-basierte API für konsistente Fehlerbehandlung
 * ✅ Integration mit ShiftRecognitionEngine für intelligente Alarm-Erstellung
 */
class AlarmUseCase(
    private val alarmRepository: IAlarmRepository,
    private val alarmManagerService: AlarmManagerService,
    private val shiftConfigRepository: IShiftConfigRepository,
    private val shiftRecognitionEngine: ShiftRecognitionEngine
) : IAlarmUseCase {
    
    /**
     * PERFORMANCE OPTIMIZATION: Optimized active alarms flow with reduced polling
     */
    override val activeAlarms: Flow<List<AlarmInfo>> = flow {
        while (currentCoroutineContext().isActive) {
            val alarms = alarmRepository.getAllAlarms().getOrNull() ?: emptyList()
            emit(alarms)
            delay(10000) // PERFORMANCE: Reduced polling from 5s to 10s for better performance
        }
    }.distinctUntilChanged { old, new -> 
        // PERFORMANCE: Only emit when alarm list actually changes
        old.size == new.size && old.zip(new).all { (a, b) -> a.id == b.id && a.triggerTime == b.triggerTime }
    }
    
    /**
     * PERFORMANCE OPTIMIZATION: Enhanced alarm creation with atomic clearing and batching
     */
    @Volatile
    private var alarmCreationInProgress = false
    
    override suspend fun createAlarmsFromEvents(
        events: List<CalendarEvent>,
        shiftConfig: ShiftConfig
    ): Result<List<AlarmInfo>> = withContext(Dispatchers.IO) {
        // PERFORMANCE: Prevent concurrent alarm creation
        if (alarmCreationInProgress) {
            Logger.d(LogTags.ALARM, "🔒 BATCH-CREATE: Alarm creation already in progress, skipping duplicate call")
            return@withContext Result.success(emptyList())
        }
        
        alarmCreationInProgress = true
        
        try {
            SafeExecutor.safeExecute("AlarmUseCase.createAlarmsFromEvents") {
                if (!shiftConfig.autoAlarmEnabled) {
                    Logger.d(LogTags.ALARM, "Auto-alarm disabled, not creating alarms")
                    return@safeExecute emptyList()
                }
                
                Logger.d(LogTags.ALARM, "🔄 BATCH-CREATE: Starting batch alarm creation for ${events.size} events")
                
                // ATOMIC CLEARING: Clear existing alarms first (single operation)
                deleteAllAlarms().getOrThrow()
                
                // PERFORMANCE: Get shift matches with optimized recognition
                val shiftMatches = shiftRecognitionEngine.getAllMatchingShifts(events)
                
                // PERFORMANCE: Batch create alarms
                val alarmInfos = mutableListOf<AlarmInfo>()
                
                for (shiftMatch in shiftMatches) {
                    try {
                        val alarmInfo = createAlarmFromShiftMatch(shiftMatch)
                        alarmInfos.add(alarmInfo)
                        
                        // Save alarm in repository
                        alarmRepository.saveAlarm(alarmInfo).getOrThrow()
                        
                        Logger.d(LogTags.ALARM, "✅ BATCH-CREATE: Created alarm for shift: ${shiftMatch.shiftDefinition.name}")
                    } catch (e: Exception) {
                        Logger.e(LogTags.ALARM, "❌ BATCH-CREATE: Error creating alarm for shift: ${shiftMatch.shiftDefinition.name} - ${e.message}")
                        // Continue with other alarms
                    }
                }
                
                Logger.business(LogTags.ALARM, "✅ BATCH-CREATE: Created ${alarmInfos.size} alarms from ${events.size} events")
                alarmInfos
            }
        } finally {
            alarmCreationInProgress = false
        }
    }
    
    override suspend fun saveAlarm(alarmInfo: AlarmInfo): Result<Unit> = 
        alarmRepository.saveAlarm(alarmInfo)
    
    override suspend fun deleteAlarm(alarmId: Int): Result<Unit> = 
        SafeExecutor.safeExecute("AlarmUseCase.deleteAlarm") {
            Logger.d(LogTags.ALARM, "🧹 ATOMIC-SINGLE: Deleting single alarm ID=$alarmId")
            
            // ATOMIC SINGLE: Cancel system alarm first, then repository
            cancelSystemAlarm(alarmId).getOrThrow()
            alarmRepository.deleteAlarm(alarmId).getOrThrow()
            
            Logger.d(LogTags.ALARM, "✅ ATOMIC-SINGLE: Alarm $alarmId deleted successfully")
        }
    
    /**
     * ATOMIC OPERATION: Single-point alarm clearing to prevent redundant operations
     * Coordinates both system alarm cancellation and repository clearing
     */
    @Volatile
    private var clearingInProgress = false
    
    override suspend fun deleteAllAlarms(): Result<Unit> = 
        SafeExecutor.safeExecute("AlarmUseCase.deleteAllAlarms") {
            // ATOMIC CLEARING: Prevent concurrent clearing operations
            if (clearingInProgress) {
                Logger.d(LogTags.ALARM, "🔒 ATOMIC-CLEAR: Clearing already in progress, skipping duplicate call")
                return@safeExecute
            }
            
            clearingInProgress = true
            
            try {
                Logger.d(LogTags.ALARM, "🧹 ATOMIC-CLEAR: Starting atomic alarm clearing operation")
                
                // ATOMIC STEP 1: Cancel system alarms first (prevents ghost alarms)
                Logger.d(LogTags.ALARM, "🧹 ATOMIC-CLEAR: Cancelling system alarms...")
                alarmManagerService.cancelSystemAlarm()
                
                // ATOMIC STEP 2: Clear repository (local storage)
                Logger.d(LogTags.ALARM, "🧹 ATOMIC-CLEAR: Clearing alarm repository...")
                alarmRepository.deleteAllAlarms().getOrThrow()
                
                Logger.business(LogTags.ALARM, "✅ ATOMIC-CLEAR: All alarms cleared successfully (system + repository)")
            } finally {
                clearingInProgress = false
            }
        }
    
    override suspend fun scheduleSystemAlarm(alarmInfo: AlarmInfo): Result<Unit> = 
        SafeExecutor.safeExecute("AlarmUseCase.scheduleSystemAlarm") {
            // Create dummy ShiftMatch for AlarmManagerService compatibility
            val shiftDefinition = ShiftDefinition(
                id = alarmInfo.shiftId,
                name = alarmInfo.shiftName,
                keywords = listOf(),
                alarmTime = LocalTime.of(AlarmConstants.DEFAULT_ALARM_HOUR, AlarmConstants.DEFAULT_ALARM_MINUTE), // Default
                isEnabled = true
            )
            
            val calendarEvent = CalendarEvent(
                id = alarmInfo.id.toString(), // Convert Int to String
                title = alarmInfo.shiftName,
                startTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(alarmInfo.triggerTime),
                    ZoneId.systemDefault()
                ),
                endTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(alarmInfo.triggerTime + CalendarConstants.DEFAULT_EVENT_DURATION_MS), // +1 hour
                    ZoneId.systemDefault()
                ),
                calendarId = ""
            )
            
            val shiftMatch = ShiftMatch(
                shiftDefinition = shiftDefinition,
                calendarEvent = calendarEvent,
                calculatedAlarmTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(alarmInfo.triggerTime),
                    ZoneId.systemDefault()
                )
            )
            
            val config = shiftConfigRepository.getCurrentShiftConfig().getOrThrow()
            alarmManagerService.setAlarmFromShiftMatch(shiftMatch, config.autoAlarmEnabled)
        }
    
    override suspend fun cancelSystemAlarm(alarmId: Int): Result<Unit> = 
        SafeExecutor.safeExecute("AlarmUseCase.cancelSystemAlarm") {
            // For now, cancel all system alarms (AlarmManagerService limitation)
            alarmManagerService.cancelSystemAlarm()
        }
    
    override suspend fun getAllAlarms(): Result<List<AlarmInfo>> = 
        alarmRepository.getAllAlarms()
    
    /**
     * Erstellt AlarmInfo aus ShiftMatch
     */
    private fun createAlarmFromShiftMatch(shiftMatch: ShiftMatch): AlarmInfo {
        val alarmTime = shiftMatch.calculatedAlarmTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        
        return AlarmInfo(
            id = shiftMatch.calendarEvent.id.hashCode(), // Convert String ID to Int
            shiftId = shiftMatch.shiftDefinition.id,
            shiftName = shiftMatch.shiftDefinition.name,
            triggerTime = alarmTime,
            formattedTime = formatAlarmTime(alarmTime)
        )
    }
    
    /**
     * Formatiert Alarm-Zeit für Anzeige - public für ViewModel-Zugriff
     */
    fun formatAlarmTime(timeInMillis: Long): String {
        val instant = java.time.Instant.ofEpochMilli(timeInMillis)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return DateTimeFormatter.ofPattern(DateTimeFormats.STANDARD_DATETIME).format(localDateTime)
    }
    
    // Legacy methods für Kompatibilität mit bestehendem Code
    suspend fun setAlarmsForShift(shift: ShiftInfo): Result<Unit> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("AlarmUseCase.setAlarmsForShift") {
            val config = shiftConfigRepository.getCurrentShiftConfig().getOrThrow()
            if (!config.autoAlarmEnabled) {
                Logger.d(LogTags.ALARM, "Auto-alarm disabled, not setting alarm")
                return@safeExecute
            }
            
            // ATOMIC CLEARING: Use atomic clearing method (single call, no redundancy)
            Logger.d(LogTags.ALARM, "🔄 LEGACY-SHIFT: Clearing existing alarms before setting new one")
            deleteAllAlarms().getOrThrow()
            
            // Create alarm info from shift
            val alarmTime = shift.alarmTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            
            val alarmInfo = AlarmInfo(
                id = shift.id.hashCode(), // Convert String to Int
                shiftId = shift.id,
                shiftName = shift.shiftType.displayName,
                triggerTime = alarmTime,
                formattedTime = formatAlarmTime(alarmTime)
            )
            
            // Save and schedule alarm
            saveAlarm(alarmInfo).getOrThrow()
            scheduleSystemAlarm(alarmInfo).getOrThrow()
            
            Logger.business(LogTags.ALARM, "Alarm set for ${shift.shiftType.displayName} at ${alarmInfo.formattedTime}")
        }
    }
    
    suspend fun cancelAlarm(alarmId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("AlarmUseCase.cancelAlarm") {
            deleteAlarm(alarmId).getOrThrow()
            Logger.business(LogTags.ALARM, "✅ LEGACY-CANCEL: Alarm $alarmId cancelled")
        }
    }
    
    suspend fun cancelAllAlarms(): Result<Unit> = deleteAllAlarms() // ATOMIC: Direct delegation to atomic method
    
    fun getActiveAlarmsFlow(): Flow<List<AlarmInfo>> = activeAlarms
}
