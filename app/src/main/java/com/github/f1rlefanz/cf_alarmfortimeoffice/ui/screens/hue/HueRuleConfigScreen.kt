package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.DefaultShiftDefinitions
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.components.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueUiState
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueRuleConfigScreen(
    viewModel: HueViewModel,
    ruleId: String?,
    onNavigateBack: () -> Unit
) {
    val configuration by viewModel.configuration.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Find existing rule if editing
    val existingRule = ruleId?.let { id ->
        configuration.rules.find { it.id == id }
    }
    
    // Rule configuration state
    var ruleName by remember { mutableStateOf(existingRule?.name ?: "") }
    var selectedShiftPattern by remember { mutableStateOf(existingRule?.shiftPattern ?: "") }
    var isEnabled by remember { mutableStateOf(existingRule?.enabled != false) }
    var showShiftDropdown by remember { mutableStateOf(false) }
    
    // Time range state
    var startTime by remember { mutableStateOf("07:00") }
    var endTime by remember { mutableStateOf("08:00") }
    var actionType by remember { mutableStateOf(ActionType.TURN_ON) }
    var brightness by remember { mutableIntStateOf(254) }
    
    // Validation state
    var showValidationErrors by remember { mutableStateOf(false) }
    
    // Selected targets state
    var selectedLights by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Initialize with existing rule data
    LaunchedEffect(existingRule) {
        existingRule?.let { rule ->
            if (rule.timeRanges.isNotEmpty()) {
                val firstRange = rule.timeRanges.first()
                startTime = firstRange.startTime
                endTime = firstRange.endTime
                
                if (firstRange.actions.isNotEmpty()) {
                    val firstAction = firstRange.actions.first()
                    actionType = firstAction.actionType
                    brightness = firstAction.brightness ?: 254
                    
                    // Set selected targets
                    when (firstAction.targetType) {
                        TargetType.LIGHT -> selectedLights = selectedLights + firstAction.targetId
                        TargetType.GROUP, TargetType.ROOM, TargetType.ZONE -> selectedGroups = selectedGroups + firstAction.targetId
                    }
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == null) "New Rule" else "Edit Rule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            showValidationErrors = true
                            if (validateRule(ruleName, selectedShiftPattern, startTime, endTime, selectedLights, selectedGroups)) {
                                saveRule(
                                    viewModel = viewModel,
                                    ruleId = ruleId,
                                    ruleName = ruleName,
                                    selectedShiftPattern = selectedShiftPattern,
                                    isEnabled = isEnabled,
                                    startTime = startTime,
                                    endTime = endTime,
                                    actionType = actionType,
                                    brightness = brightness,
                                    selectedLights = selectedLights,
                                    selectedGroups = selectedGroups,
                                    uiState = uiState,
                                    onNavigateBack = onNavigateBack
                                )
                            }
                        }
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Basic Rule Information
            BasicRuleInfoSection(
                ruleName = ruleName,
                onRuleNameChange = { ruleName = it },
                selectedShiftPattern = selectedShiftPattern,
                onShiftPatternChange = { selectedShiftPattern = it },
                showShiftDropdown = showShiftDropdown,
                onShowShiftDropdownChange = { showShiftDropdown = it },
                isEnabled = isEnabled,
                onEnabledChange = { isEnabled = it }
            )
            
            // Time Range Configuration
            TimeRangeSection(
                startTime = startTime,
                onStartTimeChange = { startTime = it },
                endTime = endTime,
                onEndTimeChange = { endTime = it },
                actionType = actionType,
                onActionTypeChange = { actionType = it },
                brightness = brightness,
                onBrightnessChange = { brightness = it },
                showValidationErrors = showValidationErrors
            )
            
            // Target Selection
            TargetSelectionSection(
                lights = uiState.lights,
                groups = uiState.groups,
                selectedLights = selectedLights,
                onSelectedLightsChange = { selectedLights = it },
                selectedGroups = selectedGroups,
                onSelectedGroupsChange = { selectedGroups = it }
            )
            
            // Rule Preview
            if (ruleName.isNotBlank() || selectedShiftPattern.isNotBlank() || selectedLights.isNotEmpty() || selectedGroups.isNotEmpty()) {
                RulePreviewCard(
                    ruleName = ruleName,
                    shiftPattern = selectedShiftPattern,
                    startTime = startTime,
                    endTime = endTime,
                    actionType = actionType,
                    brightness = brightness,
                    targetCount = selectedLights.size + selectedGroups.size,
                    isEnabled = isEnabled
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicRuleInfoSection(
    ruleName: String,
    onRuleNameChange: (String) -> Unit,
    selectedShiftPattern: String,
    onShiftPatternChange: (String) -> Unit,
    showShiftDropdown: Boolean,
    onShowShiftDropdownChange: (Boolean) -> Unit,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Rule Information",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Rule Name
            OutlinedTextField(
                value = ruleName,
                onValueChange = onRuleNameChange,
                label = { Text("Rule Name") },
                placeholder = { Text("Enter rule name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Shift Pattern Selection
            ExposedDropdownMenuBox(
                expanded = showShiftDropdown,
                onExpandedChange = onShowShiftDropdownChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedShiftPattern,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Shift Pattern") },
                    placeholder = { Text("Select shift pattern") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showShiftDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = showShiftDropdown,
                    onDismissRequest = { onShowShiftDropdownChange(false) }
                ) {
                    DefaultShiftDefinitions.predefined.forEach { shiftDef ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(shiftDef.name)
                                    Text(
                                        text = "Alarm: ${shiftDef.alarmTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onShiftPatternChange(shiftDef.name)
                                onShowShiftDropdownChange(false)
                            }
                        )
                    }
                }
            }
            
            // Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Enable Rule",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Rule will be active when enabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}

@Composable
private fun TimeRangeSection(
    startTime: String,
    onStartTimeChange: (String) -> Unit,
    endTime: String,
    onEndTimeChange: (String) -> Unit,
    actionType: ActionType,
    onActionTypeChange: (ActionType) -> Unit,
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    showValidationErrors: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Time Range & Actions",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start Time
                TimeInputField(
                    value = startTime,
                    onValueChange = onStartTimeChange,
                    label = "Start Time",
                    modifier = Modifier.weight(1f),
                    isError = showValidationErrors && !isValidTimeFormat(startTime),
                    errorMessage = if (!isValidTimeFormat(startTime)) "Invalid time format" else null
                )
                
                // End Time
                TimeInputField(
                    value = endTime,
                    onValueChange = onEndTimeChange,
                    label = "End Time",
                    modifier = Modifier.weight(1f),
                    isError = showValidationErrors && (!isValidTimeFormat(endTime) || !isValidTimeRange(startTime, endTime)),
                    errorMessage = when {
                        !isValidTimeFormat(endTime) -> "Invalid time format"
                        !isValidTimeRange(startTime, endTime) -> "End time must be after start time"
                        else -> null
                    }
                )
            }
            
            // Action Type
            Text(
                text = "Action",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = actionType == ActionType.TURN_ON,
                    onClick = { onActionTypeChange(ActionType.TURN_ON) },
                    label = { Text("Turn On") }
                )
                FilterChip(
                    selected = actionType == ActionType.TURN_OFF,
                    onClick = { onActionTypeChange(ActionType.TURN_OFF) },
                    label = { Text("Turn Off") }
                )
                FilterChip(
                    selected = actionType == ActionType.DIM,
                    onClick = { onActionTypeChange(ActionType.DIM) },
                    label = { Text("Set Brightness") }
                )
            }
            
            // Brightness Slider (only if not TURN_OFF)
            if (actionType != ActionType.TURN_OFF) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Brightness: ${(brightness * 100 / 254)}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { onBrightnessChange(it.toInt()) },
                        valueRange = 1f..254f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetSelectionSection(
    lights: Map<String, HueLight>,
    groups: Map<String, HueGroup>,
    selectedLights: Set<String>,
    onSelectedLightsChange: (Set<String>) -> Unit,
    selectedGroups: Set<String>,
    onSelectedGroupsChange: (Set<String>) -> Unit
) {
    var showLights by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Target Selection",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Tab selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showLights,
                    onClick = { showLights = true },
                    label = { Text("Lights (${lights.size})") }
                )
                FilterChip(
                    selected = !showLights,
                    onClick = { showLights = false },
                    label = { Text("Groups (${groups.size})") }
                )
            }
            
            if (showLights) {
                if (lights.isEmpty()) {
                    Text(
                        text = "No lights available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    lights.forEach { (lightId, light) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = light.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = light.type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = selectedLights.contains(lightId),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onSelectedLightsChange(selectedLights + lightId)
                                    } else {
                                        onSelectedLightsChange(selectedLights - lightId)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                if (groups.isEmpty()) {
                    Text(
                        text = "No groups available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groups.forEach { (groupId, group) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${group.lights.size} lights",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = selectedGroups.contains(groupId),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onSelectedGroupsChange(selectedGroups + groupId)
                                    } else {
                                        onSelectedGroupsChange(selectedGroups - groupId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Selected summary
            val totalSelected = selectedLights.size + selectedGroups.size
            if (totalSelected > 0) {
                Text(
                    text = "$totalSelected targets selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun saveRule(
    viewModel: HueViewModel,
    ruleId: String?,
    ruleName: String,
    selectedShiftPattern: String,
    isEnabled: Boolean,
    startTime: String,
    endTime: String,
    actionType: ActionType,
    brightness: Int,
    selectedLights: Set<String>,
    selectedGroups: Set<String>,
    uiState: HueUiState,
    onNavigateBack: () -> Unit
) {
    // Create actions for all selected targets
    val actions = mutableListOf<HueLightAction>()
    
    // Add light actions
    selectedLights.forEach { lightId ->
        val light = uiState.lights[lightId]
        actions.add(
            HueLightAction(
                targetType = TargetType.LIGHT,
                targetId = lightId,
                targetName = light?.name,
                actionType = actionType,
                brightness = if (actionType != ActionType.TURN_OFF) brightness else null
            )
        )
    }
    
    // Add group actions
    selectedGroups.forEach { groupId ->
        val group = uiState.groups[groupId]
        actions.add(
            HueLightAction(
                targetType = TargetType.GROUP,
                targetId = groupId,
                targetName = group?.name,
                actionType = actionType,
                brightness = if (actionType != ActionType.TURN_OFF) brightness else null
            )
        )
    }
    
    // Create time range
    val timeRange = HueTimeRange(
        startTime = startTime,
        endTime = endTime,
        actions = actions
    )
    
    // Create or update rule
    val rule = HueScheduleRule(
        id = ruleId ?: HueScheduleRule.generateId(),
        name = ruleName,
        shiftPattern = selectedShiftPattern,
        enabled = isEnabled,
        timeRanges = listOf(timeRange)
    )
    
    // Save rule
    if (ruleId == null) {
        viewModel.addScheduleRule(rule)
    } else {
        viewModel.updateScheduleRule(rule)
    }
    
    // Navigate back
    onNavigateBack()
}

private fun validateRule(
    ruleName: String,
    selectedShiftPattern: String,
    startTime: String,
    endTime: String,
    selectedLights: Set<String>,
    selectedGroups: Set<String>
): Boolean {
    return ruleName.isNotBlank() &&
            selectedShiftPattern.isNotBlank() &&
            isValidTimeFormat(startTime) &&
            isValidTimeFormat(endTime) &&
            isValidTimeRange(startTime, endTime) &&
            (selectedLights.isNotEmpty() || selectedGroups.isNotEmpty())
}
