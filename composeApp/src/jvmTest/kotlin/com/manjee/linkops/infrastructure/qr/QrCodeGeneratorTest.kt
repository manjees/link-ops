package com.manjee.linkops.infrastructure.qr

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrCodeGeneratorTest {

    private val generator = QrCodeGenerator()

    // --- generateMatrix tests ---

    @Test
    fun `generateMatrix should return non-empty matrix for simple URI`() {
        val matrix = generator.generateMatrix("https://example.com")
        assertTrue(matrix.isNotEmpty(), "Matrix should not be empty")
        assertTrue(matrix[0].isNotEmpty(), "Matrix rows should not be empty")
    }

    @Test
    fun `generateMatrix should return square matrix`() {
        val matrix = generator.generateMatrix("https://example.com/path")
        assertEquals(matrix.size, matrix[0].size, "Matrix should be square")
    }

    @Test
    fun `generateMatrix should contain both dark and light modules`() {
        val matrix = generator.generateMatrix("https://example.com")
        val hasDark = matrix.any { row -> row.any { it } }
        val hasLight = matrix.any { row -> row.any { !it } }
        assertTrue(hasDark, "Matrix should contain dark modules")
        assertTrue(hasLight, "Matrix should contain light modules")
    }

    @Test
    fun `generateMatrix should handle custom scheme URI`() {
        val matrix = generator.generateMatrix("myapp://product/123")
        assertTrue(matrix.isNotEmpty(), "Matrix should not be empty for custom scheme")
    }

    @Test
    fun `generateMatrix should handle URI with special characters`() {
        val matrix = generator.generateMatrix("https://example.com/path?q=hello%20world&lang=en")
        assertTrue(matrix.isNotEmpty(), "Matrix should handle special characters")
    }

    @Test
    fun `generateMatrix should handle very short URI`() {
        val matrix = generator.generateMatrix("a")
        assertTrue(matrix.isNotEmpty(), "Matrix should handle very short input")
    }

    // --- generate (BufferedImage) tests ---

    @Test
    fun `generate should return image with default size`() {
        val image = generator.generate("https://example.com")
        assertEquals(QrCodeGenerator.DEFAULT_IMAGE_SIZE, image.width, "Image width should match default")
        assertEquals(QrCodeGenerator.DEFAULT_IMAGE_SIZE, image.height, "Image height should match default")
    }

    @Test
    fun `generate should return image with custom size`() {
        val image = generator.generate("https://example.com", size = 256)
        assertEquals(256, image.width, "Image width should match custom size")
        assertEquals(256, image.height, "Image height should match custom size")
    }

    @Test
    fun `generate should produce image with black and white pixels`() {
        val image = generator.generate("https://example.com")
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()

        var hasBlack = false
        var hasWhite = false
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                if (rgb == black) hasBlack = true
                if (rgb == white) hasWhite = true
                if (hasBlack && hasWhite) break
            }
            if (hasBlack && hasWhite) break
        }
        assertTrue(hasBlack, "Image should contain black pixels")
        assertTrue(hasWhite, "Image should contain white pixels")
    }

    // --- generateWithInfo tests ---

    @Test
    fun `generateWithInfo should use EC level H for short URI`() {
        val result = generator.generateWithInfo("https://example.com")
        assertEquals(ErrorCorrectionLevel.H, result.errorCorrectionLevel, "Short URI should use EC level H")
        assertNull(result.warning, "Short URI should not have warning")
    }

    @Test
    fun `generateWithInfo should use EC level H for URI at 300 chars threshold`() {
        val uri = "https://example.com/" + "a".repeat(280)
        assertEquals(300, uri.length, "URI should be exactly 300 chars")
        val result = generator.generateWithInfo(uri)
        assertEquals(ErrorCorrectionLevel.H, result.errorCorrectionLevel, "URI at 300 chars should use EC level H")
        assertNull(result.warning, "URI at 300 chars should not have warning")
    }

    @Test
    fun `generateWithInfo should use EC level M for URI above 300 chars`() {
        val uri = "https://example.com/" + "a".repeat(281)
        assertEquals(301, uri.length, "URI should be 301 chars")
        val result = generator.generateWithInfo(uri)
        assertEquals(ErrorCorrectionLevel.M, result.errorCorrectionLevel, "URI at 301 chars should use EC level M")
        assertNull(result.warning, "URI at 301 chars should not have warning")
    }

    @Test
    fun `generateWithInfo should use EC level M for URI at 500 chars threshold`() {
        val uri = "https://example.com/" + "a".repeat(480)
        assertEquals(500, uri.length, "URI should be exactly 500 chars")
        val result = generator.generateWithInfo(uri)
        assertEquals(ErrorCorrectionLevel.M, result.errorCorrectionLevel, "URI at 500 chars should use EC level M")
        assertNull(result.warning, "URI at 500 chars should not have warning")
    }

    @Test
    fun `generateWithInfo should use EC level L for URI above 500 chars`() {
        val uri = "https://example.com/" + "a".repeat(481)
        assertEquals(501, uri.length, "URI should be 501 chars")
        val result = generator.generateWithInfo(uri)
        assertEquals(ErrorCorrectionLevel.L, result.errorCorrectionLevel, "URI above 500 chars should use EC level L")
        assertNotNull(result.warning, "Long URI should have warning")
        assertTrue(result.warning!!.contains("501"), "Warning should include URI length")
    }

    @Test
    fun `generateWithInfo should include warning for very long URI`() {
        val uri = "https://example.com/" + "a".repeat(1000)
        val result = generator.generateWithInfo(uri)
        assertEquals(ErrorCorrectionLevel.L, result.errorCorrectionLevel)
        assertNotNull(result.warning, "Very long URI should have warning")
        assertTrue(result.warning!!.contains("difficult to scan"), "Warning should mention scan difficulty")
    }

    // --- ecLevelDescription tests ---

    @Test
    fun `ecLevelDescription should return correct string for each level`() {
        val shortResult = generator.generateWithInfo("short")
        assertEquals("H (30% recovery)", shortResult.ecLevelDescription)

        val mediumUri = "https://example.com/" + "a".repeat(281)
        val medResult = generator.generateWithInfo(mediumUri)
        assertEquals("M (15% recovery)", medResult.ecLevelDescription)

        val longUri = "https://example.com/" + "a".repeat(481)
        val longResult = generator.generateWithInfo(longUri)
        assertEquals("L (7% recovery)", longResult.ecLevelDescription)
    }

    // --- Consistency tests ---

    @Test
    fun `generateMatrix should produce same result for same input`() {
        val content = "https://example.com/consistent"
        val matrix1 = generator.generateMatrix(content)
        val matrix2 = generator.generateMatrix(content)

        assertEquals(matrix1.size, matrix2.size, "Matrices should have same height")
        for (y in matrix1.indices) {
            assertTrue(
                matrix1[y].contentEquals(matrix2[y]),
                "Matrix row $y should be identical"
            )
        }
    }

    @Test
    fun `different URIs should produce different matrices`() {
        val matrix1 = generator.generateMatrix("https://example.com/a")
        val matrix2 = generator.generateMatrix("https://example.com/b")

        var hasDifference = false
        val minRows = minOf(matrix1.size, matrix2.size)
        for (y in 0 until minRows) {
            val minCols = minOf(matrix1[y].size, matrix2[y].size)
            for (x in 0 until minCols) {
                if (matrix1[y][x] != matrix2[y][x]) {
                    hasDifference = true
                    break
                }
            }
            if (hasDifference) break
        }
        assertTrue(hasDifference, "Different URIs should produce different matrices")
    }

    // --- Edge case: Unicode and special characters ---

    @Test
    fun `generateMatrix should handle URI with unicode characters`() {
        val matrix = generator.generateMatrix("https://example.com/path?name=\u00e9\u00e8\u00ea")
        assertTrue(matrix.isNotEmpty(), "Matrix should handle unicode characters")
    }

    @Test
    fun `generateMatrix should handle URI with fragments`() {
        val matrix = generator.generateMatrix("https://example.com/page#section-1")
        assertTrue(matrix.isNotEmpty(), "Matrix should handle fragment URIs")
    }

    @Test
    fun `generateMatrix should handle deep nested path`() {
        val matrix = generator.generateMatrix("https://example.com/a/b/c/d/e/f/g/h/i/j")
        assertTrue(matrix.isNotEmpty(), "Matrix should handle deep nested paths")
    }
}
