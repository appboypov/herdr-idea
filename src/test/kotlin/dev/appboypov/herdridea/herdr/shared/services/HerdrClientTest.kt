package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.enums.NativePlatform
import dev.appboypov.herdridea.ghostty.shared.services.NativeLibraryLoader
import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind
import dev.appboypov.herdridea.herdr.shared.models.HerdrPanelState
import dev.appboypov.herdridea.terminal.shared.models.TerminalColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class HerdrClientTest {
    private val directory: Path = Files.createTempDirectory("herdr-client")
    private val clients = mutableListOf<HerdrClient>()

    @AfterEach
    fun closeClients() = clients.forEach { it.close() }

    @Test
    fun `Given a configured herdr path that does not exist, When connecting, Then the panel shows herdr missing with that path`() {
        val client = client(herdrPath = "/nowhere/herdr")

        client.connect(HerdrSessionKind.SHARED, checkExists = true).get()

        assertEquals(HerdrPanelState.HerdrMissing(listOf("/nowhere/herdr")), client.state.value)
    }

    @Test
    fun `Given no native library for the platform, When connecting, Then the panel shows the unsupported platform`() {
        val client = client(herdrPath = stubHerdr(sessions = "default"), library = null)

        client.connect(HerdrSessionKind.SHARED, checkExists = true).get()

        assertEquals(HerdrPanelState.UnsupportedPlatform("test-os"), client.state.value)
    }

    @Test
    fun `Given Herdr does not list the project session, When reattaching to it, Then the panel shows the session missing`() {
        val client = client(herdrPath = stubHerdr(sessions = "default"))

        client.connect(HerdrSessionKind.PROJECT, checkExists = true).get()

        assertEquals(HerdrPanelState.SessionMissing("idea-demo-abc123"), client.state.value)
    }

    @Test
    fun `Given Herdr lists the project session, When reattaching to it, Then the client runs herdr on that session`() {
        val client = client(herdrPath = stubHerdr(sessions = "idea-demo-abc123"))

        client.connect(HerdrSessionKind.PROJECT, checkExists = true).get()

        assertEquals(HerdrPanelState.Live(HerdrSessionKind.PROJECT, "idea-demo-abc123"), client.state.value)
        awaitScreen(client, "args: --session idea-demo-abc123")
    }

    @Test
    fun `Given a missing project session, When creating it, Then the client starts herdr on it without asking Herdr first`() {
        val client = client(herdrPath = stubHerdr(sessions = "default"))

        client.connect(HerdrSessionKind.PROJECT, checkExists = false).get()

        assertEquals(HerdrPanelState.Live(HerdrSessionKind.PROJECT, "idea-demo-abc123"), client.state.value)
    }

    @Test
    fun `Given a live shared client, When the client exits with code 7, Then the panel shows it exited with 7`() {
        val client = client(herdrPath = stubHerdr(sessions = "default"))
        client.connect(HerdrSessionKind.SHARED, checkExists = true).get()
        assertEquals(HerdrPanelState.Live(HerdrSessionKind.SHARED, null), client.state.value)
        awaitScreen(client, "args:")

        client.paste("quit\n")

        assertEquals(HerdrPanelState.Exited(HerdrSessionKind.SHARED, 7), await(client.state) { it is HerdrPanelState.Exited })
    }

    @Test
    fun `Given an exited client, When reconnecting, Then a new client is live on the same session`() {
        val client = client(herdrPath = stubHerdr(sessions = "default"))
        client.connect(HerdrSessionKind.SHARED, checkExists = true).get()
        awaitScreen(client, "args:")
        client.paste("quit\n")
        await(client.state) { it is HerdrPanelState.Exited }

        client.connect(HerdrSessionKind.SHARED, checkExists = true).get()

        assertEquals(HerdrPanelState.Live(HerdrSessionKind.SHARED, null), client.state.value)
    }

    private fun client(herdrPath: String, library: GhosttyVt? = vt) = HerdrClient(
        projectDirectory = directory.toString(),
        projectSessionName = "idea-demo-abc123",
        configuredPath = { herdrPath },
        locator = HerdrLocator(shell = null),
        library = { library },
        platformName = "test-os",
        initialColors = TerminalColors(0xFFFFFF, 0x000000, List(16) { 0x808080 }),
        onClipboard = {},
    ).also { clients += it }

    /** A `herdr` stand-in: lists [sessions], else prints its arguments and exits 7 after one input line. */
    private fun stubHerdr(sessions: String): String {
        val script = directory.resolve("herdr")
        Files.writeString(
            script,
            """
            #!/bin/sh
            if [ "${'$'}1" = session ]; then printf '{"sessions":[{"name":"$sessions"}]}'; exit 0; fi
            printf 'args: %s\n' "${'$'}*"
            read line
            exit 7
            """.trimIndent(),
        )
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        return script.toString()
    }

    private fun awaitScreen(client: HerdrClient, text: String) {
        val frame = await(client.frame) { it != null && text in it.text() }
        assertTrue(text in frame!!.text())
    }

    private fun <T> await(flow: StateFlow<T>, predicate: (T) -> Boolean): T = runBlocking {
        withTimeout(10_000) { flow.first(predicate) }
    }

    companion object {
        private lateinit var vt: GhosttyVt

        @JvmStatic
        @BeforeAll
        fun load() {
            vt = NativeLibraryLoader(NativePlatform.current()!!, Files.createTempDirectory("ghostty-test")).load()
        }
    }
}
