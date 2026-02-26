package com.manjee.linkops.data.repository

import com.manjee.linkops.data.parser.IntentPayloadParser
import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.repository.IntentPayloadRepository
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor
import kotlinx.coroutines.delay

/**
 * Implementation of IntentPayloadRepository using ADB commands
 *
 * Captures intent payloads by executing `dumpsys activity activities` and parsing
 * the topmost activity's intent information. Supports top activity change detection
 * via polling `dumpsys activity top`.
 *
 * @param adbExecutor ADB shell command executor
 * @param parser Parser for dumpsys activity output
 */
class IntentPayloadRepositoryImpl(
    private val adbExecutor: AdbShellExecutor,
    private val parser: IntentPayloadParser
) : IntentPayloadRepository {

    override suspend fun captureCurrentPayload(deviceSerial: String): Result<IntentPayload> {
        return adbExecutor.executeOnDevice(deviceSerial, DUMPSYS_ACTIVITIES_COMMAND)
            .mapCatching { output ->
                val topActivityOutput = extractTopActivitySection(output)
                parser.parse(topActivityOutput)
            }
    }

    override suspend fun captureAfterActivityChange(
        deviceSerial: String,
        timeoutMs: Long
    ): Result<IntentPayload> {
        // Record current top activity
        val currentTopActivity = adbExecutor.executeOnDevice(deviceSerial, DUMPSYS_TOP_COMMAND)
            .getOrElse { return Result.failure(it) }

        val currentTopId = extractTopActivityIdentifier(currentTopActivity)

        // Poll for activity change
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(IntentPayloadRepository.POLLING_INTERVAL_MS)

            val newTopActivity = adbExecutor.executeOnDevice(deviceSerial, DUMPSYS_TOP_COMMAND)
                .getOrElse { continue }

            val newTopId = extractTopActivityIdentifier(newTopActivity)

            if (newTopId != currentTopId) {
                // Top activity changed, capture the new payload
                return captureCurrentPayload(deviceSerial)
            }
        }

        // Timeout reached, capture current state as fallback
        return captureCurrentPayload(deviceSerial)
    }

    /**
     * Extracts the top activity section from full dumpsys activities output
     *
     * Looks for the "Running activities" or "Hist #0" section which contains
     * the topmost activity's intent data.
     */
    private fun extractTopActivitySection(fullOutput: String): String {
        val lines = fullOutput.lines()

        // Find the first "Hist #0" entry which is the topmost activity
        val histIndex = lines.indexOfFirst { it.trim().startsWith("Hist #0") }
        if (histIndex < 0) return fullOutput

        // Collect lines from Hist #0 until the next Hist entry or section end
        val sectionLines = mutableListOf<String>()
        for (i in histIndex until lines.size) {
            val line = lines[i]
            if (i > histIndex && (line.trim().startsWith("Hist #") || line.trim().startsWith("Task id"))) {
                break
            }
            sectionLines.add(line)
        }

        return sectionLines.joinToString("\n")
    }

    /**
     * Extracts a unique identifier for the current top activity
     *
     * Looks for the ACTIVITY line or first meaningful content from dumpsys activity top
     */
    private fun extractTopActivityIdentifier(topOutput: String): String {
        val activityLine = topOutput.lines().firstOrNull { line ->
            line.trim().startsWith("ACTIVITY") || line.trim().startsWith("Hist #0")
        }
        return activityLine?.trim() ?: topOutput.take(TOP_ACTIVITY_ID_LENGTH)
    }

    companion object {
        private const val DUMPSYS_ACTIVITIES_COMMAND = "dumpsys activity activities"
        private const val DUMPSYS_TOP_COMMAND = "dumpsys activity top"
        private const val TOP_ACTIVITY_ID_LENGTH = 200
    }
}
