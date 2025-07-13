package com.github.f1rlefanz.cf_alarmfortimeoffice.calendar

import com.github.f1rlefanz.cf_alarmfortimeoffice.error.AppError
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ROBUST SOLUTION: Direct REST API client for Google Calendar
 * 
 * Ersetzt die problematische Google Calendar Client Library mit direkten HTTP-Calls
 * 
 * VORTEILE:
 * ✅ Keine GoogleUtils ExceptionInInitializerError mehr
 * ✅ Vollständige Kontrolle über HTTP-Requests  
 * ✅ Kleinere APK-Größe durch weniger Dependencies
 * ✅ Robuster und thread-safe
 * ✅ Einfaches Error-Handling
 * ✅ Bessere Performance durch weniger Overhead
 */
class GoogleCalendarRestClient {
    
    private val httpClient: OkHttpClient
    private val gson = Gson()
    
    companion object {
        private const val CALENDAR_API_BASE_URL = "https://www.googleapis.com/calendar/v3"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Logger.d(LogTags.CALENDAR_API, "HTTP: $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC // Only log request/response lines
        }
        
        httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Lädt die verfügbaren Kalender des Benutzers
     */
    suspend fun getCalendarList(accessToken: String): Result<List<CalendarItem>> = 
        withContext(Dispatchers.IO) {
            try {
                Logger.d(LogTags.CALENDAR_API, "Loading calendar list via REST API...")
                
                val request = Request.Builder()
                    .url("$CALENDAR_API_BASE_URL/users/me/calendarList?fields=items(id,summary,accessRole)")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Accept", "application/json")
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, response.message)
                }
                
                val responseBody = response.body?.string() 
                    ?: throw AppError.ApiError(code = 500, message = "Empty response body")
                
                val calendarListResponse = gson.fromJson(responseBody, CalendarListResponse::class.java)
                val calendars = calendarListResponse.items?.map { item ->
                    CalendarItem(
                        id = item.id ?: "unknown_id_${System.currentTimeMillis()}",
                        displayName = item.summary ?: "Unbenannter Kalender"
                    )
                } ?: emptyList()
                
                Logger.i(LogTags.CALENDAR_API, "Loaded ${calendars.size} calendars via REST API")
                Result.success(calendars)
                
            } catch (e: Exception) {
                Logger.e(LogTags.CALENDAR_API, "Failed to load calendars via REST API", e)
                Result.failure(mapCalendarException(e))
            }
        }
    
    /**
     * Lädt Events für einen spezifischen Kalender
     */
    suspend fun getCalendarEvents(
        accessToken: String,
        calendarId: String,
        timeMin: String,
        timeMax: String,
        maxResults: Int = 250
    ): Result<CalendarEventsResponse> = withContext(Dispatchers.IO) {
        try {
            Logger.d(LogTags.CALENDAR_API, "Loading calendar events via REST API for calendar: $calendarId")
            
            val url = "$CALENDAR_API_BASE_URL/calendars/${java.net.URLEncoder.encode(calendarId, "UTF-8")}/events" +
                "?timeMin=${java.net.URLEncoder.encode(timeMin, "UTF-8")}" +
                "&timeMax=${java.net.URLEncoder.encode(timeMax, "UTF-8")}" +
                "&orderBy=startTime" +
                "&singleEvents=true" +
                "&maxResults=$maxResults" +
                "&fields=items(id,summary,start,end),nextPageToken"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw mapHttpError(response.code, response.message)
            }
            
            val responseBody = response.body?.string() 
                ?: throw AppError.ApiError(code = 500, message = "Empty response body")
            
            val eventsResponse = gson.fromJson(responseBody, CalendarEventsResponse::class.java)
            
            Logger.i(LogTags.CALENDAR_API, "Loaded ${eventsResponse.items?.size ?: 0} events via REST API")
            Result.success(eventsResponse)
            
        } catch (e: Exception) {
            Logger.e(LogTags.CALENDAR_API, "Failed to load events via REST API", e)
            Result.failure(mapCalendarException(e))
        }
    }
    
    /**
     * Maps HTTP response codes to app-specific errors
     */
    private fun mapHttpError(code: Int, message: String): AppError = when (code) {
        401 -> AppError.AuthenticationError(
            message = "Kalenderzugriff verweigert. Bitte neu anmelden.",
            cause = Exception("HTTP $code: $message")
        )
        403 -> AppError.PermissionError(
            permission = "calendar.readonly",
            message = "Keine Berechtigung für Kalenderzugriff",
            cause = Exception("HTTP $code: $message")
        )
        404 -> AppError.CalendarNotFoundError(
            message = "Kalender nicht gefunden",
            cause = Exception("HTTP $code: $message")
        )
        in 500..599 -> AppError.ApiError(
            code = code,
            message = "Google Kalender Server-Fehler: $message",
            cause = Exception("HTTP $code: $message")
        )
        else -> AppError.ApiError(
            code = code,
            message = "Kalenderfehler: $message",
            cause = Exception("HTTP $code: $message")
        )
    }
    
    /**
     * Maps exceptions to app-specific errors
     */
    private fun mapCalendarException(e: Exception): AppError = when (e) {
        is AppError -> e // Re-throw app errors unchanged
        is UnknownHostException -> AppError.NetworkError(
            message = "Keine Internetverbindung",
            cause = e
        )
        is IOException -> AppError.NetworkError(
            message = "Netzwerkfehler beim Kalenderzugriff",
            cause = e
        )
        else -> AppError.CalendarAccessError(
            message = "Unerwarteter Fehler beim Kalenderzugriff: ${e.message}",
            cause = e
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        // OkHttpClient resources are automatically cleaned up
        Logger.d(LogTags.CALENDAR_API, "GoogleCalendarRestClient cleaned up")
    }
}

// Data classes for REST API responses
data class CalendarListResponse(
    @SerializedName("items") val items: List<CalendarListItem>?
)

data class CalendarListItem(
    @SerializedName("id") val id: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("accessRole") val accessRole: String?
)

data class CalendarEventsResponse(
    @SerializedName("items") val items: List<CalendarEventItem>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class CalendarEventItem(
    @SerializedName("id") val id: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("start") val start: EventDateTime?,
    @SerializedName("end") val end: EventDateTime?
)

data class EventDateTime(
    @SerializedName("dateTime") val dateTime: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("timeZone") val timeZone: String?
)
