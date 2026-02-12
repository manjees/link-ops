package com.manjee.linkops.domain.usecase.batchtest

import com.manjee.linkops.domain.model.LaunchResult
import com.manjee.linkops.domain.model.TestScenario
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecuteBatchTestUseCaseTest {

    private val fakeRepository = FakeBatchTestRepository()
    private val useCase = ExecuteBatchTestUseCase(fakeRepository)

    @Test
    fun `invoke should emit results for all URIs`() = runTest {
        val scenario = TestScenario(
            id = "test",
            name = "Test",
            delayBetweenLaunchesMs = 0
        )
        val uris = listOf("myapp://a", "myapp://b", "myapp://c")

        val results = useCase("device-123", scenario, uris).toList()

        assertEquals(3, results.size)
        results.forEach { result ->
            assertIs<LaunchResult.Success>(result)
        }
    }

    @Test
    fun `invoke should emit error results for failed launches`() = runTest {
        fakeRepository.launchResults["myapp://fail"] = Result.success(
            LaunchResult.Error(
                uri = "myapp://fail",
                errorMessage = "Activity not found",
                timeTakenMs = 50
            )
        )

        val scenario = TestScenario(
            id = "test",
            name = "Test",
            delayBetweenLaunchesMs = 0
        )
        val uris = listOf("myapp://ok", "myapp://fail")

        val results = useCase("device-123", scenario, uris).toList()

        assertEquals(2, results.size)
        assertIs<LaunchResult.Success>(results[0])
        assertIs<LaunchResult.Error>(results[1])
        assertEquals("Activity not found", (results[1] as LaunchResult.Error).errorMessage)
    }

    @Test
    fun `invoke should handle repository exceptions gracefully`() = runTest {
        fakeRepository.launchResults["myapp://error"] =
            Result.failure(RuntimeException("ADB crashed"))

        val scenario = TestScenario(
            id = "test",
            name = "Test",
            delayBetweenLaunchesMs = 0
        )
        val uris = listOf("myapp://error")

        val results = useCase("device-123", scenario, uris).toList()

        assertEquals(1, results.size)
        assertIs<LaunchResult.Error>(results[0])
        assertTrue((results[0] as LaunchResult.Error).errorMessage.contains("ADB crashed"))
    }

    @Test
    fun `invoke should return empty flow for empty URI list`() = runTest {
        val scenario = TestScenario(
            id = "test",
            name = "Test",
            delayBetweenLaunchesMs = 0
        )

        val results = useCase("device-123", scenario, emptyList()).toList()

        assertTrue(results.isEmpty())
    }
}
