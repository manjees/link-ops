package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.model.LocalHostingConfig
import com.manjee.linkops.domain.model.VerificationStep
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunVerificationWorkflowUseCaseTest {

    private lateinit var fakeRepository: FakeLocalHostingRepository
    private lateinit var useCase: RunVerificationWorkflowUseCase

    @BeforeTest
    fun setup() {
        fakeRepository = FakeLocalHostingRepository()
        useCase = RunVerificationWorkflowUseCase(fakeRepository)
    }

    @Test
    fun `should emit all workflow steps`() = runTest {
        fakeRepository.workflowSteps = listOf(
            VerificationStep("Start server", VerificationStep.StepStatus.SUCCESS, "OK"),
            VerificationStep("Verify", VerificationStep.StepStatus.SUCCESS, "Verified"),
            VerificationStep("Stop server", VerificationStep.StepStatus.SUCCESS, "Stopped")
        )

        val config = LocalHostingConfig(
            packageName = "com.example.app",
            sha256Fingerprints = listOf("AB:CD:EF:01")
        )

        val results = useCase("emulator-5554", "com.example.app", config).toList()

        assertEquals(3, results.size)
        assertEquals("Start server", results[0].name)
        assertEquals("Verify", results[1].name)
        assertEquals("Stop server", results[2].name)
    }

    @Test
    fun `should propagate error when repository fails`() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = RuntimeException("Workflow failed")

        val config = LocalHostingConfig(
            packageName = "com.example.app",
            sha256Fingerprints = listOf("AB:CD:EF:01")
        )

        try {
            useCase("emulator-5554", "com.example.app", config).toList()
            assertTrue(false, "Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals("Workflow failed", e.message)
        }
    }

    @Test
    fun `should return empty list when no steps`() = runTest {
        fakeRepository.workflowSteps = emptyList()

        val config = LocalHostingConfig(
            packageName = "com.example.app",
            sha256Fingerprints = listOf("AB:CD:EF:01")
        )

        val results = useCase("emulator-5554", "com.example.app", config).toList()

        assertTrue(results.isEmpty())
    }
}
