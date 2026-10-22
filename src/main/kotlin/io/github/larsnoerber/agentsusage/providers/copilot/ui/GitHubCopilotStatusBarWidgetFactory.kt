package io.github.larsnoerber.agentsusage.providers.copilot.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.providers.copilot.CopilotQuota
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsage
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsageService
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPart
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPresentation
import io.github.larsnoerber.agentsusage.ui.components.TooltipUsageBar
import io.github.larsnoerber.agentsusage.ui.components.UsageStatusBarWidget
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import io.github.larsnoerber.agentsusage.ui.components.usageTooltip
import java.awt.Color

class GitHubCopilotStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "GitHubCopilotUsageStatusBar"
    override fun getDisplayName(): String = "GitHub Copilot Usage"
    override fun isAvailable(project: Project): Boolean = AgentsUsageSettings.getInstance().state.showCopilot &&
        GitHubCopilotUsageService.isAvailable()
    override fun createWidget(project: Project): StatusBarWidget =
        UsageStatusBarWidget(project, getId(), "copilot", GitHubCopilotUsageService.getInstance(), ::copilotPresentation)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
}

private fun copilotPresentation(usage: GitHubCopilotUsage): StatusBarPresentation {
    val quota = usage.primary
    val text = when {
        quota?.unlimited == true -> "∞"
        quota?.percentUsed != null -> "${quota.percentUsed}%"
        usage.error != null || quota != null -> "—"
        else -> "…"
    }
    val lines = buildList {
        add("Subscription: ${formatSubscriptionPlan(usage.plan)}")
        listOfNotNull(usage.primary, usage.chat, usage.completions).distinctBy { it.title }.forEach {
            if (it.unlimited) {
                add("${it.title}: unlimited quota")
            } else {
                add("${it.title}: ${it.displayUsed} used")
                add("${it.title}: ${it.displayRemaining} remaining")
            }
        }
        usage.resetsAt?.let { add("Resets: ${formatResetTime(it)}") }
        usage.reportedAt?.let { add("Last reported: ${formatResetTime(it)}") }
        add(usage.error ?: "Click to open usage details")
    }
    val bars = listOfNotNull(usage.primary, usage.chat, usage.completions)
        .distinctBy { it.title }
        .mapNotNull { item ->
            item.percentUsed?.let { percent ->
                TooltipUsageBar(item.title, percent, "$percent% used", copilotQuotaColor(item))
            }
        }
    return StatusBarPresentation(
        listOf(StatusBarPart("Copilot | "), StatusBarPart(text, copilotQuotaColor(quota))),
        usageTooltip("GitHub Copilot usage", lines, bars),
        dimmed = usage.error != null
    )
}

internal fun copilotQuotaColor(quota: CopilotQuota?): Color =
    usageBarColor(if (quota?.unlimited == true) 100 else quota?.percentUsed?.let { 100 - it })
