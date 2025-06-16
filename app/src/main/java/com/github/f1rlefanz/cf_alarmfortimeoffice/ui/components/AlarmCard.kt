package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch


@Composable
fun AlarmCard(
    shiftMatch: ShiftMatch,
    systemAlarmSet: Boolean,
    canScheduleExactAlarms: Boolean,
    onCancelAlarm: () -> Unit,
    onSetAlarm: () -> Unit,
    onRequestAlarmPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header mit Icon und Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (systemAlarmSet) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
                    contentDescription = null,
                    tint = if (systemAlarmSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nächster Wecker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (systemAlarmSet) "Alarm aktiv" else "Alarm nicht gesetzt",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (systemAlarmSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider()

            // Countdown Timer
            CountdownTimer(
                targetTime = shiftMatch.calculatedAlarmTime,
                onTimeUp = { /* Handled by system */ }
            )

            // Schicht-Details
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DetailRow(
                    label = "Schicht:",
                    value = shiftMatch.shiftDefinition.name
                )
                DetailRow(
                    label = "Termin:",
                    value = shiftMatch.calendarEventTitle
                )
                DetailRow(
                    label = "Weckzeit:",
                    value = shiftMatch.formattedAlarmTime,
                    highlight = true
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!canScheduleExactAlarms) {
                    Button(
                        onClick = onRequestAlarmPermission,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Berechtigung erteilen")
                    }
                } else if (systemAlarmSet) {
                    OutlinedButton(
                        onClick = onCancelAlarm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Alarm abbrechen")
                    }
                } else {
                    // Alarm nicht gesetzt, aber kann gesetzt werden
                    Button(
                        onClick = onSetAlarm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.AlarmAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Alarm setzen")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
