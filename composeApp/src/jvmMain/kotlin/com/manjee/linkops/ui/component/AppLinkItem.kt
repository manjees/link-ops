package com.manjee.linkops.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manjee.linkops.domain.model.AppLink
import com.manjee.linkops.domain.model.DomainVerification
import com.manjee.linkops.domain.model.VerificationState
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * App link card component displaying package info and domain verification status
 *
 * @param appLink AppLink data to display
 * @param onReverify Callback when re-verify button is clicked
 * @param onFireIntent Callback when fire intent is requested for a domain
 * @param onShowQr Callback when QR code is requested for a domain URI
 * @param modifier Modifier for the card
 */
@Composable
fun AppLinkCard(
    appLink: AppLink,
    onReverify: () -> Unit,
    onFireIntent: ((String) -> Unit)? = null,
    onShowQr: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val verifiedCount = appLink.domains.count { it.verificationState.isSuccessful }
    val totalCount = appLink.domains.size

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Package info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appLink.packageName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Domain count summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$verifiedCount/$totalCount domains verified",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (verifiedCount == totalCount) {
                                LinkOpsColors.Verified
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        // Overall status indicator
                        StatusDot(
                            color = when {
                                verifiedCount == totalCount -> LinkOpsColors.Verified
                                verifiedCount > 0 -> LinkOpsColors.Unverified
                                else -> LinkOpsColors.Denied
                            }
                        )
                    }
                }

                // Re-verify button
                IconButton(onClick = onReverify) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-verify",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Expand/Collapse icon
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Domain list (expandable)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    appLink.domains.forEach { domain ->
                        DomainVerificationItem(
                            domainVerification = domain,
                            onFireIntent = onFireIntent?.let { { it("https://${domain.domain}") } },
                            onShowQr = onShowQr?.let { { it("https://${domain.domain}") } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Domain verification item showing domain and its verification state
 */
@Composable
fun DomainVerificationItem(
    domainVerification: DomainVerification,
    onFireIntent: (() -> Unit)? = null,
    onShowQr: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        val (icon, color) = when (domainVerification.verificationState) {
            VerificationState.VERIFIED,
            VerificationState.APPROVED -> "✓" to LinkOpsColors.Verified
            VerificationState.DENIED -> "✗" to LinkOpsColors.Denied
            VerificationState.UNVERIFIED,
            VerificationState.LEGACY_FAILURE -> "?" to LinkOpsColors.Unverified
            VerificationState.UNKNOWN -> "•" to LinkOpsColors.Unknown
        }

        Text(
            text = icon,
            color = color,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Domain info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = domainVerification.domain,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (domainVerification.fingerprint != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SHA: ${domainVerification.fingerprint.take(16)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Verification badge
        VerificationBadge(state = domainVerification.verificationState)

        // QR code button (optional)
        if (onShowQr != null) {
            Spacer(modifier = Modifier.width(4.dp))
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
        }

        // Fire intent button (optional)
        if (onFireIntent != null) {
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = onFireIntent,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Simple app link item for compact lists
 */
@Composable
fun AppLinkItemSimple(
    appLink: AppLink,
    onReverify: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appLink.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onReverify) {
                    Text("Re-verify", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            appLink.domains.forEach { domain ->
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, color) = when (domain.verificationState) {
                        VerificationState.VERIFIED,
                        VerificationState.APPROVED -> "✓" to LinkOpsColors.Verified
                        VerificationState.DENIED -> "✗" to LinkOpsColors.Denied
                        VerificationState.UNVERIFIED,
                        VerificationState.LEGACY_FAILURE -> "?" to LinkOpsColors.Unverified
                        VerificationState.UNKNOWN -> "•" to LinkOpsColors.Unknown
                    }

                    Text(text = icon, color = color)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${domain.domain}: ${domain.verificationState}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
