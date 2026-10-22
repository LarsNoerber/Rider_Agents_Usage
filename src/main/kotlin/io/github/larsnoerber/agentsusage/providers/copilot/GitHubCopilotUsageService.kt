package io.github.larsnoerber.agentsusage.providers.copilot

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import io.github.larsnoerber.agentsusage.core.UsageSource
import io.github.larsnoerber.agentsusage.core.history.UsageHistory
import io.github.larsnoerber.agentsusage.core.refresh.UsageReadInfo
import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem
import io.github.larsnoerber.agentsusage.core.refresh.UsagePolling
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings

@Service(Service.Level.APP)
class GitHubCopilotUsageService : UsageSource<GitHubCopilotUsage>, Disposable {
    private val reader = GitHubCopilotUsageReader()
    private val log = Logger.getInstance(GitHubCopilotUsageService::class.java)
    private val polling = UsagePolling(GitHubCopilotUsage(error = if (isAvailable()) null else
        "Install the GitHub Copilot ACP agent or enable its IDE plugin to load quota."), reader::read,
        { AgentsUsageSettings.getInstance().refreshIntervalSeconds },
        { AgentsUsageSettings.getInstance().state.showCopilot && isAvailable() },
        provider = "copilot",
        describe = { UsageReadInfo(it.primary != null || it.chat != null || it.completions != null, it.error,
            it.reportedAt?.times(1000) ?: 0, it.problem) },
        retain = { old, fresh ->
            if (fresh.error != null && (old.primary != null || old.chat != null || old.completions != null))
                old.copy(error = fresh.error, problem = fresh.problem) else fresh
        },
        record = { usage ->
            val history = UsageHistory.getInstance()
            listOfNotNull(usage.primary, usage.chat, usage.completions).distinctBy { it.title }.forEach { quota ->
                quota.percentUsed?.let {
                    val key = quota.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "-")
                    history.record("copilot.$key", it.toDouble(), usage.resetsAt, usage.reportedAt?.times(1000) ?: System.currentTimeMillis())
                }
            }
        }) { error ->
        log.debug("Unable to read GitHub Copilot usage: ${error.javaClass.simpleName}")
        GitHubCopilotUsage(problem = UsageProblem.INCOMPATIBLE, error = "GitHub Copilot usage is unavailable with this Copilot version")
    }

    override val current: GitHubCopilotUsage
        get() = polling.current

    override fun addListener(listener: (GitHubCopilotUsage) -> Unit) = polling.addListener(listener)
    override fun removeListener(listener: (GitHubCopilotUsage) -> Unit) = polling.removeListener(listener)
    override fun refresh() = polling.refresh()
    override fun dispose() { polling.dispose(); reader.dispose() }

    companion object {
        fun getInstance(): GitHubCopilotUsageService =
            ApplicationManager.getApplication().getService(GitHubCopilotUsageService::class.java)
        fun isAvailable(): Boolean = GitHubCopilotUsageReader.isAvailable()
    }
}
