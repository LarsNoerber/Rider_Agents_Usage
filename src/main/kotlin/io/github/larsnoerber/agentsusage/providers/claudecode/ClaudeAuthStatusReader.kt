package io.github.larsnoerber.agentsusage.providers.claudecode

import io.github.larsnoerber.agentsusage.core.refresh.UsageProblem

import com.google.gson.JsonParser
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.larsnoerber.agentsusage.core.agents.AcpAgentInstallation
import io.github.larsnoerber.agentsusage.core.storage.stringValue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** The official CLI reports authentication mode without exposing credentials to this plugin. */
internal class ClaudeAuthStatusReader : AutoCloseable {
    @Volatile private var closed = false
    private val active = AtomicReference<Process?>()

    fun read(): ClaudeCodeUsage {
        val unavailable = ClaudeCodeUsage(quotaError = "Claude subscription credentials are unavailable in the local store.")
        if (closed) return unavailable
        val os = System.getProperty("os.name").lowercase()
        val platform = when {
            os.startsWith("windows") -> "win32"
            os.startsWith("mac") -> "darwin"
            else -> "linux"
        }
        val arch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "x64"
        val executable = AcpAgentInstallation.installedFile("claude-acp",
            "node_modules/@anthropic-ai/claude-agent-sdk-$platform-$arch/${if (platform == "win32") "claude.exe" else "claude"}")
            ?: return unavailable
        return try {
            val process = ProcessBuilder(executable.toString(), "auth", "status", "--json")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            active.set(process)
            val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule({ stop(process) }, 10, TimeUnit.SECONDS)
            try {
                if (closed) return unavailable
                val bytes = process.inputStream.use { it.readNBytes(65_537) }
                if (bytes.size > 65_536 || !process.waitFor(1, TimeUnit.SECONDS)) return unavailable
                val status = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
                val loggedIn = status.get("loggedIn")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                when {
                    loggedIn && status.stringValue("authMethod") == "api_key" -> ClaudeCodeUsage(
                        plan = "Anthropic API", updatedAt = System.currentTimeMillis(),
                        quotaError = "Signed in with an API key; Claude Pro/Max subscription quotas do not apply to this login.")
                    loggedIn -> unavailable.copy(plan = status.stringValue("subscriptionType"),
                        quotaError = "Claude is signed in, but subscription quota credentials are not readable in the local store.")
                    else -> ClaudeCodeUsage(problem = UsageProblem.AUTHENTICATION, quotaError = "Sign in to the Claude ACP agent or Claude Code to load usage.")
                }
            } finally {
                timeout.cancel(false)
                active.compareAndSet(process, null)
                stop(process)
            }
        } catch (_: Exception) { unavailable }
    }

    private fun stop(process: Process) {
        runCatching { process.descendants().use { it.forEach { child -> child.destroyForcibly() } } }
        process.destroyForcibly()
    }

    override fun close() {
        closed = true
        active.getAndSet(null)?.let(::stop)
    }
}
