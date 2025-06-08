package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.HueBridgeSetupScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.HueMainScreen
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
    var selectedTab by remember { mutableStateOf(0) }
    var showHueSetup by remember { mutableStateOf(false) }
    
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
                    // Main content screen
                    val authState by authViewModel.authState.collectAsState()
                    val calendars by authViewModel.calendars.collectAsState()
                    val persistedCalendarId by authViewModel.persistedCalendarId.collectAsState("")
                    
                    MainContentScreen(
                        authState = authState,
                        persistedCalendarId = persistedCalendarId,
                        calendars = calendars,
                        authViewModel = authViewModel,
                        activity = activity,
                        onSignOut = onSignOut,
                        onShowShiftConfig = { selectedTab = 1 },
                        onShowCalendarSelection = { /* Handle calendar selection */ }
                    )
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
                    if (showHueSetup) {
                        HueBridgeSetupScreen(
                            viewModel = hueViewModel,
                            onSetupComplete = { showHueSetup = false }
                        )
                    } else {
                        HueMainScreen(
                            viewModel = hueViewModel,
                            onNavigateToSetup = { showHueSetup = true },
                            onNavigateToRuleConfig = { /* TODO: Navigate to rule config */ },
                            onNavigateToLightSelection = { /* TODO: Navigate to light selection */ }
                        )
                    }
                }
            }
        }
    }
}
