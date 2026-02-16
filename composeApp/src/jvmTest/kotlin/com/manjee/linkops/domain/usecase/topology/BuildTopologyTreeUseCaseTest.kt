package com.manjee.linkops.domain.usecase.topology

import com.manjee.linkops.domain.model.ActivityInfo
import com.manjee.linkops.domain.model.DeepLinkInfo
import com.manjee.linkops.domain.model.DomainVerificationInfo
import com.manjee.linkops.domain.model.DomainVerificationResult
import com.manjee.linkops.domain.model.DomainVerificationStatus
import com.manjee.linkops.domain.model.InsightCategory
import com.manjee.linkops.domain.model.InsightSeverity
import com.manjee.linkops.domain.model.IntentFilterInfo
import com.manjee.linkops.domain.model.ManifestInfo
import com.manjee.linkops.domain.model.TopologyNodeType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildTopologyTreeUseCaseTest {

    private lateinit var useCase: BuildTopologyTreeUseCase

    @BeforeTest
    fun setup() {
        useCase = BuildTopologyTreeUseCase()
    }

    @Test
    fun `should build tree from empty deep links`() {
        val manifestInfo = createManifestInfo(emptyList())

        val result = useCase(manifestInfo, null)

        assertEquals(0, result.totalSchemes)
        assertEquals(0, result.totalHosts)
        assertEquals(0, result.totalPaths)
        assertEquals(0, result.totalActivities)
        assertTrue(result.tree.children.isEmpty())
        assertEquals(TopologyNodeType.APP_ROOT, result.tree.type)
        assertEquals("com.example.app", result.tree.label)
    }

    @Test
    fun `should group deep links by scheme`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home"),
            createDeepLink(scheme = "myapp", host = "open", path = "/main"),
            createDeepLink(scheme = "https", host = "example.com", path = "/about")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        assertEquals(2, result.totalSchemes)
        assertEquals(2, result.tree.children.size)

        val httpsScheme = result.tree.children.find { it.label == "https://" }
        assertNotNull(httpsScheme)
        assertEquals(TopologyNodeType.SCHEME, httpsScheme.type)

        val customScheme = result.tree.children.find { it.label == "myapp://" }
        assertNotNull(customScheme)
        assertEquals(TopologyNodeType.SCHEME, customScheme.type)
    }

    @Test
    fun `should group deep links by host within scheme`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home"),
            createDeepLink(scheme = "https", host = "test.com", path = "/login"),
            createDeepLink(scheme = "https", host = "example.com", path = "/about")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        assertEquals(1, result.totalSchemes)
        val httpsScheme = result.tree.children.first()
        assertEquals(2, httpsScheme.children.size)

        val exampleHost = httpsScheme.children.find { it.label == "example.com" }
        assertNotNull(exampleHost)
        assertEquals(TopologyNodeType.HOST, exampleHost.type)
        assertEquals(2, exampleHost.metadata.deepLinkCount)

        val testHost = httpsScheme.children.find { it.label == "test.com" }
        assertNotNull(testHost)
        assertEquals(1, testHost.metadata.deepLinkCount)
    }

    @Test
    fun `should group deep links by path within host`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home"),
            createDeepLink(scheme = "https", host = "example.com", path = "/about"),
            createDeepLink(scheme = "https", host = "example.com", path = "/home")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val httpsScheme = result.tree.children.first()
        val exampleHost = httpsScheme.children.first()
        assertEquals(2, exampleHost.children.size)

        val homePath = exampleHost.children.find { it.label == "/home" }
        assertNotNull(homePath)
        assertEquals(TopologyNodeType.PATH, homePath.type)
        assertEquals(2, homePath.children.size)
    }

    @Test
    fun `should create activity nodes as leaves`() {
        val deepLinks = listOf(
            createDeepLink(
                scheme = "https",
                host = "example.com",
                path = "/home",
                activityName = "com.example.app.HomeActivity"
            )
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val httpsScheme = result.tree.children.first()
        val host = httpsScheme.children.first()
        val path = host.children.first()
        val activity = path.children.first()

        assertEquals(TopologyNodeType.ACTIVITY, activity.type)
        assertEquals("HomeActivity", activity.label)
        assertEquals("com.example.app.HomeActivity", activity.metadata.activityName)
        assertTrue(activity.children.isEmpty())
    }

    @Test
    fun `should handle deep links with no host`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "myapp", host = null, path = null)
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val scheme = result.tree.children.first()
        val host = scheme.children.first()
        assertEquals("(no host)", host.label)
    }

    @Test
    fun `should handle deep links with path prefix`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = null, pathPrefix = "/products/")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val scheme = result.tree.children.first()
        val host = scheme.children.first()
        val path = host.children.first()
        assertEquals("/products/*", path.label)
    }

    @Test
    fun `should handle deep links with path pattern`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = null, pathPattern = "/items/.*")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val scheme = result.tree.children.first()
        val host = scheme.children.first()
        val path = host.children.first()
        assertEquals("/items/.*", path.label)
    }

    @Test
    fun `should handle deep links with no path`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = null)
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val scheme = result.tree.children.first()
        val host = scheme.children.first()
        val path = host.children.first()
        assertEquals("/", path.label)
    }

    @Test
    fun `should include verification status from domain verification result`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home", autoVerify = true)
        )
        val manifestInfo = createManifestInfo(deepLinks)
        val domainVerification = DomainVerificationResult(
            packageName = "com.example.app",
            domains = listOf(
                DomainVerificationInfo("example.com", DomainVerificationStatus.VERIFIED)
            )
        )

        val result = useCase(manifestInfo, domainVerification)

        val scheme = result.tree.children.first()
        val host = scheme.children.first()
        assertEquals(DomainVerificationStatus.VERIFIED, host.metadata.verificationStatus)
    }

    @Test
    fun `should detect duplicate scheme with multiple activities handling same host`() {
        val deepLinks = listOf(
            createDeepLink(
                scheme = "https",
                host = "example.com",
                path = "/home",
                activityName = "com.example.app.HomeActivity"
            ),
            createDeepLink(
                scheme = "https",
                host = "example.com",
                path = "/home",
                activityName = "com.example.app.MainActivity"
            )
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val duplicateInsights = result.insights.filter { it.category == InsightCategory.DUPLICATE_SCHEME }
        assertTrue(duplicateInsights.isNotEmpty())
        assertEquals(InsightSeverity.WARNING, duplicateInsights.first().severity)
    }

    @Test
    fun `should detect unverified domains`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home", autoVerify = true)
        )
        val manifestInfo = createManifestInfo(deepLinks)
        val domainVerification = DomainVerificationResult(
            packageName = "com.example.app",
            domains = listOf(
                DomainVerificationInfo("example.com", DomainVerificationStatus.NONE)
            )
        )

        val result = useCase(manifestInfo, domainVerification)

        val unverifiedInsights = result.insights.filter { it.category == InsightCategory.UNVERIFIED_DOMAIN }
        assertTrue(unverifiedInsights.isNotEmpty())
        assertEquals(InsightSeverity.ERROR, unverifiedInsights.first().severity)
        assertTrue(unverifiedInsights.first().title.contains("example.com"))
    }

    @Test
    fun `should not detect unverified domains when domain verification is null`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home", autoVerify = true)
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val unverifiedInsights = result.insights.filter { it.category == InsightCategory.UNVERIFIED_DOMAIN }
        assertTrue(unverifiedInsights.isEmpty())
    }

    @Test
    fun `should detect mixed verification states`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home", autoVerify = true),
            createDeepLink(scheme = "https", host = "example.com", path = "/about", autoVerify = false)
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val mixedInsights = result.insights.filter { it.category == InsightCategory.MIXED_VERIFICATION }
        assertTrue(mixedInsights.isNotEmpty())
        assertEquals(InsightSeverity.WARNING, mixedInsights.first().severity)
    }

    @Test
    fun `should calculate correct stats`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home", activityName = "com.example.HomeActivity"),
            createDeepLink(scheme = "https", host = "test.com", path = "/login", activityName = "com.example.LoginActivity"),
            createDeepLink(scheme = "myapp", host = "open", path = null, activityName = "com.example.HomeActivity")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        assertEquals(2, result.totalSchemes)
        assertEquals(3, result.totalHosts)
        assertEquals(2, result.totalPaths)
        assertEquals(2, result.totalActivities)
    }

    @Test
    fun `should set autoVerify metadata on scheme nodes`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home", autoVerify = true)
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val scheme = result.tree.children.first()
        assertTrue(scheme.metadata.autoVerify)
    }

    @Test
    fun `should set sample URI on host and path nodes`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val scheme = result.tree.children.first()
        val host = scheme.children.first()
        assertEquals("https://example.com", host.metadata.sampleUri)

        val path = host.children.first()
        assertNotNull(path.metadata.sampleUri)
        assertTrue(path.metadata.sampleUri!!.contains("example.com"))
    }

    @Test
    fun `should assign unique IDs to all nodes`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home"),
            createDeepLink(scheme = "myapp", host = "open", path = "/main")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        val allIds = collectAllNodeIds(result.tree)
        assertEquals(allIds.size, allIds.toSet().size, "All node IDs should be unique")
    }

    @Test
    fun `should handle large number of deep links`() {
        val deepLinks = (1..100).map { i ->
            createDeepLink(
                scheme = if (i % 3 == 0) "myapp" else "https",
                host = "host${i % 10}.com",
                path = "/path/$i",
                activityName = "com.example.Activity${i % 5}"
            )
        }
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        assertEquals(2, result.totalSchemes)
        assertTrue(result.tree.children.isNotEmpty())
        assertTrue(result.totalPaths > 0)
    }

    @Test
    fun `should not report verified domains as unverified`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home", autoVerify = true)
        )
        val manifestInfo = createManifestInfo(deepLinks)
        val domainVerification = DomainVerificationResult(
            packageName = "com.example.app",
            domains = listOf(
                DomainVerificationInfo("example.com", DomainVerificationStatus.VERIFIED)
            )
        )

        val result = useCase(manifestInfo, domainVerification)

        val unverifiedInsights = result.insights.filter { it.category == InsightCategory.UNVERIFIED_DOMAIN }
        assertTrue(unverifiedInsights.isEmpty())
    }

    @Test
    fun `should set deep link count on root node`() {
        val deepLinks = listOf(
            createDeepLink(scheme = "https", host = "example.com", path = "/home"),
            createDeepLink(scheme = "https", host = "example.com", path = "/about"),
            createDeepLink(scheme = "myapp", host = "open", path = "/main")
        )
        val manifestInfo = createManifestInfo(deepLinks)

        val result = useCase(manifestInfo, null)

        assertEquals(3, result.tree.metadata.deepLinkCount)
    }

    // -- Helper functions --

    private fun createManifestInfo(
        deepLinks: List<DeepLinkInfo>,
        activities: List<ActivityInfo> = emptyList()
    ): ManifestInfo {
        return ManifestInfo(
            packageName = "com.example.app",
            versionName = "1.0.0",
            versionCode = 1,
            activities = activities,
            deepLinks = deepLinks
        )
    }

    private fun createDeepLink(
        scheme: String = "https",
        host: String? = "example.com",
        path: String? = "/home",
        pathPrefix: String? = null,
        pathPattern: String? = null,
        activityName: String = "com.example.app.MainActivity",
        autoVerify: Boolean = false
    ): DeepLinkInfo {
        return DeepLinkInfo(
            scheme = scheme,
            host = host,
            path = path,
            pathPrefix = pathPrefix,
            pathPattern = pathPattern,
            activityName = activityName,
            autoVerify = autoVerify
        )
    }

    private fun collectAllNodeIds(node: com.manjee.linkops.domain.model.TopologyNode): List<String> {
        val ids = mutableListOf(node.id)
        node.children.forEach { child ->
            ids.addAll(collectAllNodeIds(child))
        }
        return ids
    }
}
