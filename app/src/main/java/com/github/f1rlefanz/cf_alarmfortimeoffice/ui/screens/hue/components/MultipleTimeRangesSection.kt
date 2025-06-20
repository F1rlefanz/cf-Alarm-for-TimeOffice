package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.ActionType
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueLightAction

@Composable
fun MultipleTimeRangesSection(
    timeRanges: List<TimeRangeConfig>,
    onTimeRangesChange: (List<TimeRangeConfig>) -> Unit,
    showValidationErrors: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Time Ranges (${timeRanges.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(
                    onClick = {
                        val newRange = TimeRangeConfig(
                            id = "range_${System.currentTimeMillis()}",
                            startTime = "07:00",
                            endTime = "08:00",
                            actionType = ActionType.TURN_ON,
                            brightness = 254
                        )
                        onTimeRangesChange(timeRanges + newRange)
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Time Range")
                }
            }
            
            if (timeRanges.isEmpty()) {
                EmptyTimeRangesState(
                    onAddFirst = {
                        val newRange = TimeRangeConfig(
                            id = "range_${System.currentTimeMillis()}",
                            startTime = "07:00",
                            endTime = "08:00",
                            actionType = ActionType.TURN_ON,
                            brightness = 254
                        )
                        onTimeRangesChange(listOf(newRange))
                    }
                )
            } else {
                timeRanges.forEachIndexed { index, timeRange ->
                    TimeRangeItem(
                        timeRange = timeRange,
                        onTimeRangeChange = { updatedRange ->
                            val updatedList = timeRanges.toMutableList()
                            updatedList[index] = updatedRange
                            onTimeRangesChange(updatedList)
                        },
                        onDelete = {
                            onTimeRangesChange(timeRanges.filterIndexed { i, _ -> i != index })
                        },
                        showValidationErrors = showValidationErrors,
                        canDelete = timeRanges.size > 1
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeRangesState(
    onAddFirst: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "No Time Ranges",
            style = MaterialTheme.typography.titleMedium
        )
        
        Text(
            text = "Add a time range to define when the rule should be active",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Button(onClick = onAddFirst) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add First Time Range")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeItem(
    timeRange: TimeRangeConfig,
    onTimeRangeChange: (TimeRangeConfig) -> Unit,
    onDelete: () -> Unit,
    showValidationErrors: Boolean,
    canDelete: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Time Range",
                    style = MaterialTheme.typography.titleSmall
                )
                
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // Time inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeInputField(
                    value = timeRange.startTime,
                    onValueChange = { 
                        onTimeRangeChange(timeRange.copy(startTime = it))
                    },
                    label = "Start",
                    modifier = Modifier.weight(1f),
                    isError = showValidationErrors && !isValidTimeFormat(timeRange.startTime),
                    errorMessage = if (!isValidTimeFormat(timeRange.startTime)) "Invalid format" else null
                )
                
                TimeInputField(
                    value = timeRange.endTime,
                    onValueChange = { 
                        onTimeRangeChange(timeRange.copy(endTime = it))
                    },
                    label = "End",
                    modifier = Modifier.weight(1f),
                    isError = showValidationErrors && (!isValidTimeFormat(timeRange.endTime) || !isValidTimeRange(timeRange.startTime, timeRange.endTime)),
                    errorMessage = when {
                        !isValidTimeFormat(timeRange.endTime) -> "Invalid format"
                        !isValidTimeRange(timeRange.startTime, timeRange.endTime) -> "Must be after start"
                        else -> null
                    }
                )
            }
            
            // Action selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = timeRange.actionType == ActionType.TURN_ON,
                    onClick = { onTimeRangeChange(timeRange.copy(actionType = ActionType.TURN_ON)) },
                    label = { Text("On") }
                )
                FilterChip(
                    selected = timeRange.actionType == ActionType.TURN_OFF,
                    onClick = { onTimeRangeChange(timeRange.copy(actionType = ActionType.TURN_OFF)) },
                    label = { Text("Off") }
                )
                FilterChip(
                    selected = timeRange.actionType == ActionType.DIM,
                    onClick = { onTimeRangeChange(timeRange.copy(actionType = ActionType.DIM)) },
                    label = { Text("Dim") }
                )
            }
            
            // Brightness slider
            if (timeRange.actionType != ActionType.TURN_OFF) {
                Column {
                    Text(
                        text = "Brightness: ${(timeRange.brightness * 100 / 254)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = timeRange.brightness.toFloat(),
                        onValueChange = { 
                            onTimeRangeChange(timeRange.copy(brightness = it.toInt()))
                        },
                        valueRange = 1f..254f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

data class TimeRangeConfig(
    val id: String,
    val startTime: String,
    val endTime: String,
    val actionType: ActionType,
    val brightness: Int
)
