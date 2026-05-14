package com.manjee.linkops.di

import com.manjee.linkops.data.analyzer.CertificateFingerprintComparator
import com.manjee.linkops.data.analyzer.VerificationFailureAnalyzer
import com.manjee.linkops.data.generator.AssetLinksGenerator
import com.manjee.linkops.data.mapper.DeviceMapper
import com.manjee.linkops.data.mapper.ParameterSubstituter
import com.manjee.linkops.data.mapper.ScenarioMapper
import com.manjee.linkops.data.parser.AmStartOutputParser
import com.manjee.linkops.data.parser.AssetLinksParser
import com.manjee.linkops.data.parser.DumpsysParser
import com.manjee.linkops.data.parser.FingerprintParser
import com.manjee.linkops.data.parser.GetAppLinksParser
import com.manjee.linkops.data.parser.IntentFilterParser
import com.manjee.linkops.data.parser.IntentPayloadParser
import com.manjee.linkops.data.parser.LogcatParser
import com.manjee.linkops.data.parser.ManifestParser
import com.manjee.linkops.data.repository.AppLinkRepositoryImpl
import com.manjee.linkops.data.repository.AssetLinksRepositoryImpl
import com.manjee.linkops.data.repository.BatchTestRepositoryImpl
import com.manjee.linkops.data.repository.CollisionRepositoryImpl
import com.manjee.linkops.data.repository.DeviceRepositoryImpl
import com.manjee.linkops.data.repository.FavoriteRepositoryImpl
import com.manjee.linkops.data.repository.IntentPayloadRepositoryImpl
import com.manjee.linkops.data.repository.LocalHostingRepositoryImpl
import com.manjee.linkops.data.repository.LogStreamRepositoryImpl
import com.manjee.linkops.data.repository.ManifestRepositoryImpl
import com.manjee.linkops.data.repository.SettingsRepositoryImpl
import com.manjee.linkops.data.repository.VerificationDiagnosticsRepositoryImpl
import com.manjee.linkops.data.strategy.AdbCommandStrategyFactory
import com.manjee.linkops.domain.repository.AppLinkRepository
import com.manjee.linkops.domain.repository.AssetLinksRepository
import com.manjee.linkops.domain.repository.BatchTestRepository
import com.manjee.linkops.domain.repository.CollisionRepository
import com.manjee.linkops.domain.repository.DeviceRepository
import com.manjee.linkops.domain.repository.FavoriteRepository
import com.manjee.linkops.domain.repository.IntentPayloadRepository
import com.manjee.linkops.domain.repository.LocalHostingRepository
import com.manjee.linkops.domain.repository.LogStreamRepository
import com.manjee.linkops.domain.repository.ManifestRepository
import com.manjee.linkops.domain.repository.SettingsRepository
import com.manjee.linkops.domain.repository.VerificationDiagnosticsRepository
import com.manjee.linkops.domain.usecase.applink.FireIntentUseCase
import com.manjee.linkops.domain.usecase.applink.ForceReverifyUseCase
import com.manjee.linkops.domain.usecase.applink.GetAppLinksUseCase
import com.manjee.linkops.domain.usecase.batchtest.ExecuteBatchTestUseCase
import com.manjee.linkops.domain.usecase.batchtest.ExportScenarioUseCase
import com.manjee.linkops.domain.usecase.batchtest.ImportScenarioUseCase
import com.manjee.linkops.domain.usecase.batchtest.ResolveTemplateUrisUseCase
import com.manjee.linkops.domain.usecase.device.DetectDevicesUseCase
import com.manjee.linkops.domain.usecase.device.RefreshDevicesUseCase
import com.manjee.linkops.domain.usecase.diagnostics.AnalyzeVerificationUseCase
import com.manjee.linkops.domain.usecase.diagnostics.DetectCollisionsUseCase
import com.manjee.linkops.domain.usecase.diagnostics.ValidateAssetLinksUseCase
import com.manjee.linkops.domain.usecase.favorite.AddFavoriteUseCase
import com.manjee.linkops.domain.usecase.favorite.ObserveFavoritesUseCase
import com.manjee.linkops.domain.usecase.favorite.RemoveFavoriteUseCase
import com.manjee.linkops.domain.usecase.localhosting.ExtractFingerprintUseCase
import com.manjee.linkops.domain.usecase.localhosting.GenerateAssetLinksUseCase
import com.manjee.linkops.domain.usecase.localhosting.RunVerificationWorkflowUseCase
import com.manjee.linkops.domain.usecase.localhosting.StartLocalServerUseCase
import com.manjee.linkops.domain.usecase.localhosting.StopLocalServerUseCase
import com.manjee.linkops.domain.usecase.logstream.ObserveLogStreamUseCase
import com.manjee.linkops.domain.usecase.settings.ObserveSettingsUseCase
import com.manjee.linkops.domain.usecase.settings.UpdateSettingsUseCase
import com.manjee.linkops.domain.usecase.sniffer.CaptureIntentPayloadUseCase
import com.manjee.linkops.domain.usecase.sniffer.ComparePayloadsUseCase
import com.manjee.linkops.domain.usecase.manifest.AnalyzeManifestUseCase
import com.manjee.linkops.domain.usecase.topology.BuildTopologyTreeUseCase
import com.manjee.linkops.domain.usecase.manifest.GetInstalledPackagesUseCase
import com.manjee.linkops.domain.usecase.manifest.SearchPackagesUseCase
import com.manjee.linkops.domain.usecase.manifest.TestDeepLinkUseCase
import com.manjee.linkops.domain.model.IntentFiredEvent
import com.manjee.linkops.infrastructure.adb.AdbBinaryManager
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor
import com.manjee.linkops.infrastructure.network.AssetLinksClient
import com.manjee.linkops.infrastructure.qr.QrCodeGenerator
import com.manjee.linkops.infrastructure.server.AssetLinksServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple dependency injection container
 * Provides singleton instances of all dependencies
 */
object AppContainer {

    // Infrastructure - ADB
    val adbBinaryManager: AdbBinaryManager by lazy {
        AdbBinaryManager(settingsRepository)
    }

    val adbShellExecutor: AdbShellExecutor by lazy {
        AdbShellExecutor(adbBinaryManager)
    }

    // Infrastructure - QR
    val qrCodeGenerator: QrCodeGenerator by lazy {
        QrCodeGenerator()
    }

    // Infrastructure - Network
    private val assetLinksClient: AssetLinksClient by lazy {
        AssetLinksClient()
    }

    // Infrastructure - Server
    private val assetLinksServer: AssetLinksServer by lazy {
        AssetLinksServer()
    }

    // Data - Mappers & Parsers
    private val deviceMapper: DeviceMapper by lazy {
        DeviceMapper()
    }

    private val getAppLinksParser: GetAppLinksParser by lazy {
        GetAppLinksParser()
    }

    private val dumpsysParser: DumpsysParser by lazy {
        DumpsysParser()
    }

    private val assetLinksParser: AssetLinksParser by lazy {
        AssetLinksParser()
    }

    private val manifestParser: ManifestParser by lazy {
        ManifestParser()
    }

    private val logcatParser: LogcatParser by lazy {
        LogcatParser()
    }

    private val amStartOutputParser: AmStartOutputParser by lazy {
        AmStartOutputParser()
    }

    private val scenarioMapper: ScenarioMapper by lazy {
        ScenarioMapper()
    }

    private val parameterSubstituter: ParameterSubstituter by lazy {
        ParameterSubstituter()
    }

    private val intentFilterParser: IntentFilterParser by lazy {
        IntentFilterParser()
    }

    private val fingerprintParser: FingerprintParser by lazy {
        FingerprintParser()
    }

    private val intentPayloadParser: IntentPayloadParser by lazy {
        IntentPayloadParser()
    }

    // Data - Generators
    private val assetLinksGenerator: AssetLinksGenerator by lazy {
        AssetLinksGenerator()
    }

    // Data - Strategy
    private val strategyFactory: AdbCommandStrategyFactory by lazy {
        AdbCommandStrategyFactory()
    }

    // Data - Analyzers
    private val certificateFingerprintComparator: CertificateFingerprintComparator by lazy {
        CertificateFingerprintComparator()
    }

    private val verificationFailureAnalyzer: VerificationFailureAnalyzer by lazy {
        VerificationFailureAnalyzer()
    }

    // Repositories
    val deviceRepository: DeviceRepository by lazy {
        DeviceRepositoryImpl(adbShellExecutor, deviceMapper, settingsRepository)
    }

    val appLinkRepository: AppLinkRepository by lazy {
        AppLinkRepositoryImpl(
            adbShellExecutor,
            strategyFactory,
            getAppLinksParser,
            dumpsysParser
        )
    }

    val assetLinksRepository: AssetLinksRepository by lazy {
        AssetLinksRepositoryImpl(assetLinksClient, assetLinksParser)
    }

    val manifestRepository: ManifestRepository by lazy {
        ManifestRepositoryImpl(adbShellExecutor, manifestParser)
    }

    val verificationDiagnosticsRepository: VerificationDiagnosticsRepository by lazy {
        VerificationDiagnosticsRepositoryImpl(
            adbExecutor = adbShellExecutor,
            strategyFactory = strategyFactory,
            getAppLinksParser = getAppLinksParser,
            dumpsysParser = dumpsysParser,
            assetLinksRepository = assetLinksRepository,
            fingerprintComparator = certificateFingerprintComparator,
            failureAnalyzer = verificationFailureAnalyzer
        )
    }

    val localHostingRepository: LocalHostingRepository by lazy {
        LocalHostingRepositoryImpl(
            assetLinksServer = assetLinksServer,
            assetLinksGenerator = assetLinksGenerator,
            adbExecutor = adbShellExecutor,
            fingerprintParser = fingerprintParser,
            strategyFactory = strategyFactory
        )
    }

    val favoriteRepository: FavoriteRepository by lazy {
        FavoriteRepositoryImpl()
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl()
    }

    val logStreamRepository: LogStreamRepository by lazy {
        LogStreamRepositoryImpl(adbShellExecutor, logcatParser)
    }

    val batchTestRepository: BatchTestRepository by lazy {
        BatchTestRepositoryImpl(adbShellExecutor, amStartOutputParser, scenarioMapper)
    }

    val collisionRepository: CollisionRepository by lazy {
        CollisionRepositoryImpl(adbShellExecutor, intentFilterParser)
    }

    val intentPayloadRepository: IntentPayloadRepository by lazy {
        IntentPayloadRepositoryImpl(adbShellExecutor, intentPayloadParser)
    }

    // UseCases - Device
    val detectDevicesUseCase: DetectDevicesUseCase by lazy {
        DetectDevicesUseCase(deviceRepository)
    }

    val refreshDevicesUseCase: RefreshDevicesUseCase by lazy {
        RefreshDevicesUseCase(deviceRepository)
    }

    // UseCases - App Links
    val getAppLinksUseCase: GetAppLinksUseCase by lazy {
        GetAppLinksUseCase(appLinkRepository)
    }

    val fireIntentUseCase: FireIntentUseCase by lazy {
        FireIntentUseCase(appLinkRepository)
    }

    val forceReverifyUseCase: ForceReverifyUseCase by lazy {
        ForceReverifyUseCase(appLinkRepository)
    }

    // UseCases - Diagnostics
    val validateAssetLinksUseCase: ValidateAssetLinksUseCase by lazy {
        ValidateAssetLinksUseCase(assetLinksRepository)
    }

    val analyzeVerificationUseCase: AnalyzeVerificationUseCase by lazy {
        AnalyzeVerificationUseCase(verificationDiagnosticsRepository)
    }

    val detectCollisionsUseCase: DetectCollisionsUseCase by lazy {
        DetectCollisionsUseCase(collisionRepository)
    }

    // UseCases - Manifest
    val analyzeManifestUseCase: AnalyzeManifestUseCase by lazy {
        AnalyzeManifestUseCase(manifestRepository)
    }

    val getInstalledPackagesUseCase: GetInstalledPackagesUseCase by lazy {
        GetInstalledPackagesUseCase(manifestRepository)
    }

    val searchPackagesUseCase: SearchPackagesUseCase by lazy {
        SearchPackagesUseCase(manifestRepository)
    }

    val testDeepLinkUseCase: TestDeepLinkUseCase by lazy {
        TestDeepLinkUseCase(manifestRepository)
    }

    // UseCases - Local Hosting
    val startLocalServerUseCase: StartLocalServerUseCase by lazy {
        StartLocalServerUseCase(localHostingRepository)
    }

    val stopLocalServerUseCase: StopLocalServerUseCase by lazy {
        StopLocalServerUseCase(localHostingRepository)
    }

    val generateAssetLinksUseCase: GenerateAssetLinksUseCase by lazy {
        GenerateAssetLinksUseCase(localHostingRepository)
    }

    val extractFingerprintUseCase: ExtractFingerprintUseCase by lazy {
        ExtractFingerprintUseCase(localHostingRepository)
    }

    val runVerificationWorkflowUseCase: RunVerificationWorkflowUseCase by lazy {
        RunVerificationWorkflowUseCase(localHostingRepository)
    }

    // UseCases - Topology
    val buildTopologyTreeUseCase: BuildTopologyTreeUseCase by lazy {
        BuildTopologyTreeUseCase()
    }

    // UseCases - Favorite
    val observeFavoritesUseCase: ObserveFavoritesUseCase by lazy {
        ObserveFavoritesUseCase(favoriteRepository)
    }

    val addFavoriteUseCase: AddFavoriteUseCase by lazy {
        AddFavoriteUseCase(favoriteRepository)
    }

    val removeFavoriteUseCase: RemoveFavoriteUseCase by lazy {
        RemoveFavoriteUseCase(favoriteRepository)
    }

    // UseCases - Settings
    val observeSettingsUseCase: ObserveSettingsUseCase by lazy {
        ObserveSettingsUseCase(settingsRepository)
    }

    val updateSettingsUseCase: UpdateSettingsUseCase by lazy {
        UpdateSettingsUseCase(settingsRepository)
    }

    // UseCases - Log Stream
    val observeLogStreamUseCase: ObserveLogStreamUseCase by lazy {
        ObserveLogStreamUseCase(logStreamRepository)
    }

    // UseCases - Batch Test
    val executeBatchTestUseCase: ExecuteBatchTestUseCase by lazy {
        ExecuteBatchTestUseCase(batchTestRepository)
    }

    val exportScenarioUseCase: ExportScenarioUseCase by lazy {
        ExportScenarioUseCase(batchTestRepository)
    }

    val importScenarioUseCase: ImportScenarioUseCase by lazy {
        ImportScenarioUseCase(batchTestRepository)
    }

    val resolveTemplateUrisUseCase: ResolveTemplateUrisUseCase by lazy {
        ResolveTemplateUrisUseCase(parameterSubstituter)
    }

    // UseCases - Intent Sniffer
    val captureIntentPayloadUseCase: CaptureIntentPayloadUseCase by lazy {
        CaptureIntentPayloadUseCase(intentPayloadRepository)
    }

    val comparePayloadsUseCase: ComparePayloadsUseCase by lazy {
        ComparePayloadsUseCase()
    }

    // Events - Intent Sniffer
    private val _intentFiredEvent = MutableSharedFlow<IntentFiredEvent>(extraBufferCapacity = 1)
    val intentFiredEvent: SharedFlow<IntentFiredEvent> = _intentFiredEvent.asSharedFlow()

    /**
     * Emits an intent fired event for auto-capture in Intent Sniffer
     */
    fun emitIntentFired(event: IntentFiredEvent) {
        _intentFiredEvent.tryEmit(event)
    }
}
