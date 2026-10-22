package io.github.larsnoerber.agentsusage.providers.jetbrainsai

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

import java.math.BigDecimal
import java.math.RoundingMode

data class JetBrainsAiQuota(val remaining: BigDecimal, val total: BigDecimal) {
    val percentLeft: Int?
        get() = if (total.signum() > 0) {
            remaining.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt().coerceIn(0, 100)
        } else null
}

data class JetBrainsAiUsage(
    val quota: JetBrainsAiQuota? = null,
    val subscription: JetBrainsAiQuota? = null,
    val topUp: JetBrainsAiQuota? = null,
    val resetsAt: Long? = null,
    val unlimited: Boolean = false,
    val error: String? = null,
    val plan: String? = null,
    val planUnavailableReason: String? = null,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE
)

fun formatAiCredits(value: BigDecimal): String {
    val rounded = value.setScale(2, RoundingMode.HALF_UP)
    return if (value.signum() > 0 && rounded.signum() == 0) "<0.01" else rounded.stripTrailingZeros().toPlainString()
}
