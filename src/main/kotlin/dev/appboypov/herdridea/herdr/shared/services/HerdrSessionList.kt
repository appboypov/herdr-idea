package dev.appboypov.herdridea.herdr.shared.services

import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

/** Reads the sessions Herdr knows through `herdr session list --json`. */
object HerdrSessionList {
    /** Session names, or null when Herdr could not be asked. */
    fun names(herdrPath: String, environment: Map<String, String>): Set<String>? = try {
        val process = ProcessBuilder(herdrPath, "session", "list", "--json")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .apply {
                environment().clear()
                environment().putAll(environment)
            }
            .start()
        val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
        if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) parse(output) else null
    } catch (_: Exception) {
        null
    }

    fun parse(json: String): Set<String> =
        JsonParser.parseString(json).asJsonObject.getAsJsonArray("sessions")
            .map { it.asJsonObject.get("name").asString }
            .toSet()
}
