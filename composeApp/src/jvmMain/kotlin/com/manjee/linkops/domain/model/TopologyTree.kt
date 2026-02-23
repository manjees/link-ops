package com.manjee.linkops.domain.model

/**
 * Represents a node in the deep link topology tree
 *
 * Tree structure: APP_ROOT -> SCHEME -> HOST -> PATH -> ACTIVITY
 */
data class TopologyNode(
    val id: String,
    val label: String,
    val type: TopologyNodeType,
    val children: List<TopologyNode>,
    val metadata: TopologyNodeMetadata = TopologyNodeMetadata()
) {
    /**
     * Total number of descendants including this node
     */
    val totalDescendants: Int
        get() = 1 + children.sumOf { it.totalDescendants }
}

/**
 * Type of topology node in the hierarchy
 */
enum class TopologyNodeType {
    APP_ROOT,
    SCHEME,
    HOST,
    PATH,
    ACTIVITY
}

/**
 * Metadata for a topology node
 */
data class TopologyNodeMetadata(
    val autoVerify: Boolean = false,
    val verificationStatus: DomainVerificationStatus? = null,
    val isDuplicate: Boolean = false,
    val isOrphaned: Boolean = false,
    val sampleUri: String? = null,
    val activityName: String? = null,
    val deepLinkCount: Int = 0
)

/**
 * Result of topology tree analysis
 */
data class TopologyAnalysisResult(
    val tree: TopologyNode,
    val insights: List<TopologyInsight>,
    val totalSchemes: Int,
    val totalHosts: Int,
    val totalPaths: Int,
    val totalActivities: Int
)

/**
 * An insight detected during topology analysis
 */
data class TopologyInsight(
    val severity: InsightSeverity,
    val category: InsightCategory,
    val title: String,
    val description: String,
    val affectedNodeIds: List<String>
)

/**
 * Severity level of a topology insight
 */
enum class InsightSeverity {
    INFO,
    WARNING,
    ERROR
}

/**
 * Category of topology insight
 */
enum class InsightCategory {
    DUPLICATE_SCHEME,
    MISSING_PATH,
    ORPHANED_ACTIVITY,
    UNVERIFIED_DOMAIN,
    MIXED_VERIFICATION
}
