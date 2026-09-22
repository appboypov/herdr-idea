package dev.appboypov.herdridea.terminal.shared.services

import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Turns AWT key events into terminal key input: the physical key as a `GhosttyKey` name, the
 * modifier bits, the text the key produces and its unshifted US-layout codepoint. Option is
 * treated as alt, as in a terminal with option-as-alt.
 */
object AwtKeyTranslator {
    const val SHIFT = 1
    const val CTRL = 2
    const val ALT = 4
    const val SUPER = 8

    private val named = mapOf(
        KeyEvent.VK_ENTER to "ENTER", KeyEvent.VK_ESCAPE to "ESCAPE", KeyEvent.VK_TAB to "TAB",
        KeyEvent.VK_BACK_SPACE to "BACKSPACE", KeyEvent.VK_DELETE to "DELETE", KeyEvent.VK_INSERT to "INSERT",
        KeyEvent.VK_HOME to "HOME", KeyEvent.VK_END to "END", KeyEvent.VK_PAGE_UP to "PAGE_UP", KeyEvent.VK_PAGE_DOWN to "PAGE_DOWN",
        KeyEvent.VK_UP to "ARROW_UP", KeyEvent.VK_DOWN to "ARROW_DOWN", KeyEvent.VK_LEFT to "ARROW_LEFT", KeyEvent.VK_RIGHT to "ARROW_RIGHT",
        KeyEvent.VK_SPACE to "SPACE", KeyEvent.VK_BACK_QUOTE to "BACKQUOTE", KeyEvent.VK_MINUS to "MINUS", KeyEvent.VK_EQUALS to "EQUAL",
        KeyEvent.VK_OPEN_BRACKET to "BRACKET_LEFT", KeyEvent.VK_CLOSE_BRACKET to "BRACKET_RIGHT", KeyEvent.VK_BACK_SLASH to "BACKSLASH",
        KeyEvent.VK_SEMICOLON to "SEMICOLON", KeyEvent.VK_QUOTE to "QUOTE", KeyEvent.VK_COMMA to "COMMA",
        KeyEvent.VK_PERIOD to "PERIOD", KeyEvent.VK_SLASH to "SLASH",
    )

    private val unshiftedPunctuation = mapOf(
        KeyEvent.VK_SPACE to ' ', KeyEvent.VK_BACK_QUOTE to '`', KeyEvent.VK_MINUS to '-', KeyEvent.VK_EQUALS to '=',
        KeyEvent.VK_OPEN_BRACKET to '[', KeyEvent.VK_CLOSE_BRACKET to ']', KeyEvent.VK_BACK_SLASH to '\\',
        KeyEvent.VK_SEMICOLON to ';', KeyEvent.VK_QUOTE to '\'', KeyEvent.VK_COMMA to ',', KeyEvent.VK_PERIOD to '.', KeyEvent.VK_SLASH to '/',
    )

    /** The `GhosttyKey` name for [keyCode], or null for keys the terminal does not encode (modifiers alone). */
    fun key(keyCode: Int): String? = when (keyCode) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> ('A' + (keyCode - KeyEvent.VK_A)).toString()
        in KeyEvent.VK_0..KeyEvent.VK_9 -> "DIGIT_${keyCode - KeyEvent.VK_0}"
        in KeyEvent.VK_F1..KeyEvent.VK_F12 -> "F${keyCode - KeyEvent.VK_F1 + 1}"
        in KeyEvent.VK_F13..KeyEvent.VK_F24 -> "F${keyCode - KeyEvent.VK_F13 + 13}"
        else -> named[keyCode]
    }

    /** The character the key gives without modifiers on a US layout, or null for non-character keys. */
    fun unshiftedChar(keyCode: Int): Char? = when (keyCode) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> 'a' + (keyCode - KeyEvent.VK_A)
        in KeyEvent.VK_0..KeyEvent.VK_9 -> '0' + (keyCode - KeyEvent.VK_0)
        else -> unshiftedPunctuation[keyCode]
    }

    fun mods(event: InputEvent): Int {
        var mods = 0
        if (event.isShiftDown) mods = mods or SHIFT
        if (event.isControlDown) mods = mods or CTRL
        if (event.isAltDown) mods = mods or ALT
        if (event.isMetaDown) mods = mods or SUPER
        return mods
    }

    /** Whether [event] is only a modifier key going down or up. */
    fun isModifierOnly(event: KeyEvent) = event.keyCode in setOf(
        KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_CAPS_LOCK,
    )

    /** The text a press produces: its character when no ctrl, alt or command is held, else null. */
    fun text(event: KeyEvent): String? {
        if (mods(event) and (CTRL or ALT or SUPER) != 0) return null
        val char = event.keyChar
        if (char == KeyEvent.CHAR_UNDEFINED || char.isISOControl()) return null
        return char.toString()
    }

    /** The key input for [event] with [action] (`PRESS`, `REPEAT` or `RELEASE`) and [text]. */
    fun input(event: KeyEvent, action: String, text: String?): TerminalKeyInput {
        val mods = mods(event)
        return TerminalKeyInput(
            action = action,
            key = key(event.keyCode) ?: "UNIDENTIFIED",
            mods = mods,
            consumedMods = if (text != null) mods and SHIFT else 0,
            text = text,
            unshiftedCodepoint = unshiftedChar(event.keyCode)?.code ?: 0,
        )
    }
}
