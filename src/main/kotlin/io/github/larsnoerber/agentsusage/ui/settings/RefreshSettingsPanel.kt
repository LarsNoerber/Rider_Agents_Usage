package io.github.larsnoerber.agentsusage.ui.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.larsnoerber.agentsusage.application.UsageRefreshCoordinator
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.UsageSurface
import io.github.larsnoerber.agentsusage.ui.components.secondaryTextColor
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import io.github.larsnoerber.agentsusage.ui.components.warningColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.text.ParseException
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/** Edits the shared refresh interval without owning provider subscriptions or timers. */
internal class RefreshSettingsPanel(private val onBack: () -> Unit) : JPanel(BorderLayout()), Disposable {
    private val settingsNotice = JBLabel(" ")
    private val agents = AgentSelectionPanel()
    private val showWeeklyInsights = JBCheckBox("Show Weekly recap")
    private val showGames = JBCheckBox("Show Games")
    private lateinit var intervalSpinner: JSpinner
    private lateinit var presetButtons: List<RefreshChoice>

    init {
        isOpaque = false
        add(createSettings(), BorderLayout.NORTH)
    }

    fun resetFromSettings() {
        agents.reset()
        val settings = AgentsUsageSettings.getInstance()
        showWeeklyInsights.isSelected = settings.state.showWeeklyInsights
        showGames.isSelected = settings.state.showGames
        intervalSpinner.value = settings.refreshIntervalSeconds
        settingsNotice.text = " "
        settingsNotice.foreground = usageBarColor(100)
    }

    override fun dispose() = agents.dispose()

    private fun createSettings(): JPanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
        add(settingsHeader(), GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
        })
        add(agentSettings(), sectionConstraints(1, 8))
        add(overviewSettings(), sectionConstraints(2, 8))
        add(refreshSettings(), sectionConstraints(3, 8))
        add(settingsActions(), sectionConstraints(4, 8))
    }

    private fun agentSettings(): UsageSurface = UsageSurface().apply {
        addRow(JBLabel("Agents").apply { font = font.deriveFont(Font.BOLD, 14f) })
        addRow(JBLabel("Choose which providers appear in the overview and status bar.").apply {
            foreground = secondaryTextColor()
        }, top = 4)
        addRow(agents, top = 8)
        addRow(JBLabel("<html>Install missing ACP packages from Rider's ACP Registry, then sign in to the agent.</html>").apply {
            foreground = secondaryTextColor()
        }, top = 4)
    }

    private fun overviewSettings(): UsageSurface = UsageSurface().apply {
        addRow(JBLabel("Overview sections").apply { font = font.deriveFont(Font.BOLD, 14f) })
        addRow(JBLabel("Choose which extra sections appear in the overview.").apply {
            foreground = secondaryTextColor()
        }, top = 4)
        addRow(showWeeklyInsights, top = 8)
        addRow(showGames, top = 2)
    }

    private fun refreshSettings(): UsageSurface = UsageSurface().apply {
        addRow(JBLabel("Refresh interval").apply { font = font.deriveFont(Font.BOLD, 14f) })
        addRow(JBLabel("Choose how often AgentMeter refreshes all selected providers.").apply {
            foreground = secondaryTextColor()
        }, top = 4)
        presetButtons = listOf(
            presetButton("30 sec", "Frequent updates", 30),
            presetButton("1 min", "Recommended", 60),
            presetButton("5 min", "Reduce background activity", 300)
        )
        addRow(JPanel(GridLayout(0, 1, 0, 4)).apply {
            isOpaque = false
            presetButtons.forEach(::add)
        }, top = 8)

        val currentSeconds = AgentsUsageSettings.getInstance().refreshIntervalSeconds
        intervalSpinner = JSpinner(SpinnerNumberModel(currentSeconds, 10, 3600, 10)).apply {
            preferredSize = Dimension(78, preferredSize.height)
            addChangeListener { selectPresetIfMatched() }
        }
        addRow(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(JBLabel("Custom"))
            add(intervalSpinner)
            add(JBLabel("sec"))
        }, top = 8)
        selectPresetIfMatched()
    }

    private fun settingsActions(): UsageSurface = UsageSurface().apply {
        addRow(JBLabel("Save and links").apply { font = font.deriveFont(Font.BOLD, 14f) })
        addRow(JButton("Apply settings").apply {
            border = JBUI.Borders.empty(7, 10)
            background = JBColor(Color(59, 112, 209), Color(59, 112, 209))
            foreground = Color.WHITE
            addActionListener { saveInterval() }
        }, top = 8)
        addRow(settingsNotice.apply {
            foreground = usageBarColor(100)
        }, top = 4)
        addRow(JButton("GitHub repository").apply {
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            horizontalAlignment = JButton.LEFT
            foreground = JBColor.BLUE
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { BrowserUtil.browse(REPOSITORY_URL) }
        }, top = 4)
    }

    private fun sectionConstraints(row: Int, top: Int) = GridBagConstraints().apply {
        gridx = 0
        gridy = row
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.NORTHWEST
        insets = Insets(JBUI.scale(top), 0, 0, 0)
    }

    private fun settingsHeader(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        isOpaque = false
        add(JButton("‹").apply {
            font = font.deriveFont(16f)
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            margin = Insets(0, 0, 0, 0)
            val buttonSize = JBUI.size(24, 28)
            minimumSize = buttonSize
            preferredSize = buttonSize
            maximumSize = buttonSize
            border = JBUI.Borders.empty(2)
            foreground = secondaryTextColor()
            toolTipText = "Back to usage overview"
            addActionListener { onBack() }
        })
        add(JButton("AgentMeter").apply {
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            margin = Insets(0, 0, 0, 0)
            val buttonSize = JBUI.size(100, 28)
            minimumSize = buttonSize
            preferredSize = buttonSize
            maximumSize = buttonSize
            border = JBUI.Borders.empty(2)
            foreground = secondaryTextColor()
            toolTipText = "Back to usage overview"
            addActionListener { onBack() }
        })
        add(JBLabel("/").apply {
            foreground = secondaryTextColor()
            border = JBUI.Borders.empty(0, 3)
        })
        add(JBLabel("Agent settings").apply { font = font.deriveFont(Font.BOLD, 15f) })
    }

    private fun presetButton(title: String, note: String, seconds: Int): RefreshChoice =
        RefreshChoice(title, note, seconds).apply {
            addActionListener { intervalSpinner.value = seconds }
        }

    private fun selectPresetIfMatched() {
        val value = (intervalSpinner.value as Number).toInt()
        presetButtons.forEach { it.setSelectedStyle(it.seconds == value) }
    }

    private fun saveInterval() {
        try {
            intervalSpinner.commitEdit()
        } catch (_: ParseException) {
            settingsNotice.foreground = warningColor()
            settingsNotice.text = "Enter an interval between 10 and 3600 seconds"
            return
        }
        settingsNotice.foreground = usageBarColor(100)
        val seconds = (intervalSpinner.value as Number).toInt().coerceIn(10, 3600)
        agents.applySelection()
        UsageRefreshCoordinator.changeOverviewFeatures(showWeeklyInsights.isSelected, showGames.isSelected)
        UsageRefreshCoordinator.changeRefreshInterval(seconds)
        settingsNotice.text = "Settings saved · refresh every $seconds seconds"
    }

    private companion object {
        const val REPOSITORY_URL = "https://github.com/larsnoerber/Rider_Agents_Usage"
    }

}
