package io.github.larsnoerber.agentsusage.providers.jetbrainsai.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.core.format.formatCompactTime
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiQuota
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsage
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsageService
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.formatAiCredits
import io.github.larsnoerber.agentsusage.ui.components.ProviderHeader
import io.github.larsnoerber.agentsusage.ui.components.UsageCard
import io.github.larsnoerber.agentsusage.ui.components.UsageSummary
import io.github.larsnoerber.agentsusage.ui.components.ProviderUsageDetails
import io.github.larsnoerber.agentsusage.ui.components.UsageDetailMetric
import io.github.larsnoerber.agentsusage.ui.components.compactUsageMessage
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import io.github.larsnoerber.agentsusage.ui.components.ProviderRefreshStatus
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import com.intellij.util.ui.JBUI
import javax.swing.JPanel

internal class JetBrainsAiUsagePanel : JPanel(BorderLayout(0, 4)), Disposable {
    private val header = ProviderHeader("JetBrains AI")
    private val subscription = AiCreditCard("Subscription")
    private val topUp = AiCreditCard("Top-up")
    private val status = JBLabel().apply { foreground = JBColor.GRAY }
    private val details = ProviderUsageDetails("jetbrainsai")
    private val summary = UsageSummary(details, header)
    private val refreshStatus = ProviderRefreshStatus("jetbrainsai")
    private val service = JetBrainsAiUsageService.getInstance()
    private val listener: (JetBrainsAiUsage) -> Unit = ::render

    init {
        isOpaque = false
        add(header, BorderLayout.NORTH)
        add(JPanel(GridBagLayout()).apply {
            isOpaque = false
            listOf(subscription, topUp).forEachIndexed { index, card ->
                add(card, GridBagConstraints().apply {
                    gridx = 0
                    gridy = index
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(if (index == 0) 0 else JBUI.scale(4), 0, 0, 0)
                })
            }
        }, BorderLayout.CENTER)
        add(JPanel(BorderLayout(0, 3)).apply {
            isOpaque = false
            add(summary, BorderLayout.NORTH)
            add(JPanel(BorderLayout(0, 3)).apply { isOpaque = false; add(status, BorderLayout.NORTH); add(refreshStatus, BorderLayout.SOUTH) }, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
        service.addListener(listener)
        render(service.current)
    }

    private fun render(usage: JetBrainsAiUsage) {
        val hasDetails = usage.subscription != null || usage.topUp != null
        details.render(buildList {
            (usage.subscription ?: usage.quota)?.percentLeft?.let {
                add(UsageDetailMetric("subscription", "Subscription", (100 - it).toDouble(), usage.resetsAt))
            }
            usage.topUp?.percentLeft?.let { add(UsageDetailMetric("topup", "Top-up", (100 - it).toDouble())) }
        })
        subscription.render(
            usage.subscription ?: if (!hasDetails) usage.quota else null,
            if (hasDetails) "Subscription" else "Total credits"
        )
        topUp.render(usage.topUp, "Top-up")
        topUp.isVisible = usage.topUp != null && !usage.unlimited
        subscription.isVisible = !usage.unlimited && (usage.subscription != null || !hasDetails)
        val details = buildList {
            usage.planUnavailableReason?.let { add("Subscription lookup: $it") }
            when {
                usage.unlimited -> add("Unlimited credits")
                usage.quota != null -> add("${formatAiCredits(usage.quota.remaining)} / ${formatAiCredits(usage.quota.total)} credits remaining")
                usage.error != null -> add("Credit balance unavailable")
                else -> add("Loading JetBrains AI credits…")
            }
            usage.error?.let { add(it) }
            usage.resetsAt?.let { add("Subscription resets ${formatResetTime(it)}") }
        }
        header.render(usage.plan, details)
        summary.render("JetBrains AI details", listOf(
            "Credits left" to when {
                usage.unlimited -> "Unlimited"
                usage.quota != null -> formatAiCredits(usage.quota.remaining)
                else -> "—"
            },
            "Used" to (usage.quota?.let { formatAiCredits(it.total.subtract(it.remaining).max(java.math.BigDecimal.ZERO)) } ?: "—"),
            "Total" to (usage.quota?.let { formatAiCredits(it.total) } ?: "—"),
            "Reset" to (usage.resetsAt?.let(::formatCompactTime) ?: "—")
        ), buildList {
            add("Subscription" to formatSubscriptionPlan(usage.plan))
            usage.subscription?.let { add("Subscription left" to "${formatAiCredits(it.remaining)} / ${formatAiCredits(it.total)} credits") }
            usage.topUp?.let { add("Top-up left" to "${formatAiCredits(it.remaining)} / ${formatAiCredits(it.total)} credits") }
            usage.quota?.percentLeft?.let { add("Available" to "$it%") }
            usage.resetsAt?.let { add("Resets at" to formatResetTime(it)) }
            add("Refresh" to "Every ${AgentsUsageSettings.getInstance().refreshIntervalSeconds} seconds")
            add("Status" to if (usage.error != null) "Credit balance unavailable" else if (usage.quota != null || usage.unlimited) "Balance available" else "Waiting for balance")
            usage.error?.let { add("Details" to it) }
            usage.planUnavailableReason?.let { add("Subscription lookup" to it) }
        })
        status.text = usage.error?.let(::compactUsageMessage).orEmpty()
        status.isVisible = usage.error != null
        status.toolTipText = header.toolTipText
        revalidate()
        repaint()
    }

    override fun dispose() { service.removeListener(listener); summary.dispose(); refreshStatus.dispose() }
}

private class AiCreditCard(title: String) : UsageCard(title) {
    fun render(quota: JetBrainsAiQuota?, title: String) {
        heading.text = title
        remaining.text = quota?.let { "${formatAiCredits(it.remaining)} / ${formatAiCredits(it.total)}" } ?: "—"
        detail.text = quota?.let { "of ${formatAiCredits(it.total)} credits" } ?: "Credits unavailable"
        updateProgress(quota?.percentLeft, usageBarColor(quota?.percentLeft))
    }
}
