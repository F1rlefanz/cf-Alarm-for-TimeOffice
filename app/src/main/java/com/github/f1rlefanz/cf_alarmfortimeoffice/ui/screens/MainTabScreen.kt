package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.HueBridgeSetupScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.HueMainScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.HueLightSelectionScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.HueRuleConfigScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.CalendarSelectionScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScreen(
    authViewModel: AuthViewModel,
    activity: MainActivity,
    onSignOut: () -> Unit
) {
    val hueViewModel: HueViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showHueSetup by remember { mutableStateOf(false) }
    var showCalendarSelection by remember { mutableStateOf(false) }
    var tempSelectedCalendarId by remember { mutableStateOf("") }
    
    // Hue navigation states
    var showHueLightSelection by remember { mutableStateOf(false) }
    var showHueRuleConfig by remember { mutableStateOf(false) }
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    
    // Observe calendar selection flag
    val shouldShowCalendarSelection by authViewModel.shouldShowCalendarSelection.collectAsState()

    // React to calendar selection flag
    LaunchedEffect(shouldShowCalendarSelection) {
        if (shouldShowCalendarSelection) {
            showCalendarSelection = true
            authViewModel.clearCalendarSelectionFlag()
        }
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    label = { Text("Schichten") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
                    label = { Text("Hue") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    // Main content screen with calendar selection overlay
                    val authState by authViewModel.authState.collectAsState()
                    val calendars by authViewModel.calendars.collectAsState()
                    val persistedCalendarId by authViewModel.persistedCalendarId.collectAsState("")
                    
                    if (showCalendarSelection) {
                        CalendarSelectionScreen(
                            calendars = calendars,
                            selectedCalendarId = tempSelectedCalendarId.ifEmpty { persistedCalendarId },
                            onCalendarSelected = { calendarId ->
                                tempSelectedCalendarId = calendarId
                            },
                            onSaveClicked = {
                                if (tempSelectedCalendarId.isNotEmpty()) {
                                    authViewModel.onCalendarTemporarilySelected(tempSelectedCalendarId)
                                    authViewModel.persistSelectedCalendar()
                                }
                                showCalendarSelection = false
                                tempSelectedCalendarId = ""
                            },
                            onCancelClicked = {
                                showCalendarSelection = false
                                tempSelectedCalendarId = ""
                            },
                            isLoading = authState.calendarsLoading
                        )
                    } else {
                        MainContentScreen(
                            authState = authState,
                            persistedCalendarId = persistedCalendarId,
                            calendars = calendars,
                            authViewModel = authViewModel,
                            activity = activity,
                            onSignOut = onSignOut,
                            onShowShiftConfig = { selectedTab = 1 },
                            onShowCalendarSelection = { showCalendarSelection = true }
                        )
                    }
                }
                
                1 -> {
                    // Shift configuration screen
                    val authState by authViewModel.authState.collectAsState()
                    
                    ShiftConfigScreen(
                        authState = authState,
                        onToggleAutoAlarm = { authViewModel.toggleAutoAlarm() },
                        onUpdateShiftDefinition = { authViewModel.updateShiftDefinition(it) },
                        onDeleteShiftDefinition = { authViewModel.deleteShiftDefinition(it) },
                        onResetToDefaults = { authViewModel.resetShiftConfigToDefaults() },
                        onNavigateBack = { selectedTab = 0 }
                    )
                }
                
                2 -> {
                    // Hue screen
                    when {
                        showHueSetup -> {
                            HueBridgeSetupScreen(
                                viewModel = hueViewModel,
                                onSetupComplete = { showHueSetup = false }
                            )
                        }
                        showHueLightSelection -> {
                            HueLightSelectionScreen(
                                viewModel = hueViewModel,
                                onNavigateBack = { showHueLightSelection = false }
                            )
                        }
                        showHueRuleConfig -> {
                            HueRuleConfigScreen(
                                viewModel = hueViewModel,
                                ruleId = editingRuleId,
                                onNavigateBack = { 
                                    showHueRuleConfig = false
                                    editingRuleId = null
                                }
                            )
                        }
                        else -> {
                            HueMainScreen(
                                viewModel = hueViewModel,
                                onNavigateToSetup = { showHueSetup = true },
                                onNavigateToRuleConfig = { ruleId ->
                                    editingRuleId = ruleId
                                    showHueRuleConfig = true
                                },
                                onNavigateToLightSelection = { showHueLightSelection = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
