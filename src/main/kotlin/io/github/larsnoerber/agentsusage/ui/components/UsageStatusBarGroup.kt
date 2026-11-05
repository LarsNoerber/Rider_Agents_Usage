package io.github.larsnoerber.agentsusage.ui.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import java.awt.GridBagLayout

/** Keep the existing independent widget IDs while placing visible agents in one contiguous group. */
internal object UsageStatusBarGroup {
    private val ids = listOf("CodexUsageStatusBar", "JetBrainsAiCreditsStatusBar", "GitHubCopilotUsageStatusBar",
        "ClaudeCodeUsageStatusBar", "CursorUsageStatusBar", "GeminiUsageStatusBar")

    fun schedule(statusBar: StatusBar) {
        // Widget installation/removal and the IDE's own sorting finish before we adjust the layout.
        ApplicationManager.getApplication().invokeLater {
            if (statusBar.project?.isDisposed == true) return@invokeLater
            val agents = ids.mapNotNull { id ->
                (statusBar.getWidget(id) as? CustomStatusBarWidget)?.component
            }
            if (agents.size < 2) return@invokeLater
            val parent = agents.first().parent ?: return@invokeLater
            if (agents.any { it.parent !== parent }) return@invokeLater
            val layout = parent.layout as? GridBagLayout ?: return@invokeLater
            val current = parent.components.sortedBy { layout.getConstraints(it).gridx }
            val first = current.indexOfFirst { it in agents }
            if (first < 0) return@invokeLater
            val ordered = current.filterNot { it in agents }.toMutableList().apply { addAll(first, agents) }
            if (current == ordered) return@invokeLater
            ordered.forEachIndexed { index, component ->
                val constraints = layout.getConstraints(component)
                constraints.gridx = index
                layout.setConstraints(component, constraints)
            }
            parent.revalidate()
            parent.repaint()
        }
    }
}
