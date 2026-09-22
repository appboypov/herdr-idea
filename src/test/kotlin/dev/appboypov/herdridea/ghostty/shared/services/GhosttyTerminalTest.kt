package dev.appboypov.herdridea.ghostty.shared.services

import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.enums.NativePlatform
import dev.appboypov.herdridea.terminal.shared.models.ScreenRow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files

class GhosttyTerminalTest {
    private val replies = mutableListOf<Byte>()
    private val clipboard = mutableListOf<String>()
    private val terminal = GhosttyTerminal(vt, 80, 24, { replies += it.toList() }, { clipboard += it })

    @AfterEach
    fun close() = terminal.close()

    @Test
    fun `given red text, when snapshotted, then the cells carry the text and the red palette colour`() {
        terminal.setColors(foreground = 0xDDDDDD, background = 0x111111, ansi = IntArray(16) { if (it == 1) 0xFF0000 else 0x808080 })

        terminal.feed("\u001b[31mhi".toByteArray())
        val frame = terminal.snapshot()

        assertEquals("hi", frame.rows[0].text().trimEnd())
        assertEquals(0xFF0000, frame.rows[0].fg[0])
        assertEquals(0xDDDDDD, frame.defaultForeground)
        assertEquals(0x111111, frame.defaultBackground)
    }

    @Test
    fun `given a drawn frame, when two lines are written, then exactly those rows are dirty and the cursor follows`() {
        terminal.snapshot()

        terminal.feed("one\r\ntwo".toByteArray())
        val frame = terminal.snapshot()

        assertEquals(setOf(0, 1), frame.dirtyRows.stream().toArray().toSet())
        assertEquals("one", frame.rows[0].text().trimEnd())
        assertEquals("two", frame.rows[1].text().trimEnd())
        assertEquals(3 to 1, frame.cursor.x to frame.cursor.y)
        assertTrue(frame.cursor.visible)
    }

    @Test
    fun `given a clean frame, when nothing is written, then no row is dirty and rows are shared`() {
        terminal.feed("same".toByteArray())
        val first = terminal.snapshot()

        val second = terminal.snapshot()

        assertTrue(second.dirtyRows.isEmpty)
        assertTrue(first.rows[0] === second.rows[0])
    }

    @Test
    fun `given a wide character, when snapshotted, then it takes two cells and reads back once`() {
        terminal.feed("界a".toByteArray())
        val row = terminal.snapshot().rows[0]

        assertTrue(row.flags[0] and ScreenRow.WIDE_FLAG != 0)
        assertTrue(row.flags[1] and ScreenRow.SPACER_FLAG != 0)
        assertEquals("界a", row.text().trimEnd())
    }

    @Test
    fun `given bold underlined text, when snapshotted, then the style flags are set`() {
        terminal.feed("\u001b[1;4mb".toByteArray())
        val flags = terminal.snapshot().rows[0].flags[0]

        assertTrue(flags and ScreenRow.BOLD_FLAG != 0)
        assertTrue(flags and ScreenRow.UNDERLINE_FLAG != 0)
    }

    @Test
    fun `given the Kitty keyboard protocol is enabled, when super+k is pressed, then it encodes as CSI u`() {
        terminal.feed("\u001b[>1u".toByteArray())

        val bytes = terminal.encodeKey("PRESS", "K", SUPER, 0, null, 'k'.code)

        assertEquals("\u001b[107;9u", String(bytes))
    }

    @Test
    fun `given no keyboard protocol, when Esc is pressed, then it encodes as a bare escape`() {
        assertEquals("\u001b", String(terminal.encodeKey("PRESS", "ESCAPE", 0, 0, null, 0)))
    }

    @Test
    fun `given bracketed paste is enabled, when text is pasted, then it is wrapped in paste markers`() {
        terminal.feed("\u001b[?2004h".toByteArray())

        val bytes = String(terminal.encodePaste("one\ntwo"))

        assertTrue(bytes.startsWith("\u001b[200~"))
        assertTrue(bytes.endsWith("\u001b[201~"))
        assertTrue(bytes.contains("one"))
    }

    @Test
    fun `given bracketed paste is off, when multi-line text is pasted, then newlines become carriage returns`() {
        assertEquals("one\rtwo", String(terminal.encodePaste("one\ntwo")))
    }

    @Test
    fun `given SGR mouse tracking, when the left button is pressed in a cell, then it encodes the cell position`() {
        terminal.resize(80, 24, 10, 20)
        terminal.feed("\u001b[?1000h\u001b[?1006h".toByteArray())

        val bytes = terminal.encodeMouse("PRESS", "LEFT", 0, 15f, 25f, anyButtonPressed = false)

        assertTrue(terminal.mouseTracking())
        assertEquals("\u001b[<0;2;2M", String(bytes))
    }

    @Test
    fun `given a primary device attributes query, when fed, then the terminal answers through the pty`() {
        terminal.feed("\u001b[c".toByteArray())

        assertEquals("\u001b[?62;22c", String(replies.toByteArray()))
    }

    @Test
    fun `given an OSC 52 copy, when fed, then the decoded text reaches the clipboard`() {
        terminal.feed("\u001b]52;c;aGk=\u0007".toByteArray())

        assertEquals(listOf("hi"), clipboard)
    }

    @Test
    fun `given a resize, when the program asks for the size, then the new columns and rows are reported`() {
        terminal.resize(100, 30, 8, 16)
        terminal.feed("\u001b[18t".toByteArray())

        assertArrayEquals("\u001b[8;30;100t".toByteArray(), replies.toByteArray())
    }

    companion object {
        private const val SUPER = 8
        private lateinit var vt: GhosttyVt

        @JvmStatic
        @BeforeAll
        fun load() {
            vt = NativeLibraryLoader(NativePlatform.current()!!, Files.createTempDirectory("ghostty-test")).load()
        }
    }
}
