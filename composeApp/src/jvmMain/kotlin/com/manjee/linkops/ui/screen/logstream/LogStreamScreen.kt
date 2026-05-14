package com.manjee.linkops.ui.screen.logstream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.LogLevel
import com.manjee.linkops.ui.component.EmptyState
import com.manjee.linkops.ui.theme.LinkOpsColors
import com.manjee.linkops.domain.model.DeepLinkEventType
import com.manjee.linkops.domain.model.LogEntry as DomainLogEntry

/**
 * Log Stream Screen - Real-time logcat streaming with deep link filtering
 *
 * Layout:
 * - Left panel: Controls (device selector, filters, streaming controls)
 * - Right panel: Log output with color-coded entries
 */
@Composable
fun LogStreamScreen(
    viewModel: LogStreamViewModel,
    devices: List<Device>,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedDevice by remember { mutableStateOf<Device?>(null) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Left Panel - Controls
        ControlPanel(
            uiState = uiState,
            devices = devices,
            selectedDevice = selectedDevice,
            onDeviceSelected = { selectedDevice = it },
            onStartStreaming = { device -> viewModel.startStreaming(device) },
            onStopStreaming = { viewModel.stopStreaming() },
            onPauseStreaming = { viewModel.pauseStreaming() },
            onResumeStreaming = { viewModel.resumeStreaming() },
            onClearEntries = { viewModel.clearEntries() },
            onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
            onLogLevelChanged = { viewModel.updateLogLevel(it) },
            onToggleDeepLinkEvents = { viewModel.toggleDeepLinkEventsOnly() },
            onCustomTagInputChanged = { viewModel.updateCustomTagInput(it) },
            onAddCustomTag = { viewModel.addCustomTag() },
            onRemoveTag = { viewModel.removeTag(it) },
            onCustomKeywordInputChanged = { viewModel.updateCustomKeywordInput(it) },
            onAddCustomKeyword = { viewModel.addCustomKeyword() },
            onRemoveKeyword = { viewModel.removeKeyword(it) },
            onExport = { viewModel.exportLogs() },
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
        )

        // Right Panel - Log Output
        LogStreamPanel(
            entries = uiState.entries,
            streamingState = uiState.streamingState,
            onClear = { viewModel.clearEntries() },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

/**
 * Left panel with streaming controls and filters
 */
@Composable
private fun ControlPanel(
    uiState: LogStreamUiState,
    devices: List<Device>,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit,
    onStartStreaming: (Device) -> Unit,
    onStopStreaming: () -> Unit,
    onPauseStreaming: () -> Unit,
    onResumeStreaming: () -> Unit,
    onClearEntries: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onLogLevelChanged: (LogLevel) -> Unit,
    onToggleDeepLinkEvents: () -> Unit,
    onCustomTagInputChanged: (String) -> Unit,
    onAddCustomTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onCustomKeywordInputChanged: (String) -> Unit,
    onAddCustomKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onExport: () -> String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "Log Streamer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device Selection
            item {
                DeviceSelectionSection(
                    devices = devices,
                    selectedDevice = selectedDevice,
                    onDeviceSelected = onDeviceSelected
                )
            }

            item { HorizontalDivider() }

            // Streaming Controls
            item {
                StreamingControlsSection(
                    streamingState = uiState.streamingState,
                    selectedDevice = selectedDevice,
                    entryCount = uiState.entries.size,
                    onStart = { selectedDevice?.let { onStartStreaming(it) } },
                    onStop = onStopStreaming,
                    onPause = onPauseStreaming,
                    onResume = onResumeStreaming,
                    onClear = onClearEntries,
                    onExport = onExport
                )
            }

            item { HorizontalDivider() }

            // Search & Filter
            item {
                SearchFilterSection(
                    searchQuery = uiState.searchQuery,
                    selectedLevel = uiState.selectedLevel,
                    showOnlyDeepLinkEvents = uiState.showOnlyDeepLinkEvents,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onLogLevelChanged = onLogLevelChanged,
                    onToggleDeepLinkEvents = onToggleDeepLinkEvents
                )
            }

            item { HorizontalDivider() }

            // Tag & Keyword Filters
            item {
                TagKeywordSection(
                    tags = uiState.filter.tags,
                    keywords = uiState.filter.keywords,
                    customTagInput = uiState.customTagInput,
                    customKeywordInput = uiState.customKeywordInput,
                    onCustomTagInputChanged = onCustomTagInputChanged,
                    onAddCustomTag = onAddCustomTag,
                    onRemoveTag = onRemoveTag,
                    onCustomKeywordInputChanged = onCustomKeywordInputChanged,
                    onAddCustomKeyword = onAddCustomKeyword,
                    onRemoveKeyword = onRemoveKeyword
                )
            }
        }

        // Error display
        uiState.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = LinkOpsColors.Error
            )
        }
    }
}

/**
 * Device selection section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectionSection(
    devices: List<Device>,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Device",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (devices.isEmpty()) {
            Text(
                text = "No devices connected. Refresh from Dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedDevice?.displayName ?: "Select a device",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    devices.filter { it.isAvailable }.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.displayName) },
                            onClick = {
                                onDeviceSelected(device)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Streaming control buttons
 */
@Composable
private fun StreamingControlsSection(
    streamingState: StreamingState,
    selectedDevice: Device?,
    entryCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Entry count badge
            Text(
                text = "$entryCount entries",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Streaming state indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (statusText, statusColor) = when (streamingState) {
                StreamingState.IDLE -> "Idle" to LinkOpsColors.Unknown
                StreamingState.STREAMING -> "Streaming" to LinkOpsColors.Success
                StreamingState.PAUSED -> "Paused" to LinkOpsColors.Warning
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, RoundedCornerShape(50))
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (streamingState) {
                StreamingState.IDLE -> {
                    Button(
                        onClick = onStart,
                        enabled = selectedDevice != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LinkOpsColors.Success
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start")
                    }
                }
                StreamingState.STREAMING -> {
                    Button(
                        onClick = onPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LinkOpsColors.Warning
                        )
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LinkOpsColors.Error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
                StreamingState.PAUSED -> {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LinkOpsColors.Success
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LinkOpsColors.Error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        }

        // Clear & Export
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }

            OutlinedButton(
                onClick = { onExport() },
                enabled = entryCount > 0
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export")
            }
        }
    }
}

/**
 * Search and filter controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterSection(
    searchQuery: String,
    selectedLevel: LogLevel,
    showOnlyDeepLinkEvents: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onLogLevelChanged: (LogLevel) -> Unit,
    onToggleDeepLinkEvents: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Filters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            label = { Text("Search logs") },
            placeholder = { Text("Filter by keyword...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            }
        )

        // Log level dropdown
        var levelExpanded by remember { mutableStateOf(false) }
        val filterLevels = listOf(
            LogLevel.VERBOSE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR
        )

        ExposedDropdownMenuBox(
            expanded = levelExpanded,
            onExpandedChange = { levelExpanded = !levelExpanded }
        ) {
            OutlinedTextField(
                value = "Min Level: ${selectedLevel.name}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = levelExpanded,
                onDismissRequest = { levelExpanded = false }
            ) {
                filterLevels.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.name) },
                        onClick = {
                            onLogLevelChanged(level)
                            levelExpanded = false
                        }
                    )
                }
            }
        }

        // Deep link events only toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Deep link events only",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = showOnlyDeepLinkEvents,
                onCheckedChange = { onToggleDeepLinkEvents() }
            )
        }
    }
}

/**
 * Tag and keyword filter management
 */
@Composable
private fun TagKeywordSection(
    tags: Set<String>,
    keywords: Set<String>,
    customTagInput: String,
    customKeywordInput: String,
    onCustomTagInputChanged: (String) -> Unit,
    onAddCustomTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onCustomKeywordInputChanged: (String) -> Unit,
    onAddCustomKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Current tags as chips
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { onRemoveTag(tag) },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove $tag",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }

        // Add custom tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = customTagInput,
                onValueChange = onCustomTagInputChanged,
                placeholder = { Text("Add tag...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = onAddCustomTag) {
                Icon(Icons.Default.Add, contentDescription = "Add tag")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Keywords",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        // Current keywords as chips
        if (keywords.isNotEmpty()) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                keywords.forEach { keyword ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveKeyword(keyword) },
                        label = { Text(keyword, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $keyword",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }

        // Add custom keyword
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = customKeywordInput,
                onValueChange = onCustomKeywordInputChanged,
                placeholder = { Text("Add keyword...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = onAddCustomKeyword) {
                Icon(Icons.Default.Add, contentDescription = "Add keyword")
            }
        }
    }
}

/**
 * Right panel - Log stream output
 */
@Composable
private fun LogStreamPanel(
    entries: List<DomainLogEntry>,
    streamingState: StreamingState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Log Output",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (streamingState == StreamingState.STREAMING) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = LinkOpsColors.OnPrimary,
                        modifier = Modifier
                            .background(LinkOpsColors.Error, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear log",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    LinkOpsColors.TerminalBackground,
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            if (entries.isEmpty()) {
                EmptyState(
                    title = "No log entries",
                    description = "Start streaming to capture logcat output",
                    icon = Icons.Default.Terminal
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entries) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }
    }
}

/**
 * Single log entry row with color-coded level
 */
@Composable
private fun LogEntryRow(entry: DomainLogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.VERBOSE -> LinkOpsColors.TerminalText.copy(alpha = 0.5f)
        LogLevel.DEBUG -> LinkOpsColors.TerminalText.copy(alpha = 0.7f)
        LogLevel.INFO -> LinkOpsColors.TerminalInfo
        LogLevel.WARNING -> LinkOpsColors.TerminalWarning
        LogLevel.ERROR -> LinkOpsColors.TerminalError
        LogLevel.FATAL -> LinkOpsColors.Error
        LogLevel.UNKNOWN -> LinkOpsColors.TerminalText.copy(alpha = 0.4f)
    }

    val eventColor = when (entry.deepLinkEvent?.type) {
        DeepLinkEventType.CLICKED -> LinkOpsColors.Info
        DeepLinkEventType.RESOLVED -> LinkOpsColors.Primary
        DeepLinkEventType.STARTED -> LinkOpsColors.Success
        DeepLinkEventType.RESULT -> LinkOpsColors.Success
        DeepLinkEventType.ERROR -> LinkOpsColors.Error
        null -> null
    }

    Column(modifier = Modifier.padding(vertical = 1.dp)) {
        Row {
            // Timestamp
            if (entry.timestamp.isNotEmpty()) {
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = LinkOpsColors.TerminalText.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Level
            Text(
                text = entry.level.initial,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = levelColor
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Tag
            if (entry.tag.isNotEmpty()) {
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = LinkOpsColors.TerminalInfo.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Message
            SelectionContainer {
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = LinkOpsColors.TerminalText
                )
            }
        }

        // Deep link event indicator
        if (entry.deepLinkEvent != null && eventColor != null) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(eventColor, RoundedCornerShape(50))
                )
                Text(
                    text = "${entry.deepLinkEvent.type.name}: ${entry.deepLinkEvent.description}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = eventColor
                )
            }
        }
    }
}
