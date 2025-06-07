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
            preferences[userEmailKey] = userEmail ?: "" // Sicherstellen, dass es nicht null ist
        }
    }

    suspend fun saveCalendarId(calendarId: String) {
        context.dataStore.edit { preferences ->
            preferences[calendarIdKey] = calendarId
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

    // Funktion zum Löschen der Daten beim Logout
    suspend fun clearAuthData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}