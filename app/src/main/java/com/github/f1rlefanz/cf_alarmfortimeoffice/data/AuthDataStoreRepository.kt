package com.github.f1rlefanz.cf_alarmfortimeoffice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthDataStoreRepository(private val context: Context) {

    private val loginStatusKey = booleanPreferencesKey("login_status")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val calendarIdKey = stringPreferencesKey("calendar_id")
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val tokenExpiryKey = stringPreferencesKey("token_expiry")
    private val selectedCalendarsKey = stringPreferencesKey("selected_calendars")

    // Funktionen zum Speichern
    suspend fun saveLoginStatus(isLoggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[loginStatusKey] = isLoggedIn
        }
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[userIdKey] = userId
        }
    }

    suspend fun saveUserEmail(userEmail: String?) {
        context.dataStore.edit { preferences ->
            preferences[userEmailKey] = userEmail ?: ""
        }
    }

    suspend fun saveCalendarId(calendarId: String) {
        context.dataStore.edit { preferences ->
            preferences[calendarIdKey] = calendarId
        }
    }

    suspend fun saveAccessToken(accessToken: String?) {
        context.dataStore.edit { preferences ->
            preferences[accessTokenKey] = accessToken ?: ""
        }
    }

    suspend fun saveTokenExpiry(expiryTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[tokenExpiryKey] = expiryTime.toString()
        }
    }

    suspend fun saveSelectedCalendars(calendarIds: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[selectedCalendarsKey] = calendarIds.joinToString(",")
        }
    }

    // Funktionen zum Abrufen
    val loginStatus: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[loginStatusKey] == true
    }

    val userId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[userIdKey] ?: ""
    }

    val userEmail: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[userEmailKey] ?: ""
    }

    val calendarId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[calendarIdKey] ?: ""
    }

    val accessToken: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[accessTokenKey] ?: ""
    }

    val tokenExpiry: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[tokenExpiryKey]?.toLongOrNull() ?: 0L
    }

    val selectedCalendars: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val calendarsString = preferences[selectedCalendarsKey] ?: ""
        if (calendarsString.isEmpty()) emptySet() else calendarsString.split(",").toSet()
    }

    // Funktion zum Löschen der Daten beim Logout
    suspend fun clearAuthData() {
        context.dataStore.edit { preferences ->
            // Preserve calendar selection and selected calendars across logout/login
            val savedCalendarId = preferences[calendarIdKey]
            val savedSelectedCalendars = preferences[selectedCalendarsKey]
            
            preferences.clear()
            
            // Restore calendar settings
            savedCalendarId?.let { preferences[calendarIdKey] = it }
            savedSelectedCalendars?.let { preferences[selectedCalendarsKey] = it }
        }
    }
}