package com.manjee.linkops.data.mapper

import com.manjee.linkops.domain.model.DeepLinkTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParameterSubstituterTest {

    private val substituter = ParameterSubstituter()

    // --- substitute ---

    @Test
    fun `substitute should replace single placeholder`() {
        val result = substituter.substitute("myapp://product/{id}", mapOf("id" to "123"))
        assertEquals("myapp://product/123", result)
    }

    @Test
    fun `substitute should replace multiple placeholders`() {
        val result = substituter.substitute(
            "myapp://product/{id}/variant/{color}",
            mapOf("id" to "42", "color" to "red")
        )
        assertEquals("myapp://product/42/variant/red", result)
    }

    @Test
    fun `substitute should return original when no matching placeholders`() {
        val result = substituter.substitute(
            "myapp://product/{id}",
            mapOf("name" to "test")
        )
        assertEquals("myapp://product/{id}", result)
    }

    @Test
    fun `substitute should handle empty values map`() {
        val result = substituter.substitute("myapp://product/{id}", emptyMap())
        assertEquals("myapp://product/{id}", result)
    }

    @Test
    fun `substitute should handle URI without placeholders`() {
        val result = substituter.substitute(
            "myapp://home",
            mapOf("id" to "123")
        )
        assertEquals("myapp://home", result)
    }

    // --- generateCombinations ---

    @Test
    fun `generateCombinations should return empty list for empty variables`() {
        val result = substituter.generateCombinations(emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateCombinations should generate single variable combinations`() {
        val result = substituter.generateCombinations(
            mapOf("id" to listOf("1", "2", "3"))
        )
        assertEquals(3, result.size)
        assertEquals(mapOf("id" to "1"), result[0])
        assertEquals(mapOf("id" to "2"), result[1])
        assertEquals(mapOf("id" to "3"), result[2])
    }

    @Test
    fun `generateCombinations should generate cartesian product`() {
        val result = substituter.generateCombinations(
            mapOf(
                "id" to listOf("1", "2"),
                "color" to listOf("red", "blue")
            )
        )
        assertEquals(4, result.size)
        assertTrue(result.contains(mapOf("id" to "1", "color" to "red")))
        assertTrue(result.contains(mapOf("id" to "1", "color" to "blue")))
        assertTrue(result.contains(mapOf("id" to "2", "color" to "red")))
        assertTrue(result.contains(mapOf("id" to "2", "color" to "blue")))
    }

    @Test
    fun `generateCombinations should skip empty value lists`() {
        val result = substituter.generateCombinations(
            mapOf(
                "id" to listOf("1", "2"),
                "empty" to emptyList()
            )
        )
        assertEquals(2, result.size)
        assertEquals(mapOf("id" to "1"), result[0])
        assertEquals(mapOf("id" to "2"), result[1])
    }

    @Test
    fun `generateCombinations should return empty for all empty value lists`() {
        val result = substituter.generateCombinations(
            mapOf(
                "a" to emptyList(),
                "b" to emptyList()
            )
        )
        assertTrue(result.isEmpty())
    }

    // --- resolveAll ---

    @Test
    fun `resolveAll should return empty list for empty templates`() {
        val result = substituter.resolveAll(emptyList(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolveAll should return URIs without substitution when no variables`() {
        val templates = listOf(
            DeepLinkTemplate(uri = "myapp://home"),
            DeepLinkTemplate(uri = "myapp://settings")
        )
        val result = substituter.resolveAll(templates, emptyMap())
        assertEquals(listOf("myapp://home", "myapp://settings"), result)
    }

    @Test
    fun `resolveAll should return original URIs for templates without placeholders`() {
        val templates = listOf(
            DeepLinkTemplate(uri = "myapp://home"),
            DeepLinkTemplate(uri = "myapp://settings")
        )
        val result = substituter.resolveAll(templates, mapOf("id" to listOf("1", "2")))
        assertEquals(listOf("myapp://home", "myapp://settings"), result)
    }

    @Test
    fun `resolveAll should expand templates with variables`() {
        val templates = listOf(
            DeepLinkTemplate(uri = "myapp://product/{id}")
        )
        val variables = mapOf("id" to listOf("1", "2", "3"))

        val result = substituter.resolveAll(templates, variables)

        assertEquals(3, result.size)
        assertEquals("myapp://product/1", result[0])
        assertEquals("myapp://product/2", result[1])
        assertEquals("myapp://product/3", result[2])
    }

    @Test
    fun `resolveAll should expand multiple templates with multiple variables`() {
        val templates = listOf(
            DeepLinkTemplate(uri = "myapp://product/{id}"),
            DeepLinkTemplate(uri = "myapp://category/{cat}")
        )
        val variables = mapOf(
            "id" to listOf("1", "2"),
            "cat" to listOf("shoes", "hats")
        )

        val result = substituter.resolveAll(templates, variables)

        // 2 templates * 4 combinations = 8 results
        assertEquals(8, result.size)
        assertTrue(result.contains("myapp://product/1"))
        assertTrue(result.contains("myapp://product/2"))
        assertTrue(result.contains("myapp://category/shoes"))
        assertTrue(result.contains("myapp://category/hats"))
    }

    @Test
    fun `resolveAll should handle mix of templates with and without placeholders`() {
        val templates = listOf(
            DeepLinkTemplate(uri = "myapp://home"),
            DeepLinkTemplate(uri = "myapp://product/{id}")
        )
        val variables = mapOf("id" to listOf("1", "2"))

        val result = substituter.resolveAll(templates, variables)

        assertEquals(3, result.size)
        assertEquals("myapp://home", result[0])
        assertEquals("myapp://product/1", result[1])
        assertEquals("myapp://product/2", result[2])
    }
}
