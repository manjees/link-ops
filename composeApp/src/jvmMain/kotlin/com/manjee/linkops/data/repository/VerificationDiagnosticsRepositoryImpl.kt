package com.manjee.linkops.data.repository

import com.manjee.linkops.data.analyzer.CertificateFingerprintComparator
import com.manjee.linkops.data.analyzer.VerificationFailureAnalyzer
import com.manjee.linkops.data.parser.DumpsysParser
import com.manjee.linkops.data.parser.GetAppLinksParser
import com.manjee.linkops.data.strategy.AdbCommandStrategyFactory
import com.manjee.linkops.domain.model.*
import com.manjee.linkops.domain.repository.AssetLinksRepository
import com.manjee.linkops.domain.repository.VerificationDiagnosticsRepository
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Implementation of VerificationDiagnosticsRepository
 *
 * Orchestrates device ADB queries, assetlinks.json fetching,
 * fingerprint comparison, and failure analysis.
 */
class VerificationDiagnosticsRepositoryImpl(
    private val adbExecutor: AdbShellExecutor,
    private val strategyFactory: AdbCommandStrategyFactory,
    private val getAppLinksParser: GetAppLinksParser,
    private val dumpsysParser: DumpsysParser,
    private val assetLinksRepository: AssetLinksRepository,
    private val fingerprintComparator: CertificateFingerprintComparator,
    private val failureAnalyzer: VerificationFailureAnalyzer
) : VerificationDiagnosticsRepository {

    companion object {
        private const val ANDROID_12_SDK_LEVEL = 31
    }

    override suspend fun analyzeVerification(
        deviceSerial: String,
        packageName: String
    ): Result<VerificationDiagnostics> = runCatching {
        // 1. Get device SDK level
        val sdkLevel = getSdkLevel(deviceSerial)
            .getOrElse { throw it }

        // 2. Get app links (including fingerprint on Android 12+)
        val strategy = strategyFactory.create(sdkLevel)
        val command = strategy.getAppLinksCommand(packageName)
        val appLinks = adbExecutor.executeOnDevice(deviceSerial, command)
            .mapCatching { output ->
                if (sdkLevel >= ANDROID_12_SDK_LEVEL) getAppLinksParser.parse(output)
                else dumpsysParser.parse(output)
            }
            .getOrElse { throw it }

        // 3. Find the target package
        val appLink = appLinks.find { it.packageName == packageName }
            ?: throw IllegalStateException("Package $packageName not found in app links")

        // 4. Extract local fingerprint (available on Android 12+ from Signatures field)
        val localFingerprint = appLink.domains.firstOrNull()?.fingerprint

        // 5. For each domain, fetch assetlinks.json and analyze (concurrently)
        val domainResults = coroutineScope {
            appLink.domains.map { domainVerification ->
                async {
                    analyzeDomain(
                        domainVerification = domainVerification,
                        packageName = packageName,
                        localFingerprint = localFingerprint
                    )
                }
            }.awaitAll()
        }

        VerificationDiagnostics(
            packageName = packageName,
            deviceSerial = deviceSerial,
            domainResults = domainResults,
            localFingerprint = localFingerprint
        )
    }

    private suspend fun analyzeDomain(
        domainVerification: DomainVerification,
        packageName: String,
        localFingerprint: String?
    ): DomainDiagnosticResult {
        val domain = domainVerification.domain

        // Fetch and validate assetlinks.json
        val validationResult = assetLinksRepository.validateAssetLinks(domain)

        val validation = validationResult.getOrNull()
        val assetLinksStatus = mapValidationStatus(validation?.status)
        val assetLinksContent = validation?.content

        // Compare fingerprints
        val fingerprintResult = fingerprintComparator.compare(
            localFingerprint = localFingerprint,
            packageName = packageName,
            assetLinksContent = assetLinksContent
        )

        // Analyze failures
        val (failureReasons, suggestions) = failureAnalyzer.analyze(
            verificationState = domainVerification.verificationState,
            fingerprintResult = fingerprintResult,
            assetLinksStatus = assetLinksStatus,
            packageName = packageName,
            domain = domain
        )

        return DomainDiagnosticResult(
            domain = domain,
            verificationState = domainVerification.verificationState,
            fingerprintComparison = fingerprintResult,
            assetLinksStatus = assetLinksStatus,
            failureReasons = failureReasons,
            suggestions = suggestions
        )
    }

    private fun mapValidationStatus(status: ValidationStatus?): AssetLinksStatus {
        return when (status) {
            ValidationStatus.VALID -> AssetLinksStatus.VALID
            ValidationStatus.NOT_FOUND -> AssetLinksStatus.NOT_FOUND
            ValidationStatus.INVALID_JSON -> AssetLinksStatus.INVALID_JSON
            ValidationStatus.NETWORK_ERROR -> AssetLinksStatus.NETWORK_ERROR
            ValidationStatus.REDIRECT -> AssetLinksStatus.REDIRECT
            ValidationStatus.FINGERPRINT_MISMATCH -> AssetLinksStatus.INVALID_JSON
            ValidationStatus.INVALID_CONTENT_TYPE -> AssetLinksStatus.INVALID_CONTENT_TYPE
            null -> AssetLinksStatus.NETWORK_ERROR
        }
    }

    private suspend fun getSdkLevel(deviceSerial: String): Result<Int> {
        return adbExecutor
            .executeOnDevice(deviceSerial, "getprop ro.build.version.sdk")
            .mapCatching { output ->
                output.trim().toIntOrNull()
                    ?: throw IllegalStateException("Failed to parse SDK level: $output")
            }
    }
}
