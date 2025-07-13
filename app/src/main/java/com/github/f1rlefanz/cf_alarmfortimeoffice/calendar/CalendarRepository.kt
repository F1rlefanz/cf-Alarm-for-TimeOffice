package com.github.f1rlefanz.cf_alarmfortimeoffice.calendar

import com.github.f1rlefanz.cf_alarmfortimeoffice.error.AppError
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.SafeExecutor
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.ICalendarRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.CalendarConstants
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.CalendarList
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
 * CalendarRepository implementiert ICalendarRepository Interface
 * mit Event-Caching und Google Calendar API Integration
 */
class CalendarRepository(private var context: Context? = null) : ICalendarRepository {
    
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val eventCache = CalendarEventCache()
    
    private var cachedService: Calendar? = null
    private var cachedToken: String? = null

    override fun setContext(context: Context) {
        this.context = context
    }

    override suspend fun getCalendarsWithToken(accessToken: String): Result<List<CalendarItem>> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("CalendarRepository.getCalendarsWithToken") {
            Logger.d(LogTags.CALENDAR_API, "Loading available calendars...")
            val service = getCalendarService(accessToken)

            try {
                val calendarList: CalendarList = service.calendarList().list()
                    .setFields("items(id,summary,primary,accessRole)")
                    .setMinAccessRole("reader")
                    .execute()

                Logger.d(LogTags.CALENDAR_API, "Calendar API response received: ${calendarList.items?.size ?: 0} items")
                
                if (calendarList.items.isNullOrEmpty()) {
                    Logger.w(LogTags.CALENDAR_API, "No calendars found in Google Calendar API response")
                    Logger.d(LogTags.CALENDAR_API, "Full API response: $calendarList")
                    Logger.i(LogTags.CALENDAR_API, "DIAGNOSTIC: User account appears to have no calendars or calendar access is restricted")
                    
                    // Enhanced diagnostic logging
                    Logger.d(LogTags.CALENDAR_API, "DIAGNOSTIC: API Response Details:")
                    Logger.d(LogTags.CALENDAR_API, "  - ETag: ${calendarList.etag}")
                    Logger.d(LogTags.CALENDAR_API, "  - Kind: ${calendarList.kind}")
                    Logger.d(LogTags.CALENDAR_API, "  - NextPageToken: ${calendarList.nextPageToken}")
                    Logger.d(LogTags.CALENDAR_API, "  - NextSyncToken: ${calendarList.nextSyncToken}")
                } else {
                    Logger.d(LogTags.CALENDAR_API, "Found calendars: ${calendarList.items.map { "${it.summary} (${it.id})" }}")
                    Logger.i(LogTags.CALENDAR_API, "DIAGNOSTIC: Successfully loaded ${calendarList.items.size} calendars")
                }

                val calendars = calendarList.items?.mapNotNull { calendarEntry ->
                    try {
                        CalendarItem(
                            id = calendarEntry.id ?: return@mapNotNull null,
                            displayName = calendarEntry.summary ?: "Unnamed Calendar"
                        )
                    } catch (e: Exception) {
                        Logger.w(LogTags.CALENDAR_API, "Failed to parse calendar: ${calendarEntry.summary}", e)
                        null
                    }
                } ?: emptyList()

                Logger.i(LogTags.CALENDAR_API, "${calendars.size} calendars loaded successfully")
                calendars
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
        return getCalendarEventsWithCache(accessToken, calendarId, daysAhead, forceRefresh = false)
    }

    override suspend fun getCalendarEventsWithCache(
        accessToken: String,
        calendarId: String,
        daysAhead: Int,
        forceRefresh: Boolean
    ): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("CalendarRepository.getEventsWithCache") {
            val isOfflineMode = !isNetworkAvailable()
            
            if (!forceRefresh && eventCache.isCached(calendarId, daysAhead, allowStale = isOfflineMode)) {
                val cachedEvents = eventCache.get(calendarId, daysAhead, allowStale = isOfflineMode)
                if (cachedEvents != null) {
                    val cacheType = if (isOfflineMode) "OFFLINE" else "CACHED"
                    Logger.i(LogTags.CALENDAR_CACHE, "Returning ${cachedEvents.size} $cacheType events")
                    return@safeExecute cachedEvents
                }
            }
            
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
                
                val priority = when {
                    daysAhead <= 1 -> CalendarEventCache.CachePriority.HIGH
                    daysAhead <= 7 -> CalendarEventCache.CachePriority.NORMAL
                    else -> CalendarEventCache.CachePriority.LOW
                }
                
                eventCache.put(calendarId, daysAhead, calendarEvents, result.etag, priority)
                Logger.d(LogTags.CALENDAR_CACHE, "${calendarEvents.size} events cached for future requests (Priority: $priority)")
                
                calendarEvents
            } catch (e: Exception) {
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
    
    override fun cleanup() {
        Logger.d(LogTags.REPOSITORY, "Clearing CalendarRepository resources")
        cachedService = null
        cachedToken = null
    }
    
    private fun getCalendarService(accessToken: String): Calendar {
        if (cachedService == null || cachedToken != accessToken) {
            Logger.business(LogTags.CALENDAR_API, "🔗 API-SERVICE: Creating Calendar service with token: ${accessToken.take(20)}...")
            Logger.d(LogTags.CALENDAR_API, "📊 TOKEN-INFO: Token length=${accessToken.length}")
            
            // DIAGNOSTIC: Check if this looks like a real OAuth2 token
            when {
                accessToken == "valid_credential_token" -> {
                    Logger.e(LogTags.CALENDAR_API, "❌ CRITICAL: Still using placeholder token 'valid_credential_token'!")
                    Logger.e(LogTags.CALENDAR_API, "💡 FIX-HINT: OAuth2 token integration is broken - check AuthViewModel and ModernOAuth2TokenManager")
                }
                accessToken.startsWith("ya29.") -> {
                    Logger.business(LogTags.CALENDAR_API, "✅ TOKEN-OK: Real Google OAuth2 access token detected (ya29.)")
                }
                accessToken.length < 10 -> {
                    Logger.w(LogTags.CALENDAR_API, "⚠️ TOKEN-SUSPICIOUS: Token seems too short (${accessToken.length} chars)")
                }
                else -> {
                    Logger.d(LogTags.CALENDAR_API, "🔍 TOKEN-INFO: Using token of ${accessToken.length} chars")
                }
            }
            
            // Use standard OAuth2 Bearer token authentication
            Logger.d(LogTags.CALENDAR_API, "🔐 AUTH-METHOD: Using OAuth2 Bearer token authentication")
            val requestInitializer = HttpRequestInitializer { request: HttpRequest ->
                request.headers.authorization = "Bearer $accessToken"
            }
            
            cachedService = Calendar.Builder(transport, jsonFactory, requestInitializer)
                .setApplicationName("CF-Alarm for TimeOffice")
                .build()
            cachedToken = accessToken
            
            Logger.d(LogTags.CALENDAR_API, "✅ API-SERVICE: Calendar service ready for API calls")
        }
        return cachedService!!
    }
    
    private fun mapCalendarException(e: Exception): AppError {
        Logger.e(LogTags.CALENDAR_API, "Calendar API error", e)
        return when (e) {
            is GoogleJsonResponseException -> {
                when (e.statusCode) {
                    401 -> AppError.AuthenticationError("Google Calendar authentication failed")
                    403 -> AppError.PermissionError("Insufficient permissions for Google Calendar")
                    404 -> AppError.NetworkError("Calendar not found")
                    else -> AppError.NetworkError("Google Calendar API error: ${e.statusMessage}")
                }
            }
            is UnknownHostException -> AppError.NetworkError("No internet connection")
            is IOException -> AppError.NetworkError("Network error: ${e.message}")
            else -> AppError.UnknownError("Calendar error: ${e.message}")
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        return try {
            context?.let { ctx ->
                val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                } else {
                    @Suppress("DEPRECATION")
                    connectivityManager.activeNetworkInfo?.isConnected == true
                }
            } ?: true
        } catch (e: Exception) {
            Logger.w(LogTags.REPOSITORY, "Error checking network availability", e)
            true
        }
}
}
