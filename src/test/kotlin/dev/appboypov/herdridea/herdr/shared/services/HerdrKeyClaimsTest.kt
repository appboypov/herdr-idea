package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrKeyClaim
import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.CTRL
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SHIFT
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SUPER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Against Brian's config fixture: prefix `ctrl+;`; `cmd+shift+f`, `cmd+1`, `cmd+c` and `cmd+v` unbound. */
class HerdrKeyClaimsTest {
    private val keymap = HerdrKeymapParser.parse(javaClass.getResource("/herdr/config.toml")!!.readText()).keymap

    @Test
    fun `Given the panel has focus, when a key Herdr binds and the IDE also binds is pressed, then Herdr gets it`() {
        val claims = HerdrKeyClaims(isMac = true)

        val spec = listOf(char('k', SUPER), char('p', SUPER), char('w', SUPER), char('t', SUPER), char('e', SUPER), char('k', SUPER or SHIFT))

        assertEquals(spec.map { HerdrKeyClaim.HERDR }, spec.map { claims.claim(it, keymap) })
    }

    @Test
    fun `Given the panel has focus, when a key Herdr does not bind is pressed, then the IDE gets it`() {
        val claims = HerdrKeyClaims(isMac = true)

        assertEquals(HerdrKeyClaim.IDE, claims.claim(char('f', SUPER or SHIFT), keymap))
        assertEquals(HerdrKeyClaim.IDE, claims.claim(char('1', SUPER), keymap))
        assertEquals(HerdrKeyClaim.IDE, claims.claim(char('c', CTRL), keymap))
        assertEquals(HerdrKeyClaim.IDE, claims.claim(null, keymap))
    }

    @Test
    fun `Given the prefix was pressed, when the next key is one the IDE binds, then Herdr still gets it once`() {
        val claims = HerdrKeyClaims(isMac = true)

        assertEquals(HerdrKeyClaim.HERDR, claims.claim(char(';', CTRL), keymap))
        assertEquals(HerdrKeyClaim.HERDR, claims.claim(char('1', SUPER), keymap))
        assertEquals(HerdrKeyClaim.IDE, claims.claim(char('1', SUPER), keymap))
    }

    @Test
    fun `Given the panel has focus, when Esc is pressed, then Herdr gets it`() {
        assertEquals(HerdrKeyClaim.HERDR, HerdrKeyClaims(isMac = true).claim(HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.ESC), 0), keymap))
    }

    @Test
    fun `Given each platform, when its copy and paste keys are pressed, then the panel copies and pastes`() {
        val mac = HerdrKeyClaims(isMac = true)
        val linux = HerdrKeyClaims(isMac = false)

        assertEquals(HerdrKeyClaim.COPY, mac.claim(char('c', SUPER), keymap))
        assertEquals(HerdrKeyClaim.PASTE, mac.claim(char('v', SUPER), keymap))
        assertEquals(HerdrKeyClaim.COPY, linux.claim(char('c', CTRL or SHIFT), keymap))
        assertEquals(HerdrKeyClaim.PASTE, linux.claim(char('v', CTRL or SHIFT), keymap))
    }

    private fun char(char: Char, mods: Int) = HerdrKeyStroke(HerdrKey.Char(char), mods)
}
