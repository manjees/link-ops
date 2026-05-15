package com.manjee.linkops.ui.screen.diagnostics

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.AasaStatus
import com.manjee.linkops.domain.model.AasaValidation
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
 * Which platform's well-known file the user is currently validating.
 * The two share the same domain input box; only the fetcher and the result panel differ.
 */
enum class DiagnosticsPlatform(val title: String) {
    ANDROID("Android"),
    IOS("iOS")
}

/**
 * UI State for Diagnostics Screen
 */
data class DiagnosticsUiState(
    val activeTab: DiagnosticsTab = DiagnosticsTab.ASSET_LINKS,
    val platform: DiagnosticsPlatform = DiagnosticsPlatform.ANDROID,
    val domain: String = "",
    val isLoading: Boolean = false,
    val validation: AssetLinksValidation? = null,
    val aasaValidation: AasaValidation? = null,
    val error: String? = null,
    val history: List<ValidationHistoryItem> = emptyList(),
    val collisionReport: CollisionReport? = null,
    val isDetectingCollisions: Boolean = false,
    val collisionError: String? = null,
    val selectedDeviceSerial: String? = null
)

/**
 * History item for past validations.
 *
 * Holds either an Android [ValidationStatus] or an iOS [AasaStatus] — exactly one
 * is non-null based on which platform produced the entry. Kept as a single class
 * so the recent-list UI can render both with one row component.
 */
data class ValidationHistoryItem(
    val domain: String,
    val platform: DiagnosticsPlatform,
    val androidStatus: ValidationStatus? = null,
    val iosStatus: AasaStatus? = null,
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
     * Switch validation platform. Clears the previously shown result so the user
     * isn't comparing apples to oranges in the result panel.
     */
    fun switchPlatform(platform: DiagnosticsPlatform) {
        _uiState.update {
            it.copy(
                platform = platform,
                validation = null,
                aasaValidation = null,
                error = null
            )
        }
    }

    /**
     * Update domain input
     */
    fun updateDomain(domain: String) {
        _uiState.update { it.copy(domain = domain, error = null) }
    }

    /**
     * Validate the entered domain for the currently selected platform.
     */
    fun validateDomain() {
        val domain = _uiState.value.domain.trim()
        if (domain.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a domain") }
            return
        }

        when (_uiState.value.platform) {
            DiagnosticsPlatform.ANDROID -> validateAndroid(domain)
            DiagnosticsPlatform.IOS -> validateIos(domain)
        }
    }

    private fun validateAndroid(domain: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            AppContainer.validateAssetLinksUseCase(domain)
                .onSuccess { validation ->
                    val historyItem = ValidationHistoryItem(
                        domain = validation.domain,
                        platform = DiagnosticsPlatform.ANDROID,
                        androidStatus = validation.status
                    )
                    val newHistory = listOf(historyItem) + _uiState.value.history.take(9)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            validation = validation,
                            aasaValidation = null,
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

    private fun validateIos(domain: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            AppContainer.validateAasaUseCase(domain)
                .onSuccess { validation ->
                    val historyItem = ValidationHistoryItem(
                        domain = validation.domain,
                        platform = DiagnosticsPlatform.IOS,
                        iosStatus = validation.status
                    )
                    val newHistory = listOf(historyItem) + _uiState.value.history.take(9)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            aasaValidation = validation,
                            validation = null,
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
     * Replays a history entry on its original platform — switching the platform
     * toggle and re-running the matching validator.
     */
    fun validateFromHistory(item: ValidationHistoryItem) {
        _uiState.update {
            it.copy(
                domain = item.domain,
                platform = item.platform
            )
        }
        validateDomain()
    }

    /**
     * Clear current validation result
     */
    fun clearResult() {
        _uiState.update { it.copy(validation = null, aasaValidation = null, error = null) }
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
