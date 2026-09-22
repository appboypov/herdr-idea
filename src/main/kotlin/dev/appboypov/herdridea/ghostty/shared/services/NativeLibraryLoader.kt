package dev.appboypov.herdridea.ghostty.shared.services

import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.enums.NativePlatform
import dev.appboypov.herdridea.ghostty.shared.exceptions.GhosttyException
import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Extracts the bundled libghostty-vt for [platform] into [extractRoot] and binds it.
 *
 * Each library is extracted once per content hash, so plugin updates never load a stale copy.
 */
class NativeLibraryLoader(
    private val platform: NativePlatform,
    private val extractRoot: Path,
    private val classLoader: ClassLoader = NativeLibraryLoader::class.java.classLoader,
) {
    fun load(): GhosttyVt {
        val bytes = classLoader.getResourceAsStream(platform.resourcePath)?.use { it.readAllBytes() }
            ?: throw GhosttyException("Bundled library ${platform.resourcePath} is missing from the plugin")
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).take(16)
        val target = extractRoot.resolve(platform.id).resolve(hash).resolve(platform.libraryFileName)
        if (!Files.exists(target)) {
            Files.createDirectories(target.parent)
            val temp = Files.createTempFile(target.parent, "lib", ".part")
            Files.write(temp, bytes)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        val lookup = try {
            SymbolLookup.libraryLookup(target, Arena.global())
        } catch (e: IllegalArgumentException) {
            throw GhosttyException("Cannot load $target", e)
        }
        return GhosttyVt(lookup)
    }
}
