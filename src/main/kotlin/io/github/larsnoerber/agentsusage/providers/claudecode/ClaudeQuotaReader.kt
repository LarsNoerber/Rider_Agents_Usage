package io.github.larsnoerber.agentsusage.providers.claudecode

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

import io.github.larsnoerber.agentsusage.core.http.UsageHttp
import io.github.larsnoerber.agentsusage.core.http.UsageHttpFailure
import io.github.larsnoerber.agentsusage.core.storage.LocalUsageFiles
import io.github.larsnoerber.agentsusage.core.storage.numberValue
import io.github.larsnoerber.agentsusage.core.storage.objectValue
import io.github.larsnoerber.agentsusage.core.storage.stringValue
import java.nio.file.Files
import java.time.Instant
import kotlin.math.roundToInt

internal class ClaudeQuotaReader : AutoCloseable {
    private val http = UsageHttp()
    private val authStatus = ClaudeAuthStatusReader()

    fun read(): ClaudeCodeUsage {
        return try {
            val directory = LocalUsageFiles.absoluteEnvironment("CLAUDE_CONFIG_DIR") ?: LocalUsageFiles.home.resolve(".claude")
            val credentialsFile = directory.resolve(".credentials.json")
            if (!Files.isRegularFile(credentialsFile)) return authStatus.read()
            val oauth = LocalUsageFiles.json(credentialsFile).objectValue("claudeAiOauth")
                ?: return authStatus.read()
            val token = oauth.stringValue("accessToken")
                ?: return ClaudeCodeUsage(problem = UsageProblem.AUTHENTICATION, quotaError = "Claude subscription sign-in unavailable.")
            if (oauth.numberValue("expiresAt")?.let { it <= System.currentTimeMillis() } == true)
                return ClaudeCodeUsage(problem = UsageProblem.AUTHENTICATION, quotaError = "Claude session expired. Open Claude Code to renew your sign-in.")
            val response = http.get("https://api.anthropic.com/api/oauth/usage", mapOf(
                "Authorization" to "Bearer $token", "anthropic-beta" to "oauth-2025-04-20",
                "User-Agent" to "claude-code/2.1.131", "Content-Type" to "application/json"))
            val quotas = listOf("five_hour" to "Session", "seven_day" to "Weekly",
                "seven_day_sonnet" to "Sonnet", "seven_day_opus" to "Opus").mapNotNull { (key, title) ->
                val window = response.objectValue(key) ?: return@mapNotNull null
                val utilization = window.numberValue("utilization") ?: return@mapNotNull null
                ClaudeQuota(title, utilization.coerceIn(0.0, 100.0).roundToInt(), window.stringValue("resets_at")
                    ?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() })
            }
            ClaudeCodeUsage(quotas = quotas, plan = oauth.stringValue("subscriptionType"),
                updatedAt = System.currentTimeMillis(),
                quotaError = if (quotas.isEmpty()) "Claude did not report subscription quotas." else null)
        } catch (failure: UsageHttpFailure) {
            ClaudeCodeUsage(problem = if (failure.status == 401 || failure.status == 403) UsageProblem.AUTHENTICATION else UsageProblem.UNAVAILABLE,
            quotaError = when (failure.status) {
                401, 403 -> "Claude rejected the session. Sign in again with Claude Code."
                429 -> "Claude usage is rate limited. Try again later."
                else -> "Claude quota request failed (HTTP ${failure.status})."
            })
        } catch (_: Exception) {
            ClaudeCodeUsage(quotaError = "Claude quota unavailable. Check your local sign-in and connection.")
        }
    }

    override fun close() { http.close(); authStatus.close() }
}
