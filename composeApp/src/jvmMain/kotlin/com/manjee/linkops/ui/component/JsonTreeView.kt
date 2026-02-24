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
import com.manjee.linkops.domain.model.BundleExtraType
import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.model.ParsingMethod
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Displays intent payload fields and extras in a structured tree view
 *
 * @param intentPayload The intent payload to display
 * @param modifier Modifier for the component
 */
@Composable
fun JsonTreeView(
    intentPayload: IntentPayload,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Parsing method badge
        item {
            ParsingMethodBadge(intentPayload.parsingMethod)
        }

        // Intent fields
        item {
            Text(
                text = "Intent Fields",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item { FieldRow("action", intentPayload.action ?: "(none)") }
        item { FieldRow("data", intentPayload.dataUri ?: "(none)") }
        item { FieldRow("flags", "0x${intentPayload.flags.toString(16).uppercase()}") }
        item { FieldRow("component", intentPayload.component ?: "(none)") }

        if (intentPayload.categories.isNotEmpty()) {
            item {
                FieldRow("categories", intentPayload.categories.joinToString(", "))
            }
        }

        // Extras section
        if (intentPayload.hasExtras) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Extras (${intentPayload.extras.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(intentPayload.extras.values.toList()) { extra ->
                ExtraRow(extra)
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No extras",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExtraRow(
    extra: BundleExtra,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = extra.key,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = extra.value ?: "(null)",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ExtraTypeBadge(extra.type)
    }
}

@Composable
private fun ExtraTypeBadge(
    type: BundleExtraType,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (type) {
        BundleExtraType.STRING -> "String" to LinkOpsColors.Info
        BundleExtraType.INT -> "Int" to LinkOpsColors.Success
        BundleExtraType.LONG -> "Long" to LinkOpsColors.Success
        BundleExtraType.FLOAT -> "Float" to LinkOpsColors.Success
        BundleExtraType.BOOLEAN -> "Bool" to LinkOpsColors.Warning
        BundleExtraType.BUNDLE -> "Bundle" to LinkOpsColors.Primary
        BundleExtraType.PARCELABLE -> "Parcelable" to LinkOpsColors.Secondary
        BundleExtraType.UNKNOWN -> "Unknown" to LinkOpsColors.Unknown
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ParsingMethodBadge(
    method: ParsingMethod,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (method) {
        ParsingMethod.REGEX -> "Parsed via Regex" to LinkOpsColors.Success
        ParsingMethod.SIMPLE_SPLIT -> "Parsed via Split" to LinkOpsColors.Warning
        ParsingMethod.RAW_ONLY -> "Raw Only" to LinkOpsColors.Error
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
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
