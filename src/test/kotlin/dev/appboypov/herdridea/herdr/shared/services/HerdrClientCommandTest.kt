package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HerdrClientCommandTest {
    @Test
    fun `given an IDE started from a Herdr pane, when the client environment is built, then pane context is dropped and user settings stay`() {
        val login = mapOf(
            "HERDR_ENV" to "1",
            "HERDR_SOCKET_PATH" to "/s.sock",
            "HERDR_PANE_ID" to "p1",
            "HERDR_ACTIVE_PANE_CWD" to "/x",
            "HERDR_CONFIG_PATH" to "/custom/config.toml",
            "PATH" to "/usr/bin",
        )

        assertEquals(
            mapOf("HERDR_CONFIG_PATH" to "/custom/config.toml", "PATH" to "/usr/bin"),
            HerdrClientCommand.environment(login),
        )
    }

    @Test
    fun `given each session kind, when the command is built, then shared attaches the default and project names its session`() {
        assertEquals(listOf("/bin/herdr"), HerdrClientCommand.command("/bin/herdr", HerdrSessionKind.SHARED, "idea-a-123456"))
        assertEquals(
            listOf("/bin/herdr", "--session", "idea-a-123456"),
            HerdrClientCommand.command("/bin/herdr", HerdrSessionKind.PROJECT, "idea-a-123456"),
        )
    }
}
