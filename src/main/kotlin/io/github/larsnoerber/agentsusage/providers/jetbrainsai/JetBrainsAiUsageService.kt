package io.github.larsnoerber.agentsusage.providers.jetbrainsai

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
class JetBrainsAiUsageService : UsageSource<JetBrainsAiUsage>, Disposable {
    private val reader = JetBrainsAiUsageReader()
    private val log = Logger.getInstance(JetBrainsAiUsageService::class.java)
    private val polling = UsagePolling(JetBrainsAiUsage(), reader::read,
        { AgentsUsageSettings.getInstance().refreshIntervalSeconds },
        { AgentsUsageSettings.getInstance().state.showJetBrainsAi && isAvailable() },
        provider = "jetbrainsai",
        describe = { UsageReadInfo(it.quota != null || it.subscription != null || it.topUp != null || it.unlimited, it.error, problem = it.problem) },
        retain = { old, fresh ->
            if (fresh.error != null && (old.quota != null || old.subscription != null || old.topUp != null || old.unlimited))
                old.copy(error = fresh.error, problem = fresh.problem) else fresh
        },
        record = { usage ->
            if (!usage.unlimited) {
                val history = UsageHistory.getInstance()
                (usage.subscription ?: usage.quota)?.percentLeft?.let {
                    history.record("jetbrainsai.subscription", (100 - it).toDouble(), usage.resetsAt)
                }
                usage.topUp?.percentLeft?.let { history.record("jetbrainsai.topup", (100 - it).toDouble(), null) }
            }
        }) { error ->
        log.debug("Unable to read JetBrains AI credits: ${error.javaClass.simpleName}")
        JetBrainsAiUsage(problem = UsageProblem.INCOMPATIBLE, error = "JetBrains AI credits are unavailable with this AI Assistant version")
    }

    override val current: JetBrainsAiUsage
        get() = polling.current

    override fun addListener(listener: (JetBrainsAiUsage) -> Unit) = polling.addListener(listener)
    override fun removeListener(listener: (JetBrainsAiUsage) -> Unit) = polling.removeListener(listener)
    override fun refresh() = polling.refresh()
    override fun dispose() = polling.dispose()

    companion object {
        fun getInstance(): JetBrainsAiUsageService =
            ApplicationManager.getApplication().getService(JetBrainsAiUsageService::class.java)
        fun isAvailable(): Boolean = JetBrainsAiUsageReader.isAvailable()
    }
}
