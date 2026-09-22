package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class HerdrConfigWatcherTest {
    @Test
    fun `Given Brian's config, when a cmd+j binding is saved, then the keymap claims cmd+j`(@TempDir dir: Path) {
        val cmdJ = HerdrKeyStroke(HerdrKey.Char('j'), HerdrKeyStroke.SUPER)
        val fixture = javaClass.getResource("/herdr/config.toml")!!.readText()
        val config = dir.resolve("config.toml")
        Files.writeString(config, fixture)

        HerdrConfigWatcher(config, intervalMillis = 20).use { watcher ->
            assertFalse(watcher.keymap.bindsDirect(cmdJ))

            Files.writeString(config, fixture + "\n[[keys.command]]\nkey = \"cmd+j\"\ntype = \"shell\"\ncommand = \"true\"\n")

            val deadline = System.nanoTime() + 5_000_000_000
            while (!watcher.keymap.bindsDirect(cmdJ) && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(watcher.keymap.bindsDirect(cmdJ), "cmd+j is claimed after the save")
        }
    }
}
