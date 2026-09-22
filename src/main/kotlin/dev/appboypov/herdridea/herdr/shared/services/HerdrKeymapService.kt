package dev.appboypov.herdridea.herdr.shared.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap

/** Herdr's current keymap for every panel in the IDE, kept equal to Herdr's config file. */
@Service(Service.Level.APP)
class HerdrKeymapService : Disposable {
    private val watcher = HerdrConfigWatcher(HerdrConfigWatcher.defaultPath())

    val keymap: HerdrKeymap get() = watcher.keymap

    override fun dispose() = watcher.close()

    companion object {
        fun getInstance(): HerdrKeymapService = service()
    }
}
