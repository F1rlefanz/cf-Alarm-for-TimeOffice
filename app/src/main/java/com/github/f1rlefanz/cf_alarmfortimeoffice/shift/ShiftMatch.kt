package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ShiftMatch(
    val shiftDefinition: ShiftDefinition,
    val calendarEvent: CalendarEvent,
    val calculatedAlarmTime: LocalDateTime
) {
    val calendarEventTitle: String
        get() = calendarEvent.title
    
    val formattedAlarmTime: String
        get() = calculatedAlarmTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}
