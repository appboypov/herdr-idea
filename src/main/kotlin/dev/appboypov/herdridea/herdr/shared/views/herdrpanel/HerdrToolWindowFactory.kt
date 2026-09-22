package dev.appboypov.herdridea.herdr.shared.views.herdrpanel

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Registers the docked `Herdr` panel (spec: Herdr panel docks in the IDE). */
class HerdrToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(null, "", false)
        val view = HerdrPanelView(HerdrPanelViewModel(HerdrPanelViewService.getInstance(project)), content, content)
        content.component = view.component
        content.preferredFocusableComponent = view.focusTarget
        toolWindow.contentManager.addContent(content)
        val actions = ActionManager.getInstance()
        toolWindow.setTitleActions(listOfNotNull(actions.getAction(HEADER_GROUP)))
        (actions.getAction(GEAR_GROUP) as? ActionGroup)?.let(toolWindow::setAdditionalGearActions)
    }

    companion object {
        const val ID = "Herdr"
        private const val HEADER_GROUP = "herdr.panel.header"
        private const val GEAR_GROUP = "herdr.panel.gear"
    }
}
