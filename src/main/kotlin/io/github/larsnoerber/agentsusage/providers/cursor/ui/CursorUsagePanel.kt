package io.github.larsnoerber.agentsusage.providers.cursor.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import io.github.larsnoerber.agentsusage.core.format.formatCompactTime
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsage
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsageService
import io.github.larsnoerber.agentsusage.ui.components.*
import io.github.larsnoerber.agentsusage.ui.components.ProviderRefreshStatus
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JPanel

internal class CursorUsagePanel : JPanel(BorderLayout(0, 4)), Disposable {
    private val header = ProviderHeader("Cursor")
    private val plan = QuotaUsageCard("Included plan")
    private val auto = QuotaUsageCard("Auto / Composer")
    private val api = QuotaUsageCard("API models")
    private val details = ProviderUsageDetails("cursor")
    private val summary = UsageSummary(details, header)
    private val status = JBLabel().apply { foreground = secondaryTextColor() }
    private val refreshStatus = ProviderRefreshStatus("cursor")
    private val service = CursorUsageService.getInstance()
    private val listener: (CursorUsage) -> Unit = ::render

    init {
        isOpaque = false
        add(header, BorderLayout.NORTH)
        add(JPanel(GridLayout(0, 1, 0, 4)).apply { isOpaque = false; add(plan); add(auto); add(api) }, BorderLayout.CENTER)
        add(JPanel(BorderLayout(0, 3)).apply { isOpaque = false; add(summary, BorderLayout.NORTH); add(JPanel(BorderLayout(0, 3)).apply { isOpaque = false; add(status, BorderLayout.NORTH); add(refreshStatus, BorderLayout.SOUTH) }, BorderLayout.SOUTH) }, BorderLayout.SOUTH)
        service.addListener(listener)
        render(service.current)
    }

    private fun render(usage: CursorUsage) {
        header.render(usage.plan, listOfNotNull(usage.error, "${usage.quotaScope}: consumed quota"))
        plan.render(usage.percentUsed, usage.resetsAt)
        auto.render(usage.autoPercentUsed, usage.resetsAt); auto.isVisible = usage.autoPercentUsed != null
        api.render(usage.apiPercentUsed, usage.resetsAt); api.isVisible = usage.apiPercentUsed != null
        summary.render("Cursor details", listOf(
            "Used" to money(usage.usedUsd), "Included" to money(usage.limitUsd),
            "On-demand" to money(usage.onDemandUsd), "Reset" to (usage.resetsAt?.let(::formatCompactTime) ?: "—")
        ), listOfNotNull(
            "Scope" to usage.quotaScope,
            usage.resetsAt?.let { "Resets at" to formatResetTime(it) },
            "Source" to "Cursor account usage summary",
            "Status" to (usage.error ?: "Provider-reported usage")
        ))
        details.render(listOfNotNull(
            usage.percentUsed?.let { UsageDetailMetric("plan", usage.quotaScope, it.toDouble(), usage.resetsAt) },
            usage.autoPercentUsed?.let { UsageDetailMetric("auto", "Auto", it.toDouble(), usage.resetsAt) },
            usage.apiPercentUsed?.let { UsageDetailMetric("api", "API", it.toDouble(), usage.resetsAt) }
        ))
        status.text = usage.error?.let(::compactUsageMessage).orEmpty()
        status.isVisible = usage.error != null
        status.toolTipText = header.toolTipText
        revalidate(); repaint()
    }

    override fun dispose() { service.removeListener(listener); summary.dispose(); refreshStatus.dispose() }
    private fun money(value: Double?): String = value?.let { "$%.2f".format(java.util.Locale.US, it) } ?: "—"
}
