package dev.appboypov.herdridea.herdr.shared.models

import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey

/**
 * The key half of a keystroke: Herdr's `crossterm::event::KeyCode`.
 *
 * A [Char] always holds the key's unshifted form, the way Herdr stores it: `cmd+K` in a config
 * parses to `Char('k')` plus the shift modifier, and a keystroke from the keyboard carries the
 * unshifted base character with the shift modifier beside it.
 */
sealed interface HerdrKey {

    /** A character key, for example `k`, `;`, `-` or `` ` ``. */
    data class Char(val char: kotlin.Char) : HerdrKey

    /** A key Herdr's grammar names, for example enter, esc or f1. */
    data class Named(val key: HerdrNamedKey) : HerdrKey
}
