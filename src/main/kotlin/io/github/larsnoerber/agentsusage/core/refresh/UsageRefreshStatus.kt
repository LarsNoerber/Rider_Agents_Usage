package io.github.larsnoerber.agentsusage.core.refresh

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

enum class UsageProblem { UNAVAILABLE, AUTHENTICATION, INCOMPATIBLE }

internal data class UsageReadInfo(
    val hasValues: Boolean,
    val error: String? = null,
    val observedAt: Long = 0,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE
)

internal data class UsageRefreshStatus(
    val loading: Boolean = false,
    val hasValues: Boolean = false,
    val lastSuccess: Long = 0,
    val observedAt: Long = 0,
    val error: String? = null,
    val problem: UsageProblem = UsageProblem.UNAVAILABLE
)

/** Runtime metadata only; no persisted errors, accounts or authentication. */
internal object UsageRefreshStatuses {
    val publicationLock = Any()
    private var generation = 0L
    private val states = mutableMapOf<String, UsageRefreshStatus>()
    private val notificationPending = AtomicBoolean()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun generation(): Long = synchronized(publicationLock) { generation }

    fun invalidate() = synchronized(publicationLock) {
        generation++
        states.replaceAll { _, state -> state.copy(loading = false) }
        notifyListeners()
    }

    fun get(provider: String): UsageRefreshStatus = synchronized(publicationLock) {
        states[provider] ?: UsageRefreshStatus()
    }

    fun update(provider: String, state: UsageRefreshStatus) = synchronized(publicationLock) {
        if (states[provider] != state) {
            states[provider] = state
            notifyListeners()
        }
    }

    fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    private fun notifyListeners() {
        if (!notificationPending.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater {
            notificationPending.set(false)
            listeners.forEach { listener ->
                try { listener() } catch (_: Exception) {
                    Logger.getInstance(UsageRefreshStatuses::class.java).warn("Unable to update refresh status view")
                }
            }
        }
    }
}
