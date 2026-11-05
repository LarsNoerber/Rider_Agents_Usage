package io.github.larsnoerber.agentsusage.providers.gemini.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.core.format.formatSubscriptionPlan
import io.github.larsnoerber.agentsusage.providers.gemini.GeminiUsage
import io.github.larsnoerber.agentsusage.providers.gemini.GeminiUsageService
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPart
import io.github.larsnoerber.agentsusage.ui.components.StatusBarPresentation
import io.github.larsnoerber.agentsusage.ui.components.TooltipUsageBar
import io.github.larsnoerber.agentsusage.ui.components.UsageStatusBarWidget
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import io.github.larsnoerber.agentsusage.ui.components.usageTooltip

class GeminiStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "GeminiUsageStatusBar"
    override fun getDisplayName(): String = "Gemini Usage"
    override fun isAvailable(project: Project): Boolean =
        AgentsUsageSettings.getInstance().state.let {
            it.showGemini && (it.geminiStatusAllModels || it.geminiStatusModels.isNotEmpty()) && GeminiUsageService.isAvailable()
        }
    override fun createWidget(project: Project): StatusBarWidget =
        UsageStatusBarWidget(project, getId(), "gemini", GeminiUsageService.getInstance(), ::geminiPresentation)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
}

private fun geminiPresentation(usage: GeminiUsage): StatusBarPresentation {
    val parts = mutableListOf(StatusBarPart("Gemini | "))
    val settings = AgentsUsageSettings.getInstance().state
    val models = usage.models.filter { settings.geminiStatusAllModels || it.modelId in settings.geminiStatusModels }
    if (models.isEmpty()) parts.add(StatusBarPart(if (usage.error != null || usage.models.isNotEmpty()) "—" else "…"))
    models.forEachIndexed { index, quota ->
        if (index > 0) parts.add(StatusBarPart(" - "))
        val name = quota.modelId.removePrefix("models/").removePrefix("gemini-")
        parts.add(StatusBarPart("$name="))
        parts.add(StatusBarPart("${quota.percentLeft}%", usageBarColor(quota.percentLeft)))
    }
    val bars = usage.models.map { quota ->
        TooltipUsageBar(quota.modelId, quota.percentLeft, "${quota.percentLeft}% remaining", usageBarColor(quota.percentLeft))
    }
    val lines = buildList {
        add("Subscription: ${formatSubscriptionPlan(usage.plan)}")
        if (usage.models.isNotEmpty() && models.isEmpty()) add("No quota available for the selected status models")
        usage.models.forEach { quota -> quota.resetsAt?.let { add("${quota.modelId} resets: ${formatResetTime(it)}") } }
        add(usage.error ?: "Click to open usage details")
    }
    return StatusBarPresentation(parts, usageTooltip("Gemini quota", lines, bars), dimmed = usage.error != null)
}
