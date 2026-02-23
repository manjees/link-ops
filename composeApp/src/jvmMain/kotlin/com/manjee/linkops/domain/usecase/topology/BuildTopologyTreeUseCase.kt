package com.manjee.linkops.domain.usecase.topology

import com.manjee.linkops.domain.model.DeepLinkInfo
import com.manjee.linkops.domain.model.DomainVerificationResult
import com.manjee.linkops.domain.model.DomainVerificationStatus
import com.manjee.linkops.domain.model.InsightCategory
import com.manjee.linkops.domain.model.InsightSeverity
import com.manjee.linkops.domain.model.ManifestInfo
import com.manjee.linkops.domain.model.TopologyAnalysisResult
import com.manjee.linkops.domain.model.TopologyInsight
import com.manjee.linkops.domain.model.TopologyNode
import com.manjee.linkops.domain.model.TopologyNodeMetadata
import com.manjee.linkops.domain.model.TopologyNodeType

/**
 * Builds a topology tree from ManifestInfo deep links
 *
 * Groups deep links by scheme -> host -> path -> activity and detects
 * structural issues like duplicate schemes, orphaned activities, and
 * unverified domains.
 */
class BuildTopologyTreeUseCase {

    /**
     * Build topology tree from manifest analysis data
     *
     * @param manifestInfo Parsed manifest information
     * @param domainVerification Optional domain verification result
     * @return Topology analysis result with tree and insights
     */
    operator fun invoke(
        manifestInfo: ManifestInfo,
        domainVerification: DomainVerificationResult?
    ): TopologyAnalysisResult {
        val deepLinks = manifestInfo.deepLinks
        val insights = mutableListOf<TopologyInsight>()

        val schemeGroups = deepLinks.groupBy { it.scheme }

        val schemeNodes = schemeGroups.map { (scheme, schemeLinks) ->
            buildSchemeNode(scheme, schemeLinks, domainVerification, insights)
        }

        val rootNode = TopologyNode(
            id = "root",
            label = manifestInfo.packageName,
            type = TopologyNodeType.APP_ROOT,
            children = schemeNodes,
            metadata = TopologyNodeMetadata(
                deepLinkCount = deepLinks.size
            )
        )

        // Detect duplicate schemes (same scheme handled by multiple activities)
        detectDuplicateSchemes(schemeGroups, insights)

        // Detect orphaned activities
        detectOrphanedActivities(manifestInfo, deepLinks, insights)

        // Detect unverified domains
        detectUnverifiedDomains(deepLinks, domainVerification, insights)

        // Detect mixed verification states
        detectMixedVerification(deepLinks, insights)

        val allActivities = deepLinks.map { it.activityName }.distinct()
        val allHosts = deepLinks.mapNotNull { it.host }.distinct()

        return TopologyAnalysisResult(
            tree = rootNode,
            insights = insights,
            totalSchemes = schemeGroups.size,
            totalHosts = allHosts.size,
            totalPaths = deepLinks.count { it.path != null || it.pathPrefix != null || it.pathPattern != null },
            totalActivities = allActivities.size
        )
    }

    private fun buildSchemeNode(
        scheme: String,
        schemeLinks: List<DeepLinkInfo>,
        domainVerification: DomainVerificationResult?,
        insights: MutableList<TopologyInsight>
    ): TopologyNode {
        val hostGroups = schemeLinks.groupBy { it.host ?: "(no host)" }

        val hostNodes = hostGroups.map { (host, hostLinks) ->
            buildHostNode(scheme, host, hostLinks, domainVerification)
        }

        return TopologyNode(
            id = "scheme:$scheme",
            label = "$scheme://",
            type = TopologyNodeType.SCHEME,
            children = hostNodes,
            metadata = TopologyNodeMetadata(
                autoVerify = schemeLinks.any { it.autoVerify },
                deepLinkCount = schemeLinks.size
            )
        )
    }

    private fun buildHostNode(
        scheme: String,
        host: String,
        hostLinks: List<DeepLinkInfo>,
        domainVerification: DomainVerificationResult?
    ): TopologyNode {
        val verificationStatus = if (host != "(no host)") {
            domainVerification?.domains?.find { it.domain == host }?.status
        } else {
            null
        }

        val pathGroups = hostLinks.groupBy { extractPathKey(it) }

        val pathNodes = pathGroups.map { (pathKey, pathLinks) ->
            buildPathNode(scheme, host, pathKey, pathLinks)
        }

        return TopologyNode(
            id = "host:$scheme://$host",
            label = host,
            type = TopologyNodeType.HOST,
            children = pathNodes,
            metadata = TopologyNodeMetadata(
                autoVerify = hostLinks.any { it.autoVerify },
                verificationStatus = verificationStatus,
                deepLinkCount = hostLinks.size,
                sampleUri = "$scheme://$host"
            )
        )
    }

    private fun buildPathNode(
        scheme: String,
        host: String,
        pathKey: String,
        pathLinks: List<DeepLinkInfo>
    ): TopologyNode {
        val activityNodes = pathLinks.map { link ->
            TopologyNode(
                id = "activity:${link.activityName}:${link.sampleUri}",
                label = link.activityName.substringAfterLast("."),
                type = TopologyNodeType.ACTIVITY,
                children = emptyList(),
                metadata = TopologyNodeMetadata(
                    activityName = link.activityName,
                    sampleUri = link.sampleUri,
                    autoVerify = link.autoVerify
                )
            )
        }

        val displayHost = if (host == "(no host)") "" else host
        val sampleUri = if (pathKey == "(no path)") {
            "$scheme://$displayHost/"
        } else {
            "$scheme://$displayHost$pathKey"
        }

        return TopologyNode(
            id = "path:$scheme://$host$pathKey",
            label = if (pathKey == "(no path)") "/" else pathKey,
            type = TopologyNodeType.PATH,
            children = activityNodes,
            metadata = TopologyNodeMetadata(
                deepLinkCount = pathLinks.size,
                sampleUri = sampleUri
            )
        )
    }

    private fun extractPathKey(link: DeepLinkInfo): String {
        return when {
            link.path != null -> link.path
            link.pathPrefix != null -> "${link.pathPrefix}*"
            link.pathPattern != null -> link.pathPattern
            else -> "(no path)"
        }
    }

    private fun detectDuplicateSchemes(
        schemeGroups: Map<String, List<DeepLinkInfo>>,
        insights: MutableList<TopologyInsight>
    ) {
        schemeGroups.forEach { (scheme, links) ->
            val activitiesByHost = links.groupBy { it.host ?: "(no host)" }
            activitiesByHost.forEach { (host, hostLinks) ->
                val activities = hostLinks.map { it.activityName }.distinct()
                if (activities.size > 1) {
                    insights.add(
                        TopologyInsight(
                            severity = InsightSeverity.WARNING,
                            category = InsightCategory.DUPLICATE_SCHEME,
                            title = "Multiple activities handle $scheme://$host",
                            description = "${activities.size} activities handle the same scheme+host combination: ${activities.joinToString(", ") { it.substringAfterLast(".") }}",
                            affectedNodeIds = activities.map { "activity:$it:$scheme://$host" }
                        )
                    )
                }
            }
        }
    }

    private fun detectOrphanedActivities(
        manifestInfo: ManifestInfo,
        deepLinks: List<DeepLinkInfo>,
        insights: MutableList<TopologyInsight>
    ) {
        val deepLinkActivities = deepLinks.map { it.activityName }.toSet()
        val allActivities = manifestInfo.activities.filter { activity ->
            activity.intentFilters.any { filter ->
                filter.isViewAction && filter.isBrowsable
            }
        }

        val orphaned = allActivities.filter { activity ->
            activity.name !in deepLinkActivities &&
                activity.intentFilters.any { it.hasDeepLinkData }
        }

        orphaned.forEach { activity ->
            insights.add(
                TopologyInsight(
                    severity = InsightSeverity.WARNING,
                    category = InsightCategory.ORPHANED_ACTIVITY,
                    title = "Orphaned activity: ${activity.name.substringAfterLast(".")}",
                    description = "Activity ${activity.name} has intent filters with deep link data but no resolved deep links",
                    affectedNodeIds = listOf("activity:${activity.name}")
                )
            )
        }
    }

    private fun detectUnverifiedDomains(
        deepLinks: List<DeepLinkInfo>,
        domainVerification: DomainVerificationResult?,
        insights: MutableList<TopologyInsight>
    ) {
        if (domainVerification == null) return

        val appLinkHosts = deepLinks
            .filter { it.isAppLink }
            .mapNotNull { it.host }
            .distinct()

        appLinkHosts.forEach { host ->
            val status = domainVerification.domains.find { it.domain == host }?.status
            if (status != null && status != DomainVerificationStatus.VERIFIED && status != DomainVerificationStatus.ALWAYS) {
                insights.add(
                    TopologyInsight(
                        severity = InsightSeverity.ERROR,
                        category = InsightCategory.UNVERIFIED_DOMAIN,
                        title = "Unverified domain: $host",
                        description = "Domain $host has verification status: ${status.displayName}. App Links will not work as expected.",
                        affectedNodeIds = deepLinks
                            .filter { it.host == host && it.isAppLink }
                            .map { "host:${it.scheme}://$host" }
                            .distinct()
                    )
                )
            }
        }
    }

    private fun detectMixedVerification(
        deepLinks: List<DeepLinkInfo>,
        insights: MutableList<TopologyInsight>
    ) {
        val hostGroups = deepLinks.groupBy { it.host }
        hostGroups.forEach { (host, links) ->
            if (host == null) return@forEach
            val hasAutoVerify = links.any { it.autoVerify }
            val hasNoAutoVerify = links.any { !it.autoVerify }
            if (hasAutoVerify && hasNoAutoVerify) {
                insights.add(
                    TopologyInsight(
                        severity = InsightSeverity.WARNING,
                        category = InsightCategory.MIXED_VERIFICATION,
                        title = "Mixed verification for $host",
                        description = "Some deep links for $host have autoVerify=true while others do not. This may cause inconsistent behavior.",
                        affectedNodeIds = links.flatMap { link ->
                            listOf("host:${link.scheme}://$host")
                        }.distinct()
                    )
                )
            }
        }
    }
}
