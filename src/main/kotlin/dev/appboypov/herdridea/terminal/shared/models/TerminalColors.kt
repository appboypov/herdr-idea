package dev.appboypov.herdridea.terminal.shared.models

/** The terminal's default colours as `0xRRGGBB`; [ansi] holds the 16 ANSI colours. */
data class TerminalColors(val foreground: Int, val background: Int, val ansi: List<Int>)
