package dev.appboypov.herdridea.herdr.shared.models

import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey

/**
 * One keystroke: a key plus modifier bits.
 *
 * The same type carries two things, exactly as Herdr's `KeyCombo` does:
 * - a binding parsed from `config.toml`, the keystroke Herdr expects;
 * - a keystroke that came from the keyboard, which carries the **unshifted base character**
 *   (lowercase letter, US-layout unshifted digit or punctuation) plus [SHIFT] when shift is held.
 *
 * [matches] answers whether a keystroke from the keyboard triggers a parsed binding. It ports
 * `key_parts_match_combo` from Herdr v0.9.0 `src/config/keybinds.rs`, including the shifted-character
 * and letter-case rules. Herdr gets the shifted character from the terminal (the Kitty keyboard
 * protocol's shifted codepoint); here it is derived from the US layout, the layout Herdr's own
 * shifted-punctuation table assumes.
 */
data class HerdrKeyStroke(val key: HerdrKey, val modifiers: Int) {

    /**
     * Herdr's `normalize_key_combo`: shift+tab is back-tab, and back-tab never carries shift.
     * Registries, lookups and matching all work on normalised keystrokes.
     */
    fun normalized(): HerdrKeyStroke = when {
        key == TAB && hasShift -> HerdrKeyStroke(BACK_TAB, modifiers and SHIFT.inv())
        key == BACK_TAB && hasShift -> HerdrKeyStroke(key, modifiers and SHIFT.inv())
        else -> this
    }

    /** True when this keystroke, pressed on the keyboard, triggers the [expected] binding. */
    fun matches(expected: HerdrKeyStroke): Boolean {
        val actual = normalized()
        val wanted = expected.normalized()
        if (actual.modifiers == wanted.modifiers && actual.keyMatches(wanted)) return true
        if (actual.hasShift &&
            actual.modifiers and SHIFT.inv() == wanted.modifiers &&
            shiftedMatches(actual.key, actual.shiftedChar(), wanted.key)
        ) {
            return true
        }
        return actual.legacyShiftedLetterMatches(wanted)
    }

    private val hasShift: Boolean get() = modifiers and SHIFT != 0

    /** The character shift produces on a US layout, or null when this keystroke has no shift. */
    private fun shiftedChar(): Char? {
        if (!hasShift) return null
        val char = (key as? HerdrKey.Char)?.char ?: return null
        if (char in 'a'..'z') return char.uppercaseChar()
        return US_SHIFTED[char]
    }

    /** Herdr's `key_codes_match`, with this keystroke as the one from the keyboard. */
    private fun keyMatches(expected: HerdrKeyStroke): Boolean {
        val actualKey = key
        val expectedKey = expected.key
        if (actualKey !is HerdrKey.Char || expectedKey !is HerdrKey.Char) return actualKey == expectedKey
        if (isAsciiLetter(actualKey.char) && isAsciiLetter(expectedKey.char)) {
            return actualKey.char == expectedKey.char ||
                (hasShift && expected.hasShift && actualKey.char.lowercaseChar() == expectedKey.char.lowercaseChar())
        }
        return actualKey.char == expectedKey.char || shiftedMatches(actualKey, shiftedChar(), expectedKey)
    }

    /**
     * Herdr's `legacy_shifted_ascii_letter_matches`: a terminal that reports `K` without a shift
     * modifier still triggers a binding written as `shift+k`.
     */
    private fun legacyShiftedLetterMatches(expected: HerdrKeyStroke): Boolean {
        if (hasShift) return false
        val actualChar = (key as? HerdrKey.Char)?.char ?: return false
        val expectedChar = (expected.key as? HerdrKey.Char)?.char ?: return false
        return actualChar in 'A'..'Z' &&
            expectedChar in 'a'..'z' &&
            actualChar.lowercaseChar() == expectedChar &&
            modifiers or SHIFT == expected.modifiers
    }

    companion object {
        const val SHIFT = 1
        const val CTRL = 2
        const val ALT = 4
        const val SUPER = 8

        /**
         * Herdr's `hyper` modifier token. No keystroke from AWT carries it, so a binding that uses
         * it is parsed and then never matches, which is what Herdr does on a keyboard without it.
         */
        const val HYPER = 16

        private val TAB = HerdrKey.Named(HerdrNamedKey.TAB)
        private val BACK_TAB = HerdrKey.Named(HerdrNamedKey.BACK_TAB)

        /** Herdr's `is_shifted_punctuation`. */
        private const val SHIFTED_PUNCTUATION = "!@#\$%^&*()_+{}|:\"<>?~"

        private val US_SHIFTED = mapOf(
            '`' to '~',
            '1' to '!',
            '2' to '@',
            '3' to '#',
            '4' to '$',
            '5' to '%',
            '6' to '^',
            '7' to '&',
            '8' to '*',
            '9' to '(',
            '0' to ')',
            '-' to '_',
            '=' to '+',
            '[' to '{',
            ']' to '}',
            '\\' to '|',
            ';' to ':',
            '\'' to '"',
            ',' to '<',
            '.' to '>',
            '/' to '?',
        )

        private fun isAsciiLetter(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z'

        /** Herdr's `shifted_char_matches_expected`. */
        private fun shiftedMatches(actualKey: HerdrKey, shifted: Char?, expectedKey: HerdrKey): Boolean {
            val expectedChar = (expectedKey as? HerdrKey.Char)?.char ?: return false
            if (shifted != null) return shifted == expectedChar
            return actualKey is HerdrKey.Char &&
                actualKey.char == expectedChar &&
                expectedChar in SHIFTED_PUNCTUATION
        }
    }
}
