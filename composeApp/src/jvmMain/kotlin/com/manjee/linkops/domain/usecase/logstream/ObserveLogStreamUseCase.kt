package com.manjee.linkops.domain.usecase.logstream

import com.manjee.linkops.domain.model.LogEntry
import com.manjee.linkops.domain.model.LogFilter
import com.manjee.linkops.domain.repository.LogStreamRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes real-time logcat stream filtered for deep link events
 *
 * @param logStreamRepository Repository for log stream operations
 */
class ObserveLogStreamUseCase(
    private val logStreamRepository: LogStreamRepository
) {
    /**
     * @param deviceSerial Device serial number
     * @param filter Log filter configuration
     * @return Flow of parsed log entries
     */
    operator fun invoke(
        deviceSerial: String,
        filter: LogFilter
    ): Flow<LogEntry> {
        return logStreamRepository.observeLogStream(deviceSerial, filter)
    }
}
