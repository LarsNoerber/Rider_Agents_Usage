package io.github.larsnoerber.agentsusage.providers.gemini

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem
import kotlin.math.roundToInt

data class GeminiModelQuota(val modelId: String, val remainingFraction: Double, val resetsAt: Long?) {
    val percentLeft: Int get() = (remainingFraction * 100).roundToInt()
}

data class GeminiUsage(
    val models: List<GeminiModelQuota> = emptyList(),
    val error: String? = null,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE,
    val plan: String? = null
)
