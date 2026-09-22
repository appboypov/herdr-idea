package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.ALT
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.CTRL
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SHIFT
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SUPER
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The fixture is a copy of Brian's live `~/.config/herdr/config.toml`. Expected bindings below are
 * read off that file and off Herdr v0.9.0's defaults, never off the parser.
 */
class HerdrKeymapParserTest {

    // region Brian's config

    @Test
    fun `Given Brian's config, when parsed, then the prefix is ctrl+semicolon`() {
        val keymap = parseFixture()

        assertEquals(press(char(';'), CTRL), keymap.prefix)
        assertTrue(keymap.bindsDirect(press(char(';'), CTRL)), "the prefix key itself is a direct binding")
    }

    @Test
    fun `Given Brian's config, when parsed, then every direct binding in the file is claimed`() {
        val keymap = parseFixture()

        val expected = listOf(
            "cmd+e split_vertical" to press(char('e'), SUPER),
            "cmd+d split_horizontal" to press(char('d'), SUPER),
            "cmd+t new_tab" to press(char('t'), SUPER),
            "cmd+r rename_tab" to press(char('r'), SUPER),
            "ctrl+r rename_pane" to press(char('r'), CTRL),
            "alt+left previous_tab" to press(key(HerdrNamedKey.LEFT), ALT),
            "alt+right next_tab" to press(key(HerdrNamedKey.RIGHT), ALT),
            "cmd+n new_workspace" to press(char('n'), SUPER),
            "cmd+shift+w close_workspace" to press(char('w'), SUPER, SHIFT),
            "cmd+shift+r rename_workspace" to press(char('r'), SUPER, SHIFT),
            "alt+up previous_workspace" to press(key(HerdrNamedKey.UP), ALT),
            "alt+down next_workspace" to press(key(HerdrNamedKey.DOWN), ALT),
            "cmd+up previous_agent" to press(key(HerdrNamedKey.UP), SUPER),
            "cmd+down next_agent" to press(key(HerdrNamedKey.DOWN), SUPER),
            "ctrl+down focus_pane_down" to press(key(HerdrNamedKey.DOWN), CTRL),
            "ctrl+up focus_pane_up" to press(key(HerdrNamedKey.UP), CTRL),
            "cmd+backtick zoom" to press(char('`'), SUPER),
            "cmd+shift+k goto" to press(char('k'), SUPER, SHIFT),
            "cmd+w close pane command" to press(char('w'), SUPER),
            "cmd+k search command" to press(char('k'), SUPER),
            "alt+k search command" to press(char('k'), ALT),
            "ctrl+alt+k search command" to press(char('k'), CTRL, ALT),
            "cmd+p quick actions command" to press(char('p'), SUPER),
            "alt+p quick actions command" to press(char('p'), ALT),
            "ctrl+alt+p quick actions command" to press(char('p'), CTRL, ALT),
            "cmd+shift+c annotate command" to press(char('c'), SUPER, SHIFT),
            "cmd+shift+m annotate command" to press(char('m'), SUPER, SHIFT),
            "cmd+shift+x annotate command" to press(char('x'), SUPER, SHIFT),
            "cmd+shift+a annotate command" to press(char('a'), SUPER, SHIFT),
            "cmd+shift+o annotate command" to press(char('o'), SUPER, SHIFT),
            "cmd+shift+l annotate command" to press(char('l'), SUPER, SHIFT),
        )

        assertEquals(emptyList<String>(), expected.filterNot { keymap.bindsDirect(it.second) }.map { it.first })
    }

    @Test
    fun `Given Brian's config, when parsed, then every prefixed binding in the file is claimed`() {
        val keymap = parseFixture()

        val expected = listOf(
            "prefix+v split_vertical" to press(char('v')),
            "prefix+minus split_horizontal" to press(char('-')),
            "prefix+x close_pane" to press(char('x')),
            "prefix+c new_tab" to press(char('c')),
            "prefix+shift+t rename_tab" to press(char('t'), SHIFT),
            "prefix+shift+p rename_pane" to press(char('p'), SHIFT),
            "prefix+p previous_tab" to press(char('p')),
            "prefix+n next_tab" to press(char('n')),
            "prefix+shift+n new_workspace" to press(char('n'), SHIFT),
            "prefix+shift+d close_workspace" to press(char('d'), SHIFT),
            "prefix+shift+w rename_workspace" to press(char('w'), SHIFT),
            "prefix+h focus_pane_left" to press(char('h')),
            "prefix+j focus_pane_down" to press(char('j')),
            "prefix+k focus_pane_up" to press(char('k')),
            "prefix+l focus_pane_right" to press(char('l')),
            "prefix+z zoom" to press(char('z')),
            "prefix+r reviewr command" to press(char('r')),
        )

        assertEquals(emptyList<String>(), expected.filterNot { keymap.bindsAfterPrefix(it.second) }.map { it.first })
    }

    @Test
    fun `Given Brian's config, when parsed, then Herdr's defaults fill the actions he left unset`() {
        val keymap = parseFixture()

        val expectedAfterPrefix = listOf(
            "prefix+? help, typed as shift and slash" to press(char('/'), SHIFT),
            "prefix+s settings" to press(char('s')),
            "prefix+shift+g new_worktree" to press(char('g'), SHIFT),
            "prefix+w workspace_picker" to press(char('w')),
            "prefix+q detach" to press(char('q')),
            "prefix+shift+r reload_config" to press(char('r'), SHIFT),
            "prefix+o open_notification_target" to press(char('o')),
            "prefix+1 switch_tab" to press(char('1')),
            "prefix+9 switch_tab" to press(char('9')),
            "prefix+shift+x close_tab" to press(char('x'), SHIFT),
            "prefix+e edit_scrollback" to press(char('e')),
            "prefix+[ copy_mode" to press(char('[')),
            "prefix+shift+h swap_pane_left" to press(char('h'), SHIFT),
            "prefix+shift+l swap_pane_right" to press(char('l'), SHIFT),
            "prefix+tab cycle_pane_next" to press(key(HerdrNamedKey.TAB)),
            "prefix+shift+tab cycle_pane_previous" to press(key(HerdrNamedKey.TAB), SHIFT),
            "prefix+b toggle_sidebar" to press(char('b')),
        )
        val expectedDirect = listOf(
            "h navigate_pane_left" to press(char('h')),
            "j navigate_pane_down" to press(char('j')),
            "k navigate_pane_up" to press(char('k')),
            "l navigate_pane_right" to press(char('l')),
            "up navigate_workspace_up" to press(key(HerdrNamedKey.UP)),
            "down navigate_workspace_down" to press(key(HerdrNamedKey.DOWN)),
        )

        assertEquals(
            emptyList<String>(),
            expectedAfterPrefix.filterNot { keymap.bindsAfterPrefix(it.second) }.map { it.first },
        )
        assertEquals(emptyList<String>(), expectedDirect.filterNot { keymap.bindsDirect(it.second) }.map { it.first })
    }

    @Test
    fun `Given Brian's config, when parsed, then Herdr's grammar accepts every key string in it`() {
        assertEquals(emptyList<String>(), HerdrKeymapParser.parse(fixture()).skipped)
    }

    @Test
    fun `Given a keyboard stroke with an unshifted base character, when matched, then shift is understood`() {
        val keymap = parseFixture()

        assertTrue(keymap.bindsDirect(press(char('k'), SUPER, SHIFT)), "cmd+shift+k is goto")
        // The fixture binds cmd+shift+a to the annotate.manage plugin action.
        assertTrue(keymap.bindsDirect(press(char('a'), SUPER, SHIFT)), "cmd+shift+a is a custom command")
        assertFalse(keymap.bindsDirect(press(char('j'), SUPER, SHIFT)), "cmd+shift+j is bound nowhere")
        assertTrue(keymap.bindsAfterPrefix(press(char('/'), SHIFT)), "shift and slash reach the prefix+? help")
    }

    @Test
    fun `Given a key Brian's config leaves alone, when matched, then Herdr does not claim it`() {
        val keymap = parseFixture()

        assertFalse(keymap.bindsDirect(press(char('1'), SUPER)), "cmd+1 stays an IDE shortcut")
        assertFalse(keymap.bindsDirect(press(char('j'), SUPER)), "cmd+j stays an IDE shortcut")
        assertFalse(keymap.bindsDirect(press(key(HerdrNamedKey.ESC))), "esc is navigate-mode runtime, not a binding")
        assertFalse(keymap.bindsAfterPrefix(press(char('y'))), "prefix+y is bound nowhere")
    }

    @Test
    fun `Given shift and tab, when matched, then it reaches the back-tab binding`() {
        val keymap = parseFixture()

        assertTrue(keymap.bindsAfterPrefix(press(key(HerdrNamedKey.TAB), SHIFT)))
        assertTrue(keymap.bindsAfterPrefix(press(key(HerdrNamedKey.BACK_TAB))))
        assertTrue(keymap.bindsAfterPrefix(press(key(HerdrNamedKey.TAB))), "prefix+tab stays its own binding")
    }

    // endregion

    // region Herdr's own rules

    @Test
    fun `Given no config, when parsed, then Herdr's default prefix and bindings are reported`() {
        for (keymap in listOf(HerdrKeymapParser.parse(null).keymap, HerdrKeymapParser.parse("").keymap)) {
            assertEquals(press(char('b'), CTRL), keymap.prefix)
            assertTrue(keymap.bindsDirect(press(char('b'), CTRL)))
            assertTrue(keymap.bindsAfterPrefix(press(char('c'))), "prefix+c new_tab")
            assertTrue(keymap.bindsAfterPrefix(press(char('-'))), "prefix+minus split_horizontal")
            assertTrue(keymap.bindsAfterPrefix(press(char('r'))), "prefix+r resize_mode")
            assertTrue(keymap.bindsAfterPrefix(press(char('n'), SHIFT)), "prefix+shift+n new_workspace")
            assertTrue(keymap.bindsDirect(press(char('h'))), "h navigate_pane_left")
            assertFalse(keymap.bindsDirect(press(char('k'), SUPER)), "Herdr binds no cmd chord by default")
        }
    }

    @Test
    fun `Given an unknown token, when parsed, then it is skipped and the rest keeps working`() {
        val result = HerdrKeymapParser.parse(
            """
            [keys]
            zoom = ["cmd+nosuchkey", "cmd+j"]
            settings = "prefix+s"
            """.trimIndent(),
        )

        assertEquals(listOf("cmd+nosuchkey"), result.skipped)
        assertTrue(result.keymap.bindsDirect(press(char('j'), SUPER)), "the sibling binding survives")
        assertTrue(result.keymap.bindsAfterPrefix(press(char('s'))), "other actions survive")
    }

    @Test
    fun `Given a bare printable key bound to an action, when parsed, then Herdr disables it`() {
        val result = HerdrKeymapParser.parse("[keys]\nzoom = \"z\"\n")

        assertEquals(listOf("z"), result.skipped)
        assertFalse(result.keymap.bindsDirect(press(char('z'))), "a bare key would swallow typing")
        assertFalse(result.keymap.bindsAfterPrefix(press(char('z'))), "the default prefix+z is replaced, not kept")
    }

    @Test
    fun `Given two actions on one keystroke, when parsed, then the first keeps it`() {
        val result = HerdrKeymapParser.parse("[keys]\nzoom = \"cmd+j\"\nsettings = \"cmd+j\"\n")

        assertEquals(listOf("cmd+j"), result.skipped)
        assertTrue(result.keymap.bindsDirect(press(char('j'), SUPER)))
        assertEquals(1, result.keymap.directBindings.count { it == press(char('j'), SUPER) })
    }

    @Test
    fun `Given a binding that repeats the prefix key, when parsed, then Herdr disables it`() {
        val result = HerdrKeymapParser.parse("[keys]\nprefix = \"ctrl+b\"\nzoom = \"prefix+ctrl+b\"\n")

        assertEquals(listOf("prefix+ctrl+b"), result.skipped)
        assertTrue(result.keymap.bindsDirect(press(char('b'), CTRL)), "the prefix stays a direct binding")
        assertFalse(result.keymap.bindsAfterPrefix(press(char('b'), CTRL)))
    }

    @Test
    fun `Given a legacy indexed modifier, when parsed, then it takes the number keys from the default range`() {
        val result = HerdrKeymapParser.parse("[keys.indexed]\ntabs = \"cmd\"\n")

        assertTrue(result.keymap.bindsDirect(press(char('1'), SUPER)))
        assertTrue(result.keymap.bindsDirect(press(char('9'), SUPER)))
        assertFalse(result.keymap.bindsAfterPrefix(press(char('1'))), "the prefix+1..9 default is displaced")
    }

    @Test
    fun `Given a custom command without a command string, when parsed, then its key is not claimed`() {
        val result = HerdrKeymapParser.parse("[[keys.command]]\nkey = \"cmd+j\"\ncommand = \"\"\n")

        assertEquals(listOf("cmd+j"), result.skipped)
        assertFalse(result.keymap.bindsDirect(press(char('j'), SUPER)))
    }

    @Test
    fun `Given a range binding on a plain action, when parsed, then only indexed actions accept it`() {
        val result = HerdrKeymapParser.parse("[keys]\nzoom = \"prefix+1..9\"\n")

        assertEquals(listOf("prefix+1..9"), result.skipped)
        assertTrue(result.keymap.bindsAfterPrefix(press(char('1'))), "switch_tab still holds the range")
    }

    @Test
    fun `Given a navigate binding behind the prefix, when parsed, then Herdr disables it`() {
        val result = HerdrKeymapParser.parse("[keys]\nnavigate_pane_left = \"prefix+h\"\n")

        assertEquals(listOf("prefix+h"), result.skipped)
        assertFalse(result.keymap.bindsDirect(press(char('h'))), "the h default does not come back")
    }

    @Test
    fun `Given an uppercase letter in a binding, when parsed, then it asks for shift`() {
        val keymap = HerdrKeymapParser.parse("[keys]\nzoom = \"cmd+K\"\n").keymap

        assertTrue(keymap.bindsDirect(press(char('k'), SUPER, SHIFT)))
        assertFalse(keymap.bindsDirect(press(char('k'), SUPER)))
    }

    @Test
    fun `Given a named key token, when parsed, then the grammar's names resolve`() {
        val keymap = HerdrKeymapParser.parse(
            """
            [keys]
            zoom = "cmd+backtick"
            settings = "cmd+f5"
            detach = "cmd+semicolon"
            last_pane = "cmd+enter"
            """.trimIndent(),
        ).keymap

        assertTrue(keymap.bindsDirect(press(char('`'), SUPER)))
        assertTrue(keymap.bindsDirect(press(key(HerdrNamedKey.F5), SUPER)))
        assertTrue(keymap.bindsDirect(press(char(';'), SUPER)))
        assertTrue(keymap.bindsDirect(press(key(HerdrNamedKey.ENTER), SUPER)))
    }

    // endregion

    private fun parseFixture(): HerdrKeymap = HerdrKeymapParser.parse(fixture()).keymap

    private fun fixture(): String =
        checkNotNull(javaClass.getResourceAsStream(FIXTURE)) { "missing test resource $FIXTURE" }
            .bufferedReader()
            .use { it.readText() }

    private fun press(key: HerdrKey, vararg modifiers: Int) =
        HerdrKeyStroke(key, modifiers.fold(0) { bits, bit -> bits or bit })

    private fun char(value: Char): HerdrKey = HerdrKey.Char(value)

    private fun key(named: HerdrNamedKey): HerdrKey = HerdrKey.Named(named)

    private companion object {
        const val FIXTURE = "/herdr/config.toml"
    }
}
