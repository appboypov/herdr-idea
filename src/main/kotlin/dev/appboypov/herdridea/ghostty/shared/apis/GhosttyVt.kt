package dev.appboypov.herdridea.ghostty.shared.apis

import dev.appboypov.herdridea.ghostty.shared.exceptions.GhosttyException
import dev.appboypov.herdridea.ghostty.shared.models.GhosttyAbi
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.foreign.AddressLayout

/**
 * The libghostty-vt C API, bound through the Foreign Function and Memory API.
 *
 * Every native function the plugin calls is declared here and nowhere else (ADR-0001).
 * Struct offsets and enum values come from [abi], which the library reports about itself.
 * Handles are raw [MemorySegment] addresses; callers own their lifetime and threading.
 */
class GhosttyVt(private val lookup: SymbolLookup) {
    private val linker = Linker.nativeLinker()
    private val clipboardReplyFn = linker.downcallHandle(FunctionDescriptor.ofVoid(A, A))

    val abi: GhosttyAbi = GhosttyAbi(
        (fn("ghostty_type_json", A).invoke() as MemorySegment).reinterpret(Long.MAX_VALUE).getString(0),
    )

    // region Terminal

    private val terminalNew = fn("ghostty_terminal_new", I, A, A, S, S)
    private val terminalFree = voidFn("ghostty_terminal_free", A)
    private val terminalResize = fn("ghostty_terminal_resize", I, A, S, S, I, I)
    private val terminalSet = fn("ghostty_terminal_set", I, A, I, A)
    private val terminalGet = fn("ghostty_terminal_get", I, A, I, A)
    private val terminalVtWrite = voidFn("ghostty_terminal_vt_write", A, A, L)

    fun terminalNew(cols: Int, rows: Int): MemorySegment = Arena.ofConfined().use { arena ->
        val out = arena.allocate(A)
        check(terminalNew.invoke(MemorySegment.NULL, out, cols.toShort(), rows.toShort()) as Int, "ghostty_terminal_new")
        out.get(A, 0)
    }

    fun terminalFree(terminal: MemorySegment) {
        terminalFree.invoke(terminal)
    }

    fun terminalResize(terminal: MemorySegment, cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        check(terminalResize.invoke(terminal, cols.toShort(), rows.toShort(), cellWidthPx, cellHeightPx) as Int, "ghostty_terminal_resize")
    }

    /** Sets a terminal option. [value] is the pointer the option's input type documents. */
    fun terminalSet(terminal: MemorySegment, option: String, value: MemorySegment) {
        check(terminalSet.invoke(terminal, abi.enumValue("GhosttyTerminalOption", option), value) as Int, "ghostty_terminal_set $option")
    }

    /** Reads terminal data into [out]; returns the raw GhosttyResult. */
    fun terminalGet(terminal: MemorySegment, data: String, out: MemorySegment): Int =
        terminalGet.invoke(terminal, abi.enumValue("GhosttyTerminalData", data), out) as Int

    fun terminalVtWrite(terminal: MemorySegment, data: MemorySegment, length: Long) {
        terminalVtWrite.invoke(terminal, data, length)
    }

    // endregion

    // region Render state

    private val renderStateNew = fn("ghostty_render_state_new", I, A, A)
    private val renderStateFree = voidFn("ghostty_render_state_free", A)
    private val renderStateUpdate = fn("ghostty_render_state_update", I, A, A)
    private val renderStateGet = fn("ghostty_render_state_get", I, A, I, A)
    private val renderStateClean = fn("ghostty_render_state_clean", I, A)
    private val rowIteratorNew = fn("ghostty_render_state_row_iterator_new", I, A, A)
    private val rowIteratorFree = voidFn("ghostty_render_state_row_iterator_free", A)
    private val rowIteratorNext = fn("ghostty_render_state_row_iterator_next", Z, A)
    private val rowIteratorNextDirty = fn("ghostty_render_state_row_iterator_next_dirty", Z, A, A)
    private val rowGet = fn("ghostty_render_state_row_get", I, A, I, A)
    private val rowCellsNew = fn("ghostty_render_state_row_cells_new", I, A, A)
    private val rowCellsFree = voidFn("ghostty_render_state_row_cells_free", A)
    private val rowCellsNext = fn("ghostty_render_state_row_cells_next", Z, A)
    private val rowCellsGet = fn("ghostty_render_state_row_cells_get", I, A, I, A)
    private val cellGet = fn("ghostty_cell_get", I, L, I, A)

    fun renderStateNew(): MemorySegment = newHandle(renderStateNew, "ghostty_render_state_new")
    fun renderStateFree(state: MemorySegment) { renderStateFree.invoke(state) }
    fun renderStateUpdate(state: MemorySegment, terminal: MemorySegment) {
        check(renderStateUpdate.invoke(state, terminal) as Int, "ghostty_render_state_update")
    }
    fun renderStateGet(state: MemorySegment, data: Int, out: MemorySegment) {
        check(renderStateGet.invoke(state, data, out) as Int, "ghostty_render_state_get")
    }
    fun renderStateClean(state: MemorySegment) {
        check(renderStateClean.invoke(state) as Int, "ghostty_render_state_clean")
    }
    fun rowIteratorNew(): MemorySegment = newHandle(rowIteratorNew, "ghostty_render_state_row_iterator_new")
    fun rowIteratorFree(iterator: MemorySegment) { rowIteratorFree.invoke(iterator) }
    fun rowIteratorNext(iterator: MemorySegment): Boolean = rowIteratorNext.invoke(iterator) as Boolean
    fun rowIteratorNextDirty(iterator: MemorySegment, outY: MemorySegment): Boolean =
        rowIteratorNextDirty.invoke(iterator, outY) as Boolean
    fun rowGet(iterator: MemorySegment, data: Int, out: MemorySegment) {
        check(rowGet.invoke(iterator, data, out) as Int, "ghostty_render_state_row_get")
    }
    fun rowCellsNew(): MemorySegment = newHandle(rowCellsNew, "ghostty_render_state_row_cells_new")
    fun rowCellsFree(cells: MemorySegment) { rowCellsFree.invoke(cells) }
    fun rowCellsNext(cells: MemorySegment): Boolean = rowCellsNext.invoke(cells) as Boolean
    fun rowCellsGet(cells: MemorySegment, data: Int, out: MemorySegment): Int =
        rowCellsGet.invoke(cells, data, out) as Int
    fun cellGet(cell: Long, data: Int, out: MemorySegment): Int = cellGet.invoke(cell, data, out) as Int

    // endregion

    // region Key encoding

    private val keyEncoderNew = fn("ghostty_key_encoder_new", I, A, A)
    private val keyEncoderFree = voidFn("ghostty_key_encoder_free", A)
    private val keyEncoderSetopt = voidFn("ghostty_key_encoder_setopt", A, I, A)
    private val keyEncoderSetoptFromTerminal = voidFn("ghostty_key_encoder_setopt_from_terminal", A, A)
    private val keyEncoderEncode = fn("ghostty_key_encoder_encode", I, A, A, A, L, A)
    private val keyEventNew = fn("ghostty_key_event_new", I, A, A)
    private val keyEventFree = voidFn("ghostty_key_event_free", A)
    private val keyEventSetAction = voidFn("ghostty_key_event_set_action", A, I)
    private val keyEventSetKey = voidFn("ghostty_key_event_set_key", A, I)
    private val keyEventSetMods = voidFn("ghostty_key_event_set_mods", A, S)
    private val keyEventSetConsumedMods = voidFn("ghostty_key_event_set_consumed_mods", A, S)
    private val keyEventSetUtf8 = voidFn("ghostty_key_event_set_utf8", A, A, L)
    private val keyEventSetUnshiftedCodepoint = voidFn("ghostty_key_event_set_unshifted_codepoint", A, I)

    fun keyEncoderNew(): MemorySegment = newHandle(keyEncoderNew, "ghostty_key_encoder_new")
    fun keyEncoderFree(encoder: MemorySegment) { keyEncoderFree.invoke(encoder) }
    fun keyEncoderSetopt(encoder: MemorySegment, option: String, value: MemorySegment) {
        keyEncoderSetopt.invoke(encoder, abi.enumValue("GhosttyKeyEncoderOption", option), value)
    }
    fun keyEncoderSetoptFromTerminal(encoder: MemorySegment, terminal: MemorySegment) {
        keyEncoderSetoptFromTerminal.invoke(encoder, terminal)
    }
    /** Encodes [event]; returns the raw GhosttyResult and stores the byte count in [outLength]. */
    fun keyEncoderEncode(encoder: MemorySegment, event: MemorySegment, buffer: MemorySegment, outLength: MemorySegment): Int =
        keyEncoderEncode.invoke(encoder, event, buffer, buffer.byteSize(), outLength) as Int
    fun keyEventNew(): MemorySegment = newHandle(keyEventNew, "ghostty_key_event_new")
    fun keyEventFree(event: MemorySegment) { keyEventFree.invoke(event) }
    fun keyEventSetAction(event: MemorySegment, action: Int) { keyEventSetAction.invoke(event, action) }
    fun keyEventSetKey(event: MemorySegment, key: Int) { keyEventSetKey.invoke(event, key) }
    fun keyEventSetMods(event: MemorySegment, mods: Int) { keyEventSetMods.invoke(event, mods.toShort()) }
    fun keyEventSetConsumedMods(event: MemorySegment, mods: Int) { keyEventSetConsumedMods.invoke(event, mods.toShort()) }
    fun keyEventSetUtf8(event: MemorySegment, utf8: MemorySegment, length: Long) { keyEventSetUtf8.invoke(event, utf8, length) }
    fun keyEventSetUnshiftedCodepoint(event: MemorySegment, codepoint: Int) {
        keyEventSetUnshiftedCodepoint.invoke(event, codepoint)
    }

    // endregion

    // region Mouse encoding

    private val mousePosition = MemoryLayout.structLayout(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
    private val mouseEncoderNew = fn("ghostty_mouse_encoder_new", I, A, A)
    private val mouseEncoderFree = voidFn("ghostty_mouse_encoder_free", A)
    private val mouseEncoderSetopt = voidFn("ghostty_mouse_encoder_setopt", A, I, A)
    private val mouseEncoderSetoptFromTerminal = voidFn("ghostty_mouse_encoder_setopt_from_terminal", A, A)
    private val mouseEncoderEncode = fn("ghostty_mouse_encoder_encode", I, A, A, A, L, A)
    private val mouseEventNew = fn("ghostty_mouse_event_new", I, A, A)
    private val mouseEventFree = voidFn("ghostty_mouse_event_free", A)
    private val mouseEventSetAction = voidFn("ghostty_mouse_event_set_action", A, I)
    private val mouseEventSetButton = voidFn("ghostty_mouse_event_set_button", A, I)
    private val mouseEventClearButton = voidFn("ghostty_mouse_event_clear_button", A)
    private val mouseEventSetMods = voidFn("ghostty_mouse_event_set_mods", A, S)
    private val mouseEventSetPosition = voidFn("ghostty_mouse_event_set_position", A, mousePosition)

    fun mouseEncoderNew(): MemorySegment = newHandle(mouseEncoderNew, "ghostty_mouse_encoder_new")
    fun mouseEncoderFree(encoder: MemorySegment) { mouseEncoderFree.invoke(encoder) }
    fun mouseEncoderSetopt(encoder: MemorySegment, option: String, value: MemorySegment) {
        mouseEncoderSetopt.invoke(encoder, abi.enumValue("GhosttyMouseEncoderOption", option), value)
    }
    fun mouseEncoderSetoptFromTerminal(encoder: MemorySegment, terminal: MemorySegment) {
        mouseEncoderSetoptFromTerminal.invoke(encoder, terminal)
    }
    fun mouseEncoderEncode(encoder: MemorySegment, event: MemorySegment, buffer: MemorySegment, outLength: MemorySegment): Int =
        mouseEncoderEncode.invoke(encoder, event, buffer, buffer.byteSize(), outLength) as Int
    fun mouseEventNew(): MemorySegment = newHandle(mouseEventNew, "ghostty_mouse_event_new")
    fun mouseEventFree(event: MemorySegment) { mouseEventFree.invoke(event) }
    fun mouseEventSetAction(event: MemorySegment, action: Int) { mouseEventSetAction.invoke(event, action) }
    fun mouseEventSetButton(event: MemorySegment, button: Int) { mouseEventSetButton.invoke(event, button) }
    fun mouseEventClearButton(event: MemorySegment) { mouseEventClearButton.invoke(event) }
    fun mouseEventSetMods(event: MemorySegment, mods: Int) { mouseEventSetMods.invoke(event, mods.toShort()) }
    fun mouseEventSetPosition(event: MemorySegment, x: Float, y: Float) = Arena.ofConfined().use { arena ->
        val position = arena.allocate(mousePosition)
        position.set(ValueLayout.JAVA_FLOAT, 0, x)
        position.set(ValueLayout.JAVA_FLOAT, 4, y)
        mouseEventSetPosition.invoke(event, position)
    }

    // endregion

    // region Paste

    private val pasteEncode = fn("ghostty_paste_encode", I, A, L, Z, A, L, A)

    /** Encodes [data] (modified in place) into [buffer]; returns the raw GhosttyResult. */
    fun pasteEncode(data: MemorySegment, bracketed: Boolean, buffer: MemorySegment, outWritten: MemorySegment): Int =
        pasteEncode.invoke(data, data.byteSize(), bracketed, buffer, buffer.byteSize(), outWritten) as Int

    // endregion

    // region Callbacks

    /** Creates a C function pointer for [target] that lives as long as [arena]. */
    fun upcall(target: MethodHandle, descriptor: FunctionDescriptor, arena: Arena): MemorySegment =
        linker.upcallStub(target, descriptor, arena)

    /** Answers a clipboard write request through the reply function pointer the request carries. */
    fun clipboardReply(replyFn: MemorySegment, write: MemorySegment, reply: MemorySegment) {
        clipboardReplyFn.invoke(replyFn, write, reply)
    }

    // endregion

    fun result(name: String): Int = abi.enumValue("GhosttyResult", name)

    private fun newHandle(handle: MethodHandle, name: String): MemorySegment = Arena.ofConfined().use { arena ->
        val out = arena.allocate(A)
        check(handle.invoke(MemorySegment.NULL, out) as Int, name)
        out.get(A, 0)
    }

    private fun check(result: Int, call: String) {
        if (result != 0) throw GhosttyException("$call failed with GhosttyResult $result")
    }

    private fun fn(name: String, returns: MemoryLayout, vararg args: MemoryLayout): MethodHandle =
        linker.downcallHandle(symbol(name), FunctionDescriptor.of(returns, *args))

    private fun voidFn(name: String, vararg args: MemoryLayout): MethodHandle =
        linker.downcallHandle(symbol(name), FunctionDescriptor.ofVoid(*args))

    private fun symbol(name: String): MemorySegment =
        lookup.find(name).orElseThrow { GhosttyException("libghostty-vt has no symbol $name") }

    companion object {
        val A: AddressLayout = ValueLayout.ADDRESS
        val I: ValueLayout.OfInt = ValueLayout.JAVA_INT
        val S: ValueLayout.OfShort = ValueLayout.JAVA_SHORT
        val L: ValueLayout.OfLong = ValueLayout.JAVA_LONG
        val Z: ValueLayout.OfBoolean = ValueLayout.JAVA_BOOLEAN
    }
}
