package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.core.services.HerdrLog
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Keeps [keymap] equal to Herdr's config at [path] (design D5): parses it at start and again whenever
 * its modification time or size changes, including when an editor replaces the file or it appears
 * or disappears. A missing file means Herdr's defaults.
 */
class HerdrConfigWatcher(private val path: Path, intervalMillis: Long = 500) : AutoCloseable {
    private val log = HerdrLog.of(HerdrConfigWatcher::class.java)
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "Herdr config watch").apply { isDaemon = true } }
    private var stamp: Pair<Long, Long>? = null

    @Volatile var keymap: HerdrKeymap = load()
        private set

    init {
        scheduler.scheduleWithFixedDelay(::check, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
    }

    override fun close() {
        scheduler.shutdownNow()
    }

    private fun check() {
        try {
            if (currentStamp() != stamp) keymap = load()
        } catch (e: Exception) {
            log.warn("Herdr config reload failed", e, "path" to path)
        }
    }

    private fun load(): HerdrKeymap {
        stamp = currentStamp()
        val text = if (Files.isRegularFile(path)) Files.readString(path) else null
        val result = HerdrKeymapParser.parse(text)
        if (result.skipped.isNotEmpty()) log.warn("Herdr key bindings skipped", null, "path" to path, "skipped" to result.skipped)
        log.info("Herdr keymap loaded", "path" to path, "direct" to result.keymap.directBindings.size, "prefixed" to result.keymap.prefixedBindings.size)
        return result.keymap
    }

    private fun currentStamp(): Pair<Long, Long>? =
        if (Files.isRegularFile(path)) Files.getLastModifiedTime(path).toMillis() to Files.size(path) else null

    companion object {
        /** `$XDG_CONFIG_HOME/herdr/config.toml`, else `~/.config/herdr/config.toml`. */
        fun defaultPath(environment: Map<String, String> = System.getenv()): Path {
            val base = environment["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".config")
            return base.resolve("herdr").resolve("config.toml")
        }
    }
}
