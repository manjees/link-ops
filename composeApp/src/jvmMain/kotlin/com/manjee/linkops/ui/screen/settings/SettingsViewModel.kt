package com.manjee.linkops.ui.screen.settings

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.AppSettings
import com.manjee.linkops.domain.model.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Form-local state for the Settings screen.
 *
 * `draft` is what the user is currently typing / toggling; `persisted` is the last
 * value confirmed by the repository. We keep them separate so the user can edit
 * text fields without each keystroke immediately writing to disk, and so we can
 * tell when a field is dirty (draft != persisted).
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val draft: AppSettings = AppSettings(),
    val persisted: AppSettings = AppSettings(),
    val portInput: String = AppSettings.DEFAULT_PORT.toString(),
    val pollingInput: String = AppSettings.DEFAULT_POLLING_INTERVAL_SECONDS.toString(),
    val error: String? = null,
    val savedAt: Long? = null
) {
    val isDirty: Boolean
        get() = draft != persisted

    val isPortInputValid: Boolean
        get() = portInput.toIntOrNull()
            ?.let { it in AppSettings.MIN_PORT..AppSettings.MAX_PORT }
            ?: false

    val isPollingInputValid: Boolean
        get() = pollingInput.toIntOrNull()
            ?.let { it in AppSettings.MIN_POLLING_INTERVAL_SECONDS..AppSettings.MAX_POLLING_INTERVAL_SECONDS }
            ?: false

    val canSave: Boolean
        get() = isDirty && isPortInputValid && isPollingInputValid
}

class SettingsViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            AppContainer.observeSettingsUseCase().collect { settings ->
                _uiState.update {
                    // Only reset the draft from persisted when the user has no dirty edits,
                    // otherwise we'd discard their in-progress typing every time the file changes.
                    if (it.isDirty) {
                        it.copy(persisted = settings, isLoading = false)
                    } else {
                        it.copy(
                            persisted = settings,
                            draft = settings,
                            portInput = settings.defaultPort.toString(),
                            pollingInput = settings.devicePollingIntervalSeconds.toString(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Theme toggles persist immediately — there's no failure mode worth a confirm step
     * and the visual feedback is instant.
     */
    fun setTheme(theme: ThemePreference) {
        _uiState.update { it.copy(draft = it.draft.copy(theme = theme)) }
        viewModelScope.launch {
            AppContainer.updateSettingsUseCase { current -> current.copy(theme = theme) }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to save theme") }
                }
        }
    }

    fun updateAdbPathOverride(path: String) {
        _uiState.update {
            it.copy(draft = it.draft.copy(adbPathOverride = path.takeIf { p -> p.isNotBlank() }))
        }
    }

    fun updateDefaultHost(host: String) {
        _uiState.update { it.copy(draft = it.draft.copy(defaultHost = host)) }
    }

    fun updatePortInput(value: String) {
        _uiState.update { state ->
            state.copy(
                portInput = value,
                draft = value.toIntOrNull()?.let { state.draft.copy(defaultPort = it) } ?: state.draft
            )
        }
    }

    fun updatePollingInput(value: String) {
        _uiState.update { state ->
            state.copy(
                pollingInput = value,
                draft = value.toIntOrNull()
                    ?.let { state.draft.copy(devicePollingIntervalSeconds = it) }
                    ?: state.draft
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val target = state.draft

        viewModelScope.launch {
            AppContainer.updateSettingsUseCase { _ -> target }
                .onSuccess {
                    _uiState.update {
                        it.copy(error = null, savedAt = System.currentTimeMillis())
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to save settings")
                    }
                }
        }
    }

    fun discardChanges() {
        _uiState.update { state ->
            state.copy(
                draft = state.persisted,
                portInput = state.persisted.defaultPort.toString(),
                pollingInput = state.persisted.devicePollingIntervalSeconds.toString(),
                error = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onCleared() {
        viewModelScope.cancel()
    }
}
