package com.manjee.linkops.infrastructure.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/**
 * Generates QR codes from URI strings with automatic error correction level optimization.
 *
 * Error correction levels are selected based on URI length:
 * - URI <= 300 chars: Level H (30% recovery, best scan reliability)
 * - URI 301~500 chars: Level M (15% recovery, medium density)
 * - URI > 500 chars: Level L (7% recovery, lowest density)
 */
class QrCodeGenerator {

    /**
     * Generates a QR code matrix for Canvas rendering
     *
     * @param content URI string to encode
     * @return Boolean matrix where true = dark module, false = light module
     */
    fun generateMatrix(content: String): Array<BooleanArray> {
        val ecLevel = selectErrorCorrectionLevel(content)
        return encodeToMatrix(content, ecLevel)
    }

    /**
     * Generates a QR code as a BufferedImage for PNG export
     *
     * @param content URI string to encode
     * @param size Image dimension in pixels (width = height)
     * @return BufferedImage with the QR code
     */
    fun generate(content: String, size: Int = DEFAULT_IMAGE_SIZE): BufferedImage {
        val matrix = generateMatrix(content)
        return renderToImage(matrix, size)
    }

    /**
     * Generates a QR code with metadata about the generation process
     *
     * @param content URI string to encode
     * @return QrGenerationResult containing matrix, error correction level, and optional warning
     */
    fun generateWithInfo(content: String): QrGenerationResult {
        val ecLevel = selectErrorCorrectionLevel(content)
        val warning = buildWarning(content, ecLevel)
        val matrix = encodeToMatrix(content, ecLevel)
        return QrGenerationResult(
            matrix = matrix,
            errorCorrectionLevel = ecLevel,
            warning = warning
        )
    }

    private fun selectErrorCorrectionLevel(content: String): ErrorCorrectionLevel {
        return when {
            content.length <= SHORT_URI_THRESHOLD -> ErrorCorrectionLevel.H
            content.length <= MEDIUM_URI_THRESHOLD -> ErrorCorrectionLevel.M
            else -> ErrorCorrectionLevel.L
        }
    }

    private fun buildWarning(content: String, ecLevel: ErrorCorrectionLevel): String? {
        if (content.length > MEDIUM_URI_THRESHOLD) {
            return "URI is very long (${content.length} chars). QR code uses low error correction and may be difficult to scan."
        }
        return null
    }

    private fun encodeToMatrix(content: String, ecLevel: ErrorCorrectionLevel): Array<BooleanArray> {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ecLevel,
            EncodeHintType.MARGIN to QR_MARGIN
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        return Array(height) { y ->
            BooleanArray(width) { x ->
                bitMatrix.get(x, y)
            }
        }
    }

    private fun renderToImage(matrix: Array<BooleanArray>, size: Int): BufferedImage {
        val matrixHeight = matrix.size
        val matrixWidth = if (matrixHeight > 0) matrix[0].size else 0

        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()

        // Fill white background
        graphics.color = java.awt.Color.WHITE
        graphics.fillRect(0, 0, size, size)

        // Draw dark modules
        graphics.color = java.awt.Color.BLACK
        val cellWidth = size.toDouble() / matrixWidth
        val cellHeight = size.toDouble() / matrixHeight

        for (y in matrix.indices) {
            for (x in matrix[y].indices) {
                if (matrix[y][x]) {
                    val px = (x * cellWidth).toInt()
                    val py = (y * cellHeight).toInt()
                    val pw = ((x + 1) * cellWidth).toInt() - px
                    val ph = ((y + 1) * cellHeight).toInt() - py
                    graphics.fillRect(px, py, pw, ph)
                }
            }
        }

        graphics.dispose()
        return image
    }

    companion object {
        const val SHORT_URI_THRESHOLD = 300
        const val MEDIUM_URI_THRESHOLD = 500
        const val DEFAULT_IMAGE_SIZE = 512
        private const val QR_MARGIN = 1
    }
}

/**
 * Result of QR code generation with metadata
 *
 * @param matrix Boolean matrix where true = dark module
 * @param errorCorrectionLevel The EC level used for this generation
 * @param warning Optional warning message (null = OK)
 */
data class QrGenerationResult(
    val matrix: Array<BooleanArray>,
    val errorCorrectionLevel: ErrorCorrectionLevel,
    val warning: String?
) {
    /**
     * Human-readable description of the error correction level
     */
    val ecLevelDescription: String
        get() = when (errorCorrectionLevel) {
            ErrorCorrectionLevel.H -> "H (30% recovery)"
            ErrorCorrectionLevel.M -> "M (15% recovery)"
            ErrorCorrectionLevel.Q -> "Q (25% recovery)"
            ErrorCorrectionLevel.L -> "L (7% recovery)"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrGenerationResult) return false
        return matrix.contentDeepEquals(other.matrix) &&
            errorCorrectionLevel == other.errorCorrectionLevel &&
            warning == other.warning
    }

    override fun hashCode(): Int {
        var result = matrix.contentDeepHashCode()
        result = 31 * result + errorCorrectionLevel.hashCode()
        result = 31 * result + (warning?.hashCode() ?: 0)
        return result
    }
}
