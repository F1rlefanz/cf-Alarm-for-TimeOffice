package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import com.github.f1rlefanz.cf_alarmfortimeoffice.model.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftDefinition
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class ShiftRecognitionEngine(
    private val shiftConfigRepository: IShiftConfigRepository
) {
    
    suspend fun findNextShiftAlarm(events: List<CalendarEvent>): ShiftMatch? {
        val now = LocalDateTime.now()
        
        val matchingShifts = getAllMatchingShifts(events)
            .filter { it.calendarEvent.startTime.isAfter(now) }
            .sortedBy { it.calendarEvent.startTime }
        
        return matchingShifts.firstOrNull()
    }
    
    /**
     * PERFORMANCE OPTIMIZATION: Enhanced recognition with caching
     * Caches results for identical event sets instead of skipping
     */
    @Volatile
    private var lastRecognitionHash = 0
    @Volatile
    private var cachedMatches: List<ShiftMatch> = emptyList()
    @Volatile
    private var recognitionInProgress = false
    
    /**
     * Clears the recognition cache to force re-processing of events.
     * This should be called when shift configuration changes.
     */
    fun clearRecognitionCache() {
        lastRecognitionHash = 0
        cachedMatches = emptyList()
        recognitionInProgress = false
        Logger.d(LogTags.SHIFT_RECOGNITION, "🔄 CACHE-CLEAR: Recognition cache cleared due to configuration change")
    }
    
    suspend fun getAllMatchingShifts(events: List<CalendarEvent>): List<ShiftMatch> {
        // PERFORMANCE: Calculate hash of input to prevent duplicate processing
        val eventsHash = events.hashCode()
        
        // DEDUPLICATION: Skip if same events are being processed
        if (recognitionInProgress) {
            Logger.d(LogTags.SHIFT_RECOGNITION, "Recognition already in progress, waiting...")
            kotlinx.coroutines.delay(50)
            if (recognitionInProgress) {
                Logger.d(LogTags.SHIFT_RECOGNITION, "Recognition still in progress, skipping duplicate call")
                return cachedMatches
            }
        }
        
        // CACHE HIT: Return cached results if same events were processed recently
        if (lastRecognitionHash == eventsHash && lastRecognitionHash != 0) {
            Logger.d(LogTags.SHIFT_RECOGNITION, "Same events already processed recently, returning cached ${cachedMatches.size} matches")
            return cachedMatches
        }
        
        recognitionInProgress = true
        lastRecognitionHash = eventsHash
        
        try {
            val matches = performRecognition(events)
            cachedMatches = matches
            return matches
        } finally {
            recognitionInProgress = false
        }
    }
    
    private suspend fun performRecognition(events: List<CalendarEvent>): List<ShiftMatch> {
        val shiftConfigResult = shiftConfigRepository.getCurrentShiftConfig()
        val shiftDefinitions = shiftConfigResult.getOrNull()?.definitions ?: emptyList()
        val matches = mutableListOf<ShiftMatch>()
        
        Logger.d(LogTags.SHIFT_RECOGNITION, "Starting shift recognition with ${shiftDefinitions.size} definitions and ${events.size} events")
        
        for (event in events) {
            Logger.d(LogTags.SHIFT_RECOGNITION, "Checking event: '${event.title}' at ${event.startTime}")
            
            for (definition in shiftDefinitions) {
                Logger.d(LogTags.SHIFT_RECOGNITION, "Testing definition '${definition.name}' with keywords: ${definition.keywords}")
                
                if (definition.matchesKeywords(event.title)) {
                    val alarmTime = calculateAlarmTime(event, definition)
                    matches.add(
                        ShiftMatch(
                            shiftDefinition = definition,
                            calendarEvent = event,
                            calculatedAlarmTime = alarmTime
                        )
                    )
                    Logger.i(LogTags.SHIFT_RECOGNITION, "✅ MATCH found: '${event.title}' matches '${definition.name}' with keywords ${definition.keywords}")
                    break // Only match first matching definition per event
                } else {
                    Logger.d(LogTags.SHIFT_RECOGNITION, "❌ No match: '${event.title}' doesn't contain any of ${definition.keywords}")
                }
            }
        }
        
        Logger.i(LogTags.SHIFT_RECOGNITION, "Recognition complete: Found ${matches.size} shifts")
        return matches.sortedBy { it.calculatedAlarmTime }
    }
    
    suspend fun findAllShiftMatches(events: List<CalendarEvent>): List<ShiftMatch> {
        return getAllMatchingShifts(events)
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
