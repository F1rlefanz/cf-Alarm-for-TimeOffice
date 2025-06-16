package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueConfiguration
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueScheduleRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

/**
 * Repository for persisting Hue configuration and rules
 */
class HueConfigRepository(private val context: Context) {
    
    companion object {
        private val Context.hueDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "hue_config"
        )
        
        private val BRIDGE_ID_KEY = stringPreferencesKey("bridge_id")
        private val BRIDGE_IP_KEY = stringPreferencesKey("bridge_ip")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val RULES_KEY = stringPreferencesKey("rules")
        private val LAST_SYNC_KEY = longPreferencesKey("last_sync")
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Save Hue bridge connection info
     */
    suspend fun saveBridgeInfo(bridgeId: String, bridgeIp: String, username: String) {
        context.hueDataStore.edit { preferences ->
            preferences[BRIDGE_ID_KEY] = bridgeId
            preferences[BRIDGE_IP_KEY] = bridgeIp
            preferences[USERNAME_KEY] = username
            preferences[LAST_SYNC_KEY] = System.currentTimeMillis()
        }
        Timber.d("Saved bridge info: $bridgeId at $bridgeIp")
    }
    
    /**
     * Save schedule rules
     */
    suspend fun saveRules(rules: List<HueScheduleRule>) {
        context.hueDataStore.edit { preferences ->
            preferences[RULES_KEY] = json.encodeToString(rules)
        }
        Timber.d("Saved ${rules.size} Hue rules")
    }
    
    /**
     * Add a new rule
     */
    suspend fun addRule(rule: HueScheduleRule) {
        val currentRules = getRulesOnce()
        val updatedRules = currentRules + rule
        saveRules(updatedRules)
    }
    
    /**
     * Update an existing rule
     */
    suspend fun updateRule(rule: HueScheduleRule) {
        val currentRules = getRulesOnce()
        val updatedRules = currentRules.map { 
            if (it.id == rule.id) rule else it 
        }
        saveRules(updatedRules)
    }
    
    /**
     * Delete a rule
     */
    suspend fun deleteRule(ruleId: String) {
        val currentRules = getRulesOnce()
        val updatedRules = currentRules.filter { it.id != ruleId }
        saveRules(updatedRules)
    }
    
    /**
     * Get current configuration as Flow
     */
    fun getConfiguration(): Flow<HueConfiguration> = context.hueDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading Hue config")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            HueConfiguration(
                bridgeId = preferences[BRIDGE_ID_KEY],
                bridgeIp = preferences[BRIDGE_IP_KEY],
                username = preferences[USERNAME_KEY],
                rules = preferences[RULES_KEY]?.let { 
                    try {
                        json.decodeFromString<List<HueScheduleRule>>(it)
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing rules")
                        emptyList()
                    }
                } ?: emptyList(),
                lastSync = preferences[LAST_SYNC_KEY] ?: 0
            )
        }
    
    /**
     * Get rules once (not as Flow)
     */
    private suspend fun getRulesOnce(): List<HueScheduleRule> {
        return context.hueDataStore.data
            .map { preferences ->
                preferences[RULES_KEY]?.let {
                    try {
                        json.decodeFromString<List<HueScheduleRule>>(it)
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing rules")
                        emptyList()
                    }
                } ?: emptyList()
            }
            .catch { 
                emit(emptyList())
            }
            .first()
    }
    
    /**
     * Clear all Hue configuration
     */
    suspend fun clearConfiguration() {
        context.hueDataStore.edit { preferences ->
            preferences.clear()
        }
        Timber.d("Cleared Hue configuration")
    }
}
