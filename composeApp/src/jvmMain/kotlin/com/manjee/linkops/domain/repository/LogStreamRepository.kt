package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.LogEntry
import com.manjee.linkops.domain.model.LogFilter
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for logcat streaming operations
 */
interface LogStreamRepository {
    /**
     * Observes logcat output in real-time with the given filter
     * @param deviceSerial Device serial number
     * @param filter Log filter configuration
     * @return Flow of parsed log entries
     */
    fun observeLogStream(
        deviceSerial: String,
        filter: LogFilter
    ): Flow<LogEntry>
}
