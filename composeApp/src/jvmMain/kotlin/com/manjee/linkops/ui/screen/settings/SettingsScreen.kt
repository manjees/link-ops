package com.manjee.linkops.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manjee.linkops.domain.model.AppSettings
import com.manjee.linkops.domain.model.ThemePreference
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Application settings screen.
 *
 * Four sections that mirror the categories users actually want to tune:
 * appearance, ADB, local hosting defaults, and device polling. Theme is applied
 * instantly because the visual feedback is the confirmation; everything else
 * waits for an explicit Save so a half-typed port doesn't immediately break things.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Header(state = state, onSave = viewModel::save, onDiscard = viewModel::discardChanges)

            AppearanceSection(
                current = state.draft.theme,
                onChange = viewModel::setTheme
            )

            AdbSection(
                value = state.draft.adbPathOverride.orEmpty(),
                onChange = viewModel::updateAdbPathOverride
            )

            LocalHostingSection(
                host = state.draft.defaultHost,
                portInput = state.portInput,
                portValid = state.isPortInputValid,
                onHostChange = viewModel::updateDefaultHost,
                onPortChange = viewModel::updatePortInput
            )

            DeviceDetectionSection(
                pollingInput = state.pollingInput,
                pollingValid = state.isPollingInputValid,
                onPollingChange = viewModel::updatePollingInput
            )

            state.error?.let { ErrorBanner(message = it, onDismiss = viewModel::clearError) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(
    state: SettingsUiState,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    state.isLoading -> "Loading…"
                    state.isDirty -> "Unsaved changes"
                    state.savedAt != null -> "Saved"
                    else -> "All preferences are persisted to ~/.linkops/settings.json"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.isDirty) {
            TextButton(onClick = onDiscard) { Text("Discard") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSave, enabled = state.canSave) { Text("Save") }
        }
    }
}

@Composable
private fun AppearanceSection(
    current: ThemePreference,
    onChange: (ThemePreference) -> Unit
) {
    SettingsCard(
        title = "Appearance",
        icon = Icons.Default.Palette,
        helper = "Theme is applied immediately."
    ) {
        val options = listOf(
            ThemeOption(ThemePreference.LIGHT, "Light", Icons.Default.Brightness6),
            ThemeOption(ThemePreference.DARK, "Dark", Icons.Default.Brightness4),
            ThemeOption(ThemePreference.SYSTEM, "System", Icons.Default.BrightnessAuto)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.value == current,
                    onClick = { onChange(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = { Icon(option.icon, contentDescription = null) }
                ) {
                    Text(option.label)
                }
            }
        }
    }
}

private data class ThemeOption(
    val value: ThemePreference,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun AdbSection(
    value: String,
    onChange: (String) -> Unit
) {
    SettingsCard(
        title = "ADB",
        icon = Icons.Default.Terminal,
        helper = "Leave empty to use the system PATH or the bundled binary."
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("Override path") },
            placeholder = { Text("/usr/local/bin/adb") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LocalHostingSection(
    host: String,
    portInput: String,
    portValid: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit
) {
    SettingsCard(
        title = "Local Hosting defaults",
        icon = Icons.Default.Dns,
        helper = "Used as the initial values when you open the Local Hosting screen."
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Default host") },
            placeholder = { Text(AppSettings.DEFAULT_HOST) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = portInput,
            onValueChange = onPortChange,
            label = { Text("Default port") },
            placeholder = { Text(AppSettings.DEFAULT_PORT.toString()) },
            singleLine = true,
            isError = !portValid,
            supportingText = {
                if (!portValid) {
                    Text("Enter a number between ${AppSettings.MIN_PORT} and ${AppSettings.MAX_PORT}.")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeviceDetectionSection(
    pollingInput: String,
    pollingValid: Boolean,
    onPollingChange: (String) -> Unit
) {
    SettingsCard(
        title = "Device detection",
        icon = Icons.Default.Devices,
        helper = "How often LinkOps polls ADB for connected devices. Lower is more responsive but uses more CPU."
    ) {
        OutlinedTextField(
            value = pollingInput,
            onValueChange = onPollingChange,
            label = { Text("Polling interval (seconds)") },
            singleLine = true,
            isError = !pollingValid,
            supportingText = {
                if (!pollingValid) {
                    Text(
                        "Enter a number between ${AppSettings.MIN_POLLING_INTERVAL_SECONDS} " +
                            "and ${AppSettings.MAX_POLLING_INTERVAL_SECONDS}."
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    helper: String,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LinkOpsColors.ErrorLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = message, modifier = Modifier.weight(1f), color = LinkOpsColors.Error)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
