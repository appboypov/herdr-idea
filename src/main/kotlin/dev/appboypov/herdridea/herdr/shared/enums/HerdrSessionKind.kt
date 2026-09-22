package dev.appboypov.herdridea.herdr.shared.enums

/** Which Herdr session a project's panel attaches to (design D6). */
enum class HerdrSessionKind {
    /** Herdr's default session, the same one a terminal gets from `herdr`. */
    SHARED,

    /** The project's own named session, started in the project root. */
    PROJECT,
}
