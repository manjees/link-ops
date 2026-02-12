package com.manjee.linkops.data.mapper

import com.manjee.linkops.domain.model.DeepLinkTemplate
import com.manjee.linkops.domain.model.TestScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioMapperTest {

    private val mapper = ScenarioMapper()

    @Test
    fun `toJson and fromJson should roundtrip a complete scenario`() {
        val scenario = TestScenario(
            id = "test-id-123",
            name = "Product Test",
            description = "Test product deep links",
            templates = listOf(
                DeepLinkTemplate(
                    uri = "myapp://product/{id}",
                    name = "Product Page",
                    expectedActivity = "com.example/.ProductActivity"
                ),
                DeepLinkTemplate(uri = "myapp://home")
            ),
            variables = mapOf(
                "id" to listOf("1", "2", "3")
            ),
            delayBetweenLaunchesMs = 2000L
        )

        val json = mapper.toJson(scenario)
        val restored = mapper.fromJson(json)

        assertEquals(scenario.id, restored.id)
        assertEquals(scenario.name, restored.name)
        assertEquals(scenario.description, restored.description)
        assertEquals(scenario.templates.size, restored.templates.size)
        assertEquals(scenario.templates[0].uri, restored.templates[0].uri)
        assertEquals(scenario.templates[0].name, restored.templates[0].name)
        assertEquals(scenario.templates[0].expectedActivity, restored.templates[0].expectedActivity)
        assertEquals(scenario.templates[1].uri, restored.templates[1].uri)
        assertEquals(scenario.variables, restored.variables)
        assertEquals(scenario.delayBetweenLaunchesMs, restored.delayBetweenLaunchesMs)
    }

    @Test
    fun `toJson and fromJson should roundtrip minimal scenario`() {
        val scenario = TestScenario(
            id = "min-id",
            name = "Minimal"
        )

        val json = mapper.toJson(scenario)
        val restored = mapper.fromJson(json)

        assertEquals(scenario.id, restored.id)
        assertEquals(scenario.name, restored.name)
        assertEquals("", restored.description)
        assertTrue(restored.templates.isEmpty())
        assertTrue(restored.variables.isEmpty())
        assertEquals(TestScenario.DEFAULT_DELAY_MS, restored.delayBetweenLaunchesMs)
    }

    @Test
    fun `toJson should produce valid JSON`() {
        val scenario = TestScenario(
            id = "json-test",
            name = "JSON Test",
            templates = listOf(DeepLinkTemplate(uri = "myapp://test"))
        )

        val json = mapper.toJson(scenario)

        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("\"templates\""))
        assertTrue(json.contains("myapp://test"))
    }

    @Test
    fun `fromJson should handle unknown keys gracefully`() {
        val json = """
            {
                "id": "test",
                "name": "Test",
                "unknownField": "should be ignored",
                "templates": [],
                "variables": {}
            }
        """.trimIndent()

        val scenario = mapper.fromJson(json)

        assertEquals("test", scenario.id)
        assertEquals("Test", scenario.name)
    }

    @Test
    fun `fromJson should handle special characters in URIs`() {
        val json = """
            {
                "id": "special",
                "name": "Special Chars",
                "templates": [
                    {"uri": "myapp://path?key=value&foo=bar%20baz"}
                ]
            }
        """.trimIndent()

        val scenario = mapper.fromJson(json)

        assertEquals("myapp://path?key=value&foo=bar%20baz", scenario.templates[0].uri)
    }
}
