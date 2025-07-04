package com.github.f1rlefanz.cf_alarmfortimeoffice.calendar

import com.github.f1rlefanz.cf_alarmfortimeoffice.error.AppError
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.SafeExecutor
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.ICalendarRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.CalendarConstants
import com.google.api.client.googleapis.json.GoogleJsonResponseException
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
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.EventsPage
import java.io.IOException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.ZoneId
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

data class CalendarItem(val id: String, val displayName: String)

/**
 * REFACTORED: CalendarRepository mit Event-Caching und Change Detection
 * 
 * ÄNDERUNGEN:
 * ✅ Kein Singleton mehr - jetzt via Dependency Injection
 * ✅ Implementiert ICalendarRepository Interface für bessere Testbarkeit
 * ✅ Transport und JsonFactory als Instanz-Variablen
 * ✅ Service-Caching pro Repository-Instanz
 * ✅ EVENT-CACHING: Intelligentes Caching für wiederholte Abfragen
 * ✅ FORCE-REFRESH: Bypass Cache für manuelle Aktualisierung
 * ✅ CHANGE-DETECTION: Bereit für ETag-basierte Änderungserkennung
 * ✅ Explizite cleanup() Methode
 * ✅ OFFLINE-SUPPORT: Echte Netzwerk-Erkennung implementiert
 * 
 * OPTIMIERUNGEN:
 * - Reduziert API-Aufrufe durch intelligentes Caching
 * - Verbesserte Performance bei wiederholten Abfragen
 * - Memory-effizientes Cache-Management
 * - Force-Refresh für manuelle Aktualisierung
 * - Thread-safe Cache-Operations
 * - Automatische Offline-Erkennung und Fallback-Strategien
 * 
 * VORTEILE:
 * - Bessere Testbarkeit durch Interface
 * - Konsistent mit anderen Repositories
 * - Lifecycle-Management durch ViewModel/UseCase
 * - Mock-fähig für Unit Tests
 * - Reduzierte Netzwerklast
 * - Robuste Offline-Funktionalität
 */
class CalendarRepository : ICalendarRepository {
    
    // Instanz-basierte Variablen statt Singleton
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    
    // Service Caching pro Instanz
    private var cachedService: Calendar? = null
    private var cachedToken: String? = null
    
    // Event-Cache für effiziente Abfragen
    private val eventCache = CalendarEventCache()
    
    // Context für Netzwerk-Checks (wird über DI gesetzt)
    private var context: Context? = null
    
    private val applicationName = "CF-Alarm for TimeOffice"
    
    /**
     * Sets Android context for network connectivity checks
     * Called by DI container during initialization
     */
    override fun setContext(context: Context) {
        this.context = context
    }
    
    /**
     * OFFLINE SUPPORT: Echte Netzwerk-Konnektivitätsprüfung
     */
    private fun isNetworkAvailable(): Boolean {
        val context = this.context ?: return true // Fallback: assume online if no context
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            activeNetworkInfo?.isConnected == true
        }
    }

    private fun getCalendarService(accessToken: String): Calendar {
        // Service wird nur neu erstellt wenn Token sich ändert
        if (cachedService == null || cachedToken != accessToken) {
            Logger.d(LogTags.CALENDAR_API, "Creating new Calendar service for token: ${accessToken.take(10)}...")

            val requestInitializer = HttpRequestInitializer { httpRequest: HttpRequest ->
                httpRequest.headers.authorization = "Bearer $accessToken"
            }

            cachedToken = accessToken
            cachedService = Calendar.Builder(transport, jsonFactory, requestInitializer)
                .setApplicationName(applicationName)
                .build()

            Logger.d(LogTags.CALENDAR_API, "Calendar service created and cached")
        } else {
            Logger.d(LogTags.CALENDAR_API, "Using cached Calendar service")
        }

        return cachedService!!
    }

    /**
     * Maps Google API exceptions to app-specific errors
     */
    private fun mapCalendarException(e: Exception): AppError = when (e) {
        is GoogleJsonResponseException -> when (e.statusCode) {
            401 -> AppError.AuthenticationError(
                message = "Kalenderzugriff verweigert. Bitte neu anmelden.",
                cause = e
            )
            403 -> AppError.PermissionError(
                permission = "android.permission.READ_CALENDAR",
                message = "Keine Berechtigung für Kalenderzugriff",
                cause = e
            )
            404 -> AppError.CalendarNotFoundError(
                message = "Kalender nicht gefunden",
                cause = e
            )
            in 500..599 -> AppError.ApiError(
                code = e.statusCode,
                message = "Google Kalender Server-Fehler",
                cause = e
            )
            else -> AppError.ApiError(
                code = e.statusCode,
                message = "Kalenderfehler: ${e.statusMessage}",
                cause = e
            )
        }
        is UnknownHostException -> AppError.NetworkError(
            message = "Keine Internetverbindung",
            cause = e
        )
        is IOException -> AppError.NetworkError(
            message = "Netzwerkfehler beim Kalenderzugriff",
            cause = e
        )
        else -> AppError.CalendarAccessError(
            message = "Unerwarteter Fehler beim Kalenderzugriff",
            cause = e
        )
    }

    override suspend fun getCalendarsWithToken(accessToken: String): Result<List<CalendarItem>> =
        withContext(Dispatchers.IO) {
            SafeExecutor.safeExecute("CalendarRepository.getCalendars") {
                Logger.d(LogTags.CALENDAR_API, "Loading calendar list with token...")
                val service = getCalendarService(accessToken)

                try {
                    val result: CalendarList = service.calendarList().list()
                        .setFields("items(id,summary,accessRole)")
                        .execute()

                    val calendarEntries: List<CalendarListEntry> = result.items ?: emptyList()
                    Logger.i(LogTags.CALENDAR, "Loaded ${calendarEntries.size} calendars")
                    
                    calendarEntries.map { calendarListEntry ->
                        CalendarItem(
                            id = calendarListEntry.id ?: "unknown_id_${System.currentTimeMillis()}",
                            displayName = calendarListEntry.summary ?: "Unbenannter Kalender"
                        )
                    }
                } catch (e: Exception) {
                    throw mapCalendarException(e)
                }
            }
        }

    override suspend fun getCalendarEventsWithToken(
        accessToken: String,
        calendarId: String,
        daysAhead: Int
    ): Result<List<CalendarEvent>> {
        // Delegation an Cache-Methode mit Standard-Verhalten (kein Force-Refresh)
        return getCalendarEventsWithCache(accessToken, calendarId, daysAhead, forceRefresh = false)
    }

    override suspend fun getCalendarEventsWithCache(
        accessToken: String,
        calendarId: String,
        daysAhead: Int,
        forceRefresh: Boolean
    ): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("CalendarRepository.getEventsWithCache") {
            
            // OFFLINE SUPPORT: Check for network connectivity and adjust strategy
            val isOfflineMode = !isNetworkAvailable()
            
            // Cache-Check: Nur wenn nicht Force-Refresh und Cache verfügbar
            if (!forceRefresh && eventCache.isCached(calendarId, daysAhead, allowStale = isOfflineMode)) {
                val cachedEvents = eventCache.get(calendarId, daysAhead, allowStale = isOfflineMode)
                if (cachedEvents != null) {
                    val cacheType = if (isOfflineMode) "OFFLINE" else "CACHED"
                    Logger.i(LogTags.CALENDAR_CACHE, "Returning ${cachedEvents.size} $cacheType events")
                    return@safeExecute cachedEvents
                }
            }
            
            // OFFLINE SUPPORT: If offline and no cache available, return empty list with info
            if (isOfflineMode) {
                Logger.w(LogTags.CALENDAR_API, "Offline mode: No cached data available for calendar $calendarId")
                return@safeExecute emptyList<CalendarEvent>()
            }
            
            if (forceRefresh) {
                Logger.i(LogTags.CALENDAR_API, "Force refresh requested - bypassing cache")
            }
            
            Logger.d(LogTags.CALENDAR_API, "Loading events from API...")
            val service = getCalendarService(accessToken)

            try {
                val now = LocalDateTime.now()
                val timeMin = now.atZone(ZoneId.systemDefault()).toInstant().toString()
                val timeMax = now.plusDays(daysAhead.toLong())
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toString()

                val result: Events = service.events().list(calendarId)
                    .setTimeMin(com.google.api.client.util.DateTime(timeMin))
                    .setTimeMax(com.google.api.client.util.DateTime(timeMax))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(CalendarConstants.MAX_EVENTS_PER_QUERY)
                    .setFields("items(id,summary,start,end)")
                    .execute()

                val events = result.items ?: emptyList()
                Logger.i(LogTags.CALENDAR_API, "${events.size} events loaded for next $daysAhead days")

                val calendarEvents = events.mapNotNull { event ->
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
                                calendarId = calendarId,
                                isAllDay = false
                            )
                        } else null
                    } catch (e: Exception) {
                        Logger.w(LogTags.CALENDAR_API, "Failed to parse event: ${event.summary}", e)
                        null
                    }
                }
                
                // OFFLINE SUPPORT: Cache mit Priorität basierend auf Aktualität
                val priority = when {
                    daysAhead <= 1 -> CalendarEventCache.CachePriority.HIGH // Next day events are high priority
                    daysAhead <= 7 -> CalendarEventCache.CachePriority.NORMAL // Week events are normal
                    else -> CalendarEventCache.CachePriority.LOW // Future events are low priority
                }
                
                eventCache.put(calendarId, daysAhead, calendarEvents, result.etag, priority)
                Logger.d(LogTags.CALENDAR_CACHE, "${calendarEvents.size} events cached for future requests (Priority: $priority)")
                
                calendarEvents
            } catch (e: Exception) {
                // OFFLINE SUPPORT: Try to return stale cache as fallback
                val fallbackEvents = eventCache.get(calendarId, daysAhead, allowStale = true)
                if (fallbackEvents != null) {
                    Logger.w(LogTags.CALENDAR_API, "API failed, returning ${fallbackEvents.size} stale cached events", e)
                    return@safeExecute fallbackEvents
                }
                
                throw mapCalendarException(e)
            }
        }
    }
    
    override suspend fun getCalendarEventsWithPagination(
        accessToken: String,
        calendarId: String,
        daysAhead: Int,
        maxResults: Int,
        pageToken: String?
    ): Result<EventsPage> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("CalendarRepository.getCalendarEventsWithPagination") {
            
            Logger.d(LogTags.CALENDAR_API, "Loading events with pagination: pageToken=${pageToken?.take(10)}..., maxResults=$maxResults")
            val service = getCalendarService(accessToken)

            try {
                val now = LocalDateTime.now()
                val timeMin = now.atZone(ZoneId.systemDefault()).toInstant().toString()
                val timeMax = now.plusDays(daysAhead.toLong())
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toString()

                val eventsRequest = service.events().list(calendarId)
                    .setTimeMin(com.google.api.client.util.DateTime(timeMin))
                    .setTimeMax(com.google.api.client.util.DateTime(timeMax))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(maxResults)
                    .setFields("items(id,summary,start,end),nextPageToken")

                // ECHTE LAZY LOADING: Setze pageToken für echte Pagination
                if (pageToken != null) {
                    eventsRequest.pageToken = pageToken
                }

                val result = eventsRequest.execute()
                val events = result.items ?: emptyList()
                val nextPageToken = result.nextPageToken

                Logger.i(LogTags.CALENDAR_API, "${events.size} events loaded for page (maxResults=$maxResults), hasMore=${nextPageToken != null}")

                val calendarEvents = events.mapNotNull { event ->
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
                                calendarId = calendarId,
                                isAllDay = false
                            )
                        } else null
                    } catch (e: Exception) {
                        Logger.w(LogTags.CALENDAR_API, "Failed to parse event: ${event.summary}", e)
                        null
                    }
                }
                
                EventsPage(
                    events = calendarEvents,
                    nextPageToken = nextPageToken,
                    hasMorePages = nextPageToken != null
                )
            } catch (e: Exception) {
                throw mapCalendarException(e)
            }
        }
    }
    
    override suspend fun invalidateCalendarCache(calendarId: String, daysAhead: Int?) {
        if (daysAhead != null) {
            eventCache.invalidate(calendarId, daysAhead)
        } else {
            eventCache.invalidateCalendar(calendarId)
        }
    }
    
    override suspend fun clearEventCache() {
        eventCache.clear()
    }
    
    override suspend fun getCacheStats(): String {
        return eventCache.getCacheStats()
    }
    
    /**
     * Cleanup method to clear cached resources
     * Should be called when the repository is no longer needed
     */
    override fun cleanup() {
        Logger.d(LogTags.REPOSITORY, "Clearing CalendarRepository resources")
        cachedService = null
        cachedToken = null
        // Note: eventCache.clear() wird in clearEventCache() als suspend function aufgerufen
    }
}