package com.manjee.linkops.data.analyzer

import com.manjee.linkops.domain.model.*

/**
 * Analyzes verification failures and provides root cause identification with actionable suggestions
 */
class VerificationFailureAnalyzer {

    /**
     * Analyze a domain's verification result and identify failure reasons
     *
     * @param verificationState Device-reported verification state
     * @param fingerprintResult Result of fingerprint comparison
     * @param assetLinksStatus Status of the assetlinks.json fetch
     * @param packageName The package name being analyzed
     * @param domain The domain being analyzed
     * @return Pair of failure reasons and suggestions
     */
    fun analyze(
        verificationState: VerificationState,
        fingerprintResult: FingerprintComparisonResult,
        assetLinksStatus: AssetLinksStatus,
        packageName: String,
        domain: String
    ): Pair<List<FailureReason>, List<String>> {
        // If verification is successful, no failures to report
        if (verificationState.isSuccessful) {
            return emptyList<FailureReason>() to emptyList()
        }

        val reasons = mutableListOf<FailureReason>()
        val suggestions = mutableListOf<String>()

        // Check assetlinks.json availability
        analyzeAssetLinksStatus(assetLinksStatus, domain, reasons, suggestions)

        // Check fingerprint match
        analyzeFingerprintResult(fingerprintResult, packageName, domain, reasons, suggestions)

        // If no specific reason found, mark as unknown
        if (reasons.isEmpty()) {
            reasons.add(FailureReason.UNKNOWN)
            suggestions.add(
                "Verification failed for unknown reason. " +
                    "Try running 'adb shell pm verify-app-links --re-verify $packageName' " +
                    "and check logcat for details."
            )
        }

        return reasons to suggestions
    }

    private fun analyzeAssetLinksStatus(
        status: AssetLinksStatus,
        domain: String,
        reasons: MutableList<FailureReason>,
        suggestions: MutableList<String>
    ) {
        when (status) {
            AssetLinksStatus.NOT_FOUND -> {
                reasons.add(FailureReason.ASSET_LINKS_MISSING)
                suggestions.add(
                    "Create assetlinks.json at https://$domain/.well-known/assetlinks.json " +
                        "with the correct package name and SHA256 fingerprint."
                )
            }
            AssetLinksStatus.INVALID_JSON -> {
                reasons.add(FailureReason.ASSET_LINKS_INVALID_JSON)
                suggestions.add(
                    "Fix the JSON syntax in https://$domain/.well-known/assetlinks.json. " +
                        "Use the Digital Asset Links validator to check the format."
                )
            }
            AssetLinksStatus.INVALID_CONTENT_TYPE -> {
                reasons.add(FailureReason.ASSET_LINKS_INVALID_CONTENT_TYPE)
                suggestions.add(
                    "The assetlinks.json at $domain is served with the wrong Content-Type. " +
                        "Configure the server to return 'application/json' so Android's " +
                        "verifier accepts the response."
                )
            }
            AssetLinksStatus.NETWORK_ERROR -> {
                reasons.add(FailureReason.ASSET_LINKS_NETWORK_ERROR)
                suggestions.add(
                    "Check that $domain is reachable and the server responds to HTTPS requests. " +
                        "Ensure the SSL certificate is valid."
                )
            }
            AssetLinksStatus.REDIRECT -> {
                reasons.add(FailureReason.ASSET_LINKS_REDIRECT)
                suggestions.add(
                    "The assetlinks.json at $domain is served via redirect, which is not allowed. " +
                        "Serve the file directly at https://$domain/.well-known/assetlinks.json " +
                        "without any redirects."
                )
            }
            AssetLinksStatus.VALID, AssetLinksStatus.NOT_CHECKED -> { /* no issue */ }
        }
    }

    private fun analyzeFingerprintResult(
        result: FingerprintComparisonResult,
        packageName: String,
        domain: String,
        reasons: MutableList<FailureReason>,
        suggestions: MutableList<String>
    ) {
        when (result) {
            is FingerprintComparisonResult.Mismatch -> {
                reasons.add(FailureReason.FINGERPRINT_MISMATCH)
                suggestions.add(
                    "Certificate fingerprint mismatch. " +
                        "Update the sha256_cert_fingerprints in " +
                        "https://$domain/.well-known/assetlinks.json " +
                        "to match the APK signing certificate: ${result.localFingerprint}"
                )
            }
            is FingerprintComparisonResult.NoRemoteFingerprint -> {
                reasons.add(FailureReason.PACKAGE_NOT_IN_ASSET_LINKS)
                suggestions.add(
                    "Package $packageName is not declared in " +
                        "https://$domain/.well-known/assetlinks.json. " +
                        "Add a statement with the correct package name and fingerprint."
                )
            }
            is FingerprintComparisonResult.Match,
            is FingerprintComparisonResult.NoLocalFingerprint,
            is FingerprintComparisonResult.RemoteUnavailable -> { /* no additional issue */ }
        }
    }
}
