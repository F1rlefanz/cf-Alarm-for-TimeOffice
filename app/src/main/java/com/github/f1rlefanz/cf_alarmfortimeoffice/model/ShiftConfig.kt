package com.github.f1rlefanz.cf_alarmfortimeoffice.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.time.LocalTime

/**
 * IMMUTABLE Shift Configuration Model
 * 
 * PERFORMANCE OPTIMIZATIONS:
 * ✅ @Immutable annotation für Compose-Performance
 * ✅ Unveränderliche Listen für bessere Performance
 */
@Immutable
@Serializable
data class ShiftConfig(
    val autoAlarmEnabled: Boolean = true,
    val definitions: List<ShiftDefinition> = emptyList(),
    val daysAhead: Int = 7
) {
    companion object {
        fun getDefaultConfig(): ShiftConfig = ShiftConfig(
            autoAlarmEnabled = true,
            daysAhead = 7,
            definitions = listOf(
                ShiftDefinition(
                    id = "early_shift",
                    name = "Frühschicht",
                    keywords = listOf("Früh", "FD", "FS", "Frühschicht"),
                    alarmTime = LocalTime.of(5, 30),
                    isEnabled = true
                ),
                ShiftDefinition(
                    id = "late_shift", 
                    name = "Spätschicht",
                    keywords = listOf("Spät", "SD", "SS", "Spätschicht"),
                    alarmTime = LocalTime.of(13, 0),
                    isEnabled = true
                ),
                ShiftDefinition(
                    id = "night_shift",
                    name = "Nachtschicht",
                    keywords = listOf("Nacht", "ND", "NS", "Nachtschicht"),
                    alarmTime = LocalTime.of(21, 0),
                    isEnabled = true
                )
            )
        )
    }
}
