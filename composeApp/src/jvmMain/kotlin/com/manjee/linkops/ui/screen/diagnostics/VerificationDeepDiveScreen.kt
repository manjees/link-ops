package com.manjee.linkops.ui.screen.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjee.linkops.domain.model.*
import com.manjee.linkops.ui.component.EmptyState
import com.manjee.linkops.ui.component.LoadingOverlay
import com.manjee.linkops.ui.component.StatusDot
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Verification Deep Dive Screen
 *
 * Provides deep verification analysis for a specific package, including:
 * - Per-domain verification status
 * - Certificate fingerprint comparison
 * - Root cause analysis with actionable suggestions
 */
@Composable
fun VerificationDeepDiveScreen(
    viewModel: VerificationDeepDiveViewModel,
    devices: List<Device>,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Sync device selection from available devices
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
        // Left Panel - Input
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Verification Deep Dive",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            InputSection(
                packageName = uiState.packageName,
                onPackageNameChange = { viewModel.updatePackageName(it) },
                devices = devices,
                selectedDevice = uiState.selectedDevice,
                onDeviceSelected = { viewModel.selectDevice(it) },
                onAnalyze = { viewModel.analyzeVerification() },
                isLoading = uiState.isLoading,
                error = uiState.error
            )

            // Summary card when diagnostics are available
            if (uiState.diagnostics != null) {
                HorizontalDivider()
                SummaryCard(uiState.diagnostics!!)
            }
        }

        // Right Panel - Results
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            DiagnosticsResultsPanel(
                diagnostics = uiState.diagnostics,
                isLoading = uiState.isLoading,
                onClear = { viewModel.clearResult() }
            )
        }
    }

    LoadingOverlay(
        isLoading = uiState.isLoading,
        message = "Analyzing verification status..."
    )
}

/**
 * Input section for device and package selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSection(
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    devices: List<Device>,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit,
    onAnalyze: () -> Unit,
    isLoading: Boolean,
    error: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Analysis Target",
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

        // Package name input
        OutlinedTextField(
            value = packageName,
            onValueChange = onPackageNameChange,
            label = { Text("Package Name") },
            placeholder = { Text("com.example.app") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (packageName.isNotEmpty()) {
                    IconButton(onClick = { onPackageNameChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error, color = MaterialTheme.colorScheme.error) }
            } else null
        )

        Button(
            onClick = onAnalyze,
            enabled = !isLoading && packageName.isNotBlank() && selectedDevice != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analyze Verification")
        }

        Text(
            text = "Analyzes domain verification status, compares certificate fingerprints, and identifies failure root causes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Summary card showing overall diagnostics status
 */
@Composable
private fun SummaryCard(diagnostics: VerificationDiagnostics) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(
                    label = "Total Domains",
                    value = "${diagnostics.totalDomains}",
                    color = MaterialTheme.colorScheme.onSurface
                )
                SummaryItem(
                    label = "Verified",
                    value = "${diagnostics.verifiedDomains}",
                    color = LinkOpsColors.Success
                )
                SummaryItem(
                    label = "Failed",
                    value = "${diagnostics.failedDomains}",
                    color = if (diagnostics.failedDomains > 0) LinkOpsColors.Error
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            if (diagnostics.localFingerprint != null) {
                HorizontalDivider()
                Text(
                    text = "Local Fingerprint",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = diagnostics.localFingerprint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Summary stat item
 */
@Composable
private fun SummaryItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Results panel showing per-domain diagnostics
 */
@Composable
private fun DiagnosticsResultsPanel(
    diagnostics: VerificationDiagnostics?,
    isLoading: Boolean,
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
                text = "Domain Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (diagnostics != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }

        if (diagnostics == null && !isLoading) {
            EmptyState(
                title = "No analysis results",
                description = "Select a device and package, then click Analyze",
                icon = Icons.Default.Search
            )
        } else if (diagnostics != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(diagnostics.domainResults) { result ->
                    DomainDiagnosticCard(result)
                }
            }
        }
    }
}

/**
 * Card displaying diagnostics for a single domain
 */
@Composable
private fun DomainDiagnosticCard(result: DomainDiagnosticResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Domain header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(
                    color = verificationStateColor(result.verificationState),
                    size = 12
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = result.domain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                VerificationStatusChip(result.verificationState)
            }

            HorizontalDivider()

            // AssetLinks status
            AssetLinksStatusRow(result.assetLinksStatus)

            // Fingerprint comparison
            FingerprintComparisonRow(result.fingerprintComparison)

            // Failure reasons and suggestions
            if (result.failureReasons.isNotEmpty()) {
                HorizontalDivider()
                FailureReasonsSection(result.failureReasons, result.suggestions)
            }
        }
    }
}

/**
 * Chip showing verification state
 */
@Composable
private fun VerificationStatusChip(state: VerificationState) {
    val (text, color) = when (state) {
        VerificationState.VERIFIED -> "Verified" to LinkOpsColors.Success
        VerificationState.APPROVED -> "Approved" to LinkOpsColors.Success
        VerificationState.DENIED -> "Denied" to LinkOpsColors.Error
        VerificationState.UNVERIFIED -> "Unverified" to LinkOpsColors.Warning
        VerificationState.LEGACY_FAILURE -> "Failed" to LinkOpsColors.Error
        VerificationState.UNKNOWN -> "Unknown" to LinkOpsColors.Unknown
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Row showing assetlinks.json status
 */
@Composable
private fun AssetLinksStatusRow(status: AssetLinksStatus) {
    val (statusText, color) = when (status) {
        AssetLinksStatus.VALID -> "Valid" to LinkOpsColors.Success
        AssetLinksStatus.NOT_FOUND -> "Not Found (404)" to LinkOpsColors.Error
        AssetLinksStatus.INVALID_JSON -> "Invalid JSON" to LinkOpsColors.Error
        AssetLinksStatus.INVALID_CONTENT_TYPE -> "Wrong Content-Type" to LinkOpsColors.Error
        AssetLinksStatus.NETWORK_ERROR -> "Network Error" to LinkOpsColors.Error
        AssetLinksStatus.REDIRECT -> "Redirect (not allowed)" to LinkOpsColors.Warning
        AssetLinksStatus.NOT_CHECKED -> "Not Checked" to LinkOpsColors.Unknown
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "assetlinks.json: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusDot(color = color)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Row showing fingerprint comparison result
 */
@Composable
private fun FingerprintComparisonRow(result: FingerprintComparisonResult) {
    val (statusText, color) = when (result) {
        is FingerprintComparisonResult.Match -> "Match" to LinkOpsColors.Success
        is FingerprintComparisonResult.Mismatch -> "Mismatch" to LinkOpsColors.Error
        is FingerprintComparisonResult.NoLocalFingerprint ->
            "No local fingerprint (Android 11)" to LinkOpsColors.Unknown
        is FingerprintComparisonResult.RemoteUnavailable ->
            "Remote unavailable" to LinkOpsColors.Warning
        is FingerprintComparisonResult.NoRemoteFingerprint ->
            "Package not in assetlinks.json" to LinkOpsColors.Error
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Fingerprint: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusDot(color = color)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }

        // Show mismatch details
        if (result is FingerprintComparisonResult.Mismatch) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        LinkOpsColors.Error.copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Local APK:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = result.localFingerprint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = LinkOpsColors.Error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Remote assetlinks.json (${result.remoteFingerprints.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.remoteFingerprints.forEach { fp ->
                    SelectionContainer {
                        Text(
                            text = fp,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section showing failure reasons and actionable suggestions
 */
@Composable
private fun FailureReasonsSection(
    reasons: List<FailureReason>,
    suggestions: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Root Cause Analysis",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = LinkOpsColors.Error
        )

        // Failure reasons
        reasons.forEach { reason ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        LinkOpsColors.Error.copy(alpha = 0.08f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "!", color = LinkOpsColors.Error, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFailureReason(reason),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Suggestions
        if (suggestions.isNotEmpty()) {
            Text(
                text = "Suggestions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LinkOpsColors.Info
            )

            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            LinkOpsColors.Info.copy(alpha = 0.08f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "->",
                        color = LinkOpsColors.Info,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Format failure reason enum to human-readable text
 */
private fun formatFailureReason(reason: FailureReason): String {
    return when (reason) {
        FailureReason.ASSET_LINKS_MISSING -> "assetlinks.json not found"
        FailureReason.ASSET_LINKS_INVALID_JSON -> "assetlinks.json has invalid JSON"
        FailureReason.ASSET_LINKS_INVALID_CONTENT_TYPE -> "assetlinks.json wrong Content-Type"
        FailureReason.ASSET_LINKS_NETWORK_ERROR -> "Cannot reach domain server"
        FailureReason.ASSET_LINKS_REDIRECT -> "assetlinks.json served via redirect"
        FailureReason.FINGERPRINT_MISMATCH -> "Certificate fingerprint mismatch"
        FailureReason.PACKAGE_NOT_IN_ASSET_LINKS -> "Package not declared in assetlinks.json"
        FailureReason.DNS_FAILURE -> "DNS resolution failed"
        FailureReason.UNKNOWN -> "Unknown failure reason"
    }
}

/**
 * Get color for verification state
 */
private fun verificationStateColor(state: VerificationState): androidx.compose.ui.graphics.Color {
    return when (state) {
        VerificationState.VERIFIED, VerificationState.APPROVED -> LinkOpsColors.Success
        VerificationState.DENIED, VerificationState.LEGACY_FAILURE -> LinkOpsColors.Error
        VerificationState.UNVERIFIED -> LinkOpsColors.Warning
        VerificationState.UNKNOWN -> LinkOpsColors.Unknown
    }
}
