package dev.appboypov.herdridea.terminal.shared.services

import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.enums.NativePlatform
import dev.appboypov.herdridea.ghostty.shared.services.NativeLibraryLoader
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.terminal.shared.models.TerminalColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TerminalSessionTest {
    private val frame = AtomicReference<ScreenFrame>()
    private val exit = CompletableFuture<Int>()

    @Test
    fun `given a program that prints and exits, when it runs, then the frame shows its output and the exit code is reported`() {
        start("printf 'a\\tb\\n'; exit 3").use {
            assertEquals(3, exit.get(10, TimeUnit.SECONDS))
            assertEquals("a       b", frame.get().rows[0].text().trimEnd())
        }
    }

    @Test
    fun `given a running program, when the session is resized, then the program sees the new size`() {
        start("read x; echo \"size \$(tput cols)x\$(tput lines)\"; read y").use { session ->
            session.resize(100, 30, 8, 16)
            session.paste("\n")

            awaitText("size 100x30")
        }
    }

    private fun start(script: String) = TerminalSession.start(
        vt,
        listOf("/bin/sh", "-c", script),
        System.getProperty("user.home"),
        System.getenv() + ("TERM" to "xterm-256color"),
        80,
        24,
        TerminalColors(0xFFFFFF, 0x000000, List(16) { 0x808080 }),
        onFrame = frame::set,
        onExit = { exit.complete(it) },
    )

    private fun awaitText(text: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (frame.get()?.text()?.contains(text) == true) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for '$text'; screen was:\n${frame.get()?.text()}")
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
