package dev.appboypov.herdridea.herdr.shared.services

import java.security.MessageDigest
import java.util.HexFormat

/** Names of the Herdr sessions the plugin creates (design D6). */
object HerdrSessionNames {
    private const val MAX_LENGTH = 64
    private const val PREFIX = "idea-"

    /**
     * `idea-<project-name-slug>-<hash6>`: stable for one project path, distinct for two projects
     * with the same name, and always within Herdr's rule (ASCII letters, digits, `.`, `_`, `-`; at most 64 bytes).
     */
    fun projectSession(projectName: String, basePath: String): String {
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(basePath.toByteArray())).take(6)
        val room = MAX_LENGTH - PREFIX.length - 1 - hash.length
        val slug = projectName.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.')
            .take(room)
            .trimEnd('-', '.')
            .ifEmpty { "project" }
        return "$PREFIX$slug-$hash"
    }
}
