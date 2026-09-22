package dev.appboypov.herdridea.terminal.shared.services

import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * Sends keys no IDE action consumed to the terminal (design D4). A press that types nothing, or one
 * with ctrl, alt or command, is sent on press. A typing press waits for its typed event and is sent
 * with that text. A typed event whose press an IDE action took is dropped.
 */
class TerminalKeyFallback(private val onKey: (TerminalKeyInput) -> Unit) : KeyListener {
    private var typingPress: KeyEvent? = null
    private val pressed = HashSet<Int>()

    override fun keyPressed(e: KeyEvent) {
        typingPress = null
        if (e.isConsumed || AwtKeyTranslator.isModifierOnly(e)) return
        if (AwtKeyTranslator.text(e) != null) {
            typingPress = e
            return
        }
        send(e, null)
        e.consume()
    }

    override fun keyTyped(e: KeyEvent) {
        val press = typingPress ?: return
        typingPress = null
        if (e.isConsumed || e.keyChar == KeyEvent.CHAR_UNDEFINED || e.keyChar.isISOControl()) return
        send(press, e.keyChar.toString())
        e.consume()
    }

    override fun keyReleased(e: KeyEvent) {
        if (!pressed.remove(e.keyCode)) return
        onKey(AwtKeyTranslator.input(e, "RELEASE", null))
        e.consume()
    }

    private fun send(event: KeyEvent, text: String?) {
        val action = if (!pressed.add(event.keyCode)) "REPEAT" else "PRESS"
        onKey(AwtKeyTranslator.input(event, action, text))
    }
}
