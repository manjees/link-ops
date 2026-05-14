package com.manjee.linkops.domain.model

/**
 * Represents a single parsed logcat entry
 */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val deepLinkEvent: DeepLinkEvent? = null
) {
    val isDeepLinkEvent: Boolean get() = deepLinkEvent != null
}

/**
 * Log level from logcat output
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL,
    UNKNOWN;

    val initial: String
        get() = when (this) {
            VERBOSE -> "V"
            DEBUG -> "D"
            INFO -> "I"
            WARNING -> "W"
            ERROR -> "E"
            FATAL -> "F"
            UNKNOWN -> "?"
        }
}

/**
 * Filter configuration for logcat streaming
 */
data class LogFilter(
    val tags: Set<String> = DEFAULT_TAGS,
    val keywords: Set<String> = emptySet(),
    val minLogLevel: LogLevel = LogLevel.VERBOSE
) {
    companion object {
        val DEFAULT_TAGS = setOf(
            "ActivityTaskManager",
            "IntentResolver",
            "PackageManager",
            "BrowserActivity",
            "ResolverActivity"
        )
    }
}

/**
 * Type of deep link lifecycle event
 */
enum class DeepLinkEventType {
    CLICKED,
    RESOLVED,
    STARTED,
    RESULT,
    ERROR
}

/**
 * Represents a deep link lifecycle event extracted from a log entry
 */
data class DeepLinkEvent(
    val type: DeepLinkEventType,
    val description: String
)
