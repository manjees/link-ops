package com.manjee.linkops.ui.screen.apk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjee.linkops.domain.model.ApkAnalysisResult
import com.manjee.linkops.domain.model.ApkDomainVerification
import com.manjee.linkops.domain.model.ApkFingerprintMatch
import com.manjee.linkops.domain.model.ApkInfo
import com.manjee.linkops.domain.model.ApkIntentFilter
import com.manjee.linkops.domain.model.ApkLinkDomain
import com.manjee.linkops.domain.model.ApkPathPattern
import com.manjee.linkops.domain.model.ApkSignature
import com.manjee.linkops.domain.model.ValidationStatus
import com.manjee.linkops.ui.component.LoadingOverlay
import com.manjee.linkops.ui.component.StatusDot
import com.manjee.linkops.ui.theme.LinkOpsColors
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Screen for inspecting a local APK without installing it. Pulls package metadata,
 * signing certificates, intent-filters, and (most importantly) cross-checks every
 * autoVerify domain against the live assetlinks.json so the user can answer
 * "would this build verify on a device?" before shipping.
 */
@Composable
fun ApkInspectorScreen(
    viewModel: ApkInspectorViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "APK Inspector",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            FilePickerSection(
                selectedFile = state.selectedFile,
                isAnalyzing = state.isAnalyzing,
                onFilePicked = { file -> viewModel.analyzeFile(file) },
                onClear = { viewModel.clearResult() }
            )

            if (state.error != null) {
                ErrorBanner(message = state.error!!, onDismiss = { viewModel.clearError() })
            }

            HorizontalDivider()

            Text(
                text = "Inspects without installing. Reads manifest + signing certs + intent-filters " +
                    "directly from the APK, then fetches assetlinks.json for every autoVerify domain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            ResultsPanel(state.result)
        }
    }

    LoadingOverlay(
        isLoading = state.isAnalyzing,
        message = state.progressLabel
    )
}

@Composable
private fun FilePickerSection(
    selectedFile: File?,
    isAnalyzing: Boolean,
    onFilePicked: (File) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Select an APK",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Compose Desktop has no native picker, so we delegate to AWT — works
        // identically across macOS / Windows / Linux because FileDialog uses the
        // OS-native picker in each case.
        OutlinedButton(
            onClick = {
                val file = pickApkFile()
                if (file != null) onFilePicked(file)
            },
            enabled = !isAnalyzing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse for APK…")
        }

        if (selectedFile != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${"%.2f".format(selectedFile.length() / (1024.0 * 1024.0))} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isAnalyzing) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            }
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

@Composable
private fun ResultsPanel(result: ApkAnalysisResult?) {
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No APK selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Browse and pick an .apk file to inspect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ApkInfoCard(result.info) }

        item { SignaturesCard(result.signatures) }

        if (result.domainVerifications.isNotEmpty()) {
            item { DomainVerificationCard(result.domainVerifications) }
        }

        if (result.linkDomains.isNotEmpty()) {
            item { LinkDomainsCard(result.linkDomains) }
        }

        if (result.intentFilters.isNotEmpty()) {
            item { IntentFiltersCard(result.intentFilters) }
        }
    }
}

@Composable
private fun ApkInfoCard(info: ApkInfo) {
    SectionCard(title = "Package") {
        InfoRow("Package name", info.packageName)
        info.applicationLabel?.let { InfoRow("Label", it) }
        info.versionName?.let { InfoRow("Version", "$it (code ${info.versionCode ?: '?'})") }
        InfoRow(
            "SDK",
            "min ${info.minSdk ?: '?'} · target ${info.targetSdk ?: '?'}"
        )
        InfoRow("File", info.filePath, monospace = true)
    }
}

@Composable
private fun SignaturesCard(signatures: List<ApkSignature>) {
    SectionCard(title = "Signing certificates (${signatures.size})") {
        if (signatures.isEmpty()) {
            Text(
                text = "No signing certificates found — APK appears to be unsigned.",
                style = MaterialTheme.typography.bodySmall,
                color = LinkOpsColors.Warning
            )
            return@SectionCard
        }
        signatures.forEachIndexed { index, sig ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SHA-256",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Text(
                    text = sig.sha256Fingerprint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
            sig.subjectDn?.let {
                Text(
                    text = "Algorithm: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DomainVerificationCard(verifications: List<ApkDomainVerification>) {
    SectionCard(title = "App Links verification (${verifications.size})") {
        verifications.forEach { verification ->
            VerificationRow(verification)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VerificationRow(verification: ApkDomainVerification) {
    val (color, label) = when (val match = verification.fingerprintMatch) {
        ApkFingerprintMatch.FullMatch -> LinkOpsColors.Success to "Fingerprint matches"
        is ApkFingerprintMatch.PartialMatch ->
            LinkOpsColors.Warning to "Partial match (${match.matchedFingerprints.size} of ${match.matchedFingerprints.size + match.unmatchedFingerprints.size})"
        is ApkFingerprintMatch.NoMatch -> LinkOpsColors.Error to "No fingerprint match"
        ApkFingerprintMatch.PackageNotDeclared ->
            LinkOpsColors.Error to "Package not in assetlinks.json"
        ApkFingerprintMatch.AssetLinksUnavailable ->
            LinkOpsColors.Unknown to when (verification.assetLinksValidation.status) {
                ValidationStatus.NOT_FOUND -> "assetlinks.json not found"
                ValidationStatus.INVALID_JSON -> "assetlinks.json invalid"
                ValidationStatus.NETWORK_ERROR -> "Network error"
                ValidationStatus.REDIRECT -> "Redirect (not allowed)"
                else -> "assetlinks.json unavailable"
            }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = color)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = verification.domain,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
private fun LinkDomainsCard(domains: List<ApkLinkDomain>) {
    SectionCard(title = "All declared link domains (${domains.size})") {
        domains.forEach { domain ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(
                            if (domain.isAppLinkCandidate) LinkOpsColors.SuccessLight
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (domain.isAppLinkCandidate) "AppLink" else domain.schemes.firstOrNull() ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (domain.isAppLinkCandidate) LinkOpsColors.Success
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SelectionContainer {
                    Text(
                        text = domain.host,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = domain.schemes.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntentFiltersCard(filters: List<ApkIntentFilter>) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(title = "Intent filters (${filters.size})") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Per-component declarations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Collapse" else "Expand")
            }
        }
        if (expanded) {
            filters.forEach { filter -> IntentFilterItem(filter) }
        }
    }
}

@Composable
private fun IntentFilterItem(filter: ApkIntentFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = filter.componentName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (filter.autoVerify) {
                Box(
                    modifier = Modifier
                        .background(LinkOpsColors.SuccessLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "autoVerify",
                        style = MaterialTheme.typography.labelSmall,
                        color = LinkOpsColors.Success,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (filter.actions.isNotEmpty()) {
            InfoRow("Actions", filter.actions.joinToString(", "))
        }
        if (filter.schemes.isNotEmpty()) {
            InfoRow("Schemes", filter.schemes.joinToString(", "), monospace = true)
        }
        if (filter.hosts.isNotEmpty()) {
            InfoRow("Hosts", filter.hosts.joinToString(", "), monospace = true)
        }
        if (filter.pathPatterns.isNotEmpty()) {
            Text(
                text = "Paths:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            filter.pathPatterns.forEach { pp ->
                Text(
                    text = "${pp.type.label} ${pp.pattern}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private val ApkPathPattern.PatternType.label: String
    get() = when (this) {
        ApkPathPattern.PatternType.LITERAL -> "path:"
        ApkPathPattern.PatternType.PREFIX -> "pathPrefix:"
        ApkPathPattern.PatternType.SUFFIX -> "pathSuffix:"
        ApkPathPattern.PatternType.GLOB -> "pathPattern:"
        ApkPathPattern.PatternType.ADVANCED_GLOB -> "pathAdvancedPattern:"
    }

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = if (monospace) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * AWT FileDialog uses the OS-native picker on every platform, so the user gets
 * exactly the picker they expect (Finder on macOS, Explorer on Windows, etc.).
 */
private fun pickApkFile(): File? {
    val dialog = FileDialog(null as Frame?, "Select APK", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".apk", ignoreCase = true) }
        isVisible = true
    }
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
