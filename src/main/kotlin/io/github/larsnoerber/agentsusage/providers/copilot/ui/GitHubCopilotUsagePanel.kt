package io.github.larsnoerber.agentsusage.providers.copilot.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.core.format.formatCompactTime
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.providers.copilot.CopilotQuota
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsage
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsageService
import io.github.larsnoerber.agentsusage.ui.components.ProviderHeader
import io.github.larsnoerber.agentsusage.ui.components.UsageCard
import io.github.larsnoerber.agentsusage.ui.components.UsageSummary
import io.github.larsnoerber.agentsusage.ui.components.ProviderUsageDetails
import io.github.larsnoerber.agentsusage.ui.components.UsageDetailMetric
import io.github.larsnoerber.agentsusage.ui.components.compactUsageMessage
import io.github.larsnoerber.agentsusage.ui.components.ProviderRefreshStatus
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JPanel

internal class GitHubCopilotUsagePanel : JPanel(BorderLayout(0, 4)), Disposable {
    private val header = ProviderHeader("GitHub Copilot")
    private val cards = JPanel(GridLayout(0, 1, 0, 4)).apply { isOpaque = false }
    private val usageCards = LinkedHashMap<String, CopilotUsageCard>()
    private val status = JBLabel().apply { foreground = JBColor.GRAY }
    private val details = ProviderUsageDetails("copilot")
    private val summary = UsageSummary(details, header)
    private val refreshStatus = ProviderRefreshStatus("copilot")
    private val service = GitHubCopilotUsageService.getInstance()
    private val listener: (GitHubCopilotUsage) -> Unit = ::render

    init {
        isOpaque = false
        add(header, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
        add(JPanel(BorderLayout(0, 3)).apply {
            isOpaque = false
            add(summary, BorderLayout.NORTH)
            add(JPanel(BorderLayout(0, 3)).apply { isOpaque = false; add(status, BorderLayout.NORTH); add(refreshStatus, BorderLayout.SOUTH) }, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
        service.addListener(listener)
        render(service.current)
    }

    private fun render(usage: GitHubCopilotUsage) {
        val quotas = listOfNotNull(usage.primary, usage.chat, usage.completions).distinctBy { it.title }
        details.render(quotas.mapNotNull { quota ->
            quota.percentUsed?.let { UsageDetailMetric(
                quota.title.lowercase().replace(Regex("[^a-z0-9]"), "-"), quota.title, it.toDouble(), usage.resetsAt) }
        })
        val titles = quotas.map { it.title }.ifEmpty { listOf("Usage") }
        // Available categories depend on the Copilot plan and billing model.
        if (usageCards.keys.toList() != titles) {
            cards.removeAll()
            usageCards.clear()
            titles.forEach { title ->
                usageCards[title] = CopilotUsageCard(title).also { cards.add(it) }
            }
        }
        usageCards.forEach { (title, card) -> card.render(quotas.firstOrNull { it.title == title }) }
        header.render(usage.plan, buildList {
            usage.error?.let { add(it) }
            usage.resetsAt?.let { add("Resets ${formatResetTime(it)}") }
            usage.reportedAt?.let { add("Last reported ${formatResetTime(it)}") }
        })
        val quota = usage.primary
        summary.render("Copilot details", listOf(
            "Used" to (quota?.displayUsed ?: "—"),
            "Available" to (quota?.displayRemaining ?: "—"),
            "Reset" to (usage.resetsAt?.let(::formatCompactTime) ?: "—"),
            "Reported" to (usage.reportedAt?.let(::formatCompactTime) ?: "—")
        ), buildList {
            add("Subscription" to formatSubscriptionPlan(usage.plan))
            quotas.forEach {
                add("${it.title} used" to it.displayUsed)
                add("${it.title} left" to it.displayRemaining)
            }
            usage.resetsAt?.let { add("Resets at" to formatResetTime(it)) }
            usage.reportedAt?.let { add("Last report" to formatResetTime(it)) }
            add("Refresh" to "Every ${AgentsUsageSettings.getInstance().refreshIntervalSeconds} seconds")
            add("Status" to if (usage.error != null) "Usage unavailable" else if (quota != null) "Report available" else "Waiting for report")
            usage.error?.let { add("Details" to it) }
        })
        status.text = usage.error?.let(::compactUsageMessage).orEmpty()
        status.isVisible = usage.error != null
        status.toolTipText = header.toolTipText
        revalidate()
        repaint()
    }

    override fun dispose() { service.removeListener(listener); summary.dispose(); refreshStatus.dispose() }
}

private class CopilotUsageCard(title: String) : UsageCard(title) {
    fun render(quota: CopilotQuota?) {
        remaining.text = when {
            quota?.unlimited == true -> "Unlimited"
            quota?.percentUsed != null -> "${quota.percentUsed}%"
            else -> "—"
        }
        detail.text = when {
            quota?.unlimited == true -> "No usage limit"
            quota?.used != null && quota.total != null -> "${quota.used} / ${quota.total} used"
            quota?.percentUsed != null -> "Used quota: 0% unused, 100% exhausted"
            else -> "Usage unavailable"
        }
        updateProgress(if (quota?.unlimited == true) 0 else quota?.percentUsed, copilotQuotaColor(quota))
        if (quota != null && !quota.unlimited) {
            updateDetailsTooltip("Remaining: ${quota.displayRemaining}")
        }
    }
}
