package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.AppError
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.SafeExecutor
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftConfig
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IShiftConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

// Extension property for Context to create DataStore
private val Context.shiftDataStore: DataStore<Preferences> by preferencesDataStore(name = "shift_prefs")

/**
 * ShiftConfigRepository - implementiert IShiftConfigRepository Interface
 * 
 * REFACTORED:
 * ✅ Implementiert IShiftConfigRepository für bessere Testbarkeit
 * ✅ Result-basierte API für konsistente Fehlerbehandlung
 * ✅ Flow-basierte reaktive Datenbeobachtung
 * ✅ Vollständige CRUD-Operationen mit Validierung
 * 
 * Verwaltet Schicht-Konfigurationen mit DataStore Preferences
 */
class ShiftConfigRepository(
    private val context: Context
) : IShiftConfigRepository {

    private val dataStore = context.shiftDataStore
    private val shiftConfigKey = stringPreferencesKey("shift_config")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val shiftConfig: Flow<ShiftConfig> = dataStore.data.map { preferences ->
        val jsonString = preferences[shiftConfigKey]
        if (jsonString != null) {
            try {
                json.decodeFromString<ShiftConfig>(jsonString)
            } catch (e: SerializationException) {
                Logger.e(LogTags.SHIFT_CONFIG, "Error decoding shift config from flow, returning default", e)
                ShiftConfig.getDefaultConfig()
            }
        } else {
            ShiftConfig.getDefaultConfig()
        }
    }

    override suspend fun saveShiftConfig(config: ShiftConfig): Result<Unit> = 
        SafeExecutor.safeExecute("ShiftConfigRepository.saveShiftConfig") {
            val jsonString = try {
                json.encodeToString(config)
            } catch (e: SerializationException) {
                throw AppError.DataStoreError(
                    message = "Fehler beim Serialisieren der Schicht-Konfiguration",
                    cause = e
                )
            }
            
            dataStore.edit { preferences ->
                preferences[shiftConfigKey] = jsonString
            }
            Logger.d(LogTags.SHIFT_CONFIG, "Shift config saved with ${config.definitions.size} definitions")
        }

    override suspend fun getCurrentShiftConfig(): Result<ShiftConfig> = 
        SafeExecutor.safeExecute("ShiftConfigRepository.getCurrentShiftConfig") {
            val preferences = dataStore.data.first()
            val jsonString = preferences[shiftConfigKey]
            if (jsonString != null) {
                try {
                    json.decodeFromString<ShiftConfig>(jsonString)
                } catch (e: SerializationException) {
                    Logger.e(LogTags.SHIFT_CONFIG, "Error decoding shift config, returning default", e)
                    ShiftConfig.getDefaultConfig()
                }
            } else {
                ShiftConfig.getDefaultConfig()
            }
        }
    
    override suspend fun resetToDefaults(): Result<Unit> = 
        SafeExecutor.safeExecute("ShiftConfigRepository.resetToDefaults") {
            val defaultConfig = ShiftConfig.getDefaultConfig()
            val jsonString = json.encodeToString(defaultConfig)
            
            dataStore.edit { preferences ->
                preferences[shiftConfigKey] = jsonString
            }
            Logger.d(LogTags.SHIFT_CONFIG, "Shift config reset to defaults")
        }
    
    override suspend fun hasValidConfig(): Result<Boolean> = 
        SafeExecutor.safeExecute("ShiftConfigRepository.hasValidConfig") {
            val config = getCurrentShiftConfig().getOrElse { 
                return@safeExecute false
            }
            
            // Validierung: Mindestens eine Schichtdefinition mit gültigem Namen
            config.definitions.isNotEmpty() && 
            config.definitions.any { it.name.isNotBlank() }
        }
}
