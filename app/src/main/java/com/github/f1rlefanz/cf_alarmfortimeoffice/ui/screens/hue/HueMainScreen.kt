package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueConfiguration
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueScheduleRule
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueMainScreen(
    viewModel: HueViewModel,
    onNavigateToSetup: () -> Unit,
    onNavigateToRuleConfig: (String?) -> Unit,
    onNavigateToLightSelection: () -> Unit
) {
    val configuration by viewModel.configuration.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Philips Hue") },
                actions = {
                    if (configuration.bridgeIp != null) {
                        IconButton(onClick = { viewModel.refreshLightsAndGroups() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { /* TODO: Show settings */ }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (configuration.bridgeIp != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToRuleConfig(null) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New Rule") }
                )
            }
        }
    ) { padding ->
        if (configuration.bridgeIp == null) {
            // Not connected
            NotConnectedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onSetupClick = onNavigateToSetup
            )
        } else {
            // Connected
            ConnectedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                configuration = configuration,
                rules = configuration.rules,
                onRuleClick = { onNavigateToRuleConfig(it.id) },
                onRuleToggle = { rule, enabled ->
                    viewModel.updateScheduleRule(rule.copy(enabled = enabled))
                },
                onRuleDelete = { viewModel.deleteScheduleRule(it) },
                onViewLightsClick = onNavigateToLightSelection
            )
        }
    }
}

@Composable
private fun NotConnectedContent(
    modifier: Modifier = Modifier,
    onSetupClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "No Hue Bridge Connected",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Connect to your Philips Hue Bridge to start creating lighting schedules",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp, start = 32.dp, end = 32.dp)
        )
        
        Button(onClick = onSetupClick) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connect Bridge")
        }
    }
}

@Composable
private fun ConnectedContent(
    modifier: Modifier = Modifier,
    configuration: HueConfiguration,
    rules: List<HueScheduleRule>,
    onRuleClick: (HueScheduleRule) -> Unit,
    onRuleToggle: (HueScheduleRule, Boolean) -> Unit,
    onRuleDelete: (String) -> Unit,
    onViewLightsClick: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Bridge info card
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hue Bridge Connected",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = configuration.bridgeIp ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = onViewLightsClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Lights & Groups")
                    }
                }
            }
        }
        
        // Rules section
        if (rules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "No Rules Created",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Create rules to automatically control your lights based on shifts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(rules) { rule ->
                HueRuleCard(
                    rule = rule,
                    onClick = { onRuleClick(rule) },
                    onToggle = { enabled -> onRuleToggle(rule, enabled) },
                    onDelete = { onRuleDelete(rule.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HueRuleCard(
    rule: HueScheduleRule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Shift: ${rule.shiftPattern}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${rule.timeRanges.size} time ranges, ${rule.timeRanges.sumOf { it.actions.size }} actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = onToggle
                    )
                    
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Rule?") },
            text = { Text("Are you sure you want to delete '${rule.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
