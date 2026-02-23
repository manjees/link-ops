package com.manjee.linkops.ui.screen.manifest

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.IntentConfig
import com.manjee.linkops.domain.model.ManifestAnalysisResult
import com.manjee.linkops.domain.model.TopologyAnalysisResult
import com.manjee.linkops.domain.model.TopologyInsight
import com.manjee.linkops.domain.repository.PackageFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Tab selection for Manifest Analyzer
 */
enum class ManifestTab {
    LIST_VIEW,
    TOPOLOGY_MAP
}

/**
 * UI State for Manifest Analyzer Screen
 */
data class ManifestAnalyzerUiState(
    val selectedDevice: Device? = null,
    val packageQuery: String = "",
    val packages: List<String> = emptyList(),
    val selectedPackage: String? = null,
    val analysisResult: ManifestAnalysisResult? = null,
    val isLoadingPackages: Boolean = false,
    val isAnalyzing: Boolean = false,
    val packageFilter: PackageFilter = PackageFilter.THIRD_PARTY,
    val error: String? = null,
    val testResult: DeepLinkTestResult? = null,
    val favoriteUris: Set<String> = emptySet(),
    val selectedTab: ManifestTab = ManifestTab.LIST_VIEW,
    val topologyResult: TopologyAnalysisResult? = null,
    val topologySearchQuery: String = "",
    val highlightedNodeIds: Set<String> = emptySet()
)

/**
 * Result of deep link test
 */
data class DeepLinkTestResult(
    val uri: String,
    val success: Boolean,
    val message: String
)

/**
 * ViewModel for Manifest Analyzer Screen
 */
class ManifestAnalyzerViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ManifestAnalyzerUiState())
    val uiState: StateFlow<ManifestAnalyzerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            AppContainer.observeFavoritesUseCase()
                .collect { favorites ->
                    _uiState.update { it.copy(favoriteUris = favorites.map { f -> f.uri }.toSet()) }
                }
        }
    }

    /**
     * Set the selected device
     */
    fun setDevice(device: Device?) {
        _uiState.update {
            it.copy(
                selectedDevice = device,
                packages = emptyList(),
                selectedPackage = null,
                analysisResult = null,
                topologyResult = null,
                error = null
            )
        }

        if (device != null) {
            loadPackages()
        }
    }

    /**
     * Update package filter
     */
    fun setPackageFilter(filter: PackageFilter) {
        _uiState.update { it.copy(packageFilter = filter) }
        loadPackages()
    }

    /**
     * Load packages from device
     */
    fun loadPackages() {
        val device = _uiState.value.selectedDevice ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPackages = true, error = null) }

            AppContainer.getInstalledPackagesUseCase(
                device.serialNumber,
                _uiState.value.packageFilter
            )
                .onSuccess { packages ->
                    _uiState.update {
                        it.copy(
                            packages = packages,
                            isLoadingPackages = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingPackages = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    /**
     * Update package search query
     */
    fun updatePackageQuery(query: String) {
        _uiState.update { it.copy(packageQuery = query) }

        // Debounce search
        searchJob?.cancel()
        if (query.isBlank()) {
            loadPackages()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            searchPackages(query)
        }
    }

    private suspend fun searchPackages(query: String) {
        val device = _uiState.value.selectedDevice ?: return

        _uiState.update { it.copy(isLoadingPackages = true) }

        AppContainer.searchPackagesUseCase(device.serialNumber, query)
            .onSuccess { packages ->
                _uiState.update {
                    it.copy(
                        packages = packages,
                        isLoadingPackages = false
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingPackages = false,
                        error = error.message
                    )
                }
            }
    }

    /**
     * Select a package for analysis
     */
    fun selectPackage(packageName: String) {
        _uiState.update {
            it.copy(
                selectedPackage = packageName,
                analysisResult = null,
                topologyResult = null
            )
        }
        analyzePackage(packageName)
    }

    /**
     * Analyze a package's manifest
     */
    fun analyzePackage(packageName: String) {
        val device = _uiState.value.selectedDevice ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }

            AppContainer.analyzeManifestUseCase(device.serialNumber, packageName)
                .onSuccess { result ->
                    val topologyResult = result.manifestInfo?.let { info ->
                        AppContainer.buildTopologyTreeUseCase(info, result.domainVerification)
                    }
                    _uiState.update {
                        it.copy(
                            analysisResult = result,
                            topologyResult = topologyResult,
                            isAnalyzing = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    /**
     * Switch between List View and Topology Map tabs
     *
     * @param tab The tab to switch to
     */
    fun selectTab(tab: ManifestTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Update topology search query
     *
     * @param query The search query string
     */
    fun updateTopologySearch(query: String) {
        _uiState.update { it.copy(topologySearchQuery = query) }
    }

    /**
     * Highlight nodes affected by an insight
     *
     * @param insight The topology insight whose affected nodes to highlight
     */
    fun highlightInsightNodes(insight: TopologyInsight) {
        _uiState.update { it.copy(highlightedNodeIds = insight.affectedNodeIds.toSet()) }
    }

    /**
     * Clear highlighted nodes
     */
    fun clearHighlightedNodes() {
        _uiState.update { it.copy(highlightedNodeIds = emptySet()) }
    }

    /**
     * Clear analysis result
     */
    fun clearAnalysis() {
        _uiState.update {
            it.copy(
                selectedPackage = null,
                analysisResult = null,
                topologyResult = null,
                topologySearchQuery = "",
                highlightedNodeIds = emptySet()
            )
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Test a deep link by firing an intent
     */
    fun testDeepLink(uri: String) {
        val device = _uiState.value.selectedDevice ?: return

        viewModelScope.launch {
            AppContainer.testDeepLinkUseCase(device.serialNumber, uri)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            testResult = DeepLinkTestResult(
                                uri = uri,
                                success = true,
                                message = "Intent fired successfully"
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            testResult = DeepLinkTestResult(
                                uri = uri,
                                success = false,
                                message = error.message ?: "Failed to fire intent"
                            )
                        )
                    }
                }
        }
    }

    /**
     * Clear test result
     */
    fun clearTestResult() {
        _uiState.update { it.copy(testResult = null) }
    }

    /**
     * Fire an intent with full configuration
     */
    fun fireIntent(config: IntentConfig) {
        val device = _uiState.value.selectedDevice ?: return

        viewModelScope.launch {
            try {
                AppContainer.fireIntentUseCase(device.serialNumber, config)
                    .catch { e ->
                        _uiState.update {
                            it.copy(
                                testResult = DeepLinkTestResult(
                                    uri = config.uri,
                                    success = false,
                                    message = e.message ?: "Failed to fire intent"
                                )
                            )
                        }
                    }
                    .collect { }
                _uiState.update {
                    it.copy(
                        testResult = DeepLinkTestResult(
                            uri = config.uri,
                            success = true,
                            message = "Intent fired successfully"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        testResult = DeepLinkTestResult(
                            uri = config.uri,
                            success = false,
                            message = e.message ?: "Failed to fire intent"
                        )
                    )
                }
            }
        }
    }

    /**
     * Toggle favorite status for a deep link URI
     */
    fun toggleFavorite(uri: String, name: String) {
        viewModelScope.launch {
            if (_uiState.value.favoriteUris.contains(uri)) {
                val favorites = AppContainer.observeFavoritesUseCase().first()
                val favorite = favorites.find { it.uri == uri }
                if (favorite != null) {
                    AppContainer.removeFavoriteUseCase(favorite.id)
                }
            } else {
                AppContainer.addFavoriteUseCase(uri, name)
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        searchJob?.cancel()
        viewModelScope.cancel()
    }
}
