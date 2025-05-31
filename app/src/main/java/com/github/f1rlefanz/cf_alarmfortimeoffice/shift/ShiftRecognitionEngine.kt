package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ShiftRecognitionEngine(
    private val shiftConfigRepository: ShiftConfigRepository
) {
    
    suspend fun findNextShiftAlarm(events: List<CalendarEvent>): ShiftMatch? {
        val now = LocalDateTime.now()
        val shiftDefinitions = shiftConfigRepository.getShiftDefinitions()
        
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
    
    fun isValidShiftEvent(event: CalendarEvent): Boolean {
        // Add logic to validate if an event is a valid shift event
        return event.title.isNotBlank() && 
               event.startTime.isAfter(LocalDateTime.now().minusDays(1))
    }
}
