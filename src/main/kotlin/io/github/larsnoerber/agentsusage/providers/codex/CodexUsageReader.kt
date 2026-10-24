package io.github.larsnoerber.agentsusage.providers.codex

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Owns the CLI process so timeout and plugin disposal also stop its child processes. */
internal class CodexUsageReader : Disposable {
    @Volatile private var disposed = false
    @Volatile private var detectedPath: String? = null
    private val activeProcess = AtomicReference<Process?>()

    fun read(configuredPath: String): CodexUsage {
        check(!disposed) { "Codex usage reader is disposed" }
        val path = resolvePath(configuredPath)
            ?: error("Codex CLI was not found. Install and sign in to Codex CLI, or specify its path in Settings > Tools > AgentMeter.")
        val command = if (path.endsWith(".exe", ignoreCase = true)) {
            ProcessBuilder(path, "app-server", "--stdio")
        } else {
            ProcessBuilder("cmd.exe", "/d", "/c", "\"$path\" app-server --stdio")
        }
        val process = command.redirectError(ProcessBuilder.Redirect.DISCARD).start()
        activeProcess.set(process)
        val timedOut = AtomicBoolean()
        var timeout: ScheduledFuture<*>? = null
        try {
            if (disposed) {
                stopProcess(process)
                error("Codex usage reader is disposed")
            }
            timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule({
                timedOut.set(true)
                stopProcess(process)
            }, 30, TimeUnit.SECONDS)
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                process.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    val clientInfo = JsonObject().apply {
                        addProperty("name", "agents-usage")
                        addProperty("version", "1.2.1")
                    }
                    request(reader, writer, 1, "initialize", JsonObject().apply { add("clientInfo", clientInfo) })
                    writer.write("""{"method":"initialized"}""")
                    writer.newLine()
                    writer.flush()
                    val account = try {
                        request(reader, writer, 2, "account/read", JsonObject().apply { addProperty("refreshToken", false) })
                            .objectValue("result")?.objectValue("account")
                    } catch (_: CodexProtocolException) {
                        null
                    }
                    val plan = account?.stringValue("planType")
                        ?: if (account?.stringValue("type") == "apiKey") "API key" else null
                    return try {
                        val response = request(reader, writer, 3, "account/rateLimits/read")
                        val usage = CodexUsageParser.parseRateLimits(response)
                        usage.copy(plan = usage.plan ?: plan)
                    } catch (e: CodexProtocolException) {
                        CodexUsage(plan = plan, error = e.message)
                    }
                }
            }
        } catch (e: Exception) {
            if (timedOut.get()) error("Codex CLI did not respond within 30 seconds")
            throw e
        } finally {
            timeout?.cancel(false)
            activeProcess.compareAndSet(process, null)
            stopProcess(process)
        }
    }

    fun detectCodexPath(): String? {
        if (disposed) return null
        val process = ProcessBuilder("where.exe", "codex")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) return null
            val paths = process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.map(String::trim).filter(String::isNotEmpty).toList()
            }
            return paths.firstOrNull { it.endsWith(".cmd", ignoreCase = true) } ?: paths.firstOrNull()
        } finally {
            stopProcess(process)
        }
    }

    private fun resolvePath(configuredPath: String): String? {
        val configured = configuredPath.trim()
        if (configured.isNotEmpty()) {
            val looksLikePath = configured.contains('\\') || configured.contains('/')
            if (!looksLikePath || File(configured).isFile) return configured
        }
        detectedPath?.takeIf { File(it).isFile }?.let { return it }
        return detectCodexPath().also { detectedPath = it }
    }

    private fun request(
        reader: BufferedReader,
        writer: BufferedWriter,
        id: Int,
        method: String,
        params: JsonObject? = null
    ): JsonObject {
        val message = JsonObject().apply {
            addProperty("id", id)
            addProperty("method", method)
            add("params", params ?: JsonNull.INSTANCE)
        }
        writer.write(message.toString())
        writer.newLine()
        writer.flush()
        while (true) {
            val line = reader.readLine() ?: error("Codex app-server closed the connection")
            val response = try {
                JsonParser.parseString(line).takeIf { it.isJsonObject }?.asJsonObject
            } catch (_: JsonParseException) {
                null
            } ?: continue
            if (response.stringValue("id") != id.toString()) continue
            response.objectValue("error")?.let {
                throw CodexProtocolException(it.stringValue("message") ?: "Codex CLI request failed")
            }
            return response
        }
    }

    private fun stopProcess(process: Process) {
        try {
            process.descendants().use { descendants ->
                descendants.toList().asReversed().forEach { it.destroyForcibly() }
            }
        } finally {
            process.destroyForcibly()
        }
    }

    override fun dispose() {
        disposed = true
        activeProcess.getAndSet(null)?.let(::stopProcess)
    }
}

private class CodexProtocolException(message: String) : IllegalStateException(message)
