package com.manjee.linkops.domain.usecase.sniffer

import com.manjee.linkops.domain.model.BundleExtra
import com.manjee.linkops.domain.model.BundleExtraType
import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.model.ParsingMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CaptureIntentPayloadUseCaseTest {

    private val fakeRepository = FakeIntentPayloadRepository()
    private val useCase = CaptureIntentPayloadUseCase(fakeRepository)

    @Test
    fun `invoke should return success when repository succeeds`() = runTest {
        fakeRepository.captureResult = IntentPayload(
            action = "android.intent.action.VIEW",
            dataUri = "https://example.com",
            categories = emptyList(),
            flags = 0x10000000,
            component = "com.example/.MainActivity",
            extras = mapOf(
                "referrer" to BundleExtra("referrer", "com.android.shell", BundleExtraType.STRING)
            ),
            rawOutput = "test output",
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.REGEX
        )

        val result = useCase("emulator-5554")

        assertTrue(result.isSuccess)
        val payload = result.getOrNull()
        assertNotNull(payload)
        assertEquals("android.intent.action.VIEW", payload.action)
        assertEquals("https://example.com", payload.dataUri)
        assertEquals(1, payload.extras.size)
        assertEquals("com.android.shell", payload.extras["referrer"]?.value)
    }

    @Test
    fun `invoke should return failure when repository fails`() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = RuntimeException("ADB not available")

        val result = useCase("emulator-5554")

        assertTrue(result.isFailure)
        assertEquals("ADB not available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `captureAfterChange should return success when repository succeeds`() = runTest {
        fakeRepository.captureAfterChangeResult = IntentPayload(
            action = "android.intent.action.VIEW",
            dataUri = "https://changed.example.com",
            categories = listOf("android.intent.category.BROWSABLE"),
            flags = 0x14000000,
            component = "com.example/.DeepLinkActivity",
            extras = emptyMap(),
            rawOutput = "changed output",
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.SIMPLE_SPLIT
        )

        val result = useCase.captureAfterChange("emulator-5554")

        assertTrue(result.isSuccess)
        val payload = result.getOrNull()
        assertNotNull(payload)
        assertEquals("https://changed.example.com", payload.dataUri)
        assertEquals(ParsingMethod.SIMPLE_SPLIT, payload.parsingMethod)
    }

    @Test
    fun `captureAfterChange should return failure when repository fails`() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = RuntimeException("Device disconnected")

        val result = useCase.captureAfterChange("emulator-5554")

        assertTrue(result.isFailure)
        assertEquals("Device disconnected", result.exceptionOrNull()?.message)
    }

    @Test
    fun `captureAfterChange should accept custom timeout`() = runTest {
        val result = useCase.captureAfterChange("emulator-5554", timeoutMs = 5000)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke should handle payload with empty extras`() = runTest {
        fakeRepository.captureResult = IntentPayload(
            action = "android.intent.action.MAIN",
            dataUri = null,
            categories = emptyList(),
            flags = 0,
            component = "com.example/.Main",
            extras = emptyMap(),
            rawOutput = "",
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.RAW_ONLY
        )

        val result = useCase("device-123")

        assertTrue(result.isSuccess)
        val payload = result.getOrNull()
        assertNotNull(payload)
        assertTrue(payload.extras.isEmpty())
    }
}
