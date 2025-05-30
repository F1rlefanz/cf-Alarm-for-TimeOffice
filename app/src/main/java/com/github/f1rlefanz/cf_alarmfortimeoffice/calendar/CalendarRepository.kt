package com.github.f1rlefanz.cf_alarmfortimeoffice.calendar

import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.CalendarList
import com.google.api.services.calendar.model.CalendarListEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class CalendarItem(val id: String, val displayName: String)

class CalendarRepository {

    // Die Methode akzeptiert jetzt einen Access Token
    suspend fun getCalendarsWithToken(accessToken: String): List<CalendarItem> =
        withContext(Dispatchers.IO) {
            Timber.d("CalendarRepository: Rufe Kalenderliste mit Access Token ab...")
            val transport = NetHttpTransport() // Du verwendest dies bereits korrekt
            val jsonFactory = GsonFactory.getDefaultInstance()

            // Erstelle einen HttpRequestInitializer, der den Access Token setzt
            val requestInitializer = HttpRequestInitializer { httpRequest: HttpRequest ->
                // Setze den Authorization Header direkt auf dem übergebenen httpRequest
                httpRequest.headers.authorization = "Bearer $accessToken"
                // Die Interceptor-Logik, falls du sie brauchst (z.B. für Logging),
                // wäre httpRequest.interceptor = ... aber für den Auth-Header reicht das hier.
            }

            // Baue den Calendar Service mit dem neuen Initializer
            val service = Calendar.Builder(
                transport,
                jsonFactory,
                requestInitializer // Verwende den neuen Initializer
            ).setApplicationName("CF-Alarm for TimeOffice").build()

            return@withContext try {
                val result: CalendarList = service.calendarList().list()
                    .setFields("items(id,summary,accessRole)") // accessRole hinzugefügt für Debugging
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
                // Hier könntest du spezifische Fehler (z.B. 401, 403) anders behandeln,
                // aber fürs Erste werfen wir sie weiter, damit das ViewModel sie fangen kann.
                throw e // Wirf die Exception weiter, damit das ViewModel sie spezifisch behandeln kann
            }
            catch (e: Exception) {
                Timber.e(e, "CalendarRepository: Allgemeiner Fehler beim Abrufen der Kalender")
                // Wirf die Exception weiter oder gib eine leere Liste mit Fehlerindikator zurück
                throw e // oder return@withContext emptyList() und setze Fehler im ViewModel basierend auf dem Ergebnis
            }
        }
}