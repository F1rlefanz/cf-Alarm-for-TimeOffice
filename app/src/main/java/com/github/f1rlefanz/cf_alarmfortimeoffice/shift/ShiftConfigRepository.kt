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
            val userDefinitions = if (!jsonString.isNullOrEmpty()) {
                try {
                    json.decodeFromString<List<ShiftDefinition>>(jsonString)
                } catch (e: Exception) {
                    Timber.e(e, "Error decoding shift definitions")
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            // ALWAYS include default definitions as "examples" that users can modify
            // This ensures they're always available immediately, even on first start
            val defaultsAsExamples = DefaultShiftDefinitions.predefined.map { default ->
                // Check if user has modified this default
                val userVersion = userDefinitions.find { it.id == default.id }
                userVersion ?: default // Use user version if exists, otherwise use default
            }
            
            // Add any custom user definitions that aren't overrides of defaults
            val customDefinitions = userDefinitions.filter { userDef ->
                DefaultShiftDefinitions.predefined.none { it.id == userDef.id }
            }
            
            val result = defaultsAsExamples + customDefinitions
            
            Timber.d("Returning ${result.size} shift definitions (${defaultsAsExamples.size} defaults/modified, ${customDefinitions.size} custom)")
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