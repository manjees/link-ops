package com.manjee.linkops.data.repository

import com.manjee.linkops.data.parser.LogcatParser
import com.manjee.linkops.domain.model.LogEntry
import com.manjee.linkops.domain.model.LogFilter
import com.manjee.linkops.domain.repository.LogStreamRepository
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Implementation of [LogStreamRepository] using ADB logcat streaming
 */
class LogStreamRepositoryImpl(
    private val adbExecutor: AdbShellExecutor,
    private val logcatParser: LogcatParser
) : LogStreamRepository {

    override fun observeLogStream(
        deviceSerial: String,
        filter: LogFilter
    ): Flow<LogEntry> {
        val command = buildLogcatCommand(filter)
        return adbExecutor.executeStreamOnDevice(deviceSerial, command)
            .mapNotNull { line -> logcatParser.parseLine(line) }
            .mapNotNull { entry -> applyFilter(entry, filter) }
    }

    private fun buildLogcatCommand(filter: LogFilter): String {
        val sb = StringBuilder("logcat -v time")

        if (filter.tags.isNotEmpty()) {
            sb.append(" -s")
            filter.tags.forEach { tag ->
                sb.append(" $tag:${levelToChar(filter.minLogLevel)}")
            }
        }

        return sb.toString()
    }

    private fun applyFilter(entry: LogEntry, filter: LogFilter): LogEntry? {
        if (entry.level.ordinal < filter.minLogLevel.ordinal) return null

        if (filter.keywords.isNotEmpty()) {
            val matchesKeyword = filter.keywords.any { keyword ->
                entry.message.contains(keyword, ignoreCase = true) ||
                        entry.tag.contains(keyword, ignoreCase = true)
            }
            if (!matchesKeyword) return null
        }

        return entry
    }

    private fun levelToChar(level: com.manjee.linkops.domain.model.LogLevel): String {
        return level.initial
    }
}
