package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.DiscoveryMethod
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.DiscoveryStatus

/**
 * Card that displays the current discovery status with animations
 */
@Composable
fun DiscoveryStatusCard(
    discoveryStatus: DiscoveryStatus,
    modifier: Modifier = Modifier
) {
    // Rotation animation for the loading icon
    val infiniteTransition = rememberInfiniteTransition(label = "discovery_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discovery_rotation_animation"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (discoveryStatus.currentMethod) {
                DiscoveryMethod.FAILED -> MaterialTheme.colorScheme.errorContainer
                DiscoveryMethod.COMPLETE -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon based on current method
                when (discoveryStatus.currentMethod) {
                    DiscoveryMethod.IDLE -> {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DiscoveryMethod.ONLINE_DISCOVERY -> {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation)
                        )
                    }
                    DiscoveryMethod.LOCAL_NETWORK_SCAN -> {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation)
                        )
                    }
                    DiscoveryMethod.TESTING_CONNECTION -> {
                        Icon(
                            Icons.Default.Router,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DiscoveryMethod.COMPLETE -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DiscoveryMethod.FAILED -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
                
                // Main message
                Text(
                    text = discoveryStatus.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (discoveryStatus.currentMethod) {
                        DiscoveryMethod.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                        DiscoveryMethod.COMPLETE -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Progress bar if available
            discoveryStatus.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = when (discoveryStatus.currentMethod) {
                        DiscoveryMethod.FAILED -> MaterialTheme.colorScheme.error
                        DiscoveryMethod.COMPLETE -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            
            // Detail message if available
            discoveryStatus.detailMessage?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (discoveryStatus.currentMethod) {
                        DiscoveryMethod.FAILED -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        DiscoveryMethod.COMPLETE -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    }
                )
            }
            
            // Method-specific additional info
            when (discoveryStatus.currentMethod) {
                DiscoveryMethod.ONLINE_DISCOVERY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Nutzt Philips Cloud-Dienst für schnelle Erkennung",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                DiscoveryMethod.LOCAL_NETWORK_SCAN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Durchsucht Ihr lokales Netzwerk nach Bridges",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                else -> { /* No additional info */ }
            }
        }
    }
}
