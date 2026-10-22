package io.github.larsnoerber.agentsusage.providers.jetbrainsai.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsage
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsageService
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.formatAiCredits
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPart
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPresentation
import io.github.larsnoerber.agentsusage.ui.components.TooltipUsageBar
import io.github.larsnoerber.agentsusage.ui.components.UsageStatusBarWidget
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import io.github.larsnoerber.agentsusage.ui.components.usageTooltip
import java.awt.Color

class JetBrainsAiStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "JetBrainsAiCreditsStatusBar"
    override fun getDisplayName(): String = "JetBrains AI Usage"
    override fun isAvailable(project: Project): Boolean =
        AgentsUsageSettings.getInstance().state.showJetBrainsAi && JetBrainsAiUsageService.isAvailable()
    override fun createWidget(project: Project): StatusBarWidget =
        UsageStatusBarWidget(project, getId(), "jetbrainsai", JetBrainsAiUsageService.getInstance(), ::jetBrainsPresentation)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
}

private fun jetBrainsPresentation(usage: JetBrainsAiUsage): StatusBarPresentation {
    val percent = usage.quota?.percentLeft
    val text = when {
        usage.unlimited -> "∞"
        percent != null -> "$percent%"
        usage.error != null || usage.quota != null -> "—"
        else -> "…"
    }
    val lines = buildList {
        add("Subscription: ${formatSubscriptionPlan(usage.plan)}")
        usage.planUnavailableReason?.let { add("Subscription lookup: $it") }
        usage.quota?.let { add("Remaining: ${formatAiCredits(it.remaining)} / ${formatAiCredits(it.total)} credits") }
        usage.subscription?.let { add("Subscription: ${formatAiCredits(it.remaining)} / ${formatAiCredits(it.total)} credits") }
        usage.topUp?.let { add("Top-up: ${formatAiCredits(it.remaining)} / ${formatAiCredits(it.total)} credits") }
        usage.resetsAt?.let { add("Subscription resets: ${formatResetTime(it)}") }
        if (usage.unlimited) add("Unlimited quota")
        add(usage.error ?: "Click to open usage details")
    }
    val bars = listOfNotNull(usage.quota?.percentLeft?.let {
        TooltipUsageBar("Credits", it, "$it% remaining", aiCreditColor(usage))
    })
    return StatusBarPresentation(
        listOf(StatusBarPart("JetBrainAi | "), StatusBarPart(text, aiCreditColor(usage))),
        usageTooltip("JetBrains AI credits", lines, bars),
        dimmed = usage.error != null
    )
}

internal fun aiCreditColor(usage: JetBrainsAiUsage): Color = usageBarColor(
    if (usage.error != null) null else if (usage.unlimited) 100 else usage.quota?.percentLeft
)
