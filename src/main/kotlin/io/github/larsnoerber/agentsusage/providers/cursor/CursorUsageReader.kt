package io.github.larsnoerber.agentsusage.providers.cursor

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

import com.google.gson.JsonParser
import io.github.larsnoerber.agentsusage.core.http.UsageHttp
import io.github.larsnoerber.agentsusage.core.http.UsageHttpFailure
import io.github.larsnoerber.agentsusage.core.storage.LocalUsageFiles
import io.github.larsnoerber.agentsusage.core.storage.numberValue
import io.github.larsnoerber.agentsusage.core.storage.objectValue
import io.github.larsnoerber.agentsusage.core.storage.stringValue
import java.time.Instant
import java.util.Base64
import java.nio.file.Files
import kotlin.math.roundToInt

internal class CursorUsageReader : AutoCloseable {
    private val http = UsageHttp()

    companion object {
        private fun agentAuthPath() = when {
            System.getProperty("os.name").startsWith("Windows", true) -> LocalUsageFiles.configRoot("Cursor").resolve("auth.json")
            System.getProperty("os.name").startsWith("Mac", true) -> LocalUsageFiles.home.resolve(".cursor/auth.json")
            else -> (LocalUsageFiles.absoluteEnvironment("XDG_CONFIG_HOME") ?: LocalUsageFiles.home.resolve(".config"))
                .resolve("cursor/auth.json")
        }

        fun hasLocalAccount(): Boolean {
            val agentAuth = agentAuthPath()
            return Files.isRegularFile(agentAuth)
        }
    }

    fun read(): CursorUsage {
        return try {
        val agentAuth = agentAuthPath()
        val token = (if (Files.isRegularFile(agentAuth)) LocalUsageFiles.json(agentAuth).stringValue("accessToken") else null)
            ?: return CursorUsage(problem = UsageProblem.AUTHENTICATION, error = "Sign in to the Cursor ACP agent to read usage.")
        val parts = token.split('.')
        if (parts.size != 3) return CursorUsage(problem = UsageProblem.AUTHENTICATION, error = "Cursor session is unavailable. Sign in again in Cursor.")
        val payload = JsonParser.parseString(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).asJsonObject
        val user = payload.stringValue("sub")?.substringAfterLast('|')
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
            ?: return CursorUsage(error = "Cursor session format is unavailable.")
        val expires = payload.numberValue("exp")
        if (expires == null || expires <= Instant.now().epochSecond + 60)
            return CursorUsage(problem = UsageProblem.AUTHENTICATION, error = "Cursor session expired. Sign in again to the Cursor ACP agent.")
        val response = try {
            http.get("https://cursor.com/api/usage-summary", mapOf(
                "Cookie" to "WorkosCursorSessionToken=$user%3A%3A$token", "Origin" to "https://cursor.com",
                "Referer" to "https://cursor.com/dashboard"))
        } catch (failure: UsageHttpFailure) {
            if (failure.status != 403) throw failure
            // Cursor Agent's authenticated usage endpoint can work when the web dashboard is blocked.
            http.post("https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage", mapOf(
                "Authorization" to "Bearer $token", "Connect-Protocol-Version" to "1"))
        }
        val individual = response.objectValue("individualUsage")
        val plan = individual?.objectValue("plan") ?: response.objectValue("planUsage")
        val overall = individual?.objectValue("overall")
        val pooled = response.objectValue("teamUsage")?.objectValue("pooled")
        val quota = listOfNotNull(plan, overall, pooled).firstOrNull {
            it.numberValue("totalPercentUsed") != null ||
                (it.numberValue("limit")?.let { limit -> limit > 0 } == true &&
                    (it.numberValue("used") != null || it.numberValue("remaining") != null))
        }
        val limit = quota?.numberValue("limit")
        val used = quota?.numberValue("used") ?: quota?.numberValue("remaining")?.let { remaining ->
            limit?.let { (it - remaining).coerceAtLeast(0.0) }
        }
        val percent = quota?.numberValue("totalPercentUsed")
            ?: if (used != null && limit != null && limit > 0) used * 100 / limit else null
        CursorUsage(
            percentUsed = percent?.coerceIn(0.0, 100.0)?.roundToInt(),
            autoPercentUsed = plan?.numberValue("autoPercentUsed")?.coerceIn(0.0, 100.0)?.roundToInt(),
            apiPercentUsed = plan?.numberValue("apiPercentUsed")?.coerceIn(0.0, 100.0)?.roundToInt(),
            usedUsd = used?.div(100), limitUsd = limit?.div(100),
            onDemandUsd = individual?.objectValue("onDemand")?.numberValue("used")?.div(100),
            resetsAt = response.stringValue("billingCycleEnd")?.let {
                it.toLongOrNull()?.div(1000) ?: runCatching { Instant.parse(it).epochSecond }.getOrNull()
            },
            plan = response.stringValue("membershipType"),
            quotaScope = if (quota != null && quota === pooled) "Shared team pool" else "Included plan",
            updatedAt = System.currentTimeMillis(),
            error = if (percent == null) "Cursor did not report a finite quota for this plan." else null
        )
    } catch (failure: UsageHttpFailure) {
        CursorUsage(problem = if (failure.status == 401 || failure.status == 403) UsageProblem.AUTHENTICATION else UsageProblem.UNAVAILABLE,
            error = when (failure.status) {
            401, 403 -> "Cursor rejected the session. Sign in again in Cursor."
            429 -> "Cursor usage is rate limited. Try again later."
            else -> "Cursor quota request failed (HTTP ${failure.status})."
        })
    } catch (_: Exception) {
        CursorUsage(error = "Cursor usage unavailable. Check the local sign-in and connection.")
    }
    }

    override fun close() = http.close()
}
