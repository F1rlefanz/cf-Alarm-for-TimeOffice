package com.github.f1rlefanz.cf_alarmfortimeoffice.util

import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftDefinition
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * DEBUG HELPER für Manual Alarm Issue
 * 
 * Hilft dabei, das Problem mit der falschen Spätschicht-Zeit (12:00 statt 12:30) zu identifizieren
 */
object ManualAlarmDebugHelper {
    
    fun logShiftDetails(shifts: List<ShiftDefinition>, source: String) {
        Logger.d(LogTags.ALARM, "🔍 DEBUG - Shift definitions from $source:")
        shifts.forEach { shift ->
            Logger.d(LogTags.ALARM, "  ${shift.id}: ${shift.name} -> ${shift.getAlarmTimeFormatted()}")
            if (shift.id == "late_shift" || shift.name.contains("Spät", ignoreCase = true)) {
                Logger.business(LogTags.ALARM, "  🎯 LATE SHIFT FOUND: ${shift.name} has alarmTime = ${shift.alarmTime} (formatted: ${shift.getAlarmTimeFormatted()})")
            }
        }
    }
    
    fun logSelectedShiftDetails(shift: ShiftDefinition?, context: String) {
        shift?.let {
            Logger.business(LogTags.ALARM, "🎯 SELECTED SHIFT in $context: ${it.name} -> alarmTime=${it.alarmTime}, formatted=${it.getAlarmTimeFormatted()}")
        } ?: Logger.w(LogTags.ALARM, "⚠️ NO SHIFT SELECTED in $context")
    }
}
