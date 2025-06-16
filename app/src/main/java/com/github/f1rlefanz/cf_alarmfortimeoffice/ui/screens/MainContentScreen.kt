package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarItem
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.AlarmCard
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthState
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel
import timber.log.Timber

@Composable
fun MainContentScreen(
    authState: AuthState,
    persistedCalendarId: String,
    calendars: List<CalendarItem>,
    authViewModel: AuthViewModel,
    activity: MainActivity,
    onSignOut: () -> Unit,
    onShowShiftConfig: () -> Unit,
    onShowCalendarSelection: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header
        item {
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

        // Alarm display logic
        when {
            authState.nextShiftAlarm != null -> {
                item {
                    AlarmCard(
                        shiftMatch = authState.nextShiftAlarm,
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

            persistedCalendarId.isBlank() -> {
                item {
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
            }

            !authState.autoAlarmEnabled -> {
                item {
                    NoAlarmCard(
                        reason = AutoAlarmDisabled(),
                        onActionClick = onShowShiftConfig
                    )
                }
            }

            authState.calendarsLoading || (!authState.calendarEventsLoaded && authState.nextShiftAlarm == null) -> {
                item {
                    NoAlarmCard(
                        reason = if (authState.calendarsLoading) LoadingShifts() else NoShiftFound(),
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
            }
        }

        // Error handling cards
        item {
            when {
                !authState.androidCalendarPermissionGranted && authState.showAndroidCalendarPermissionRationale -> {
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

                !authState.androidCalendarPermissionGranted && authState.calendarPermissionDenied && authState.error?.contains(
                    "Android Kalenderberechtigung"
                ) == true -> {
                    StatusCard(
                        title = "Kalenderberechtigung verweigert",
                        subtitle = authState.error,
                        icon = Icons.Filled.CalendarMonth,
                        isPositive = false,
                        actionButton = {
                            ActionButton(
                                text = "App-Einstellungen öffnen",
                                icon = Icons.Filled.Settings,
                                onClick = {
                                    val intent =
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    val uri = "package:${activity.packageName}".toUri()
                                    intent.data = uri
                                    activity.startActivity(intent)
                                },
                                variant = ButtonVariant.Error
                            )
                        }
                    )
                }

                authState.accessToken.isNullOrBlank() && !authState.calendarsLoading && authState.calendarPermissionDenied -> {
                    StatusCard(
                        title = "Google Kalender Autorisierung fehlgeschlagen",
                        subtitle = authState.error
                            ?: "Autorisierung für Google Kalender fehlgeschlagen.",
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

                authState.error != null && (authState.error.contains("Kalender") || authState.error.contains(
                    "Authentifizierung"
                )) -> {
                    StatusCard(
                        title = "Fehler",
                        subtitle = authState.error,
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

        // Calendar Selection Status
        item {
            StatusCard(
                title = "Dienstplan-Kalender",
                subtitle = if (persistedCalendarId.isNotBlank()) persistedCalendarId else "Kein Kalender ausgewählt",
                icon = Icons.Outlined.CalendarMonth,
                isPositive = persistedCalendarId.isNotBlank(),
                actionButton = if (persistedCalendarId.isNotBlank() && !authState.calendarsLoading) {
                    {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { authViewModel.refreshCalendarEvents() },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kalender prüfen")
                            }
                            OutlinedButton(
                                onClick = onShowCalendarSelection,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Wechseln")
                            }
                        }
                    }
                } else null
            )
        }

        // Loading indicator
        if (authState.calendarsLoading) {
            item {
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

        // Sign out button
        item {
            Spacer(modifier = Modifier.height(32.dp))
            ActionButton(
                text = "Abmelden",
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = onSignOut,
                variant = ButtonVariant.Error
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    CFAlarmForTimeOfficeTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "CF-Alarm",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Willkommen, Test Nutzer!",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item {
                InfoCard(
                    title = "Nächster Wecker",
                    content = {
                        Text("Schicht: Nachtdienst")
                        Text("Termin: IMC Nachtdienst")
                        Text("Weckzeit: 06.06.2025 19:45", fontWeight = FontWeight.Bold)
                    },
                    icon = Icons.Filled.Schedule
                )
            }

            item {
                StatusCard(
                    title = "System-Alarm",
                    subtitle = "Alarm gesetzt für 06.06.2025 19:45",
                    icon = Icons.Filled.NotificationsActive,
                    isPositive = true
                )
            }

            item {
                ActionButton(
                    text = "Schicht-Einstellungen",
                    icon = Icons.Filled.Settings,
                    onClick = { /* Preview */ }
                )
            }
        }
    }
}