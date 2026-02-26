package com.manjee.linkops.ui.screen.sniffer

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.model.PayloadComparison
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Selected tab in the Intent Sniffer screen
 */
enum class SnifferTab {
    DETAILS, COMPARE, RAW
}

/**
 * UI State for Intent Sniffer Screen
 */
data class IntentSnifferUiState(
    val payloadHistory: List<IntentPayload> = emptyList(),
    val selectedPayload: IntentPayload? = null,
    val compareBasePayload: IntentPayload? = null,
    val comparison: PayloadComparison? = null,
    val selectedTab: SnifferTab = SnifferTab.DETAILS,
    val isCapturing: Boolean = false,
    val autoCapture: Boolean = false,
    val error: String? = null
) {
    val hasPayloads: Boolean get() = payloadHistory.isNotEmpty()
    val canCompare: Boolean get() = compareBasePayload != null && selectedPayload != null
}

/**
 * ViewModel for Intent Sniffer Screen
 *
 * Manages intent payload capture, comparison, and history
 */
class IntentSnifferViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(IntentSnifferUiState())
    val uiState: StateFlow<IntentSnifferUiState> = _uiState.asStateFlow()

    private var autoCaptureJob: Job? = null

    init {
        observeIntentFiredEvents()
    }

    /**
     * Manually capture the current intent payload from the device
     */
    fun capturePayload(device: Device) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }

            AppContainer.captureIntentPayloadUseCase(device.serialNumber)
                .onSuccess { payload ->
                    addPayloadToHistory(payload)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isCapturing = false, error = error.message) }
                }
        }
    }

    /**
     * Capture payload after detecting activity change (used for auto-capture)
     */
    private fun captureAfterChange(deviceSerial: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }

            AppContainer.captureIntentPayloadUseCase.captureAfterChange(deviceSerial)
                .onSuccess { payload ->
                    addPayloadToHistory(payload)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isCapturing = false, error = error.message) }
                }
        }
    }

    /**
     * Toggle auto-capture mode
     */
    fun toggleAutoCapture() {
        val newState = !_uiState.value.autoCapture
        _uiState.update { it.copy(autoCapture = newState) }
    }

    /**
     * Select a payload from history to view details
     */
    fun selectPayload(payload: IntentPayload) {
        _uiState.update { it.copy(selectedPayload = payload) }
        updateComparison()
    }

    /**
     * Select a payload as the base for comparison
     */
    fun selectCompareBase(payload: IntentPayload) {
        _uiState.update { it.copy(compareBasePayload = payload) }
        updateComparison()
    }

    /**
     * Switch the active tab
     */
    fun selectTab(tab: SnifferTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Clear all captured payloads
     */
    fun clearHistory() {
        _uiState.update {
            it.copy(
                payloadHistory = emptyList(),
                selectedPayload = null,
                compareBasePayload = null,
                comparison = null
            )
        }
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

    private fun addPayloadToHistory(payload: IntentPayload) {
        _uiState.update { state ->
            val updatedHistory = (listOf(payload) + state.payloadHistory).take(MAX_HISTORY_SIZE)
            state.copy(
                payloadHistory = updatedHistory,
                selectedPayload = payload,
                isCapturing = false
            )
        }
        updateComparison()
    }

    private fun updateComparison() {
        val state = _uiState.value
        val base = state.compareBasePayload
        val current = state.selectedPayload

        if (base != null && current != null && base != current) {
            val comparison = AppContainer.comparePayloadsUseCase(base, current)
            _uiState.update { it.copy(comparison = comparison) }
        } else {
            _uiState.update { it.copy(comparison = null) }
        }
    }

    private fun observeIntentFiredEvents() {
        viewModelScope.launch {
            AppContainer.intentFiredEvent.collect { event ->
                if (_uiState.value.autoCapture) {
                    captureAfterChange(event.deviceSerial)
                }
            }
        }
    }

    companion object {
        private const val MAX_HISTORY_SIZE = 50
    }
}
