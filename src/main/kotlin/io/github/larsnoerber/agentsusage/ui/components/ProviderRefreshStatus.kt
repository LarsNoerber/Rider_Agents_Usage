package io.github.larsnoerber.agentsusage.ui.components

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import io.github.larsnoerber.agentsusage.core.format.formatCompactTime
import io.github.larsnoerber.agentsusage.core.format.formatResetTime
import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem
import io.github.larsnoerber.agentsusage.core.refresh.UsageRefreshStatus
import io.github.larsnoerber.agentsusage.core.refresh.UsageRefreshStatuses

/** A panel owns this subscription, independently of its provider snapshot listener. */
internal class ProviderRefreshStatus(private val provider: String) : JBLabel(), Disposable {
    private var disposed = false
    private val listener: () -> Unit = { if (!disposed) render() }

    init {
        foreground = secondaryTextColor()
        font = font.deriveFont((font.size2D - 1).coerceAtLeast(10f))
        render()
        UsageRefreshStatuses.addListener(listener)
    }

    private fun render() {
        val status = UsageRefreshStatuses.get(provider)
        val readLabel = if (status.measurementTimeKnown) "Last success" else "Last read"
        text = refreshStatusText(status) + if (status.lastSuccess > 0) " | $readLabel ${formatCompactTime(status.lastSuccess / 1000)}" else ""
        toolTipText = usageTooltip("Refresh status", refreshStatusLines(status))
        // In a JLabel subclass Kotlin can resolve accessibleContext to the protected, still-null field.
        // Call the getter explicitly so Swing creates the context before assigning its name.
        getAccessibleContext()?.accessibleName = text
    }

    override fun dispose() {
        disposed = true
        UsageRefreshStatuses.removeListener(listener)
    }
}

internal fun refreshStatusText(status: UsageRefreshStatus): String {
    val state = when {
        status.loading -> "Refreshing..."
        status.error != null -> when (status.problem) {
            UsageProblem.AUTHENTICATION -> "Sign-in required"
            UsageProblem.INCOMPATIBLE -> "Provider API incompatible"
            UsageProblem.UNAVAILABLE -> "Refresh unavailable"
        } + if (status.hasValues) " | Values may be stale" else ""
        status.hasValues && !status.measurementTimeKnown -> "Provider state | Age unknown"
        status.hasValues -> "Current"
        else -> "Waiting for measurements"
    }
    return state
}

internal fun refreshStatusLines(status: UsageRefreshStatus): List<String> = buildList {
    add(refreshStatusText(status))
    if (status.observedAt > 0) add("Last measurement: ${formatResetTime(status.observedAt / 1000)}")
    if (status.hasValues && !status.measurementTimeKnown) {
        add("Measurement time unavailable. The provider controls quota updates; reading its state does not request fresh quota.")
    }
    if (status.lastSuccess > 0) {
        val readLabel = if (status.measurementTimeKnown) "Last successful refresh" else "Last successful local read"
        add("$readLabel: ${formatResetTime(status.lastSuccess / 1000)}")
    }
    status.error?.let { add(it) }
}
