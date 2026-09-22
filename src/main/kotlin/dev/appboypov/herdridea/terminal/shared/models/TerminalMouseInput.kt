package dev.appboypov.herdridea.terminal.shared.models

/**
 * One mouse event in libghostty-vt terms, at pixel position ([x], [y]) inside the terminal area.
 *
 * @property action `GhosttyMouseAction` name: PRESS, RELEASE or MOTION.
 * @property button `GhosttyMouseButton` name, or null for motion without a button.
 * @property mods `GhosttyMods` bits.
 */
data class TerminalMouseInput(
    val action: String,
    val button: String?,
    val mods: Int,
    val x: Float,
    val y: Float,
    val anyButtonPressed: Boolean,
) {
    /** The input as named-action arguments. */
    fun toArgs(): Map<String, String> = buildMap {
        put("event", action)
        button?.let { put("button", it) }
        put("mods", mods.toString())
        put("x", x.toString())
        put("y", y.toString())
        put("anyButtonPressed", anyButtonPressed.toString())
    }

    companion object {
        /** The input named-action arguments describe; `x` and `y` are pixels inside the terminal area. */
        fun fromArgs(args: Map<String, String>) = TerminalMouseInput(
            action = args["event"] ?: "PRESS",
            button = args["button"],
            mods = args["mods"]?.toInt() ?: 0,
            x = requireNotNull(args["x"]) { "Missing argument: x" }.toFloat(),
            y = requireNotNull(args["y"]) { "Missing argument: y" }.toFloat(),
            anyButtonPressed = args["anyButtonPressed"]?.toBoolean() ?: false,
        )
    }
}
