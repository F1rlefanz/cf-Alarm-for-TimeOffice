package com.github.f1rlefanz.cf_alarmfortimeoffice.repository

import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AlarmInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IAlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * Repository für Alarm-Daten - implementiert IAlarmRepository Interface
 * 
 * REFACTORED:
 * ✅ Implementiert IAlarmRepository für bessere Testbarkeit
 * ✅ Verwendet AlarmInfo Model statt interne AlarmData
 * ✅ Result-basierte API für bessere Fehlerbehandlung
 * ✅ Vollständige CRUD-Operationen
 * 
 * Verwaltet den Zustand aktiver Alarme mit In-Memory Storage
 * (Kann später auf persistente Speicherung erweitert werden)
 */
class AlarmRepository(
    private val context: Context
) : IAlarmRepository {
    
    // In-memory storage für aktive Alarme
    private val _activeAlarms = MutableStateFlow<List<AlarmInfo>>(emptyList())
    val activeAlarms: Flow<List<AlarmInfo>> = _activeAlarms.asStateFlow()
    
    override suspend fun saveAlarm(alarmInfo: AlarmInfo): Result<Unit> {
        return try {
            val currentAlarms = _activeAlarms.value.toMutableList()
            val existingIndex = currentAlarms.indexOfFirst { it.id == alarmInfo.id }
            
            if (existingIndex != -1) {
                // Update existing alarm
                currentAlarms[existingIndex] = alarmInfo
                Logger.d(LogTags.ALARM, "Alarm updated: ${alarmInfo.id}")
            } else {
                // Add new alarm
                currentAlarms.add(alarmInfo)
                Logger.business(LogTags.ALARM, "Alarm added", alarmInfo.id.toString())
            }
            
            _activeAlarms.value = currentAlarms
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error saving alarm: ${alarmInfo.id}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAllAlarms(): Result<List<AlarmInfo>> {
        return try {
            Result.success(_activeAlarms.value)
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error getting all alarms", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAlarmById(alarmId: Int): Result<AlarmInfo?> {
        return try {
            val alarm = _activeAlarms.value.find { it.id == alarmId }
            Result.success(alarm)
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error getting alarm by ID: $alarmId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteAlarm(alarmId: Int): Result<Unit> {
        return try {
            _activeAlarms.value = _activeAlarms.value.filter { it.id != alarmId }
            Logger.business(LogTags.ALARM, "Alarm removed", alarmId.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error deleting alarm: $alarmId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteAllAlarms(): Result<Unit> {
        return try {
            _activeAlarms.value = emptyList()
            Logger.business(LogTags.ALARM, "All alarms cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error clearing all alarms", e)
            Result.failure(e)
        }
    }
    
    override suspend fun alarmExists(alarmId: Int): Result<Boolean> {
        return try {
            val exists = _activeAlarms.value.any { it.id == alarmId }
            Result.success(exists)
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error checking if alarm exists: $alarmId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Legacy method für Kompatibilität mit bestehendem Code
     * @deprecated Verwende getAllAlarms() stattdessen
     */
    @Deprecated("Use getAllAlarms() instead", ReplaceWith("getAllAlarms()"))
    fun getActiveAlarmCount(): Int = _activeAlarms.value.size
}
