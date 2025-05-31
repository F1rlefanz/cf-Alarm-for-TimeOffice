package com.github.f1rlefanz.cf_alarmfortimeoffice.calendar

import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.CalendarEvent
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.CalendarList
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Events
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId

data class CalendarItem(val id: String, val displayName: String)

class CalendarRepository {

    private fun createCalendarService(accessToken: String): Calendar {
        val transport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val requestInitializer = HttpRequestInitializer { httpRequest: HttpRequest ->
            httpRequest.headers.authorization = "Bearer $accessToken"
        }
        
        return Calendar.Builder(transport, jsonFactory, requestInitializer)
            .setApplicationName("CF-Alarm for TimeOffice")
            .build()
    }

    suspend fun getCalendarsWithToken(accessToken: String): List<CalendarItem> =
        withContext(Dispatchers.IO) {
            Timber.d("CalendarRepository: Rufe Kalenderliste mit Access Token ab...")
            val service = createCalendarService(accessToken)

            return@withContext try {
                val result: CalendarList = service.calendarList().list()
                    .setFields("items(id,summary,accessRole)")
                    .execute()

                val calendarEntries: List<CalendarListEntry> = result.items ?: emptyList()
                Timber.i("CalendarRepository: ${calendarEntries.size} Kalendereinträge gefunden.")
                calendarEntries.forEach { entry ->
                    Timber.d("Kalender: ${entry.summary} (ID: ${entry.id}, Rolle: ${entry.accessRole})")
                }

                calendarEntries.map { calendarListEntry ->
                    CalendarItem(
                        id = calendarListEntry.id ?: "unknown_id_${System.currentTimeMillis()}",
                        displayName = calendarListEntry.summary ?: "Unbenannter Kalender"
                    )
                }
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                Timber.e(e, "CalendarRepository: GoogleJsonResponseException beim Abrufen der Kalender. Status: ${e.statusCode}, Nachricht: ${e.statusMessage}, Details: ${e.details}")
                throw e
            }
            catch (e: Exception) {
                Timber.e(e, "CalendarRepository: Allgemeiner Fehler beim Abrufen der Kalender")
                throw e
            }
        }

    suspend fun getCalendarEventsWithToken(
        accessToken: String, 
        calendarId: String,
        daysAhead: Int = 7
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        Timber.d("CalendarRepository: Rufe Kalendereinträge für Kalender $calendarId ab...")
        val service = createCalendarService(accessToken)

        return@withContext try {
            val now = LocalDateTime.now()
            val timeMin = now.atZone(ZoneId.systemDefault()).toInstant().toString()
            val timeMax = now.plusDays(daysAhead.toLong()).atZone(ZoneId.systemDefault()).toInstant().toString()

            val result: Events = service.events().list(calendarId)
                .setTimeMin(com.google.api.client.util.DateTime(timeMin))
                .setTimeMax(com.google.api.client.util.DateTime(timeMax))
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setMaxResults(50)
                .setFields("items(id,summary,start,end)")
                .execute()

            val events = result.items ?: emptyList()
            Timber.i("CalendarRepository: ${events.size} Kalendereinträge für die nächsten $daysAhead Tage gefunden.")

            events.mapNotNull { event ->
                try {
                    val startDateTime = event.start?.dateTime ?: event.start?.date
                    val endDateTime = event.end?.dateTime ?: event.end?.date
                    
                    if (startDateTime != null && endDateTime != null) {
                        val startTime = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(startDateTime.value),
                            ZoneId.systemDefault()
                        )
                        val endTime = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(endDateTime.value),
                            ZoneId.systemDefault()
                        )
                        
                        CalendarEvent(
                            id = event.id ?: "unknown_${System.currentTimeMillis()}",
                            title = event.summary ?: "Unbenannter Termin",
                            startTime = startTime,
                            endTime = endTime,
                            calendarId = calendarId
                        )
                    } else null
                } catch (e: Exception) {
                    Timber.w(e, "Fehler beim Parsen des Kalendereintrags: ${event.summary}")
                    null
                }
            }
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            Timber.e(e, "CalendarRepository: GoogleJsonResponseException beim Abrufen der Kalendereinträge. Status: ${e.statusCode}")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CalendarRepository: Allgemeiner Fehler beim Abrufen der Kalendereinträge")
            throw e
        }
    }
}