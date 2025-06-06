package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.LoadingScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel
import timber.log.Timber

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    activity: MainActivity
) {
    val authState by authViewModel.authState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            authState.isLoading && !authState.isSignedIn -> {
                LoadingScreen(message = "Lade Anmeldestatus...")
            }

            authState.isSignedIn -> {
                MainAppNavigator(
                    authViewModel = authViewModel,
                    onSignOut = { authViewModel.signOut() },
                    activity = activity
                )
            }

            else -> {
                LoginScreen(authViewModel = authViewModel)
            }
        }
    }
}

@Composable
private fun MainAppNavigator(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    activity: MainActivity
) {
    val authState by authViewModel.authState.collectAsState()
    val calendars by authViewModel.calendars.collectAsState()
    val temporarilySelectedCalendarId by authViewModel.temporarilySelectedCalendarId.collectAsState()
    val persistedCalendarId by authViewModel.persistedCalendarId.collectAsState("")
    val context = LocalContext.current

    var showCalendarSelectionScreen by rememberSaveable { mutableStateOf(false) }
    var showShiftConfigScreen by rememberSaveable { mutableStateOf(false) }

    // Effects
    LaunchedEffect(
        authState.isSignedIn,
        authState.androidCalendarPermissionGranted,
        authState.accessToken
    ) {
        if (authState.isSignedIn && !authState.userEmailOrId.isNullOrBlank()) {
            if (!authState.androidCalendarPermissionGranted && !authState.showAndroidCalendarPermissionRationale && !authState.calendarPermissionDenied) {
                Timber.d("MainAppScreen Effect: SignedIn, Android-Berechtigung fehlt. Fordere an.")
                activity.checkAndRequestCalendarPermission()
            } else if (authState.androidCalendarPermissionGranted && authState.accessToken.isNullOrBlank() && !authState.calendarsLoading && !authState.calendarPermissionDenied) {
                Timber.d("MainAppScreen Effect: Android-Berechtigung vorhanden, AccessToken fehlt. Fordere Google API Auth an.")
                authViewModel.requestCalendarScopes(activity)
            }
        }
    }

    LaunchedEffect(authState.authorizationPendingIntent) {
        authState.authorizationPendingIntent?.let { pendingIntent ->
            Timber.d("MainAppScreen Effect: authorizationPendingIntent vorhanden. Starte Launcher.")
            activity.launchAuthorizationIntent(pendingIntent)
        }
    }

    LaunchedEffect(showCalendarSelectionScreen, persistedCalendarId) {
        if (showCalendarSelectionScreen) {
            authViewModel.onCalendarTemporarilySelected(persistedCalendarId)
        }
    }

    when {
        showShiftConfigScreen -> {
            ShiftConfigScreen(
                authState = authState,
                onToggleAutoAlarm = { authViewModel.toggleAutoAlarm() },
                onUpdateShiftDefinition = { authViewModel.updateShiftDefinition(it) },
                onDeleteShiftDefinition = { authViewModel.deleteShiftDefinition(it) },
                onResetToDefaults = { authViewModel.resetShiftConfigToDefaults() },
                onNavigateBack = { showShiftConfigScreen = false }
            )
        }

        showCalendarSelectionScreen && authState.androidCalendarPermissionGranted && !authState.accessToken.isNullOrBlank() -> {
            CalendarSelectionScreen(
                calendars = calendars,
                selectedCalendarId = temporarilySelectedCalendarId,
                onCalendarSelected = { calendarId ->
                    authViewModel.onCalendarTemporarilySelected(calendarId)
                },
                onSaveClicked = {
                    if (temporarilySelectedCalendarId.isNotBlank()) {
                        authViewModel.persistSelectedCalendar()
                        showCalendarSelectionScreen = false
                        Toast.makeText(context, "Kalenderauswahl gespeichert!", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            context,
                            "Bitte einen Kalender auswählen.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onCancelClicked = {
                    authViewModel.clearTemporaryCalendarSelection()
                    showCalendarSelectionScreen = false
                },
                isLoading = authState.calendarsLoading
            )
        }

        else -> {
            MainContentScreen(
                authState = authState,
                persistedCalendarId = persistedCalendarId,
                calendars = calendars,
                authViewModel = authViewModel,
                activity = activity,
                onSignOut = onSignOut,
                onShowShiftConfig = { showShiftConfigScreen = true },
                onShowCalendarSelection = { showCalendarSelectionScreen = true }
            )
        }
    }
}