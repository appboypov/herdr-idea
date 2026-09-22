package dev.appboypov.herdridea.herdr.shared.enums

/**
 * A key Herdr's key grammar names instead of writing it as a character.
 *
 * Ported from `parse_key_combo` in Herdr v0.9.0 `src/config/keybinds.rs`, where these are the
 * `KeyCode` variants that are not `KeyCode::Char`. Herdr's grammar also names characters
 * (`minus`, `backtick`, `semicolon`, ...); those parse to a character key, not to a member here.
 *
 * `BACK_TAB` has no token of its own: Herdr parses `shift+tab` into it and drops the shift
 * modifier, both when parsing a binding and when normalising a keystroke.
 */
enum class HerdrNamedKey {
    ENTER,
    ESC,
    TAB,
    BACK_TAB,
    BACKSPACE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    F13,
    F14,
    F15,
    F16,
    F17,
    F18,
    F19,
    F20,
    F21,
    F22,
    F23,
    F24;

    companion object {
        private val FUNCTION_KEYS = listOf(
            F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
            F13, F14, F15, F16, F17, F18, F19, F20, F21, F22, F23, F24,
        )

        /** The key an `f<number>` token names, or null when no keyboard reports it. */
        fun function(number: Int): HerdrNamedKey? = FUNCTION_KEYS.getOrNull(number - 1)
    }
}
