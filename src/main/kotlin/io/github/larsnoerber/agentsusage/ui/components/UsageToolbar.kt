package io.github.larsnoerber.agentsusage.ui.components

import com.intellij.openapi.Disposable
import io.github.larsnoerber.agentsusage.application.UsageRefreshCoordinator
import io.github.larsnoerber.agentsusage.core.refresh.UsageRefreshStatuses
import io.github.larsnoerber.agentsusage.core.format.formatCompactTime
import javax.swing.JProgressBar
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

internal class UsageToolbar(
    private val onRefresh: () -> Unit,
    private val onSettings: () -> Unit
) : JPanel(FlowLayout(FlowLayout.RIGHT, 3, 0)), Disposable {
    private var disposed = false
    private val refresh = refreshButton()
    private val progress = JProgressBar().apply {
        isIndeterminate = true
        preferredSize = JBUI.size(32, 8)
        isVisible = false
        accessibleContext.accessibleName = "Refreshing selected providers"
    }
    private val status = JBLabel().apply {
        foreground = secondaryTextColor()
        font = font.deriveFont((font.size2D - 2).coerceAtLeast(9f))
    }
    private val listener: () -> Unit = { if (!disposed) renderStatus() }

    init {
        isOpaque = false
        add(status)
        add(progress)
        add(refresh)
        add(toolbarDivider())
        add(settingsButton())
        UsageRefreshStatuses.addListener(listener)
        UsageRefreshCoordinator.addVisibilityListener(listener)
        renderStatus()
    }

    private fun renderStatus() {
        val states = UsageRefreshCoordinator.selectedProviderIds().map { UsageRefreshStatuses.get(it) }
        val loading = states.any { it.loading }
        progress.isVisible = loading
        refresh.isEnabled = !loading
        val lastSuccess = states.map { it.lastSuccess }.filter { it > 0 }.minOrNull()
        status.text = if (loading) "Refreshing..." else lastSuccess?.let { "Last success ${formatCompactTime(it / 1000)}" }.orEmpty()
        status.toolTipText = usageTooltip("Selected providers", UsageRefreshCoordinator.selectedProviderIds().flatMap { id ->
            listOf(id) + refreshStatusLines(UsageRefreshStatuses.get(id))
        })
        revalidate()
        repaint()
    }

    override fun dispose() {
        disposed = true
        UsageRefreshStatuses.removeListener(listener)
        UsageRefreshCoordinator.removeVisibilityListener(listener)
        progress.isIndeterminate = false
    }

    private fun refreshButton(): JButton = JButton("↻").apply {
        font = font.deriveFont(16f)
        accessibleContext.accessibleName = "Refresh all agents"
        toolTipText = "Refresh usage for all available providers immediately"
        isFocusPainted = false
        margin = Insets(0, 0, 0, 0)
        val buttonSize = JBUI.size(24, 24)
        minimumSize = buttonSize
        preferredSize = buttonSize
        maximumSize = buttonSize
        background = JBColor(Color(244, 246, 250), Color(54, 57, 64))
        foreground = JBColor(Color(70, 73, 80), Color(222, 225, 230))
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(210, 214, 222), Color(73, 77, 86))),
            JBUI.Borders.empty(2)
        )
        addActionListener { onRefresh() }
    }

    private fun toolbarDivider(): JBLabel = JBLabel("│").apply {
        foreground = JBColor(Color(190, 194, 201), Color(92, 96, 106))
        border = JBUI.Borders.empty(0, 2)
    }

    private fun settingsButton(): JButton = JButton("⚙").apply {
        font = font.deriveFont(15f)
        toolTipText = "Settings for all agents"
        isFocusPainted = false
        isContentAreaFilled = false
        isBorderPainted = false
        margin = Insets(0, 0, 0, 0)
        val buttonSize = JBUI.size(24, 24)
        minimumSize = buttonSize
        preferredSize = buttonSize
        maximumSize = buttonSize
        border = JBUI.Borders.empty(2)
        foreground = secondaryTextColor()
        addActionListener { onSettings() }
    }

}
