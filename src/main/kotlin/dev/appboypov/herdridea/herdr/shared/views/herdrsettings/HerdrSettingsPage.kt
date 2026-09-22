package dev.appboypov.herdridea.herdr.shared.views.herdrsettings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import dev.appboypov.herdridea.core.services.HerdrIdeaBundle
import dev.appboypov.herdridea.herdr.shared.services.HerdrSettings

/** Settings | Tools | Herdr: the `herdr` path override (spec: the user can set the herdr path). */
class HerdrSettingsPage : BoundConfigurable(HerdrIdeaBundle.message("settings.displayName")) {
    private val settings = HerdrSettings.getInstance()

    override fun createPanel() = panel {
        row(HerdrIdeaBundle.message("settings.herdrPath.label")) {
            textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile().withTitle(HerdrIdeaBundle.message("settings.herdrPath.chooserTitle")))
                .bindText(settings::herdrPath)
                .align(AlignX.FILL)
                .comment(HerdrIdeaBundle.message("settings.herdrPath.comment"))
        }
    }
}
