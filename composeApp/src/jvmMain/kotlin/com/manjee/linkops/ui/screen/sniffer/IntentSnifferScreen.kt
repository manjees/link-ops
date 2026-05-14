package com.manjee.linkops.ui.screen.sniffer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.ui.component.EmptyState
import com.manjee.linkops.ui.component.JsonTreeView
import com.manjee.linkops.ui.component.PayloadComparisonView
import com.manjee.linkops.ui.theme.LinkOpsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Intent Sniffer Screen - Capture and analyze intent payloads
 *
 * Layout:
 * - Left panel (0.35): Device selection, capture controls, history list
 * - Right panel (0.65): Details/Compare/Raw tabs
 */
@Composable
fun IntentSnifferScreen(
    viewModel: IntentSnifferViewModel,
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
        // Left Panel - Controls & History
        ControlPanel(
            uiState = uiState,
            devices = devices,
            selectedDevice = selectedDevice,
            onDeviceSelected = { selectedDevice = it },
            onCapture = { device -> viewModel.capturePayload(device) },
            onToggleAutoCapture = { viewModel.toggleAutoCapture() },
            onSelectPayload = { viewModel.selectPayload(it) },
            onSelectCompareBase = { viewModel.selectCompareBase(it) },
            onClearHistory = { viewModel.clearHistory() },
            modifier = Modifier.weight(0.35f)
        )

        // Divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(LinkOpsColors.Divider)
        )

        // Right Panel - Content
        ContentPanel(
            uiState = uiState,
            onTabSelected = { viewModel.selectTab(it) },
            modifier = Modifier.weight(0.65f)
        )
    }

    // Error snackbar
    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}

@Composable
private fun ControlPanel(
    uiState: IntentSnifferUiState,
    devices: List<Device>,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit,
    onCapture: (Device) -> Unit,
    onToggleAutoCapture: () -> Unit,
    onSelectPayload: (IntentPayload) -> Unit,
    onSelectCompareBase: (IntentPayload) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Intent Sniffer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Device Selection
        DeviceSelector(
            devices = devices,
            selectedDevice = selectedDevice,
            onDeviceSelected = onDeviceSelected
        )

        // Capture Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { selectedDevice?.let { onCapture(it) } },
                enabled = selectedDevice != null && !uiState.isCapturing,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capturing...")
                } else {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture")
                }
            }
        }

        // Auto-capture toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Auto-capture on intent fire",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = uiState.autoCapture,
                onCheckedChange = { onToggleAutoCapture() }
            )
        }

        HorizontalDivider()

        // History Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "History (${uiState.payloadHistory.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (uiState.payloadHistory.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // History List
        if (uiState.payloadHistory.isEmpty()) {
            EmptyState(
                title = "No captures yet",
                description = "Select a device and capture an intent payload",
                iconEmoji = null,
                icon = Icons.Default.CameraAlt,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.payloadHistory) { payload ->
                    PayloadHistoryItem(
                        payload = payload,
                        isSelected = payload == uiState.selectedPayload,
                        isCompareBase = payload == uiState.compareBasePayload,
                        onClick = { onSelectPayload(payload) },
                        onSetAsBase = { onSelectCompareBase(payload) }
                    )
                }
            }
        }

        // Error display
        if (uiState.error != null) {
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelector(
    devices: List<Device>,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedDevice?.displayName ?: "Select device",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true,
            label = { Text("Device") }
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

            if (devices.none { it.isAvailable }) {
                DropdownMenuItem(
                    text = { Text("No devices available") },
                    onClick = { expanded = false },
                    enabled = false
                )
            }
        }
    }
}

@Composable
private fun PayloadHistoryItem(
    payload: IntentPayload,
    isSelected: Boolean,
    isCompareBase: Boolean,
    onClick: () -> Unit,
    onSetAsBase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isCompareBase -> LinkOpsColors.InfoLight
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payload.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = timeFormat.format(Date(payload.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Compare base marker
        if (isCompareBase) {
            Box(
                modifier = Modifier
                    .background(LinkOpsColors.Info, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "BASE",
                    style = MaterialTheme.typography.labelSmall,
                    color = LinkOpsColors.OnPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            TextButton(
                onClick = onSetAsBase,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Set Base",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ContentPanel(
    uiState: IntentSnifferUiState,
    onTabSelected: (SnifferTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Row
        PrimaryTabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SnifferTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        // Tab Content
        val selectedPayload = uiState.selectedPayload
        if (selectedPayload == null) {
            EmptyState(
                title = "No payload selected",
                description = "Capture an intent or select one from history",
                icon = Icons.Default.CameraAlt,
                iconEmoji = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            when (uiState.selectedTab) {
                SnifferTab.DETAILS -> {
                    JsonTreeView(
                        intentPayload = selectedPayload,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SnifferTab.COMPARE -> {
                    val comparison = uiState.comparison
                    if (comparison != null) {
                        PayloadComparisonView(
                            comparison = comparison,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        EmptyState(
                            title = "No comparison available",
                            description = "Select a base payload from history to compare",
                            iconEmoji = null,
                            icon = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                SnifferTab.RAW -> {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(LinkOpsColors.TerminalBackground)
                                .padding(16.dp)
                        ) {
                            item {
                                Text(
                                    text = selectedPayload.rawOutput,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = LinkOpsColors.TerminalText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
