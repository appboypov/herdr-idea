package dev.appboypov.herdridea.terminal.shared.models

/**
 * One key event in libghostty-vt terms, ready for its key encoder.
 *
 * @property action `GhosttyKeyAction` name: PRESS, REPEAT or RELEASE.
 * @property key `GhosttyKey` value name, such as `K`, `ENTER` or `ARROW_UP`.
 * @property mods `GhosttyMods` bits: shift 1, ctrl 2, alt 4, super 8.
 * @property consumedMods modifiers the platform already used to produce [text].
 * @property text the text the key produces, or null.
 * @property unshiftedCodepoint the key's character without modifiers, or 0.
 */
data class TerminalKeyInput(
    val action: String,
    val key: String,
    val mods: Int,
    val consumedMods: Int,
    val text: String?,
    val unshiftedCodepoint: Int,
) {
    /** The input as named-action arguments. */
    fun toArgs(): Map<String, String> = buildMap {
        put("action", action)
        put("key", key)
        put("mods", mods.toString())
        put("consumedMods", consumedMods.toString())
        text?.let { put("text", it) }
        put("unshifted", unshiftedCodepoint.toString())
    }

    companion object {
        /** The input named-action arguments describe; `key` is required, the rest default to a plain press. */
        fun fromArgs(args: Map<String, String>) = TerminalKeyInput(
            action = args["action"] ?: "PRESS",
            key = requireNotNull(args["key"]) { "Missing argument: key" },
            mods = args["mods"]?.toInt() ?: 0,
            consumedMods = args["consumedMods"]?.toInt() ?: 0,
            text = args["text"],
            unshiftedCodepoint = args["unshifted"]?.toInt() ?: 0,
        )
    }
}
