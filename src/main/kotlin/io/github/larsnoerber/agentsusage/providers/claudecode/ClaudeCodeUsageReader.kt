package io.github.larsnoerber.agentsusage.providers.claudecode

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import io.github.larsnoerber.agentsusage.core.storage.LocalUsageFiles

/** Reads only token metadata from Claude Code's local session logs; prompt text is never retained. */
internal class ClaudeCodeUsageReader : AutoCloseable {
    private data class FileState(
        val size: Long,
        val modified: Long,
        val fileKey: Any?,
        val offset: Long,
        val messages: Map<String, ClaudeCodeUsage>
    )

    private val closed = AtomicBoolean()
    private var cachedMonth: YearMonth? = null
    private val cache = mutableMapOf<Path, FileState>()
    private var nextScan = 0L
    private var nextRead = 0L
    private var current = ClaudeCodeUsage()

    fun read(): ClaudeCodeUsage {
        check(!closed.get()) { "Claude Code usage reader is closed" }
        val now = YearMonth.now()
        val monotonic = System.nanoTime()
        if (cachedMonth != now) {
            cache.clear()
            cachedMonth = now
            nextScan = 0
            nextRead = 0
        }
        if (monotonic < nextRead) return current
        nextRead = monotonic + java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
        val root = (LocalUsageFiles.absoluteEnvironment("CLAUDE_CONFIG_DIR") ?: LocalUsageFiles.home.resolve(".claude"))
            .resolve("projects")
        if (!Files.isDirectory(root)) {
            cache.clear()
            current = ClaudeCodeUsage()
            return current
        }
        var incomplete = false
        val paths = cache.keys.toMutableSet()
        if (monotonic >= nextScan) {
            nextScan = monotonic + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
            val cutoff = now.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            try {
                Files.walk(root).use { files ->
                    files.takeWhile { !closed.get() }
                        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jsonl") }
                        .forEach { path ->
                            val normalized = path.toAbsolutePath().normalize()
                            if (normalized in cache || Files.getLastModifiedTime(path).toMillis() >= cutoff) paths.add(normalized)
                        }
                }
            } catch (_: Exception) { incomplete = true }
        }
        paths.forEach { path ->
            if (closed.get()) return@forEach
            try {
                val attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java)
                val old = cache[path]
                if (old == null || old.size != attributes.size() || old.modified != attributes.lastModifiedTime().toMillis() ||
                    old.fileKey != attributes.fileKey()) {
                    cache[path] = parseFile(path, now, attributes, old)
                }
            } catch (_: java.nio.file.NoSuchFileException) {
                cache.remove(path)
            } catch (_: Exception) { incomplete = true }
        }
        current = cache.values.flatMap { it.messages.values }.fold(ClaudeCodeUsage()) { sum, usage -> sum + usage }
            .copy(logsFound = cache.isNotEmpty(), localUpdatedAt = System.currentTimeMillis(),
                error = if (incomplete) "Some local Claude usage files could not be read; totals may be incomplete." else null)
        return current
    }

    private fun parseFile(
        path: Path,
        month: YearMonth,
        attributes: java.nio.file.attribute.BasicFileAttributes,
        previous: FileState?
    ): FileState {
        // Appends reuse numeric per-message results. Truncation/replacement restarts the file.
        val append = previous != null && attributes.fileKey() == previous.fileKey && attributes.size() > previous.size
        val messages = if (append) previous.messages.toMutableMap() else linkedMapOf()
        var offset = if (append) previous.offset else 0L
        Files.newByteChannel(path).use { channel ->
            channel.position(offset)
            java.io.BufferedInputStream(java.nio.channels.Channels.newInputStream(channel)).use { input ->
                val line = java.io.ByteArrayOutputStream()
                var position = offset
                var oversized = false
                while (!closed.get() && position < attributes.size()) {
                    val byte = input.read()
                    if (byte < 0) break
                    position++
                    if (byte == 10) {
                        if (!oversized) parseEntry(line.toString(Charsets.UTF_8), month)?.let { (id, usage) ->
                            messages[id ?: "local-entry-$offset"] = usage
                        }
                        offset = position
                        line.reset()
                        oversized = false
                    } else if (line.size() < 4 * 1024 * 1024) line.write(byte) else oversized = true
                }
                // An unfinished final line is reread on the next append, never counted twice.
            }
        }
        return FileState(attributes.size(), attributes.lastModifiedTime().toMillis(), attributes.fileKey(), offset, messages)
    }

    private fun parseEntry(line: String, month: YearMonth): Pair<String?, ClaudeCodeUsage>? = runCatching {
        val entry = JsonParser.parseString(line).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        if (entry.get("type")?.asString != "assistant") return null
        val date = Instant.parse(entry.get("timestamp")?.asString).atZone(ZoneId.systemDefault())
        if (YearMonth.from(date) != month) return null
        val message = entry.get("message")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val usage = message.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val messageId = message.get("id")?.takeIf { it.isJsonPrimitive }?.asString
        val model = message.get("model")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: "Unknown model"
        val input = usage.long("input_tokens")
        val output = usage.long("output_tokens")
        val cacheRead = usage.long("cache_read_input_tokens")
        val cacheWrite = usage.long("cache_creation_input_tokens")
        messageId to ClaudeCodeUsage(
            requests = 1, inputTokens = input, outputTokens = output, cacheReadTokens = cacheRead, cacheWriteTokens = cacheWrite,
            modelTokens = mapOf(model to (input + output + cacheRead + cacheWrite)), logsFound = true
        )
    }.getOrNull()

    override fun close() {
        closed.set(true)
    }

    private fun JsonObject.long(name: String): Long = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.let { runCatching { it.asLong.coerceAtLeast(0) }.getOrDefault(0) } ?: 0

    private operator fun ClaudeCodeUsage.plus(other: ClaudeCodeUsage): ClaudeCodeUsage = ClaudeCodeUsage(
        requests + other.requests,
        inputTokens + other.inputTokens,
        outputTokens + other.outputTokens,
        cacheReadTokens + other.cacheReadTokens,
        cacheWriteTokens + other.cacheWriteTokens,
        (modelTokens.keys + other.modelTokens.keys).associateWith { (modelTokens[it] ?: 0) + (other.modelTokens[it] ?: 0) },
        logsFound || other.logsFound
    )
}
