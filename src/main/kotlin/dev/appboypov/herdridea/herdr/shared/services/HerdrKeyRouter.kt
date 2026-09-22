package dev.appboypov.herdridea.herdr.shared.services

import com.intellij.ide.IdeEventQueue
import dev.appboypov.herdridea.herdr.shared.enums.HerdrKeyClaim
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap
import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import dev.appboypov.herdridea.terminal.shared.services.AwtKeyTranslator
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * Applies [HerdrKeyClaims] to AWT key events before the IDE's action system sees them, while [target]
 * owns keyboard focus. A claimed press goes to [onKey], [onCopy] or [onPaste], and its typed and
 * released events are held back from the IDE too.
 */
class HerdrKeyRouter(
    private val target: Component,
    private val keymap: () -> HerdrKeymap,
    private val claims: HerdrKeyClaims,
    private val onKey: (TerminalKeyInput) -> Unit,
    private val onCopy: () -> Unit,
    private val onPaste: () -> Unit,
) : IdeEventQueue.EventDispatcher {
    private var swallowTyped = false
    private val claimedKeys = HashSet<Int>()

    /** The event may still be addressed to the window, so focus is read from the focus manager. */
    override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner !== target) return false
        return when (e.id) {
            KeyEvent.KEY_PRESSED -> press(e)
            KeyEvent.KEY_TYPED -> swallowTyped.also { swallowTyped = false }
            KeyEvent.KEY_RELEASED -> release(e)
            else -> false
        }
    }

    private fun press(event: KeyEvent): Boolean {
        swallowTyped = false
        if (AwtKeyTranslator.isModifierOnly(event)) return false
        when (claims.claim(HerdrKeyStrokes.of(event), keymap())) {
            HerdrKeyClaim.IDE -> return false
            HerdrKeyClaim.HERDR -> onKey(AwtKeyTranslator.input(event, "PRESS", AwtKeyTranslator.text(event)))
            HerdrKeyClaim.COPY -> onCopy()
            HerdrKeyClaim.PASTE -> onPaste()
        }
        claimedKeys += event.keyCode
        swallowTyped = true
        return true
    }

    private fun release(event: KeyEvent): Boolean {
        if (!claimedKeys.remove(event.keyCode)) return false
        onKey(AwtKeyTranslator.input(event, "RELEASE", null))
        return true
    }
}
