package com.manjee.linkops.domain.usecase.logstream

import com.manjee.linkops.domain.model.LogEntry
import com.manjee.linkops.domain.model.LogFilter
import com.manjee.linkops.domain.model.LogLevel
import com.manjee.linkops.domain.repository.LogStreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveLogStreamUseCaseTest {

    @Test
    fun `invoke should return log entries from repository`() = runTest {
        val expectedEntries = listOf(
            LogEntry(
                timestamp = "01-15 12:34:56.789",
                level = LogLevel.INFO,
                tag = "ActivityTaskManager",
                message = "START u0 {dat=https://example.com}"
            ),
            LogEntry(
                timestamp = "01-15 12:34:56.790",
                level = LogLevel.DEBUG,
                tag = "IntentResolver",
                message = "Resolving intent"
            )
        )

        val fakeRepository = FakeLogStreamRepository(expectedEntries)
        val useCase = ObserveLogStreamUseCase(fakeRepository)

        val result = useCase("device-123", LogFilter()).toList()

        assertEquals(expectedEntries.size, result.size)
        assertEquals(expectedEntries[0].tag, result[0].tag)
        assertEquals(expectedEntries[1].tag, result[1].tag)
    }

    @Test
    fun `invoke should return empty flow when no entries match`() = runTest {
        val fakeRepository = FakeLogStreamRepository(emptyList())
        val useCase = ObserveLogStreamUseCase(fakeRepository)

        val result = useCase("device-123", LogFilter()).toList()

        assertTrue(result.isEmpty(), "Should return empty list")
    }

    @Test
    fun `invoke should pass filter to repository`() = runTest {
        val fakeRepository = FakeLogStreamRepository(emptyList())
        val useCase = ObserveLogStreamUseCase(fakeRepository)
        val customFilter = LogFilter(
            tags = setOf("CustomTag"),
            minLogLevel = LogLevel.WARNING
        )

        useCase("device-456", customFilter).toList()

        assertEquals("device-456", fakeRepository.lastDeviceSerial)
        assertEquals(customFilter, fakeRepository.lastFilter)
    }

    @Test
    fun `invoke should propagate errors from repository`() = runTest {
        val fakeRepository = FakeLogStreamRepository(
            entries = emptyList(),
            shouldThrow = true
        )
        val useCase = ObserveLogStreamUseCase(fakeRepository)

        try {
            useCase("device-123", LogFilter()).toList()
            assertTrue(false, "Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals("Stream failed", e.message)
        }
    }
}

/**
 * Fake implementation of LogStreamRepository for testing
 */
private class FakeLogStreamRepository(
    private val entries: List<LogEntry>,
    private val shouldThrow: Boolean = false
) : LogStreamRepository {

    var lastDeviceSerial: String? = null
        private set
    var lastFilter: LogFilter? = null
        private set

    override fun observeLogStream(
        deviceSerial: String,
        filter: LogFilter
    ): Flow<LogEntry> {
        lastDeviceSerial = deviceSerial
        lastFilter = filter

        return flow {
            if (shouldThrow) {
                throw RuntimeException("Stream failed")
            }
            entries.forEach { emit(it) }
        }
    }
}
