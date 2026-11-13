package io.github.larsnoerber.agentsusage

/** Reads the shared version embedded by processResources, without querying IDE internals. */
object PluginVersion {
    val current: String by lazy {
        PluginVersion::class.java.getResourceAsStream("/agentmeter-version.txt")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText().trim() }
            ?.takeIf { it.isNotEmpty() } ?: "Unknown"
    }
}
