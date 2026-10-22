package io.github.larsnoerber.agentsusage.providers.cursor

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

data class CursorUsage(
    val percentUsed: Int? = null,
    val autoPercentUsed: Int? = null,
    val apiPercentUsed: Int? = null,
    val usedUsd: Double? = null,
    val limitUsd: Double? = null,
    val onDemandUsd: Double? = null,
    val resetsAt: Long? = null,
    val plan: String? = null,
    val quotaScope: String = "Included plan",
    val updatedAt: Long = 0,
    val error: String? = null,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE
)
