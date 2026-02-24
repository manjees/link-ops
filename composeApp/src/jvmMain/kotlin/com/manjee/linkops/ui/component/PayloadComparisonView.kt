package com.manjee.linkops.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manjee.linkops.domain.model.BundleExtra
import com.manjee.linkops.domain.model.PayloadComparison
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Displays a side-by-side diff comparison of two intent payloads
 *
 * @param comparison The payload comparison result
 * @param modifier Modifier for the component
 */
@Composable
fun PayloadComparisonView(
    comparison: PayloadComparison,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Added extras
        if (comparison.addedExtras.isNotEmpty()) {
            item {
                DiffSectionHeader(
                    title = "Added (${comparison.addedExtras.size})",
                    color = LinkOpsColors.Success
                )
            }
            items(comparison.addedExtras.values.toList()) { extra ->
                DiffExtraRow(extra = extra, diffType = DiffType.ADDED)
            }
        }

        // Removed extras
        if (comparison.removedExtras.isNotEmpty()) {
            item {
                DiffSectionHeader(
                    title = "Removed (${comparison.removedExtras.size})",
                    color = LinkOpsColors.Error
                )
            }
            items(comparison.removedExtras.values.toList()) { extra ->
                DiffExtraRow(extra = extra, diffType = DiffType.REMOVED)
            }
        }

        // Changed extras
        if (comparison.changedExtras.isNotEmpty()) {
            item {
                DiffSectionHeader(
                    title = "Changed (${comparison.changedExtras.size})",
                    color = LinkOpsColors.Warning
                )
            }
            items(comparison.changedExtras.entries.toList()) { (_, pair) ->
                ChangedExtraRow(oldExtra = pair.first, newExtra = pair.second)
            }
        }

        // Unchanged extras
        if (comparison.unchangedExtras.isNotEmpty()) {
            item {
                DiffSectionHeader(
                    title = "Unchanged (${comparison.unchangedExtras.size})",
                    color = LinkOpsColors.Unknown
                )
            }
            items(comparison.unchangedExtras.values.toList()) { extra ->
                DiffExtraRow(extra = extra, diffType = DiffType.UNCHANGED)
            }
        }

        // Empty state
        if (comparison.addedExtras.isEmpty() && comparison.removedExtras.isEmpty() &&
            comparison.changedExtras.isEmpty() && comparison.unchangedExtras.isEmpty()
        ) {
            item {
                Text(
                    text = "Both payloads have no extras to compare",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class DiffType {
    ADDED, REMOVED, UNCHANGED
}

@Composable
private fun DiffSectionHeader(
    title: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DiffExtraRow(
    extra: BundleExtra,
    diffType: DiffType,
    modifier: Modifier = Modifier
) {
    val (bgColor, prefix) = when (diffType) {
        DiffType.ADDED -> LinkOpsColors.SuccessLight to "+"
        DiffType.REMOVED -> LinkOpsColors.ErrorLight to "-"
        DiffType.UNCHANGED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) to " "
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = "${extra.key} = ${extra.value ?: "(null)"}",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = extra.type.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChangedExtraRow(
    oldExtra: BundleExtra,
    newExtra: BundleExtra,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LinkOpsColors.WarningLight, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = oldExtra.key,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "- ",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = LinkOpsColors.Error
            )
            Text(
                text = oldExtra.value ?: "(null)",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = LinkOpsColors.Error
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "+ ",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = LinkOpsColors.Success
            )
            Text(
                text = newExtra.value ?: "(null)",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = LinkOpsColors.Success
            )
        }
    }
}
