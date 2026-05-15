package com.manjee.linkops.ui.screen.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjee.linkops.LocalSearchFocusTrigger
import com.manjee.linkops.domain.model.*
import com.manjee.linkops.ui.component.*
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Diagnostics Screen with tabbed layout for AssetLinks validation and Collision Detection
 */
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    devices: List<Device> = emptyList(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab row
        PrimaryTabRow(
            selectedTabIndex = uiState.activeTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DiagnosticsTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.activeTab == tab,
                    onClick = { viewModel.switchTab(tab) },
                    text = { Text(tab.title) }
                )
            }
        }

        // Tab content
        when (uiState.activeTab) {
            DiagnosticsTab.ASSET_LINKS -> AssetLinksContent(
                uiState = uiState,
                viewModel = viewModel
            )
            DiagnosticsTab.COLLISION_DETECTOR -> CollisionDetectorContent(
                uiState = uiState,
                devices = devices,
                viewModel = viewModel
            )
        }
    }

    // Loading overlay
    val isLoading = uiState.isLoading || uiState.isDetectingCollisions
    val loadingMessage = when {
        uiState.isLoading -> "Validating assetlinks.json..."
        uiState.isDetectingCollisions -> "Scanning apps for collisions..."
        else -> null
    }
    LoadingOverlay(
        isLoading = isLoading,
        message = loadingMessage
    )
}

// -- AssetLinks Tab --

/**
 * AssetLinks validation tab content
 */
@Composable
private fun AssetLinksContent(
    uiState: DiagnosticsUiState,
    viewModel: DiagnosticsViewModel
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Left Panel - Input and History
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Domain Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            PlatformToggle(
                selected = uiState.platform,
                onSelect = { viewModel.switchPlatform(it) }
            )

            HorizontalDivider()

            DomainInputSection(
                domain = uiState.domain,
                platform = uiState.platform,
                onDomainChange = { viewModel.updateDomain(it) },
                onValidate = { viewModel.validateDomain() },
                isLoading = uiState.isLoading,
                error = uiState.error
            )

            HorizontalDivider()

            HistorySection(
                history = uiState.history,
                onSelectItem = { viewModel.validateFromHistory(it) },
                onClear = { viewModel.clearHistory() }
            )
        }

        // Right Panel - Results
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            ResultsPanel(
                platform = uiState.platform,
                validation = uiState.validation,
                aasaValidation = uiState.aasaValidation,
                isLoading = uiState.isLoading,
                onClear = { viewModel.clearResult() }
            )
        }
    }
}

@Composable
private fun PlatformToggle(
    selected: DiagnosticsPlatform,
    onSelect: (DiagnosticsPlatform) -> Unit
) {
    val entries = DiagnosticsPlatform.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, platform ->
            SegmentedButton(
                selected = platform == selected,
                onClick = { onSelect(platform) },
                shape = SegmentedButtonDefaults.itemShape(index, entries.size)
            ) {
                Text(platform.title)
            }
        }
    }
}

// -- Collision Detector Tab --

/**
 * Collision detection tab content
 */
@Composable
private fun CollisionDetectorContent(
    uiState: DiagnosticsUiState,
    devices: List<Device>,
    viewModel: DiagnosticsViewModel
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Left Panel - Device selection and scan controls
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Scheme Collision Detector",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            DeviceSelectionSection(
                devices = devices,
                selectedDeviceSerial = uiState.selectedDeviceSerial,
                onSelectDevice = { viewModel.updateSelectedDevice(it) }
            )

            HorizontalDivider()

            Button(
                onClick = { viewModel.detectCollisions() },
                enabled = !uiState.isDetectingCollisions && uiState.selectedDeviceSerial != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan for Collisions")
            }

            if (uiState.collisionError != null) {
                Text(
                    text = uiState.collisionError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "Scans all third-party apps for conflicting URI scheme registrations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Summary card
            if (uiState.collisionReport != null) {
                CollisionSummaryCard(report = uiState.collisionReport)
            }
        }

        // Right Panel - Collision results
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            CollisionResultsPanel(
                report = uiState.collisionReport,
                isLoading = uiState.isDetectingCollisions,
                onClear = { viewModel.clearCollisionResult() }
            )
        }
    }
}

/**
 * Device selection section for collision detection
 */
@Composable
private fun DeviceSelectionSection(
    devices: List<Device>,
    selectedDeviceSerial: String?,
    onSelectDevice: (Device?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Target Device",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (devices.isEmpty()) {
            Text(
                text = "No devices connected. Connect a device via Dashboard first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val onlineDevices = devices.filter { it.isAvailable }
            if (onlineDevices.isEmpty()) {
                Text(
                    text = "No online devices available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                onlineDevices.forEach { device ->
                    val isSelected = device.serialNumber == selectedDeviceSerial
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(device) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusDot(color = LinkOpsColors.DeviceOnline)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = device.serialNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "Selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Summary card showing collision statistics
 */
@Composable
private fun CollisionSummaryCard(report: CollisionReport) {
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
                text = "Scan Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Packages scanned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${report.scannedPackages}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "URI registrations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${report.totalRegistrations}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Critical collisions",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkOpsColors.Error
                )
                Text(
                    text = "${report.criticalCount}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (report.criticalCount > 0) LinkOpsColors.Error
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Warnings",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkOpsColors.Warning
                )
                Text(
                    text = "${report.warningCount}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (report.warningCount > 0) LinkOpsColors.Warning
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Collision results panel
 */
@Composable
private fun CollisionResultsPanel(
    report: CollisionReport?,
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
                text = "Collision Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (report != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }

        if (report == null && !isLoading) {
            EmptyState(
                title = "No collision results",
                description = "Select a device and click Scan to detect collisions",
                icon = Icons.Default.Warning
            )
        } else if (report != null) {
            if (!report.hasCollisions) {
                EmptyState(
                    title = "No collisions detected",
                    description = "Scanned ${report.scannedPackages} packages with ${report.totalRegistrations} URI registrations",
                    icon = Icons.Default.Search
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(report.collisionGroups) { group ->
                        CollisionGroupCard(group)
                    }
                }
            }
        }
    }
}

/**
 * Card displaying a single collision group
 */
@Composable
private fun CollisionGroupCard(group: CollisionGroup) {
    val severityColor = when (group.severity) {
        CollisionSeverity.CRITICAL -> LinkOpsColors.Error
        CollisionSeverity.WARNING -> LinkOpsColors.Warning
    }

    val severityBgColor = when (group.severity) {
        CollisionSeverity.CRITICAL -> LinkOpsColors.ErrorLight
        CollisionSeverity.WARNING -> LinkOpsColors.WarningLight
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: severity badge + pattern
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(severityBgColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = group.severity.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = group.pattern,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${group.appCount} apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (group.hasVerifiedApp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusDot(color = LinkOpsColors.Verified, size = 6)
                    Text(
                        text = "Contains verified app(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkOpsColors.Verified
                    )
                }
            }

            HorizontalDivider()

            // List conflicting registrations grouped by package
            val byPackage = group.registrations.groupBy { it.packageName }
            byPackage.forEach { (packageName, registrations) ->
                CollisionRegistrationItem(packageName, registrations)
            }
        }
    }
}

/**
 * Display registrations for a single package within a collision group
 */
@Composable
private fun CollisionRegistrationItem(
    packageName: String,
    registrations: List<SchemeRegistration>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (registrations.any { it.autoVerify }) {
                Box(
                    modifier = Modifier
                        .background(LinkOpsColors.SuccessLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AutoVerify",
                        style = MaterialTheme.typography.labelSmall,
                        color = LinkOpsColors.Verified,
                        fontSize = 10.sp
                    )
                }
            }
        }

        registrations.forEach { reg ->
            Text(
                text = reg.uriPattern,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )

            Text(
                text = reg.activityName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// -- Existing AssetLinks composables (unchanged) --

/**
 * Domain input section
 */
@Composable
private fun DomainInputSection(
    domain: String,
    platform: DiagnosticsPlatform,
    onDomainChange: (String) -> Unit,
    onValidate: () -> Unit,
    isLoading: Boolean,
    error: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Domain Validation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        val focusRequester = remember { FocusRequester() }
        val searchFocusTrigger by LocalSearchFocusTrigger.current

        LaunchedEffect(searchFocusTrigger) {
            if (searchFocusTrigger > 0) {
                focusRequester.requestFocus()
            }
        }

        OutlinedTextField(
            value = domain,
            onValueChange = onDomainChange,
            label = { Text("Domain") },
            placeholder = { Text("example.com") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (domain.isNotEmpty()) {
                    IconButton(onClick = { onDomainChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error, color = MaterialTheme.colorScheme.error) }
            } else null
        )

        val (buttonLabel, helperText) = when (platform) {
            DiagnosticsPlatform.ANDROID ->
                "Validate assetlinks.json" to
                    "Fetches https://<domain>/.well-known/assetlinks.json"
            DiagnosticsPlatform.IOS ->
                "Validate apple-app-site-association" to
                    "Tries /.well-known/apple-app-site-association first, then root"
        }

        Button(
            onClick = onValidate,
            enabled = !isLoading && domain.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonLabel)
        }

        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * History section
 */
@Composable
private fun HistorySection(
    history: List<ValidationHistoryItem>,
    onSelectItem: (ValidationHistoryItem) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }

        if (history.isEmpty()) {
            Text(
                text = "No recent validations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(history) { item ->
                    HistoryItem(
                        item = item,
                        onClick = { onSelectItem(item) }
                    )
                }
            }
        }
    }
}

/**
 * History item
 */
@Composable
private fun HistoryItem(
    item: ValidationHistoryItem,
    onClick: () -> Unit
) {
    val statusColor = when (item.platform) {
        DiagnosticsPlatform.ANDROID -> when (item.androidStatus) {
            ValidationStatus.VALID -> LinkOpsColors.Success
            ValidationStatus.NOT_FOUND, ValidationStatus.INVALID_JSON -> LinkOpsColors.Error
            ValidationStatus.REDIRECT, ValidationStatus.INVALID_CONTENT_TYPE -> LinkOpsColors.Warning
            else -> LinkOpsColors.Unknown
        }
        DiagnosticsPlatform.IOS -> when (item.iosStatus) {
            AasaStatus.VALID -> LinkOpsColors.Success
            AasaStatus.NOT_FOUND, AasaStatus.INVALID_JSON, AasaStatus.REDIRECT -> LinkOpsColors.Error
            AasaStatus.INVALID_CONTENT_TYPE, AasaStatus.NO_APPLINKS_SECTION -> LinkOpsColors.Warning
            else -> LinkOpsColors.Unknown
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = statusColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.domain,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.platform.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Results panel — branches on platform but shares the empty/loading scaffold.
 */
@Composable
private fun ResultsPanel(
    platform: DiagnosticsPlatform,
    validation: AssetLinksValidation?,
    aasaValidation: AasaValidation?,
    isLoading: Boolean,
    onClear: () -> Unit
) {
    val hasResult = when (platform) {
        DiagnosticsPlatform.ANDROID -> validation != null
        DiagnosticsPlatform.IOS -> aasaValidation != null
    }

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
                text = "Validation Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (hasResult) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }

        if (!hasResult && !isLoading) {
            EmptyState(
                title = "No validation results",
                description = when (platform) {
                    DiagnosticsPlatform.ANDROID -> "Enter a domain and click Validate"
                    DiagnosticsPlatform.IOS -> "Enter a domain and validate the iOS AASA file"
                },
                icon = Icons.Default.Search
            )
        } else {
            when (platform) {
                DiagnosticsPlatform.ANDROID -> validation?.let { AndroidResults(it) }
                DiagnosticsPlatform.IOS -> aasaValidation?.let { IosResults(it) }
            }
        }
    }
}

@Composable
private fun AndroidResults(validation: AssetLinksValidation) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ValidationStatusCard(validation) }

        if (validation.issues.isNotEmpty()) {
            item { IssuesCard(validation.issues) }
        }

        if (validation.content != null) {
            item { ContentCard(validation.content) }
        }

        if (validation.rawJson != null) {
            item { RawJsonCard(validation.rawJson) }
        }
    }
}

/**
 * Validation status card
 */
@Composable
private fun ValidationStatusCard(validation: AssetLinksValidation) {
    val (statusText, statusColor, statusIcon) = when (validation.status) {
        ValidationStatus.VALID -> Triple("Valid", LinkOpsColors.Success, "✓")
        ValidationStatus.INVALID_JSON -> Triple("Invalid JSON", LinkOpsColors.Error, "✗")
        ValidationStatus.NOT_FOUND -> Triple("Not Found", LinkOpsColors.Error, "✗")
        ValidationStatus.REDIRECT -> Triple("Redirect", LinkOpsColors.Warning, "⚠")
        ValidationStatus.NETWORK_ERROR -> Triple("Network Error", LinkOpsColors.Error, "✗")
        ValidationStatus.FINGERPRINT_MISMATCH -> Triple("Fingerprint Mismatch", LinkOpsColors.Error, "✗")
        ValidationStatus.INVALID_CONTENT_TYPE -> Triple("Invalid Content-Type", LinkOpsColors.Warning, "⚠")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusIcon,
                    color = statusColor,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = validation.domain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = validation.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Issues card
 */
@Composable
private fun IssuesCard(issues: List<ValidationIssue>) {
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
                text = "Issues (${issues.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            issues.forEach { issue ->
                IssueItem(issue)
            }
        }
    }
}

/**
 * Issue item
 */
@Composable
private fun IssueItem(issue: ValidationIssue) {
    val (icon, color) = when (issue.severity) {
        ValidationIssue.Severity.ERROR -> "✗" to LinkOpsColors.Error
        ValidationIssue.Severity.WARNING -> "⚠" to LinkOpsColors.Warning
        ValidationIssue.Severity.INFO -> "ℹ" to LinkOpsColors.Info
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, color = color)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (issue.details != null) {
                Text(
                    text = issue.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Content card showing parsed statements
 */
@Composable
private fun ContentCard(content: AssetLinksContent) {
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
                text = "Statements (${content.statements.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            content.statements.forEachIndexed { index, statement ->
                StatementItem(index + 1, statement)
            }
        }
    }
}

/**
 * Statement item
 */
@Composable
private fun StatementItem(index: Int, statement: AssetStatement) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Statement #$index",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Package name
        Row {
            Text(
                text = "Package: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statement.target.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        // Relations
        Row {
            Text(
                text = "Relations: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statement.relation.joinToString(", "),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Fingerprints
        Text(
            text = "Fingerprints:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        statement.target.sha256CertFingerprints.forEach { fp ->
            SelectionContainer {
                Text(
                    text = fp,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Raw JSON card
 */
@Composable
private fun RawJsonCard(rawJson: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Raw JSON",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }

            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(
                            LinkOpsColors.TerminalBackground,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = rawJson,
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
    }
}

// -- iOS (AASA) results --

@Composable
private fun IosResults(validation: AasaValidation) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AasaStatusCard(validation) }

        if (validation.issues.isNotEmpty()) {
            item { AasaIssuesCard(validation.issues) }
        }

        validation.content?.let { content ->
            content.applinks?.let { applinks ->
                item { AasaApplinksCard(applinks) }
            }
        }

        if (validation.rawJson != null) {
            item { RawJsonCard(validation.rawJson) }
        }
    }
}

@Composable
private fun AasaStatusCard(validation: AasaValidation) {
    val (statusText, statusColor, statusIcon) = when (validation.status) {
        AasaStatus.VALID -> Triple("Valid", LinkOpsColors.Success, "✓")
        AasaStatus.INVALID_JSON -> Triple("Invalid JSON", LinkOpsColors.Error, "✗")
        AasaStatus.NOT_FOUND -> Triple("Not Found", LinkOpsColors.Error, "✗")
        AasaStatus.REDIRECT -> Triple("Redirect (not allowed)", LinkOpsColors.Error, "✗")
        AasaStatus.NETWORK_ERROR -> Triple("Network Error", LinkOpsColors.Error, "✗")
        AasaStatus.INVALID_CONTENT_TYPE -> Triple("Wrong Content-Type", LinkOpsColors.Warning, "⚠")
        AasaStatus.NO_APPLINKS_SECTION -> Triple("No applinks section", LinkOpsColors.Warning, "⚠")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusIcon,
                    color = statusColor,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = validation.domain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = validation.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (validation.content != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapabilityChip(
                        label = "applinks",
                        present = validation.content.applinks != null
                    )
                    CapabilityChip(
                        label = "webcredentials",
                        present = validation.content.hasWebcredentials
                    )
                    CapabilityChip(
                        label = "appclips",
                        present = validation.content.hasAppclips
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityChip(label: String, present: Boolean) {
    val bg = if (present) LinkOpsColors.SuccessLight else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (present) LinkOpsColors.Success else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AasaIssuesCard(issues: List<AasaIssue>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Issues (${issues.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            issues.forEach { issue -> AasaIssueItem(issue) }
        }
    }
}

@Composable
private fun AasaIssueItem(issue: AasaIssue) {
    val (icon, color) = when (issue.severity) {
        AasaIssue.Severity.ERROR -> "✗" to LinkOpsColors.Error
        AasaIssue.Severity.WARNING -> "⚠" to LinkOpsColors.Warning
        AasaIssue.Severity.INFO -> "ℹ" to LinkOpsColors.Info
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, color = color)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (issue.details != null) {
                Text(
                    text = issue.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AasaApplinksCard(applinks: AppLinksSection) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "applinks.details (${applinks.details.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            applinks.details.forEachIndexed { index, detail ->
                AasaDetailItem(index + 1, detail)
            }
        }
    }
}

@Composable
private fun AasaDetailItem(index: Int, detail: AppLinkDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Detail #$index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (detail.usesLegacySchema) {
                Box(
                    modifier = Modifier
                        .background(LinkOpsColors.WarningLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Legacy",
                        style = MaterialTheme.typography.labelSmall,
                        color = LinkOpsColors.Warning,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = if (detail.usesLegacySchema) "appID" else "appIDs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        detail.appIDs.forEach { id ->
            SelectionContainer {
                Text(
                    text = id,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (detail.paths.isNotEmpty()) {
            Text(
                text = "paths (legacy):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            detail.paths.forEach { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (detail.components.isNotEmpty()) {
            Text(
                text = "components:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            detail.components.forEach { component ->
                AasaComponentRow(component)
            }
        }

        if (!detail.hasAnyPattern) {
            Text(
                text = "No paths or components — iOS will not match any URL.",
                style = MaterialTheme.typography.bodySmall,
                color = LinkOpsColors.Warning,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun AasaComponentRow(component: AasaPathComponent) {
    val parts = buildList {
        component.path?.let { add("/ = $it") }
        component.query?.let { add("? = $it") }
        component.fragment?.let { add("# = $it") }
        if (component.exclude) add("exclude")
        if (!component.caseSensitive) add("caseSensitive=false")
        if (!component.percentEncoded) add("percentEncoded=false")
    }
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        ),
        color = if (component.exclude) LinkOpsColors.Warning else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 8.dp)
    )
}
