package dev.appboypov.herdridea.herdr.shared.views.herdrpanel

import dev.appboypov.herdridea.herdr.shared.models.HerdrPanelState

/** The panel state as plain readable values for the published view. */
object HerdrPanelReadout {
    fun state(state: HerdrPanelState): Map<String, Any?> = when (state) {
        HerdrPanelState.Connecting -> mapOf("type" to "Connecting")
        is HerdrPanelState.Live -> mapOf("type" to "Live", "kind" to state.kind.name, "session" to state.sessionName)
        is HerdrPanelState.Exited -> mapOf("type" to "Exited", "kind" to state.kind.name, "exitCode" to state.exitCode)
        is HerdrPanelState.HerdrMissing -> mapOf("type" to "HerdrMissing", "searched" to state.searched)
        is HerdrPanelState.SessionMissing -> mapOf("type" to "SessionMissing", "session" to state.sessionName)
        is HerdrPanelState.UnsupportedPlatform -> mapOf("type" to "UnsupportedPlatform", "platform" to state.platform)
    }
}
