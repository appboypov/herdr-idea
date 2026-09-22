package dev.appboypov.herdridea.terminal.shared.models

/**
 * One immutable row of cells. Index `x` in every array describes column `x`.
 *
 * [text] holds the cell's grapheme cluster, or null for an empty cell and for the spacer
 * column after a wide character. Colours are `0xRRGGBB`, or [DEFAULT_COLOR] for the
 * terminal's default foreground or background. [flags] combines the `*_FLAG` bits.
 */
class ScreenRow(
    val text: Array<String?>,
    val fg: IntArray,
    val bg: IntArray,
    val flags: IntArray,
) {
    val width: Int get() = text.size

    /** The row's text from [start] (inclusive) to [end] (exclusive), with empty cells as spaces. */
    fun text(start: Int = 0, end: Int = width): String = buildString {
        for (x in start.coerceAtLeast(0) until end.coerceAtMost(width)) {
            if (flags[x] and SPACER_FLAG != 0) continue
            append(text[x] ?: " ")
        }
    }

    companion object {
        const val DEFAULT_COLOR = -1

        const val BOLD_FLAG = 1
        const val ITALIC_FLAG = 1 shl 1
        const val FAINT_FLAG = 1 shl 2
        const val UNDERLINE_FLAG = 1 shl 3
        const val STRIKETHROUGH_FLAG = 1 shl 4
        const val INVERSE_FLAG = 1 shl 5
        const val INVISIBLE_FLAG = 1 shl 6
        const val OVERLINE_FLAG = 1 shl 7

        /** The cell holds a character two columns wide. */
        const val WIDE_FLAG = 1 shl 8

        /** The cell is the second column of a wide character and draws nothing itself. */
        const val SPACER_FLAG = 1 shl 9

        fun blank(width: Int) = ScreenRow(
            arrayOfNulls(width),
            IntArray(width) { DEFAULT_COLOR },
            IntArray(width) { DEFAULT_COLOR },
            IntArray(width),
        )
    }
}
