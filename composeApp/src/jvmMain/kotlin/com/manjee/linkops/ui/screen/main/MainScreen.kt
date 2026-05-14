package com.manjee.linkops.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.manjee.linkops.LocalSearchFocusTrigger
import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.Favorite
import com.manjee.linkops.domain.model.IntentConfig
import com.manjee.linkops.ui.component.*
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Main Screen - Dashboard view for LinkOps
 *
 * Layout:
 * - Left panel: Controls (ADB status, devices, app links, intent firing)
 * - Right panel: Log output
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val logText by viewModel.logText.collectAsState()

    var showIntentDialog by remember { mutableStateOf(false) }
    var intentUri by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrDialogUri by remember { mutableStateOf("") }

    val isLoading = uiState.isLoadingDevices || uiState.isLoadingAppLinks || uiState.isFiringIntent

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Left Panel - Controls
            ControlPanel(
                uiState = uiState,
                intentUri = intentUri,
                onIntentUriChange = { intentUri = it },
                onCheckAdb = { viewModel.checkAdbStatus() },
                onRefreshDevices = { viewModel.refreshDevices() },
                onDeviceSelected = { viewModel.selectDevice(it) },
                onLoadAppLinks = { viewModel.loadAppLinks() },
                onReverify = { viewModel.forceReverify(it) },
                onFireIntent = {
                    if (intentUri.isNotBlank()) {
                        val config = IntentConfig(uri = intentUri)
                        viewModel.fireIntent(config)
                    }
                },
                onOpenIntentDialog = { showIntentDialog = true },
                onFireFavorite = { uri ->
                    intentUri = uri
                    viewModel.fireFavorite(uri)
                },
                onRemoveFavorite = { id -> viewModel.removeFavorite(id) },
                onShowQr = { uri ->
                    qrDialogUri = uri
                    showQrDialog = true
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            // Right Panel - Log Output
            LogPanel(
                logText = logText,
                title = "Log Output",
                onClear = { viewModel.clearLog() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Loading overlay
        LoadingOverlay(
            isLoading = isLoading,
            message = when {
                uiState.isLoadingDevices -> "Detecting devices..."
                uiState.isLoadingAppLinks -> "Loading app links..."
                uiState.isFiringIntent -> "Firing intent..."
                else -> null
            }
        )

        // Intent fire dialog
        if (showIntentDialog) {
            IntentFireDialog(
                onDismiss = { showIntentDialog = false },
                onFire = { config ->
                    viewModel.fireIntent(config)
                    showIntentDialog = false
                },
                initialUri = intentUri
            )
        }

        // QR code dialog
        if (showQrDialog && qrDialogUri.isNotBlank()) {
            QrCodeDialog(
                uri = qrDialogUri,
                qrCodeGenerator = AppContainer.qrCodeGenerator,
                onDismiss = { showQrDialog = false }
            )
        }
    }
}

/**
 * Left panel with all controls
 */
@Composable
private fun ControlPanel(
    uiState: MainUiState,
    intentUri: String,
    onIntentUriChange: (String) -> Unit,
    onCheckAdb: () -> Unit,
    onRefreshDevices: () -> Unit,
    onDeviceSelected: (com.manjee.linkops.domain.model.Device) -> Unit,
    onLoadAppLinks: () -> Unit,
    onReverify: (String) -> Unit,
    onFireIntent: () -> Unit,
    onOpenIntentDialog: () -> Unit,
    onFireFavorite: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onShowQr: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "LinkOps",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        // Scrollable content
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: ADB Status
            item {
                AdbStatusSection(
                    status = uiState.adbStatus,
                    onCheck = onCheckAdb
                )
            }

            item { HorizontalDivider() }

            // Section 2: Devices
            item {
                DevicesSection(
                    devices = uiState.devices,
                    selectedDevice = uiState.selectedDevice,
                    isLoading = uiState.isLoadingDevices,
                    onRefresh = onRefreshDevices,
                    onDeviceSelected = onDeviceSelected
                )
            }

            item { HorizontalDivider() }

            // Section 3: Fire Intent
            item {
                FireIntentSection(
                    intentUri = intentUri,
                    onIntentUriChange = onIntentUriChange,
                    selectedDevice = uiState.selectedDevice,
                    isFiring = uiState.isFiringIntent,
                    onFireIntent = onFireIntent,
                    onOpenAdvanced = onOpenIntentDialog,
                    onShowQr = onShowQr
                )
            }

            // Section: Favorites (shown only if there are favorites)
            if (uiState.favorites.isNotEmpty()) {
                item { HorizontalDivider() }

                item {
                    FavoritesSection(
                        favorites = uiState.favorites,
                        selectedDevice = uiState.selectedDevice,
                        onFireFavorite = onFireFavorite,
                        onRemoveFavorite = onRemoveFavorite,
                        onShowQr = onShowQr
                    )
                }
            }

            item { HorizontalDivider() }

            // Section 4: App Links
            item {
                AppLinksSection(
                    appLinks = uiState.appLinks,
                    selectedDevice = uiState.selectedDevice,
                    isLoading = uiState.isLoadingAppLinks,
                    onLoadAppLinks = onLoadAppLinks,
                    onReverify = onReverify
                )
            }
        }
    }
}

/**
 * ADB Status Section
 */
@Composable
private fun AdbStatusSection(
    status: AdbStatus,
    onCheck: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "1. ADB Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = onCheck,
                enabled = status !is AdbStatus.Checking
            ) {
                Text("Check ADB")
            }
        }

        // Status indicator
        when (status) {
            is AdbStatus.Unknown -> {
                Text(
                    text = "Status unknown - click Check ADB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AdbStatus.Checking -> {
                LoadingIndicator(message = "Checking ADB...")
            }
            is AdbStatus.Available -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = LinkOpsColors.Success)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADB Available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LinkOpsColors.Success
                    )
                }
            }
            is AdbStatus.Unavailable -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = LinkOpsColors.Error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADB Unavailable: ${status.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LinkOpsColors.Error
                    )
                }
            }
        }
    }
}

/**
 * Devices Section (Collapsible)
 */
@Composable
private fun DevicesSection(
    devices: List<com.manjee.linkops.domain.model.Device>,
    selectedDevice: com.manjee.linkops.domain.model.Device?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDeviceSelected: (com.manjee.linkops.domain.model.Device) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
                Text(
                    text = "2. Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (devices.isNotEmpty()) {
                    Text(
                        text = "(${devices.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onRefresh,
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (devices.isEmpty()) {
                    EmptyState(
                        title = "No devices found",
                        description = "Connect an Android device or start an emulator",
                        icon = Icons.Default.PhoneAndroid,
                        actionLabel = "Refresh Devices",
                        onAction = onRefresh
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        devices.forEach { device ->
                            DeviceCard(
                                device = device,
                                isSelected = selectedDevice?.serialNumber == device.serialNumber,
                                onClick = { onDeviceSelected(device) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * App Links Section (Collapsible)
 */
@Composable
private fun AppLinksSection(
    appLinks: List<com.manjee.linkops.domain.model.AppLink>,
    selectedDevice: com.manjee.linkops.domain.model.Device?,
    isLoading: Boolean,
    onLoadAppLinks: () -> Unit,
    onReverify: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
                Text(
                    text = "4. App Links",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (appLinks.isNotEmpty()) {
                    Text(
                        text = "(${appLinks.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onLoadAppLinks,
                enabled = !isLoading && selectedDevice != null
            ) {
                Text("Get App Links")
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedDevice == null) {
                    Text(
                        text = "Select a device first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (appLinks.isEmpty()) {
                    if (!isLoading) {
                        EmptyState(
                            title = "No app links found",
                            description = "Click 'Get App Links' to fetch",
                            icon = Icons.Default.Link,
                            actionLabel = "Get App Links",
                            onAction = onLoadAppLinks
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        appLinks.forEach { appLink ->
                            AppLinkCard(
                                appLink = appLink,
                                onReverify = { onReverify(appLink.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fire Intent Section
 */
@Composable
private fun FireIntentSection(
    intentUri: String,
    onIntentUriChange: (String) -> Unit,
    selectedDevice: com.manjee.linkops.domain.model.Device?,
    isFiring: Boolean,
    onFireIntent: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onShowQr: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "3. Fire Intent",
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
            value = intentUri,
            onValueChange = onIntentUriChange,
            label = { Text("URI") },
            placeholder = { Text("myapp://product/123 or https://example.com/path") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            enabled = selectedDevice != null
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onFireIntent,
                enabled = !isFiring && selectedDevice != null && intentUri.isNotBlank()
            ) {
                Text("Fire Intent")
            }

            OutlinedButton(
                onClick = onOpenAdvanced,
                enabled = selectedDevice != null
            ) {
                Text("Advanced...")
            }

            IconButton(
                onClick = { onShowQr(intentUri) },
                enabled = intentUri.isNotBlank()
            ) {
                Icon(
                    Icons.Default.QrCode2,
                    contentDescription = "Generate QR Code"
                )
            }
        }
    }
}

/**
 * Favorites Section (Collapsible)
 */
@Composable
private fun FavoritesSection(
    favorites: List<Favorite>,
    selectedDevice: com.manjee.linkops.domain.model.Device?,
    onFireFavorite: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onShowQr: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = LinkOpsColors.Error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "(${favorites.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                favorites.forEach { favorite ->
                    FavoriteItem(
                        favorite = favorite,
                        hasDevice = selectedDevice != null,
                        onFire = { onFireFavorite(favorite.uri) },
                        onRemove = { onRemoveFavorite(favorite.id) },
                        onShowQr = { onShowQr(favorite.uri) }
                    )
                }
            }
        }
    }
}

/**
 * Individual favorite item card
 */
@Composable
private fun FavoriteItem(
    favorite: Favorite,
    hasDevice: Boolean,
    onFire: () -> Unit,
    onRemove: () -> Unit,
    onShowQr: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = favorite.uri,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onFire,
                    enabled = hasDevice,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Fire intent",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onShowQr,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = "Generate QR Code",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove favorite",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
