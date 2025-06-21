package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarItem
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthState
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel

/**
 * OPTIMIERUNG: Extracted Composables für MainContentScreen
 * 
 * Reduziert Komplexität durch modulare UI-Komponenten
 */

@Composable
fun WelcomeHeader(authState: AuthState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "CF-Alarm",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Willkommen, ${authState.userName ?: authState.userEmailOrId ?: "Nutzer"}!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MainContent(
    contentState: ContentState,
    authState: AuthState,
    persistedCalendarId: String,
    calendars: List<CalendarItem>,
    authViewModel: AuthViewModel,
    activity: MainActivity,
    onShowShiftConfig: () -> Unit,
    onShowCalendarSelection: () -> Unit
) {
    when (contentState) {
        ContentState.ShowAlarm -> {
            AlarmContent(authState, authViewModel, activity)
        }
        ContentState.NoCalendarSelected -> {
            NoCalendarSelectedContent(authViewModel, activity, calendars, onShowCalendarSelection)
        }
        ContentState.AutoAlarmDisabled -> {
            AutoAlarmDisabledContent(onShowShiftConfig)
        }
        ContentState.LoadingShifts -> {
            LoadingShiftsContent()
        }
        ContentState.NoShiftFound -> {
            NoShiftFoundContent(authViewModel, activity, calendars, persistedCalendarId, authState, onShowCalendarSelection)
        }
    }
}

@Composable
private fun AlarmContent(
    authState: AuthState,
    authViewModel: AuthViewModel,
    activity: MainActivity
) {
    authState.nextShiftAlarm?.let { shiftMatch ->
        AlarmCard(
            shiftMatch = shiftMatch,
            systemAlarmSet = authState.systemAlarmSet,
            canScheduleExactAlarms = authState.canScheduleExactAlarms,
            onCancelAlarm = { authViewModel.cancelSystemAlarm() },
            onSetAlarm = { authViewModel.setAlarmFromShiftMatch() },
            onRequestAlarmPermission = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        "package:${activity.packageName}".toUri()
                    )
                    activity.startActivity(intent)
                }
            }
        )
    }
}

@Composable
private fun NoCalendarSelectedContent(
    authViewModel: AuthViewModel,
    activity: MainActivity,
    calendars: List<CalendarItem>,
    onShowCalendarSelection: () -> Unit
) {
    NoAlarmCard(
        reason = NoCalendarSelected(),
        onActionClick = {
            authViewModel.triggerCalendarAccess(activity)
            if (calendars.isNotEmpty()) {
                onShowCalendarSelection()
            }
        }
    )
}

@Composable
private fun AutoAlarmDisabledContent(onShowShiftConfig: () -> Unit) {
    NoAlarmCard(
        reason = AutoAlarmDisabled(),
        onActionClick = onShowShiftConfig
    )
}

@Composable
private fun LoadingShiftsContent() {
    NoAlarmCard(
        reason = LoadingShifts(),
        onActionClick = null
    )
}

@Composable
private fun NoShiftFoundContent(
    authViewModel: AuthViewModel,
    activity: MainActivity,
    calendars: List<CalendarItem>,
    persistedCalendarId: String,
    authState: AuthState,
    onShowCalendarSelection: () -> Unit
) {
    NoAlarmCard(
        reason = NoShiftFound(),
        onActionClick = if (!authState.calendarsLoading) {
            {
                authViewModel.triggerCalendarAccess(activity)
                if (calendars.isNotEmpty() || authState.calendarsLoading) {
                    authViewModel.onCalendarTemporarilySelected(persistedCalendarId)
                    onShowCalendarSelection()
                }
            }
        } else null
    )
}

@Composable
fun ErrorContent(
    errorState: ErrorState,
    authState: AuthState,
    authViewModel: AuthViewModel,
    activity: MainActivity
) {
    when (errorState) {
        ErrorState.None -> { /* No error to display */ }
        
        ErrorState.CalendarPermissionRationale -> {
            StatusCard(
                title = "Kalenderberechtigung erforderlich",
                subtitle = "Diese App benötigt Zugriff auf deine Kalender, um Dienstpläne zu lesen.",
                icon = Icons.Filled.CalendarMonth,
                isPositive = false,
                actionButton = {
                    ActionButton(
                        text = "Kalenderberechtigung erneut anfordern",
                        icon = Icons.Filled.CalendarMonth,
                        onClick = { activity.checkAndRequestCalendarPermission() },
                        variant = ButtonVariant.Error
                    )
                }
            )
        }
        
        ErrorState.CalendarPermissionDenied -> {
            StatusCard(
                title = "Kalenderberechtigung verweigert",
                subtitle = authState.error ?: "Kalenderberechtigung wurde verweigert",
                icon = Icons.Filled.CalendarMonth,
                isPositive = false,
                actionButton = {
                    ActionButton(
                        text = "App-Einstellungen öffnen",
                        icon = Icons.Filled.Settings,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = "package:${activity.packageName}".toUri()
                            intent.data = uri
                            activity.startActivity(intent)
                        },
                        variant = ButtonVariant.Error
                    )
                }
            )
        }
        
        ErrorState.GoogleAuthFailed -> {
            StatusCard(
                title = "Google Kalender Autorisierung fehlgeschlagen",
                subtitle = authState.error ?: "Autorisierung für Google Kalender fehlgeschlagen.",
                icon = Icons.Filled.Error,
                isPositive = false,
                actionButton = {
                    ActionButton(
                        text = "Google Kalender Autorisierung erneut versuchen",
                        icon = Icons.Filled.Refresh,
                        onClick = { authViewModel.requestCalendarScopes(activity) },
                        variant = ButtonVariant.Error
                    )
                }
            )
        }
        
        is ErrorState.GeneralError -> {
            StatusCard(
                title = "Fehler",
                subtitle = errorState.message,
                icon = Icons.Filled.Error,
                isPositive = false,
                actionButton = {
                    ActionButton(
                        text = "Erneut versuchen",
                        icon = Icons.Filled.Refresh,
                        onClick = { authViewModel.retryCalendarAccessOrReAuth(activity) },
                        variant = ButtonVariant.Error
                    )
                }
            )
        }
    }
}

@Composable
fun CalendarStatusSection(
    persistedCalendarId: String,
    authState: AuthState,
    authViewModel: AuthViewModel,
    onShowCalendarSelection: () -> Unit
) {
    StatusCard(
        title = "Dienstplan-Kalender",
        subtitle = if (persistedCalendarId.isNotBlank()) persistedCalendarId else "Kein Kalender ausgewählt",
        icon = Icons.Outlined.CalendarMonth,
        isPositive = persistedCalendarId.isNotBlank(),
        actionButton = if (persistedCalendarId.isNotBlank() && !authState.calendarsLoading) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { authViewModel.refreshCalendarEvents() },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kalender prüfen")
                    }
                    OutlinedButton(
                        onClick = onShowCalendarSelection,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wechseln")
                    }
                }
            }
        } else null
    )
}

@Composable
fun LoadingIndicator(authState: AuthState) {
    if (authState.calendarsLoading) {
        InfoCard(
            title = "Lade Kalenderliste...",
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Kalender werden geladen...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            icon = Icons.Default.Refresh
        )
    }
}
