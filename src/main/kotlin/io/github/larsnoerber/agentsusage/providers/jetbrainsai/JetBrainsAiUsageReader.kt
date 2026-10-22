package io.github.larsnoerber.agentsusage.providers.jetbrainsai

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

import com.intellij.openapi.application.ApplicationManager
import io.github.larsnoerber.agentsusage.core.reflection.PluginApi
import io.github.larsnoerber.agentsusage.core.reflection.loadedPlugin
import io.github.larsnoerber.agentsusage.core.reflection.loadPluginClass
import java.math.BigDecimal
import java.time.Instant

/** Keeps the optional, internal AI Assistant API isolated from the rest of the plugin. */
internal class JetBrainsAiUsageReader {
    private val api = PluginApi("JetBrains AI")
    private val subscriptionReader = JetBrainsAiSubscriptionReader()
    fun read(requestUpdate: Boolean): JetBrainsAiUsage {
        val plugin = loadedPlugin(PLUGIN_ID)
            ?: return JetBrainsAiUsage(error = "JetBrains AI Assistant is not installed or enabled")
        val plan = subscriptionReader.read(plugin.pluginClassLoader)
        val planUnavailableReason = subscriptionReader.unavailableReason
        val managerClass = loadPluginClass("$QUOTA_PACKAGE.QuotaManager2", plugin.pluginClassLoader, "intellij.ml.llm.core")
        val manager = ApplicationManager.getApplication().getService(managerClass)
            ?: error("JetBrains AI quota service is unavailable")
        if (requestUpdate) getter(manager, "requestUpdateEverything")
        val info = flowValue(getter(manager, "getQuotaInfo") ?: error("JetBrains AI quota is unavailable"))
            ?: return JetBrainsAiUsage(plan = plan, planUnavailableReason = planUnavailableReason,
                problem = UsageProblem.AUTHENTICATION, error = "Waiting for JetBrains AI quota. Sign in to AI Assistant.")
        val refillInfo = getter(manager, "getNextRefill")?.let(::flowValue)
        // Recent AI Assistant versions wrap the refill payload in QuotaRefillInfo.Known.
        val refill = refillInfo?.takeIf { it.javaClass.simpleName == "Known" }
            ?.let { getter(it, "getRefill", required = false) } ?: refillInfo
        val reset = refill?.let { getter(it, "getNext", required = false) }?.toString()
            ?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
        return when (info.javaClass.simpleName) {
            "Unlimited" -> JetBrainsAiUsage(unlimited = true, plan = plan, planUnavailableReason = planUnavailableReason)
            "Available", "Reached" -> {
                // QuotaManager2 now publishes status wrappers; the credit values live in their JbaiQuota payload.
                val quotaInfo = getter(info, "getQuota", required = false) ?: info
                // `current` is consumed quota; `available` in the details is the remaining balance.
                val maximum = amount(quotaInfo, "getMaximum") ?: error("JetBrains AI quota format is unsupported")
                val used = if (info.javaClass.simpleName == "Reached") maximum else
                    amount(quotaInfo, "getCurrent") ?: error("JetBrains AI quota format is unsupported")
                val unitsPerCredit = managerClass.classLoader.loadClass("$QUOTA_PACKAGE.CreditsKt")
                    .getMethod("getUNITS_IN_CREDIT").invoke(null) as BigDecimal
                fun quota(remaining: BigDecimal, total: BigDecimal) = JetBrainsAiQuota(
                    remaining.max(BigDecimal.ZERO).divide(unitsPerCredit),
                    total.max(BigDecimal.ZERO).divide(unitsPerCredit)
                )
                fun details(name: String): JetBrainsAiQuota? {
                    val detail = getter(quotaInfo, name, required = false) ?: return null
                    val total = amount(detail, "getMaximum") ?: return null
                    val remaining = amount(detail, "getAvailable")
                        ?: amount(detail, "getCurrent")?.let { total.subtract(it) } ?: return null
                    return quota(remaining, total)
                }
                JetBrainsAiUsage(
                    quota = quota(maximum.subtract(used), maximum),
                    subscription = details("getTariffQuota"),
                    topUp = details("getTopUpQuota"),
                    resetsAt = reset,
                    plan = plan,
                    planUnavailableReason = planUnavailableReason
                )
            }
            "Error" -> JetBrainsAiUsage(plan = plan, planUnavailableReason = planUnavailableReason,
                error = "JetBrains AI could not refresh the credit balance. Check AI Assistant.")
            else -> JetBrainsAiUsage(plan = plan, planUnavailableReason = planUnavailableReason,
                problem = UsageProblem.AUTHENTICATION, error = "Waiting for JetBrains AI quota. Sign in to AI Assistant.")
        }
    }

    private fun flowValue(flow: Any): Any? = api.flowValue(flow)

    private fun getter(target: Any, name: String, required: Boolean = true): Any? = api.getter(target, name, required)

    private fun amount(target: Any, name: String): BigDecimal? =
        when (val value = getter(target, name, required = false)) {
            is BigDecimal -> value
            is Number -> value.toString().toBigDecimalOrNull()
            else -> null
        }

    companion object {
        const val PLUGIN_ID = "com.intellij.ml.llm"
        private const val QUOTA_PACKAGE = "com.intellij.ml.llm.core.quota"
        fun isAvailable(): Boolean = loadedPlugin(PLUGIN_ID) != null
    }
}
