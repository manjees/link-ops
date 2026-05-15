package com.manjee.linkops.domain.model

/**
 * Result of validating an `apple-app-site-association` (AASA) file for a domain.
 * Mirrors [AssetLinksValidation] so the UI can render Android and iOS results
 * with the same shape.
 */
data class AasaValidation(
    val domain: String,
    /**
     * The exact URL that returned the AASA — useful because there are two valid
     * locations and the user needs to know which one was found.
     */
    val url: String,
    val status: AasaStatus,
    val issues: List<AasaIssue> = emptyList(),
    val content: AppleAppSiteAssociation? = null,
    val rawJson: String? = null,
    /**
     * True when the file was served from `/.well-known/apple-app-site-association`
     * (the iOS 13+ recommended location). False when only the legacy root path worked.
     */
    val servedFromWellKnown: Boolean = false
)

/**
 * Parsed AASA payload, normalized across the legacy and iOS 14+ schemas.
 *
 * MVP scope is `applinks` only — `webcredentials` and `appclips` are surfaced
 * as presence flags so the UI can hint at related capabilities without us
 * promising to validate them yet.
 */
data class AppleAppSiteAssociation(
    val applinks: AppLinksSection? = null,
    val hasWebcredentials: Boolean = false,
    val hasAppclips: Boolean = false
)

/**
 * The `applinks` block. iOS allows two coexisting shapes; this is the union.
 */
data class AppLinksSection(
    /**
     * Legacy `apps: []` array. Apple has required this to be empty for years
     * but old configs still ship with values — surfaced for visibility.
     */
    val apps: List<String> = emptyList(),
    val details: List<AppLinkDetail>
)

/**
 * Single entry inside `applinks.details`.
 *
 * Both schemas are normalized into this:
 * - Legacy: `appID` (singular) + `paths`
 * - iOS 14+: `appIDs` (plural) + `components`
 *
 * [usesLegacySchema] preserves the source so the UI can flag old configurations
 * worth migrating.
 */
data class AppLinkDetail(
    val appIDs: List<String>,
    val paths: List<String> = emptyList(),
    val components: List<AasaPathComponent> = emptyList(),
    val usesLegacySchema: Boolean = false
) {
    val hasAnyPattern: Boolean
        get() = paths.isNotEmpty() || components.isNotEmpty()
}

/**
 * iOS 14+ path component. Only the most common keys are modeled — the JSON spec
 * is open-ended and the goal here is to surface what the developer wrote, not
 * to evaluate it.
 */
data class AasaPathComponent(
    val path: String? = null,
    val query: String? = null,
    val fragment: String? = null,
    val exclude: Boolean = false,
    val caseSensitive: Boolean = true,
    val percentEncoded: Boolean = true
)

enum class AasaStatus {
    VALID,
    INVALID_JSON,
    NOT_FOUND,
    REDIRECT,
    NETWORK_ERROR,
    INVALID_CONTENT_TYPE,
    /**
     * The file exists and is valid JSON but contains no `applinks` section —
     * possible if the domain only ships `webcredentials` or `appclips`.
     */
    NO_APPLINKS_SECTION
}

data class AasaIssue(
    val severity: Severity,
    val code: AasaIssueCode,
    val message: String,
    val details: String? = null
) {
    enum class Severity { ERROR, WARNING, INFO }

    enum class AasaIssueCode {
        // Errors
        FILE_NOT_FOUND,
        INVALID_JSON_SYNTAX,
        MISSING_APPLINKS,
        EMPTY_DETAILS,
        INVALID_APP_ID_FORMAT,
        NETWORK_TIMEOUT,
        NETWORK_ERROR,
        SSL_ERROR,

        // Warnings
        REDIRECT_DETECTED,
        WRONG_CONTENT_TYPE,
        ROOT_PATH_FALLBACK,
        MISSING_PATHS_AND_COMPONENTS,
        LEGACY_SCHEMA,
        NON_EMPTY_APPS_ARRAY,

        // Info
        WEB_CREDENTIALS_PRESENT,
        APP_CLIPS_PRESENT
    }
}
