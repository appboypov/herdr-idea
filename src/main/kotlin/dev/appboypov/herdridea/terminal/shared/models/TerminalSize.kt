package dev.appboypov.herdridea.terminal.shared.models

/** A terminal's grid size and the pixel size of one cell. */
data class TerminalSize(val cols: Int, val rows: Int, val cellWidthPx: Int, val cellHeightPx: Int) {
    companion object {
        val DEFAULT = TerminalSize(80, 24, 8, 16)
    }
}
