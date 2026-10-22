package io.github.larsnoerber.agentsusage.providers.codex.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsage
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsageService
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPart
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPresentation
import io.github.larsnoerber.agentsusage.ui.components.TooltipUsageBar
import io.github.larsnoerber.agentsusage.ui.components.UsageStatusBarWidget
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import io.github.larsnoerber.agentsusage.ui.components.usageTooltip

class CodexUsageStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "CodexUsageStatusBar"
    override fun getDisplayName(): String = "OpenAI Usage"
    override fun isAvailable(project: Project): Boolean =
        AgentsUsageSettings.getInstance().state.showOpenAi && CodexUsageService.isAvailable()
    override fun createWidget(project: Project): StatusBarWidget =
        UsageStatusBarWidget(project, getId(), "codex", CodexUsageService.getInstance(), ::codexPresentation)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
}

private fun codexPresentation(usage: CodexUsage): StatusBarPresentation {
    val five = usage.fiveHourLeft
    val week = usage.weeklyLeft
    val unavailable = if (usage.error == null) "…" else "—"
    val parts = listOf(
        StatusBarPart("OpenAi | D="),
        StatusBarPart(five?.let { "$it%" } ?: unavailable, usageBarColor(five)),
        StatusBarPart(" - W="),
        StatusBarPart(week?.let { "$it%" } ?: unavailable, usageBarColor(week))
    )
    return StatusBarPresentation(
        parts = parts,
        dimmed = usage.error != null,
        tooltip = usageTooltip("OpenAI Codex usage", listOf(
            "Subscription: ${formatSubscriptionPlan(usage.plan)}",
            "5h: remaining ${five ?: "—"}% · ${usage.fiveHourReset ?: "reset time unknown"}",
            "Weekly: remaining ${week ?: "—"}% · ${usage.weeklyReset ?: "reset time unknown"}",
            "Credits: ${usage.credits ?: "—"}",
            usage.error ?: "Click to open AgentMeter and view details"
        ), listOfNotNull(
            five?.let { TooltipUsageBar("5-hour", it, "$it% remaining", usageBarColor(it)) },
            week?.let { TooltipUsageBar("Weekly", it, "$it% remaining", usageBarColor(it)) }
        ))
    )
}
