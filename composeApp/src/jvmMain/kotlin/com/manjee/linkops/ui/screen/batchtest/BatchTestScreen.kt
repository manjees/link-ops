package com.manjee.linkops.ui.screen.batchtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjee.linkops.domain.model.BatchTestResult
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.LaunchResult
import com.manjee.linkops.domain.model.TestScenario
import com.manjee.linkops.ui.component.EmptyState
import com.manjee.linkops.ui.component.LoadingOverlay
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Batch Test Screen
 *
 * Provides scenario editing, batch execution, and result reporting
 * for deep link testing
 */
@Composable
fun BatchTestScreen(
    viewModel: BatchTestViewModel,
    devices: List<Device>,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-select first online device
    LaunchedEffect(devices) {
        if (uiState.selectedDevice == null && devices.isNotEmpty()) {
            val onlineDevice = devices.firstOrNull { it.state == Device.DeviceState.ONLINE }
            onlineDevice?.let { viewModel.setDevice(it) }
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    // Show import success snackbar
    LaunchedEffect(uiState.importSuccess) {
        if (uiState.importSuccess) {
            snackbarHostState.showSnackbar(
                message = "Scenario imported successfully",
                duration = SnackbarDuration.Short
            )
            viewModel.clearImportSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Left Panel - Scenario Editor
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ScenarioEditorPanel(
                    scenario = uiState.scenario,
                    devices = devices,
                    selectedDevice = uiState.selectedDevice,
                    resolvedUris = uiState.resolvedUris,
                    isExecuting = uiState.isExecuting,
                    onDeviceSelected = { viewModel.setDevice(it) },
                    onNameChange = { viewModel.updateScenarioName(it) },
                    onDescriptionChange = { viewModel.updateScenarioDescription(it) },
                    onDelayChange = { viewModel.updateDelay(it) },
                    onAddTemplate = { uri, name, expected -> viewModel.addTemplate(uri, name, expected) },
                    onRemoveTemplate = { viewModel.removeTemplate(it) },
                    onAddVariable = { name, values -> viewModel.addVariable(name, values) },
                    onRemoveVariable = { viewModel.removeVariable(it) },
                    onExecute = { viewModel.executeBatchTest() },
                    onStop = { viewModel.stopExecution() },
                    onExport = { viewModel.exportScenario() },
                    onImport = { viewModel.importScenario(it) },
                    onNew = { viewModel.newScenario() }
                )
            }

            // Right Panel - Results
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                ResultsPanel(
                    batchResult = uiState.batchResult,
                    results = uiState.results,
                    isExecuting = uiState.isExecuting,
                    executionProgress = uiState.executionProgress,
                    executionTotal = uiState.executionTotal,
                    onClear = { viewModel.clearResults() }
                )
            }
        }
    }

    // Export dialog
    uiState.exportedJson?.let { json ->
        ExportDialog(
            json = json,
            onDismiss = { viewModel.clearExportedJson() }
        )
    }

    // Loading overlay during execution
    LoadingOverlay(
        isLoading = uiState.isExecuting,
        message = "Executing batch test... (${uiState.executionProgress}/${uiState.executionTotal})"
    )
}

/**
 * Scenario editor panel (left side)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenarioEditorPanel(
    scenario: TestScenario,
    devices: List<Device>,
    selectedDevice: Device?,
    resolvedUris: List<String>,
    isExecuting: Boolean,
    onDeviceSelected: (Device) -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDelayChange: (Long) -> Unit,
    onAddTemplate: (String, String, String) -> Unit,
    onRemoveTemplate: (Int) -> Unit,
    onAddVariable: (String, List<String>) -> Unit,
    onRemoveVariable: (String) -> Unit,
    onExecute: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
    onNew: () -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Batch Test",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onNew, enabled = !isExecuting) {
                    Icon(Icons.Default.Add, contentDescription = "New Scenario")
                }
                IconButton(onClick = onExport, enabled = !isExecuting) {
                    Icon(Icons.Default.Share, contentDescription = "Export")
                }
                IconButton(onClick = { showImportDialog = true }, enabled = !isExecuting) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Import")
                }
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device selector
            item {
                DeviceSelector(
                    devices = devices,
                    selectedDevice = selectedDevice,
                    onDeviceSelected = onDeviceSelected
                )
            }

            // Scenario metadata
            item {
                ScenarioMetadataSection(
                    scenario = scenario,
                    onNameChange = onNameChange,
                    onDescriptionChange = onDescriptionChange,
                    onDelayChange = onDelayChange
                )
            }

            // Deep link templates
            item {
                TemplatesSection(
                    templates = scenario.templates,
                    onAddTemplate = onAddTemplate,
                    onRemoveTemplate = onRemoveTemplate,
                    isExecuting = isExecuting
                )
            }

            // Variables
            item {
                VariablesSection(
                    variables = scenario.variables,
                    onAddVariable = onAddVariable,
                    onRemoveVariable = onRemoveVariable,
                    isExecuting = isExecuting
                )
            }

            // Resolved URIs preview
            if (resolvedUris.isNotEmpty()) {
                item {
                    ResolvedUrisPreview(resolvedUris = resolvedUris)
                }
            }
        }

        // Execute button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isExecuting) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LinkOpsColors.Error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Execution")
                }
            } else {
                Button(
                    onClick = onExecute,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedDevice != null && scenario.templates.isNotEmpty()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Execute Batch Test (${scenario.totalLaunchCount} launches)")
                }
            }
        }
    }

    // Import dialog
    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { json ->
                onImport(json)
                showImportDialog = false
            }
        )
    }
}

/**
 * Device selector dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelector(
    devices: List<Device>,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Device",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedDevice?.let { "${it.model} (${it.serialNumber})" } ?: "Select device",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                devices.filter { it.state == Device.DeviceState.ONLINE }.forEach { device ->
                    DropdownMenuItem(
                        text = { Text("${device.model} (${device.serialNumber})") },
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

/**
 * Scenario metadata inputs
 */
@Composable
private fun ScenarioMetadataSection(
    scenario: TestScenario,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDelayChange: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Scenario",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = scenario.name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = scenario.description,
            onValueChange = onDescriptionChange,
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        OutlinedTextField(
            value = scenario.delayBetweenLaunchesMs.toString(),
            onValueChange = { value ->
                value.toLongOrNull()?.let { onDelayChange(it.coerceAtLeast(0)) }
            },
            label = { Text("Delay between launches (ms)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

/**
 * Deep link templates section
 */
@Composable
private fun TemplatesSection(
    templates: List<com.manjee.linkops.domain.model.DeepLinkTemplate>,
    onAddTemplate: (String, String, String) -> Unit,
    onRemoveTemplate: (Int) -> Unit,
    isExecuting: Boolean
) {
    var newUri by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Deep Link Templates (${templates.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        // Add new template
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = newUri,
                    onValueChange = { newUri = it },
                    label = { Text("URI (e.g., myapp://product/{id})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isExecuting
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isExecuting
                )
            }

            IconButton(
                onClick = {
                    onAddTemplate(newUri, newName, "")
                    newUri = ""
                    newName = ""
                },
                enabled = newUri.isNotBlank() && !isExecuting
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add template")
            }
        }

        // Template list
        templates.forEachIndexed { index, template ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (template.name.isNotBlank()) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = template.uri,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onRemoveTemplate(index) },
                        enabled = !isExecuting
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = LinkOpsColors.Error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Variables section for combinatorial testing
 */
@Composable
private fun VariablesSection(
    variables: Map<String, List<String>>,
    onAddVariable: (String, List<String>) -> Unit,
    onRemoveVariable: (String) -> Unit,
    isExecuting: Boolean
) {
    var newVarName by remember { mutableStateOf("") }
    var newVarValues by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Variables (${variables.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        // Add new variable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = newVarName,
                    onValueChange = { newVarName = it },
                    label = { Text("Variable name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isExecuting
                )
                OutlinedTextField(
                    value = newVarValues,
                    onValueChange = { newVarValues = it },
                    label = { Text("Values (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isExecuting
                )
            }

            IconButton(
                onClick = {
                    val values = newVarValues.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onAddVariable(newVarName, values)
                    newVarName = ""
                    newVarValues = ""
                },
                enabled = newVarName.isNotBlank() && newVarValues.isNotBlank() && !isExecuting
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add variable")
            }
        }

        // Variable list
        variables.forEach { (name, values) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "{$name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = values.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onRemoveVariable(name) },
                        enabled = !isExecuting
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = LinkOpsColors.Error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview of resolved URIs after variable substitution
 */
@Composable
private fun ResolvedUrisPreview(resolvedUris: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Resolved URIs Preview (${resolvedUris.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = LinkOpsColors.TerminalBackground
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                resolvedUris.take(20).forEachIndexed { index, uri ->
                    Text(
                        text = "${index + 1}. $uri",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = LinkOpsColors.TerminalText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (resolvedUris.size > 20) {
                    Text(
                        text = "... and ${resolvedUris.size - 20} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkOpsColors.TerminalInfo
                    )
                }
            }
        }
    }
}

/**
 * Results panel (right side)
 */
@Composable
private fun ResultsPanel(
    batchResult: BatchTestResult?,
    results: List<LaunchResult>,
    isExecuting: Boolean,
    executionProgress: Int,
    executionTotal: Int,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (batchResult != null || results.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }

        // Progress indicator during execution
        if (isExecuting) {
            LinearProgressIndicator(
                progress = { if (executionTotal > 0) executionProgress.toFloat() / executionTotal else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Progress: $executionProgress / $executionTotal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Summary card
        batchResult?.let { result ->
            ResultSummaryCard(result = result)
        }

        if (results.isEmpty() && !isExecuting) {
            EmptyState(
                title = "No results yet",
                description = "Configure a scenario and execute batch test",
                icon = Icons.Default.PlayArrow
            )
        } else {
            // Result list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    ResultItemCard(result = result)
                }
            }
        }
    }
}

/**
 * Result summary card
 */
@Composable
private fun ResultSummaryCard(result: BatchTestResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Summary: ${result.scenarioName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(label = "Total", value = result.totalCount.toString())
                SummaryStatItem(
                    label = "Success",
                    value = result.successCount.toString(),
                    color = LinkOpsColors.Success
                )
                SummaryStatItem(
                    label = "Failed",
                    value = result.failureCount.toString(),
                    color = if (result.failureCount > 0) LinkOpsColors.Error else MaterialTheme.colorScheme.onSurface
                )
                SummaryStatItem(
                    label = "Time",
                    value = "${result.totalTimeMs}ms"
                )
            }

            // Success rate bar
            LinearProgressIndicator(
                progress = { result.successRate },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = LinkOpsColors.Success,
                trackColor = LinkOpsColors.ErrorLight
            )

            Text(
                text = "Success rate: ${"%.1f".format(result.successRate * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Individual result item card
 */
@Composable
private fun ResultItemCard(result: LaunchResult) {
    val (bgColor, statusIcon, statusColor) = when (result) {
        is LaunchResult.Success -> Triple(LinkOpsColors.SuccessLight, "OK", LinkOpsColors.Success)
        is LaunchResult.Error -> Triple(LinkOpsColors.ErrorLight, "FAIL", LinkOpsColors.Error)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status badge
            Box(
                modifier = Modifier
                    .background(statusColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusIcon,
                    style = MaterialTheme.typography.labelSmall,
                    color = LinkOpsColors.OnPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.uri,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                when (result) {
                    is LaunchResult.Success -> {
                        Text(
                            text = "Activity: ${result.resolvedActivity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is LaunchResult.Error -> {
                        Text(
                            text = result.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkOpsColors.Error
                        )
                    }
                }
            }

            // Timing
            val timeTaken = when (result) {
                is LaunchResult.Success -> result.timeTakenMs
                is LaunchResult.Error -> result.timeTakenMs
            }
            Text(
                text = "${timeTaken}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Export dialog showing JSON content
 */
@Composable
private fun ExportDialog(
    json: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Scenario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Copy the JSON below to share this scenario:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LinkOpsColors.TerminalBackground
                    )
                ) {
                    Text(
                        text = json,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = LinkOpsColors.TerminalText,
                        modifier = Modifier
                            .padding(12.dp)
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Import dialog for pasting JSON
 */
@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var jsonInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Scenario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste the scenario JSON below:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(jsonInput) },
                enabled = jsonInput.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
