package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import java.time.LocalDateTime

class ShiftRecognitionEngine(
    private val shiftConfigRepository: ShiftConfigRepository
) {
    
    suspend fun findNextShiftAlarm(events: List<CalendarEvent>): ShiftMatch? {
        val now = LocalDateTime.now()
        
        val matchingShifts = getAllMatchingShifts(events)
            .filter { it.calendarEvent.startTime.isAfter(now) }
            .sortedBy { it.calendarEvent.startTime }
        
        return matchingShifts.firstOrNull()
    }
    
    suspend fun getAllMatchingShifts(events: List<CalendarEvent>): List<ShiftMatch> {
        val shiftDefinitions = shiftConfigRepository.getShiftDefinitions()
        val matches = mutableListOf<ShiftMatch>()
        
        for (event in events) {
            for (definition in shiftDefinitions) {
                if (definition.matchesCalendarEntry(event.title)) {
                    val alarmTime = calculateAlarmTime(event, definition)
                    matches.add(
                        ShiftMatch(
                            shiftDefinition = definition,
                            calendarEvent = event,
                            calculatedAlarmTime = alarmTime
                        )
                    )
                    break // Only match first matching definition per event
                }
            }
        }
        
        return matches.sortedBy { it.calculatedAlarmTime }
    }
    
    private fun calculateAlarmTime(event: CalendarEvent, definition: ShiftDefinition): LocalDateTime {
        val shiftStartTime = event.startTime
        val alarmTime = definition.getAlarmLocalTime()
        
        // Calculate alarm time on the same date as the shift
        val alarmDateTime = LocalDateTime.of(
            shiftStartTime.toLocalDate(),
            alarmTime
        )
        
        // If alarm time is after shift start time, assume it's for the previous day
        return if (alarmDateTime.isAfter(shiftStartTime)) {
            alarmDateTime.minusDays(1)
        } else {
            alarmDateTime
        }
    }
}
