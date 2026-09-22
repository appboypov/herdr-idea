package dev.appboypov.herdridea.herdr.shared.services

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** The plugin's application settings. */
@Service(Service.Level.APP)
@State(name = "HerdrSettings", storages = [Storage("herdr-idea.xml")])
class HerdrSettings : SimplePersistentStateComponent<HerdrSettings.Values>(Values()) {
    class Values : BaseState() {
        /** The `herdr` executable to use; empty means find it on the login-shell PATH. */
        var herdrPath by string("")
    }

    var herdrPath: String
        get() = state.herdrPath.orEmpty()
        set(value) {
            state.herdrPath = value.trim()
        }

    companion object {
        fun getInstance(): HerdrSettings = service()
    }
}
