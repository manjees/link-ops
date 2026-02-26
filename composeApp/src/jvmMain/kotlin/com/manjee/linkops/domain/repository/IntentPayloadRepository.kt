package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.IntentPayload

/**
 * Repository for capturing and analyzing intent payloads from Android devices
 */
interface IntentPayloadRepository {

    /**
     * Captures the current top activity's intent payload from a device
     *
     * Executes `dumpsys activity activities` on the target device and parses
     * the topmost activity's intent information.
     *
     * @param deviceSerial Serial number of the target device
     * @return Result containing the parsed IntentPayload or an error
     */
    suspend fun captureCurrentPayload(deviceSerial: String): Result<IntentPayload>

    /**
     * Captures intent payload after detecting a top activity change
     *
     * Uses polling to detect when the top activity changes (after an intent is fired),
     * then captures the new activity's intent payload.
     *
     * @param deviceSerial Serial number of the target device
     * @param timeoutMs Maximum time to wait for activity change in milliseconds
     * @return Result containing the parsed IntentPayload or an error
     */
    suspend fun captureAfterActivityChange(
        deviceSerial: String,
        timeoutMs: Long = DEFAULT_POLLING_TIMEOUT_MS
    ): Result<IntentPayload>

    companion object {
        const val DEFAULT_POLLING_TIMEOUT_MS = 3000L
        const val POLLING_INTERVAL_MS = 100L
    }
}
