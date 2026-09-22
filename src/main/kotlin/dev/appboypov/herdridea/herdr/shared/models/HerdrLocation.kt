package dev.appboypov.herdridea.herdr.shared.models

/** Where the `herdr` executable is, or where the plugin looked for it. */
sealed interface HerdrLocation {
    /** [environment] is the login-shell environment clients are started with. */
    data class Found(val path: String, val environment: Map<String, String>) : HerdrLocation

    data class Missing(val searched: List<String>) : HerdrLocation
}
