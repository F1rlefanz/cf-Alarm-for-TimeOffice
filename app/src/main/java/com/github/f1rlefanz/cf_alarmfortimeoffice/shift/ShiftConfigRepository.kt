package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.shiftDataStore: DataStore<Preferences> by preferencesDataStore(name = "shift_prefs")

class ShiftConfigRepository(private val context: Context) {
    
    private val shiftDefinitionsKey = stringPreferencesKey("shift_definitions")
    private val autoAlarmEnabledKey = booleanPreferencesKey("auto_alarm_enabled")
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    suspend fun saveShiftDefinitions(definitions: List<ShiftDefinition>) {
        try {
            val jsonString = json.encodeToString(definitions)
            context.shiftDataStore.edit { preferences ->
                preferences[shiftDefinitionsKey] = jsonString
            }
            Timber.d("Shift definitions saved: ${definitions.size} definitions")
        } catch (e: Exception) {
            Timber.e(e, "Error saving shift definitions")
        }
    }
    
    val shiftDefinitions: Flow<List<ShiftDefinition>> = context.shiftDataStore.data.map { preferences ->
        try {
            val jsonString = preferences[shiftDefinitionsKey]
            val definitions = if (!jsonString.isNullOrEmpty()) {
                try {
                    json.decodeFromString<List<ShiftDefinition>>(jsonString)
                } catch (e: Exception) {
                    Timber.e(e, "Error decoding shift definitions, using defaults")
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            // Always ensure we have the default definitions
            val result = if (definitions.isEmpty()) {
                Timber.d("No valid shift definitions found, using defaults")
                DefaultShiftDefinitions.predefined
            } else {
                // Merge with defaults to ensure all predefined shifts are present
                val existingIds = definitions.map { it.id }.toSet()
                val missingDefaults = DefaultShiftDefinitions.predefined.filter { it.id !in existingIds }
                if (missingDefaults.isNotEmpty()) {
                    Timber.d("Adding ${missingDefaults.size} missing default definitions")
                    definitions + missingDefaults
                } else {
                    definitions
                }
            }
            
            Timber.d("Returning ${result.size} shift definitions")
            result
        } catch (e: Exception) {
            Timber.e(e, "Critical error loading shift definitions, returning defaults")
            DefaultShiftDefinitions.predefined
        }
    }.catch { e ->
        Timber.e(e, "Flow error in shift definitions, emitting defaults")
        emit(DefaultShiftDefinitions.predefined)
    }.distinctUntilChanged()
    
    // Synchronous getter for immediate access
    suspend fun getShiftDefinitions(): List<ShiftDefinition> {
        return shiftDefinitions.first()
    }
    
    suspend fun saveAutoAlarmEnabled(enabled: Boolean) {
        context.shiftDataStore.edit { preferences ->
            preferences[autoAlarmEnabledKey] = enabled
        }
        Timber.d("Auto alarm enabled set to: $enabled")
    }
    
    val autoAlarmEnabled: Flow<Boolean> = context.shiftDataStore.data.map { preferences ->
        preferences[autoAlarmEnabledKey] != false
    }

    suspend fun resetToDefaults() {
        saveShiftDefinitions(DefaultShiftDefinitions.predefined)
        saveAutoAlarmEnabled(true)
        Timber.i("Shift configuration reset to defaults")
    }
}