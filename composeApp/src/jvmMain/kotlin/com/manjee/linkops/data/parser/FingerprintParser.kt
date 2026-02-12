package com.manjee.linkops.data.parser

/**
 * Parser for extracting SHA-256 certificate fingerprints from ADB output
 *
 * Parses the output of `dumpsys package <packageName>` to extract signing certificate
 * fingerprints. The relevant section looks like:
 * ```
 * Signing details:
 *   ...
 *   Signatures: [AB:CD:EF:01:23:...]
 * ```
 *
 * Also handles the output of `pm get-app-links` which contains:
 * ```
 * com.example.app:
 *   ID: ...
 *   Signatures: [AB:CD:EF:01:23:...]
 * ```
 */
class FingerprintParser {

    /**
     * Parse fingerprint from `pm get-app-links` output for a specific package
     *
     * @param output Raw output from the ADB command
     * @param packageName Package name to match
     * @return Extracted fingerprint or null if not found
     */
    fun parseFromGetAppLinks(output: String, packageName: String): String? {
        if (output.isBlank()) return null

        val lines = output.lines()
        var inTargetPackage = false

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("$packageName:") || trimmed == packageName) {
                inTargetPackage = true
                continue
            }

            if (inTargetPackage && !line.startsWith(" ") && !line.startsWith("\t") && trimmed.isNotEmpty()) {
                inTargetPackage = false
            }

            if (inTargetPackage && trimmed.startsWith("Signatures:")) {
                return extractFingerprintFromSignaturesLine(trimmed)
            }
        }

        return null
    }

    /**
     * Parse fingerprint from `dumpsys package` output
     *
     * @param output Raw output from `dumpsys package <pkg>`
     * @return Extracted SHA-256 fingerprint or null if not found
     */
    fun parseFromDumpsysPackage(output: String): String? {
        if (output.isBlank()) return null

        val lines = output.lines()
        var inSigningSection = false

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.contains("Signing details:") || trimmed.contains("signatures:")) {
                inSigningSection = true
                continue
            }

            if (inSigningSection) {
                if (trimmed.startsWith("Cert") && trimmed.contains("SHA-256")) {
                    return extractFingerprintFromCertLine(trimmed)
                }
                if (!line.startsWith(" ") && !line.startsWith("\t") && trimmed.isNotEmpty()
                    && !trimmed.startsWith("Cert") && !trimmed.startsWith("Subject")
                    && !trimmed.startsWith("Issuer") && !trimmed.startsWith("Serial")
                    && !trimmed.startsWith("Valid")
                ) {
                    inSigningSection = false
                }
            }
        }

        return null
    }

    private fun extractFingerprintFromSignaturesLine(line: String): String? {
        // Format: "Signatures: [AB:CD:EF:...]"
        val bracketStart = line.indexOf('[')
        val bracketEnd = line.indexOf(']')
        if (bracketStart < 0 || bracketEnd < 0) return null

        val content = line.substring(bracketStart + 1, bracketEnd).trim()
        return content.ifBlank { null }
    }

    private fun extractFingerprintFromCertLine(line: String): String? {
        // Format: "Cert SHA-256 digest: AB:CD:EF:..."
        val colonIndex = line.indexOf("digest:")
        if (colonIndex < 0) return null

        val fingerprint = line.substring(colonIndex + "digest:".length).trim()
        return fingerprint.ifBlank { null }
    }
}
