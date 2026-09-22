package dev.appboypov.herdridea.terminal.shared.models

import java.util.BitSet

/**
 * An immutable snapshot of the visible terminal screen, published by the terminal thread
 * and read by the EDT (design D2).
 *
 * Rows that did not change share their [ScreenRow] with the previous frame; [dirtyRows]
 * marks the rows that changed since it, so the view repaints only those.
 */
class ScreenFrame(
    val cols: Int,
    val rows: List<ScreenRow>,
    val dirtyRows: BitSet,
    val cursor: ScreenCursor,
    val defaultForeground: Int,
    val defaultBackground: Int,
    /** Whether the program asked for mouse events; otherwise dragging selects text locally. */
    val mouseTracking: Boolean,
) {
    val rowCount: Int get() = rows.size

    /** The whole screen as text, one line per row with trailing spaces removed. */
    fun text(): String = rows.joinToString("\n") { it.text().trimEnd() }

    companion object {
        fun empty(cols: Int, rows: Int, foreground: Int, background: Int) = ScreenFrame(
            cols,
            List(rows) { ScreenRow.blank(cols) },
            BitSet().apply { set(0, rows) },
            ScreenCursor.HIDDEN,
            foreground,
            background,
            mouseTracking = false,
        )
    }
}
