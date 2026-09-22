package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import dev.appboypov.herdridea.terminal.shared.services.AwtKeyTranslator
import java.awt.event.KeyEvent

/** Turns an AWT key press into the keystroke Herdr's keymap matches: the unshifted key plus modifiers (design D4). */
object HerdrKeyStrokes {
    private val named = mapOf(
        KeyEvent.VK_ENTER to HerdrNamedKey.ENTER, KeyEvent.VK_ESCAPE to HerdrNamedKey.ESC, KeyEvent.VK_TAB to HerdrNamedKey.TAB,
        KeyEvent.VK_BACK_SPACE to HerdrNamedKey.BACKSPACE, KeyEvent.VK_LEFT to HerdrNamedKey.LEFT, KeyEvent.VK_RIGHT to HerdrNamedKey.RIGHT,
        KeyEvent.VK_UP to HerdrNamedKey.UP, KeyEvent.VK_DOWN to HerdrNamedKey.DOWN,
    )

    /** The keystroke for a key [keyCode] with AWT-derived [mods] bits, or null when Herdr cannot bind that key. */
    fun of(keyCode: Int, mods: Int): HerdrKeyStroke? {
        val key = named[keyCode]?.let { HerdrKey.Named(it) }
            ?: functionKey(keyCode)?.let { HerdrKey.Named(it) }
            ?: AwtKeyTranslator.unshiftedChar(keyCode)?.let { HerdrKey.Char(it) }
            ?: return null
        return HerdrKeyStroke(key, mods)
    }

    fun of(event: KeyEvent): HerdrKeyStroke? = of(event.keyCode, AwtKeyTranslator.mods(event))

    private fun functionKey(keyCode: Int): HerdrNamedKey? = when (keyCode) {
        in KeyEvent.VK_F1..KeyEvent.VK_F12 -> HerdrNamedKey.function(keyCode - KeyEvent.VK_F1 + 1)
        in KeyEvent.VK_F13..KeyEvent.VK_F24 -> HerdrNamedKey.function(keyCode - KeyEvent.VK_F13 + 13)
        else -> null
    }
}
