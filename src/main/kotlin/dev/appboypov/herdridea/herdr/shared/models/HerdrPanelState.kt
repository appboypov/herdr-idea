package dev.appboypov.herdridea.herdr.shared.models

import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind

/** What the Herdr panel shows (design D7). */
sealed interface HerdrPanelState {
    data object Connecting : HerdrPanelState

    /** A client is attached; [sessionName] is null for the shared session, which Herdr names itself. */
    data class Live(val kind: HerdrSessionKind, val sessionName: String?) : HerdrPanelState

    data class Exited(val kind: HerdrSessionKind, val exitCode: Int) : HerdrPanelState

    data class HerdrMissing(val searched: List<String>) : HerdrPanelState

    /** The remembered project session [sessionName] no longer exists. */
    data class SessionMissing(val sessionName: String) : HerdrPanelState

    data class UnsupportedPlatform(val platform: String) : HerdrPanelState
}
