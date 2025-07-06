package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Factory for creating Hue repository instances with proper dependency injection
 * 
 * Provides centralized creation of repositories with correct DataStore configuration
 * following Clean Architecture and DI principles.
 */
object HueRepositoryFactory {
    
    /**
     * DataStore extension for Hue configuration
     * Single instance per application context following DataStore best practices
     */
    private val Context.hueDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "hue_configuration"
    )
    
    /**
     * Creates HueConfigRepository with proper DataStore configuration
     * 
     * @param context Application or Activity context
     * @return Configured HueConfigRepository instance
     */
    fun createHueConfigRepository(context: Context): HueConfigRepository {
        return HueConfigRepository(context.hueDataStore)
    }
    
    /**
     * Creates HueBridgeRepository with context
     * 
     * @param context Application or Activity context for mDNS discovery
     * @return Configured HueBridgeRepository instance
     */
    fun createHueBridgeRepository(context: Context): HueBridgeRepository {
        return HueBridgeRepository(context)
    }
    
    /**
     * Creates HueLightRepository with bridge repository dependency
     * 
     * @param hueBridgeRepository Bridge repository for API communication
     * @return Configured HueLightRepository instance
     */
    fun createHueLightRepository(hueBridgeRepository: HueBridgeRepository): HueLightRepository {
        return HueLightRepository(hueBridgeRepository)
    }
}
