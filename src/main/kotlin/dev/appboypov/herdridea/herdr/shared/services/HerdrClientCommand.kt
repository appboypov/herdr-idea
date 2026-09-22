package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind

/** The command line and environment for a Herdr client in the panel. */
object HerdrClientCommand {
    /** Variables Herdr sets for its own panes. An IDE started from a Herdr pane inherits them. */
    private val paneContext = setOf(
        "HERDR_ENV", "HERDR_SOCKET_PATH", "HERDR_PANE_ID", "HERDR_TAB_ID", "HERDR_WORKSPACE_ID", "HERDR_BIN_PATH",
    )

    fun command(herdrPath: String, kind: HerdrSessionKind, projectSessionName: String): List<String> = when (kind) {
        HerdrSessionKind.SHARED -> listOf(herdrPath)
        HerdrSessionKind.PROJECT -> listOf(herdrPath, "--session", projectSessionName)
    }

    /**
     * The login-shell environment without Herdr's pane context: the panel is its own terminal,
     * not a pane, so Herdr must neither refuse to start as nested nor attach through a pane's socket.
     */
    fun environment(loginEnvironment: Map<String, String>): Map<String, String> =
        loginEnvironment.filterKeys { it !in paneContext && !it.startsWith("HERDR_ACTIVE_") && !it.startsWith("HERDR_PLUGIN_") }
}
