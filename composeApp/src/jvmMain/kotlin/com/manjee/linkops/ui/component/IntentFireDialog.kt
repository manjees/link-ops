package com.manjee.linkops.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.IntentConfig

/**
 * Dialog for configuring and firing an intent
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onFire Callback when intent is fired with configuration
 * @param initialUri Optional initial URI to populate
 */
@Composable
fun IntentFireDialog(
    onDismiss: () -> Unit,
    onFire: (IntentConfig) -> Unit,
    initialUri: String = ""
) {
    var uri by remember { mutableStateOf(initialUri) }
    var action by remember { mutableStateOf(IntentConfig.ACTION_VIEW) }
    var packageName by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }

    // Intent flags
    var newTask by remember { mutableStateOf(false) }
    var clearTop by remember { mutableStateOf(false) }
    var singleTop by remember { mutableStateOf(false) }
    var clearTask by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Text(
                    text = "Fire Intent",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // URI Input
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("URI") },
                    placeholder = { Text("myapp://product/123 or https://example.com/path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action selector
                Text(
                    text = "Action",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChip(
                        label = "VIEW",
                        selected = action == IntentConfig.ACTION_VIEW,
                        onClick = { action = IntentConfig.ACTION_VIEW }
                    )
                    ActionChip(
                        label = "BROWSABLE",
                        selected = action == "android.intent.action.BROWSABLE",
                        onClick = { action = "android.intent.action.BROWSABLE" }
                    )
                    ActionChip(
                        label = "MAIN",
                        selected = action == "android.intent.action.MAIN",
                        onClick = { action = "android.intent.action.MAIN" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Package name (optional)
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name (optional)") },
                    placeholder = { Text("com.example.app") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Flags
                Text(
                    text = "Intent Flags",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    FlagCheckbox(
                        label = "FLAG_ACTIVITY_NEW_TASK",
                        checked = newTask,
                        onCheckedChange = { newTask = it }
                    )
                    FlagCheckbox(
                        label = "FLAG_ACTIVITY_CLEAR_TOP",
                        checked = clearTop,
                        onCheckedChange = { clearTop = it }
                    )
                    FlagCheckbox(
                        label = "FLAG_ACTIVITY_SINGLE_TOP",
                        checked = singleTop,
                        onCheckedChange = { singleTop = it }
                    )
                    FlagCheckbox(
                        label = "FLAG_ACTIVITY_CLEAR_TASK",
                        checked = clearTask,
                        onCheckedChange = { clearTask = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Preview
                if (uri.isNotBlank()) {
                    val flags = buildSet {
                        if (newTask) add(IntentConfig.IntentFlag.ACTIVITY_NEW_TASK)
                        if (clearTop) add(IntentConfig.IntentFlag.ACTIVITY_CLEAR_TOP)
                        if (singleTop) add(IntentConfig.IntentFlag.ACTIVITY_SINGLE_TOP)
                        if (clearTask) add(IntentConfig.IntentFlag.ACTIVITY_CLEAR_TASK)
                    }

                    val config = IntentConfig(
                        uri = uri,
                        action = action,
                        flags = flags,
                        packageName = packageName.takeIf { it.isNotBlank() }
                    )

                    Text(
                        text = "ADB Command Preview",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LogPanelCompact(
                        logText = config.toAdbCommand(),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showQrDialog = true },
                        enabled = uri.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = "Generate QR Code"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val flags = buildSet {
                                if (newTask) add(IntentConfig.IntentFlag.ACTIVITY_NEW_TASK)
                                if (clearTop) add(IntentConfig.IntentFlag.ACTIVITY_CLEAR_TOP)
                                if (singleTop) add(IntentConfig.IntentFlag.ACTIVITY_SINGLE_TOP)
                                if (clearTask) add(IntentConfig.IntentFlag.ACTIVITY_CLEAR_TASK)
                            }

                            val config = IntentConfig(
                                uri = uri,
                                action = action,
                                flags = flags,
                                packageName = packageName.takeIf { it.isNotBlank() }
                            )

                            onFire(config)
                        },
                        enabled = uri.isNotBlank()
                    ) {
                        Text("Fire Intent")
                    }
                }
            }
        }
    }

    // QR code dialog
    if (showQrDialog && uri.isNotBlank()) {
        QrCodeDialog(
            uri = uri,
            qrCodeGenerator = AppContainer.qrCodeGenerator,
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun FlagCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
