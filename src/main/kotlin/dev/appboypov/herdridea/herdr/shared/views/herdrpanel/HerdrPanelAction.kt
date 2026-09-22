package dev.appboypov.herdridea.herdr.shared.views.herdrpanel

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * The IDE route to a panel interaction (design D7). Registered once per action id in plugin.xml;
 * it runs the [HerdrPanelViewService] handler registered under its own id.
 */
class HerdrPanelAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val id = ActionManager.getInstance().getId(this) ?: return
        HerdrPanelViewService.getInstance(project).run(id)
    }
}
