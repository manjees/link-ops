package com.manjee.linkops.data.parser

import com.manjee.linkops.domain.model.DeepLinkEvent
import com.manjee.linkops.domain.model.DeepLinkEventType
import com.manjee.linkops.domain.model.LogEntry
import com.manjee.linkops.domain.model.LogLevel

/**
 * Parser for `adb logcat -v time` output lines
 *
 * Example output format:
 * ```
 * 01-15 12:34:56.789 I/ActivityTaskManager( 1234): START u0 {act=android.intent.action.VIEW dat=https://example.com/... flg=0x10000000 cmp=com.example.app/.MainActivity}
 * 01-15 12:34:56.790 I/IntentResolver( 1234): Resolving intent {act=android.intent.action.VIEW dat=https://example.com/...}
 * 01-15 12:34:56.791 W/PackageManager( 1234): Domain verification status: example.com -> verified
 * ```
 */
class LogcatParser {

    /**
     * Parse a single logcat line into a LogEntry
     * @param line Raw logcat line
     * @return Parsed LogEntry or null if the line cannot be parsed
     */
    fun parseLine(line: String): LogEntry? {
        if (line.isBlank()) return null

        val matchResult = LOGCAT_TIME_PATTERN.matchEntire(line) ?: return parseUnstructuredLine(line)

        val timestamp = matchResult.groupValues[1]
        val levelChar = matchResult.groupValues[2]
        val tag = matchResult.groupValues[3]
        val message = matchResult.groupValues[4]

        val level = parseLogLevel(levelChar)
        val deepLinkEvent = detectDeepLinkEvent(tag, message)

        return LogEntry(
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message,
            deepLinkEvent = deepLinkEvent
        )
    }

    private fun parseLogLevel(levelChar: String): LogLevel {
        return when (levelChar.uppercase()) {
            "V" -> LogLevel.VERBOSE
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARNING
            "E" -> LogLevel.ERROR
            "F" -> LogLevel.FATAL
            else -> LogLevel.UNKNOWN
        }
    }

    private fun parseUnstructuredLine(line: String): LogEntry? {
        if (line.startsWith("-----") || line.startsWith("---")) return null

        return LogEntry(
            timestamp = "",
            level = LogLevel.UNKNOWN,
            tag = "",
            message = line.trim()
        )
    }

    private fun detectDeepLinkEvent(tag: String, message: String): DeepLinkEvent? {
        return when {
            isIntentStartEvent(tag, message) -> DeepLinkEvent(
                type = DeepLinkEventType.STARTED,
                description = extractIntentDescription(message)
            )
            isIntentResolveEvent(tag, message) -> DeepLinkEvent(
                type = DeepLinkEventType.RESOLVED,
                description = extractResolveDescription(message)
            )
            isIntentClickEvent(tag, message) -> DeepLinkEvent(
                type = DeepLinkEventType.CLICKED,
                description = extractClickDescription(message)
            )
            isIntentResultEvent(tag, message) -> DeepLinkEvent(
                type = DeepLinkEventType.RESULT,
                description = message.trim()
            )
            isIntentErrorEvent(tag, message) -> DeepLinkEvent(
                type = DeepLinkEventType.ERROR,
                description = message.trim()
            )
            else -> null
        }
    }

    private fun isIntentStartEvent(tag: String, message: String): Boolean {
        return tag == "ActivityTaskManager" && message.contains("START")
    }

    private fun isIntentResolveEvent(tag: String, message: String): Boolean {
        return (tag == "IntentResolver" && message.contains("Resolv")) ||
                (tag == "PackageManager" && message.contains("verification"))
    }

    private fun isIntentClickEvent(tag: String, message: String): Boolean {
        return (tag == "BrowserActivity" || tag == "ResolverActivity") &&
                (message.contains("intent") || message.contains("click"))
    }

    private fun isIntentResultEvent(tag: String, message: String): Boolean {
        return tag == "ActivityTaskManager" &&
                (message.contains("Displayed") || message.contains("RESULT"))
    }

    private fun isIntentErrorEvent(tag: String, message: String): Boolean {
        return message.contains("Error", ignoreCase = true) ||
                message.contains("Exception", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ||
                message.contains("Permission denied", ignoreCase = true)
    }

    private fun extractIntentDescription(message: String): String {
        val datMatch = DAT_PATTERN.find(message)
        val cmpMatch = CMP_PATTERN.find(message)

        return buildString {
            append("Activity started")
            datMatch?.let { append(" - URI: ${it.groupValues[1]}") }
            cmpMatch?.let { append(" - Component: ${it.groupValues[1]}") }
        }
    }

    private fun extractResolveDescription(message: String): String {
        val datMatch = DAT_PATTERN.find(message)
        return if (datMatch != null) {
            "Intent resolved for: ${datMatch.groupValues[1]}"
        } else {
            "Intent resolution: ${message.take(MAX_DESCRIPTION_LENGTH)}"
        }
    }

    private fun extractClickDescription(message: String): String {
        return "Deep link clicked: ${message.take(MAX_DESCRIPTION_LENGTH)}"
    }

    companion object {
        private val LOGCAT_TIME_PATTERN = Regex(
            """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+([VDIWEF])/(.+?)\s*\(\s*\d+\):\s*(.*)$"""
        )
        private val DAT_PATTERN = Regex("""dat=(\S+)""")
        private val CMP_PATTERN = Regex("""cmp=(\S+)""")
        private const val MAX_DESCRIPTION_LENGTH = 120
    }
}
