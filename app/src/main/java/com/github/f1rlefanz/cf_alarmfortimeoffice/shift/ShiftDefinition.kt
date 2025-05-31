package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import kotlinx.serialization.Serializable
import java.time.LocalTime

@Serializable
data class ShiftDefinition(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val alarmTime: String, // Format: "HH:mm"
    val isEnabled: Boolean = true
) {
    fun getAlarmLocalTime(): LocalTime = LocalTime.parse(alarmTime)
    
    fun matchesCalendarEntry(title: String): Boolean {
        return isEnabled && keywords.any { keyword ->
            title.contains(keyword, ignoreCase = true)
        }
    }
}

// Predefined shift definitions based on the original Pi config
object DefaultShiftDefinitions {
    val predefined = listOf(
        ShiftDefinition("F", "Frühdienst", listOf("F", "IMCF"), "05:30"),
        ShiftDefinition("S", "Spätdienst", listOf("S", "IMCS"), "12:30"),
        ShiftDefinition("N", "Nachtdienst", listOf("N", "IMCN"), "19:45"),
        ShiftDefinition("IMCZ", "IMC Zwischen", listOf("IMCZ"), "07:00"),
        ShiftDefinition("Z02", "Zwischendienst 02", listOf("Z02"), "06:30"),
        ShiftDefinition("S2", "Spätdienst 2", listOf("S2"), "13:48"),
        ShiftDefinition("PF", "Pflegeforum", listOf("PF"), "07:00")
    )
}
