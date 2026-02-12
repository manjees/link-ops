package com.manjee.linkops.domain.usecase.localhosting

import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopLocalServerUseCaseTest {

    private lateinit var fakeRepository: FakeLocalHostingRepository
    private lateinit var useCase: StopLocalServerUseCase

    @BeforeTest
    fun setup() {
        fakeRepository = FakeLocalHostingRepository()
        useCase = StopLocalServerUseCase(fakeRepository)
    }

    @Test
    fun `should stop server successfully`() = runTest {
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepository.stopCount)
    }

    @Test
    fun `should return failure when repository fails`() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = RuntimeException("Stop failed")

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals("Stop failed", result.exceptionOrNull()?.message)
    }
}
