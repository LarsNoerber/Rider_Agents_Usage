package io.github.larsnoerber.agentsusage.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.larsnoerber.agentsusage.application.UsageRefreshCoordinator
import io.github.larsnoerber.agentsusage.PluginVersion
import io.github.larsnoerber.agentsusage.providers.codex.ui.CodexUsagePanel
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsageService
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsageService
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsageService
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsageService
import io.github.larsnoerber.agentsusage.core.agents.AcpAgentInstallation
import io.github.larsnoerber.agentsusage.providers.claudecode.ui.ClaudeCodeUsagePanel
import io.github.larsnoerber.agentsusage.providers.cursor.ui.CursorUsagePanel
import io.github.larsnoerber.agentsusage.providers.copilot.ui.GitHubCopilotUsagePanel
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsageService
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.ui.JetBrainsAiUsagePanel
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.AgentSection
import io.github.larsnoerber.agentsusage.ui.components.UsageToolbar
import io.github.larsnoerber.agentsusage.ui.settings.RefreshSettingsPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.Color
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.HierarchyEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.ScrollPaneConstants

/** Composes provider views and navigation; each child owns its own data subscription. */
internal class AgentsUsagePanel : JPanel(BorderLayout()), Disposable {
    private val pageLayout = object : CardLayout() {
        override fun preferredLayoutSize(parent: Container): Dimension {
            val size = parent.components.firstOrNull { it.isVisible }?.preferredSize ?: Dimension()
            val insets = parent.insets
            return Dimension(size.width + insets.left + insets.right, size.height + insets.top + insets.bottom)
        }
    }
    private val pages = JPanel(pageLayout).apply { isOpaque = false }
    private val overview = JPanel(BorderLayout(0, 5)).apply { isOpaque = false }
    private val overviewContent = JPanel(BorderLayout(0, JBUI.scale(8))).apply { isOpaque = false }
    private val agentOverview = JPanel(BorderLayout(0, JBUI.scale(4))).apply { isOpaque = false }
    private val overviewExtras = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val sections = JPanel(GridBagLayout()).apply { isOpaque = false }
    private val agentUsageToggle = javax.swing.JButton().apply {
        horizontalAlignment = javax.swing.JButton.LEFT
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        margin = JBUI.emptyInsets()
        font = font.deriveFont(Font.BOLD, 11f)
        addActionListener { setAgentUsageExpanded(!settingsState().agentUsageExpanded) }
    }
    private var agentUsageExpanded = settingsState().agentUsageExpanded
    private var insights: WeeklyUsageInsightsPanel? = null
    private var games: GamesPanel? = null
    private var codex: CodexUsagePanel? = null
    private var jetBrainsAi: JetBrainsAiUsagePanel? = null
    private var copilot: GitHubCopilotUsagePanel? = null
    private var claudeCode: ClaudeCodeUsagePanel? = null
    private var cursor: CursorUsagePanel? = null
    private var visibleAgentSelection: List<Boolean>? = null
    private var disposed = false
    private val visibilityListener: () -> Unit = { if (!disposed) updateVisibleAgents() }
    private val toolbar = UsageToolbar(UsageRefreshCoordinator::refreshAll, ::showSettings)
    private val settings = RefreshSettingsPanel { showPage(OVERVIEW) }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(6)
        overview.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JBLabel("AgentMeter").apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.WEST)
            add(toolbar, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        agentOverview.add(agentUsageToggle, BorderLayout.NORTH)
        agentOverview.add(sections, BorderLayout.CENTER)
        overviewContent.add(agentOverview, BorderLayout.NORTH)
        overviewContent.add(overviewExtras, BorderLayout.SOUTH)
        overview.add(overviewContent, BorderLayout.CENTER)
        overview.add(JBLabel("Version ${PluginVersion.current}").apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            font = font.deriveFont(font.size2D - 1f)
        }, BorderLayout.SOUTH)
        pages.add(overview, OVERVIEW)
        pages.add(settings, SETTINGS)
        add(JBScrollPane(object : JPanel(BorderLayout()), Scrollable {
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableTracksViewportWidth(): Boolean = true
            override fun getScrollableTracksViewportHeight(): Boolean = false
            override fun getScrollableUnitIncrement(rect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
            override fun getScrollableBlockIncrement(rect: Rectangle, orientation: Int, direction: Int): Int =
                (rect.height - JBUI.scale(16)).coerceAtLeast(JBUI.scale(16))
        }.apply {
            isOpaque = false
            add(pages, BorderLayout.NORTH)
        }).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && isShowing && !disposed) {
                UsageRefreshCoordinator.refreshAll()
            }
        }
        UsageRefreshCoordinator.addVisibilityListener(visibilityListener)
        setAgentUsageExpanded(agentUsageExpanded)
        updateVisibleAgents()
    }

    private fun updateVisibleAgents() {
        updateOverviewFeatures()
        val choices = AgentsUsageSettings.getInstance().state
        val selection = listOf(choices.showOpenAi && (CodexUsageService.isAvailable() || AcpAgentInstallation.isInstalled("codex-acp")),
            choices.showJetBrainsAi && JetBrainsAiUsageService.isAvailable(),
            choices.showCopilot && (GitHubCopilotUsageService.isAvailable() || AcpAgentInstallation.isInstalled("github-copilot")),
            choices.showClaudeCode && ClaudeCodeUsageService.isInstalled(),
            choices.showCursor && CursorUsageService.isInstalled())
        if (selection == visibleAgentSelection) return
        visibleAgentSelection = selection
        codex?.dispose()
        jetBrainsAi?.dispose()
        copilot?.dispose()
        claudeCode?.dispose()
        cursor?.dispose()
        codex = if (selection[0]) CodexUsagePanel() else null
        jetBrainsAi = if (selection[1]) JetBrainsAiUsagePanel() else null
        copilot = if (selection[2]) GitHubCopilotUsagePanel() else null
        claudeCode = if (selection[3]) ClaudeCodeUsagePanel() else null
        cursor = if (selection[4]) CursorUsagePanel() else null
        sections.removeAll()
        listOfNotNull(
            codex?.let { AgentSection(it, Color(57, 174, 153)) },
            jetBrainsAi?.let { AgentSection(it, Color(157, 119, 220)) },
            copilot?.let { AgentSection(it, Color(82, 151, 230)) },
            claudeCode?.let { AgentSection(it, Color(207, 117, 77)) },
            cursor?.let { AgentSection(it, Color(130, 145, 164)) }
        ).forEachIndexed { index, section ->
            sections.add(section, GridBagConstraints().apply {
                gridx = 0
                gridy = index
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
                insets = Insets(if (index == 0) 0 else JBUI.scale(6), 0, 0, 0)
            })
        }
        if (sections.componentCount == 0) {
            sections.add(JBLabel("No agents selected or available. Choose agents in settings.").apply {
                toolTipText = text
            })
        }
        insights?.refreshView()
        pages.revalidate()
        pages.repaint()
    }

    private fun updateOverviewFeatures() {
        val state = settingsState()
        if (state.showWeeklyInsights && insights == null) insights = WeeklyUsageInsightsPanel()
        if (!state.showWeeklyInsights) { insights?.dispose(); insights = null }
        if (state.showGames && games == null) games = GamesPanel()
        overviewExtras.removeAll()
        insights?.let { overviewExtras.add(it) }
        if (state.showGames) games?.let { overviewExtras.add(it) }
        overviewExtras.revalidate()
        pages.revalidate()
        pages.repaint()
    }

    private fun settingsState() = AgentsUsageSettings.getInstance().state

    private fun setAgentUsageExpanded(value: Boolean) {
        agentUsageExpanded = value
        settingsState().agentUsageExpanded = value
        sections.isVisible = value
        agentUsageToggle.text = if (value) "AGENT USAGE  ▾" else "AGENT USAGE  ▸"
        agentUsageToggle.accessibleContext.accessibleName = if (value) "Collapse agent usage" else "Expand agent usage"
        agentOverview.revalidate()
        pages.revalidate()
        pages.repaint()
    }

    private fun showSettings() {
        settings.resetFromSettings()
        showPage(SETTINGS)
    }

    internal fun showUsageOverview() {
        showPage(OVERVIEW)
    }

    private fun showPage(name: String) {
        pageLayout.show(pages, name)
        pages.revalidate()
        pages.repaint()
    }

    override fun dispose() {
        disposed = true
        toolbar.dispose()
        UsageRefreshCoordinator.removeVisibilityListener(visibilityListener)
        codex?.dispose()
        jetBrainsAi?.dispose()
        copilot?.dispose()
        claudeCode?.dispose()
        cursor?.dispose()
        insights?.dispose()
    }

    private companion object {
        const val OVERVIEW = "overview"
        const val SETTINGS = "settings"
    }
}
