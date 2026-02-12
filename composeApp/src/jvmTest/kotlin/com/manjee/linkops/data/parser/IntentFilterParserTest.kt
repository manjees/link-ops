package com.manjee.linkops.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentFilterParserTest {

    private val parser = IntentFilterParser()

    @Test
    fun `parse should return empty list for empty string`() {
        val result = parser.parse("com.example", "")
        assertTrue(result.isEmpty(), "Empty input should return empty list")
    }

    @Test
    fun `parse should return empty list for blank string`() {
        val result = parser.parse("com.example", "   \n  \n  ")
        assertTrue(result.isEmpty(), "Blank input should return empty list")
    }

    @Test
    fun `parse should return empty list for adb error message`() {
        val result = parser.parse("com.example", "error: device not found")
        assertTrue(result.isEmpty(), "ADB error should return empty list")
    }

    @Test
    fun `parse should return empty list for error closed message`() {
        val result = parser.parse("com.example", "error: closed")
        assertTrue(result.isEmpty(), "ADB closed error should return empty list")
    }

    @Test
    fun `parse should return empty list for package not found`() {
        val result = parser.parse("com.example", "Unable to find package: com.example")
        assertTrue(result.isEmpty(), "Package not found should return empty list")
    }

    @Test
    fun `parse should return empty list for no Activity Resolver Table`() {
        val output = """
            Package [com.example] (abcdef1):
              versionCode=1
              versionName=1.0.0
              Some other content
        """.trimIndent()

        val result = parser.parse("com.example", output)
        assertTrue(result.isEmpty(), "No Activity Resolver Table should return empty list")
    }

    @Test
    fun `parse should extract single scheme registration`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size, "Should find one registration")
        assertEquals("com.example.app", result[0].packageName)
        assertEquals("com.example.app/.MainActivity", result[0].activityName)
        assertEquals("https", result[0].scheme)
        assertEquals("example.com", result[0].host)
    }

    @Test
    fun `parse should extract multiple schemes from same filter`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "http"
                    Scheme: "https"
                    Authority: "example.com": -1
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(2, result.size, "Should find two registrations (http + https)")

        val schemes = result.map { it.scheme }.toSet()
        assertTrue(schemes.contains("http"), "Should contain http scheme")
        assertTrue(schemes.contains("https"), "Should contain https scheme")
    }

    @Test
    fun `parse should detect autoVerify flag`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    AutoVerify=true
                    Scheme: "https"
                    Authority: "example.com": -1
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertTrue(result[0].autoVerify, "Should detect AutoVerify flag")
    }

    @Test
    fun `parse should extract path information`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
                    Path: "PatternMatcher{LITERAL: /products}"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertEquals("/products", result[0].path)
    }

    @Test
    fun `parse should extract path prefix`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
                    Path: "PatternMatcher{PREFIX: /products/}"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertEquals("/products/", result[0].pathPrefix)
    }

    @Test
    fun `parse should skip non-VIEW filters`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                custom:
                  abc1234 com.example.app/.OtherActivity filter def5678
                    Action: "android.intent.action.SEND"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "custom"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertTrue(result.isEmpty(), "Non-VIEW action should be skipped")
    }

    @Test
    fun `parse should skip non-BROWSABLE filters`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                custom:
                  abc1234 com.example.app/.OtherActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Scheme: "custom"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertTrue(result.isEmpty(), "Non-BROWSABLE filter should be skipped")
    }

    @Test
    fun `parse should handle multiple activities`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
                  ghi9012 com.example.app/.DeepLinkActivity filter jkl3456
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "other.example.com": -1
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(2, result.size, "Should find two registrations from different activities")

        val activities = result.map { it.activityName }.toSet()
        assertTrue(activities.contains("com.example.app/.MainActivity"))
        assertTrue(activities.contains("com.example.app/.DeepLinkActivity"))
    }

    @Test
    fun `parse should handle custom scheme without host`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                myapp:
                  abc1234 com.example.app/.DeepLinkActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "myapp"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertEquals("myapp", result[0].scheme)
        assertEquals(null, result[0].host)
    }

    @Test
    fun `parse should produce correct uriPattern`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
                    Path: "PatternMatcher{LITERAL: /products}"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertEquals("https://example.com/products", result[0].uriPattern)
    }

    @Test
    fun `parse should produce correct collisionKey`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertEquals("https://example.com", result[0].collisionKey)
    }

    @Test
    fun `parse should handle scheme and path combinations`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "http"
                    Scheme: "https"
                    Authority: "example.com": -1
                    Path: "PatternMatcher{LITERAL: /a}"
                    Path: "PatternMatcher{LITERAL: /b}"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        // 2 schemes x 2 paths = 4 registrations
        assertEquals(4, result.size, "Should create combinations of schemes and paths")
    }

    @Test
    fun `parse should handle truncated output gracefully`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
        """.trimIndent()

        // Should not crash, should extract whatever it can
        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size, "Should handle truncated output")
        assertEquals("https", result[0].scheme)
    }

    @Test
    fun `parse should handle permission denied output`() {
        val result = parser.parse("com.example", "Permission denied")
        // Should not crash — no Activity Resolver Table, so empty
        assertTrue(result.isEmpty(), "Permission denied should return empty list")
    }

    @Test
    fun `parse should handle special characters in host`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "*.example.com": -1
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertEquals("*.example.com", result[0].host)
    }

    @Test
    fun `parse should handle glob path pattern`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
                    Path: "PatternMatcher{GLOB: /products/.*}"
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size)
        assertEquals("/products/.*", result[0].pathPattern)
    }

    @Test
    fun `parse should handle duplicate registrations across filters`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
                  abc1234 com.example.app/.MainActivity filter ghi9012
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
            Receiver Resolver Table:
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(2, result.size, "Should include both filter registrations even if duplicate")
    }

    @Test
    fun `parse should stop at Receiver Resolver Table boundary`() {
        val output = """
            Activity Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.app/.MainActivity filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "example.com": -1
            Receiver Resolver Table:
              Schemes:
                https:
                  abc1234 com.example.receiver/.MyReceiver filter def5678
                    Action: "android.intent.action.VIEW"
                    Category: "android.intent.category.DEFAULT"
                    Category: "android.intent.category.BROWSABLE"
                    Scheme: "https"
                    Authority: "other.com": -1
        """.trimIndent()

        val result = parser.parse("com.example.app", output)
        assertEquals(1, result.size, "Should only parse Activity Resolver Table section")
    }
}
