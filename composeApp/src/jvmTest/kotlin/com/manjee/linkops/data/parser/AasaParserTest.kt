package com.manjee.linkops.data.parser

import com.manjee.linkops.domain.model.AasaIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AasaParserTest {

    private val parser = AasaParser()

    @Test
    fun `parses iOS 14+ schema with appIDs and components`() {
        val json = """
            {
              "applinks": {
                "details": [
                  {
                    "appIDs": ["ABCDE12345.com.example.app"],
                    "components": [
                      { "/": "/promo/*", "?": { "campaign": "?*" } },
                      { "/": "/admin", "exclude": true }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val result = parser.parse(json) as AasaParser.ParseResult.Success
        val applinks = assertNotNull(result.content.applinks)
        assertEquals(1, applinks.details.size)

        val detail = applinks.details.first()
        assertEquals(listOf("ABCDE12345.com.example.app"), detail.appIDs)
        assertEquals(2, detail.components.size)
        assertFalse(detail.usesLegacySchema, "appIDs schema should not be flagged as legacy")

        val excludeComponent = detail.components.last()
        assertTrue(excludeComponent.exclude)
    }

    @Test
    fun `parses legacy schema with appID and paths and flags as legacy`() {
        val json = """
            {
              "applinks": {
                "apps": [],
                "details": [
                  { "appID": "ABCDE12345.com.example.app", "paths": ["/promo/*", "/help"] }
                ]
              }
            }
        """.trimIndent()

        val result = parser.parse(json) as AasaParser.ParseResult.Success
        val detail = result.content.applinks!!.details.first()

        assertEquals(listOf("ABCDE12345.com.example.app"), detail.appIDs)
        assertEquals(listOf("/promo/*", "/help"), detail.paths)
        assertTrue(detail.usesLegacySchema, "Legacy appID detail should be flagged")
        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.LEGACY_SCHEMA })
    }

    @Test
    fun `flags missing applinks section`() {
        val json = """{ "webcredentials": { "apps": ["ABCDE12345.com.example.app"] } }"""
        val result = parser.parse(json) as AasaParser.ParseResult.Success

        assertNull(result.content.applinks)
        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.MISSING_APPLINKS })
        assertTrue(result.content.hasWebcredentials)
        assertFalse(result.content.hasAppclips)
    }

    @Test
    fun `flags empty details array`() {
        val json = """{ "applinks": { "details": [] } }"""
        val result = parser.parse(json) as AasaParser.ParseResult.Success

        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.EMPTY_DETAILS })
    }

    @Test
    fun `flags non-empty applinks apps array`() {
        val json = """
            {
              "applinks": {
                "apps": ["ABCDE12345.com.example.app"],
                "details": [
                  { "appIDs": ["ABCDE12345.com.example.app"], "components": [{"/": "*"}] }
                ]
              }
            }
        """.trimIndent()
        val result = parser.parse(json) as AasaParser.ParseResult.Success

        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.NON_EMPTY_APPS_ARRAY })
    }

    @Test
    fun `flags missing paths and components`() {
        val json = """
            {
              "applinks": {
                "details": [ { "appIDs": ["ABCDE12345.com.example.app"] } ]
              }
            }
        """.trimIndent()
        val result = parser.parse(json) as AasaParser.ParseResult.Success

        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.MISSING_PATHS_AND_COMPONENTS })
    }

    @Test
    fun `flags malformed app ID`() {
        val json = """
            {
              "applinks": {
                "details": [
                  { "appIDs": ["just-the-bundle-no-team"], "paths": ["*"] }
                ]
              }
            }
        """.trimIndent()
        val result = parser.parse(json) as AasaParser.ParseResult.Success

        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.INVALID_APP_ID_FORMAT })
    }

    @Test
    fun `accepts mixed legacy and modern entries in same details array`() {
        val json = """
            {
              "applinks": {
                "details": [
                  { "appID": "ABCDE12345.com.example.app", "paths": ["/legacy"] },
                  { "appIDs": ["ABCDE12345.com.example.app"], "components": [{"/": "/modern"}] }
                ]
              }
            }
        """.trimIndent()
        val result = parser.parse(json) as AasaParser.ParseResult.Success

        val details = result.content.applinks!!.details
        assertEquals(2, details.size)
        assertTrue(details[0].usesLegacySchema)
        assertFalse(details[1].usesLegacySchema)
    }

    @Test
    fun `surfaces appclips presence`() {
        val json = """
            {
              "applinks": {
                "details": [
                  { "appIDs": ["ABCDE12345.com.example.app"], "components": [{"/": "*"}] }
                ]
              },
              "appclips": { "apps": ["ABCDE12345.com.example.AppClip"] }
            }
        """.trimIndent()
        val result = parser.parse(json) as AasaParser.ParseResult.Success

        assertTrue(result.content.hasAppclips)
        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.APP_CLIPS_PRESENT })
    }

    @Test
    fun `returns Error for malformed JSON`() {
        val result = parser.parse("{ this is not json")
        assertTrue(result is AasaParser.ParseResult.Error)
        assertTrue(result.issues.any { it.code == AasaIssue.AasaIssueCode.INVALID_JSON_SYNTAX })
    }
}
