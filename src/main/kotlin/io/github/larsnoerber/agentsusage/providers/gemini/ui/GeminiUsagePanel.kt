package io.github.larsnoerber.agentsusage.providers.gemini.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.providers.gemini.GeminiModelQuota
import io.github.larsnoerber.agentsusage.providers.gemini.GeminiUsage
import io.github.larsnoerber.agentsusage.providers.gemini.GeminiUsageService
import io.github.larsnoerber.agentsusage.ui.components.ProviderHeader
import io.github.larsnoerber.agentsusage.ui.components.ProviderRefreshStatus
import io.github.larsnoerber.agentsusage.ui.components.UsageCard
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JPanel

internal class GeminiUsagePanel : JPanel(BorderLayout(0, 4)), Disposable {
    private val header = ProviderHeader("Gemini")
    private val cards = JPanel(GridBagLayout()).apply { isOpaque = false }
    private val status = JBLabel().apply { foreground = JBColor.GRAY }
    private val refreshStatus = ProviderRefreshStatus("gemini")
    private val service = GeminiUsageService.getInstance()
    private val listener: (GeminiUsage) -> Unit = ::render

    init {
        isOpaque = false
        add(header, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
        add(JPanel(BorderLayout(0, 3)).apply {
            isOpaque = false
            add(status, BorderLayout.NORTH)
            add(refreshStatus, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
        service.addListener(listener)
        render(service.current)
    }

    private fun render(usage: GeminiUsage) {
        header.render(usage.plan, listOf(usage.error ?: if (usage.models.isEmpty()) "Waiting for Gemini quota" else "Gemini plugin quota"))
        cards.removeAll()
        usage.models.forEachIndexed { index, quota ->
            cards.add(GeminiQuotaCard(quota), GridBagConstraints().apply {
                gridx = 0
                gridy = index
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(if (index == 0) 0 else JBUI.scale(4), 0, 0, 0)
            })
        }
        status.text = usage.error ?: if (usage.models.isEmpty()) "Waiting for Gemini quota…" else ""
        status.isVisible = status.text.isNotEmpty()
        status.toolTipText = header.toolTipText
        revalidate()
        repaint()
    }

    override fun dispose() { service.removeListener(listener); refreshStatus.dispose() }
}

private class GeminiQuotaCard(quota: GeminiModelQuota) : UsageCard("Model: ${quota.modelId}") {
    init {
        // Give model variants the full row width rather than the shared 100-pixel title column.
        heading.text = quota.modelId.removePrefix("models/").replace('-', ' ').replaceFirstChar { it.uppercaseChar() }
        heading.preferredSize = null
        add(heading, BorderLayout.NORTH)
        remaining.text = "${quota.percentLeft}%"
        detail.text = quota.resetsAt?.let { "Resets ${formatResetTime(it)}" } ?: "Reset unavailable"
        updateProgress(quota.percentLeft, usageBarColor(quota.percentLeft))
        updateDetailsTooltip("Model ID: ${quota.modelId}")
    }
}
