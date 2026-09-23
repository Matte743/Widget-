package com.matte743.nothingqs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** A 5x7 dot-matrix font, drawn as round dots like Nothing's NDot typeface. */
object DotFont {
    private const val W = 5
    private const val H = 7

    private val glyphs: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        '%' to arrayOf("11000", "11001", "00010", "00100", "01000", "10011", "00011"),
        '-' to arrayOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
        '.' to arrayOf("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
        ' ' to arrayOf("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
        'A' to arrayOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to arrayOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to arrayOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
        'D' to arrayOf("11100", "10010", "10001", "10001", "10001", "10010", "11100"),
        'E' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'F' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to arrayOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
        'H' to arrayOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'I' to arrayOf("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
        'J' to arrayOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
        'K' to arrayOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'L' to arrayOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'M' to arrayOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to arrayOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
        'O' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'P' to arrayOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'Q' to arrayOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        'R' to arrayOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to arrayOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to arrayOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'V' to arrayOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        'W' to arrayOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
        'X' to arrayOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        'Y' to arrayOf("10001", "10001", "10001", "01010", "00100", "00100", "00100"),
        'Z' to arrayOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    )

    /** Width in dot columns of [text], including one blank column between letters. */
    fun columns(text: String): Int = if (text.isEmpty()) 0 else text.length * (W + 1) - 1

    const val ROWS = H

    /** Draws [text] onto [canvas] starting at ([left], [top]) with square cells of [cell] px. */
    fun draw(canvas: Canvas, text: String, left: Float, top: Float, cell: Float, paint: Paint) {
        val radius = cell * 0.40f
        text.uppercase().forEachIndexed { index, ch ->
            val glyph = glyphs[ch] ?: glyphs.getValue(' ')
            val x0 = left + index * (W + 1) * cell
            for (row in 0 until H) {
                val line = glyph[row]
                for (col in 0 until W) {
                    if (line[col] == '1') {
                        canvas.drawCircle(x0 + (col + 0.5f) * cell, top + (row + 0.5f) * cell, radius, paint)
                    }
                }
            }
        }
    }

    /** Renders white dots on a transparent bitmap; tint it with the view's colour. */
    fun render(text: String, cellPx: Int = 6): Bitmap {
        val cols = columns(text).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(cols * cellPx, H * cellPx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        draw(Canvas(bitmap), text, 0f, 0f, cellPx.toFloat(), paint)
        return bitmap
    }
}
