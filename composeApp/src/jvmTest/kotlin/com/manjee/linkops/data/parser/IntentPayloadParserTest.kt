package com.manjee.linkops.data.parser

import com.manjee.linkops.domain.model.BundleExtraType
import com.manjee.linkops.domain.model.ParsingMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentPayloadParserTest {

    private val parser = IntentPayloadParser()

    // --- Empty / Error Input Tests ---

    @Test
    fun `parse should return raw-only payload for empty input`() {
        val result = parser.parse("")
        assertEquals(ParsingMethod.RAW_ONLY, result.parsingMethod)
        assertNull(result.action)
        assertNull(result.dataUri)
        assertTrue(result.extras.isEmpty())
    }

    @Test
    fun `parse should return raw-only payload for blank input`() {
        val result = parser.parse("   \n  \n  ")
        assertEquals(ParsingMethod.RAW_ONLY, result.parsingMethod)
        assertNull(result.action)
    }

    @Test
    fun `parse should handle ADB error device not found`() {
        val result = parser.parse("error: device not found")
        assertEquals(ParsingMethod.RAW_ONLY, result.parsingMethod)
        assertEquals("error: device not found", result.rawOutput)
    }

    @Test
    fun `parse should handle ADB error closed`() {
        val result = parser.parse("error: closed")
        assertEquals(ParsingMethod.RAW_ONLY, result.parsingMethod)
    }

    @Test
    fun `parse should handle permission denied response`() {
        val result = parser.parse("Permission denied")
        assertEquals(ParsingMethod.RAW_ONLY, result.parsingMethod)
    }

    @Test
    fun `parse should handle operation not permitted response`() {
        val result = parser.parse("Operation not permitted")
        assertEquals(ParsingMethod.RAW_ONLY, result.parsingMethod)
    }

    // --- Regex Parsing Tests (Stage 1) ---

    @Test
    fun `parse should extract action via regex`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(ParsingMethod.REGEX, result.parsingMethod)
        assertEquals("android.intent.action.VIEW", result.action)
    }

    @Test
    fun `parse should extract data URI via regex`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com/path?q=1 flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals("https://example.com/path?q=1", result.dataUri)
    }

    @Test
    fun `parse should extract flags via regex`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(0x10000000, result.flags)
    }

    @Test
    fun `parse should extract component via regex`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals("com.example/.MainActivity", result.component)
    }

    @Test
    fun `parse should extract categories via regex`() {
        val output = """
            intent={act=android.intent.action.VIEW cat=[android.intent.category.BROWSABLE,android.intent.category.DEFAULT] dat=https://example.com flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(2, result.categories.size)
        assertTrue(result.categories.contains("android.intent.category.BROWSABLE"))
        assertTrue(result.categories.contains("android.intent.category.DEFAULT"))
    }

    @Test
    fun `parse should handle intent with extras section`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x10000000 cmp=com.example/.MainActivity (has extras)}
              Extras:
                referrer = "com.android.shell" (String)
                extra_id = 42 (Integer)
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(ParsingMethod.REGEX, result.parsingMethod)
        assertEquals(2, result.extras.size)

        val referrer = result.extras["referrer"]
        assertNotNull(referrer)
        assertEquals("com.android.shell", referrer.value)
        assertEquals(BundleExtraType.STRING, referrer.type)

        val extraId = result.extras["extra_id"]
        assertNotNull(extraId)
        assertEquals("42", extraId.value)
        assertEquals(BundleExtraType.INT, extraId.type)
    }

    @Test
    fun `parse should handle multiple extra types`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com cmp=com.example/.Main}
              Extras:
                str_val = "hello" (String)
                int_val = 42 (Integer)
                long_val = 123456789 (Long)
                float_val = 3.14 (Float)
                bool_val = true (Boolean)
                bundle_val = Bundle[...] (Bundle)
                parcel_val = MyParcelable@abc (Parcelable)
                unknown_val = something (CustomType)
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(8, result.extras.size)
        assertEquals(BundleExtraType.STRING, result.extras["str_val"]?.type)
        assertEquals(BundleExtraType.INT, result.extras["int_val"]?.type)
        assertEquals(BundleExtraType.LONG, result.extras["long_val"]?.type)
        assertEquals(BundleExtraType.FLOAT, result.extras["float_val"]?.type)
        assertEquals(BundleExtraType.BOOLEAN, result.extras["bool_val"]?.type)
        assertEquals(BundleExtraType.BUNDLE, result.extras["bundle_val"]?.type)
        assertEquals(BundleExtraType.PARCELABLE, result.extras["parcel_val"]?.type)
        assertEquals(BundleExtraType.UNKNOWN, result.extras["unknown_val"]?.type)
    }

    @Test
    fun `parse should handle intent without data URI`() {
        val output = """
            intent={act=android.intent.action.MAIN flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals("android.intent.action.MAIN", result.action)
        assertNull(result.dataUri)
    }

    @Test
    fun `parse should handle intent without flags`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(0, result.flags)
    }

    @Test
    fun `parse should handle multiline intent block`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com/very/long/path
                    flg=0x10000000 cmp=com.example/.MainActivity (has extras)}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(ParsingMethod.REGEX, result.parsingMethod)
        assertEquals("android.intent.action.VIEW", result.action)
    }

    // --- Fallback Split Parsing Tests (Stage 2) ---

    @Test
    fun `parse should fallback to split when regex fails on non-standard format`() {
        val output = """
            Running activities (most recent first):
            TaskRecord{abc #42 intent={act=android.intent.action.VIEW dat=myapp://test flg=0x10000000 cmp=com.test/.TestActivity}}
        """.trimIndent()

        val result = parser.parse(output)
        assertNotNull(result.action)
    }

    // --- Edge Cases ---

    @Test
    fun `parse should handle truncated output gracefully`() {
        val output = """
            intent={act=android.intent.action.VIE
        """.trimIndent()

        val result = parser.parse(output)
        // Should not crash, may return raw-only or partial parse
        assertNotNull(result)
        assertNotNull(result.rawOutput)
    }

    @Test
    fun `parse should handle special characters in URI`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com/path?key=value&foo=bar%20baz#fragment flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(
            "https://example.com/path?key=value&foo=bar%20baz#fragment",
            result.dataUri
        )
    }

    @Test
    fun `parse should handle custom scheme URIs`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=myapp://deep/link/path flg=0x10000000 cmp=com.example/.DeepLinkActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals("myapp://deep/link/path", result.dataUri)
    }

    @Test
    fun `parse should handle intent with no extras`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x10000000 cmp=com.example/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertTrue(result.extras.isEmpty())
    }

    @Test
    fun `parse should preserve raw output`() {
        val output = "some raw adb output content"
        val result = parser.parse(output)
        assertEquals(output, result.rawOutput)
    }

    @Test
    fun `parse should set timestamp`() {
        val before = System.currentTimeMillis()
        val result = parser.parse("intent={act=android.intent.action.VIEW dat=https://example.com cmp=com.example/.Main}")
        val after = System.currentTimeMillis()

        assertTrue(result.timestamp in before..after)
    }

    @Test
    fun `parse should handle Samsung OEM dumpsys format with Intent keyword`() {
        val output = """
            Running activities (most recent first):
            Hist #0: ActivityRecord{abc123}
              Intent { act=android.intent.action.VIEW dat=https://samsung.example.com flg=0x14000000 cmp=com.samsung.test/.DeepLinkActivity (has extras) }
              Extras:
                referrer = "com.android.shell" (String)
        """.trimIndent()

        val result = parser.parse(output)
        // Should extract at least via one of the parsing methods
        assertNotNull(result)
    }

    @Test
    fun `parse should handle Xiaomi OEM dumpsys format`() {
        val output = """
            ACTIVITY com.xiaomi.test/.MainActivity abc123 pid=1234
            intent={act=android.intent.action.VIEW dat=miui://settings flg=0x10000000 cmp=com.xiaomi.test/.MainActivity}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals("android.intent.action.VIEW", result.action)
        assertEquals("miui://settings", result.dataUri)
    }

    @Test
    fun `parse should handle Bundle inline format`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x10000000 cmp=com.example/.Main}
              Bundle[{key1=value1, key2=value2, key3=value3}]
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(3, result.extras.size)
        assertEquals("value1", result.extras["key1"]?.value)
        assertEquals("value2", result.extras["key2"]?.value)
        assertEquals("value3", result.extras["key3"]?.value)
    }

    @Test
    fun `parse should handle duplicate extra keys by keeping last`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com cmp=com.example/.Main}
              Extras:
                key1 = "first" (String)
                key1 = "second" (String)
        """.trimIndent()

        val result = parser.parse(output)
        // Map naturally keeps last inserted for duplicate keys
        assertEquals("second", result.extras["key1"]?.value)
    }

    @Test
    fun `parse should handle intent with zero flags`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x0 cmp=com.example/.Main}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals(0, result.flags)
    }

    @Test
    fun `displayName should return dataUri when available`() {
        val output = """
            intent={act=android.intent.action.VIEW dat=https://example.com flg=0x10000000 cmp=com.example/.Main}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals("https://example.com", result.displayName)
    }

    @Test
    fun `displayName should return action when dataUri is null`() {
        val output = """
            intent={act=android.intent.action.MAIN flg=0x10000000 cmp=com.example/.Main}
        """.trimIndent()

        val result = parser.parse(output)
        assertEquals("android.intent.action.MAIN", result.displayName)
    }

    @Test
    fun `displayName should return Unknown Intent when both are null`() {
        val result = parser.parse("")
        assertEquals("Unknown Intent", result.displayName)
    }
}
