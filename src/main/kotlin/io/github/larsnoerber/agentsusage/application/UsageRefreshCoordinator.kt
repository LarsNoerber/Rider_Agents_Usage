package io.github.larsnoerber.agentsusage.application

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsageService
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsageService
import io.github.larsnoerber.agentsusage.providers.claudecode.ui.ClaudeCodeStatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsageService
import io.github.larsnoerber.agentsusage.providers.cursor.ui.CursorStatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.providers.codex.ui.CodexUsageStatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsageService
import io.github.larsnoerber.agentsusage.providers.copilot.ui.GitHubCopilotStatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsageService
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.ui.JetBrainsAiStatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.core.refresh.UsageRefreshStatuses
import java.util.concurrent.CopyOnWriteArrayList

/** Coordinates user actions across providers; persistence itself does not start services. */
internal object UsageRefreshCoordinator {
    private val visibilityListeners = CopyOnWriteArrayList<() -> Unit>()

    fun addVisibilityListener(listener: () -> Unit) {
        visibilityListeners.addIfAbsent(listener)
    }

    fun removeVisibilityListener(listener: () -> Unit) {
        visibilityListeners -= listener
    }

    fun changeVisibleAgents(openAi: Boolean, jetBrainsAi: Boolean, copilot: Boolean, claudeCode: Boolean, cursor: Boolean) {
        val settings = AgentsUsageSettings.getInstance().state
        val changed = settings.showOpenAi != openAi || settings.showJetBrainsAi != jetBrainsAi ||
            settings.showCopilot != copilot || settings.showClaudeCode != claudeCode ||
            settings.showCursor != cursor
        // Invalidate in-flight reads atomically with the choices they were started for.
        synchronized(UsageRefreshStatuses.publicationLock) {
            if (changed) UsageRefreshStatuses.invalidate()
            settings.showOpenAi = openAi
            settings.showJetBrainsAi = jetBrainsAi
            settings.showCopilot = copilot
            settings.showClaudeCode = claudeCode
            settings.showCursor = cursor
        }
        if (!changed) return
        if (openAi) CodexUsageService.getInstance().refresh()
        ApplicationManager.getApplication().invokeLater { visibilityListeners.forEach { it() } }
        refreshStatusWidgets()
        refreshOptionalProviders()
    }

    fun refreshStatusWidgets() {
        ApplicationManager.getApplication().invokeLater {
            ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }.forEach { project ->
                project.getService(StatusBarWidgetsManager::class.java).apply {
                    updateWidget(CodexUsageStatusBarWidgetFactory::class.java)
                    updateWidget(JetBrainsAiStatusBarWidgetFactory::class.java)
                    updateWidget(GitHubCopilotStatusBarWidgetFactory::class.java)
                    updateWidget(ClaudeCodeStatusBarWidgetFactory::class.java)
                    updateWidget(CursorStatusBarWidgetFactory::class.java)
                }
            }
        }
    }

    fun changeOverviewFeatures(weekly: Boolean, games: Boolean) {
        val settings = AgentsUsageSettings.getInstance().state
        if (settings.showWeeklyInsights == weekly && settings.showGames == games) return
        settings.showWeeklyInsights = weekly
        settings.showGames = games
        ApplicationManager.getApplication().invokeLater { visibilityListeners.forEach { it() } }
    }

    fun selectedProviderIds(): List<String> {
        val state = AgentsUsageSettings.getInstance().state
        return buildList {
            if (state.showOpenAi) add("codex")
            if (state.showJetBrainsAi) add("jetbrainsai")
            if (state.showCopilot) add("copilot")
            if (state.showClaudeCode) add("claude")
            if (state.showCursor) add("cursor")
        }
    }

    fun refreshAll() {
        if (AgentsUsageSettings.getInstance().state.showOpenAi) CodexUsageService.getInstance().refresh()
        refreshOptionalProviders()
    }

    fun changeRefreshInterval(seconds: Int) {
        AgentsUsageSettings.getInstance().applyRefreshInterval(seconds)
        // Restarting the CLI timer also schedules its immediate refresh.
        if (AgentsUsageSettings.getInstance().state.showOpenAi) CodexUsageService.getInstance().restartTimer()
        refreshOptionalProviders()
    }

    private fun refreshOptionalProviders() {
        // Availability checks precede service creation so optional plugins remain optional.
        val settings = AgentsUsageSettings.getInstance().state
        if (settings.showJetBrainsAi && JetBrainsAiUsageService.isAvailable()) JetBrainsAiUsageService.getInstance().refresh()
        if (settings.showCopilot && GitHubCopilotUsageService.isAvailable()) GitHubCopilotUsageService.getInstance().refresh()
        if (settings.showClaudeCode && ClaudeCodeUsageService.isInstalled()) ClaudeCodeUsageService.getInstance().refresh()
        if (settings.showCursor && CursorUsageService.isInstalled()) CursorUsageService.getInstance().refresh()
    }
}
