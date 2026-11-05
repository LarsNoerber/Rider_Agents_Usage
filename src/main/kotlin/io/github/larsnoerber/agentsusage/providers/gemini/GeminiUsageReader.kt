package io.github.larsnoerber.agentsusage.providers.gemini

import io.github.larsnoerber.agentsusage.core.reflection.PluginApi
import io.github.larsnoerber.agentsusage.core.reflection.loadedPlugin
import java.time.Instant

internal class GeminiUsageReader {
    private val api = PluginApi("Gemini")

    fun read(@Suppress("UNUSED_PARAMETER") requestUpdate: Boolean): GeminiUsage {
        val plugin = loadedPlugin(PLUGIN_ID)
            ?: return GeminiUsage(error = "Gemini for Android Studio is not installed or enabled")
        val source = quotaSource(plugin.pluginClassLoader)
        val serviceClass = Class.forName(source.serviceClass, true, plugin.pluginClassLoader)
        val companion = serviceClass.getField("Companion").get(null)
        val service = api.getter(companion, "getInstance")
            ?: return GeminiUsage(plan = source.plan, error = "Gemini quota service is unavailable")
        val flow = api.getter(service, "getQuotaStatus") ?: error("Gemini quota flow is unavailable")
        val values = api.flowValue(flow) as? List<*> ?: error("Unsupported Gemini quota list")
        if (values.isEmpty()) return GeminiUsage(plan = source.plan,
            error = "Gemini has not published model quotas yet. Open Gemini and check its quota display.")
        val models = values.map { value ->
            val model = value ?: error("Unsupported Gemini quota entry")
            val id = (api.getter(model, "getModelId") as? String)?.takeIf { it.isNotBlank() }
                ?: error("Unsupported Gemini model ID")
            val fraction = (api.getter(model, "getRemainingFraction") as? Number)?.toDouble()
                ?: error("Unsupported Gemini remaining fraction")
            if (!fraction.isFinite() || fraction !in 0.0..1.0) error("Invalid Gemini remaining fraction")
            val reset = api.getter(model, "getResetTime")?.toString()?.let {
                runCatching { Instant.parse(it).epochSecond }.getOrNull()
            }
            GeminiModelQuota(id, fraction, reset)
        }
        return GeminiUsage(models = models, plan = source.plan)
    }

    private data class QuotaSource(val serviceClass: String, val plan: String? = null)

    private fun quotaSource(loader: ClassLoader?): QuotaSource {
        val providerClass = try {
            Class.forName("com.android.studio.ml.GeminiUserInfoProvider", true, loader)
        } catch (_: ClassNotFoundException) {
            // Older Gemini versions exposed their model quotas through the original service only.
            return QuotaSource(BUSINESS_SERVICE)
        }
        val companion = providerClass.getField("Companion").get(null)
        val provider = api.getter(companion, "current") ?: error("Gemini user info provider is unavailable")
        val flow = api.getter(provider, "getUserInfo") ?: error("Gemini user info flow is unavailable")
        val userInfo = api.flowValue(flow) ?: error("Gemini user info is unavailable")
        // Read only category and public tier metadata, never authorized-user, identity or credential fields.
        val type = api.getter(userInfo, "getUserType", required = false) as? Enum<*>
        val plan = (api.getter(userInfo, "getTierName", required = false) as? String)?.takeIf { it.isNotBlank() }
            // Matches GeminiUserInfo.Companion.isFreeTier: Consumer with an explicit unpaid-tier flag.
            ?: if (type?.name == "CONSUMER" && api.getter(userInfo, "isPaidTier", required = false) == false) "Free" else null
        if (type == null) return QuotaSource(BUSINESS_SERVICE, plan)
        val serviceClass = when (type.name) {
            "CONSUMER" -> "com.android.studio.ml.backends.aicode.AiCodeQuotaService"
            "BUSINESS" -> BUSINESS_SERVICE
            "ENTERPRISE" -> "com.android.studio.ml.backends.baicode.BaicodeQuotaService"
            else -> error("Unsupported Gemini account category")
        }
        return QuotaSource(serviceClass, plan)
    }

    companion object {
        const val PLUGIN_ID = "com.google.tools.ij.aiplugin"
        private const val BUSINESS_SERVICE = "com.google.tools.intellij.aiplugin.quota.UserQuotaService"
        fun isAvailable(): Boolean = loadedPlugin(PLUGIN_ID) != null
    }
}
