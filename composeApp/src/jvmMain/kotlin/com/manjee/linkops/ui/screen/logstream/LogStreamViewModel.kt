package com.manjee.linkops.ui.screen.logstream

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.LogEntry
import com.manjee.linkops.domain.model.LogFilter
import com.manjee.linkops.domain.model.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Streaming state for the log streamer
 */
enum class StreamingState {
    IDLE,
    STREAMING,
    PAUSED
}

/**
 * UI State for Log Stream Screen
 */
data class LogStreamUiState(
    val streamingState: StreamingState = StreamingState.IDLE,
    val entries: List<LogEntry> = emptyList(),
    val filter: LogFilter = LogFilter(),
    val searchQuery: String = "",
    val selectedLevel: LogLevel = LogLevel.VERBOSE,
    val customTagInput: String = "",
    val customKeywordInput: String = "",
    val showOnlyDeepLinkEvents: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for Log Stream Screen
 *
 * Manages real-time logcat streaming, filtering, and log entry display
 */
class LogStreamViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(LogStreamUiState())
    val uiState: StateFlow<LogStreamUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private val allEntries = mutableListOf<LogEntry>()

    /**
     * Start streaming logcat from the given device
     */
    fun startStreaming(device: Device) {
        if (_uiState.value.streamingState == StreamingState.STREAMING) return

        streamJob?.cancel()
        _uiState.update { it.copy(streamingState = StreamingState.STREAMING, error = null) }

        streamJob = viewModelScope.launch {
            try {
                AppContainer.observeLogStreamUseCase(
                    deviceSerial = device.serialNumber,
                    filter = _uiState.value.filter
                ).collect { entry ->
                    if (_uiState.value.streamingState == StreamingState.STREAMING) {
                        addEntry(entry)
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation, do nothing
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        streamingState = StreamingState.IDLE,
                        error = e.message ?: "Stream failed"
                    )
                }
            }
        }
    }

    /**
     * Stop streaming
     */
    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { it.copy(streamingState = StreamingState.IDLE) }
    }

    /**
     * Pause streaming (keeps connection but stops adding entries)
     */
    fun pauseStreaming() {
        if (_uiState.value.streamingState == StreamingState.STREAMING) {
            _uiState.update { it.copy(streamingState = StreamingState.PAUSED) }
        }
    }

    /**
     * Resume streaming after pause
     */
    fun resumeStreaming() {
        if (_uiState.value.streamingState == StreamingState.PAUSED) {
            _uiState.update { it.copy(streamingState = StreamingState.STREAMING) }
        }
    }

    /**
     * Clear all log entries
     */
    fun clearEntries() {
        allEntries.clear()
        _uiState.update { it.copy(entries = emptyList()) }
    }

    /**
     * Update search query for filtering displayed entries
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshFilteredEntries()
    }

    /**
     * Update minimum log level filter
     */
    fun updateLogLevel(level: LogLevel) {
        _uiState.update {
            it.copy(
                selectedLevel = level,
                filter = it.filter.copy(minLogLevel = level)
            )
        }
        refreshFilteredEntries()
    }

    /**
     * Toggle deep link events only filter
     */
    fun toggleDeepLinkEventsOnly() {
        _uiState.update { it.copy(showOnlyDeepLinkEvents = !it.showOnlyDeepLinkEvents) }
        refreshFilteredEntries()
    }

    /**
     * Update custom tag input field
     */
    fun updateCustomTagInput(input: String) {
        _uiState.update { it.copy(customTagInput = input) }
    }

    /**
     * Add a custom tag to the filter
     */
    fun addCustomTag() {
        val tag = _uiState.value.customTagInput.trim()
        if (tag.isBlank()) return

        _uiState.update {
            it.copy(
                filter = it.filter.copy(tags = it.filter.tags + tag),
                customTagInput = ""
            )
        }
    }

    /**
     * Remove a tag from the filter
     */
    fun removeTag(tag: String) {
        _uiState.update {
            it.copy(filter = it.filter.copy(tags = it.filter.tags - tag))
        }
    }

    /**
     * Update custom keyword input field
     */
    fun updateCustomKeywordInput(input: String) {
        _uiState.update { it.copy(customKeywordInput = input) }
    }

    /**
     * Add a custom keyword to the filter
     */
    fun addCustomKeyword() {
        val keyword = _uiState.value.customKeywordInput.trim()
        if (keyword.isBlank()) return

        _uiState.update {
            it.copy(
                filter = it.filter.copy(keywords = it.filter.keywords + keyword),
                customKeywordInput = ""
            )
        }
        refreshFilteredEntries()
    }

    /**
     * Remove a keyword from the filter
     */
    fun removeKeyword(keyword: String) {
        _uiState.update {
            it.copy(filter = it.filter.copy(keywords = it.filter.keywords - keyword))
        }
        refreshFilteredEntries()
    }

    /**
     * Generate exportable text from current log entries
     * @return Formatted log text
     */
    fun exportLogs(): String {
        return _uiState.value.entries.joinToString("\n") { entry ->
            "${entry.timestamp} ${entry.level.initial}/${entry.tag}: ${entry.message}"
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

    private fun addEntry(entry: LogEntry) {
        // Ring buffer: keep max entries to prevent memory issues
        if (allEntries.size >= MAX_ENTRIES) {
            allEntries.removeAt(0)
        }
        allEntries.add(entry)
        refreshFilteredEntries()
    }

    private fun refreshFilteredEntries() {
        val state = _uiState.value
        val filtered = allEntries.filter { entry ->
            val matchesSearch = state.searchQuery.isBlank() ||
                    entry.message.contains(state.searchQuery, ignoreCase = true) ||
                    entry.tag.contains(state.searchQuery, ignoreCase = true)

            val matchesDeepLinkFilter = !state.showOnlyDeepLinkEvents || entry.isDeepLinkEvent

            matchesSearch && matchesDeepLinkFilter
        }
        _uiState.update { it.copy(entries = filtered) }
    }

    companion object {
        private const val MAX_ENTRIES = 5000
    }
}
