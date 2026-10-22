package io.github.larsnoerber.agentsusage.providers.codex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import io.github.larsnoerber.agentsusage.core.UsageSource
import io.github.larsnoerber.agentsusage.core.refresh.UsagePolling
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import java.io.File
import io.github.larsnoerber.agentsusage.core.history.UsageHistory
import io.github.larsnoerber.agentsusage.core.refresh.UsageReadInfo

@Service(Service.Level.APP)
class CodexUsageService : UsageSource<CodexUsage>, Disposable {
    private val reader = CodexUsageReader()
    private val polling = UsagePolling(CodexUsage(),
        { reader.read(AgentsUsageSettings.getInstance().state.codexPath) },
        { AgentsUsageSettings.getInstance().refreshIntervalSeconds },
        { AgentsUsageSettings.getInstance().state.showOpenAi },
        provider = "codex",
        describe = { UsageReadInfo(it.fiveHourLeft != null || it.weeklyLeft != null, it.error, it.updatedAt, it.problem) },
        retain = { old, fresh ->
            if (fresh.error != null && (old.fiveHourLeft != null || old.weeklyLeft != null))
                old.copy(error = fresh.error, problem = fresh.problem) else fresh
        },
        record = { usage ->
            val history = UsageHistory.getInstance()
            usage.fiveHourLeft?.let { history.record("codex.session", (100 - it).toDouble(), usage.fiveHourResetsAt, usage.updatedAt) }
            usage.weeklyLeft?.let { history.record("codex.weekly", (100 - it).toDouble(), usage.weeklyResetsAt, usage.updatedAt) }
        },
        localPolling = false) {
        CodexUsage(error = "Unable to read Codex usage. Check the CLI, sign-in and connection.")
    }

    override val current: CodexUsage get() = polling.current
    override fun addListener(listener: (CodexUsage) -> Unit) = polling.addListener(listener)
    override fun removeListener(listener: (CodexUsage) -> Unit) = polling.removeListener(listener)
    override fun refresh() = polling.refresh()
    fun restartTimer() = polling.refresh()
    fun detectCodexPath(): String? = runCatching { reader.detectCodexPath() }.getOrNull()
    override fun dispose() { polling.dispose(); reader.dispose() }

    companion object {
        fun isAvailable(): Boolean {
            val configured = AgentsUsageSettings.getInstance().state.codexPath.trim()
            if (configured.isNotEmpty() && (configured.contains('\\') || configured.contains('/'))) {
                return File(configured).isFile
            }
            val names = if (configured.isBlank()) listOf("codex.exe", "codex.cmd", "codex.bat", "codex") else buildList {
                add(configured)
                if (File(configured).extension.isEmpty()) {
                    add("$configured.exe")
                    add("$configured.cmd")
                    add("$configured.bat")
                }
            }
            if (names.any { File(it).isFile }) return true
            val pathEntries = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
            return pathEntries.any { directory -> names.any { name -> File(directory, name).isFile } }
        }

        fun getInstance(): CodexUsageService =
            ApplicationManager.getApplication().getService(CodexUsageService::class.java)
    }
}
