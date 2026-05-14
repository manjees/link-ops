package com.manjee.linkops.domain.usecase.sniffer

import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.model.ParsingMethod
import com.manjee.linkops.domain.repository.IntentPayloadRepository

/**
 * Fake implementation of IntentPayloadRepository for testing
 */
class FakeIntentPayloadRepository : IntentPayloadRepository {

    var shouldFail = false
    var failureException: Exception = RuntimeException("Simulated failure")
    var captureResult: IntentPayload = createDefaultPayload()
    var captureAfterChangeResult: IntentPayload = createDefaultPayload()

    override suspend fun captureCurrentPayload(deviceSerial: String): Result<IntentPayload> {
        if (shouldFail) return Result.failure(failureException)
        return Result.success(captureResult)
    }

    override suspend fun captureAfterActivityChange(
        deviceSerial: String,
        timeoutMs: Long
    ): Result<IntentPayload> {
        if (shouldFail) return Result.failure(failureException)
        return Result.success(captureAfterChangeResult)
    }

    private fun createDefaultPayload(): IntentPayload {
        return IntentPayload(
            action = "android.intent.action.VIEW",
            dataUri = "https://example.com",
            categories = emptyList(),
            flags = 0,
            component = "com.example/.MainActivity",
            extras = emptyMap(),
            rawOutput = "test output",
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.REGEX
        )
    }
}
