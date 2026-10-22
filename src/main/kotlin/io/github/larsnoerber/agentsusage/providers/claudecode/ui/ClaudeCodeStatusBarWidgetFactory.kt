package io.github.larsnoerber.agentsusage.providers.claudecode.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsage
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsageService
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.*

class ClaudeCodeStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "ClaudeCodeUsageStatusBar"
    override fun getDisplayName(): String = "Claude Usage"
    override fun isAvailable(project: Project): Boolean =
        AgentsUsageSettings.getInstance().state.showClaudeCode && ClaudeCodeUsageService.isInstalled()
    override fun createWidget(project: Project): StatusBarWidget =
        UsageStatusBarWidget(project, getId(), "claude", ClaudeCodeUsageService.getInstance(), ::presentation)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
}

private fun presentation(usage: ClaudeCodeUsage): StatusBarPresentation {
    if (usage.quotas.isEmpty()) {
        val apiKeyLogin = usage.plan == "Anthropic API"
        val localUsage = usage.logsFound && usage.error == null
        val value = when {
            localUsage -> "%,d tokens".format(usage.totalTokens)
            apiKeyLogin -> "API"
            usage.quotaError == null -> "…"
            else -> "—"
        }
        return StatusBarPresentation(listOf(StatusBarPart("Claude | "), StatusBarPart(value, secondaryTextColor())),
            usageTooltip("Claude usage", listOfNotNull(
                "Account: ${formatSubscriptionPlan(usage.plan)}",
                if (apiKeyLogin) "Signed in with an API key; subscription quotas do not apply" else usage.quotaError,
                if (localUsage) "This month: ${usage.totalTokens} local tokens; ${usage.requests} requests"
                else usage.error ?: "No local token records are available",
                "Click to open usage details")),
            dimmed = !localUsage && !apiKeyLogin && usage.quotaError != null)
    }
    val session = usage.quotas.firstOrNull { it.title == "Session" }
    val week = usage.quotas.firstOrNull { it.title == "Weekly" }
    val absent = if (usage.quotaError == null) "…" else "—"
    return StatusBarPresentation(listOf(StatusBarPart("Claude | D="),
        StatusBarPart(session?.let { "${it.percentLeft}%" } ?: absent, usageBarColor(session?.percentLeft)),
        StatusBarPart(" - W="), StatusBarPart(week?.let { "${it.percentLeft}%" } ?: absent, usageBarColor(week?.percentLeft))),
        usageTooltip("Claude usage", listOf("Subscription: ${formatSubscriptionPlan(usage.plan)}",
            "Status shows remaining subscription quota", "Local monthly tokens: ${usage.totalTokens}",
            usage.quotaError ?: "Click to open usage details"), usage.quotas.map {
            TooltipUsageBar(it.title, it.percentLeft, "${it.percentLeft}% remaining", usageBarColor(it.percentLeft))
        }), dimmed = usage.quotaError != null)
}
