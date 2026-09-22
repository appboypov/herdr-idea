package dev.appboypov.herdridea.herdr.shared.apis

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import dev.appboypov.herdridea.core.services.HerdrLog
import dev.appboypov.herdridea.herdr.shared.exceptions.HerdrActionException
import dev.appboypov.herdridea.herdr.shared.views.herdrpanel.HerdrPanelViewService
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

/**
 * The dispatcher (design D7): runs a panel action by name from outside the IDE through the built-in
 * server, `POST /api/herdr?action=<name>&project=<name>&<arg>=<value>`. Localhost only; it runs as the
 * user who runs the IDE. Answers `{"ok":true,"result":…}` or an error status with the reason.
 */
class HerdrActionRestService : RestService() {
    private val log = HerdrLog.of(HerdrActionRestService::class.java)
    private val json = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    override fun getServiceName() = "herdr"

    override fun isMethodSupported(method: HttpMethod) = method == HttpMethod.POST

    override fun getMaxRequestsPerMinute() = 600

    override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
        val action = getStringParameter("action", urlDecoder) ?: return "Missing parameter: action"
        val projectName = getStringParameter("project", urlDecoder)
        val project = ProjectManager.getInstance().openProjects.firstOrNull { it.name == projectName }
            ?: if (projectName == null) getLastFocusedOrOpenedProject() else null
        project ?: return "No open project${projectName?.let { " named $it" } ?: ""}"
        val args = urlDecoder.parameters().filterKeys { it != "action" && it != "project" }.mapValues { it.value.last() }
        var result: Any? = null
        var failure: Throwable? = null
        ApplicationManager.getApplication().invokeAndWait({
            try {
                result = HerdrPanelViewService.getInstance(project).run(action, args)
            } catch (e: Throwable) {
                failure = e
            }
        }, ModalityState.any())
        failure?.let {
            if (it is HerdrActionException) return it.message
            log.error("dispatched action failed", it, "action" to action)
            return "Action failed: ${it.message}"
        }
        val out = BufferExposingByteArrayOutputStream()
        out.write(json.toJson(mapOf("ok" to true, "action" to action, "project" to project.name, "result" to result)).toByteArray())
        send(out, request, context)
        return null
    }
}
