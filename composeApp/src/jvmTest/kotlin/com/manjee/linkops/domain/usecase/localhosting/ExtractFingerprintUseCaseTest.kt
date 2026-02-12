package com.manjee.linkops.domain.usecase.localhosting

import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractFingerprintUseCaseTest {

    private lateinit var fakeRepository: FakeLocalHostingRepository
    private lateinit var useCase: ExtractFingerprintUseCase

    @BeforeTest
    fun setup() {
        fakeRepository = FakeLocalHostingRepository()
        useCase = ExtractFingerprintUseCase(fakeRepository)
    }

    @Test
    fun `should extract fingerprint successfully`() = runTest {
        fakeRepository.extractedFingerprint = "AB:CD:EF:01:23:45:67:89"

        val result = useCase("emulator-5554", "com.example.app")

        assertTrue(result.isSuccess)
        assertEquals("AB:CD:EF:01:23:45:67:89", result.getOrNull())
    }

    @Test
    fun `should return failure when repository fails`() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = RuntimeException("Package not installed")

        val result = useCase("emulator-5554", "com.example.app")

        assertTrue(result.isFailure)
        assertEquals("Package not installed", result.exceptionOrNull()?.message)
    }
}
