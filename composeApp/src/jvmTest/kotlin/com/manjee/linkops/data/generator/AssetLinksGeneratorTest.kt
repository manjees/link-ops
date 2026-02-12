package com.manjee.linkops.data.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssetLinksGeneratorTest {

    private val generator = AssetLinksGenerator()

    @Test
    fun `generate should produce valid JSON with single fingerprint`() {
        val result = generator.generate(
            "com.example.app",
            listOf("AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89")
        )

        assertTrue(result.contains("delegate_permission/common.handle_all_urls"))
        assertTrue(result.contains("android_app"))
        assertTrue(result.contains("com.example.app"))
        assertTrue(result.contains("AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89"))
    }

    @Test
    fun `generate should produce valid JSON with multiple fingerprints`() {
        val fingerprints = listOf(
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
            "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
        )
        val result = generator.generate("com.example.app", fingerprints)

        assertTrue(result.contains("AA:BB:CC:DD"))
        assertTrue(result.contains("11:22:33:44"))
    }

    @Test
    fun `generate should contain correct JSON structure`() {
        val result = generator.generate(
            "com.test.pkg",
            listOf("AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89")
        )

        assertTrue(result.contains("\"relation\""))
        assertTrue(result.contains("\"target\""))
        assertTrue(result.contains("\"namespace\""))
        assertTrue(result.contains("\"package_name\""))
        assertTrue(result.contains("\"sha256_cert_fingerprints\""))
    }

    @Test
    fun `generate should throw for blank package name`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generate("", listOf("AB:CD:EF:01"))
        }
    }

    @Test
    fun `generate should throw for whitespace-only package name`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generate("   ", listOf("AB:CD:EF:01"))
        }
    }

    @Test
    fun `generate should throw for empty fingerprints list`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generate("com.example.app", emptyList())
        }
    }

    @Test
    fun `generate should normalize lowercase fingerprint to uppercase`() {
        val result = generator.generate(
            "com.example.app",
            listOf("ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89")
        )

        assertTrue(result.contains("AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89"))
    }

    @Test
    fun `generate should handle fingerprint without colons`() {
        val hexNoColons = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789"
        val result = generator.generate("com.example.app", listOf(hexNoColons))

        // Should be normalized to colon-separated format
        assertTrue(result.contains("AB:CD:EF:01:23:45:67:89"))
    }

    @Test
    fun `generate should produce parseable JSON array`() {
        val result = generator.generate(
            "com.example.app",
            listOf("AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89")
        )

        assertTrue(result.trimStart().startsWith("["))
        assertTrue(result.trimEnd().endsWith("]"))
    }

    @Test
    fun `generate should handle special characters in package name`() {
        val result = generator.generate(
            "com.example.my_app.debug",
            listOf("AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89")
        )

        assertTrue(result.contains("com.example.my_app.debug"))
    }
}
