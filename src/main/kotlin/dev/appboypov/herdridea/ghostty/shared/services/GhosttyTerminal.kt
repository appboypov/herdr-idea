package dev.appboypov.herdridea.ghostty.shared.services

import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt.Companion.A
import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt.Companion.L
import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt.Companion.Z
import dev.appboypov.herdridea.terminal.shared.enums.CursorStyle
import dev.appboypov.herdridea.terminal.shared.models.ScreenCursor
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.terminal.shared.models.ScreenRow
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.BitSet

/**
 * One libghostty-vt terminal with its render state and input encoders.
 *
 * Not thread-safe: every call, including [close], must come from the one thread that owns
 * the session (design D2). The native callbacks run synchronously on that thread during [feed].
 *
 * @param onPtyWrite receives bytes the terminal answers to the program, such as query replies.
 * @param onClipboardWrite receives text the program copies through OSC 52.
 */
class GhosttyTerminal(
    private val vt: GhosttyVt,
    cols: Int,
    rows: Int,
    private val onPtyWrite: (ByteArray) -> Unit,
    private val onClipboardWrite: (String) -> Unit = {},
) : AutoCloseable {
    private val abi = vt.abi
    private val arena = Arena.ofShared()
    private val terminal = vt.terminalNew(cols, rows)
    private val renderState = vt.renderStateNew()
    private val rowIterator = vt.rowIteratorNew()
    private val rowCells = vt.rowCellsNew()
    private val keyEncoder = vt.keyEncoderNew()
    private val keyEvent = vt.keyEventNew()
    private val mouseEncoder = vt.mouseEncoderNew()
    private val mouseEvent = vt.mouseEventNew()

    private val inputBuffer = arena.allocate(64 * 1024L)
    private val encodeBuffer = arena.allocate(4096L)
    private val scratch = arena.allocate(256L, 8)
    private val lengthOut = arena.allocate(L)
    private val codepoints = arena.allocate(ValueLayout.JAVA_INT, MAX_GRAPHEMES.toLong())
    private val style = sized("GhosttyStyle")
    private val cursorStruct = sized("GhosttyRenderStateCursor")
    private val colorsStruct = sized("GhosttyRenderStateColors")
    private val modeConfig = arena.allocate(abi.size("GhosttyTerminalModeConfig"), 4)
    private val mouseSize = sized("GhosttyMouseEncoderSize")
    private val palette = arena.allocate(256L * 3)
    private val color = arena.allocate(3L)
    private val callbackScratch = arena.allocate(8L, 8)
    private val clipboardReply = sized("GhosttyClipboardWriteReply")
    private val rowIteratorRef = pointerTo(rowIterator)
    private val rowCellsRef = pointerTo(rowCells)

    private val rsCols = abi.enumValue("GhosttyRenderStateData", "COLS")
    private val rsRows = abi.enumValue("GhosttyRenderStateData", "ROWS")
    private val rsDirty = abi.enumValue("GhosttyRenderStateData", "DIRTY")
    private val rsRowIterator = abi.enumValue("GhosttyRenderStateData", "ROW_ITERATOR")
    private val rsCursor = abi.enumValue("GhosttyRenderStateData", "CURSOR")
    private val rsColors = abi.enumValue("GhosttyRenderStateData", "COLORS")
    private val dirtyFull = abi.enumValue("GhosttyRenderStateDirty", "FULL")
    private val dirtyFalse = abi.enumValue("GhosttyRenderStateDirty", "FALSE")
    private val rowCellsData = abi.enumValue("GhosttyRenderStateRowData", "CELLS")
    private val cellRaw = abi.enumValue("GhosttyRenderStateRowCellsData", "RAW")
    private val cellStyle = abi.enumValue("GhosttyRenderStateRowCellsData", "STYLE")
    private val cellHasStyling = abi.enumValue("GhosttyRenderStateRowCellsData", "HAS_STYLING")
    private val cellGraphemesLen = abi.enumValue("GhosttyRenderStateRowCellsData", "GRAPHEMES_LEN")
    private val cellGraphemesBuf = abi.enumValue("GhosttyRenderStateRowCellsData", "GRAPHEMES_BUF")
    private val cellFg = abi.enumValue("GhosttyRenderStateRowCellsData", "FG_COLOR")
    private val cellBg = abi.enumValue("GhosttyRenderStateRowCellsData", "BG_COLOR")
    private val rawWide = abi.enumValue("GhosttyCellData", "WIDE")
    private val wideWide = abi.enumValue("GhosttyCellWide", "WIDE")
    private val wideSpacerTail = abi.enumValue("GhosttyCellWide", "SPACER_TAIL")
    private val success = vt.result("SUCCESS")

    private val styleOffsets = StyleOffsets()
    private var previous: ScreenFrame? = null

    init {
        installCallbacks()
    }

    // region Output

    /** Feeds program output into the terminal. */
    fun feed(bytes: ByteArray, length: Int = bytes.size) {
        var offset = 0
        while (offset < length) {
            val chunk = minOf(length - offset, inputBuffer.byteSize().toInt())
            MemorySegment.copy(bytes, offset, inputBuffer, ValueLayout.JAVA_BYTE, 0, chunk)
            vt.terminalVtWrite(terminal, inputBuffer, chunk.toLong())
            offset += chunk
        }
    }

    fun resize(cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        vt.terminalResize(terminal, cols, rows, cellWidthPx, cellHeightPx)
        val size = mouseSize
        size.set(ValueLayout.JAVA_INT, abi.offset("GhosttyMouseEncoderSize", "screen_width"), cols * cellWidthPx)
        size.set(ValueLayout.JAVA_INT, abi.offset("GhosttyMouseEncoderSize", "screen_height"), rows * cellHeightPx)
        size.set(ValueLayout.JAVA_INT, abi.offset("GhosttyMouseEncoderSize", "cell_width"), cellWidthPx)
        size.set(ValueLayout.JAVA_INT, abi.offset("GhosttyMouseEncoderSize", "cell_height"), cellHeightPx)
        vt.mouseEncoderSetopt(mouseEncoder, "SIZE", size)
    }

    /**
     * Sets the default colours programs see and draw with, as `0xRRGGBB`.
     * [ansi] overrides the first entries of the 256-colour palette (normally the 16 ANSI colours).
     */
    fun setColors(foreground: Int, background: Int, ansi: IntArray) {
        writeRgb(color, 0, foreground)
        vt.terminalSet(terminal, "COLOR_FOREGROUND", color)
        writeRgb(color, 0, background)
        vt.terminalSet(terminal, "COLOR_BACKGROUND", color)
        vt.terminalGet(terminal, "COLOR_PALETTE_DEFAULT", palette)
        ansi.forEachIndexed { i, color -> writeRgb(palette, i * 3L, color) }
        vt.terminalSet(terminal, "COLOR_PALETTE", palette)
    }

    // endregion

    // region Render state

    /**
     * Captures the visible screen. Rows unchanged since the previous snapshot are shared with it.
     */
    fun snapshot(): ScreenFrame {
        vt.renderStateUpdate(renderState, terminal)
        vt.renderStateGet(renderState, rsCols, scratch)
        val cols = scratch.get(ValueLayout.JAVA_SHORT, 0).toInt() and 0xFFFF
        vt.renderStateGet(renderState, rsRows, scratch)
        val rowCount = scratch.get(ValueLayout.JAVA_SHORT, 0).toInt() and 0xFFFF
        vt.renderStateGet(renderState, rsDirty, scratch)
        val dirty = scratch.get(ValueLayout.JAVA_INT, 0)

        vt.renderStateGet(renderState, rsColors, colorsStruct)
        val background = readRgb(colorsStruct, abi.offset("GhosttyRenderStateColors", "background"))
        val foreground = readRgb(colorsStruct, abi.offset("GhosttyRenderStateColors", "foreground"))

        val prior = previous
        val reuse = prior != null && prior.cols == cols && prior.rowCount == rowCount && dirty != dirtyFull
        val rows: MutableList<ScreenRow> =
            if (reuse) prior.rows.toMutableList() else MutableList(rowCount) { ScreenRow.blank(cols) }
        val dirtyRows = BitSet()
        if (!reuse) dirtyRows.set(0, rowCount)

        if (!reuse) {
            vt.renderStateGet(renderState, rsRowIterator, rowIteratorRef)
            var row = 0
            while (row < rowCount && vt.rowIteratorNext(rowIterator)) {
                vt.rowGet(rowIterator, rowCellsData, rowCellsRef)
                rows[row++] = readRow(cols)
            }
        } else if (dirty != dirtyFalse) {
            vt.renderStateGet(renderState, rsRowIterator, rowIteratorRef)
            val y = scratch.asSlice(8, 2)
            while (vt.rowIteratorNextDirty(rowIterator, y)) {
                val row = y.get(ValueLayout.JAVA_SHORT, 0).toInt() and 0xFFFF
                if (row >= rowCount) continue
                vt.rowGet(rowIterator, rowCellsData, rowCellsRef)
                rows[row] = readRow(cols)
                dirtyRows.set(row)
            }
        }

        val frame = ScreenFrame(cols, rows, dirtyRows, readCursor(), foreground, background, mouseTracking())
        vt.renderStateClean(renderState)
        previous = frame
        return frame
    }

    private fun readRow(cols: Int): ScreenRow {
        val row = ScreenRow.blank(cols)
        var x = 0
        while (x < cols && vt.rowCellsNext(rowCells)) {
            vt.rowCellsGet(rowCells, cellRaw, scratch)
            vt.cellGet(scratch.get(ValueLayout.JAVA_LONG, 0), rawWide, scratch.asSlice(16))
            when (scratch.get(ValueLayout.JAVA_INT, 16)) {
                wideWide -> row.flags[x] = ScreenRow.WIDE_FLAG
                wideSpacerTail -> row.flags[x] = ScreenRow.SPACER_FLAG
            }

            vt.rowCellsGet(rowCells, cellGraphemesLen, scratch)
            val length = scratch.get(ValueLayout.JAVA_INT, 0)
            if (length > 0) row.text[x] = readGraphemes(length)

            if (vt.rowCellsGet(rowCells, cellFg, scratch) == success) row.fg[x] = readRgb(scratch, 0)
            if (vt.rowCellsGet(rowCells, cellBg, scratch) == success) row.bg[x] = readRgb(scratch, 0)

            vt.rowCellsGet(rowCells, cellHasStyling, scratch)
            if (scratch.get(ValueLayout.JAVA_BOOLEAN, 0)) {
                vt.rowCellsGet(rowCells, cellStyle, style)
                row.flags[x] = row.flags[x] or styleOffsets.flags(style)
            }
            x++
        }
        return row
    }

    private fun readGraphemes(length: Int): String {
        if (length <= MAX_GRAPHEMES) {
            vt.rowCellsGet(rowCells, cellGraphemesBuf, codepoints)
            return String(codepoints.toArray(ValueLayout.JAVA_INT), 0, length)
        }
        return Arena.ofConfined().use { temp ->
            val buffer = temp.allocate(ValueLayout.JAVA_INT, length.toLong())
            vt.rowCellsGet(rowCells, cellGraphemesBuf, buffer)
            String(buffer.toArray(ValueLayout.JAVA_INT), 0, length)
        }
    }

    private fun readCursor(): ScreenCursor {
        vt.renderStateGet(renderState, rsCursor, cursorStruct)
        val t = "GhosttyRenderStateCursor"
        val inViewport = cursorStruct.get(ValueLayout.JAVA_BOOLEAN, abi.offset(t, "viewport_has_value"))
        val visible = cursorStruct.get(ValueLayout.JAVA_BOOLEAN, abi.offset(t, "visible"))
        val styleValue = cursorStruct.get(ValueLayout.JAVA_INT, abi.offset(t, "visual_style"))
        val cursorStyle = CursorStyle.entries.firstOrNull {
            abi.enumValueOrNull("GhosttyRenderStateCursorVisualStyle", it.name) == styleValue
        } ?: CursorStyle.BLOCK
        return ScreenCursor(
            x = cursorStruct.get(ValueLayout.JAVA_SHORT, abi.offset(t, "viewport_x")).toInt() and 0xFFFF,
            y = cursorStruct.get(ValueLayout.JAVA_SHORT, abi.offset(t, "viewport_y")).toInt() and 0xFFFF,
            visible = inViewport && visible,
            blinking = cursorStruct.get(ValueLayout.JAVA_BOOLEAN, abi.offset(t, "blinking")),
            style = cursorStyle,
        )
    }

    // endregion

    // region Input encoding

    /** Whether the program enabled terminal mode [mode] (DEC private unless [ansi]). */
    fun mode(mode: Int, ansi: Boolean = false): Boolean {
        val value = (mode and 0x7FFF) or (if (ansi) 0x8000 else 0)
        modeConfig.set(ValueLayout.JAVA_SHORT, abi.offset("GhosttyTerminalModeConfig", "mode"), value.toShort())
        if (vt.terminalGet(terminal, "MODE", modeConfig) != success) return false
        return modeConfig.get(ValueLayout.JAVA_BOOLEAN, abi.offset("GhosttyTerminalModeConfig", "value"))
    }

    /**
     * Encodes one key event with the modes the program enabled, such as the Kitty keyboard protocol.
     *
     * @param action a `GhosttyKeyAction` name: PRESS, REPEAT or RELEASE.
     * @param key a `GhosttyKey` value name, such as `K` or `ESCAPE`.
     * @param mods `GhosttyMods` bits (shift 1, ctrl 2, alt 4, super 8).
     * @param text the text the key produces, or null for none.
     * @param unshiftedCodepoint the key's character without shift, or 0 when it has none.
     */
    fun encodeKey(action: String, key: String, mods: Int, consumedMods: Int, text: String?, unshiftedCodepoint: Int): ByteArray {
        vt.keyEncoderSetoptFromTerminal(keyEncoder, terminal)
        scratch.set(ValueLayout.JAVA_BOOLEAN, 0, true)
        vt.keyEncoderSetopt(keyEncoder, "MACOS_OPTION_AS_ALT", scratch)
        vt.keyEventSetAction(keyEvent, abi.enumValue("GhosttyKeyAction", action))
        vt.keyEventSetKey(keyEvent, abi.enumValueOrNull("GhosttyKey", key) ?: abi.enumValue("GhosttyKey", "UNIDENTIFIED"))
        vt.keyEventSetMods(keyEvent, mods)
        vt.keyEventSetConsumedMods(keyEvent, consumedMods)
        vt.keyEventSetUnshiftedCodepoint(keyEvent, unshiftedCodepoint)
        if (text.isNullOrEmpty()) {
            vt.keyEventSetUtf8(keyEvent, MemorySegment.NULL, 0)
            return encodeWith { vt.keyEncoderEncode(keyEncoder, keyEvent, encodeBuffer, lengthOut) }
        }
        return Arena.ofConfined().use { temp ->
            val utf8 = text.toByteArray(Charsets.UTF_8)
            val segment = temp.allocate(utf8.size.toLong())
            MemorySegment.copy(utf8, 0, segment, ValueLayout.JAVA_BYTE, 0, utf8.size)
            vt.keyEventSetUtf8(keyEvent, segment, utf8.size.toLong())
            encodeWith { vt.keyEncoderEncode(keyEncoder, keyEvent, encodeBuffer, lengthOut) }
        }
    }

    /**
     * Encodes a mouse event at pixel position ([x], [y]) inside the terminal area, following
     * the tracking mode and format the program enabled. Returns empty when the program does not track it.
     *
     * @param action a `GhosttyMouseAction` name: PRESS, RELEASE or MOTION.
     * @param button a `GhosttyMouseButton` name, or null for motion without a button.
     */
    fun encodeMouse(action: String, button: String?, mods: Int, x: Float, y: Float, anyButtonPressed: Boolean): ByteArray {
        vt.mouseEncoderSetoptFromTerminal(mouseEncoder, terminal)
        scratch.set(ValueLayout.JAVA_BOOLEAN, 0, anyButtonPressed)
        vt.mouseEncoderSetopt(mouseEncoder, "ANY_BUTTON_PRESSED", scratch)
        vt.mouseEventSetAction(mouseEvent, abi.enumValue("GhosttyMouseAction", action))
        if (button == null) vt.mouseEventClearButton(mouseEvent)
        else vt.mouseEventSetButton(mouseEvent, abi.enumValue("GhosttyMouseButton", button))
        vt.mouseEventSetMods(mouseEvent, mods)
        vt.mouseEventSetPosition(mouseEvent, x, y)
        return encodeWith { vt.mouseEncoderEncode(mouseEncoder, mouseEvent, encodeBuffer, lengthOut) }
    }

    /** Whether the program asked for mouse events (any tracking mode other than none). */
    fun mouseTracking(): Boolean {
        if (vt.terminalGet(terminal, "MOUSE_TRACKING", scratch) != success) return false
        return scratch.get(ValueLayout.JAVA_BOOLEAN, 0)
    }

    /** Encodes pasted [text] safely, wrapped in bracketed-paste markers when the program enabled mode 2004. */
    fun encodePaste(text: String): ByteArray = Arena.ofConfined().use { temp ->
        val bytes = text.toByteArray(Charsets.UTF_8)
        val data = temp.allocate(maxOf(bytes.size, 1).toLong()).asSlice(0, bytes.size.toLong())
        MemorySegment.copy(bytes, 0, data, ValueLayout.JAVA_BYTE, 0, bytes.size)
        val buffer = temp.allocate(bytes.size * 2L + 64)
        val written = temp.allocate(L)
        vt.pasteEncode(data, mode(BRACKETED_PASTE_MODE), buffer, written)
        buffer.asSlice(0, written.get(L, 0)).toArray(ValueLayout.JAVA_BYTE)
    }

    private inline fun encodeWith(encode: () -> Int): ByteArray {
        if (encode() != success) return ByteArray(0)
        return encodeBuffer.asSlice(0, lengthOut.get(L, 0)).toArray(ValueLayout.JAVA_BYTE)
    }

    // endregion

    // region Callbacks

    private fun installCallbacks() {
        val lookup = MethodHandles.lookup()
        fun stub(name: String, type: MethodType, descriptor: FunctionDescriptor) =
            vt.upcall(lookup.findVirtual(GhosttyTerminal::class.java, name, type).bindTo(this), descriptor, arena)

        val ptr = MemorySegment::class.java
        vt.terminalSet(
            terminal, "WRITE_PTY",
            stub("writePty", MethodType.methodType(Void.TYPE, ptr, ptr, ptr, Long::class.javaPrimitiveType), FunctionDescriptor.ofVoid(A, A, A, L)),
        )
        vt.terminalSet(
            terminal, "DEVICE_ATTRIBUTES",
            stub("deviceAttributes", MethodType.methodType(Boolean::class.javaPrimitiveType, ptr, ptr, ptr), FunctionDescriptor.of(Z, A, A, A)),
        )
        vt.terminalSet(
            terminal, "SIZE",
            stub("sizeReport", MethodType.methodType(Boolean::class.javaPrimitiveType, ptr, ptr, ptr), FunctionDescriptor.of(Z, A, A, A)),
        )
        vt.terminalSet(
            terminal, "CLIPBOARD_WRITE",
            stub("clipboardWrite", MethodType.methodType(Void.TYPE, ptr, ptr, ptr), FunctionDescriptor.ofVoid(A, A, A)),
        )
    }

    @Suppress("unused") // Native callback.
    private fun writePty(terminal: MemorySegment, userdata: MemorySegment, data: MemorySegment, length: Long) {
        onPtyWrite(data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE))
    }

    /** Answers DA1 as a VT220 with ANSI colour, DA2 as Ghostty does. */
    @Suppress("unused") // Native callback.
    private fun deviceAttributes(terminal: MemorySegment, userdata: MemorySegment, out: MemorySegment): Boolean {
        val attrs = out.reinterpret(abi.size("GhosttyDeviceAttributes"))
        val primary = abi.offset("GhosttyDeviceAttributes", "primary")
        val p = "GhosttyDeviceAttributesPrimary"
        attrs.set(ValueLayout.JAVA_SHORT, primary + abi.offset(p, "conformance_level"), DA_CONFORMANCE_VT220)
        attrs.set(ValueLayout.JAVA_SHORT, primary + abi.offset(p, "features"), DA_FEATURE_ANSI_COLOR)
        attrs.set(ValueLayout.JAVA_LONG, primary + abi.offset(p, "num_features"), 1)
        val secondary = abi.offset("GhosttyDeviceAttributes", "secondary")
        val s = "GhosttyDeviceAttributesSecondary"
        attrs.set(ValueLayout.JAVA_SHORT, secondary + abi.offset(s, "device_type"), 1)
        attrs.set(ValueLayout.JAVA_SHORT, secondary + abi.offset(s, "firmware_version"), 10)
        attrs.set(ValueLayout.JAVA_SHORT, secondary + abi.offset(s, "rom_cartridge"), 0)
        return true
    }

    @Suppress("unused") // Native callback.
    private fun sizeReport(terminal: MemorySegment, userdata: MemorySegment, out: MemorySegment): Boolean {
        val t = "GhosttySizeReportSize"
        val size = out.reinterpret(abi.size(t))
        val local = callbackScratch
        vt.terminalGet(this.terminal, "COLS", local)
        size.set(ValueLayout.JAVA_SHORT, abi.offset(t, "columns"), local.get(ValueLayout.JAVA_SHORT, 0))
        vt.terminalGet(this.terminal, "ROWS", local)
        size.set(ValueLayout.JAVA_SHORT, abi.offset(t, "rows"), local.get(ValueLayout.JAVA_SHORT, 0))
        vt.terminalGet(this.terminal, "WIDTH_PX", local)
        val width = local.get(ValueLayout.JAVA_INT, 0)
        vt.terminalGet(this.terminal, "HEIGHT_PX", local)
        val height = local.get(ValueLayout.JAVA_INT, 0)
        val cols = size.get(ValueLayout.JAVA_SHORT, abi.offset(t, "columns")).toInt().coerceAtLeast(1)
        val rows = size.get(ValueLayout.JAVA_SHORT, abi.offset(t, "rows")).toInt().coerceAtLeast(1)
        size.set(ValueLayout.JAVA_INT, abi.offset(t, "cell_width"), width / cols)
        size.set(ValueLayout.JAVA_INT, abi.offset(t, "cell_height"), height / rows)
        return true
    }

    @Suppress("unused") // Native callback.
    private fun clipboardWrite(terminal: MemorySegment, userdata: MemorySegment, write: MemorySegment) {
        val w = "GhosttyClipboardWrite"
        val request = write.reinterpret(abi.size(w))
        val count = request.get(ValueLayout.JAVA_LONG, abi.offset(w, "contents_len"))
        val contents = request.get(A, abi.offset(w, "contents")).reinterpret(count * abi.size("GhosttyClipboardContent"))
        var text: String? = null
        for (i in 0 until count) {
            val entry = contents.asSlice(i * abi.size("GhosttyClipboardContent"))
            val mime = string(entry, abi.offset("GhosttyClipboardContent", "mime"))
            if (text == null || mime.startsWith("text/plain")) text = string(entry, abi.offset("GhosttyClipboardContent", "data"))
        }
        val reply = clipboardReply
        val result = if (text != null) "SUCCESS" else "UNSUPPORTED"
        reply.set(ValueLayout.JAVA_INT, abi.offset("GhosttyClipboardWriteReply", "result"), abi.enumValue("GhosttyClipboardWriteResult", result))
        vt.clipboardReply(request.get(A, abi.offset(w, "reply")), request, reply)
        text?.let(onClipboardWrite)
    }

    // endregion

    override fun close() {
        vt.mouseEventFree(mouseEvent)
        vt.mouseEncoderFree(mouseEncoder)
        vt.keyEventFree(keyEvent)
        vt.keyEncoderFree(keyEncoder)
        vt.rowCellsFree(rowCells)
        vt.rowIteratorFree(rowIterator)
        vt.renderStateFree(renderState)
        vt.terminalFree(terminal)
        arena.close()
    }

    // region Memory helpers

    private fun sized(type: String): MemorySegment {
        val segment = arena.allocate(abi.size(type), 8)
        segment.set(ValueLayout.JAVA_LONG, 0, abi.size(type))
        return segment
    }

    /** A pointer-sized cell holding [handle], for APIs that fill an existing handle through `Handle*`. */
    private fun pointerTo(handle: MemorySegment): MemorySegment {
        val cell = arena.allocate(A)
        cell.set(A, 0, handle)
        return cell
    }

    private fun string(struct: MemorySegment, offset: Long): String {
        val ptr = struct.get(A, offset)
        val len = struct.get(ValueLayout.JAVA_LONG, offset + 8)
        return String(ptr.reinterpret(len).toArray(ValueLayout.JAVA_BYTE), Charsets.UTF_8)
    }

    private inner class StyleOffsets {
        private val t = "GhosttyStyle"
        private val bits = listOf(
            abi.offset(t, "bold") to ScreenRow.BOLD_FLAG,
            abi.offset(t, "italic") to ScreenRow.ITALIC_FLAG,
            abi.offset(t, "faint") to ScreenRow.FAINT_FLAG,
            abi.offset(t, "inverse") to ScreenRow.INVERSE_FLAG,
            abi.offset(t, "invisible") to ScreenRow.INVISIBLE_FLAG,
            abi.offset(t, "strikethrough") to ScreenRow.STRIKETHROUGH_FLAG,
            abi.offset(t, "overline") to ScreenRow.OVERLINE_FLAG,
        )
        private val underline = abi.offset(t, "underline")

        fun flags(style: MemorySegment): Int {
            var flags = 0
            for ((offset, flag) in bits) if (style.get(ValueLayout.JAVA_BOOLEAN, offset)) flags = flags or flag
            if (style.get(ValueLayout.JAVA_INT, underline) != 0) flags = flags or ScreenRow.UNDERLINE_FLAG
            return flags
        }
    }

    // endregion

    private companion object {
        const val MAX_GRAPHEMES = 32
        const val BRACKETED_PASTE_MODE = 2004
        const val DA_CONFORMANCE_VT220: Short = 62
        const val DA_FEATURE_ANSI_COLOR: Short = 22

        fun readRgb(segment: MemorySegment, offset: Long): Int =
            ((segment.get(ValueLayout.JAVA_BYTE, offset).toInt() and 0xFF) shl 16) or
                ((segment.get(ValueLayout.JAVA_BYTE, offset + 1).toInt() and 0xFF) shl 8) or
                (segment.get(ValueLayout.JAVA_BYTE, offset + 2).toInt() and 0xFF)

        fun writeRgb(segment: MemorySegment, offset: Long, color: Int) {
            segment.set(ValueLayout.JAVA_BYTE, offset, (color shr 16).toByte())
            segment.set(ValueLayout.JAVA_BYTE, offset + 1, (color shr 8).toByte())
            segment.set(ValueLayout.JAVA_BYTE, offset + 2, color.toByte())
        }
    }
}
