package com.manjee.linkops.domain.usecase.apk

import com.manjee.linkops.domain.model.ApkAnalysisResult
import com.manjee.linkops.domain.model.ApkDomainVerification
import com.manjee.linkops.domain.model.ApkFingerprintMatch
import com.manjee.linkops.domain.model.ApkLinkDomain
import com.manjee.linkops.domain.model.ApkSignature
import com.manjee.linkops.domain.model.AssetLinksContent
import com.manjee.linkops.domain.repository.ApkAnalysisRepository
import com.manjee.linkops.domain.repository.AssetLinksRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * The headline use case for APK Inspector. Pulls the manifest + signatures out of
 * the APK and then, for every host the APK marks as autoVerify https, fetches the
 * live `assetlinks.json` and checks whether the APK's signing fingerprints actually
 * appear there — answering "would this build verify on a real device?" without
 * needing a device at all.
 *
 * Domains are validated in parallel because most of the time is spent waiting on
 * remote HTTPS round-trips, and they're independent of each other.
 */
class AnalyzeApkAndValidateLinksUseCase(
    private val apkAnalysisRepository: ApkAnalysisRepository,
    private val assetLinksRepository: AssetLinksRepository
) {
    suspend operator fun invoke(apkFile: File): Result<ApkAnalysisResult> = runCatching {
        val analysis = apkAnalysisRepository.analyzeApk(apkFile).getOrThrow()
        val verifications = verifyAutoVerifyDomains(analysis.linkDomains, analysis.signatures)
        analysis.copy(domainVerifications = verifications)
    }

    private suspend fun verifyAutoVerifyDomains(
        domains: List<ApkLinkDomain>,
        signatures: List<ApkSignature>
    ): List<ApkDomainVerification> = coroutineScope {
        val candidates = domains.filter { it.isAppLinkCandidate }
        if (candidates.isEmpty()) return@coroutineScope emptyList()

        val apkFingerprints = signatures.map { it.sha256Fingerprint }

        candidates
            .map { domain ->
                async { verifyOne(domain, apkFingerprints) }
            }
            .map { it.await() }
    }

    private suspend fun verifyOne(
        domain: ApkLinkDomain,
        apkFingerprints: List<String>
    ): ApkDomainVerification {
        val validation = assetLinksRepository.validateAssetLinks(domain.host).getOrNull()
        val content = validation?.content
        val match = computeMatch(content, apkFingerprints, packageName = null)

        return ApkDomainVerification(
            domain = domain.host,
            assetLinksValidation = validation
                ?: error("Repository returned null without throwing — should not happen"),
            fingerprintMatch = match
        )
    }

    /**
     * @param packageName intentionally unused for now — we match purely on the SHA-256
     * fingerprint because that's what the live verifier on Android actually checks.
     * If we later want to also assert the package name lines up across all statements
     * we can wire it in here without touching call sites.
     */
    private fun computeMatch(
        content: AssetLinksContent?,
        apkFingerprints: List<String>,
        @Suppress("UNUSED_PARAMETER") packageName: String?
    ): ApkFingerprintMatch {
        if (content == null) return ApkFingerprintMatch.AssetLinksUnavailable

        val assetLinksFps = content.statements
            .flatMap { it.target.sha256CertFingerprints }
            .map { normalize(it) }
            .distinct()

        if (assetLinksFps.isEmpty()) return ApkFingerprintMatch.PackageNotDeclared

        val normalizedApk = apkFingerprints.map { normalize(it) }
        val matched = normalizedApk.filter { it in assetLinksFps }
        val unmatched = normalizedApk.filterNot { it in assetLinksFps }

        return when {
            matched.size == normalizedApk.size -> ApkFingerprintMatch.FullMatch
            matched.isNotEmpty() -> ApkFingerprintMatch.PartialMatch(matched, unmatched)
            else -> ApkFingerprintMatch.NoMatch(
                apkFingerprints = normalizedApk,
                assetLinksFingerprints = assetLinksFps
            )
        }
    }

    /**
     * Strip colons / whitespace / case so apkFingerprints (XX:XX:XX...) and
     * assetlinks fingerprints (often lowercase, sometimes no colons) compare equal.
     */
    private fun normalize(fp: String): String =
        fp.replace(":", "").replace(" ", "").uppercase()
}
