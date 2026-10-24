package io.github.larsnoerber.agentsusage.application

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsage
import io.github.larsnoerber.agentsusage.providers.codex.CodexUsageService
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsage
import io.github.larsnoerber.agentsusage.providers.copilot.GitHubCopilotUsageService
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsage
import io.github.larsnoerber.agentsusage.providers.jetbrainsai.JetBrainsAiUsageService
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsage
import io.github.larsnoerber.agentsusage.providers.claudecode.ClaudeCodeUsageService
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsage
import io.github.larsnoerber.agentsusage.providers.cursor.CursorUsageService
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.concurrent.CopyOnWriteArrayList

// Persisted property names are part of the existing XML schema.
class UsageInsightsState {
    var days: MutableList<UsageInsightsDay> = mutableListOf()
    var bossDamageWeek: String = ""
    var bossDamage: Int = 0
    var bossDamageVersion: Int = 0
}

data class UsageInsightsDay(
    var date: String = "",
    var codexStart: Int? = null,
    var codexLow: Int? = null,
    var codexStartAt: Long = 0,
    var jetBrainsStart: Int? = null,
    var jetBrainsLow: Int? = null,
    var jetBrainsStartAt: Long = 0,
    var copilotStart: Int? = null,
    var copilotLow: Int? = null,
    var copilotStartAt: Long = 0,
    var claudeStart: Int? = null,
    var claudeLow: Int? = null,
    var claudeStartAt: Long = 0,
    var cursorStart: Int? = null,
    var cursorLow: Int? = null,
    var cursorStartAt: Long = 0
)

internal data class UsagePartyMember(val provider: String, val percentLeft: Int?, val unlimited: Boolean = false)
internal data class UsageForecast(val provider: String, val hoursUntilLow: Double)
internal data class UsageBossHit(val id: Long, val provider: String, val points: Int, val observedAt: Long)

internal data class UsageInsightsSnapshot(
    val days: List<UsageInsightsDay>,
    val bossDamage: Int,
    val bossName: String,
    val bossVariant: Int,
    val bossPhase: String,
    val bossLine: String,
    val party: List<UsagePartyMember>,
    val forecast: UsageForecast?,
    val battleLog: List<String>,
    val celebration: String?,
    val celebrationId: Long,
    val hits: List<UsageBossHit>
)

private data class WeeklyBoss(
    val name: String,
    val lines: List<String>
)

/** Collects provider-reported quota snapshots for a small, local weekly recap. */
@Service(Service.Level.APP)
@State(name = "AgentsUsageInsights", storages = [Storage("agents-usage-insights.xml")])
internal class UsageInsightsService : PersistentStateComponent<UsageInsightsState>, Disposable {
    // Persisted daily measurements and transient battle feedback have separate lifetimes.
    private val listeners = CopyOnWriteArrayList<(UsageInsightsSnapshot) -> Unit>()
    private var state = UsageInsightsState()
    private var previousCodex: CodexUsage? = null
    private var previousJetBrains: JetBrainsAiUsage? = null
    private var previousCopilot: GitHubCopilotUsage? = null
    private var previousClaude: ClaudeCodeUsage? = null
    private var previousCursor: CursorUsage? = null
    private var celebration: String? = null
    private var celebrationUntil = 0L
    private var celebrationId = 0L
    private val battleLog = mutableListOf<String>()
    private var loggedBossWeek: LocalDate? = null
    private var loggedBossPhase = -1
    private var loggedBattleDay: LocalDate? = null
    private var loggedBattleHour: LocalDateTime? = null
    private val aggregatedHitPoints = mutableMapOf<String, Int>()
    private val lastHitAt = mutableMapOf<String, Long>()
    private val hits = mutableListOf<UsageBossHit>()
    private var nextHitId = 0L
    @Volatile private var disposed = false

    // Keep the exact listener instances so disposal can unsubscribe from every provider.
    private val codexService = CodexUsageService.getInstance()
    private val codexListener: (CodexUsage) -> Unit = ::acceptCodex
    private val jetBrainsService = if (JetBrainsAiUsageService.isAvailable()) JetBrainsAiUsageService.getInstance() else null
    private val jetBrainsListener: (JetBrainsAiUsage) -> Unit = ::acceptJetBrains
    private val copilotService = if (GitHubCopilotUsageService.isAvailable()) GitHubCopilotUsageService.getInstance() else null
    private val copilotListener: (GitHubCopilotUsage) -> Unit = ::acceptCopilot
    private val claudeService = ClaudeCodeUsageService.getInstance()
    private val claudeListener: (ClaudeCodeUsage) -> Unit = ::acceptClaude
    private val cursorService = CursorUsageService.getInstance()
    private val cursorListener: (CursorUsage) -> Unit = ::acceptCursor

    init {
        state.days = state.days.filter { it.date.toLocalDateOrNull()?.let { date -> !date.isBefore(LocalDate.now().minusDays(6)) } == true }
            .toMutableList()
        previousCodex = codexService.current
        codexService.addListener(codexListener)
        previousCodex?.let(::recordCodex)
        if (jetBrainsService != null) {
            previousJetBrains = jetBrainsService.current
            jetBrainsService.addListener(jetBrainsListener)
            previousJetBrains?.let(::recordJetBrains)
        }
        if (copilotService != null) {
            previousCopilot = copilotService.current
            copilotService.addListener(copilotListener)
            previousCopilot?.let(::recordCopilot)
        }
        previousClaude = claudeService.current
        claudeService.addListener(claudeListener)
        previousCursor = cursorService.current
        cursorService.addListener(cursorListener)
    }

    override fun getState(): UsageInsightsState = state

    override fun loadState(state: UsageInsightsState) {
        this.state = state
        this.state.days = state.days.filter {
            it.date.toLocalDateOrNull()?.let { date -> !date.isBefore(LocalDate.now().minusDays(6)) } == true
        }.toMutableList()
        synchronizeBossDamage()
    }

    fun addListener(listener: (UsageInsightsSnapshot) -> Unit) {
        listeners.addIfAbsent(listener)
        listener(snapshot())
    }

    fun refreshView() = publish()

    @Synchronized
    fun overviewOpened() {
        val weekStart = weekStart()
        val damage = persistedBossDamage(weekStart)
        if (damage > 0) {
            val boss = bossFor(weekStart)
            addBattleEvent("${boss.name}: $damage% damage this week")
        }
        publish()
    }

    fun removeListener(listener: (UsageInsightsSnapshot) -> Unit) {
        listeners -= listener
    }

    @Synchronized
    fun clearHistory() {
        state.days.clear()
        state.bossDamageWeek = weekStart().toString()
        state.bossDamage = 0
        state.bossDamageVersion = 1
        battleLog.clear()
        aggregatedHitPoints.clear()
        lastHitAt.clear()
        hits.clear()
        loggedBossWeek = null
        loggedBossPhase = -1
        loggedBattleDay = null
        loggedBattleHour = null
        publish()
    }

    // Provider events: distinguish resets from consumption before updating the daily baseline.
    private fun acceptCodex(usage: CodexUsage) {
        if (disposed) return
        val settings = AgentsUsageSettings.getInstance().state
        if (!settings.showWeeklyInsights || !settings.showOpenAi) {
            previousCodex = usage
            return
        }
        val didReset = previousCodex?.let { old ->
            reset(old.fiveHourResetsAt, usage.fiveHourResetsAt, old.fiveHourLeft, usage.fiveHourLeft) ||
                reset(old.weeklyResetsAt, usage.weeklyResetsAt, old.weeklyLeft, usage.weeklyLeft)
        } ?: false
        val before = previousCodex?.takeIf { it.error == null }?.let(::codexRemaining)
        val after = usage.takeIf { it.error == null }?.let(::codexRemaining)
        if (didReset) {
            celebrate("Codex")
            addBattleEvent("Codex reset")
        } else {
            val old = previousCodex?.takeIf { it.error == null }
            val current = usage.takeIf { it.error == null }
            val drop = maxOf(quotaDrop(old?.fiveHourLeft, current?.fiveHourLeft), quotaDrop(old?.weeklyLeft, current?.weeklyLeft))
            recordHit("Codex", drop, 0)
        }
        previousCodex = usage
        recordCodex(usage, didReset)
        publish()
    }

    private fun acceptJetBrains(usage: JetBrainsAiUsage) {
        if (disposed) return
        val settings = AgentsUsageSettings.getInstance().state
        if (!settings.showWeeklyInsights || !settings.showJetBrainsAi) {
            previousJetBrains = usage
            return
        }
        val didReset = previousJetBrains?.let { old ->
            reset(old.resetsAt, usage.resetsAt, old.quota?.percentLeft, usage.quota?.percentLeft)
        } ?: false
        val before = previousJetBrains?.takeIf { it.error == null }?.quota?.percentLeft
        val after = usage.takeIf { it.error == null }?.quota?.percentLeft
        if (didReset) {
            celebrate("JetBrains AI")
            addBattleEvent("JetBrains AI reset")
        } else recordHit("JetBrains AI", before, after)
        previousJetBrains = usage
        recordJetBrains(usage, didReset)
        publish()
    }

    private fun acceptCopilot(usage: GitHubCopilotUsage) {
        if (disposed) return
        val settings = AgentsUsageSettings.getInstance().state
        if (!settings.showWeeklyInsights || !settings.showCopilot) {
            previousCopilot = usage
            return
        }
        val didReset = previousCopilot?.let { old ->
            reset(old.resetsAt, usage.resetsAt, old.primary?.percentLeft, usage.primary?.percentLeft)
        } ?: false
        val before = previousCopilot?.takeIf { it.error == null }?.primary?.percentLeft
        val after = usage.takeIf { it.error == null }?.primary?.percentLeft
        if (didReset) {
            celebrate("Copilot")
            addBattleEvent("Copilot reset")
        } else recordHit("Copilot", before, after)
        previousCopilot = usage
        recordCopilot(usage, didReset)
        publish()
    }

    private fun acceptClaude(usage: ClaudeCodeUsage) {
        if (disposed) return
        val settings = AgentsUsageSettings.getInstance().state
        if (!settings.showWeeklyInsights || !settings.showClaudeCode) {
            previousClaude = usage
            return
        }
        val before = previousClaude?.takeIf { it.quotaError == null }?.let(::claudeRemaining)
        val after = usage.takeIf { it.quotaError == null }?.let(::claudeRemaining)
        val oldReset = previousClaude?.let(::claudeReset)
        val didReset = reset(oldReset, claudeReset(usage), before, after)
        if (didReset) {
            celebrate("Claude")
            addBattleEvent("Claude reset")
        }
        else {
            val old = previousClaude?.takeIf { it.quotaError == null }
            val drop = if (usage.quotaError == null) usage.quotas.filter { it.title == "Session" || it.title == "Weekly" }
                .maxOfOrNull { quota -> quotaDrop(old?.quotas?.firstOrNull { it.title == quota.title }?.percentLeft, quota.percentLeft) } ?: 0
                else 0
            recordHit("Claude", drop, 0)
        }
        previousClaude = usage
        if (after != null) observe("claude", after, didReset)
        publish()
    }

    private fun acceptCursor(usage: CursorUsage) {
        if (disposed) return
        val settings = AgentsUsageSettings.getInstance().state
        if (!settings.showWeeklyInsights || !settings.showCursor) {
            previousCursor = usage
            return
        }
        val before = previousCursor?.takeIf { it.error == null }?.percentUsed?.let { 100 - it }
        val after = usage.takeIf { it.error == null }?.percentUsed?.let { 100 - it }
        val didReset = reset(previousCursor?.resetsAt, usage.resetsAt, before, after)
        if (didReset) {
            celebrate("Cursor")
            addBattleEvent("Cursor reset")
        }
        else recordHit("Cursor", before, after)
        previousCursor = usage
        if (after != null) observe("cursor", after, didReset)
        publish()
    }

    private fun claudeRemaining(usage: ClaudeCodeUsage): Int? = usage.quotas
        .filter { it.title == "Session" || it.title == "Weekly" }.minOfOrNull { it.percentLeft }

    private fun claudeReset(usage: ClaudeCodeUsage): Long? = usage.quotas
        .filter { it.title == "Session" || it.title == "Weekly" }.minByOrNull { it.percentLeft }?.resetsAt

    private fun reset(oldReset: Long?, newReset: Long?, oldLeft: Int?, newLeft: Int?): Boolean =
        oldReset != null && newReset != null && oldReset != newReset && oldLeft != null && newLeft != null && newLeft >= oldLeft + 20

    private fun quotaDrop(before: Int?, after: Int?): Int = if (before == null || after == null) 0 else (before - after).coerceAtLeast(0)

    private fun celebrate(provider: String) {
        celebration = provider
        celebrationUntil = System.currentTimeMillis() + CELEBRATION_MILLIS
        celebrationId++
    }

    @Synchronized
    private fun recordHit(provider: String, before: Int?, after: Int?) {
        // Damage is recorded once on observation, independent of how often the overview repaints.
        val drop = before?.minus(after ?: before) ?: return
        if (drop < HIT_LOG_THRESHOLD) return
        synchronizeBossDamage()
        val damage = (drop * BOSS_DAMAGE_PER_QUOTA_POINT).coerceAtMost(100 - state.bossDamage)
        if (damage <= 0) return
        state.bossDamage += damage
        val now = System.currentTimeMillis()
        hits.add(UsageBossHit(++nextHitId, provider, damage, now))
        if (hits.size > MAX_BATTLE_EVENTS) hits.removeAt(0)
        val recent = now - (lastHitAt[provider] ?: 0L) <= HIT_AGGREGATION_MILLIS
        val total = if (recent) (aggregatedHitPoints[provider] ?: 0) + damage else damage
        aggregatedHitPoints[provider] = total
        lastHitAt[provider] = now
        if (recent) battleLog.removeAll { it.startsWith("Hit by $provider · ") }
        addBattleEvent("Hit by $provider · $total Points")
    }

    private fun addBattleEvent(message: String) {
        battleLog.add(0, message)
        if (battleLog.size > MAX_BATTLE_EVENTS) battleLog.removeAt(battleLog.lastIndex)
    }

    private fun recordCodex(usage: CodexUsage, resetObserved: Boolean = false) {
        if (!AgentsUsageSettings.getInstance().state.showWeeklyInsights || !AgentsUsageSettings.getInstance().state.showOpenAi || usage.error != null) return
        val values = listOfNotNull(usage.fiveHourLeft, usage.weeklyLeft)
        if (values.isNotEmpty()) observe("codex", values.min(), resetObserved)
    }

    private fun recordJetBrains(usage: JetBrainsAiUsage, resetObserved: Boolean = false) {
        if (!AgentsUsageSettings.getInstance().state.showWeeklyInsights || !AgentsUsageSettings.getInstance().state.showJetBrainsAi || usage.error != null) return
        usage.quota?.percentLeft?.let { observe("jetBrains", it, resetObserved) }
    }

    private fun recordCopilot(usage: GitHubCopilotUsage, resetObserved: Boolean = false) {
        if (!AgentsUsageSettings.getInstance().state.showWeeklyInsights || !AgentsUsageSettings.getInstance().state.showCopilot || usage.error != null) return
        usage.primary?.percentLeft?.let { observe("copilot", it, resetObserved) }
    }

    @Synchronized
    private fun observe(provider: String, remaining: Int, resetObserved: Boolean) {
        val today = LocalDate.now()
        state.days.removeAll { it.date.toLocalDateOrNull()?.isBefore(today.minusDays(6)) == true }
        val day = state.days.firstOrNull { it.date == today.toString() }
            ?: UsageInsightsDay().also { it.date = today.toString(); state.days.add(it) }
        // Pair each persisted start/low/time field with its setter; retain the existing schema.
        when (provider) {
            "codex" -> day.update(
                remaining, resetObserved,
                { day.codexStart }, { day.codexStart = it },
                { day.codexLow }, { day.codexLow = it },
                { day.codexStartAt }, { day.codexStartAt = it }
            )
            "jetBrains" -> day.update(
                remaining, resetObserved,
                { day.jetBrainsStart }, { day.jetBrainsStart = it },
                { day.jetBrainsLow }, { day.jetBrainsLow = it },
                { day.jetBrainsStartAt }, { day.jetBrainsStartAt = it }
            )
            "copilot" -> day.update(
                remaining, resetObserved,
                { day.copilotStart }, { day.copilotStart = it },
                { day.copilotLow }, { day.copilotLow = it },
                { day.copilotStartAt }, { day.copilotStartAt = it }
            )
            "claude" -> day.update(
                remaining, resetObserved,
                { day.claudeStart }, { day.claudeStart = it },
                { day.claudeLow }, { day.claudeLow = it },
                { day.claudeStartAt }, { day.claudeStartAt = it }
            )
            "cursor" -> day.update(
                remaining, resetObserved,
                { day.cursorStart }, { day.cursorStart = it },
                { day.cursorLow }, { day.cursorLow = it },
                { day.cursorStartAt }, { day.cursorStartAt = it }
            )
        }
        synchronizeBossDamage()
    }

    @Synchronized
    private fun snapshot(): UsageInsightsSnapshot {
        // Build weekly totals and record phase/day/hour transitions before composing the view model.
        val settings = AgentsUsageSettings.getInstance().state
        val current = LocalDate.now()
        val weekStart = weekStart(current)
        val week = state.days.filter { it.date.toLocalDateOrNull()?.let { date -> !date.isBefore(weekStart) && !date.isAfter(current) } == true }
        val damage = persistedBossDamage(weekStart)
        val boss = bossFor(weekStart)
        val phaseIndex = when {
            damage >= 100 -> 4
            damage >= 75 -> 3
            damage >= 50 -> 2
            damage >= 25 -> 1
            else -> 0
        }
        if (loggedBossWeek != weekStart) {
            loggedBossWeek = weekStart
            loggedBossPhase = phaseIndex
            addBattleEvent("${boss.name} arrived · ${boss.lines[phaseIndex]}")
        } else if (phaseIndex > loggedBossPhase) {
            loggedBossPhase = phaseIndex
            addBattleEvent("${boss.name} · ${boss.lines[phaseIndex]}")
        }
        val dailyDamage = week.firstOrNull { it.date == current.toString() }
            ?.let { it.codexDrop() + it.jetBrainsDrop() + it.copilotDrop() + it.claudeDrop() + it.cursorDrop() }
            ?: 0
        if (loggedBattleDay != current) {
            loggedBattleDay = current
            if (week.isNotEmpty()) addBattleEvent("New daily round")
        }
        val currentHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        if (loggedBattleHour != currentHour) {
            loggedBattleHour = currentHour
            if (dailyDamage > 0) addBattleEvent("Today: $dailyDamage% quota use")
        }
        // Party members use remaining quota consistently, including consumed-quota providers.
        val party = buildList {
            if (settings.showOpenAi) previousCodex?.takeIf { it.error == null }?.let { usage ->
                codexRemaining(usage)?.let { add(UsagePartyMember("Codex", it)) }
            }
            if (settings.showJetBrainsAi) previousJetBrains?.takeIf { it.error == null }?.let { usage ->
                if (usage.unlimited) add(UsagePartyMember("JetBrains AI", null, unlimited = true))
                else usage.quota?.percentLeft?.let { add(UsagePartyMember("JetBrains AI", it)) }
            }
            if (settings.showCopilot) previousCopilot?.takeIf { it.error == null }?.primary?.let { quota ->
                if (quota.unlimited) add(UsagePartyMember("Copilot", null, unlimited = true))
                else quota.percentLeft?.let { add(UsagePartyMember("Copilot", it)) }
            }
            if (settings.showClaudeCode) previousClaude?.takeIf { it.quotaError == null }?.let(::claudeRemaining)
                ?.let { add(UsagePartyMember("Claude", it)) }
            if (settings.showCursor) previousCursor?.takeIf { it.error == null }?.percentUsed
                ?.let { add(UsagePartyMember("Cursor", 100 - it)) }
        }
        // Forecasts use today's baseline and exclude allowances that reset before the warning level.
        val today = state.days.firstOrNull { it.date == current.toString() }
        val forecasts = buildList {
            if (settings.showOpenAi) {
                val quota = previousCodex?.takeIf { it.error == null }
                quota?.let(::codexRemaining)?.let { left ->
                    val resetsAt = codexResetForTightest(quota)
                    estimate("Codex", left, today?.codexStart, today?.codexLow, today?.codexStartAt, resetsAt)?.let(::add)
                }
            }
            if (settings.showJetBrainsAi) {
                previousJetBrains?.takeIf { it.error == null && !it.unlimited }?.quota?.percentLeft?.let { left ->
                    estimate("JetBrains AI", left, today?.jetBrainsStart, today?.jetBrainsLow, today?.jetBrainsStartAt, previousJetBrains?.resetsAt)?.let(::add)
                }
            }
            if (settings.showCopilot) {
                previousCopilot?.takeIf { it.error == null }?.let { usage ->
                    usage.primary?.percentLeft?.let { left ->
                        estimate("Copilot", left, today?.copilotStart, today?.copilotLow, today?.copilotStartAt, usage.resetsAt)?.let(::add)
                    }
                }
            }
        }
        return UsageInsightsSnapshot(
            days = week.map { it.copy() },
            bossDamage = damage,
            bossName = boss.name,
            bossVariant = bossRoster.indexOf(boss),
            bossPhase = listOf("Guarded", "Shield cracked", "Staggered", "Final phase", "Defeated")[phaseIndex],
            bossLine = boss.lines[phaseIndex],
            party = party,
            forecast = forecasts.minByOrNull { it.hoursUntilLow },
            battleLog = battleLog.toList(),
            celebration = celebration.takeIf { System.currentTimeMillis() < celebrationUntil },
            celebrationId = celebrationId,
            hits = hits.toList()
        )
    }

    private fun publish() {
        if (disposed) return
        val value = snapshot()
        listeners.forEach { listener ->
            ApplicationManager.getApplication().invokeLater { if (!disposed) listener(value) }
        }
    }

    private fun persistedBossDamage(weekStart: LocalDate): Int {
        synchronizeBossDamage(weekStart)
        return state.bossDamage.coerceIn(0, 100)
    }

    private fun synchronizeBossDamage(weekStart: LocalDate = weekStart()) {
        if (state.bossDamageWeek == weekStart.toString()) {
            if (state.bossDamageVersion == 0) {
                // Migrate legacy quota-point damage once; subsequent reads already store scaled HP.
                val recorded = state.days.filter { it.date.toLocalDateOrNull()?.let { date -> !date.isBefore(weekStart) } == true }
                    .sumOf { it.codexDrop() + it.jetBrainsDrop() + it.copilotDrop() + it.claudeDrop() + it.cursorDrop() }
                state.bossDamage = (maxOf(state.bossDamage, recorded) * BOSS_DAMAGE_PER_QUOTA_POINT).coerceIn(0, 100)
                state.bossDamageVersion = 1
            } else state.bossDamage = state.bossDamage.coerceIn(0, 100)
            return
        }
        val end = minOf(LocalDate.now(), weekStart.plusDays(6))
        hits.clear()
        aggregatedHitPoints.clear()
        lastHitAt.clear()
        state.bossDamage = state.days.asSequence()
            .filter { it.date.toLocalDateOrNull()?.let { date -> !date.isBefore(weekStart) && !date.isAfter(end) } == true }
            .sumOf { day -> (day.codexDrop() + day.jetBrainsDrop() + day.copilotDrop() + day.claudeDrop() + day.cursorDrop()) * BOSS_DAMAGE_PER_QUOTA_POINT }
            .coerceIn(0, 100)
        state.bossDamageWeek = weekStart.toString()
        state.bossDamageVersion = 1
    }

    private fun weekStart(date: LocalDate = LocalDate.now()): LocalDate =
        date.with(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.value.toLong())

    override fun dispose() {
        disposed = true
        codexService.removeListener(codexListener)
        jetBrainsService?.removeListener(jetBrainsListener)
        copilotService?.removeListener(copilotListener)
        claudeService.removeListener(claudeListener)
        cursorService.removeListener(cursorListener)
        listeners.clear()
    }

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
    private fun UsageInsightsDay.codexDrop(): Int = (codexStart ?: 0) - (codexLow ?: codexStart ?: 0)
    private fun UsageInsightsDay.jetBrainsDrop(): Int = (jetBrainsStart ?: 0) - (jetBrainsLow ?: jetBrainsStart ?: 0)
    private fun UsageInsightsDay.copilotDrop(): Int = (copilotStart ?: 0) - (copilotLow ?: copilotStart ?: 0)
    private fun UsageInsightsDay.claudeDrop(): Int = (claudeStart ?: 0) - (claudeLow ?: claudeStart ?: 0)
    private fun UsageInsightsDay.cursorDrop(): Int = (cursorStart ?: 0) - (cursorLow ?: cursorStart ?: 0)

    private inline fun UsageInsightsDay.update(
        value: Int,
        resetObserved: Boolean,
        getStart: () -> Int?,
        setStart: (Int) -> Unit,
        getLow: () -> Int?,
        setLow: (Int) -> Unit,
        getStartAt: () -> Long,
        setStartAt: (Long) -> Unit
    ) {
        val newBaseline = resetObserved || getStart() == null || getStartAt() == 0L
        // A refill starts a new baseline rather than counting a quota increase as negative use.
        if (newBaseline) {
            setStart(value)
            setLow(value)
            setStartAt(System.currentTimeMillis())
        } else {
            setLow(minOf(getLow() ?: value, value))
        }
    }

    private fun codexRemaining(usage: CodexUsage): Int? = listOfNotNull(usage.fiveHourLeft, usage.weeklyLeft).minOrNull()

    private fun codexResetForTightest(usage: CodexUsage): Long? = when {
        usage.fiveHourLeft == null -> usage.weeklyResetsAt
        usage.weeklyLeft == null -> usage.fiveHourResetsAt
        usage.fiveHourLeft <= usage.weeklyLeft -> usage.fiveHourResetsAt
        else -> usage.weeklyResetsAt
    }

    private fun estimate(
        provider: String,
        current: Int,
        start: Int?,
        low: Int?,
        startedAt: Long?,
        resetsAt: Long?
    ): UsageForecast? {
        val from = start ?: return null
        val minimum = low ?: return null
        val began = startedAt?.takeIf { it > 0 } ?: return null
        val elapsedHours = (System.currentTimeMillis() - began) / MILLIS_PER_HOUR
        val observedDrop = from - minimum
        if (elapsedHours < MIN_FORECAST_HOURS || observedDrop < MIN_FORECAST_DROP) return null
        val pointsPerHour = observedDrop / elapsedHours
        val hoursUntilLow = (current - LOW_QUOTA_THRESHOLD).coerceAtLeast(0) / pointsPerHour
        if (hoursUntilLow > MAX_FORECAST_HOURS) return null
        val resetInHours = resetsAt?.let { (it * 1_000 - System.currentTimeMillis()) / MILLIS_PER_HOUR }
        if (resetInHours != null && resetInHours > 0 && resetInHours < hoursUntilLow) return null
        return UsageForecast(provider, hoursUntilLow)
    }

    private fun bossFor(weekStart: LocalDate): WeeklyBoss {
        val index = Math.floorMod(weekStart.toEpochDay() / 7, bossRoster.size.toLong()).toInt()
        return bossRoster[index]
    }

    companion object {
        const val CELEBRATION_MILLIS = 7_000L
        private const val HIT_LOG_THRESHOLD = 1
        private const val BOSS_DAMAGE_PER_QUOTA_POINT = 4
        private const val HIT_AGGREGATION_MILLIS = 15 * 60 * 1_000L
        private const val MAX_BATTLE_EVENTS = 4
        private const val MIN_FORECAST_HOURS = 1.0
        private const val MIN_FORECAST_DROP = 3
        private const val LOW_QUOTA_THRESHOLD = 20
        private const val MAX_FORECAST_HOURS = 168.0
        private const val MILLIS_PER_HOUR = 3_600_000.0
        private val bossRoster = listOf(
            WeeklyBoss("Quota Wraith", listOf("Its shield is up. Show it a steady week.", "The shield cracks!", "The Wraith is wobbling!", "One last push to finish the fight!", "The Wraith retreats until next week.")),
            WeeklyBoss("Build Golem", listOf("The Golem is assembling its armor.", "A plate just fell off!", "The Golem is losing its footing!", "Its core is exposed!", "The Golem powers down for the week.")),
            WeeklyBoss("Deadline Dragon", listOf("The Dragon circles above the arena.", "One wing is clipped!", "The Dragon is running out of fire!", "The final flame is fading!", "The Dragon flies off until next week.")),
            WeeklyBoss("Cache Kraken", listOf("The Kraken lurks below the surface.", "A tentacle lets go!", "The Kraken is surfacing!", "Its last wave is breaking!", "The Kraken sinks back until next week.")),
            WeeklyBoss("Prompt Phantom", listOf("The Phantom appears in a cloud of prompts.", "Its cloak is torn!", "The Phantom is losing shape!", "One final spell remains!", "The Phantom vanishes for the week.")),
            WeeklyBoss("Token Titan", listOf("The Titan raises a giant token shield.", "The shield is dented!", "The Titan staggers!", "Its last rune is fading!", "The Titan returns next week.") )
        )
        fun getInstance(): UsageInsightsService =
            ApplicationManager.getApplication().getService(UsageInsightsService::class.java)
    }
}
