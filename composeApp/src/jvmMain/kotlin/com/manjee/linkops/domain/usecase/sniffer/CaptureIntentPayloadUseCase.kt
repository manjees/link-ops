package com.manjee.linkops.domain.usecase.sniffer

import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.repository.IntentPayloadRepository

/**
 * Captures the current top activity's intent payload from a device
 *
 * @param intentPayloadRepository Repository for intent payload operations
 */
class CaptureIntentPayloadUseCase(
    private val intentPayloadRepository: IntentPayloadRepository
) {
    /**
     * Captures intent payload from the current top activity
     *
     * @param deviceSerial Serial number of the target device
     * @return Result containing the parsed IntentPayload or an error
     */
    suspend operator fun invoke(deviceSerial: String): Result<IntentPayload> {
        return intentPayloadRepository.captureCurrentPayload(deviceSerial)
    }

    /**
     * Captures intent payload after detecting a top activity change
     *
     * @param deviceSerial Serial number of the target device
     * @param timeoutMs Maximum time to wait for activity change
     * @return Result containing the parsed IntentPayload or an error
     */
    suspend fun captureAfterChange(
        deviceSerial: String,
        timeoutMs: Long = IntentPayloadRepository.DEFAULT_POLLING_TIMEOUT_MS
    ): Result<IntentPayload> {
        return intentPayloadRepository.captureAfterActivityChange(deviceSerial, timeoutMs)
    }
}
