package dev.appboypov.herdridea.terminal.shared.models

/** The IDE console's font and colours the panel draws with; [selectionColor] is `0xRRGGBB`. */
data class ConsoleLook(val fontName: String, val fontSize: Float, val lineSpacing: Float, val colors: TerminalColors, val selectionColor: Int)
