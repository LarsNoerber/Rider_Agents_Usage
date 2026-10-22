package io.github.larsnoerber.agentsusage.providers.copilot

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

import kotlin.math.roundToInt

data class CopilotQuota(
    val title: String,
    val percentLeft: Int?,
    val remaining: Int? = null,
    val total: Int? = null,
    val unlimited: Boolean = false
) {
    /** Keep the provider's remaining balance; Copilot UI presents consumption instead. */
    val used: Int?
        get() = if (unlimited || total == null || total <= 0 || remaining == null) null
        else total - remaining.coerceIn(0, total)

    val percentUsed: Int?
        get() {
            if (unlimited) return null
            percentLeft?.let { return 100 - it.coerceIn(0, 100) }
            val usedCount = used ?: return null
            val limit = total ?: return null
            return (usedCount.toDouble() * 100 / limit).roundToInt().coerceIn(0, 100)
        }

    val displayUsed: String
        get() = when {
            unlimited -> "Unlimited"
            used != null && total != null -> "$used / $total"
            percentUsed != null -> "$percentUsed%"
            else -> "—"
        }

    val displayRemaining: String
        get() = when {
            unlimited -> "Unlimited"
            remaining != null && total != null -> "$remaining / $total"
            percentLeft != null -> "$percentLeft%"
            else -> "—"
        }
}

data class GitHubCopilotUsage(
    val primary: CopilotQuota? = null,
    val chat: CopilotQuota? = null,
    val completions: CopilotQuota? = null,
    val plan: String? = null,
    val resetsAt: Long? = null,
    val reportedAt: Long? = null,
    val error: String? = null,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE
)
