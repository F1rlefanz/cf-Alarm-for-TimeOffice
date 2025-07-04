package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase.interfaces.LightAction
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.ErrorMessage
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.LoadingScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.ViewModelFactory

/**
 * Hue Tab Content für die Hauptnavigation
 * Bietet Zugriff auf Bridge Setup, Light Control und Rule Management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueTabContent(
    viewModelFactory: ViewModelFactory,
    modifier: Modifier = Modifier
) {
    val hueViewModel: HueViewModel = viewModel(factory = viewModelFactory)
    val uiState by hueViewModel.uiState.collectAsState()
    val discoveryStatus by hueViewModel.discoveryStatus.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Philips Hue Integration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        // Error Display
        uiState.error?.let { error ->
            ErrorMessage(
                message = error,
                onDismiss = { hueViewModel.clearError() }
            )
        }
        
        // Loading Overlay
        if (uiState.isLoading) {
            LoadingScreen()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bridge Connection Status
                item {
                    BridgeConnectionCard(
                        connectionInfo = uiState.bridgeConnectionInfo,
                        discoveredBridges = uiState.discoveredBridges,
                        discoveryStatus = discoveryStatus,
                        onDiscoverBridges = { hueViewModel.discoverBridges() },
                        onSetupBridge = { bridge -> hueViewModel.setupBridge(bridge) },
                        onValidateConnection = { hueViewModel.validateBridgeConnection() },
                        onClearBridges = { hueViewModel.clearDiscoveredBridges() }
                    )
                }
                
                // Light Control Section
                if (uiState.bridgeConnectionInfo?.isConnected == true) {
                    item {
                        LightControlSection(
                            lightTargets = uiState.lightTargets,
                            onRefreshLights = { hueViewModel.refreshLightTargets() },
                            onExecuteLightAction = { action -> hueViewModel.executeLightAction(action) },
                            onTestConnection = { id, isGroup -> hueViewModel.testLightConnection(id, isGroup) }
                        )
                    }
                    
                    // Rule Management Section
                    item {
                        RuleManagementSection(
                            scheduleRules = uiState.scheduleRules,
                            onRefreshRules = { hueViewModel.refreshRules() },
                            onCreateRule = { rule -> hueViewModel.createRule(rule) },
                            onUpdateRule = { rule -> hueViewModel.updateRule(rule) },
                            onDeleteRule = { ruleId -> hueViewModel.deleteRule(ruleId) },
                            onTestRule = { rule -> hueViewModel.testRuleExecution(rule) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BridgeConnectionCard(
    connectionInfo: BridgeConnectionInfo?,
    discoveredBridges: List<HueBridge>,
    discoveryStatus: DiscoveryStatus?,
    onDiscoverBridges: () -> Unit,
    onSetupBridge: (HueBridge) -> Unit,
    onValidateConnection: () -> Unit,
    onClearBridges: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bridge Connection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (connectionInfo?.isConnected == true) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Connected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Connection Status
            if (connectionInfo != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (connectionInfo.isConnected) "Connected" else "Disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connectionInfo.isConnected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    
                    connectionInfo.bridgeIp?.let { ip ->
                        Text(
                            text = "Bridge IP: $ip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    connectionInfo.lastValidated?.let { timestamp ->
                        Text(
                            text = "Last validated: ${java.text.SimpleDateFormat("HH:mm:ss").format(timestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "No bridge configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Discovery Status
            discoveryStatus?.let { status ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Discovery Status",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        if (!status.isComplete && status.progress > 0) {
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            // Discovered Bridges
            if (discoveredBridges.isNotEmpty()) {
                Text(
                    text = "Discovered Bridges:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                
                discoveredBridges.forEach { bridge ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { onSetupBridge(bridge) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = bridge.name ?: "Hue Bridge",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = bridge.internalipaddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDiscoverBridges,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Discover")
                }
                
                if (connectionInfo?.isConnected == true) {
                    OutlinedButton(
                        onClick = onValidateConnection,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Validate")
                    }
                }
                
                if (discoveredBridges.isNotEmpty()) {
                    TextButton(
                        onClick = onClearBridges
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun LightControlSection(
    lightTargets: com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase.interfaces.LightTargets,
    onRefreshLights: () -> Unit,
    onExecuteLightAction: (LightAction) -> Unit,
    onTestConnection: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Light Control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onRefreshLights) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh lights"
                    )
                }
            }
            
            if (lightTargets.lights.isEmpty() && lightTargets.groups.isEmpty()) {
                Text(
                    text = "No lights or groups found. Make sure your bridge is connected and has lights configured.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Lights
                if (lightTargets.lights.isNotEmpty()) {
                    Text(
                        text = "Lights (${lightTargets.lights.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    lightTargets.lights.take(3).forEach { light ->
                        LightControlItem(
                            name = light.name,
                            id = light.id,
                            isOn = light.on,
                            brightness = light.brightness,
                            isGroup = false,
                            onToggle = { isOn ->
                                onExecuteLightAction(
                                    LightAction(
                                        targetId = light.id,
                                        isGroup = false,
                                        on = isOn
                                    )
                                )
                            },
                            onBrightnessChange = { brightness ->
                                onExecuteLightAction(
                                    LightAction(
                                        targetId = light.id,
                                        isGroup = false,
                                        brightness = brightness
                                    )
                                )
                            },
                            onTest = { onTestConnection(light.id, false) }
                        )
                    }
                    
                    if (lightTargets.lights.size > 3) {
                        Text(
                            text = "... and ${lightTargets.lights.size - 3} more lights",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Groups
                if (lightTargets.groups.isNotEmpty()) {
                    Text(
                        text = "Groups (${lightTargets.groups.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    lightTargets.groups.take(2).forEach { group ->
                        LightControlItem(
                            name = group.name,
                            id = group.id,
                            isOn = group.on,
                            brightness = group.brightness,
                            isGroup = true,
                            onToggle = { isOn ->
                                onExecuteLightAction(
                                    LightAction(
                                        targetId = group.id,
                                        isGroup = true,
                                        on = isOn
                                    )
                                )
                            },
                            onBrightnessChange = { brightness ->
                                onExecuteLightAction(
                                    LightAction(
                                        targetId = group.id,
                                        isGroup = true,
                                        brightness = brightness
                                    )
                                )
                            },
                            onTest = { onTestConnection(group.id, true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LightControlItem(
    name: String,
    id: String,
    isOn: Boolean,
    brightness: Int?,
    isGroup: Boolean,
    onToggle: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${if (isGroup) "Group" else "Light"} ID: $id",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isOn,
                        onCheckedChange = onToggle
                    )
                }
            }
            
            if (isOn && brightness != null) {
                Column {
                    Text(
                        text = "Brightness: ${(brightness * 100 / 254)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { onBrightnessChange(it.toInt()) },
                        valueRange = 1f..254f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            TextButton(
                onClick = onTest,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Test Connection")
            }
        }
    }
}

@Composable
private fun RuleManagementSection(
    scheduleRules: List<HueSchedule>,
    onRefreshRules: () -> Unit,
    onCreateRule: (HueSchedule) -> Unit,
    onUpdateRule: (HueSchedule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onTestRule: (HueSchedule) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Schedule Rules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    IconButton(onClick = onRefreshRules) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh rules"
                        )
                    }
                    IconButton(
                        onClick = {
                            // Create a sample rule for demonstration
                            val sampleRule = HueSchedule(
                                id = "",
                                name = "New Rule",
                                shiftPattern = "Early",
                                timeOffsetMinutes = 0,
                                targetLightIds = emptyList(),
                                targetGroupIds = emptyList(),
                                targetOn = true,
                                targetBrightness = 128
                            )
                            onCreateRule(sampleRule)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add rule"
                        )
                    }
                }
            }
            
            if (scheduleRules.isEmpty()) {
                Text(
                    text = "No schedule rules configured. Create rules to automatically control lights based on your shift schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                scheduleRules.forEach { rule ->
                    RuleItem(
                        rule = rule,
                        onTest = { onTestRule(rule) },
                        onDelete = { onDeleteRule(rule.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleItem(
    rule: HueSchedule,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Row {
                    TextButton(onClick = onTest) {
                        Text("Test")
                    }
                    TextButton(onClick = onDelete) {
                        Text("Delete")
                    }
                }
            }
            
            Column {
                Text(
                    text = "Shift: ${rule.shiftPattern}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Offset: ${rule.timeOffsetMinutes} minutes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Targets: ${rule.targetLightIds.size} lights, ${rule.targetGroupIds.size} groups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
