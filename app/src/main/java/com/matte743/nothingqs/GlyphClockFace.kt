package com.matte743.nothingqs

/**
 * Stacked Glyph Matrix clock: hours on the top line, minutes on the bottom line,
 * in the style of the Phone (4a) Pro clock.
 *
 * The Phone (4a) Pro matrix is 13x13 and draws each digit 4x5 LEDs wide with
 * one-LED strokes. The Phone (3) matrix is 25x25, so the same skeleton is
 * redrawn at 7x10 with two-LED strokes and rounded outer corners. The block
 * of four digits (15x21) fills the circle of the Phone (3) without touching
 * a missing corner LED.
 *
 * Pure Kotlin with no Android dependencies, so it can be tested on the JVM.
 */
object GlyphClockFace {

    const val MAX_BRIGHTNESS = 4095

    /** How the four digits are placed on a matrix of a given side. */
    class Layout(
        val side: Int,
        val digitWidth: Int,
        val digitHeight: Int,
        /** Blank LED columns between the two digits of a line. */
        val columnGap: Int,
        /** Blank LED rows between the hours line and the minutes line. */
        val rowGap: Int,
        /** Number of LEDs lit in each row, centred, from top to bottom. */
        val rowWidths: IntArray,
        val glyphs: Map<Char, Array<String>>,
    ) {
        val blockWidth get() = digitWidth * 2 + columnGap
        val blockHeight get() = digitHeight * 2 + rowGap
        val left get() = (side - blockWidth) / 2
        val top get() = (side - blockHeight) / 2

        /** True if the matrix has a physical LED at ([row], [col]). */
        fun hasLed(row: Int, col: Int): Boolean {
            if (row !in 0 until side || col !in 0 until side) return false
            val width = rowWidths[row]
            val start = (side - width) / 2
            return col in start until start + width
        }
    }

    /** Phone (3): 25x25 circle, 489 LEDs. 7x10 digits with two-LED strokes. */
    val PHONE_3 = Layout(
        side = 25,
        digitWidth = 7,
        digitHeight = 10,
        columnGap = 1,
        rowGap = 1,
        rowWidths = intArrayOf(
            7, 11, 15, 17, 19, 21, 21, 23, 23,
            25, 25, 25, 25, 25, 25, 25,
            23, 23, 21, 21, 19, 17, 15, 11, 7,
        ),
        glyphs = mapOf(
            '0' to arrayOf(
                ".#####.",
                "#######",
                "##...##",
                "##...##",
                "##...##",
                "##...##",
                "##...##",
                "##...##",
                "#######",
                ".#####.",
            ),
            '1' to arrayOf(
                "...##..",
                "..###..",
                ".####..",
                "...##..",
                "...##..",
                "...##..",
                "...##..",
                "...##..",
                ".######",
                ".######",
            ),
            '2' to arrayOf(
                "######.",
                "#######",
                ".....##",
                ".....##",
                ".######",
                "######.",
                "##.....",
                "##.....",
                "#######",
                "#######",
            ),
            '3' to arrayOf(
                "######.",
                "#######",
                ".....##",
                ".....##",
                "..#####",
                "..#####",
                ".....##",
                ".....##",
                "#######",
                "######.",
            ),
            '4' to arrayOf(
                "##...##",
                "##...##",
                "##...##",
                "##...##",
                "#######",
                ".######",
                ".....##",
                ".....##",
                ".....##",
                ".....##",
            ),
            '5' to arrayOf(
                "#######",
                "#######",
                "##.....",
                "##.....",
                "######.",
                "#######",
                ".....##",
                ".....##",
                "#######",
                "######.",
            ),
            '6' to arrayOf(
                ".######",
                "#######",
                "##.....",
                "##.....",
                "######.",
                "#######",
                "##...##",
                "##...##",
                "#######",
                ".#####.",
            ),
            '7' to arrayOf(
                "#######",
                "#######",
                ".....##",
                "....###",
                "...###.",
                "..###..",
                "..##...",
                "..##...",
                "..##...",
                "..##...",
            ),
            '8' to arrayOf(
                ".#####.",
                "#######",
                "##...##",
                "##...##",
                ".#####.",
                ".#####.",
                "##...##",
                "##...##",
                "#######",
                ".#####.",
            ),
            '9' to arrayOf(
                ".#####.",
                "#######",
                "##...##",
                "##...##",
                "#######",
                ".######",
                ".....##",
                ".....##",
                "#######",
                "######.",
            ),
        ),
    )

    /** Phone (4a) Pro: 13x13 circle, 137 LEDs. The original 4x5 layout. */
    val PHONE_4A_PRO = Layout(
        side = 13,
        digitWidth = 4,
        digitHeight = 5,
        columnGap = 1,
        rowGap = 1,
        rowWidths = intArrayOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5),
        glyphs = mapOf(
            '0' to arrayOf("####", "#..#", "#..#", "#..#", "####"),
            '1' to arrayOf("..#.", ".##.", "..#.", "..#.", ".###"),
            '2' to arrayOf("####", "...#", "####", "#...", "####"),
            '3' to arrayOf("####", "...#", ".###", "...#", "####"),
            '4' to arrayOf("#..#", "#..#", "####", "...#", "...#"),
            '5' to arrayOf("####", "#...", "####", "...#", "####"),
            '6' to arrayOf("####", "#...", "####", "#..#", "####"),
            '7' to arrayOf("####", "...#", "..#.", ".#..", ".#.."),
            '8' to arrayOf("####", "#..#", "####", "#..#", "####"),
            '9' to arrayOf("####", "#..#", "####", "...#", "####"),
        ),
    )

    /** Layout for a matrix of [side] LEDs; anything that is not 13 uses the Phone (3) one. */
    fun layoutFor(side: Int): Layout = if (side == PHONE_4A_PRO.side) PHONE_4A_PRO else PHONE_3

    /**
     * Row-major frame of `side * side` brightness values (0..4095) showing
     * [hours] above [minutes], both with two digits.
     */
    fun frame(
        hours: Int,
        minutes: Int,
        layout: Layout = PHONE_3,
        brightness: Int = MAX_BRIGHTNESS,
    ): IntArray {
        val side = layout.side
        val pixels = IntArray(side * side)
        val level = brightness.coerceIn(0, MAX_BRIGHTNESS)
        val lines = listOf(twoDigits(hours), twoDigits(minutes))
        lines.forEachIndexed { line, text ->
            val top = layout.top + line * (layout.digitHeight + layout.rowGap)
            text.forEachIndexed { index, ch ->
                val left = layout.left + index * (layout.digitWidth + layout.columnGap)
                val glyph = layout.glyphs.getValue(ch)
                for (r in 0 until layout.digitHeight) {
                    for (c in 0 until layout.digitWidth) {
                        if (glyph[r][c] == '#') pixels[(top + r) * side + left + c] = level
                    }
                }
            }
        }
        return pixels
    }

    private fun twoDigits(value: Int): String = (((value % 100) + 100) % 100).toString().padStart(2, '0')
}
