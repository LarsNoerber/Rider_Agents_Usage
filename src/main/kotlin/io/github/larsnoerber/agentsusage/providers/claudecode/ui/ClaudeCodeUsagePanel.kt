package io.github.larsnoerber.agentsusage.providers.claudecode.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsage
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsageService
import io.github.larsnoerber.agentsusage.ui.components.ProviderHeader
import io.github.larsnoerber.agentsusage.ui.components.UsageSummary
import io.github.larsnoerber.agentsusage.ui.components.ProviderUsageDetails
import io.github.larsnoerber.agentsusage.ui.components.UsageDetailMetric
import io.github.larsnoerber.agentsusage.ui.components.QuotaUsageCard
import io.github.larsnoerber.agentsusage.ui.components.compactUsageMessage
import io.github.larsnoerber.agentsusage.ui.components.usageTooltip
import java.awt.GridLayout
import io.github.larsnoerber.agentsusage.ui.components.ProviderRefreshStatus
import java.awt.BorderLayout
import javax.swing.JPanel

internal class ClaudeCodeUsagePanel : JPanel(BorderLayout(0, 4)), Disposable {
    private val header = ProviderHeader("Claude")
    private val details = ProviderUsageDetails("claude")
    private val summary = UsageSummary(details, header)
    private val cards = JPanel(GridLayout(0, 1, 0, 4)).apply { isOpaque = false }
    private val quotaCards = linkedMapOf<String, QuotaUsageCard>()
    private val status = JBLabel()
    private val refreshStatus = ProviderRefreshStatus("claude")
    private val service = ClaudeCodeUsageService.getInstance()
    private val listener: (ClaudeCodeUsage) -> Unit = ::render

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

    private fun render(usage: ClaudeCodeUsage) {
        val tokenStatus = when {
            usage.error != null -> usage.error
            !usage.logsFound -> "No local Claude Code token records found."
            usage.requests == 0L -> "No token usage recorded this month."
            else -> "Token totals from local session logs; not an account quota or billed cost."
        }
        header.render(usage.plan, listOfNotNull("Claude subscription quota and local monthly token logs", usage.quotaError, tokenStatus))
        val titles = usage.quotas.map { it.title }.ifEmpty {
            if (usage.plan == "Anthropic API") emptyList() else listOf("Session", "Weekly")
        }
        if (quotaCards.keys.toList() != titles) {
            cards.removeAll()
            quotaCards.clear()
            titles.forEach { title -> quotaCards[title] = QuotaUsageCard(title, showRemaining = true).also { cards.add(it) } }
        }
        quotaCards.forEach { (title, card) ->
            val quota = usage.quotas.firstOrNull { it.title == title }
            card.render(quota?.percentUsed, quota?.resetsAt)
        }
        details.render(usage.quotas.map { quota ->
            UsageDetailMetric(quota.title.lowercase(), quota.title, quota.percentUsed.toDouble(), quota.resetsAt,
                if (quota.title == "Session") 5 * 3600L else 7 * 86400L)
        })
        summary.render("Claude Code this month", listOf(
            "Tokens" to if (usage.logsFound) usage.totalTokens.formatted() else "—",
            "Requests" to if (usage.logsFound) usage.requests.formatted() else "—",
            "Input" to if (usage.logsFound) usage.inputTokens.formatted() else "—",
            "Output" to if (usage.logsFound) usage.outputTokens.formatted() else "—"
        ), buildList {
            add("Cache read" to if (usage.logsFound) usage.cacheReadTokens.formatted() else "—")
            add("Cache write" to if (usage.logsFound) usage.cacheWriteTokens.formatted() else "—")
            add("Quota source" to if (usage.plan == "Anthropic API") "Not applicable to API key login" else "Claude subscription usage API")
            add("Token period" to "This month · local logs only")
            add("Token status" to tokenStatus)
            usage.quotaError?.let { add("Quota status" to it) }
            usage.modelTokens.toSortedMap().forEach { (model, tokens) -> add("$model tokens" to tokens.formatted()) }
        })
        val message = if (usage.plan == "Anthropic API") {
            if (usage.logsFound) "API key connected; local monthly usage shown."
            else "API key connected; no local token records."
        } else usage.quotaError ?: tokenStatus
        status.text = compactUsageMessage(message)
        status.toolTipText = usageTooltip("Claude usage", listOfNotNull(message, usage.quotaError, tokenStatus))
        revalidate()
        repaint()
    }

    override fun dispose() { service.removeListener(listener); summary.dispose(); refreshStatus.dispose() }

    private fun Long.formatted(): String = "%,d".format(this)
}
