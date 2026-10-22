package io.github.larsnoerber.agentsusage.providers.codex

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

data class CodexUsage(
    val fiveHourLeft: Int? = null,
    val fiveHourReset: String? = null,
    val fiveHourResetsAt: Long? = null,
    val weeklyLeft: Int? = null,
    val weeklyReset: String? = null,
    val weeklyResetsAt: Long? = null,
    val credits: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
    val plan: String? = null,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE
)
