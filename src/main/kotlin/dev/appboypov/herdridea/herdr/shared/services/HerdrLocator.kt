package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.core.services.HerdrLog
import dev.appboypov.herdridea.herdr.shared.models.HerdrLocation
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Finds `herdr` the way a terminal would (design D3): the plugin setting when set, otherwise
 * `command -v herdr` in the user's login shell, so an IDE launched from the Dock sees the same
 * PATH as a shell. The login-shell environment is captured once and reused for clients.
 *
 * @param shell the user's login shell, normally `$SHELL`.
 */
class HerdrLocator(
    private val shell: String? = System.getenv("SHELL"),
    private val fallbackEnvironment: Map<String, String> = System.getenv(),
    private val timeoutSeconds: Long = 10,
) {
    private val log = HerdrLog.of(HerdrLocator::class.java)

    fun locate(configuredPath: String?): HerdrLocation {
        val (shellPath, environment) = queryLoginShell()
        if (!configuredPath.isNullOrBlank()) {
            return if (isExecutable(configuredPath)) HerdrLocation.Found(configuredPath, environment)
            else HerdrLocation.Missing(listOf(configuredPath))
        }
        if (shellPath != null && isExecutable(shellPath)) return HerdrLocation.Found(shellPath, environment)
        val path = environment["PATH"].orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        return HerdrLocation.Missing(path.map { "$it/herdr" })
    }

    /** Runs `command -v herdr` and `env -0` in one login shell. */
    private fun queryLoginShell(): Pair<String?, Map<String, String>> {
        val shell = shell?.takeIf { isExecutable(it) } ?: return null to fallbackEnvironment
        return try {
            val process = ProcessBuilder(shell, "-lc", "command -v herdr; printf '$MARKER'; env -0")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                .start()
            val output = try {
                CompletableFuture.supplyAsync { process.inputStream.readAllBytes().toString(Charsets.UTF_8) }
                    .get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                process.destroyForcibly()
                log.warn("Login shell timed out", e, "shell" to shell)
                return null to fallbackEnvironment
            }
            val markerAt = output.indexOf(MARKER)
            if (markerAt < 0) return null to fallbackEnvironment
            val herdr = output.substring(0, markerAt).lines().lastOrNull { it.startsWith("/") }
            val environment = output.substring(markerAt + MARKER.length).split('\u0000')
                .mapNotNull { entry -> entry.indexOf('=').takeIf { it > 0 }?.let { entry.substring(0, it) to entry.substring(it + 1) } }
                .toMap()
            herdr to environment.ifEmpty { fallbackEnvironment }
        } catch (e: java.io.IOException) {
            log.warn("Login shell could not start", e, "shell" to shell)
            null to fallbackEnvironment
        }
    }

    private fun isExecutable(path: String) = File(path).let { it.isFile && it.canExecute() }

    private companion object {
        const val MARKER = "\n__HERDR_IDEA_ENV__\n"
    }
}
