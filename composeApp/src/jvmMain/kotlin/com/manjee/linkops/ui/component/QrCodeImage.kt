package com.manjee.linkops.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.manjee.linkops.infrastructure.qr.QrCodeGenerator

/**
 * Composable that renders a QR code using Canvas
 *
 * @param content URI string to encode as QR code
 * @param qrCodeGenerator Generator instance for creating the QR matrix
 * @param modifier Modifier for the Canvas
 */
@Composable
fun QrCodeImage(
    content: String,
    qrCodeGenerator: QrCodeGenerator,
    modifier: Modifier = Modifier
) {
    val matrix = remember(content) {
        qrCodeGenerator.generateMatrix(content)
    }

    Canvas(modifier = modifier) {
        val matrixHeight = matrix.size
        val matrixWidth = if (matrixHeight > 0) matrix[0].size else 0
        if (matrixWidth == 0 || matrixHeight == 0) return@Canvas

        val cellWidth = size.width / matrixWidth
        val cellHeight = size.height / matrixHeight

        // Draw white background
        drawRect(Color.White, Offset.Zero, size)

        // Draw dark modules
        for (y in matrix.indices) {
            for (x in matrix[y].indices) {
                if (matrix[y][x]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cellWidth, y * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )
                }
            }
        }
    }
}
