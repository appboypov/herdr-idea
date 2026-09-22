package dev.appboypov.herdridea.ghostty.shared.services

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.appboypov.herdridea.core.services.HerdrLog
import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.enums.NativePlatform
import java.nio.file.Path

/** Loads libghostty-vt once per IDE process. */
@Service(Service.Level.APP)
class GhosttyLibrary {
    private val log = HerdrLog.of(GhosttyLibrary::class.java)

    /** The bound library, or null on a platform the plugin does not ship a library for. */
    val vt: GhosttyVt? by lazy {
        val platform = NativePlatform.current() ?: return@lazy null
        try {
            NativeLibraryLoader(platform, Path.of(PathManager.getSystemPath(), "herdr-idea", "native")).load()
                .also { log.info("libghostty-vt loaded", "platform" to platform.id) }
        } catch (e: Exception) {
            log.error("libghostty-vt failed to load", e, "platform" to platform.id)
            null
        }
    }

    /** The JVM's platform as `os-arch`, for messages. */
    val platformName: String get() = "${System.getProperty("os.name")}-${System.getProperty("os.arch")}"

    companion object {
        fun getInstance(): GhosttyLibrary = service()
    }
}
