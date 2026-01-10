package com.manjee.linkops.ui.screen.manifest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjee.linkops.domain.model.*
import com.manjee.linkops.domain.repository.PackageFilter
import com.manjee.linkops.ui.component.*
import com.manjee.linkops.ui.theme.LinkOpsColors
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manifest Analyzer Screen
 *
 * Allows users to select an installed app and analyze its deep link configuration
 */
@Composable
fun ManifestAnalyzerScreen(
    viewModel: ManifestAnalyzerViewModel,
    devices: List<Device>,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Auto-select first device if not selected
    LaunchedEffect(devices) {
        if (uiState.selectedDevice == null && devices.isNotEmpty()) {
            val onlineDevice = devices.firstOrNull { it.state == Device.DeviceState.ONLINE }
            onlineDevice?.let { viewModel.setDevice(it) }
        }
    }

    // Show test result snackbar
    LaunchedEffect(uiState.testResult) {
        uiState.testResult?.let { result ->
            snackbarHostState.showSnackbar(
                message = if (result.success) "✓ Deep link fired: ${result.uri}" else "✗ Failed: ${result.message}",
                duration = SnackbarDuration.Short
            )
            viewModel.clearTestResult()
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
            // Left Panel - Package Selection
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Manifest Analyzer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider()

                // Device selector
                DeviceSelector(
                    devices = devices,
                    selectedDevice = uiState.selectedDevice,
                    onDeviceSelected = { viewModel.setDevice(it) }
                )

                HorizontalDivider()

                // Package search and filter
                PackageSearchSection(
                    query = uiState.packageQuery,
                    onQueryChange = { viewModel.updatePackageQuery(it) },
                    filter = uiState.packageFilter,
                    onFilterChange = { viewModel.setPackageFilter(it) },
                    onRefresh = { viewModel.loadPackages() },
                    isLoading = uiState.isLoadingPackages,
                    enabled = uiState.selectedDevice != null
                )

                // Package list
                PackageList(
                    packages = uiState.packages,
                    selectedPackage = uiState.selectedPackage,
                    onPackageSelected = { viewModel.selectPackage(it) },
                    isLoading = uiState.isLoadingPackages,
                    modifier = Modifier.weight(1f)
                )
            }

            // Right Panel - Analysis Results
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                AnalysisResultsPanel(
                    result = uiState.analysisResult,
                    isAnalyzing = uiState.isAnalyzing,
                    onClear = { viewModel.clearAnalysis() },
                    onTestDeepLink = { uri -> viewModel.testDeepLink(uri) },
                    onCopyToClipboard = { uri ->
                        copyToClipboard(uri)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Copied!",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
            }
        }
    }

    // Loading overlay
    LoadingOverlay(
        isLoading = uiState.isAnalyzing,
        message = "Analyzing manifest..."
    )
}

/**
 * Copy text to system clipboard
 */
private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
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
            style = MaterialTheme.typography.titleMedium,
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
                    .menuAnchor()
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
 * Package search section
 */
@Composable
private fun PackageSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: PackageFilter,
    onFilterChange: (PackageFilter) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Packages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = onRefresh,
                enabled = enabled && !isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search packages") },
            placeholder = { Text("com.example...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        )

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == PackageFilter.THIRD_PARTY,
                onClick = { onFilterChange(PackageFilter.THIRD_PARTY) },
                label = { Text("Third-party") },
                enabled = enabled
            )
            FilterChip(
                selected = filter == PackageFilter.SYSTEM,
                onClick = { onFilterChange(PackageFilter.SYSTEM) },
                label = { Text("System") },
                enabled = enabled
            )
            FilterChip(
                selected = filter == PackageFilter.ALL,
                onClick = { onFilterChange(PackageFilter.ALL) },
                label = { Text("All") },
                enabled = enabled
            )
        }
    }
}

/**
 * Package list
 */
@Composable
private fun PackageList(
    packages: List<String>,
    selectedPackage: String?,
    onPackageSelected: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(message = "Loading packages...")
        }
    } else if (packages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                title = "No packages found",
                description = "Connect a device or try a different filter",
                icon = "📦"
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(packages) { packageName ->
                PackageItem(
                    packageName = packageName,
                    isSelected = packageName == selectedPackage,
                    onClick = { onPackageSelected(packageName) }
                )
            }
        }
    }
}

/**
 * Package item in list
 */
@Composable
private fun PackageItem(
    packageName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Text(
            text = packageName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Analysis results panel
 */
@Composable
private fun AnalysisResultsPanel(
    result: ManifestAnalysisResult?,
    isAnalyzing: Boolean,
    onClear: () -> Unit,
    onTestDeepLink: (String) -> Unit,
    onCopyToClipboard: (String) -> Unit
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
                text = "Analysis Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (result != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }

        if (result == null && !isAnalyzing) {
            EmptyState(
                title = "No analysis yet",
                description = "Select a package to analyze its deep links",
                icon = "📋"
            )
        } else if (result != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error state
                if (!result.isSuccess) {
                    item {
                        ErrorCard(result.error ?: "Unknown error")
                    }
                }

                // Package info
                result.manifestInfo?.let { info ->
                    item {
                        PackageInfoCard(info)
                    }

                    // Domain verification status
                    result.domainVerification?.let { verification ->
                        if (verification.domains.isNotEmpty()) {
                            item {
                                DomainVerificationCard(verification)
                            }
                        }
                    }

                    // Deep links summary
                    item {
                        DeepLinksSummaryCard(info)
                    }

                    // App Links (verified)
                    if (info.appLinks.isNotEmpty()) {
                        item {
                            DeepLinksCard(
                                title = "App Links (Auto-Verified)",
                                deepLinks = info.appLinks,
                                isAppLink = true,
                                domainVerification = result.domainVerification,
                                onTestDeepLink = onTestDeepLink,
                                onCopyToClipboard = onCopyToClipboard
                            )
                        }
                    }

                    // Custom scheme links
                    if (info.customSchemeLinks.isNotEmpty()) {
                        item {
                            DeepLinksCard(
                                title = "Custom Scheme Links",
                                deepLinks = info.customSchemeLinks,
                                isAppLink = false,
                                domainVerification = null,
                                onTestDeepLink = onTestDeepLink,
                                onCopyToClipboard = onCopyToClipboard
                            )
                        }
                    }

                    // All deep links (if different from above)
                    val httpLinks = info.deepLinks.filter {
                        (it.scheme == "http" || it.scheme == "https") && !it.autoVerify
                    }
                    if (httpLinks.isNotEmpty()) {
                        item {
                            DeepLinksCard(
                                title = "HTTP/HTTPS Links (Not Auto-Verified)",
                                deepLinks = httpLinks,
                                isAppLink = false,
                                domainVerification = result.domainVerification,
                                onTestDeepLink = onTestDeepLink,
                                onCopyToClipboard = onCopyToClipboard
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Error card
 */
@Composable
private fun ErrorCard(error: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = LinkOpsColors.ErrorLight
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✗", color = LinkOpsColors.Error)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = LinkOpsColors.Error
            )
        }
    }
}

/**
 * Package info card
 */
@Composable
private fun PackageInfoCard(info: ManifestInfo) {
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
                text = info.packageName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                info.versionName?.let {
                    Text(
                        text = "Version: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                info.versionCode?.let {
                    Text(
                        text = "Code: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Deep links summary card
 */
@Composable
private fun DeepLinksSummaryCard(info: ManifestInfo) {
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
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "Total Deep Links",
                    value = info.deepLinks.size.toString()
                )
                SummaryItem(
                    label = "App Links",
                    value = info.appLinks.size.toString(),
                    highlight = info.supportsAppLinks
                )
                SummaryItem(
                    label = "Custom Schemes",
                    value = info.customSchemeLinks.size.toString()
                )
            }

            if (info.schemes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Schemes:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    info.schemes.forEach { scheme ->
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = scheme,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) LinkOpsColors.Success else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Domain verification card
 */
@Composable
private fun DomainVerificationCard(verification: DomainVerificationResult) {
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
                text = "Domain Verification Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            verification.domains.forEach { domain ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = domain.domain,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )

                    val (statusIcon, statusColor) = when (domain.status) {
                        DomainVerificationStatus.VERIFIED -> "✓" to LinkOpsColors.Success
                        DomainVerificationStatus.NONE -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
                        DomainVerificationStatus.LEGACY_FAILURE -> "✗" to LinkOpsColors.Error
                        DomainVerificationStatus.ALWAYS -> "✓" to LinkOpsColors.Success
                        DomainVerificationStatus.NEVER -> "✗" to LinkOpsColors.Error
                        DomainVerificationStatus.UNKNOWN -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusIcon,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = domain.status.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deep links card
 */
@Composable
private fun DeepLinksCard(
    title: String,
    deepLinks: List<DeepLinkInfo>,
    isAppLink: Boolean,
    domainVerification: DomainVerificationResult?,
    onTestDeepLink: (String) -> Unit,
    onCopyToClipboard: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAppLink) {
                    Text(
                        text = "✓",
                        color = LinkOpsColors.Success,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "$title (${deepLinks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            deepLinks.forEach { link ->
                DeepLinkItem(
                    link = link,
                    domainVerification = domainVerification,
                    onTestDeepLink = onTestDeepLink,
                    onCopyToClipboard = onCopyToClipboard
                )
            }
        }
    }
}

/**
 * Deep link item with copy and test buttons
 */
@Composable
private fun DeepLinkItem(
    link: DeepLinkInfo,
    domainVerification: DomainVerificationResult?,
    onTestDeepLink: (String) -> Unit,
    onCopyToClipboard: (String) -> Unit
) {
    // Find verification status for this link's domain
    val verificationStatus = link.host?.let { host ->
        domainVerification?.domains?.find { domain ->
            host.endsWith(domain.domain.removePrefix("*").removePrefix(".")) ||
            domain.domain.removePrefix("*.") == host.removePrefix(".")
        }?.status
    }

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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = link.patternDescription,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            // Verification status badge
            verificationStatus?.let { status ->
                val (color, bgColor) = when (status) {
                    DomainVerificationStatus.VERIFIED -> LinkOpsColors.Success to LinkOpsColors.SuccessLight
                    DomainVerificationStatus.ALWAYS -> LinkOpsColors.Success to LinkOpsColors.SuccessLight
                    DomainVerificationStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
                    else -> LinkOpsColors.Error to LinkOpsColors.ErrorLight
                }
                Box(
                    modifier = Modifier
                        .background(bgColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }

        Text(
            text = "Activity: ${link.activityName.substringAfterLast("/")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Sample URI with Copy and Test buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = link.sampleUri,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Copy button
                FilledTonalButton(
                    onClick = { onCopyToClipboard(link.sampleUri) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("📋", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }

                // Test button
                FilledTonalButton(
                    onClick = { onTestDeepLink(link.sampleUri) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Test",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
