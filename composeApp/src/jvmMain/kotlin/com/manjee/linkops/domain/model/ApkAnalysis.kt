package com.manjee.linkops.domain.model

/**
 * Top-level result of inspecting an APK file. Optionally includes the
 * cross-reference of each autoVerify domain against the live `assetlinks.json`
 * we fetched (when the user opted in to that — which is the default).
 */
data class ApkAnalysisResult(
    val info: ApkInfo,
    val signatures: List<ApkSignature>,
    val intentFilters: List<ApkIntentFilter>,
    val linkDomains: List<ApkLinkDomain>,
    val domainVerifications: List<ApkDomainVerification> = emptyList()
)

/**
 * Identifying metadata pulled from the APK manifest.
 */
data class ApkInfo(
    val filePath: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val applicationLabel: String?
)

/**
 * One signing certificate. APKs can ship multiple (v1/v2/v3 schemes, debug + release
 * key rotation, etc.) so we surface them all rather than picking one — the user needs
 * to know which one Android will actually use to compare against `assetlinks.json`.
 */
data class ApkSignature(
    val sha256Fingerprint: String,
    val sha1Fingerprint: String?,
    val subjectDn: String?,
    val issuerDn: String?,
    val validFrom: Long?,
    val validTo: Long?
)

/**
 * One <intent-filter> declared in the manifest. Distinct from a domain — a single
 * filter can cover multiple schemes/hosts/paths simultaneously, so we keep the
 * lists rather than flattening prematurely.
 */
data class ApkIntentFilter(
    val componentName: String,
    val actions: List<String>,
    val categories: List<String>,
    val schemes: List<String>,
    val hosts: List<String>,
    val pathPatterns: List<ApkPathPattern>,
    val autoVerify: Boolean,
    val mimeTypes: List<String> = emptyList()
)

data class ApkPathPattern(
    val pattern: String,
    val type: PatternType
) {
    enum class PatternType { LITERAL, PREFIX, SUFFIX, GLOB, ADVANCED_GLOB }
}

/**
 * A normalized, deduplicated link domain extracted from intent filters. This is what
 * we feed into `assetlinks.json` validation — one entry per (host, autoVerify) pair,
 * scheme set merged.
 */
data class ApkLinkDomain(
    val host: String,
    val schemes: Set<String>,
    val autoVerify: Boolean
) {
    val isHttps: Boolean get() = "https" in schemes
    val isAppLinkCandidate: Boolean get() = autoVerify && isHttps
}

/**
 * Result of cross-checking one of the APK's autoVerify domains against the
 * `assetlinks.json` actually hosted at that domain. This is the headline value
 * of APK analysis — answers "would this build verify on a real device?"
 */
data class ApkDomainVerification(
    val domain: String,
    val assetLinksValidation: AssetLinksValidation,
    val fingerprintMatch: ApkFingerprintMatch
)

sealed class ApkFingerprintMatch {
    /** assetlinks.json contains every signature this APK ships with. */
    data object FullMatch : ApkFingerprintMatch()

    /** assetlinks.json contains at least one but not all signatures. */
    data class PartialMatch(
        val matchedFingerprints: List<String>,
        val unmatchedFingerprints: List<String>
    ) : ApkFingerprintMatch()

    /** assetlinks.json contains the package but none of this APK's signatures match. */
    data class NoMatch(
        val apkFingerprints: List<String>,
        val assetLinksFingerprints: List<String>
    ) : ApkFingerprintMatch()

    /** assetlinks.json doesn't even mention this package. */
    data object PackageNotDeclared : ApkFingerprintMatch()

    /** assetlinks.json couldn't be fetched / parsed — fingerprint check skipped. */
    data object AssetLinksUnavailable : ApkFingerprintMatch()
}
