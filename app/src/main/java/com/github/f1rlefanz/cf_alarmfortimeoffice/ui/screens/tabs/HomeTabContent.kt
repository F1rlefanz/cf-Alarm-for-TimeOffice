package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.SpacingConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.DateTimeFormats
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.CalendarConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AlarmUiState
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.CalendarUiState
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.ShiftUiState
import java.time.format.DateTimeFormatter

@Composable
fun HomeTabContent(
    calendarState: CalendarUiState,
    shiftState: ShiftUiState,
    alarmState: AlarmUiState,
    onRefresh: () -> Unit,
    onShowEventList: (() -> Unit)? = null // LAZY LOADING: Navigation to event details
) {
    val daysAhead = shiftState.currentShiftConfig?.daysAhead ?: CalendarConstants.DEFAULT_DAYS_AHEAD
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
        verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE)
    ) {
        // Header mit Refresh-Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Übersicht",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Aktualisieren",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Nächste Schicht Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacingConstants.PADDING_CARD),
                horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Work,
                    contentDescription = null,
                    modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Nächste Schicht",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (shiftState.upcomingShift != null) {
                        Text(
                            shiftState.upcomingShift.shiftType.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            DateTimeFormatter.ofPattern(DateTimeFormats.STANDARD_DATETIME)
                                .format(shiftState.upcomingShift.startTime),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Keine Schicht erkannt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Alarm Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (alarmState.hasActiveAlarms) 
                    MaterialTheme.colorScheme.secondaryContainer
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacingConstants.PADDING_CARD),
                horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccessAlarm,
                    contentDescription = null,
                    modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE),
                    tint = if (alarmState.hasActiveAlarms)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Alarm-Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (alarmState.hasActiveAlarms) {
                        Text(
                            "${alarmState.activeAlarms.size} aktive Alarme",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        alarmState.nextAlarmTime?.let {
                            Text(
                                "Nächster Alarm: $it",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Text(
                            "Keine aktiven Alarme",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Kalender Events Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onShowEventList?.invoke() } // LAZY LOADING: Make card clickable for event list
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacingConstants.PADDING_CARD),
                verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(SpacingConstants.ICON_SIZE_LARGE),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Kalender-Events",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Divider()
                
                if (calendarState.events.isNotEmpty()) {
                    // LAZY LOADING: Show limited events overview in home tab
                    val displayEventCount = minOf(calendarState.events.size, 5) // Show max 5 events in overview
                    Text("${calendarState.events.size} Events in den nächsten $daysAhead Tagen")
                    Text(
                        "${shiftState.recognizedShifts.size} Schichten erkannt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Show recognized shifts for today and tomorrow
                    val today = java.time.LocalDate.now()
                    val tomorrow = today.plusDays(1)
                    
                    val todayShifts = shiftState.recognizedShifts.filter { 
                        it.startTime.toLocalDate() == today 
                    }
                    val tomorrowShifts = shiftState.recognizedShifts.filter { 
                        it.startTime.toLocalDate() == tomorrow 
                    }
                    
                    if (todayShifts.isNotEmpty() || tomorrowShifts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (todayShifts.isNotEmpty()) {
                                Text(
                                    "Heute: ${todayShifts.joinToString(", ") { it.shiftType.displayName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (tomorrowShifts.isNotEmpty()) {
                                Text(
                                    "Morgen: ${tomorrowShifts.joinToString(", ") { it.shiftType.displayName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    // LAZY LOADING: Show if more events are available
                    if (calendarState.hasMoreEvents) {
                        Text(
                            "Zeige $displayEventCount von ${if (calendarState.totalEvents > 0) calendarState.totalEvents else "mehr"} Events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    // Clickable hint
                    Text(
                        "Antippen für Details →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Keine Events geladen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Loading Indicator
        if (calendarState.isLoading || shiftState.isLoading || alarmState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpacingConstants.SPACING_LARGE),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
