package io.github.larsnoerber.agentsusage.providers.gemini.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.larsnoerber.agentsusage.providers.gemini.GeminiUsage
import io.github.larsnoerber.agentsusage.providers.gemini.GeminiUsageService
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import io.github.larsnoerber.agentsusage.ui.components.secondaryTextColor
import java.awt.event.HierarchyEvent
import javax.swing.BoxLayout
import javax.swing.JPanel

/** Edits status visibility only; the provider selection continues to control quota reads. */
internal class GeminiStatusModelSelectionPanel : JPanel(), Disposable {
    private val all = JBCheckBox("All models in status bar (including new models)")
    private val choices = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false }
    private val selected = mutableSetOf<String>()
    private var models = emptyList<String>()
    private var service: GeminiUsageService? = null
    private var disposed = false
    private val listener: (GeminiUsage) -> Unit = { if (!disposed && service != null) updateModels(it) }

    val allModels: Boolean get() = all.isSelected
    val selectedModels: List<String> get() = selected.sorted()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(0, 24, 4, 0)
        all.isOpaque = false
        add(all)
        add(choices)
        all.addActionListener {
            if (!all.isSelected && selected.isEmpty()) selected.addAll(models)
            renderChoices()
        }
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                if (isShowing) subscribe() else unsubscribe()
            }
        }
        reset()
    }

    fun reset() {
        val state = AgentsUsageSettings.getInstance().state
        all.isSelected = state.geminiStatusAllModels
        selected.clear()
        selected.addAll(state.geminiStatusModels)
        renderChoices()
    }

    fun isModified(): Boolean = AgentsUsageSettings.getInstance().state.let {
        allModels != it.geminiStatusAllModels || selectedModels != it.geminiStatusModels.distinct().sorted()
    }

    private fun subscribe() {
        if (disposed || service != null || !GeminiUsageService.isAvailable()) return
        service = GeminiUsageService.getInstance().also {
            it.addListener(listener)
            updateModels(it.current)
        }
    }

    private fun unsubscribe() {
        service?.removeListener(listener)
        service = null
    }

    private fun updateModels(usage: GeminiUsage) {
        val ids = usage.models.map { it.modelId }.distinct()
        if (ids != models) {
            models = ids
            renderChoices()
        }
    }

    private fun renderChoices() {
        choices.removeAll()
        (models + selected.sorted()).distinct().forEach { id ->
            choices.add(JBCheckBox(id, allModels || id in selected).apply {
                isOpaque = false
                isEnabled = !allModels
                addActionListener { if (isSelected) selected.add(id) else selected.remove(id) }
            })
        }
        if (models.isEmpty()) choices.add(JBLabel("Model choices appear after Gemini publishes quota data.").apply {
            foreground = secondaryTextColor()
        })
        revalidate()
        repaint()
    }

    override fun dispose() {
        disposed = true
        unsubscribe()
    }
}
