package com.manjee.linkops.ui.screen.localhosting

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.LocalHostingConfig
import com.manjee.linkops.domain.model.ServerStatus
import com.manjee.linkops.domain.model.VerificationStep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * UI State for Local Hosting Screen
 */
data class LocalHostingUiState(
    val host: String = "127.0.0.1",
    val port: String = LocalHostingConfig.DEFAULT_PORT.toString(),
    val packageName: String = "",
    val fingerprint: String = "",
    val selectedDevice: Device? = null,
    val serverStatus: ServerStatus = ServerStatus.STOPPED,
    val generatedJson: String = "",
    val isExtractingFingerprint: Boolean = false,
    val isRunningWorkflow: Boolean = false,
    val workflowSteps: List<VerificationStep> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for Local Hosting feature
 *
 * Manages the local AssetLinks server lifecycle, certificate fingerprint extraction,
 * and the pre-deployment verification workflow.
 */
class LocalHostingViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(LocalHostingUiState())
    val uiState: StateFlow<LocalHostingUiState> = _uiState.asStateFlow()

    init {
        observeServerStatus()
    }

    private fun observeServerStatus() {
        viewModelScope.launch {
            AppContainer.localHostingRepository.observeServerStatus()
                .collect { status ->
                    _uiState.update { it.copy(serverStatus = status) }
                }
        }
    }

    /**
     * Update host input
     */
    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host, error = null) }
    }

    /**
     * Update port input
     */
    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port, error = null) }
    }

    /**
     * Update package name input
     */
    fun updatePackageName(packageName: String) {
        _uiState.update { it.copy(packageName = packageName, error = null) }
    }

    /**
     * Update fingerprint input
     */
    fun updateFingerprint(fingerprint: String) {
        _uiState.update { it.copy(fingerprint = fingerprint, error = null) }
    }

    /**
     * Set the selected device
     */
    fun selectDevice(device: Device?) {
        _uiState.update { it.copy(selectedDevice = device, error = null) }
    }

    /**
     * Extract fingerprint from the selected device for the given package
     */
    fun extractFingerprint() {
        val device = _uiState.value.selectedDevice
        val packageName = _uiState.value.packageName.trim()

        if (device == null) {
            _uiState.update { it.copy(error = "Please select a device first") }
            return
        }
        if (packageName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a package name") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExtractingFingerprint = true, error = null) }

            AppContainer.extractFingerprintUseCase(device.serialNumber, packageName)
                .onSuccess { fingerprint ->
                    _uiState.update {
                        it.copy(
                            fingerprint = fingerprint,
                            isExtractingFingerprint = false
                        )
                    }
                    generatePreview()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isExtractingFingerprint = false,
                            error = error.message ?: "Failed to extract fingerprint"
                        )
                    }
                }
        }
    }

    /**
     * Generate and preview the assetlinks.json content
     */
    fun generatePreview() {
        val packageName = _uiState.value.packageName.trim()
        val fingerprint = _uiState.value.fingerprint.trim()

        if (packageName.isBlank() || fingerprint.isBlank()) {
            _uiState.update { it.copy(generatedJson = "") }
            return
        }

        viewModelScope.launch {
            AppContainer.generateAssetLinksUseCase(packageName, listOf(fingerprint))
                .onSuccess { json ->
                    _uiState.update { it.copy(generatedJson = json) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to generate JSON")
                    }
                }
        }
    }

    /**
     * Start the local AssetLinks server
     */
    fun startServer() {
        val state = _uiState.value
        val portInt = state.port.toIntOrNull()

        if (portInt == null || portInt < 1 || portInt > 65535) {
            _uiState.update { it.copy(error = "Invalid port number (1-65535)") }
            return
        }
        if (state.packageName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a package name") }
            return
        }
        if (state.fingerprint.isBlank()) {
            _uiState.update { it.copy(error = "Please enter or extract a fingerprint") }
            return
        }

        val config = LocalHostingConfig(
            host = state.host,
            port = portInt,
            packageName = state.packageName.trim(),
            sha256Fingerprints = listOf(state.fingerprint.trim())
        )

        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            AppContainer.startLocalServerUseCase(config)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to start server")
                    }
                }
        }
    }

    /**
     * Stop the local server
     */
    fun stopServer() {
        viewModelScope.launch {
            AppContainer.stopLocalServerUseCase()
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to stop server")
                    }
                }
        }
    }

    /**
     * Run the full verification workflow
     */
    fun runVerificationWorkflow() {
        val state = _uiState.value
        val device = state.selectedDevice
        val packageName = state.packageName.trim()
        val portInt = state.port.toIntOrNull()

        if (device == null) {
            _uiState.update { it.copy(error = "Please select a device first") }
            return
        }
        if (packageName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a package name") }
            return
        }
        if (state.fingerprint.isBlank()) {
            _uiState.update { it.copy(error = "Please enter or extract a fingerprint") }
            return
        }
        if (portInt == null || portInt < 1 || portInt > 65535) {
            _uiState.update { it.copy(error = "Invalid port number") }
            return
        }

        val config = LocalHostingConfig(
            host = state.host,
            port = portInt,
            packageName = packageName,
            sha256Fingerprints = listOf(state.fingerprint.trim())
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunningWorkflow = true,
                    workflowSteps = emptyList(),
                    error = null
                )
            }

            AppContainer.runVerificationWorkflowUseCase(
                device.serialNumber,
                packageName,
                config
            ).collect { step ->
                _uiState.update { state ->
                    val updatedSteps = state.workflowSteps.toMutableList()
                    val existingIndex = updatedSteps.indexOfFirst { it.name == step.name }
                    if (existingIndex >= 0) {
                        updatedSteps[existingIndex] = step
                    } else {
                        updatedSteps.add(step)
                    }
                    state.copy(workflowSteps = updatedSteps)
                }
            }

            _uiState.update { it.copy(isRunningWorkflow = false) }
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear workflow steps
     */
    fun clearWorkflow() {
        _uiState.update { it.copy(workflowSteps = emptyList()) }
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}
