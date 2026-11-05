package io.github.larsnoerber.agentsusage.providers.gemini

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import io.github.larsnoerber.agentsusage.core.UsageSource
import io.github.larsnoerber.agentsusage.core.refresh.UsagePolling
import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem
import io.github.larsnoerber.agentsusage.core.refresh.UsageReadInfo
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings

@Service(Service.Level.APP)
class GeminiUsageService : UsageSource<GeminiUsage>, Disposable {
    private val reader = GeminiUsageReader()
    private val log = Logger.getInstance(GeminiUsageService::class.java)
    private val polling = UsagePolling(GeminiUsage(error = if (isAvailable()) null else
        "Gemini for Android Studio is not installed or enabled"), reader::read,
        { AgentsUsageSettings.getInstance().refreshIntervalSeconds },
        { AgentsUsageSettings.getInstance().state.showGemini && isAvailable() },
        provider = "gemini",
        describe = { UsageReadInfo(it.models.isNotEmpty(), it.error, problem = it.problem, measurementTimeKnown = false) },
        retain = { old, fresh ->
            if (fresh.error != null && fresh.models.isEmpty() && old.models.isNotEmpty())
                old.copy(error = fresh.error, problem = fresh.problem, plan = fresh.plan) else fresh
        }) { error ->
        log.debug("Unable to read Gemini quota: ${error.javaClass.simpleName}")
        GeminiUsage(problem = UsageProblem.INCOMPATIBLE, error = "Gemini quota is unavailable with this plugin version")
    }

    override val current: GeminiUsage get() = polling.current
    override fun addListener(listener: (GeminiUsage) -> Unit) = polling.addListener(listener)
    override fun removeListener(listener: (GeminiUsage) -> Unit) = polling.removeListener(listener)
    override fun refresh() = polling.refresh()
    override fun dispose() = polling.dispose()

    companion object {
        fun getInstance(): GeminiUsageService = ApplicationManager.getApplication().getService(GeminiUsageService::class.java)
        fun isAvailable(): Boolean = GeminiUsageReader.isAvailable()
    }
}
