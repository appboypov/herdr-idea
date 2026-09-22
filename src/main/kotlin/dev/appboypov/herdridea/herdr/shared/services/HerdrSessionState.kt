package dev.appboypov.herdridea.herdr.shared.services

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind

/** Which Herdr session this project's panel attaches to, remembered per project (design D6). */
@Service(Service.Level.PROJECT)
@State(name = "HerdrSessionState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class HerdrSessionState(private val project: Project) : SimplePersistentStateComponent<HerdrSessionState.Values>(Values()) {
    class Values : BaseState() {
        var kind by enum(HerdrSessionKind.SHARED)
    }

    var kind: HerdrSessionKind
        get() = state.kind
        set(value) {
            state.kind = value
        }

    /** This project's own session name. */
    val projectSessionName: String
        get() = HerdrSessionNames.projectSession(project.name, project.basePath ?: project.name)

    companion object {
        fun getInstance(project: Project): HerdrSessionState = project.service()
    }
}
