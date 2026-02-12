package com.manjee.linkops.ui.screen.localhosting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.manjee.linkops.domain.model.ServerStatus
import com.manjee.linkops.domain.model.VerificationStep
import com.manjee.linkops.ui.component.LoadingOverlay
import com.manjee.linkops.ui.component.StatusDot
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Local Hosting Screen
 *
 * Provides local AssetLinks server management and verification workflow:
 * - Server configuration (host, port)
 * - Certificate fingerprint extraction from device
 * - assetlinks.json preview
 * - One-click verification workflow
 */
@Composable
fun LocalHostingScreen(
    viewModel: LocalHostingViewModel,
    devices: List<Device>,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(devices) {
        if (uiState.selectedDevice == null) {
            devices.firstOrNull { it.isAvailable }?.let { viewModel.selectDevice(it) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Left Panel - Configuration
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Local Hosting",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ServerConfigSection(
                        host = uiState.host,
                        port = uiState.port,
                        onHostChange = { viewModel.updateHost(it) },
                        onPortChange = { viewModel.updatePort(it) },
                        serverStatus = uiState.serverStatus,
                        onStartServer = { viewModel.startServer() },
                        onStopServer = { viewModel.stopServer() }
                    )
                }

                item { HorizontalDivider() }

                item {
                    FingerprintSection(
                        packageName = uiState.packageName,
                        fingerprint = uiState.fingerprint,
                        onPackageNameChange = { viewModel.updatePackageName(it) },
                        onFingerprintChange = { viewModel.updateFingerprint(it) },
                        devices = devices,
                        selectedDevice = uiState.selectedDevice,
                        onDeviceSelected = { viewModel.selectDevice(it) },
                        onExtractFingerprint = { viewModel.extractFingerprint() },
                        isExtracting = uiState.isExtractingFingerprint,
                        onGeneratePreview = { viewModel.generatePreview() }
                    )
                }

                item { HorizontalDivider() }

                item {
                    VerificationWorkflowSection(
                        onRunWorkflow = { viewModel.runVerificationWorkflow() },
                        isRunning = uiState.isRunningWorkflow,
                        hasDevice = uiState.selectedDevice != null,
                        hasConfig = uiState.packageName.isNotBlank() && uiState.fingerprint.isNotBlank()
                    )
                }
            }

            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LinkOpsColors.ErrorLight
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = LinkOpsColors.Error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkOpsColors.Error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Right Panel - Preview & Results
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ResultsPanel(
                generatedJson = uiState.generatedJson,
                workflowSteps = uiState.workflowSteps,
                isRunningWorkflow = uiState.isRunningWorkflow,
                serverStatus = uiState.serverStatus,
                onClearWorkflow = { viewModel.clearWorkflow() }
            )
        }
    }

    LoadingOverlay(
        isLoading = uiState.isExtractingFingerprint,
        message = "Extracting fingerprint..."
    )
}

/**
 * Server configuration section
 */
@Composable
private fun ServerConfigSection(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    serverStatus: ServerStatus,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Server Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host") },
                modifier = Modifier.weight(2f),
                singleLine = true,
                enabled = serverStatus != ServerStatus.RUNNING
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = serverStatus != ServerStatus.RUNNING
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServerStatusIndicator(serverStatus)

            if (serverStatus == ServerStatus.RUNNING) {
                Button(
                    onClick = onStopServer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LinkOpsColors.Error
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop Server")
                }
            } else {
                Button(
                    onClick = onStartServer,
                    enabled = serverStatus == ServerStatus.STOPPED || serverStatus == ServerStatus.ERROR
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Server")
                }
            }
        }
    }
}

/**
 * Server status indicator
 */
@Composable
private fun ServerStatusIndicator(status: ServerStatus) {
    val (text, color) = when (status) {
        ServerStatus.STOPPED -> "Stopped" to LinkOpsColors.Unknown
        ServerStatus.STARTING -> "Starting..." to LinkOpsColors.Warning
        ServerStatus.RUNNING -> "Running" to LinkOpsColors.Success
        ServerStatus.STOPPING -> "Stopping..." to LinkOpsColors.Warning
        ServerStatus.ERROR -> "Error" to LinkOpsColors.Error
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = color)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Fingerprint extraction section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FingerprintSection(
    packageName: String,
    fingerprint: String,
    onPackageNameChange: (String) -> Unit,
    onFingerprintChange: (String) -> Unit,
    devices: List<Device>,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit,
    onExtractFingerprint: () -> Unit,
    isExtracting: Boolean,
    onGeneratePreview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "App Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Device selector
        val availableDevices = devices.filter { it.isAvailable }
        if (availableDevices.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedDevice?.displayName ?: "Select a device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableDevices.forEach { device ->
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
        } else {
            Text(
                text = "No devices available. Connect a device first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = packageName,
            onValueChange = onPackageNameChange,
            label = { Text("Package Name") },
            placeholder = { Text("com.example.app") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = fingerprint,
                onValueChange = onFingerprintChange,
                label = { Text("SHA-256 Fingerprint") },
                placeholder = { Text("AB:CD:EF:01:23:...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = onExtractFingerprint,
                enabled = !isExtracting && selectedDevice != null && packageName.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Extract")
            }
        }

        OutlinedButton(
            onClick = onGeneratePreview,
            enabled = packageName.isNotBlank() && fingerprint.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Preview,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Generate Preview")
        }
    }
}

/**
 * Verification workflow trigger section
 */
@Composable
private fun VerificationWorkflowSection(
    onRunWorkflow: () -> Unit,
    isRunning: Boolean,
    hasDevice: Boolean,
    hasConfig: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Verification Workflow",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Start server, trigger pm verify-app-links, and observe results in one click.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onRunWorkflow,
            enabled = !isRunning && hasDevice && hasConfig,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    Icons.Default.RocketLaunch,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isRunning) "Running..." else "Run Verification")
        }
    }
}

/**
 * Right panel showing JSON preview and workflow results
 */
@Composable
private fun ResultsPanel(
    generatedJson: String,
    workflowSteps: List<VerificationStep>,
    isRunningWorkflow: Boolean,
    serverStatus: ServerStatus,
    onClearWorkflow: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // JSON Preview
        if (generatedJson.isNotBlank()) {
            JsonPreviewCard(generatedJson)
        }

        // Workflow Results
        if (workflowSteps.isNotEmpty() || isRunningWorkflow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Verification Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (workflowSteps.isNotEmpty() && !isRunningWorkflow) {
                    IconButton(onClick = onClearWorkflow) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workflowSteps) { step ->
                    WorkflowStepCard(step)
                }
            }
        }

        // Empty state
        if (generatedJson.isBlank() && workflowSteps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Configure and start the local server",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Enter a package name and fingerprint, then generate a preview or run the full workflow",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Card showing generated assetlinks.json preview
 */
@Composable
private fun JsonPreviewCard(json: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = LinkOpsColors.TerminalBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "assetlinks.json Preview",
                style = MaterialTheme.typography.labelMedium,
                color = LinkOpsColors.TerminalInfo
            )
            SelectionContainer {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = LinkOpsColors.TerminalText
                )
            }
        }
    }
}

/**
 * Card for a single verification workflow step
 */
@Composable
private fun WorkflowStepCard(step: VerificationStep) {
    val (icon, color) = when (step.status) {
        VerificationStep.StepStatus.PENDING -> Icons.Default.Schedule to LinkOpsColors.Unknown
        VerificationStep.StepStatus.IN_PROGRESS -> Icons.Default.Sync to LinkOpsColors.Info
        VerificationStep.StepStatus.SUCCESS -> Icons.Default.CheckCircle to LinkOpsColors.Success
        VerificationStep.StepStatus.FAILURE -> Icons.Default.Error to LinkOpsColors.Error
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (step.message.isNotBlank()) {
                    Text(
                        text = step.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
