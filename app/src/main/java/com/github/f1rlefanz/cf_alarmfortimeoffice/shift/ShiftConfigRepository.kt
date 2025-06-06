package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
            if (jsonString != null) {
                json.decodeFromString<List<ShiftDefinition>>(jsonString)
            } else {
                // Return default definitions if none saved
                DefaultShiftDefinitions.predefined
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading shift definitions, returning defaults")
            DefaultShiftDefinitions.predefined
        }
    }
    
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
