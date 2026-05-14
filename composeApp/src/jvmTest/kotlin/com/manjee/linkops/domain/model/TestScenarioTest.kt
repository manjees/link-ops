package com.manjee.linkops.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestScenarioTest {

    @Test
    fun `totalLaunchCount should be zero for empty templates`() {
        val scenario = TestScenario(id = "1", name = "Test")
        assertEquals(0, scenario.totalLaunchCount)
    }

    @Test
    fun `totalLaunchCount should equal template count when no variables`() {
        val scenario = TestScenario(
            id = "1",
            name = "Test",
            templates = listOf(
                DeepLinkTemplate(uri = "myapp://a"),
                DeepLinkTemplate(uri = "myapp://b")
            )
        )
        assertEquals(2, scenario.totalLaunchCount)
    }

    @Test
    fun `totalLaunchCount should multiply templates by variable combinations`() {
        val scenario = TestScenario(
            id = "1",
            name = "Test",
            templates = listOf(
                DeepLinkTemplate(uri = "myapp://product/{id}")
            ),
            variables = mapOf("id" to listOf("1", "2", "3"))
        )
        assertEquals(3, scenario.totalLaunchCount)
    }

    @Test
    fun `totalLaunchCount should calculate cartesian product for multiple variables`() {
        val scenario = TestScenario(
            id = "1",
            name = "Test",
            templates = listOf(
                DeepLinkTemplate(uri = "myapp://product/{id}/{color}")
            ),
            variables = mapOf(
                "id" to listOf("1", "2"),
                "color" to listOf("red", "blue", "green")
            )
        )
        // 1 template * (2 * 3) combinations = 6
        assertEquals(6, scenario.totalLaunchCount)
    }

    @Test
    fun `DeepLinkTemplate hasPlaceholders should detect placeholders`() {
        assertTrue(DeepLinkTemplate(uri = "myapp://product/{id}").hasPlaceholders)
        assertTrue(DeepLinkTemplate(uri = "myapp://{category}/{id}").hasPlaceholders)
    }

    @Test
    fun `DeepLinkTemplate hasPlaceholders should return false without placeholders`() {
        assertFalse(DeepLinkTemplate(uri = "myapp://home").hasPlaceholders)
        assertFalse(DeepLinkTemplate(uri = "https://example.com/path").hasPlaceholders)
    }

    @Test
    fun `DeepLinkTemplate placeholderNames should extract all placeholder names`() {
        val template = DeepLinkTemplate(uri = "myapp://product/{id}/variant/{color}")
        assertEquals(setOf("id", "color"), template.placeholderNames)
    }

    @Test
    fun `DeepLinkTemplate placeholderNames should return empty set for no placeholders`() {
        val template = DeepLinkTemplate(uri = "myapp://home")
        assertTrue(template.placeholderNames.isEmpty())
    }

    @Test
    fun `BatchTestResult should calculate success metrics correctly`() {
        val result = BatchTestResult(
            scenarioName = "Test",
            results = listOf(
                LaunchResult.Success(uri = "a", resolvedActivity = "A", timeTakenMs = 100),
                LaunchResult.Success(uri = "b", resolvedActivity = "B", timeTakenMs = 200),
                LaunchResult.Error(uri = "c", errorMessage = "fail", timeTakenMs = 50)
            ),
            totalTimeMs = 1000
        )

        assertEquals(3, result.totalCount)
        assertEquals(2, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(2f / 3f, result.successRate, 0.001f)
    }

    @Test
    fun `BatchTestResult should handle empty results`() {
        val result = BatchTestResult(
            scenarioName = "Empty",
            results = emptyList(),
            totalTimeMs = 0
        )

        assertEquals(0, result.totalCount)
        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(0f, result.successRate)
    }

    @Test
    fun `LaunchResult isSuccess should return correct values`() {
        val success = LaunchResult.Success(uri = "a", resolvedActivity = "A", timeTakenMs = 100)
        val error = LaunchResult.Error(uri = "b", errorMessage = "fail", timeTakenMs = 50)

        assertTrue(success.isSuccess)
        assertFalse(error.isSuccess)
    }

    @Test
    fun `LaunchResult uri should be accessible from sealed class`() {
        val success: LaunchResult = LaunchResult.Success(uri = "myapp://a", resolvedActivity = "A", timeTakenMs = 100)
        val error: LaunchResult = LaunchResult.Error(uri = "myapp://b", errorMessage = "fail", timeTakenMs = 50)

        assertEquals("myapp://a", success.uri)
        assertEquals("myapp://b", error.uri)
    }
}
