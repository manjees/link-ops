package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.model.LocalHostingConfig
import com.manjee.linkops.domain.model.ServerStatus
import com.manjee.linkops.domain.model.VerificationStep
import com.manjee.linkops.domain.repository.LocalHostingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Fake implementation of LocalHostingRepository for testing
 */
class FakeLocalHostingRepository : LocalHostingRepository {

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)

    var shouldFail = false
    var failureException: Exception = RuntimeException("Simulated failure")

    var lastStartConfig: LocalHostingConfig? = null
    var stopCount = 0
    var generatedJson = "{}"
    var extractedFingerprint = "AB:CD:EF:01"
    var workflowSteps: List<VerificationStep> = emptyList()

    override fun observeServerStatus(): Flow<ServerStatus> = _serverStatus.asStateFlow()

    override suspend fun startServer(config: LocalHostingConfig): Result<Unit> {
        lastStartConfig = config
        return if (shouldFail) {
            _serverStatus.value = ServerStatus.ERROR
            Result.failure(failureException)
        } else {
            _serverStatus.value = ServerStatus.RUNNING
            Result.success(Unit)
        }
    }

    override suspend fun stopServer(): Result<Unit> {
        stopCount++
        return if (shouldFail) {
            _serverStatus.value = ServerStatus.ERROR
            Result.failure(failureException)
        } else {
            _serverStatus.value = ServerStatus.STOPPED
            Result.success(Unit)
        }
    }

    override suspend fun generateAssetLinksJson(
        packageName: String,
        sha256Fingerprints: List<String>
    ): Result<String> {
        return if (shouldFail) {
            Result.failure(failureException)
        } else {
            Result.success(generatedJson)
        }
    }

    override suspend fun extractFingerprint(
        deviceSerial: String,
        packageName: String
    ): Result<String> {
        return if (shouldFail) {
            Result.failure(failureException)
        } else {
            Result.success(extractedFingerprint)
        }
    }

    override fun runVerificationWorkflow(
        deviceSerial: String,
        packageName: String,
        config: LocalHostingConfig
    ): Flow<VerificationStep> = flow {
        if (shouldFail) {
            throw failureException
        }
        workflowSteps.forEach { emit(it) }
    }
}
