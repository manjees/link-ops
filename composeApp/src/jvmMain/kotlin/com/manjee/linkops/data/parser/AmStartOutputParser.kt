package com.manjee.linkops.data.parser

/**
 * Parser for `adb shell am start` command output
 *
 * Example success output:
 * ```
 * Starting: Intent { act=android.intent.action.VIEW dat=myapp://product/123 }
 * Status: ok
 * Activity: com.example.app/.ProductActivity
 * ThisTime: 234
 * TotalTime: 456
 * WaitTime: 567
 * Complete
 * ```
 *
 * Example error output (no matching Activity):
 * ```
 * Starting: Intent { act=android.intent.action.VIEW dat=invalid://link }
 * Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat=invalid://link flg=0x10000000 }
 * ```
 *
 * Example error output (device error):
 * ```
 * error: device not found
 * ```
 */
class AmStartOutputParser {

    /**
     * Parses am start output into a structured result
     *
     * @param output Raw stdout from `adb shell am start -W` command
     * @return ParsedAmStartResult containing activity or error info
     */
    fun parse(output: String): ParsedAmStartResult {
        if (output.isBlank()) {
            return ParsedAmStartResult.Error("Empty output from am start command")
        }

        val lines = output.lines().map { it.trim() }

        // Check for device-level errors
        lines.firstOrNull { it.startsWith("error:") }?.let { errorLine ->
            return ParsedAmStartResult.Error(errorLine)
        }

        // Check for Permission denied
        lines.firstOrNull {
            it.contains("Permission denied", ignoreCase = true) ||
                it.contains("Operation not permitted", ignoreCase = true)
        }?.let { errorLine ->
            return ParsedAmStartResult.Error(errorLine)
        }

        // Check for Error line from am start
        lines.firstOrNull { it.startsWith("Error:") }?.let { errorLine ->
            return ParsedAmStartResult.Error(errorLine.removePrefix("Error:").trim())
        }

        // Look for Activity line
        val activityLine = lines.firstOrNull { it.startsWith("Activity:") }
        val activity = activityLine?.removePrefix("Activity:")?.trim()

        // Look for Status line
        val statusLine = lines.firstOrNull { it.startsWith("Status:") }
        val status = statusLine?.removePrefix("Status:")?.trim()

        // Check for Warning (e.g., Activity not started)
        val warningLine = lines.firstOrNull { it.startsWith("Warning:") }

        return when {
            status == "ok" && activity != null -> ParsedAmStartResult.Success(activity)
            activity != null -> ParsedAmStartResult.Success(activity)
            warningLine != null -> ParsedAmStartResult.Error(
                warningLine.removePrefix("Warning:").trim()
            )
            else -> ParsedAmStartResult.Error(
                "Unable to parse am start output: ${output.take(200)}"
            )
        }
    }
}

/**
 * Parsed result from am start command output
 */
sealed class ParsedAmStartResult {
    /**
     * Activity was successfully resolved
     *
     * @param resolvedActivity Fully qualified Activity class name
     */
    data class Success(val resolvedActivity: String) : ParsedAmStartResult()

    /**
     * Launch failed with an error
     *
     * @param message Error description
     */
    data class Error(val message: String) : ParsedAmStartResult()
}
