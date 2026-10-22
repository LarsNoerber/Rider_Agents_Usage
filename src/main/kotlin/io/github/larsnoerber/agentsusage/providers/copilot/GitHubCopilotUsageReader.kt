package io.github.larsnoerber.agentsusage.providers.copilot

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import io.github.larsnoerber.agentsusage.core.reflection.PluginApi
import io.github.larsnoerber.agentsusage.core.reflection.loadedPlugin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Uses the IDE quota service or the installed ACP package's authenticated language server. */
internal class GitHubCopilotUsageReader : Disposable {
    private val api = PluginApi("GitHub Copilot")
    private val agentReader = GitHubCopilotAgentUsageReader()
    private var agentSnapshot: GitHubCopilotUsage? = null

    fun read(requestUpdate: Boolean): GitHubCopilotUsage {
        val pluginUsage = if (loadedPlugin(PLUGIN_ID) != null) {
            try {
                readPlugin(requestUpdate)
            } catch (_: Exception) {
                GitHubCopilotUsage(problem = UsageProblem.INCOMPATIBLE, error = "The GitHub Copilot IDE quota service is unavailable.")
            } catch (_: LinkageError) {
                GitHubCopilotUsage(problem = UsageProblem.INCOMPATIBLE, error = "This GitHub Copilot IDE version does not expose its quota service.")
            }
        } else null
        if (pluginUsage != null && pluginUsage.error == null) return pluginUsage
        if (!GitHubCopilotAgentUsageReader.isAvailable()) return pluginUsage
            ?: GitHubCopilotUsage(error = "GitHub Copilot agent package is not installed or enabled.")
        if (requestUpdate || agentSnapshot == null) agentSnapshot = agentReader.read()
        return agentSnapshot!!
    }

    private fun readPlugin(requestUpdate: Boolean): GitHubCopilotUsage {
        val plugin = loadedPlugin(PLUGIN_ID)
            ?: return GitHubCopilotUsage(error = "GitHub Copilot is not installed or enabled")
        val serviceClass = Class.forName("com.github.copilot.services.CopilotQuotaService", true, plugin.pluginClassLoader)
        val service = ApplicationManager.getApplication().getService(serviceClass)
            ?: error("GitHub Copilot quota service is unavailable")
        if (requestUpdate) getter(service, "refreshQuotaInBackground")
        val flow = getter(service, "getQuotaInfo") ?: error("GitHub Copilot quota state is unavailable")
        val state = api.flowValue(flow)
            ?: return GitHubCopilotUsage(problem = UsageProblem.AUTHENTICATION, error = "Waiting for GitHub Copilot usage. Sign in to Copilot.")
        val response = getter(state, "getQuotaResponse")
            ?: return GitHubCopilotUsage(problem = UsageProblem.AUTHENTICATION, error = "Waiting for GitHub Copilot usage. Sign in to Copilot.")
        val tokenBilling = getter(response, "getTokenBasedBillingEnabled", required = false) == true
        val plan = (getter(response, "getCopilotPlan", required = false) as? String)?.takeIf { it.isNotBlank() }
        val freePlan = plan.equals("free", ignoreCase = true)
        fun quota(name: String, title: String, showCounts: Boolean = true): CopilotQuota? {
            val value = getter(response, name, required = false) ?: return null
            // Copilot uses an EMPTY sentinel for categories that have not been reported yet.
            val companion = value.javaClass.getField("Companion").get(null)
            if (value == getter(companion, "getEMPTY")) return null
            val unlimited = getter(value, "getUnlimited") == true
            val percentage = (getter(value, "getPercentRemaining") as? Number)?.toDouble()
                ?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.roundToInt()
            return CopilotQuota(
                title = title,
                percentLeft = if (unlimited) null else percentage,
                remaining = if (showCounts) (getter(value, "getQuotaRemaining", required = false) as? Number)?.toInt()?.coerceAtLeast(0) else null,
                total = if (showCounts) (getter(value, "getEntitlement", required = false) as? Number)?.toInt()?.coerceAtLeast(0) else null,
                unlimited = unlimited
            )
        }
        // Copilot's Free dialog uses chat as the included quota. Its premium field can contain an unused
        // zero balance even while all Free credits are available, so it must not be the primary quota.
        val premium = if (freePlan) null else quota("getPremiumInteractions", if (tokenBilling) "AI credits" else "Premium requests", !tokenBilling)
        val chat = quota("getChat", if (freePlan && tokenBilling) "AI credits" else "Chat", !tokenBilling)
        val completions = quota("getCompletions", "Completions")
        val primary = if (freePlan) chat ?: completions else premium
        val reset = (getter(response, "getResetDateUtc", required = false) as? String)?.takeIf { it.isNotBlank() }
            ?: (getter(response, "getResetDate", required = false) as? String)
        return GitHubCopilotUsage(
            primary = primary,
            chat = chat,
            completions = completions,
            plan = plan,
            resetsAt = reset?.let(::parseReset),
            reportedAt = (getter(state, "getUpdatedAt", required = false) as? Instant)?.epochSecond,
            error = if (primary == null) "GitHub Copilot has not reported a usage quota yet" else null
        )
    }

    private fun parseReset(value: String): Long? = runCatching { Instant.parse(value).epochSecond }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toEpochSecond() }.getOrNull()

    private fun getter(target: Any, name: String, required: Boolean = true): Any? = api.getter(target, name, required)

    override fun dispose() = agentReader.dispose()

    companion object {
        const val PLUGIN_ID = "com.github.copilot"
        fun isAvailable(): Boolean = loadedPlugin(PLUGIN_ID) != null || GitHubCopilotAgentUsageReader.isAvailable()
    }
}
