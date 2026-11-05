package io.github.larsnoerber.agentsusage.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

// Keep legacy constructor defaults so omitted fields in existing XML retain their previous meaning.
data class AgentsUsageState(
    @Volatile var codexPath: String = "",
    @Volatile var refreshSeconds: Int = AgentsUsageSettings.DEFAULT_REFRESH_SECONDS,
    @Volatile var showOpenAi: Boolean = true,
    @Volatile var showJetBrainsAi: Boolean = true,
    @Volatile var showCopilot: Boolean = true,
    @Volatile var showClaudeCode: Boolean = true,
    @Volatile var showCursor: Boolean = false,
    @Volatile var showGemini: Boolean = ApplicationInfo.getInstance().build.productCode == "AI",
    @Volatile var geminiStatusAllModels: Boolean = true,
    @Volatile var geminiStatusModels: MutableList<String> = mutableListOf(),
    @Volatile var showWeeklyInsights: Boolean = true,
    @Volatile var showGames: Boolean = true,
    @Volatile var weeklyInsightsExpanded: Boolean = true,
    @Volatile var gamesExpanded: Boolean = true,
    @Volatile var agentUsageExpanded: Boolean = true
)

// Keep the original identifiers so saved paths and refresh intervals survive upgrades.
@State(name = "CodexUsageSettings", storages = [Storage("codex-usage.xml")])
class AgentsUsageSettings : PersistentStateComponent<AgentsUsageState> {
    // Fresh installations start with JetBrains AI and Android Studio's Gemini; loaded XML preserves saved selections.
    @Volatile private var state = AgentsUsageState(
        showOpenAi = false, showCopilot = false, showClaudeCode = false,
        showWeeklyInsights = false, showGames = false
    )

    val refreshIntervalSeconds: Int
        get() = state.refreshSeconds.coerceIn(MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS)

    override fun getState(): AgentsUsageState = state

    override fun loadState(state: AgentsUsageState) {
        this.state = state.copy(refreshSeconds = state.refreshSeconds.coerceIn(MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS))
    }

    /** Only persists the choice; UsageRefreshCoordinator applies it to running providers. */
    fun applyRefreshInterval(seconds: Int) {
        state.refreshSeconds = seconds.coerceIn(MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS)
    }

    companion object {
        const val MIN_REFRESH_SECONDS = 10
        const val MAX_REFRESH_SECONDS = 3600
        const val DEFAULT_REFRESH_SECONDS = 60

        fun getInstance(): AgentsUsageSettings =
            ApplicationManager.getApplication().getService(AgentsUsageSettings::class.java)
    }
}
