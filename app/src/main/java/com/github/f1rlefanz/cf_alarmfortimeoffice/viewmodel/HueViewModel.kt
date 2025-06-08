package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for Hue integration
 */
class HueViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bridgeRepository = HueBridgeRepository()
    private val configRepository = HueConfigRepository(application)
    private val lightRepository = HueLightRepository(bridgeRepository)
    
    // UI State
    private val _uiState = MutableStateFlow(HueUiState())
    val uiState: StateFlow<HueUiState> = _uiState.asStateFlow()
    
    // Configuration
    val configuration = configRepository.getConfiguration()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HueConfiguration()
        )
    
    init {
        // Load saved configuration
        viewModelScope.launch {
            configuration.collect { config ->
                config.bridgeIp?.let { ip ->
                    bridgeRepository.connectToBridge(ip)
                    config.username?.let { username ->
                        bridgeRepository.setUsername(username)
                        refreshLightsAndGroups()
                    }
                }
            }
        }
    }
    
    /**
     * Start bridge discovery
     */
    fun discoverBridges() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            bridgeRepository.discoverBridges()
                .onSuccess { bridges ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            discoveredBridges = bridges
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    /**
     * Connect to a bridge
     */
    fun connectToBridge(bridgeIp: String) {
        bridgeRepository.connectToBridge(bridgeIp)
        _uiState.update { 
            it.copy(
                currentBridgeIp = bridgeIp,
                setupStep = SetupStep.LINK_BUTTON
            )
        }
    }
    
    /**
     * Create user on bridge (after link button pressed)
     */
    fun createUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            bridgeRepository.createUser("CFAlarmForTimeOffice", android.os.Build.MODEL)
                .onSuccess { username ->
                    // Get bridge config to save ID
                    bridgeRepository.getBridgeConfig()
                        .onSuccess { config ->
                            // Save connection info
                            configRepository.saveBridgeInfo(
                                bridgeId = config.bridgeid,
                                bridgeIp = _uiState.value.currentBridgeIp!!,
                                username = username
                            )
                            
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    isConnected = true,
                                    setupStep = SetupStep.COMPLETE
                                )
                            }
                            
                            // Load lights and groups
                            refreshLightsAndGroups()
                        }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    /**
     * Refresh lights and groups from bridge
     */
    fun refreshLightsAndGroups() {
        viewModelScope.launch {
            // Get lights
            bridgeRepository.getAllLights()
                .onSuccess { lights ->
                    _uiState.update { it.copy(lights = lights) }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to get lights")
                }
            
            // Get groups
            bridgeRepository.getAllGroups()
                .onSuccess { groups ->
                    _uiState.update { it.copy(groups = groups) }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to get groups")
                }
        }
    }
    
    /**
     * Add a new schedule rule
     */
    fun addScheduleRule(rule: HueScheduleRule) {
        viewModelScope.launch {
            configRepository.addRule(rule)
        }
    }
    
    /**
     * Update a schedule rule
     */
    fun updateScheduleRule(rule: HueScheduleRule) {
        viewModelScope.launch {
            configRepository.updateRule(rule)
        }
    }
    
    /**
     * Delete a schedule rule
     */
    fun deleteScheduleRule(ruleId: String) {
        viewModelScope.launch {
            configRepository.deleteRule(ruleId)
        }
    }
    
    /**
     * Execute a light action immediately (for testing)
     */
    fun testLightAction(action: HueLightAction) {
        viewModelScope.launch {
            lightRepository.executeLightAction(action)
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(error = "Test failed: ${error.message}")
                    }
                }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Reset Hue configuration
     */
    fun resetConfiguration() {
        viewModelScope.launch {
            configRepository.clearConfiguration()
            _uiState.value = HueUiState()
        }
    }
}

/**
 * UI State for Hue screens
 */
data class HueUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false,
    val setupStep: SetupStep = SetupStep.DISCOVERY,
    val currentBridgeIp: String? = null,
    val discoveredBridges: List<BridgeDiscoveryResponse> = emptyList(),
    val lights: Map<String, HueLight> = emptyMap(),
    val groups: Map<String, HueGroup> = emptyMap()
)

/**
 * Setup flow steps
 */
enum class SetupStep {
    DISCOVERY,
    LINK_BUTTON,
    COMPLETE
}
