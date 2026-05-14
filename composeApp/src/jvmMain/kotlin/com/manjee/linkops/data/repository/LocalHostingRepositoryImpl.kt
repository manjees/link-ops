package com.manjee.linkops.data.repository

import com.manjee.linkops.data.generator.AssetLinksGenerator
import com.manjee.linkops.data.parser.FingerprintParser
import com.manjee.linkops.data.strategy.AdbCommandStrategyFactory
import com.manjee.linkops.domain.model.LocalHostingConfig
import com.manjee.linkops.domain.model.ServerStatus
import com.manjee.linkops.domain.model.VerificationStep
import com.manjee.linkops.domain.model.VerificationStep.StepStatus
import com.manjee.linkops.domain.repository.LocalHostingRepository
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor
import com.manjee.linkops.infrastructure.server.AssetLinksServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of LocalHostingRepository
 *
 * Coordinates between the infrastructure server, ADB executor, and data parsers
 * to provide local hosting and verification workflow functionality.
 */
class LocalHostingRepositoryImpl(
    private val assetLinksServer: AssetLinksServer,
    private val assetLinksGenerator: AssetLinksGenerator,
    private val adbExecutor: AdbShellExecutor,
    private val fingerprintParser: FingerprintParser,
    private val strategyFactory: AdbCommandStrategyFactory
) : LocalHostingRepository {

    companion object {
        private const val ANDROID_12_SDK_LEVEL = 31
        private const val VERIFICATION_POLL_DELAY_MS = 2000L
        private const val VERIFICATION_MAX_POLLS = 15
    }

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)

    override fun observeServerStatus(): Flow<ServerStatus> = _serverStatus.asStateFlow()

    override suspend fun startServer(config: LocalHostingConfig): Result<Unit> {
        return runCatching {
            _serverStatus.value = ServerStatus.STARTING

            if (!AssetLinksServer.isPortAvailable(config.host, config.port)) {
                _serverStatus.value = ServerStatus.ERROR
                throw IllegalStateException("Port ${config.port} is already in use on ${config.host}")
            }

            val assetLinksJson = assetLinksGenerator.generate(
                config.packageName,
                config.sha256Fingerprints
            )

            assetLinksServer.start(config.host, config.port, assetLinksJson)
            _serverStatus.value = ServerStatus.RUNNING
        }.onFailure {
            _serverStatus.value = ServerStatus.ERROR
        }
    }

    override suspend fun stopServer(): Result<Unit> {
        return runCatching {
            _serverStatus.value = ServerStatus.STOPPING
            assetLinksServer.stop()
            _serverStatus.value = ServerStatus.STOPPED
        }.onFailure {
            _serverStatus.value = ServerStatus.ERROR
        }
    }

    override suspend fun generateAssetLinksJson(
        packageName: String,
        sha256Fingerprints: List<String>
    ): Result<String> {
        return runCatching {
            assetLinksGenerator.generate(packageName, sha256Fingerprints)
        }
    }

    override suspend fun extractFingerprint(
        deviceSerial: String,
        packageName: String
    ): Result<String> {
        val sdkLevel = getSdkLevel(deviceSerial)
            .getOrElse { return Result.failure(it) }

        // Try pm get-app-links first (Android 12+)
        if (sdkLevel >= ANDROID_12_SDK_LEVEL) {
            val strategy = strategyFactory.create(sdkLevel)
            val command = strategy.getAppLinksCommand(packageName)
            val getAppLinksResult = adbExecutor.executeOnDevice(deviceSerial, command)
            if (getAppLinksResult.isSuccess) {
                val fingerprint = fingerprintParser.parseFromGetAppLinks(
                    getAppLinksResult.getOrThrow(),
                    packageName
                )
                if (fingerprint != null) {
                    return Result.success(fingerprint)
                }
            }
        }

        // Fallback to dumpsys package
        val dumpsysResult = adbExecutor.executeOnDevice(
            deviceSerial,
            "dumpsys package $packageName"
        )
        return dumpsysResult.mapCatching { output ->
            fingerprintParser.parseFromDumpsysPackage(output)
                ?: throw IllegalStateException(
                    "Could not extract fingerprint for $packageName. " +
                        "Ensure the package is installed on the device."
                )
        }
    }

    override fun runVerificationWorkflow(
        deviceSerial: String,
        packageName: String,
        config: LocalHostingConfig
    ): Flow<VerificationStep> = flow {
        // Track whether the server is still running so finally can clean up on any abnormal exit
        // (exception during polling, consumer cancellation, etc.). Without this the server could
        // leak and _serverStatus stays RUNNING forever.
        var serverStarted = false
        try {
            // Step 1: Start local server
            emit(step("Start local server", StepStatus.IN_PROGRESS))
            val startResult = startServer(config)
            if (startResult.isFailure) {
                emit(
                    step(
                        "Start local server",
                        StepStatus.FAILURE,
                        startResult.exceptionOrNull()?.message ?: "Failed to start server"
                    )
                )
                return@flow
            }
            serverStarted = true
            emit(
                step(
                    "Start local server",
                    StepStatus.SUCCESS,
                    "Server running at ${config.serverUrl}"
                )
            )

            // Step 2: Get SDK level
            emit(step("Check device SDK level", StepStatus.IN_PROGRESS))
            val sdkLevel = getSdkLevel(deviceSerial).getOrElse { error ->
                emit(
                    step(
                        "Check device SDK level",
                        StepStatus.FAILURE,
                        error.message ?: "Failed to get SDK level"
                    )
                )
                return@flow
            }
            emit(
                step(
                    "Check device SDK level",
                    StepStatus.SUCCESS,
                    "SDK level: $sdkLevel"
                )
            )

            // Step 3: Trigger re-verification
            emit(step("Trigger re-verification", StepStatus.IN_PROGRESS))
            val strategy = strategyFactory.create(sdkLevel)
            val reverifyCommand = strategy.forceReverifyCommand(packageName)
            val reverifyResult = adbExecutor.executeOnDevice(deviceSerial, reverifyCommand)
            if (reverifyResult.isFailure) {
                emit(
                    step(
                        "Trigger re-verification",
                        StepStatus.FAILURE,
                        reverifyResult.exceptionOrNull()?.message ?: "Failed to trigger re-verification"
                    )
                )
                return@flow
            }
            emit(
                step(
                    "Trigger re-verification",
                    StepStatus.SUCCESS,
                    "Re-verification triggered for $packageName"
                )
            )

            // Step 4: Wait and poll for verification results
            emit(step("Wait for verification", StepStatus.IN_PROGRESS, "Polling..."))
            delay(VERIFICATION_POLL_DELAY_MS)

            val appLinksCommand = strategy.getAppLinksCommand(packageName)
            var lastStatus = ""

            for (i in 1..VERIFICATION_MAX_POLLS) {
                val pollResult = adbExecutor.executeOnDevice(deviceSerial, appLinksCommand)
                if (pollResult.isSuccess) {
                    val output = pollResult.getOrThrow()
                    if (output != lastStatus) {
                        lastStatus = output
                        emit(
                            step(
                                "Wait for verification",
                                StepStatus.IN_PROGRESS,
                                "Poll $i/$VERIFICATION_MAX_POLLS: checking status..."
                            )
                        )
                    }

                    if (output.contains("verified") || output.contains("1024")) {
                        emit(
                            step(
                                "Wait for verification",
                                StepStatus.SUCCESS,
                                "Verification completed"
                            )
                        )
                        break
                    }
                }

                if (i == VERIFICATION_MAX_POLLS) {
                    emit(
                        step(
                            "Wait for verification",
                            StepStatus.FAILURE,
                            "Verification did not complete within the polling window"
                        )
                    )
                }

                delay(VERIFICATION_POLL_DELAY_MS)
            }

            // Step 5: Retrieve final results
            emit(step("Retrieve final results", StepStatus.IN_PROGRESS))
            val finalResult = adbExecutor.executeOnDevice(deviceSerial, appLinksCommand)
            if (finalResult.isSuccess) {
                emit(
                    step(
                        "Retrieve final results",
                        StepStatus.SUCCESS,
                        finalResult.getOrThrow().trim()
                    )
                )
            } else {
                emit(
                    step(
                        "Retrieve final results",
                        StepStatus.FAILURE,
                        finalResult.exceptionOrNull()?.message ?: "Failed to retrieve results"
                    )
                )
            }

            // Step 6: Stop server (normal completion path)
            emit(step("Stop local server", StepStatus.IN_PROGRESS))
            stopServer()
            serverStarted = false
            emit(step("Stop local server", StepStatus.SUCCESS, "Server stopped"))
        } finally {
            if (serverStarted) {
                // Best-effort cleanup for abnormal exits (early return, exception, cancellation).
                // Emitting from finally is unsafe if the flow was cancelled, so we just stop quietly.
                runCatching { stopServer() }
            }
        }
    }

    private fun step(
        name: String,
        status: StepStatus,
        message: String = ""
    ): VerificationStep = VerificationStep(
        name = name,
        status = status,
        message = message,
        timestamp = System.currentTimeMillis()
    )

    private suspend fun getSdkLevel(deviceSerial: String): Result<Int> {
        return adbExecutor
            .executeOnDevice(deviceSerial, "getprop ro.build.version.sdk")
            .mapCatching { output ->
                output.trim().toIntOrNull()
                    ?: throw IllegalStateException("Failed to parse SDK level: $output")
            }
    }
}
