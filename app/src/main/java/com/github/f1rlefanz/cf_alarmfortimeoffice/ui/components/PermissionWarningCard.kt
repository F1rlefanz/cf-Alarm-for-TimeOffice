package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.dataStore by preferencesDataStore(name = "permission_warnings")

/**
 * Shows warnings for battery optimization settings.
 * 
 * IMPORTANT: There are TWO different types of settings:
 * 1. Android's standard battery optimization (can be requested via API)
 * 2. OnePlus-specific background settings (must be set manually)
 */
@Composable
fun PermissionWarningCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // Android standard battery optimization
    var isIgnoringBatteryOptimization by remember { mutableStateOf(false) }
    
    // OnePlus warning dismissed state
    var isOnePlusWarningDismissed by remember { mutableStateOf(false) }
    val onePlusWarningDismissedKey = booleanPreferencesKey("oneplus_warning_dismissed")
    
    // Load dismissed state
    LaunchedEffect(Unit) {
        isOnePlusWarningDismissed = context.dataStore.data.map { preferences ->
            preferences[onePlusWarningDismissedKey] ?: false
        }.first()
    }
    
    // Check permissions when the screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isIgnoringBatteryOptimization = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                
                Timber.d("PermissionWarningCard: Resume check - Battery optimization ignored: $isIgnoringBatteryOptimization")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Initial check
    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isIgnoringBatteryOptimization = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        
        Timber.d("PermissionWarningCard: Initial check - Battery optimization ignored: $isIgnoringBatteryOptimization")
    }
    
    // Show different cards based on device and state
    when {
        // Any device where battery optimization is not disabled
        !isIgnoringBatteryOptimization -> {
            AndroidBatteryOptimizationCard(
                modifier = modifier,
                onRequestOptimization = {
                    requestBatteryOptimization(context)
                }
            )
        }
        
        // OnePlus device with battery optimization disabled but warning not dismissed
        isOnePlus() && isIgnoringBatteryOptimization && !isOnePlusWarningDismissed -> {
            OnePlusManualSettingsCard(
                modifier = modifier,
                onDismiss = {
                    scope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[onePlusWarningDismissedKey] = true
                        }
                        isOnePlusWarningDismissed = true
                    }
                }
            )
        }
        
        // All permissions granted or warnings dismissed
        else -> {
            // Don't show any card
        }
    }
}

@Composable
private fun AndroidBatteryOptimizationCard(
    modifier: Modifier = Modifier,
    onRequestOptimization: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = "Akku-Warnung",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Akku-Optimierung aktiv",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            Text(
                text = "Die Akku-Optimierung kann dazu führen, dass Wecker nicht zuverlässig funktionieren. Bitte deaktivieren Sie sie für diese App.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Button(
                onClick = onRequestOptimization,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Akku-Optimierung deaktivieren")
            }
        }
    }
}

@Composable
private fun OnePlusManualSettingsCard(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "OnePlus-spezifische Einstellungen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Text(
                text = "OnePlus hat zusätzliche Einstellungen, die manuell angepasst werden müssen:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingItem(
                    icon = Icons.Default.CheckCircle,
                    text = "Akku-Optimierung deaktivieren",
                    isCompleted = true
                )
                
                SettingItem(
                    icon = Icons.Default.Error,
                    text = "Aktivität im Hintergrund erlauben",
                    isCompleted = false,
                    instruction = "Einstellungen → Apps → CF-Alarm → Akku → Aktivieren"
                )
                
                SettingItem(
                    icon = Icons.Default.Error,
                    text = "Automatisch starten erlauben",
                    isCompleted = false,
                    instruction = "Einstellungen → Apps → CF-Alarm → Akku → Aktivieren"
                )
                
                SettingItem(
                    icon = Icons.Default.Error,
                    text = "App-Aktivität bei Nichtnutzung nicht stoppen",
                    isCompleted = false,
                    instruction = "Einstellungen → Apps → CF-Alarm → Akku → Deaktivieren"
                )
            }
            
            Text(
                text = "Diese Einstellungen können NICHT automatisch gesetzt werden und müssen manuell in den App-Einstellungen unter 'Akku' angepasst werden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("App-Einstellungen öffnen")
            }
        }
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isCompleted: Boolean,
    instruction: String? = null
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (!isCompleted) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (instruction != null && !isCompleted) {
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 28.dp)
            )
        }
    }
}

private fun isOnePlus(): Boolean {
    return Build.MANUFACTURER.lowercase() == "oneplus"
}

private fun requestBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "Failed to open battery optimization settings")
        // Fallback to app settings
        openAppSettings(context)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
