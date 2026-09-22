package dev.appboypov.herdridea.terminal.shared.services

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import dev.appboypov.herdridea.terminal.shared.models.ConsoleLook
import dev.appboypov.herdridea.terminal.shared.models.TerminalColors
import java.awt.Color

/** Reads the console font and colours of the IDE's active colour scheme (spec: the panel follows the IDE's look). */
object ConsoleLookReader {
    private val ansiKeys = listOf(
        ConsoleHighlighter.BLACK, ConsoleHighlighter.RED, ConsoleHighlighter.GREEN, ConsoleHighlighter.YELLOW,
        ConsoleHighlighter.BLUE, ConsoleHighlighter.MAGENTA, ConsoleHighlighter.CYAN, ConsoleHighlighter.GRAY,
        ConsoleHighlighter.DARKGRAY, ConsoleHighlighter.RED_BRIGHT, ConsoleHighlighter.GREEN_BRIGHT,
        ConsoleHighlighter.YELLOW_BRIGHT, ConsoleHighlighter.BLUE_BRIGHT, ConsoleHighlighter.MAGENTA_BRIGHT,
        ConsoleHighlighter.CYAN_BRIGHT, ConsoleHighlighter.WHITE,
    )

    fun read(scheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme): ConsoleLook {
        val foreground = scheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY)?.foregroundColor ?: scheme.defaultForeground
        val background = scheme.getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY) ?: scheme.defaultBackground
        val ansi = ansiKeys.map { rgb(foregroundOf(scheme, it) ?: foreground) }
        return ConsoleLook(
            fontName = scheme.consoleFontName,
            fontSize = scheme.consoleFontSize2D,
            lineSpacing = scheme.consoleLineSpacing,
            colors = TerminalColors(rgb(foreground), rgb(background), ansi),
            selectionColor = rgb(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR) ?: foreground),
        )
    }

    private fun foregroundOf(scheme: EditorColorsScheme, key: TextAttributesKey): Color? = scheme.getAttributes(key)?.foregroundColor

    private fun rgb(color: Color): Int = color.rgb and 0xFFFFFF
}
