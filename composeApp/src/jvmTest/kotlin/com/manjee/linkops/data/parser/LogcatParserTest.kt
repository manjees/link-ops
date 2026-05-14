package com.manjee.linkops.data.parser

import com.manjee.linkops.domain.model.DeepLinkEventType
import com.manjee.linkops.domain.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogcatParserTest {

    private val parser = LogcatParser()

    // --- Happy Path Tests ---

    @Test
    fun `parseLine should parse standard logcat time format correctly`() {
        val line = "01-15 12:34:56.789 I/ActivityTaskManager( 1234): START u0 {act=android.intent.action.VIEW dat=https://example.com cmp=com.example.app/.MainActivity}"

        val entry = parser.parseLine(line)

        assertNotNull(entry, "Entry should not be null")
        assertEquals("01-15 12:34:56.789", entry.timestamp)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("ActivityTaskManager", entry.tag)
        assertTrue(entry.message.contains("START"), "Message should contain START")
    }

    @Test
    fun `parseLine should parse all log levels correctly`() {
        val levels = mapOf(
            "V" to LogLevel.VERBOSE,
            "D" to LogLevel.DEBUG,
            "I" to LogLevel.INFO,
            "W" to LogLevel.WARNING,
            "E" to LogLevel.ERROR,
            "F" to LogLevel.FATAL
        )

        levels.forEach { (char, expectedLevel) ->
            val line = "01-15 12:34:56.789 $char/TestTag( 1234): Test message"
            val entry = parser.parseLine(line)

            assertNotNull(entry, "Entry should not be null for level $char")
            assertEquals(expectedLevel, entry.level, "Level should match for $char")
        }
    }

    @Test
    fun `parseLine should extract tag correctly from various formats`() {
        val line = "01-15 12:34:56.789 I/IntentResolver( 5678): Resolving intent"
        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertEquals("IntentResolver", entry.tag)
    }

    @Test
    fun `parseLine should extract message content correctly`() {
        val message = "Some detailed log message with special chars: [test] {data}"
        val line = "01-15 12:34:56.789 D/TestTag( 1234): $message"
        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertEquals(message, entry.message)
    }

    // --- Deep Link Event Detection Tests ---

    @Test
    fun `parseLine should detect START as deep link STARTED event`() {
        val line = "01-15 12:34:56.789 I/ActivityTaskManager( 1234): START u0 {act=android.intent.action.VIEW dat=https://example.com/path cmp=com.example/.Main}"

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertNotNull(entry.deepLinkEvent, "Should detect deep link event")
        assertEquals(DeepLinkEventType.STARTED, entry.deepLinkEvent!!.type)
        assertTrue(entry.isDeepLinkEvent)
    }

    @Test
    fun `parseLine should detect IntentResolver as RESOLVED event`() {
        val line = "01-15 12:34:56.789 I/IntentResolver( 1234): Resolving intent {act=android.intent.action.VIEW dat=https://example.com}"

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertNotNull(entry.deepLinkEvent)
        assertEquals(DeepLinkEventType.RESOLVED, entry.deepLinkEvent!!.type)
    }

    @Test
    fun `parseLine should detect PackageManager verification as RESOLVED event`() {
        val line = "01-15 12:34:56.789 I/PackageManager( 1234): Domain verification status: example.com -> verified"

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertNotNull(entry.deepLinkEvent)
        assertEquals(DeepLinkEventType.RESOLVED, entry.deepLinkEvent!!.type)
    }

    @Test
    fun `parseLine should detect Displayed as RESULT event`() {
        val line = "01-15 12:34:56.789 I/ActivityTaskManager( 1234): Displayed com.example.app/.MainActivity: +345ms"

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertNotNull(entry.deepLinkEvent)
        assertEquals(DeepLinkEventType.RESULT, entry.deepLinkEvent!!.type)
    }

    @Test
    fun `parseLine should detect error messages as ERROR event`() {
        val line = "01-15 12:34:56.789 E/ActivityTaskManager( 1234): Error: activity not found for intent"

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertNotNull(entry.deepLinkEvent)
        assertEquals(DeepLinkEventType.ERROR, entry.deepLinkEvent!!.type)
    }

    @Test
    fun `parseLine should return null deepLinkEvent for non-deeplink entries`() {
        val line = "01-15 12:34:56.789 D/SomeTag( 1234): Regular log message"

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertNull(entry.deepLinkEvent)
        assertTrue(!entry.isDeepLinkEvent)
    }

    // --- Edge Cases ---

    @Test
    fun `parseLine should return null for empty string`() {
        val entry = parser.parseLine("")
        assertNull(entry, "Empty string should return null")
    }

    @Test
    fun `parseLine should return null for blank string`() {
        val entry = parser.parseLine("   ")
        assertNull(entry, "Blank string should return null")
    }

    @Test
    fun `parseLine should return null for logcat separator lines`() {
        assertNull(parser.parseLine("--------- beginning of main"))
        assertNull(parser.parseLine("--------- beginning of system"))
        assertNull(parser.parseLine("--- separator ---"))
    }

    @Test
    fun `parseLine should handle ADB error messages gracefully`() {
        val entry = parser.parseLine("error: device not found")

        assertNotNull(entry, "Should handle error message as unstructured line")
        assertEquals(LogLevel.UNKNOWN, entry.level)
        assertEquals("", entry.tag)
        assertTrue(entry.message.contains("error: device not found"))
    }

    @Test
    fun `parseLine should handle permission denied messages`() {
        val entry = parser.parseLine("Permission denied")

        assertNotNull(entry)
        assertEquals(LogLevel.UNKNOWN, entry.level)
    }

    @Test
    fun `parseLine should handle truncated output`() {
        val entry = parser.parseLine("01-15 12:34")

        // Should attempt to parse, might return unstructured entry
        if (entry != null) {
            assertEquals(LogLevel.UNKNOWN, entry.level)
        }
    }

    @Test
    fun `parseLine should handle special characters in messages`() {
        val line = "01-15 12:34:56.789 I/TestTag( 1234): Message with \$pecial ch@rs & <xml> \"quotes\""

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertTrue(entry.message.contains("\$pecial"), "Should preserve special characters")
    }

    @Test
    fun `parseLine should handle large PID numbers`() {
        val line = "01-15 12:34:56.789 I/TestTag(99999): Test message"

        val entry = parser.parseLine(line)

        assertNotNull(entry)
        assertEquals("TestTag", entry.tag)
    }

    @Test
    fun `parseLine should handle unknown log level character`() {
        val line = "01-15 12:34:56.789 X/TestTag( 1234): Test message"

        val entry = parser.parseLine(line)

        // X is not a recognized level - should either be UNKNOWN or fail to parse
        if (entry != null && entry.tag == "TestTag") {
            assertEquals(LogLevel.UNKNOWN, entry.level)
        }
    }

    // --- Multiple Entry Parsing Tests ---

    @Test
    fun `parseLine should handle consecutive entries independently`() {
        val lines = listOf(
            "01-15 12:34:56.789 I/ActivityTaskManager( 1234): START u0 {dat=https://test.com}",
            "01-15 12:34:56.790 D/IntentResolver( 1234): Resolving intent {dat=https://test.com}",
            "01-15 12:34:56.791 I/ActivityTaskManager( 1234): Displayed com.example/.Main: +100ms"
        )

        val entries = lines.mapNotNull { parser.parseLine(it) }

        assertEquals(3, entries.size)
        assertEquals(DeepLinkEventType.STARTED, entries[0].deepLinkEvent?.type)
        assertEquals(DeepLinkEventType.RESOLVED, entries[1].deepLinkEvent?.type)
        assertEquals(DeepLinkEventType.RESULT, entries[2].deepLinkEvent?.type)
    }

    // --- LogLevel Enum Tests ---

    @Test
    fun `LogLevel initial should return correct character`() {
        assertEquals("V", LogLevel.VERBOSE.initial)
        assertEquals("D", LogLevel.DEBUG.initial)
        assertEquals("I", LogLevel.INFO.initial)
        assertEquals("W", LogLevel.WARNING.initial)
        assertEquals("E", LogLevel.ERROR.initial)
        assertEquals("F", LogLevel.FATAL.initial)
        assertEquals("?", LogLevel.UNKNOWN.initial)
    }

    // --- Intent Description Extraction ---

    @Test
    fun `parseLine should extract URI from dat= in START events`() {
        val line = "01-15 12:34:56.789 I/ActivityTaskManager( 1234): START u0 {act=android.intent.action.VIEW dat=https://example.com/path?q=test cmp=com.example/.Main}"

        val entry = parser.parseLine(line)

        assertNotNull(entry?.deepLinkEvent)
        assertTrue(
            entry!!.deepLinkEvent!!.description.contains("https://example.com/path?q=test"),
            "Description should contain the URI"
        )
    }

    @Test
    fun `parseLine should extract component from cmp= in START events`() {
        val line = "01-15 12:34:56.789 I/ActivityTaskManager( 1234): START u0 {dat=https://test.com cmp=com.example.app/.MainActivity}"

        val entry = parser.parseLine(line)

        assertNotNull(entry?.deepLinkEvent)
        assertTrue(
            entry!!.deepLinkEvent!!.description.contains("com.example.app/.MainActivity"),
            "Description should contain the component"
        )
    }
}
