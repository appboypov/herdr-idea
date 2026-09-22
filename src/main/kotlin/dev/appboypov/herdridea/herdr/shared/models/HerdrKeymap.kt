package dev.appboypov.herdridea.herdr.shared.models

/**
 * The keystrokes Herdr acts on, as the plugin's key router needs them: the ones Herdr answers
 * straight away, and the ones it answers after its prefix key.
 *
 * Built by `HerdrKeymapParser`; the lists hold Herdr's surviving bindings after its own conflict,
 * reserved-key and unsafe-binding rules ran. Matching is Herdr's, so a keystroke that carries the
 * unshifted base character plus shift still triggers a binding written as `prefix+?` or `cmd+K`.
 */
class HerdrKeymap(
    val prefix: HerdrKeyStroke?,
    directBindings: List<HerdrKeyStroke>,
    prefixedBindings: List<HerdrKeyStroke>,
) {

    /** Every keystroke Herdr acts on without the prefix, the prefix key itself included. */
    val directBindings: List<HerdrKeyStroke> = directBindings.map { it.normalized() }

    /** Every keystroke Herdr acts on while prefix mode is open. */
    val prefixedBindings: List<HerdrKeyStroke> = prefixedBindings.map { it.normalized() }

    private val directLookup: Set<HerdrKeyStroke> = this.directBindings.toHashSet()
    private val prefixedLookup: Set<HerdrKeyStroke> = this.prefixedBindings.toHashSet()

    fun bindsDirect(stroke: HerdrKeyStroke): Boolean = binds(stroke, directBindings, directLookup)

    fun bindsAfterPrefix(stroke: HerdrKeyStroke): Boolean = binds(stroke, prefixedBindings, prefixedLookup)

    private fun binds(
        stroke: HerdrKeyStroke,
        bindings: List<HerdrKeyStroke>,
        lookup: Set<HerdrKeyStroke>,
    ): Boolean {
        val normalized = stroke.normalized()
        if (normalized in lookup) return true
        return bindings.any { normalized.matches(it) }
    }
}
