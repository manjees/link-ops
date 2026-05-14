package com.manjee.linkops.ui.screen.main

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.AppLink
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.Favorite
import com.manjee.linkops.domain.model.IntentConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * UI State for Main Screen
 */
data class MainUiState(
    val adbStatus: AdbStatus = AdbStatus.Unknown,
    val devices: List<Device> = emptyList(),
    val selectedDevice: Device? = null,
    val appLinks: List<AppLink> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val isLoadingAppLinks: Boolean = false,
    val isFiringIntent: Boolean = false,
    val error: String? = null,
    val favorites: List<Favorite> = emptyList()
)

/**
 * ADB Status
 */
sealed class AdbStatus {
    data object Unknown : AdbStatus()
    data object Checking : AdbStatus()
    data class Available(val version: String, val path: String) : AdbStatus()
    data class Unavailable(val reason: String) : AdbStatus()
}

/**
 * ViewModel for Main Screen
 *
 * Manages device detection, app link queries, and intent firing
 */
class MainViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(listOf("Ready to test...\n"))
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    val logText: StateFlow<String> = _logEntries
        .map { it.joinToString("") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Ready to test...\n")

    init {
        checkAdbStatus()
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            AppContainer.observeFavoritesUseCase()
                .collect { favorites ->
                    _uiState.update { it.copy(favorites = favorites) }
                }
        }
    }

    /**
     * Append a log message
     */
    private fun appendLog(message: String) {
        _logEntries.value = _logEntries.value + "$message\n"
    }

    /**
     * Clear all log entries
     */
    fun clearLog() {
        _logEntries.value = listOf("Log cleared.\n")
    }

    /**
     * Check ADB availability and version
     */
    fun checkAdbStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(adbStatus = AdbStatus.Checking) }
            appendLog("--- ADB Check ---")

            try {
                val adbPath = AppContainer.adbBinaryManager.getAdbPath()
                if (adbPath != null) {
                    appendLog("OS: ${AppContainer.adbBinaryManager.currentOs}")
                    appendLog("Path: $adbPath")

                    AppContainer.adbShellExecutor.execute("version")
                        .onSuccess { output ->
                            val version = output.lines()
                                .firstOrNull { it.contains("version") }
                                ?: output.lines().firstOrNull()
                                ?: "Unknown"
                            appendLog("Version: $version")
                            _uiState.update {
                                it.copy(adbStatus = AdbStatus.Available(version, adbPath))
                            }
                        }
                        .onFailure { error ->
                            appendLog("Error: ${error.message}")
                            _uiState.update {
                                it.copy(adbStatus = AdbStatus.Unavailable(error.message ?: "Unknown error"))
                            }
                        }
                } else {
                    appendLog("ADB not found")
                    _uiState.update {
                        it.copy(adbStatus = AdbStatus.Unavailable("ADB binary not found"))
                    }
                }
            } catch (e: Exception) {
                appendLog("Error: ${e.message}")
                _uiState.update {
                    it.copy(adbStatus = AdbStatus.Unavailable(e.message ?: "Unknown error"))
                }
            }
        }
    }

    /**
     * Refresh connected devices
     */
    fun refreshDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true, error = null) }
            appendLog("--- Fetching Devices ---")

            AppContainer.refreshDevicesUseCase()
                .onSuccess { devices ->
                    _uiState.update {
                        it.copy(
                            devices = devices,
                            isLoadingDevices = false
                        )
                    }
                    appendLog("Found ${devices.size} device(s)")
                }
                .onFailure { error ->
                    appendLog("Error: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingDevices = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    /**
     * Select a device
     */
    fun selectDevice(device: Device) {
        _uiState.update {
            it.copy(
                selectedDevice = device,
                appLinks = emptyList()
            )
        }
        appendLog("Selected: ${device.serialNumber}")

        // Automatically load app links for the selected device
        loadAppLinks()
    }

    /**
     * Load app links for the selected device
     */
    fun loadAppLinks() {
        val device = _uiState.value.selectedDevice ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAppLinks = true, error = null) }
            appendLog("--- Fetching App Links (SDK ${device.sdkLevel}) ---")

            try {
                AppContainer.getAppLinksUseCase(device.serialNumber)
                    .onSuccess { links ->
                        _uiState.update {
                            it.copy(
                                appLinks = links,
                                isLoadingAppLinks = false
                            )
                        }
                        appendLog("Found ${links.size} app(s) with app links")
                        links.forEach { app ->
                            appendLog("  ${app.packageName}: ${app.domains.size} domain(s)")
                        }
                    }
                    .onFailure { error ->
                        appendLog("Error: ${error.message}")
                        _uiState.update {
                            it.copy(
                                isLoadingAppLinks = false,
                                error = error.message
                            )
                        }
                    }
            } catch (e: Exception) {
                appendLog("Error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoadingAppLinks = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Force re-verification for an app's domains
     */
    fun forceReverify(packageName: String) {
        val device = _uiState.value.selectedDevice ?: return

        viewModelScope.launch {
            appendLog("--- Re-verifying $packageName ---")

            try {
                AppContainer.forceReverifyUseCase(device.serialNumber, packageName)
                    .onSuccess {
                        appendLog("Re-verification triggered!")
                        // Reload app links after re-verification
                        delay(1000) // Wait a bit for verification to start
                        loadAppLinks()
                    }
                    .onFailure { error ->
                        appendLog("Error: ${error.message}")
                    }
            } catch (e: Exception) {
                appendLog("Error: ${e.message}")
            }
        }
    }

    /**
     * Fire an intent on the selected device
     */
    fun fireIntent(config: IntentConfig) {
        val device = _uiState.value.selectedDevice ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isFiringIntent = true) }
            appendLog("--- Firing Intent: ${config.uri} ---")
            appendLog("Command: ${config.toAdbCommand()}")

            try {
                AppContainer.fireIntentUseCase(device.serialNumber, config)
                    .catch { e ->
                        appendLog("Error: ${e.message}")
                    }
                    .collect { line ->
                        appendLog(line)
                    }
                appendLog("Intent fired!")
            } catch (e: Exception) {
                appendLog("Error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isFiringIntent = false) }
            }
        }
    }

    /**
     * Remove a favorite by ID
     */
    fun removeFavorite(id: String) {
        viewModelScope.launch {
            AppContainer.removeFavoriteUseCase(id)
        }
    }

    /**
     * Fire an intent from a favorite deep link
     */
    fun fireFavorite(uri: String) {
        val config = IntentConfig(uri = uri)
        fireIntent(config)
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}
