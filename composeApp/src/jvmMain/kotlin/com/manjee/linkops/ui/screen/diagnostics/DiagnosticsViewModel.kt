package com.manjee.linkops.ui.screen.diagnostics

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.AssetLinksValidation
import com.manjee.linkops.domain.model.CollisionReport
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.ValidationStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Active tab on the Diagnostics screen
 */
enum class DiagnosticsTab(val title: String) {
    ASSET_LINKS("AssetLinks"),
    COLLISION_DETECTOR("Collision Detector")
}

/**
 * UI State for Diagnostics Screen
 */
data class DiagnosticsUiState(
    val activeTab: DiagnosticsTab = DiagnosticsTab.ASSET_LINKS,
    val domain: String = "",
    val isLoading: Boolean = false,
    val validation: AssetLinksValidation? = null,
    val error: String? = null,
    val history: List<ValidationHistoryItem> = emptyList(),
    val collisionReport: CollisionReport? = null,
    val isDetectingCollisions: Boolean = false,
    val collisionError: String? = null,
    val selectedDeviceSerial: String? = null
)

/**
 * History item for past validations
 */
data class ValidationHistoryItem(
    val domain: String,
    val status: ValidationStatus,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ViewModel for Diagnostics Screen
 *
 * Handles assetlinks.json validation and URI scheme collision detection
 */
class DiagnosticsViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    /**
     * Switch active tab
     */
    fun switchTab(tab: DiagnosticsTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    /**
     * Update domain input
     */
    fun updateDomain(domain: String) {
        _uiState.update { it.copy(domain = domain, error = null) }
    }

    /**
     * Validate assetlinks.json for the entered domain
     */
    fun validateDomain() {
        val domain = _uiState.value.domain.trim()
        if (domain.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a domain") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            AppContainer.validateAssetLinksUseCase(domain)
                .onSuccess { validation ->
                    // Add to history
                    val historyItem = ValidationHistoryItem(
                        domain = validation.domain,
                        status = validation.status
                    )
                    val newHistory = listOf(historyItem) + _uiState.value.history.take(9)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            validation = validation,
                            history = newHistory
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Validation failed"
                        )
                    }
                }
        }
    }

    /**
     * Validate a domain from history
     */
    fun validateFromHistory(domain: String) {
        _uiState.update { it.copy(domain = domain) }
        validateDomain()
    }

    /**
     * Clear current validation result
     */
    fun clearResult() {
        _uiState.update { it.copy(validation = null, error = null) }
    }

    /**
     * Clear validation history
     */
    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }

    /**
     * Update selected device for collision detection
     */
    fun updateSelectedDevice(device: Device?) {
        _uiState.update { it.copy(selectedDeviceSerial = device?.serialNumber) }
    }

    /**
     * Detect URI scheme collisions on the selected device
     */
    fun detectCollisions() {
        val deviceSerial = _uiState.value.selectedDeviceSerial
        if (deviceSerial == null) {
            _uiState.update { it.copy(collisionError = "No device selected") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDetectingCollisions = true, collisionError = null) }

            AppContainer.detectCollisionsUseCase(deviceSerial)
                .onSuccess { report ->
                    _uiState.update {
                        it.copy(
                            isDetectingCollisions = false,
                            collisionReport = report
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDetectingCollisions = false,
                            collisionError = error.message ?: "Collision detection failed"
                        )
                    }
                }
        }
    }

    /**
     * Clear collision detection result
     */
    fun clearCollisionResult() {
        _uiState.update { it.copy(collisionReport = null, collisionError = null) }
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}
