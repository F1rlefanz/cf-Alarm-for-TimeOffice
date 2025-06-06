package com.github.f1rlefanz.cf_alarmfortimeoffice.shift

import java.time.LocalDateTime

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime?,
    val description: String? = null,
    val calendarId: String = ""
)