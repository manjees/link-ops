package com.manjee.linkops.ui.screen.batchtest

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.BatchTestResult
import com.manjee.linkops.domain.model.DeepLinkTemplate
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.LaunchResult
import com.manjee.linkops.domain.model.TestScenario
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * UI State for Batch Test Screen
 */
data class BatchTestUiState(
    val selectedDevice: Device? = null,
    val scenario: TestScenario = createDefaultScenario(),
    val resolvedUris: List<String> = emptyList(),
    val isExecuting: Boolean = false,
    val executionProgress: Int = 0,
    val executionTotal: Int = 0,
    val results: List<LaunchResult> = emptyList(),
    val batchResult: BatchTestResult? = null,
    val error: String? = null,
    val exportedJson: String? = null,
    val importSuccess: Boolean = false
)

/**
 * ViewModel for Batch Test Screen
 *
 * Manages test scenario editing, batch execution, and result display
 */
class BatchTestViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(BatchTestUiState())
    val uiState: StateFlow<BatchTestUiState> = _uiState.asStateFlow()

    private var executionJob: Job? = null

    /**
     * Set the selected device
     */
    fun setDevice(device: Device?) {
        _uiState.update { it.copy(selectedDevice = device, error = null) }
    }

    /**
     * Update scenario name
     */
    fun updateScenarioName(name: String) {
        _uiState.update { it.copy(scenario = it.scenario.copy(name = name)) }
    }

    /**
     * Update scenario description
     */
    fun updateScenarioDescription(description: String) {
        _uiState.update { it.copy(scenario = it.scenario.copy(description = description)) }
    }

    /**
     * Update delay between launches
     */
    fun updateDelay(delayMs: Long) {
        _uiState.update {
            it.copy(scenario = it.scenario.copy(delayBetweenLaunchesMs = delayMs))
        }
    }

    /**
     * Add a new deep link template
     */
    fun addTemplate(uri: String, name: String = "", expectedActivity: String = "") {
        if (uri.isBlank()) return
        val template = DeepLinkTemplate(uri = uri.trim(), name = name.trim(), expectedActivity = expectedActivity.trim())
        _uiState.update {
            val updatedTemplates = it.scenario.templates + template
            it.copy(scenario = it.scenario.copy(templates = updatedTemplates))
        }
        refreshResolvedUris()
    }

    /**
     * Remove a template by index
     */
    fun removeTemplate(index: Int) {
        _uiState.update {
            val updatedTemplates = it.scenario.templates.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            it.copy(scenario = it.scenario.copy(templates = updatedTemplates))
        }
        refreshResolvedUris()
    }

    /**
     * Add a variable with values
     */
    fun addVariable(name: String, values: List<String>) {
        if (name.isBlank() || values.isEmpty()) return
        _uiState.update {
            val updatedVars = it.scenario.variables + (name.trim() to values.map { v -> v.trim() })
            it.copy(scenario = it.scenario.copy(variables = updatedVars))
        }
        refreshResolvedUris()
    }

    /**
     * Remove a variable by name
     */
    fun removeVariable(name: String) {
        _uiState.update {
            val updatedVars = it.scenario.variables - name
            it.copy(scenario = it.scenario.copy(variables = updatedVars))
        }
        refreshResolvedUris()
    }

    /**
     * Refresh the resolved URI preview
     */
    private fun refreshResolvedUris() {
        val state = _uiState.value
        val resolved = AppContainer.resolveTemplateUrisUseCase(
            state.scenario.templates,
            state.scenario.variables
        )
        _uiState.update { it.copy(resolvedUris = resolved) }
    }

    /**
     * Execute the batch test
     */
    fun executeBatchTest() {
        val device = _uiState.value.selectedDevice ?: run {
            _uiState.update { it.copy(error = "No device selected") }
            return
        }

        val scenario = _uiState.value.scenario
        if (scenario.templates.isEmpty()) {
            _uiState.update { it.copy(error = "No deep link templates defined") }
            return
        }

        val resolvedUris = AppContainer.resolveTemplateUrisUseCase(
            scenario.templates,
            scenario.variables
        )

        if (resolvedUris.isEmpty()) {
            _uiState.update { it.copy(error = "No URIs to test") }
            return
        }

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExecuting = true,
                    executionProgress = 0,
                    executionTotal = resolvedUris.size,
                    results = emptyList(),
                    batchResult = null,
                    error = null
                )
            }

            val startTime = System.currentTimeMillis()
            val collectedResults = mutableListOf<LaunchResult>()

            AppContainer.executeBatchTestUseCase(device.serialNumber, scenario, resolvedUris)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            error = e.message ?: "Batch execution failed"
                        )
                    }
                }
                .collect { result ->
                    collectedResults.add(result)
                    _uiState.update {
                        it.copy(
                            executionProgress = collectedResults.size,
                            results = collectedResults.toList()
                        )
                    }
                }

            val totalTime = System.currentTimeMillis() - startTime
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    batchResult = BatchTestResult(
                        scenarioName = scenario.name,
                        results = collectedResults.toList(),
                        totalTimeMs = totalTime
                    )
                )
            }
        }
    }

    /**
     * Stop the currently executing batch test
     */
    fun stopExecution() {
        executionJob?.cancel()
        _uiState.update { it.copy(isExecuting = false) }
    }

    /**
     * Export the current scenario to JSON
     */
    fun exportScenario() {
        viewModelScope.launch {
            AppContainer.exportScenarioUseCase(_uiState.value.scenario)
                .onSuccess { json ->
                    _uiState.update { it.copy(exportedJson = json, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Export failed") }
                }
        }
    }

    /**
     * Import a scenario from JSON
     */
    fun importScenario(json: String) {
        viewModelScope.launch {
            AppContainer.importScenarioUseCase(json)
                .onSuccess { scenario ->
                    _uiState.update {
                        it.copy(
                            scenario = scenario,
                            importSuccess = true,
                            error = null,
                            batchResult = null,
                            results = emptyList()
                        )
                    }
                    refreshResolvedUris()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Import failed: ${error.message}")
                    }
                }
        }
    }

    /**
     * Clear the export result
     */
    fun clearExportedJson() {
        _uiState.update { it.copy(exportedJson = null) }
    }

    /**
     * Clear the import success flag
     */
    fun clearImportSuccess() {
        _uiState.update { it.copy(importSuccess = false) }
    }

    /**
     * Clear results
     */
    fun clearResults() {
        _uiState.update { it.copy(batchResult = null, results = emptyList()) }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Reset to a new scenario
     */
    fun newScenario() {
        _uiState.update {
            BatchTestUiState(
                selectedDevice = it.selectedDevice,
                scenario = createDefaultScenario()
            )
        }
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        executionJob?.cancel()
        viewModelScope.cancel()
    }
}

private fun createDefaultScenario(): TestScenario = TestScenario(
    id = UUID.randomUUID().toString(),
    name = "New Test Scenario"
)
