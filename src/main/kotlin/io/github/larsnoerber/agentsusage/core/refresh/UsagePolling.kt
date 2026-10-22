package io.github.larsnoerber.agentsusage.core.refresh

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes reads and rejects obsolete results before history, publication and EDT delivery. */
internal class UsagePolling<T>(
    initial: T,
    private val read: (Boolean) -> T,
    private val refreshIntervalSeconds: () -> Int,
    private val enabled: () -> Boolean = { true },
    private val provider: String,
    private val describe: (T) -> UsageReadInfo,
    private val retain: (T, T) -> T = { _, fresh -> fresh },
    private val record: (T) -> Unit = {},
    private val localPolling: Boolean = true,
    private val failure: (Throwable) -> T
) : Disposable {
    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()
    private val reading = AtomicBoolean()
    private val forceRefresh = AtomicBoolean()
    @Volatile private var disposed = false
    private var lastRefreshNanos: Long? = null
    @Volatile var current: T = initial
        private set
    private val scheduled = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
        { poll() }, 0, 5, TimeUnit.SECONDS
    )

    fun addListener(listener: (T) -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: (T) -> Unit) { listeners -= listener }

    fun refresh() {
        if (disposed || !forceRefresh.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread { poll() }
    }

    private fun poll() {
        if (disposed || !enabled() || !reading.compareAndSet(false, true)) return
        val generation = UsageRefreshStatuses.generation()
        try {
            val now = System.nanoTime()
            val interval = TimeUnit.SECONDS.toNanos(refreshIntervalSeconds().coerceAtLeast(1).toLong())
            val previous = lastRefreshNanos
            val requestUpdate = forceRefresh.getAndSet(false) || previous == null || now - previous >= interval
            if (!requestUpdate && !localPolling) return
            synchronized(UsageRefreshStatuses.publicationLock) {
                if (!valid(generation)) return
                if (requestUpdate) UsageRefreshStatuses.update(provider, UsageRefreshStatuses.get(provider).copy(loading = true))
            }
            val usage = try { read(requestUpdate) }
            catch (e: Exception) { failure(e) }
            catch (e: LinkageError) { failure(e) }
            synchronized(UsageRefreshStatuses.publicationLock) {
                if (!valid(generation)) return
                if (requestUpdate) lastRefreshNanos = now
                val info = describe(usage)
                val published = retain(current, usage)
                val display = describe(published)
                val status = UsageRefreshStatuses.get(provider)
                if (info.error == null && info.hasValues) record(usage)
                UsageRefreshStatuses.update(provider, UsageRefreshStatus(
                    hasValues = display.hasValues,
                    lastSuccess = if (requestUpdate && info.error == null && info.hasValues) System.currentTimeMillis() else status.lastSuccess,
                    observedAt = if (info.error == null && info.hasValues) info.observedAt.takeIf { it > 0 }
                        ?: status.observedAt.takeIf { !requestUpdate && it > 0 } ?: System.currentTimeMillis() else status.observedAt,
                    error = info.error,
                    problem = info.problem
                ))
                if (current == published) return
                current = published
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed && generation == UsageRefreshStatuses.generation()) listeners.forEach { listener ->
                        try { listener(published) } catch (_: Exception) {
                            Logger.getInstance(UsagePolling::class.java).warn("Unable to update usage view")
                        }
                    }
                }
            }
        } finally {
            reading.set(false)
            if (!disposed && forceRefresh.get() && enabled())
                ApplicationManager.getApplication().executeOnPooledThread { poll() }
        }
    }

    private fun valid(generation: Long): Boolean = !disposed && enabled() && generation == UsageRefreshStatuses.generation()

    override fun dispose() = synchronized(UsageRefreshStatuses.publicationLock) {
        disposed = true
        scheduled.cancel(false)
        listeners.clear()
        UsageRefreshStatuses.update(provider, UsageRefreshStatuses.get(provider).copy(loading = false))
    }
}
