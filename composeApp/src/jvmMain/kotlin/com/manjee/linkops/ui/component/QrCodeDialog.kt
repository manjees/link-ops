package com.manjee.linkops.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.manjee.linkops.infrastructure.qr.QrCodeGenerator
import com.manjee.linkops.ui.theme.LinkOpsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import javax.imageio.ImageIO

/**
 * Dialog that displays a QR code for a given URI with copy and save options
 *
 * @param uri The URI to encode as QR code
 * @param title Dialog title
 * @param qrCodeGenerator Generator instance for creating QR codes
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun QrCodeDialog(
    uri: String,
    title: String = "QR Code",
    qrCodeGenerator: QrCodeGenerator,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val qrResult = remember(uri) {
        qrCodeGenerator.generateWithInfo(uri)
    }

    val isCustomScheme = remember(uri) {
        val scheme = uri.substringBefore("://", "")
        scheme.isNotEmpty() && scheme != "http" && scheme != "https"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 640.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code
                QrCodeImage(
                    content = uri,
                    qrCodeGenerator = qrCodeGenerator,
                    modifier = Modifier.size(280.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Warning for long URIs
                qrResult.warning?.let { warning ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = LinkOpsColors.Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkOpsColors.Warning
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Custom scheme warning
                if (isCustomScheme) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = LinkOpsColors.Info,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Custom scheme URI may not be recognized by default camera apps. Use a QR scanner app instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkOpsColors.Info
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // URI display
                Text(
                    text = uri,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // EC Level info
                Text(
                    text = "EC Level: ${qrResult.ecLevelDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uri))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy URI")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                saveQrPng(uri, qrCodeGenerator)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save PNG")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Saves QR code as PNG using a file dialog
 */
private fun saveQrPng(uri: String, qrCodeGenerator: QrCodeGenerator) {
    val image = qrCodeGenerator.generate(uri)

    val sanitized = uri
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        .take(50)

    val dialog = FileDialog(null as Frame?, "Save QR Code", FileDialog.SAVE).apply {
        file = "qr_$sanitized.png"
        isVisible = true
    }

    val directory = dialog.directory
    val filename = dialog.file

    if (directory != null && filename != null) {
        val file = java.io.File(directory, filename)
        ImageIO.write(image, "PNG", file)
    }
}
