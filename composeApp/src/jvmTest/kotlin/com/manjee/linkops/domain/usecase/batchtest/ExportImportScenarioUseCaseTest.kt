package com.manjee.linkops.domain.usecase.batchtest

import com.manjee.linkops.domain.model.DeepLinkTemplate
import com.manjee.linkops.domain.model.TestScenario
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportImportScenarioUseCaseTest {

    private val fakeRepository = FakeBatchTestRepository()
    private val exportUseCase = ExportScenarioUseCase(fakeRepository)
    private val importUseCase = ImportScenarioUseCase(fakeRepository)

    @Test
    fun `export should return success with JSON`() = runTest {
        fakeRepository.exportResult = Result.success("""{"id":"1","name":"Test"}""")

        val scenario = TestScenario(id = "1", name = "Test")
        val result = exportUseCase(scenario)

        assertTrue(result.isSuccess)
        assertEquals("""{"id":"1","name":"Test"}""", result.getOrNull())
    }

    @Test
    fun `export should return failure on error`() = runTest {
        fakeRepository.exportResult = Result.failure(RuntimeException("Serialization error"))

        val scenario = TestScenario(id = "1", name = "Test")
        val result = exportUseCase(scenario)

        assertTrue(result.isFailure)
    }

    @Test
    fun `import should return success with scenario`() = runTest {
        val expected = TestScenario(
            id = "imported",
            name = "Imported Scenario",
            templates = listOf(DeepLinkTemplate(uri = "myapp://test"))
        )
        fakeRepository.importResult = Result.success(expected)

        val result = importUseCase("""{"id":"imported"}""")

        assertTrue(result.isSuccess)
        assertEquals("imported", result.getOrNull()?.id)
        assertEquals("Imported Scenario", result.getOrNull()?.name)
    }

    @Test
    fun `import should return failure for invalid JSON`() = runTest {
        fakeRepository.importResult = Result.failure(RuntimeException("Invalid JSON"))

        val result = importUseCase("not valid json")

        assertTrue(result.isFailure)
    }
}
