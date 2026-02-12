package com.manjee.linkops.data.generator

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generates valid assetlinks.json content for Android App Links verification
 *
 * Output format follows Google's Digital Asset Links specification:
 * ```json
 * [{
 *   "relation": ["delegate_permission/common.handle_all_urls"],
 *   "target": {
 *     "namespace": "android_app",
 *     "package_name": "com.example.app",
 *     "sha256_cert_fingerprints": ["AB:CD:EF:..."]
 *   }
 * }]
 * ```
 */
class AssetLinksGenerator {
    private val json = Json {
        prettyPrint = true
    }

    /**
     * Generate assetlinks.json content for a single package with given fingerprints
     *
     * @param packageName Android application package name (e.g., "com.example.app")
     * @param sha256Fingerprints List of SHA-256 certificate fingerprints
     * @return Formatted JSON string representing the assetlinks.json content
     * @throws IllegalArgumentException if packageName is blank or fingerprints are empty
     */
    fun generate(packageName: String, sha256Fingerprints: List<String>): String {
        require(packageName.isNotBlank()) { "Package name must not be blank" }
        require(sha256Fingerprints.isNotEmpty()) { "At least one fingerprint is required" }

        val normalizedFingerprints = sha256Fingerprints.map { normalizeFingerprint(it) }

        val statements = listOf(
            AssetLinksStatementDto(
                relation = listOf(RELATION_HANDLE_ALL_URLS),
                target = AssetLinksTargetDto(
                    namespace = NAMESPACE_ANDROID_APP,
                    packageName = packageName,
                    sha256CertFingerprints = normalizedFingerprints
                )
            )
        )

        return json.encodeToString(statements)
    }

    /**
     * Normalize fingerprint to uppercase colon-separated hex format
     */
    private fun normalizeFingerprint(fingerprint: String): String {
        val hex = fingerprint.uppercase().replace(":", "").replace(" ", "")
        if (hex.length != 64) return fingerprint.uppercase()
        return hex.chunked(2).joinToString(":")
    }

    companion object {
        const val RELATION_HANDLE_ALL_URLS = "delegate_permission/common.handle_all_urls"
        const val NAMESPACE_ANDROID_APP = "android_app"
    }
}
