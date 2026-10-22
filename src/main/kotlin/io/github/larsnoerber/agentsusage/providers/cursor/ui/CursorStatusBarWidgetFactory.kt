package io.github.larsnoerber.agentsusage.providers.cursor.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsage
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsageService
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.*

class CursorStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "CursorUsageStatusBar"
    override fun getDisplayName(): String = "Cursor Usage"
    override fun isAvailable(project: Project): Boolean =
        AgentsUsageSettings.getInstance().state.showCursor && CursorUsageService.isAvailable()
    override fun createWidget(project: Project): StatusBarWidget =
        UsageStatusBarWidget(project, getId(), "cursor", CursorUsageService.getInstance(), ::presentation)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
}

private fun presentation(usage: CursorUsage): StatusBarPresentation {
    val percent = usage.percentUsed
    val color = usageBarColor(percent?.let { 100 - it })
    return StatusBarPresentation(listOf(StatusBarPart("Cursor | "),
        StatusBarPart(percent?.let { "$it%" } ?: if (usage.error == null) "…" else "—", color)),
        usageTooltip("Cursor usage", listOf("Subscription: ${formatSubscriptionPlan(usage.plan)}",
            "Scope: ${usage.quotaScope}", "0% unused, 100% exhausted", usage.error ?: "Click to open usage details"),
            percent?.let { listOf(TooltipUsageBar(usage.quotaScope, it, "$it% consumed", color)) }.orEmpty()),
        dimmed = usage.error != null)
}
