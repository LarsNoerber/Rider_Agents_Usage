package io.github.larsnoerber.agentsusage.providers.codex.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsage
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsageService
import io.github.larsnoerber.agentsusage.core.format.formatCompactTime
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.ProviderHeader
import io.github.larsnoerber.agentsusage.ui.components.UsageSummary
import io.github.larsnoerber.agentsusage.ui.components.ProviderUsageDetails
import io.github.larsnoerber.agentsusage.ui.components.UsageDetailMetric
import io.github.larsnoerber.agentsusage.ui.components.compactUsageMessage
import io.github.larsnoerber.agentsusage.ui.components.usageTooltip
import io.github.larsnoerber.agentsusage.ui.components.warningColor
import io.github.larsnoerber.agentsusage.ui.components.ProviderRefreshStatus
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

internal class CodexUsagePanel(actions: JComponent? = null) : JPanel(BorderLayout(0, 4)), Disposable {
    private val header = ProviderHeader("OpenAI", actions)
    private val fiveHour = CodexUsageCard("5 hours")
    private val weekly = CodexUsageCard("This week")
    private val stateDot = JBLabel("●")
    private val stateText = JBLabel()
    private val notice = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    private val details = ProviderUsageDetails("codex")
    private val summary = UsageSummary(details, header)
    private val refreshStatus = ProviderRefreshStatus("codex")
    private val service = CodexUsageService.getInstance()
    private var disposed = false
    private val listener: (CodexUsage) -> Unit = ::render
    private val timer = Timer(1_000) {
        fiveHour.updateCountdown()
        weekly.updateCountdown()
    }

    init {
        isOpaque = false
        add(header, BorderLayout.NORTH)
        add(JPanel(GridLayout(0, 1, 0, 4)).apply {
            isOpaque = false
            add(fiveHour)
            add(weekly)
        }, BorderLayout.CENTER)
        notice.apply {
            isOpaque = false
            stateDot.font = stateDot.font.deriveFont(10f)
            stateDot.foreground = warningColor()
            stateText.foreground = warningColor()
            add(stateDot)
            add(stateText)
        }
        add(JPanel(BorderLayout(0, 3)).apply {
            isOpaque = false
            add(summary, BorderLayout.NORTH)
            add(JPanel(BorderLayout(0, 3)).apply { isOpaque = false; add(notice, BorderLayout.NORTH); add(refreshStatus, BorderLayout.SOUTH) }, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
        service.addListener(listener)
        render(service.current)
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                if (!disposed && isShowing) timer.start() else timer.stop()
            }
        }
    }

    private fun render(usage: CodexUsage) {
        fiveHour.render(usage.fiveHourLeft, usage.fiveHourReset, usage.fiveHourResetsAt)
        weekly.render(usage.weeklyLeft, usage.weeklyReset, usage.weeklyResetsAt)
        details.render(listOfNotNull(
            usage.fiveHourLeft?.let { UsageDetailMetric("session", "Session", (100 - it).toDouble(), usage.fiveHourResetsAt, 5 * 3600L) },
            usage.weeklyLeft?.let { UsageDetailMetric("weekly", "Weekly", (100 - it).toDouble(), usage.weeklyResetsAt, 7 * 86400L) }
        ))
        val info = usage.error ?: "Refreshes every ${AgentsUsageSettings.getInstance().refreshIntervalSeconds} sec · Credits ${usage.credits ?: "—"}"
        header.render(usage.plan, listOf(info))
        val hasUsage = usage.fiveHourLeft != null || usage.weeklyLeft != null
        summary.render("OpenAI details", listOf(
            "Credits" to (usage.credits?.toString() ?: "—"),
            "Updated" to if (hasUsage) formatCompactTime(usage.updatedAt / 1_000) else "—",
            "5h reset" to (usage.fiveHourResetsAt?.let(::formatCompactTime) ?: "—"),
            "Week reset" to (usage.weeklyResetsAt?.let(::formatCompactTime) ?: "—")
        ), buildList {
            add("Subscription" to formatSubscriptionPlan(usage.plan))
            add("5h left" to (usage.fiveHourLeft?.let { "$it%" } ?: "—"))
            add("Week left" to (usage.weeklyLeft?.let { "$it%" } ?: "—"))
            usage.fiveHourResetsAt?.let { add("5h resets at" to formatResetTime(it)) }
            usage.weeklyResetsAt?.let { add("Week resets at" to formatResetTime(it)) }
            add("Refresh" to "Every ${AgentsUsageSettings.getInstance().refreshIntervalSeconds} seconds")
            add("Status" to if (usage.error != null) "Last refresh failed; values may be stale" else if (hasUsage) "Usage available" else "Waiting for usage")
            usage.error?.let { add("Details" to it) }
        })
        notice.isVisible = usage.error != null
        stateText.text = usage.error?.let(::compactUsageMessage).orEmpty()
        val tooltip = usageTooltip("OpenAI Codex", listOf(info))
        stateText.toolTipText = tooltip
        stateDot.toolTipText = tooltip
        revalidate()
        repaint()
    }

    override fun dispose() {
        disposed = true
        timer.stop()
        service.removeListener(listener)
        summary.dispose(); refreshStatus.dispose()
    }
}
