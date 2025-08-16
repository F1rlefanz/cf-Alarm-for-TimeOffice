package com.github.f1rlefanz.cf_alarmfortimeoffice.debug

import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftConfig
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * DEBUG UTILITY: Diagnose Shift Config Loading Issues
 */
object ShiftConfigDebugger {
    
    fun compareConfigs(loadedConfig: ShiftConfig?, context: String) {
        val defaultConfig = ShiftConfig.getDefaultConfig()
        
        Logger.business(LogTags.SHIFT_CONFIG, "🔍 CONFIG COMPARISON in $context:")
        Logger.business(LogTags.SHIFT_CONFIG, "   Default Config Definitions: ${defaultConfig.definitions.size}")
        Logger.business(LogTags.SHIFT_CONFIG, "   Loaded Config Definitions: ${loadedConfig?.definitions?.size ?: "NULL"}")
        
        // Compare specific shift times
        val defaultLateShift = defaultConfig.definitions.find { it.id == "late_shift" }
        val loadedLateShift = loadedConfig?.definitions?.find { it.id == "late_shift" }
        
        Logger.business(LogTags.SHIFT_CONFIG, "   DEFAULT Spätschicht: ${defaultLateShift?.alarmTime}")
        Logger.business(LogTags.SHIFT_CONFIG, "   LOADED Spätschicht: ${loadedLateShift?.alarmTime}")
        
        if (defaultLateShift?.alarmTime != loadedLateShift?.alarmTime) {
            Logger.w(LogTags.SHIFT_CONFIG, "🚨 MISMATCH: Different times for Spätschicht!")
        }
        
        // Show all loaded definitions
        loadedConfig?.definitions?.forEach { def ->
            Logger.business(LogTags.SHIFT_CONFIG, "   LOADED: ${def.id} -> ${def.name} @ ${def.alarmTime}")
        }
    }
}
