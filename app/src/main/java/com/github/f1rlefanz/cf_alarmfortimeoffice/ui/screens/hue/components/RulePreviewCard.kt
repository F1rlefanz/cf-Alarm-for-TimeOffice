package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.ActionType

@Composable
fun RulePreviewCard(
    ruleName: String,
    shiftPattern: String,
    startTime: String,
    endTime: String,
    actionType: ActionType,
    brightness: Int,
    targetCount: Int,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rule Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (isEnabled) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Enabled",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            if (ruleName.isNotBlank()) {
                PreviewRow(
                    icon = Icons.Default.Badge,
                    label = "Name",
                    value = ruleName
                )
            }
            
            if (shiftPattern.isNotBlank()) {
                PreviewRow(
                    icon = Icons.Default.Schedule,
                    label = "Shift",
                    value = shiftPattern
                )
            }
            
            if (startTime.isNotBlank() && endTime.isNotBlank()) {
                PreviewRow(
                    icon = Icons.Default.AccessTime,
                    label = "Time",
                    value = "$startTime - $endTime"
                )
            }
            
            PreviewRow(
                icon = when (actionType) {
                    ActionType.TURN_ON -> Icons.Default.LightMode
                    ActionType.TURN_OFF -> Icons.Default.Lightbulb
                    ActionType.DIM -> Icons.Default.Tune
                    else -> Icons.Default.Settings
                },
                label = "Action",
                value = when (actionType) {
                    ActionType.TURN_ON -> if (brightness < 254) "Turn on (${brightness * 100 / 254}%)" else "Turn on (100%)"
                    ActionType.TURN_OFF -> "Turn off"
                    ActionType.DIM -> "Set brightness (${brightness * 100 / 254}%)"
                    else -> actionType.name
                }
            )
            
            if (targetCount > 0) {
                PreviewRow(
                    icon = Icons.Default.Lightbulb,
                    label = "Targets",
                    value = "$targetCount ${if (targetCount == 1) "device" else "devices"}"
                )
            }
        }
    }
}

@Composable
private fun PreviewRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(60.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
