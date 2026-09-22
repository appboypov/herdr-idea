package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrKeyClaim
import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap

/**
 * Key precedence while the Herdr panel has focus (ADR-0003, design D4). Herdr gets every keystroke
 * its config binds, Esc, and the key after its prefix. The platform copy and paste keys copy and
 * paste unless Herdr binds them. Every other key belongs to the IDE.
 *
 * Remembers whether the prefix is pending, so one instance serves one panel.
 */
class HerdrKeyClaims(isMac: Boolean) {
    private val clipboardMods = if (isMac) HerdrKeyStroke.SUPER else HerdrKeyStroke.CTRL or HerdrKeyStroke.SHIFT
    private val copy = HerdrKeyStroke(HerdrKey.Char('c'), clipboardMods)
    private val paste = HerdrKeyStroke(HerdrKey.Char('v'), clipboardMods)
    private var prefixPending = false

    /** Who handles [stroke] under [keymap]; a null stroke is a key Herdr cannot bind. */
    fun claim(stroke: HerdrKeyStroke?, keymap: HerdrKeymap): HerdrKeyClaim {
        if (prefixPending) {
            prefixPending = false
            return HerdrKeyClaim.HERDR
        }
        if (stroke == null) return HerdrKeyClaim.IDE
        if (keymap.bindsDirect(stroke)) {
            prefixPending = keymap.prefix?.let(stroke::matches) == true
            return HerdrKeyClaim.HERDR
        }
        return when (stroke) {
            ESC -> HerdrKeyClaim.HERDR
            copy -> HerdrKeyClaim.COPY
            paste -> HerdrKeyClaim.PASTE
            else -> HerdrKeyClaim.IDE
        }
    }

    private companion object {
        val ESC = HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.ESC), 0)
    }
}
