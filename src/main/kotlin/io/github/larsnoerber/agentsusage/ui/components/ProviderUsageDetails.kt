package io.github.larsnoerber.agentsusage.ui.components

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.larsnoerber.agentsusage.core.format.formatCompactTime
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.HierarchyEvent
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.Timer

internal data class UsageDetailMetric(
    val id: String,
    val title: String,
    val value: Double,
    val resetsAt: Long? = null,
    val windowSeconds: Long? = null
)

/** Provider-independent bars/countdowns and a measured usage chart, toggled through the provider badge. */
internal class ProviderUsageDetails(private val provider: String, private val percent: Boolean = true) :
    JPanel(BorderLayout(0, JBUI.scale(6))), Disposable {
    private val rows = JPanel(GridBagLayout()).apply { isOpaque = false }
    private val legend = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0)).apply { isOpaque = false }
    private val chart = UsageHistoryChart(provider, percent)
    private val note = JBLabel(if (percent) "Observed quota consumption · last 24 hours" else "Observed local token totals · last 24 hours").apply {
        foreground = secondaryTextColor()
        font = font.deriveFont((font.size2D - 2).coerceAtLeast(9f))
    }
    private var metrics: List<UsageDetailMetric> = emptyList()
    private var disposed = false
    private val timer = Timer(30_000) { if (!disposed) { drawRows(); chart.repaint() } }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(8, 0, 4, 0)
        add(rows, BorderLayout.NORTH)
        add(JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(legend, BorderLayout.CENTER)
                add(JComboBox(arrayOf("1 hour", "6 hours", "24 hours")).apply {
                    selectedIndex = 1
                    addActionListener { chart.hours = listOf(1, 6, 24)[selectedIndex]; chart.repaint() }
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(chart, BorderLayout.CENTER)
            add(note, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                if (isShowing && !disposed) timer.start() else timer.stop()
            }
        }
    }

    fun render(values: List<UsageDetailMetric>) {
        if (disposed) return
        metrics = values.filter { it.value.isFinite() && it.value >= 0 }.take(4)
        chart.metrics = metrics
        legend.removeAll()
        metrics.forEachIndexed { index, metric ->
            legend.add(JBLabel("● ${metric.title}").apply {
                foreground = detailSeriesColor(index)
                font = font.deriveFont((font.size2D - 1).coerceAtLeast(10f))
            })
        }
        drawRows()
        revalidate()
        repaint()
    }

    private fun drawRows() {
        rows.removeAll()
        if (metrics.isEmpty()) rows.add(JBLabel("No current usage measurements available.").apply { foreground = secondaryTextColor() })
        metrics.forEachIndexed { index, metric ->
            val reset = metric.resetsAt
            val countdown = reset?.let { resetCountdown(it) } ?: "—"
            val value = if (percent) "${metric.value.toInt()}%" else "%,.0f".format(metric.value)
            listOf(
                JBLabel(metric.title),
                DetailMetricBar(metric.value, percent, detailSeriesColor(index)),
                JBLabel(value).apply { foreground = if (percent) usageBarColor(100 - metric.value.toInt()) else detailSeriesColor(index) },
                ResetRing(reset, metric.windowSeconds, detailSeriesColor(index)),
                JBLabel(countdown).apply { foreground = secondaryTextColor() },
                JBLabel(reset?.let(::formatCompactTime) ?: "—").apply { foreground = secondaryTextColor() }
            ).forEachIndexed { column, component ->
                component.toolTipText = usageTooltip(metric.title, listOf(
                    "$value ${if (percent) "consumed" else "tokens"}", reset?.let { "Resets: ${formatResetTime(it)}" } ?: "Reset not reported"))
                rows.add(component, GridBagConstraints().apply {
                    gridx = column; gridy = index
                    weightx = if (column == 1) 1.0 else 0.0
                    fill = if (column == 1) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
                    anchor = GridBagConstraints.WEST
                    insets = Insets(3, 0, 3, if (column == 5) 0 else JBUI.scale(6))
                })
            }
        }
    }

    override fun dispose() { disposed = true; timer.stop() }
}

internal fun detailSeriesColor(index: Int): Color = listOf(
    Color(163, 107, 250), Color(73, 143, 255), Color(57, 174, 153), Color(230, 155, 65)
)[index % 4]

private fun resetCountdown(reset: Long): String {
    val minutes = ((reset - System.currentTimeMillis() / 1000).coerceAtLeast(0) + 59) / 60
    return when {
        minutes == 0L -> "Reset due"
        minutes >= 1440 -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

private class DetailMetricBar(private val value: Double, private val percent: Boolean, private val color: Color) : JPanel() {
    init { isOpaque = false; preferredSize = JBUI.size(70, 10); minimumSize = JBUI.size(24, 10) }
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(128, 128, 128, 55)
            g.fillRoundRect(0, 2, width, (height - 4).coerceAtLeast(2), 6, 6)
            if (percent) {
                g.color = color
                g.fillRoundRect(0, 2, (width * value.coerceIn(0.0, 100.0) / 100).toInt(), (height - 4).coerceAtLeast(2), 6, 6)
            }
        } finally { g.dispose() }
    }
}

private class ResetRing(private val reset: Long?, private val window: Long?, private val color: Color) : JPanel() {
    init { isOpaque = false; preferredSize = JBUI.size(17, 17) }
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.stroke = BasicStroke(JBUI.scale(2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = Color(128, 128, 128, 70)
            g.drawOval(3, 3, width - 6, height - 6)
            if (reset != null && window != null && window > 0) {
                val fraction = ((reset - System.currentTimeMillis() / 1000).toDouble() / window).coerceIn(0.0, 1.0)
                g.color = color
                g.drawArc(3, 3, width - 6, height - 6, 90, -(fraction * 360).toInt())
            }
        } finally { g.dispose() }
    }
}
