package com.manjee.linkops.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.manjee.linkops.ui.theme.LinkOpsColors

/**
 * Flattened node for display in LazyColumn
 */
private data class FlatNode(
    val node: TopologyNode,
    val depth: Int,
    val isExpanded: Boolean,
    val hasChildren: Boolean
)

/**
 * Tree view displaying topology nodes with expand/collapse
 *
 * @param analysisResult The topology analysis result to display
 * @param searchQuery Current search query for filtering
 * @param highlightedNodeIds Set of node IDs to highlight
 * @param onNodeClick Callback when a node is clicked
 * @param modifier Modifier for the component
 */
@Composable
fun TopologyTreeView(
    analysisResult: TopologyAnalysisResult,
    searchQuery: String,
    highlightedNodeIds: Set<String>,
    onNodeClick: (TopologyNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedNodes = remember { mutableStateMapOf<String, Boolean>() }

    // Auto-expand root and scheme nodes by default
    LaunchedEffect(analysisResult) {
        expandedNodes["root"] = true
        analysisResult.tree.children.forEach { schemeNode ->
            expandedNodes[schemeNode.id] = true
        }
    }

    // Auto-expand highlighted nodes
    LaunchedEffect(highlightedNodeIds) {
        if (highlightedNodeIds.isNotEmpty()) {
            expandAncestors(analysisResult.tree, highlightedNodeIds, expandedNodes)
        }
    }

    val flatNodes = remember(analysisResult, expandedNodes.toMap(), searchQuery) {
        buildFlatList(analysisResult.tree, 0, expandedNodes, searchQuery)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(flatNodes, key = { it.node.id }) { flatNode ->
            TopologyNodeRow(
                node = flatNode.node,
                depth = flatNode.depth,
                isExpanded = flatNode.isExpanded,
                hasChildren = flatNode.hasChildren,
                isHighlighted = highlightedNodeIds.contains(flatNode.node.id),
                matchesSearch = searchQuery.isNotBlank() && nodeMatchesSearch(flatNode.node, searchQuery),
                onToggleExpand = {
                    expandedNodes[flatNode.node.id] = !(expandedNodes[flatNode.node.id] ?: false)
                },
                onClick = { onNodeClick(flatNode.node) }
            )
        }
    }
}

/**
 * Row representing a single topology node
 */
@Composable
private fun TopologyNodeRow(
    node: TopologyNode,
    depth: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isHighlighted: Boolean,
    matchesSearch: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    val indentDp = (depth * 24).dp
    val nodeColor = nodeColor(node)
    val bgColor = when {
        isHighlighted -> LinkOpsColors.PrimaryLight.copy(alpha = 0.3f)
        matchesSearch -> LinkOpsColors.InfoLight.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp)
            .background(bgColor, RoundedCornerShape(6.dp))
            .clickable { if (hasChildren) onToggleExpand() else onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Expand/collapse indicator
        if (hasChildren) {
            Text(
                text = if (isExpanded) "▼" else "▶",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(16.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Node type icon
        Text(
            text = nodeIcon(node),
            style = MaterialTheme.typography.bodyMedium
        )

        // Node label
        Text(
            text = node.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (node.type != TopologyNodeType.APP_ROOT) FontFamily.Monospace else FontFamily.Default,
                fontSize = if (node.type == TopologyNodeType.APP_ROOT) 14.sp else 13.sp
            ),
            fontWeight = if (node.type == TopologyNodeType.APP_ROOT || node.type == TopologyNodeType.SCHEME) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            },
            color = nodeColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Badges
        NodeBadges(node)
    }
}

/**
 * Badges displayed on a topology node row
 */
@Composable
private fun NodeBadges(node: TopologyNode) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Verification status badge for HOST nodes
        node.metadata.verificationStatus?.let { status ->
            val (text, color, bgColor) = when (status) {
                DomainVerificationStatus.VERIFIED -> Triple("verified", LinkOpsColors.Success, LinkOpsColors.SuccessLight)
                DomainVerificationStatus.ALWAYS -> Triple("always", LinkOpsColors.Success, LinkOpsColors.SuccessLight)
                DomainVerificationStatus.NONE -> Triple("none", LinkOpsColors.Unknown, LinkOpsColors.SecondaryLight)
                DomainVerificationStatus.LEGACY_FAILURE -> Triple("failed", LinkOpsColors.Error, LinkOpsColors.ErrorLight)
                DomainVerificationStatus.NEVER -> Triple("never", LinkOpsColors.Error, LinkOpsColors.ErrorLight)
                DomainVerificationStatus.UNKNOWN -> Triple("unknown", LinkOpsColors.Unknown, LinkOpsColors.SecondaryLight)
            }
            Box(
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontSize = 10.sp
                )
            }
        }

        // Auto-verify badge for SCHEME nodes
        if (node.metadata.autoVerify && node.type == TopologyNodeType.SCHEME) {
            Box(
                modifier = Modifier
                    .background(LinkOpsColors.SuccessLight, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "autoVerify",
                    style = MaterialTheme.typography.labelSmall,
                    color = LinkOpsColors.Success,
                    fontSize = 10.sp
                )
            }
        }

        // Deep link count badge
        if (node.metadata.deepLinkCount > 0 && node.type != TopologyNodeType.ACTIVITY) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${node.metadata.deepLinkCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Panel displaying topology insights
 *
 * @param insights List of topology insights
 * @param onInsightClick Callback when an insight is clicked
 * @param modifier Modifier for the component
 */
@Composable
fun TopologyInsightsPanel(
    insights: List<TopologyInsight>,
    onInsightClick: (TopologyInsight) -> Unit,
    modifier: Modifier = Modifier
) {
    if (insights.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Insights (${insights.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            insights.forEach { insight ->
                InsightItem(
                    insight = insight,
                    onClick = { onInsightClick(insight) }
                )
            }
        }
    }
}

/**
 * Individual insight item
 */
@Composable
private fun InsightItem(
    insight: TopologyInsight,
    onClick: () -> Unit
) {
    val (bgColor, iconColor, icon) = when (insight.severity) {
        InsightSeverity.ERROR -> Triple(LinkOpsColors.InsightErrorBg, LinkOpsColors.Error, "!")
        InsightSeverity.WARNING -> Triple(LinkOpsColors.InsightWarningBg, LinkOpsColors.Warning, "!")
        InsightSeverity.INFO -> Triple(LinkOpsColors.InsightInfoBg, LinkOpsColors.Info, "i")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(iconColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = iconColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = insight.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Statistics summary for topology tree
 *
 * @param analysisResult The topology analysis result
 * @param modifier Modifier for the component
 */
@Composable
fun TopologyStatsBar(
    analysisResult: TopologyAnalysisResult,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatChip("Schemes", analysisResult.totalSchemes)
        StatChip("Hosts", analysisResult.totalHosts)
        StatChip("Paths", analysisResult.totalPaths)
        StatChip("Activities", analysisResult.totalActivities)
    }
}

@Composable
private fun StatChip(label: String, count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = LinkOpsColors.Primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// -- Helper functions --

private fun nodeIcon(node: TopologyNode): String {
    return when (node.type) {
        TopologyNodeType.APP_ROOT -> "\uD83D\uDCE6"
        TopologyNodeType.SCHEME -> if (node.label.startsWith("http")) "\uD83D\uDD12" else "\uD83D\uDD17"
        TopologyNodeType.HOST -> "\uD83C\uDF10"
        TopologyNodeType.PATH -> "\u2192"
        TopologyNodeType.ACTIVITY -> "\u25A0"
    }
}

@Composable
private fun nodeColor(node: TopologyNode): androidx.compose.ui.graphics.Color {
    return when (node.type) {
        TopologyNodeType.APP_ROOT -> MaterialTheme.colorScheme.onSurface
        TopologyNodeType.SCHEME -> if (node.label.startsWith("http")) {
            LinkOpsColors.TopologySchemeHttp
        } else {
            LinkOpsColors.TopologySchemeCustom
        }
        TopologyNodeType.HOST -> when (node.metadata.verificationStatus) {
            DomainVerificationStatus.VERIFIED, DomainVerificationStatus.ALWAYS -> LinkOpsColors.TopologyHostVerified
            DomainVerificationStatus.LEGACY_FAILURE, DomainVerificationStatus.NEVER -> LinkOpsColors.TopologyHostFailed
            else -> MaterialTheme.colorScheme.onSurface
        }
        TopologyNodeType.PATH -> LinkOpsColors.TopologyPath
        TopologyNodeType.ACTIVITY -> LinkOpsColors.TopologyActivity
    }
}

private fun nodeMatchesSearch(node: TopologyNode, query: String): Boolean {
    val q = query.lowercase()
    return node.label.lowercase().contains(q) ||
        node.metadata.activityName?.lowercase()?.contains(q) == true ||
        node.metadata.sampleUri?.lowercase()?.contains(q) == true
}

private fun buildFlatList(
    node: TopologyNode,
    depth: Int,
    expandedNodes: Map<String, Boolean>,
    searchQuery: String
): List<FlatNode> {
    val result = mutableListOf<FlatNode>()
    val isExpanded = expandedNodes[node.id] ?: false
    val hasChildren = node.children.isNotEmpty()

    // Skip root node itself, but include its children
    if (node.type == TopologyNodeType.APP_ROOT) {
        if (isExpanded || searchQuery.isNotBlank()) {
            node.children.forEach { child ->
                result.addAll(buildFlatList(child, depth, expandedNodes, searchQuery))
            }
        }
        return result
    }

    // If searching, only show matching nodes and their ancestors
    if (searchQuery.isNotBlank()) {
        if (!subtreeMatchesSearch(node, searchQuery)) {
            return result
        }
    }

    result.add(
        FlatNode(
            node = node,
            depth = depth,
            isExpanded = isExpanded,
            hasChildren = hasChildren
        )
    )

    if (isExpanded || (searchQuery.isNotBlank() && subtreeMatchesSearch(node, searchQuery))) {
        node.children.forEach { child ->
            result.addAll(buildFlatList(child, depth + 1, expandedNodes, searchQuery))
        }
    }

    return result
}

private fun subtreeMatchesSearch(node: TopologyNode, query: String): Boolean {
    if (nodeMatchesSearch(node, query)) return true
    return node.children.any { subtreeMatchesSearch(it, query) }
}

private fun expandAncestors(
    root: TopologyNode,
    targetIds: Set<String>,
    expandedNodes: MutableMap<String, Boolean>
) {
    fun findAndExpand(node: TopologyNode): Boolean {
        if (targetIds.contains(node.id)) return true
        var found = false
        node.children.forEach { child ->
            if (findAndExpand(child)) {
                expandedNodes[node.id] = true
                found = true
            }
        }
        return found
    }
    findAndExpand(root)
}
