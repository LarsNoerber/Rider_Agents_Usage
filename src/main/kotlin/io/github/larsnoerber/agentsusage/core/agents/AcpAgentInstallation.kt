package io.github.larsnoerber.agentsusage.core.agents

import com.intellij.openapi.application.PathManager
import com.intellij.util.text.VersionComparatorUtil
import java.nio.file.Files
import java.nio.file.Path

/** Detects ACP registry agents installed by the IDE without reading their authentication data. */
object AcpAgentInstallation {
    private val agentIdPattern = Regex("[a-zA-Z0-9._-]+")

    /** Resolve a package-owned file, preferring the newest installed version. */
    fun installedFile(agentId: String, relativePath: String): Path? {
        if (!agentId.matches(agentIdPattern)) return null
        val root = Path.of(PathManager.getSystemPath(), "acp-agents", agentId)
        return runCatching {
            Files.list(root).use { versions ->
                versions.filter(Files::isDirectory).toList()
                    .sortedWith { left, right -> VersionComparatorUtil.compare(right.fileName.toString(), left.fileName.toString()) }
                    .map { it.resolve(relativePath).normalize() }
                    .firstOrNull { it.startsWith(root) && Files.isRegularFile(it) }
            }
        }.getOrNull()
    }

    fun isInstalled(agentId: String): Boolean {
        if (!agentId.matches(agentIdPattern)) return false
        val versions = Path.of(PathManager.getSystemPath(), "acp-agents", agentId)
        if (!Files.isDirectory(versions)) return false
        return runCatching {
            Files.list(versions).use { entries -> entries.anyMatch(Files::isDirectory) }
        }.getOrDefault(false)
    }
}
