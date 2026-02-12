package com.manjee.linkops.domain.usecase.localhosting

import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateAssetLinksUseCaseTest {

    private lateinit var fakeRepository: FakeLocalHostingRepository
    private lateinit var useCase: GenerateAssetLinksUseCase

    @BeforeTest
    fun setup() {
        fakeRepository = FakeLocalHostingRepository()
        useCase = GenerateAssetLinksUseCase(fakeRepository)
    }

    @Test
    fun `should generate JSON successfully`() = runTest {
        fakeRepository.generatedJson = """[{"relation":["delegate_permission/common.handle_all_urls"]}]"""

        val result = useCase("com.example.app", listOf("AB:CD:EF:01"))

        assertTrue(result.isSuccess)
        assertEquals(fakeRepository.generatedJson, result.getOrNull())
    }

    @Test
    fun `should return failure when repository fails`() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = RuntimeException("Generation failed")

        val result = useCase("com.example.app", listOf("AB:CD:EF:01"))

        assertTrue(result.isFailure)
        assertEquals("Generation failed", result.exceptionOrNull()?.message)
    }
}
