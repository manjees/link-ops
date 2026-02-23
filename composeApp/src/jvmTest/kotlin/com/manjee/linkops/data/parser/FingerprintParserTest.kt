package com.manjee.linkops.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FingerprintParserTest {

    private val parser = FingerprintParser()

    // --- parseFromGetAppLinks tests ---

    @Test
    fun `parseFromGetAppLinks should extract fingerprint for target package`() {
        val output = """
            com.example.app:
              ID: 12345678-1234-1234-1234-123456789012
              Signatures: [AB:CD:EF:01:23:45:67:89]
              Domain verification state:
                example.com: verified
        """.trimIndent()

        val result = parser.parseFromGetAppLinks(output, "com.example.app")
        assertEquals("AB:CD:EF:01:23:45:67:89", result)
    }

    @Test
    fun `parseFromGetAppLinks should return null for empty input`() {
        val result = parser.parseFromGetAppLinks("", "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseFromGetAppLinks should return null for blank input`() {
        val result = parser.parseFromGetAppLinks("   ", "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseFromGetAppLinks should return null when package not found`() {
        val output = """
            com.other.app:
              ID: 12345678-1234-1234-1234-123456789012
              Signatures: [AB:CD:EF:01:23:45:67:89]
        """.trimIndent()

        val result = parser.parseFromGetAppLinks(output, "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseFromGetAppLinks should handle multiple packages`() {
        val output = """
            com.first.app:
              ID: 11111111-1111-1111-1111-111111111111
              Signatures: [11:22:33:44:55:66:77:88]
              Domain verification state:
                first.com: verified
            com.second.app:
              ID: 22222222-2222-2222-2222-222222222222
              Signatures: [AA:BB:CC:DD:EE:FF:00:11]
              Domain verification state:
                second.com: none
        """.trimIndent()

        val result = parser.parseFromGetAppLinks(output, "com.second.app")
        assertEquals("AA:BB:CC:DD:EE:FF:00:11", result)
    }

    @Test
    fun `parseFromGetAppLinks should return null for ADB error output`() {
        val output = "error: device not found"
        val result = parser.parseFromGetAppLinks(output, "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseFromGetAppLinks should return null for permission denied`() {
        val output = "Permission denied"
        val result = parser.parseFromGetAppLinks(output, "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseFromGetAppLinks should handle empty Signatures brackets`() {
        val output = """
            com.example.app:
              ID: 12345678-1234-1234-1234-123456789012
              Signatures: []
              Domain verification state:
                example.com: none
        """.trimIndent()

        val result = parser.parseFromGetAppLinks(output, "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseFromGetAppLinks should return null for truncated output`() {
        val output = """
            com.example.app:
              ID: 12345678-1234
        """.trimIndent()

        val result = parser.parseFromGetAppLinks(output, "com.example.app")
        assertNull(result)
    }

    // --- parseFromDumpsysPackage tests ---

    @Test
    fun `parseFromDumpsysPackage should extract SHA-256 fingerprint`() {
        val output = """
            Package [com.example.app]:
              Signing details:
                Cert SHA-256 digest: AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89
                Subject: C=US, ST=California
        """.trimIndent()

        val result = parser.parseFromDumpsysPackage(output)
        assertEquals(
            "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89",
            result
        )
    }

    @Test
    fun `parseFromDumpsysPackage should return null for empty input`() {
        val result = parser.parseFromDumpsysPackage("")
        assertNull(result)
    }

    @Test
    fun `parseFromDumpsysPackage should return null for blank input`() {
        val result = parser.parseFromDumpsysPackage("   ")
        assertNull(result)
    }

    @Test
    fun `parseFromDumpsysPackage should return null when no signing section`() {
        val output = """
            Package [com.example.app]:
              userId=10001
              firstInstallTime=2024-01-01
              lastUpdateTime=2024-01-01
        """.trimIndent()

        val result = parser.parseFromDumpsysPackage(output)
        assertNull(result)
    }

    @Test
    fun `parseFromDumpsysPackage should return null for ADB error`() {
        val result = parser.parseFromDumpsysPackage("error: device not found")
        assertNull(result)
    }

    @Test
    fun `parseFromDumpsysPackage should return null for permission denied`() {
        val result = parser.parseFromDumpsysPackage("Permission denied")
        assertNull(result)
    }

    @Test
    fun `parseFromDumpsysPackage should return null for error closed`() {
        val result = parser.parseFromDumpsysPackage("error: closed")
        assertNull(result)
    }

    @Test
    fun `parseFromDumpsysPackage should handle alternative signing section header`() {
        val output = """
            Package [com.example.app]:
              signatures:
                Cert SHA-256 digest: FF:EE:DD:CC:BB:AA:99:88:77:66:55:44:33:22:11:00:FF:EE:DD:CC:BB:AA:99:88:77:66:55:44:33:22:11:00
        """.trimIndent()

        val result = parser.parseFromDumpsysPackage(output)
        assertEquals(
            "FF:EE:DD:CC:BB:AA:99:88:77:66:55:44:33:22:11:00:FF:EE:DD:CC:BB:AA:99:88:77:66:55:44:33:22:11:00",
            result
        )
    }

    @Test
    fun `parseFromDumpsysPackage should return null for Operation not permitted`() {
        val result = parser.parseFromDumpsysPackage("Operation not permitted")
        assertNull(result)
    }

    @Test
    fun `parseFromDumpsysPackage should handle special characters in output`() {
        val output = """
            Package [com.example.app-debug]:
              Signing details:
                Cert SHA-256 digest: 12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0
        """.trimIndent()

        val result = parser.parseFromDumpsysPackage(output)
        assertEquals(
            "12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0",
            result
        )
    }
}
