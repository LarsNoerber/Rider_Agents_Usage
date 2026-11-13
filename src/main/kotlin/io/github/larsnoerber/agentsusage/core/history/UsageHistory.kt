package io.github.larsnoerber.agentsusage.core.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

data class UsageHistoryPoint(var at: Long = 0, var value: Double = 0.0, var reset: Long = 0)
data class UsageHistorySeries(var key: String = "", var points: MutableList<UsageHistoryPoint> = mutableListOf())
class UsageHistoryState { var series: MutableList<UsageHistorySeries> = mutableListOf() }

/** Numeric observations only. No accounts, credentials, prompts, or provider response bodies. */
@Service(Service.Level.APP)
@State(name = "AgentsUsageHistory", storages = [Storage("agents-usage-history.xml")])
internal class UsageHistory : PersistentStateComponent<UsageHistoryState> {
    private var data = UsageHistoryState()

    @Synchronized override fun getState(): UsageHistoryState = UsageHistoryState().also { state ->
        prune()
        state.series = data.series.map { series ->
            series.copy(points = series.points.map { it.copy() }.toMutableList())
        }.toMutableList()
    }

    @Synchronized override fun loadState(state: UsageHistoryState) { data = state; prune() }

    @Synchronized fun record(key: String, value: Double, reset: Long?, at: Long = System.currentTimeMillis()) {
        if (!value.isFinite() || value < 0 || !key.matches(keyPattern)) return
        prune()
        val series = data.series.firstOrNull { it.key == key }
            ?: UsageHistorySeries(key).also { data.series.add(it) }
        val previous = series.points.lastOrNull()
        if (previous != null && at <= previous.at) return
        val point = UsageHistoryPoint(at, value, reset ?: 0)
        // Bound unchanged observations to one per minute, while preserving every quota change.
        if (previous != null && previous.value == value && previous.reset == point.reset && at - previous.at < 60_000) return
        series.points.add(point)
        if (series.points.size > MAX_POINTS) series.points.removeAt(0)
    }

    @Synchronized fun points(key: String): List<UsageHistoryPoint> {
        prune()
        return data.series.firstOrNull { it.key == key }?.points?.map { it.copy() }.orEmpty()
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        data.series.forEach { series ->
            series.points = series.points.filter { it.at >= cutoff && it.at <= System.currentTimeMillis() &&
                it.value.isFinite() && it.value >= 0 }.sortedBy { it.at }.takeLast(MAX_POINTS).toMutableList()
        }
        data.series.removeAll { it.points.isEmpty() }
    }

    companion object {
        private const val MAX_POINTS = 1800
        private val keyPattern = Regex("[a-zA-Z0-9._-]{1,80}")
        fun getInstance(): UsageHistory = ApplicationManager.getApplication().getService(UsageHistory::class.java)
    }
}
