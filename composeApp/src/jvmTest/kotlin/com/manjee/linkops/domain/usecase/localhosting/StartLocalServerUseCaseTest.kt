package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.model.LocalHostingConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartLocalServerUseCaseTest {

    private lateinit var fakeRepository: FakeLocalHostingRepository
    private lateinit var useCase: StartLocalServerUseCase

    @BeforeTest
    fun setup() {
        fakeRepository = FakeLocalHostingRepository()
        useCase = StartLocalServerUseCase(fakeRepository)
    }

    @Test
    fun `should start server successfully`() = runTest {
        val config = LocalHostingConfig(
            host = "127.0.0.1",
            port = 8080,
            packageName = "com.example.app",
            sha256Fingerprints = listOf("AB:CD:EF:01")
        )

        val result = useCase(config)

        assertTrue(result.isSuccess)
        assertEquals(config, fakeRepository.lastStartConfig)
    }

    @Test
    fun `should return failure when repository fails`() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = RuntimeException("Port in use")

        val config = LocalHostingConfig(
            host = "127.0.0.1",
            port = 8080,
            packageName = "com.example.app",
            sha256Fingerprints = listOf("AB:CD:EF:01")
        )

        val result = useCase(config)

        assertTrue(result.isFailure)
        assertEquals("Port in use", result.exceptionOrNull()?.message)
    }
}
