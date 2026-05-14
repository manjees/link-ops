package com.manjee.linkops.domain.model

/**
 * Complete verification diagnostics result for a device
 *
 * Combines per-domain verification status, certificate fingerprint comparison,
 * and root cause analysis into a single result.
 *
 * @param packageName The Android package name analyzed
 * @param deviceSerial The device serial used for analysis
 * @param domainResults Per-domain verification results
 * @param localFingerprint SHA256 certificate fingerprint extracted from the device
 */
data class VerificationDiagnostics(
    val packageName: String,
    val deviceSerial: String,
    val domainResults: List<DomainDiagnosticResult>,
    val localFingerprint: String?
) {
    val totalDomains: Int get() = domainResults.size
    val verifiedDomains: Int get() = domainResults.count { it.verificationState.isSuccessful }
    val failedDomains: Int get() = totalDomains - verifiedDomains
    val hasIssues: Boolean get() = domainResults.any { it.failureReasons.isNotEmpty() }
}

/**
 * Diagnostic result for a single domain
 *
 * @param domain The domain name
 * @param verificationState Device-reported verification state
 * @param fingerprintComparison Result of fingerprint comparison with assetlinks.json
 * @param assetLinksStatus Validation status of the domain's assetlinks.json
 * @param failureReasons Identified root causes for verification failure
 * @param suggestions Actionable suggestions to fix the issue
 */
data class DomainDiagnosticResult(
    val domain: String,
    val verificationState: VerificationState,
    val fingerprintComparison: FingerprintComparisonResult,
    val assetLinksStatus: AssetLinksStatus,
    val failureReasons: List<FailureReason>,
    val suggestions: List<String>
)

/**
 * Result of comparing local APK fingerprint against remote assetlinks.json fingerprints
 */
sealed class FingerprintComparisonResult {
    /**
     * Local fingerprint matches one in assetlinks.json
     */
    data object Match : FingerprintComparisonResult()

    /**
     * Local fingerprint does not match any in assetlinks.json
     *
     * @param localFingerprint SHA256 from the device
     * @param remoteFingerprints SHA256 values from assetlinks.json
     */
    data class Mismatch(
        val localFingerprint: String,
        val remoteFingerprints: List<String>
    ) : FingerprintComparisonResult()

    /**
     * No fingerprint available on the device (e.g., Android 11)
     */
    data object NoLocalFingerprint : FingerprintComparisonResult()

    /**
     * assetlinks.json could not be fetched or parsed
     */
    data object RemoteUnavailable : FingerprintComparisonResult()

    /**
     * assetlinks.json has no fingerprints for this package
     */
    data object NoRemoteFingerprint : FingerprintComparisonResult()

    val isMatch: Boolean get() = this is Match
}

/**
 * Status of a domain's assetlinks.json
 */
enum class AssetLinksStatus {
    VALID,
    NOT_FOUND,
    INVALID_JSON,
    INVALID_CONTENT_TYPE,
    NETWORK_ERROR,
    REDIRECT,
    NOT_CHECKED
}

/**
 * Identified failure reason for domain verification
 */
enum class FailureReason {
    ASSET_LINKS_MISSING,
    ASSET_LINKS_INVALID_JSON,
    ASSET_LINKS_INVALID_CONTENT_TYPE,
    ASSET_LINKS_NETWORK_ERROR,
    ASSET_LINKS_REDIRECT,
    FINGERPRINT_MISMATCH,
    PACKAGE_NOT_IN_ASSET_LINKS,
    DNS_FAILURE,
    UNKNOWN
}
