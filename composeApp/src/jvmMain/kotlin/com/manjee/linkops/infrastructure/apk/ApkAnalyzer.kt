package com.manjee.linkops.infrastructure.apk

import com.manjee.linkops.domain.model.ApkInfo
import com.manjee.linkops.domain.model.ApkIntentFilter
import com.manjee.linkops.domain.model.ApkLinkDomain
import com.manjee.linkops.domain.model.ApkPathPattern
import com.manjee.linkops.domain.model.ApkSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.bean.CertificateMeta
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Loads an APK off disk and pulls out the bits we care about for deep-link debugging:
 * package metadata, signing certificates, intent-filters, and a deduplicated list of
 * link domains worth checking against `assetlinks.json`.
 *
 * Wraps `net.dongliu:apk-parser` so the rest of the app never sees that library —
 * if we ever need to swap it (for AAB support, for tighter APK Signature Scheme v3
 * handling, etc.) the change stays inside this file.
 *
 * All work runs on Dispatchers.IO. APKs can be hundreds of MB and the manifest XML
 * parse alone is non-trivial.
 */
class ApkAnalyzer {

    suspend fun analyze(file: File): Result<AnalyzedApk> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists()) { "APK file not found: ${file.absolutePath}" }
            require(file.isFile) { "Path is not a file: ${file.absolutePath}" }
            require(file.length() > 0) { "APK file is empty: ${file.absolutePath}" }

            ApkFile(file).use { apk ->
                val meta = apk.apkMeta
                val info = ApkInfo(
                    filePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    packageName = meta.packageName,
                    versionName = meta.versionName,
                    versionCode = meta.versionCode,
                    minSdk = meta.minSdkVersion?.toIntOrNull(),
                    targetSdk = meta.targetSdkVersion?.toIntOrNull(),
                    applicationLabel = meta.label
                )

                val signatures = extractSignatures(apk)
                val intentFilters = extractIntentFilters(apk.manifestXml)
                val linkDomains = collapseLinkDomains(intentFilters)

                AnalyzedApk(info, signatures, intentFilters, linkDomains)
            }
        }
    }

    private fun extractSignatures(apk: ApkFile): List<ApkSignature> {
        // `getAllCertificateMetas()` returns v1 + v2 + v3 metadata grouped by the
        // signing block path. APKs commonly ship multiple schemes for compatibility,
        // so we flatten and dedup by sha256. The library only hands back raw
        // certificate bytes, so SHA-256/SHA-1 are computed locally — also keeps us
        // tolerant of library versions that omit certificate helper getters.
        val metas: List<CertificateMeta> = runCatching { apk.allCertificateMetas }
            .getOrNull()
            ?.values
            ?.flatten()
            ?: emptyList()

        return metas
            .mapNotNull { meta ->
                val der = meta.data ?: return@mapNotNull null
                val sha256 = digestHex(der, "SHA-256") ?: return@mapNotNull null
                ApkSignature(
                    sha256Fingerprint = formatFingerprint(sha256),
                    sha1Fingerprint = digestHex(der, "SHA-1")?.let(::formatFingerprint),
                    subjectDn = meta.signAlgorithm,
                    issuerDn = null,
                    validFrom = meta.startDate?.time,
                    validTo = meta.endDate?.time
                )
            }
            .distinctBy { it.sha256Fingerprint }
    }

    private fun digestHex(data: ByteArray, algorithm: String): String? = runCatching {
        MessageDigest.getInstance(algorithm)
            .digest(data)
            .joinToString("") { "%02X".format(it) }
    }.getOrNull()

    private fun formatFingerprint(uppercaseHex: String): String =
        uppercaseHex.chunked(2).joinToString(":")

    private fun extractIntentFilters(manifestXml: String): List<ApkIntentFilter> {
        if (manifestXml.isBlank()) return emptyList()

        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(manifestXml.toByteArray(Charsets.UTF_8)))

        val results = mutableListOf<ApkIntentFilter>()

        // Walk all <activity>, <activity-alias>, <service>, <receiver>, <provider> —
        // any of them can declare an intent-filter that matters for deep links,
        // though in practice activities cover ~99% of cases.
        val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")
        componentTags.forEach { tag ->
            val components = doc.getElementsByTagName(tag)
            for (i in 0 until components.length) {
                val component = components.item(i) as? Element ?: continue
                val componentName = component.getAttr("name") ?: "unknown"

                val filters = component.childElements("intent-filter")
                filters.forEach { filter ->
                    parseFilter(filter, componentName)?.let(results::add)
                }
            }
        }

        return results
    }

    private fun parseFilter(filter: Element, componentName: String): ApkIntentFilter? {
        val actions = filter.childElements("action").mapNotNull { it.getAttr("name") }
        val categories = filter.childElements("category").mapNotNull { it.getAttr("name") }
        val dataNodes = filter.childElements("data")

        val schemes = dataNodes.mapNotNull { it.getAttr("scheme") }.distinct()
        val hosts = dataNodes.mapNotNull { it.getAttr("host") }.distinct()
        val mimeTypes = dataNodes.mapNotNull { it.getAttr("mimeType") }.distinct()

        val pathPatterns = buildList {
            dataNodes.forEach { node ->
                node.getAttr("path")?.let { add(ApkPathPattern(it, ApkPathPattern.PatternType.LITERAL)) }
                node.getAttr("pathPrefix")?.let { add(ApkPathPattern(it, ApkPathPattern.PatternType.PREFIX)) }
                node.getAttr("pathSuffix")?.let { add(ApkPathPattern(it, ApkPathPattern.PatternType.SUFFIX)) }
                node.getAttr("pathPattern")?.let { add(ApkPathPattern(it, ApkPathPattern.PatternType.GLOB)) }
                node.getAttr("pathAdvancedPattern")?.let {
                    add(ApkPathPattern(it, ApkPathPattern.PatternType.ADVANCED_GLOB))
                }
            }
        }

        // Drop filters that have no link-relevant data — they're for non-URI intents
        // (boot completed, sync, push, etc.) and would clutter the result panel.
        if (schemes.isEmpty() && hosts.isEmpty() && pathPatterns.isEmpty() && mimeTypes.isEmpty()) {
            return null
        }

        val autoVerify = filter.getAttr("autoVerify")?.equals("true", ignoreCase = true) ?: false

        return ApkIntentFilter(
            componentName = componentName,
            actions = actions,
            categories = categories,
            schemes = schemes,
            hosts = hosts,
            pathPatterns = pathPatterns,
            autoVerify = autoVerify,
            mimeTypes = mimeTypes
        )
    }

    /**
     * Collapse the per-filter view into one entry per host, merging schemes and
     * propagating autoVerify=true if any filter for that host declared it. This
     * matches how Android decides whether a host is an App Link candidate.
     */
    private fun collapseLinkDomains(filters: List<ApkIntentFilter>): List<ApkLinkDomain> {
        val byHost = mutableMapOf<String, ApkLinkDomain>()
        filters.forEach { filter ->
            filter.hosts.forEach { host ->
                val existing = byHost[host]
                byHost[host] = if (existing == null) {
                    ApkLinkDomain(
                        host = host,
                        schemes = filter.schemes.toSet(),
                        autoVerify = filter.autoVerify
                    )
                } else {
                    existing.copy(
                        schemes = existing.schemes + filter.schemes,
                        autoVerify = existing.autoVerify || filter.autoVerify
                    )
                }
            }
        }
        return byHost.values.sortedBy { it.host }
    }

    private fun Element.getAttr(localName: String): String? {
        // Manifest namespace is `http://schemas.android.com/apk/res/android` for `android:*`;
        // try namespaced first, fall back to bare for the rare manifest that omits it.
        val nsValue = getAttributeNS("http://schemas.android.com/apk/res/android", localName)
        if (nsValue.isNotBlank()) return nsValue
        val plain = getAttribute(localName)
        return plain.takeIf { it.isNotBlank() }
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children: NodeList = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                if (element.localName == tagName || element.tagName == tagName) {
                    result.add(element)
                }
            }
        }
        return result
    }
}

/**
 * Raw analyzer output. The repository wraps this with optional assetlinks
 * cross-referencing before handing the full picture to the UI.
 */
data class AnalyzedApk(
    val info: ApkInfo,
    val signatures: List<ApkSignature>,
    val intentFilters: List<ApkIntentFilter>,
    val linkDomains: List<ApkLinkDomain>
)
