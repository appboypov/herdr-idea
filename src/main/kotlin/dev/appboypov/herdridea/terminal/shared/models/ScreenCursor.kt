package dev.appboypov.herdridea.terminal.shared.models

import dev.appboypov.herdridea.terminal.shared.enums.CursorStyle

/** The cursor in viewport cell coordinates; [visible] is false when hidden or outside the viewport. */
data class ScreenCursor(
    val x: Int,
    val y: Int,
    val visible: Boolean,
    val blinking: Boolean,
    val style: CursorStyle,
) {
    companion object {
        val HIDDEN = ScreenCursor(0, 0, visible = false, blinking = false, style = CursorStyle.BLOCK)
    }
}
