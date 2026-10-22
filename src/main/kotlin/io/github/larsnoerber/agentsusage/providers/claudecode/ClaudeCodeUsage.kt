package io.github.larsnoerber.agentsusage.providers.claudecode

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

data class ClaudeCodeUsage(
    val requests: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val modelTokens: Map<String, Long> = emptyMap(),
    val logsFound: Boolean = false,
    val error: String? = null,
    val quotas: List<ClaudeQuota> = emptyList(),
    val plan: String? = null,
    val quotaError: String? = null,
    val updatedAt: Long = 0,
    val localUpdatedAt: Long = 0,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE
) {
    val totalTokens: Long get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens
}

data class ClaudeQuota(val title: String, val percentUsed: Int, val resetsAt: Long? = null) {
    val percentLeft: Int get() = 100 - percentUsed
}
