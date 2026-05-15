package com.manjee.linkops.data.parser

import com.manjee.linkops.domain.model.AasaIssue
import com.manjee.linkops.domain.model.AasaPathComponent
import com.manjee.linkops.domain.model.AppLinkDetail
import com.manjee.linkops.domain.model.AppLinksSection
import com.manjee.linkops.domain.model.AppleAppSiteAssociation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parser for `apple-app-site-association` JSON.
 *
 * AASA has two coexisting schemas Apple still ships:
 *  - **Legacy** (pre-iOS 14): `appID` (singular) + `paths` (array of glob strings)
 *  - **iOS 14+**: `appIDs` (plural array) + `components` (array of objects with
 *    `/`, `?`, `#`, `exclude`, `caseSensitive`, `percentEncoded`)
 *
 * Both schemas can coexist inside the same `details` array; some real-world
 * configs even mix legacy and modern entries to support older iOS versions.
 * We don't pick one — we hand both forms back to the UI in [AppLinkDetail] so
 * the developer can see exactly what's deployed and flag legacy-only entries.
 *
 * We hand-roll on JsonObject rather than declaring a fixed @Serializable shape
 * because the spec lets keys appear in either schema and the JSON `/` key in
 * components can't be a Kotlin identifier.
 */
class AasaParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(content: String): ParseResult {
        val issues = mutableListOf<AasaIssue>()

        val root = try {
            json.parseToJsonElement(content).jsonObject
        } catch (e: Exception) {
            return ParseResult.Error(
                message = "Failed to parse JSON: ${e.message}",
                cause = e,
                issues = listOf(
                    AasaIssue(
                        severity = AasaIssue.Severity.ERROR,
                        code = AasaIssue.AasaIssueCode.INVALID_JSON_SYNTAX,
                        message = "Invalid JSON syntax",
                        details = e.message
                    )
                )
            )
        }

        val applinks = root["applinks"]?.let { parseApplinks(it.jsonObject, issues) }
        if (applinks == null) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.ERROR,
                    code = AasaIssue.AasaIssueCode.MISSING_APPLINKS,
                    message = "AASA file has no `applinks` section",
                    details = "Without applinks, iOS will not register any Universal Links for this domain."
                )
            )
        }

        val hasWebcredentials = root["webcredentials"] != null
        if (hasWebcredentials) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.INFO,
                    code = AasaIssue.AasaIssueCode.WEB_CREDENTIALS_PRESENT,
                    message = "Domain ships Shared Web Credentials (`webcredentials`)"
                )
            )
        }

        val hasAppclips = root["appclips"] != null
        if (hasAppclips) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.INFO,
                    code = AasaIssue.AasaIssueCode.APP_CLIPS_PRESENT,
                    message = "Domain ships App Clips (`appclips`)"
                )
            )
        }

        return ParseResult.Success(
            content = AppleAppSiteAssociation(
                applinks = applinks,
                hasWebcredentials = hasWebcredentials,
                hasAppclips = hasAppclips
            ),
            issues = issues
        )
    }

    private fun parseApplinks(
        applinks: JsonObject,
        issues: MutableList<AasaIssue>
    ): AppLinksSection {
        val apps = (applinks["apps"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

        if (apps.isNotEmpty()) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.WARNING,
                    code = AasaIssue.AasaIssueCode.NON_EMPTY_APPS_ARRAY,
                    message = "`applinks.apps` should be an empty array",
                    details = "Apple has required this to be empty for years; values here are ignored by iOS."
                )
            )
        }

        val detailsArray = applinks["details"] as? JsonArray
        if (detailsArray == null || detailsArray.isEmpty()) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.ERROR,
                    code = AasaIssue.AasaIssueCode.EMPTY_DETAILS,
                    message = "`applinks.details` is missing or empty",
                    details = "iOS needs at least one entry in details to register Universal Links."
                )
            )
        }

        val details = detailsArray
            ?.mapNotNull { (it as? JsonObject)?.let { obj -> parseDetail(obj, issues) } }
            ?: emptyList()

        return AppLinksSection(apps = apps, details = details)
    }

    private fun parseDetail(
        detail: JsonObject,
        issues: MutableList<AasaIssue>
    ): AppLinkDetail? {
        val legacyAppId = (detail["appID"] as? JsonPrimitive)?.contentOrNull
        val modernAppIds = (detail["appIDs"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

        val appIds = when {
            modernAppIds.isNotEmpty() -> modernAppIds
            legacyAppId != null -> listOf(legacyAppId)
            else -> emptyList()
        }
        if (appIds.isEmpty()) return null

        val usesLegacy = legacyAppId != null && modernAppIds.isEmpty()
        if (usesLegacy) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.WARNING,
                    code = AasaIssue.AasaIssueCode.LEGACY_SCHEMA,
                    message = "Detail uses legacy `appID` + `paths` schema",
                    details = "Consider migrating to `appIDs` + `components` for iOS 14+ features (query, fragment, exclude)."
                )
            )
        }

        appIds.forEach { id ->
            if (!isLikelyAppId(id)) {
                issues.add(
                    AasaIssue(
                        severity = AasaIssue.Severity.ERROR,
                        code = AasaIssue.AasaIssueCode.INVALID_APP_ID_FORMAT,
                        message = "App ID does not look like TEAM_ID.bundle.id format: $id",
                        details = "Expected something like ABCDE12345.com.example.app"
                    )
                )
            }
        }

        val paths = (detail["paths"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

        val components = (detail["components"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { obj -> parseComponent(obj) } }
            ?: emptyList()

        if (paths.isEmpty() && components.isEmpty()) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.WARNING,
                    code = AasaIssue.AasaIssueCode.MISSING_PATHS_AND_COMPONENTS,
                    message = "Detail for ${appIds.first()} has neither `paths` nor `components`",
                    details = "iOS will not match any URL for this entry."
                )
            )
        }

        return AppLinkDetail(
            appIDs = appIds,
            paths = paths,
            components = components,
            usesLegacySchema = usesLegacy
        )
    }

    /**
     * The `/` key is the canonical "path" field in iOS 14+ components but isn't
     * a valid Kotlin identifier, so we read the JSON manually instead of using
     * @Serializable.
     */
    private fun parseComponent(component: JsonObject): AasaPathComponent? {
        val path = (component["/"] as? JsonPrimitive)?.contentOrNull
        val query = (component["?"] as? JsonPrimitive)?.contentOrNull
        val fragment = (component["#"] as? JsonPrimitive)?.contentOrNull
        val exclude = (component["exclude"] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: false
        val caseSensitive = (component["caseSensitive"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            ?: true
        val percentEncoded = (component["percentEncoded"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            ?: true

        if (path == null && query == null && fragment == null) return null

        return AasaPathComponent(
            path = path,
            query = query,
            fragment = fragment,
            exclude = exclude,
            caseSensitive = caseSensitive,
            percentEncoded = percentEncoded
        )
    }

    /**
     * Cheap heuristic: TEAM_ID is 10 alphanumerics, then a dot, then a reverse-DNS
     * bundle id. Bundle ids legally allow letters / digits / hyphens / dots so we
     * only check the prefix shape and the dot — not perfect, but catches typos
     * like a missing team prefix without rejecting valid configs.
     */
    private fun isLikelyAppId(id: String): Boolean {
        val firstDot = id.indexOf('.')
        if (firstDot != 10) return false
        val team = id.substring(0, 10)
        return team.all { it.isLetterOrDigit() } && id.length > 11
    }

    sealed class ParseResult {
        data class Success(
            val content: AppleAppSiteAssociation,
            val issues: List<AasaIssue>
        ) : ParseResult()

        data class Error(
            val message: String,
            val cause: Throwable?,
            val issues: List<AasaIssue>
        ) : ParseResult()
    }
}
