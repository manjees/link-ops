package com.manjee.linkops.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmStartOutputParserTest {

    private val parser = AmStartOutputParser()

    @Test
    fun `parse should return Success when output contains Activity and ok status`() {
        val output = """
            Starting: Intent { act=android.intent.action.VIEW dat=myapp://product/123 }
            Status: ok
            Activity: com.example.app/.ProductActivity
            ThisTime: 234
            TotalTime: 456
            WaitTime: 567
            Complete
        """.trimIndent()

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Success>(result)
        assertEquals("com.example.app/.ProductActivity", result.resolvedActivity)
    }

    @Test
    fun `parse should return Success when output contains Activity without status line`() {
        val output = """
            Starting: Intent { act=android.intent.action.VIEW dat=myapp://path }
            Activity: com.example.app/.MainActivity
        """.trimIndent()

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Success>(result)
        assertEquals("com.example.app/.MainActivity", result.resolvedActivity)
    }

    @Test
    fun `parse should return Error when output contains Error line`() {
        val output = """
            Starting: Intent { act=android.intent.action.VIEW dat=invalid://link }
            Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat=invalid://link flg=0x10000000 }
        """.trimIndent()

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
        assertTrue(result.message.contains("Activity not started"))
    }

    @Test
    fun `parse should return Error for empty output`() {
        val result = parser.parse("")

        assertIs<ParsedAmStartResult.Error>(result)
        assertTrue(result.message.contains("Empty output"))
    }

    @Test
    fun `parse should return Error for blank output`() {
        val result = parser.parse("   \n  \n  ")

        assertIs<ParsedAmStartResult.Error>(result)
        assertTrue(result.message.contains("Empty output"))
    }

    @Test
    fun `parse should return Error for device not found`() {
        val output = "error: device not found"

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
        assertEquals("error: device not found", result.message)
    }

    @Test
    fun `parse should return Error for device closed`() {
        val output = "error: closed"

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
        assertEquals("error: closed", result.message)
    }

    @Test
    fun `parse should return Error for permission denied`() {
        val output = "Permission denied"

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
        assertTrue(result.message.contains("Permission denied"))
    }

    @Test
    fun `parse should return Error for operation not permitted`() {
        val output = "Operation not permitted"

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
        assertTrue(result.message.contains("Operation not permitted"))
    }

    @Test
    fun `parse should return Error for warning line`() {
        val output = """
            Starting: Intent { act=android.intent.action.VIEW dat=myapp://test }
            Warning: Activity not started, its current task has been brought to the front
        """.trimIndent()

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
        assertTrue(result.message.contains("Activity not started"))
    }

    @Test
    fun `parse should return Error for unparseable output`() {
        val output = "Some random garbage output that doesn't match any pattern"

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
        assertTrue(result.message.contains("Unable to parse"))
    }

    @Test
    fun `parse should handle truncated output`() {
        val output = "Starting: Intent { act=android.intent.action.VIEW dat=myapp://pro"

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Error>(result)
    }

    @Test
    fun `parse should handle special characters in output`() {
        val output = """
            Starting: Intent { act=android.intent.action.VIEW dat=myapp://path?key=value&foo=bar%20baz }
            Status: ok
            Activity: com.example.app/.DeepLinkActivity
        """.trimIndent()

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Success>(result)
        assertEquals("com.example.app/.DeepLinkActivity", result.resolvedActivity)
    }

    @Test
    fun `parse should handle output with extra whitespace`() {
        val output = """

              Starting: Intent { act=android.intent.action.VIEW }
              Status: ok
              Activity:   com.example.app/.TestActivity

        """.trimIndent()

        val result = parser.parse(output)

        assertIs<ParsedAmStartResult.Success>(result)
        assertEquals("com.example.app/.TestActivity", result.resolvedActivity)
    }
}
