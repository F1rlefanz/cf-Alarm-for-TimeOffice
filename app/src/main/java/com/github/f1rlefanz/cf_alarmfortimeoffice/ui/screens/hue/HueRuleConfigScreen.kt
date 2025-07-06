package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.ErrorMessage
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.LoadingScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.ViewModelFactory
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.SpacingConstants

/**
 * Hue Rule Configuration Screen - Create and Edit Schedule Rules
 * 
 * Provides comprehensive rule creation and editing interface.
 * Handles shift pattern selection, timing configuration, light targeting, and actions.
 * 
 * Features:
 * - Rule creation and editing (CRUD operations)
 * - Shift pattern selection with validation
 * - Time range configuration with multiple actions
 * - Light and group target selection
 * - Action configuration (on/off, brightness, color)
 * - Rule preview and testing
 * - Form validation and error handling
 * 
 * Architecture:
 * - Form-based UI with validation
 * - Reactive state management
 * - Material Design 3 components
 * - Modular component structure
 * 
 * @author CF-Alarm Development Team
 * @since Hue Integration v2
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueRuleConfigScreen(
    ruleId: String?,
    viewModelFactory: ViewModelFactory,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hueViewModel: HueViewModel = viewModel(factory = viewModelFactory)
    val uiState by hueViewModel.uiState.collectAsState()
    
    // Form state
    var ruleName by remember { mutableStateOf("") }
    var selectedShiftPattern by remember { mutableStateOf("") }
    var timeOffsetMinutes by remember { mutableIntStateOf(0) }
    var selectedLightIds by remember { mutableStateOf(setOf<String>()) }
    var selectedGroupIds by remember { mutableStateOf(setOf<String>()) }
    var targetOn by remember { mutableStateOf(true) }
    var targetBrightness by remember { mutableIntStateOf(128) }
    var isEnabled by remember { mutableStateOf(true) }
    
    // Validation state
    var showValidationErrors by remember { mutableStateOf(false) }
    
    // Load existing rule if editing
    LaunchedEffect(ruleId) {
        ruleId?.let { id ->
            uiState.scheduleRules.find { it.id == id }?.let { rule ->
                ruleName = rule.name
                selectedShiftPattern = rule.shiftPattern
                isEnabled = rule.enabled
                
                // Extract simplified data from timeRanges for editing
                rule.timeRanges.firstOrNull()?.let { firstRange ->
                    timeOffsetMinutes = firstRange.offsetMinutes
                    firstRange.actions.firstOrNull()?.let { firstAction ->
                        targetOn = firstAction.on ?: true
                        targetBrightness = firstAction.brightness ?: 128
                        
                        // Collect target IDs based on action type
                        when (firstAction.targetType) {
                            TargetType.LIGHT -> selectedLightIds = setOf(firstAction.targetId)
                            TargetType.GROUP, TargetType.ROOM, TargetType.ZONE -> selectedGroupIds = setOf(firstAction.targetId)
                        }
                    }
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (ruleId != null) "Edit Rule" else "New Rule",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Save button
                    TextButton(
                        onClick = {
                            if (validateForm(ruleName, selectedShiftPattern, selectedLightIds, selectedGroupIds)) {
                                // Create actions from form data
                                val actions = mutableListOf<HueLightAction>()
                                
                                // Add light actions
                                selectedLightIds.forEach { lightId ->
                                    actions.add(
                                        HueLightAction(
                                            targetType = TargetType.LIGHT,
                                            targetId = lightId,
                                            actionType = if (targetOn) ActionType.TURN_ON else ActionType.TURN_OFF,
                                            on = targetOn,
                                            brightness = if (targetOn) targetBrightness else null
                                        )
                                    )
                                }
                                
                                // Add group actions
                                selectedGroupIds.forEach { groupId ->
                                    actions.add(
                                        HueLightAction(
                                            targetType = TargetType.GROUP,
                                            targetId = groupId,
                                            actionType = if (targetOn) ActionType.TURN_ON else ActionType.TURN_OFF,
                                            on = targetOn,
                                            brightness = if (targetOn) targetBrightness else null,
                                            isGroup = true
                                        )
                                    )
                                }
                                
                                val timeRange = HueTimeRange(
                                    startTime = "00:00",
                                    endTime = "23:59",
                                    relativeTo = TimeReference.SHIFT_START,
                                    offsetMinutes = timeOffsetMinutes,
                                    actions = actions
                                )
                                
                                val rule = HueScheduleRule(
                                    id = ruleId ?: HueScheduleRule.generateId(),
                                    name = ruleName,
                                    shiftPattern = selectedShiftPattern,
                                    enabled = isEnabled,
                                    timeRanges = listOf(timeRange)
                                )
                                
                                if (ruleId != null) {
                                    hueViewModel.updateRule(rule)
                                } else {
                                    hueViewModel.createRule(rule)
                                }
                                onSaveComplete()
                            } else {
                                showValidationErrors = true
                            }
                        }
                    ) {
                        Text(
                            "Save",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
                verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE)
            ) {
                // Error display
                uiState.error?.let { error ->
                    item {
                        ErrorMessage(
                            message = error,
                            onDismiss = { hueViewModel.clearError() }
                        )
                    }
                }
                
                // Rule basic info section
                item {
                    RuleBasicInfoSection(
                        ruleName = ruleName,
                        onRuleNameChange = { ruleName = it },
                        isEnabled = isEnabled,
                        onEnabledChange = { isEnabled = it },
                        showValidationErrors = showValidationErrors
                    )
                }
                
                // Shift pattern selection section
                item {
                    ShiftPatternSection(
                        selectedShiftPattern = selectedShiftPattern,
                        onShiftPatternChange = { selectedShiftPattern = it },
                        showValidationErrors = showValidationErrors
                    )
                }
                
                // Time configuration section
                item {
                    TimeConfigSection(
                        timeOffsetMinutes = timeOffsetMinutes,
                        onTimeOffsetChange = { timeOffsetMinutes = it }
                    )
                }
                
                // Target selection section
                item {
                    TargetSelectionSection(
                        lightTargets = uiState.lightTargets,
                        selectedLightIds = selectedLightIds,
                        selectedGroupIds = selectedGroupIds,
                        onLightSelectionChange = { selectedLightIds = it },
                        onGroupSelectionChange = { selectedGroupIds = it },
                        onRefreshTargets = { hueViewModel.refreshLightTargets() },
                        showValidationErrors = showValidationErrors
                    )
                }
                
                // Action configuration section
                item {
                    ActionConfigSection(
                        targetOn = targetOn,
                        targetBrightness = targetBrightness,
                        onTargetOnChange = { targetOn = it },
                        onTargetBrightnessChange = { targetBrightness = it }
                    )
                }
                
                // Rule preview section
                item {
                    RulePreviewSection(
                        ruleName = ruleName,
                        selectedShiftPattern = selectedShiftPattern,
                        timeOffsetMinutes = timeOffsetMinutes,
                        selectedLightIds = selectedLightIds,
                        selectedGroupIds = selectedGroupIds,
                        targetOn = targetOn,
                        targetBrightness = targetBrightness,
                        isEnabled = isEnabled,
                        onTestRule = {
                            // Create actions from form data for testing
                            val actions = mutableListOf<HueLightAction>()
                            
                            // Add light actions
                            selectedLightIds.forEach { lightId ->
                                actions.add(
                                    HueLightAction(
                                        targetType = TargetType.LIGHT,
                                        targetId = lightId,
                                        actionType = if (targetOn) ActionType.TURN_ON else ActionType.TURN_OFF,
                                        on = targetOn,
                                        brightness = if (targetOn) targetBrightness else null
                                    )
                                )
                            }
                            
                            // Add group actions
                            selectedGroupIds.forEach { groupId ->
                                actions.add(
                                    HueLightAction(
                                        targetType = TargetType.GROUP,
                                        targetId = groupId,
                                        actionType = if (targetOn) ActionType.TURN_ON else ActionType.TURN_OFF,
                                        on = targetOn,
                                        brightness = if (targetOn) targetBrightness else null,
                                        isGroup = true
                                    )
                                )
                            }
                            
                            val timeRange = HueTimeRange(
                                startTime = "00:00",
                                endTime = "23:59",
                                relativeTo = TimeReference.SHIFT_START,
                                offsetMinutes = timeOffsetMinutes,
                                actions = actions
                            )
                            
                            val testRule = HueScheduleRule(
                                id = "test_${System.currentTimeMillis()}",
                                name = ruleName.ifBlank { "Test Rule" },
                                shiftPattern = selectedShiftPattern,
                                enabled = true,
                                timeRanges = listOf(timeRange)
                            )
                            hueViewModel.testRuleExecution(testRule)
                        }
                    )
                }
            }
            
            // Loading overlay
            if (uiState.isLoading) {
                LoadingScreen()
            }
        }
    }
}

/**
 * Rule Basic Info Section
 */
@Composable
private fun RuleBasicInfoSection(
    ruleName: String,
    onRuleNameChange: (String) -> Unit,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    showValidationErrors: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
        ) {
            Text(
                text = "Rule Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Rule name input
            OutlinedTextField(
                value = ruleName,
                onValueChange = onRuleNameChange,
                label = { Text("Rule Name") },
                placeholder = { Text("e.g., Morning Light Early Shift") },
                modifier = Modifier.fillMaxWidth(),
                isError = showValidationErrors && ruleName.isBlank(),
                supportingText = {
                    if (showValidationErrors && ruleName.isBlank()) {
                        Text(
                            "Rule name is required",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
            
            // Enabled switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Enable Rule",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Rule will automatically execute when conditions are met",
                        style = MaterialTheme.typography.bodySmall,
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

/**
 * Shift Pattern Selection Section
 */
@Composable
private fun ShiftPatternSection(
    selectedShiftPattern: String,
    onShiftPatternChange: (String) -> Unit,
    showValidationErrors: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
        ) {
            Text(
                text = "Shift Pattern",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Select which shift pattern this rule applies to:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Shift pattern options
            val shiftPatterns = listOf("Early", "Late", "Night", "S1", "S2", "S3", "Weekend")
            
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL)
            ) {
                shiftPatterns.forEach { pattern ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedShiftPattern == pattern,
                            onClick = { onShiftPatternChange(pattern) }
                        )
                        Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
                        Text(
                            text = pattern,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            
            if (showValidationErrors && selectedShiftPattern.isBlank()) {
                Text(
                    text = "Please select a shift pattern",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Time Configuration Section
 */
@Composable
private fun TimeConfigSection(
    timeOffsetMinutes: Int,
    onTimeOffsetChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
        ) {
            Text(
                text = "Timing Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "When should this rule execute relative to your shift start?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Time offset slider
            Column(
                verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL)
            ) {
                Text(
                    text = "Time Offset: ${if (timeOffsetMinutes >= 0) "+" else ""}$timeOffsetMinutes minutes",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Slider(
                    value = timeOffsetMinutes.toFloat(),
                    onValueChange = { onTimeOffsetChange(it.toInt()) },
                    valueRange = -60f..60f,
                    steps = 23, // Every 5 minutes
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "60 min before",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "60 min after",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = when {
                        timeOffsetMinutes < 0 -> "Execute ${-timeOffsetMinutes} minutes before shift start"
                        timeOffsetMinutes > 0 -> "Execute $timeOffsetMinutes minutes after shift start"
                        else -> "Execute exactly at shift start time"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Target Selection Section
 */
@Composable
private fun TargetSelectionSection(
    lightTargets: com.github.f1rlefanz.cf_alarmfortimeoffice.hue.usecase.interfaces.LightTargets,
    selectedLightIds: Set<String>,
    selectedGroupIds: Set<String>,
    onLightSelectionChange: (Set<String>) -> Unit,
    onGroupSelectionChange: (Set<String>) -> Unit,
    onRefreshTargets: () -> Unit,
    showValidationErrors: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target Selection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onRefreshTargets) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh lights"
                    )
                }
            }
            
            Text(
                text = "Select which lights or groups this rule should control:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Tab selection for lights vs groups
            var selectedTab by remember { mutableIntStateOf(0) }
            
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Groups (${lightTargets.groups.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Lights (${lightTargets.lights.size})") }
                )
            }
            
            // Target selection content
            when (selectedTab) {
                0 -> {
                    // Groups selection
                    if (lightTargets.groups.isEmpty()) {
                        Text(
                            text = "No groups found. Create groups in the Hue app first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lightTargets.groups.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedGroupIds.contains(group.id),
                                    onCheckedChange = { isChecked ->
                                        onGroupSelectionChange(
                                            if (isChecked) {
                                                selectedGroupIds + group.id
                                            } else {
                                                selectedGroupIds - group.id
                                            }
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
                                    Column {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Group • ${if (group.state.any_on) "On" else "Off"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                            }
                        }
                    }
                }
                1 -> {
                    // Individual lights selection
                    if (lightTargets.lights.isEmpty()) {
                        Text(
                            text = "No lights found. Make sure your bridge is connected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lightTargets.lights.forEach { light ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedLightIds.contains(light.id),
                                    onCheckedChange = { isChecked ->
                                        onLightSelectionChange(
                                            if (isChecked) {
                                                selectedLightIds + light.id
                                            } else {
                                                selectedLightIds - light.id
                                            }
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
                                    Column {
                                        Text(
                                            text = light.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Light • ${if (light.state.on) "On" else "Off"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                            }
                        }
                    }
                }
            }
            
            if (showValidationErrors && selectedLightIds.isEmpty() && selectedGroupIds.isEmpty()) {
                Text(
                    text = "Please select at least one light or group",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // Selection summary
            if (selectedLightIds.isNotEmpty() || selectedGroupIds.isNotEmpty()) {
                Text(
                    text = "Selected: ${selectedLightIds.size} lights, ${selectedGroupIds.size} groups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Action Configuration Section
 */
@Composable
private fun ActionConfigSection(
    targetOn: Boolean,
    targetBrightness: Int,
    onTargetOnChange: (Boolean) -> Unit,
    onTargetBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
        ) {
            Text(
                text = "Action Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Configure what should happen when this rule executes:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // On/Off toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Turn ${if (targetOn) "On" else "Off"}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Switch lights ${if (targetOn) "on" else "off"} when rule executes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = targetOn,
                    onCheckedChange = onTargetOnChange
                )
            }
            
            // Brightness control (only if turning on)
            if (targetOn) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL)
                ) {
                    Text(
                        text = "Brightness: ${(targetBrightness * 100 / 254)}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Slider(
                        value = targetBrightness.toFloat(),
                        onValueChange = { onTargetBrightnessChange(it.toInt()) },
                        valueRange = 1f..254f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Rule Preview Section
 */
@Composable
private fun RulePreviewSection(
    ruleName: String,
    selectedShiftPattern: String,
    timeOffsetMinutes: Int,
    selectedLightIds: Set<String>,
    selectedGroupIds: Set<String>,
    targetOn: Boolean,
    targetBrightness: Int,
    isEnabled: Boolean,
    onTestRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(SpacingConstants.PADDING_SCREEN_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
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
                
                Button(
                    onClick = onTestRule,
                    enabled = ruleName.isNotBlank() && 
                            selectedShiftPattern.isNotBlank() && 
                            (selectedLightIds.isNotEmpty() || selectedGroupIds.isNotEmpty())
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
                    Text("Test Rule")
                }
            }
            
            // Rule summary
            if (ruleName.isNotBlank()) {
                Text(
                    text = "\"$ruleName\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (selectedShiftPattern.isNotBlank()) {
                Text(
                    text = "When working $selectedShiftPattern shift:",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            if (timeOffsetMinutes != 0 || selectedShiftPattern.isNotBlank()) {
                val timeText = when {
                    timeOffsetMinutes < 0 -> "${-timeOffsetMinutes} minutes before shift start"
                    timeOffsetMinutes > 0 -> "$timeOffsetMinutes minutes after shift start"
                    else -> "exactly at shift start"
                }
                Text(
                    text = "Execute $timeText",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            if (selectedLightIds.isNotEmpty() || selectedGroupIds.isNotEmpty()) {
                Text(
                    text = "Turn ${if (targetOn) "on" else "off"} ${selectedLightIds.size} lights and ${selectedGroupIds.size} groups${if (targetOn) " at ${(targetBrightness * 100 / 254)}% brightness" else ""}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Text(
                text = "Status: ${if (isEnabled) "Enabled" else "Disabled"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Form validation helper
 */
private fun validateForm(
    ruleName: String,
    selectedShiftPattern: String,
    selectedLightIds: Set<String>,
    selectedGroupIds: Set<String>
): Boolean {
    return ruleName.isNotBlank() &&
            selectedShiftPattern.isNotBlank() &&
            (selectedLightIds.isNotEmpty() || selectedGroupIds.isNotEmpty())
}
