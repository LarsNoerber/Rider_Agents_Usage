package io.github.larsnoerber.agentsusage.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.larsnoerber.agentsusage.application.UsageInsightsService
import io.github.larsnoerber.agentsusage.application.UsageBossHit
import io.github.larsnoerber.agentsusage.application.UsageInsightsSnapshot
import io.github.larsnoerber.agentsusage.application.UsagePartyMember
import io.github.larsnoerber.agentsusage.ui.components.secondaryTextColor
import io.github.larsnoerber.agentsusage.ui.components.usageBarColor
import io.github.larsnoerber.agentsusage.settings.AgentsUsageSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer

/** Compact, collapsible recap of locally observed quota changes. */
internal class WeeklyUsageInsightsPanel : JPanel(BorderLayout(0, 4)), Disposable {
    // Component construction precedes listener registration because it delivers an initial snapshot.
    private val service = UsageInsightsService.getInstance()
    private val settings = AgentsUsageSettings.getInstance()
    private val toggle = JButton("WEEKLY QUESTS  ▾")
    private val body = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val party = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(5), JBUI.scale(2))).apply {
        isOpaque = false
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }
    private val forecast = JBLabel().apply { setAllowAutoWrapping(true) }
    private val footer = JBLabel().apply {
        setAllowAutoWrapping(true)
        foreground = secondaryTextColor()
        font = font.deriveFont(font.size2D - 1f)
    }
    private val combatLog = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }
    private val bossStatus = JBLabel().apply {
        setAllowAutoWrapping(true)
        foreground = JBColor(Color(91, 57, 145), Color(218, 190, 255))
        font = font.deriveFont(font.style or java.awt.Font.BOLD, 11f)
    }
    private val bossHp = JBLabel("100%").apply {
        foreground = secondaryTextColor()
        font = font.deriveFont(java.awt.Font.BOLD, 10f)
    }
    private val bossQuote = JBLabel().apply {
        setAllowAutoWrapping(true)
        foreground = secondaryTextColor()
        font = font.deriveFont(java.awt.Font.ITALIC, font.size2D - 1f)
    }
    private val bossIcon = QuotaBossIcon()
    private val hitFeedback = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val bossBackground = JBColor(Color(244, 238, 252), Color(48, 40, 59))
    private val boss = JProgressBar(0, 100).apply {
        isStringPainted = false
        preferredSize = JBUI.size(64, 16)
        minimumSize = JBUI.size(24, 14)
        accessibleContext.accessibleName = "Quota Wraith health"
    }
    private val bossBattle = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
        isOpaque = true
        background = bossBackground
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(211, 194, 233), Color(91, 73, 111))),
            JBUI.Borders.empty(6)
        )
        add(JBLabel(bossIcon), BorderLayout.WEST)
        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(bossStatus, BorderLayout.WEST)
                add(bossHp, BorderLayout.EAST)
            })
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(boss)
            add(Box.createVerticalStrut(JBUI.scale(3)))
            add(bossQuote)
            add(hitFeedback)
        }, BorderLayout.CENTER)
    }
    private val celebration = JBLabel(" ").apply {
        setAllowAutoWrapping(true)
        foreground = JBColor(Color(45, 105, 67), Color(162, 231, 180))
        isOpaque = true
        background = JBColor(Color(229, 247, 232), Color(46, 69, 50))
        border = JBUI.Borders.empty(2, 6)
    }
    private var expanded = settings.state.weeklyInsightsExpanded
    private var disposed = false
    private var lastCelebrationId = 0L
    private var resetGlowProvider: String? = null
    private var resetGlowUntil = 0L
    private var lastSnapshot: UsageInsightsSnapshot? = null
    private var forecastMessage = ""
    private val footerMessage = "Quota changes weaken the boss. New challenger each week."
    private var battleEvents = emptyList<String>()
    private val battleEventLabels = mutableListOf<JBLabel>()
    private var battleEventWidth = -1
    private data class ActiveHit(val hit: UsageBossHit, val label: JBLabel)
    private val activeHits = mutableListOf<ActiveHit>()
    private var lastSeenHitId = 0L
    private val hitTimer = Timer(40) { updateHitAnimation() }
    private val expiryTimer = Timer(UsageInsightsService.CELEBRATION_MILLIS.toInt()) {
        celebration.text = " "
        celebration.isVisible = false
        resetGlowProvider = null
        resetGlowUntil = 0L
        lastSnapshot?.let { render(it.copy(celebration = null)) }
    }.apply { isRepeats = false }
    private val listener: (UsageInsightsSnapshot) -> Unit = ::render

    init {
        isOpaque = true
        background = JBColor(Color(250, 250, 252), Color(43, 45, 50))
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            JBUI.Borders.empty(8)
        )
        add(JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(toggle.apply {
                horizontalAlignment = JButton.LEFT
                isContentAreaFilled = false
                isBorderPainted = false
                isFocusPainted = false
                foreground = JBColor(Color(96, 63, 139), Color(209, 181, 242))
                font = font.deriveFont(java.awt.Font.BOLD, 12f)
                margin = JBUI.emptyInsets()
                toolTipText = "Show or hide the weekly quest"
                addActionListener { setExpanded(!expanded) }
            })
            add(celebration)
        }, BorderLayout.NORTH)
        addBodyRow(JBLabel("YOUR AI PARTY").apply { setSectionHeadingStyle() })
        addBodyRow(party)
        addBodyRow(forecast)
        addBodyRow(bossBattle, bottomGap = 5)
        addBodyRow(JBLabel("LATEST BATTLE EVENTS").apply { setSectionHeadingStyle() })
        addBodyRow(combatLog, bottomGap = 5)
        addBodyRow(footer, bottomGap = 0)
        add(body, BorderLayout.CENTER)
        setExpanded(expanded)
        var lastLayoutWidth = -1
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                if (lastLayoutWidth != width) {
                    lastLayoutWidth = width
                    body.revalidate()
                    party.revalidate()
                    combatLog.revalidate()
                    updateWrappedText()
                }
            }
        })
        combatLog.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) = updateBattleEventWidths()
        })
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                if (isShowing) {
                    service.overviewOpened()
                    updateHitAnimation()
                } else hitTimer.stop()
            }
        }
        service.addListener(listener)
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        settings.state.weeklyInsightsExpanded = value
        body.isVisible = value
        updateHitAnimation()
        toggle.text = if (value) "WEEKLY QUESTS  ▾" else "WEEKLY QUESTS  ▸"
        revalidate()
    }

    fun refreshView() = service.refreshView()

    private fun addBodyRow(component: JComponent, bottomGap: Int = 5) {
        component.alignmentX = LEFT_ALIGNMENT
        component.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        body.add(component)
        if (bottomGap > 0) body.add(Box.createVerticalStrut(JBUI.scale(bottomGap)))
    }

    private fun JBLabel.setSectionHeadingStyle() {
        foreground = secondaryTextColor()
        font = font.deriveFont(java.awt.Font.BOLD, 10f)
    }

    private fun render(snapshot: UsageInsightsSnapshot) {
        if (disposed) return
        lastSnapshot = snapshot
        snapshot.celebration?.let { provider ->
            if (snapshot.celebrationId != lastCelebrationId) {
                lastCelebrationId = snapshot.celebrationId
                celebration.text = "✦ $provider recharged · shield restored"
                celebration.isVisible = true
                resetGlowProvider = provider
                resetGlowUntil = System.currentTimeMillis() + RESET_GLOW_MILLIS
                expiryTimer.restart()
            }
        }
        party.removeAll()
        snapshot.party.forEach { member -> party.add(partyChip(member)) }
        if (snapshot.party.isEmpty()) {
            party.add(JBLabel("No selected providers are reporting quota yet.").apply {
                setAllowAutoWrapping(true)
                foreground = secondaryTextColor()
            })
        }

        val estimate = snapshot.forecast
        forecastMessage = estimate?.let {
            if (it.hoursUntilLow <= 0.0) "Forecast  ·  ${it.provider} near warning level"
            else "Forecast  ·  ${it.provider} may reach 20% in ${formatDuration(it.hoursUntilLow)}"
        } ?: "Forecast  ·  Learning quota use"
        updateWrappedText()
        forecast.foreground = secondaryTextColor()
        forecast.toolTipText = "Local estimate from observed quota changes. Shown after at least one hour and 3 percentage points of change; resets before the estimate are excluded."

        // Render persisted battle state; repainting must never record additional damage.
        val health = 100 - snapshot.bossDamage
        boss.value = health
        boss.foreground = bossAccent(snapshot.bossVariant)
        bossIcon.variant = snapshot.bossVariant
        bossIcon.accent = bossAccent(snapshot.bossVariant)
        bossIcon.phase = when {
            snapshot.bossDamage >= 75 -> 3
            snapshot.bossDamage >= 50 -> 2
            snapshot.bossDamage >= 25 -> 1
            else -> 0
        }
        bossStatus.text = "<html>${snapshot.bossName.uppercase()}<br>${snapshot.bossPhase.uppercase()}</html>"
        bossStatus.foreground = bossAccent(snapshot.bossVariant)
        bossStatus.toolTipText = "Each observed percentage point of quota use deals 4 HP damage. Selected quota providers contribute together. A different challenger appears each week."
        bossHp.text = if (health == 0) "DEFEATED" else "$health% HP"
        bossQuote.text = "<html>\"${snapshot.bossLine}\"</html>"
        bossIconLabelRepaint()
        showHits(snapshot.hits)

        combatLog.removeAll()
        battleEvents = snapshot.battleLog.take(3)
        battleEventLabels.clear()
        battleEvents.forEachIndexed { index, _ ->
            val label = JBLabel().apply {
                alignmentX = LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
                foreground = if (index == 0) bossAccent(snapshot.bossVariant) else secondaryTextColor()
                font = font.deriveFont(if (index == 0) java.awt.Font.BOLD else java.awt.Font.PLAIN, font.size2D)
            }
            battleEventLabels.add(label)
            combatLog.add(label)
        }
        battleEventWidth = -1
        updateBattleEventWidths()

        party.revalidate()
        combatLog.revalidate()
        revalidate()
        repaint()
    }

    private fun updateBattleEventWidths() {
        val width = combatLog.width.takeIf { it > 0 } ?: return
        if (width == battleEventWidth) return
        battleEventWidth = width
        battleEventLabels.forEachIndexed { index, label ->
            val marker = if (index == 0) "\u2726" else "\u00B7"
            setWrappedText(label, "$marker  ${battleEvents[index]}", width)
        }
        combatLog.revalidate()
        body.revalidate()
    }

    private fun updateWrappedText() {
        setWrappedText(forecast, forecastMessage)
        setWrappedText(footer, footerMessage)
        updateBattleEventWidths()
        activeHits.forEach { setWrappedText(it.label, hitText(it.hit)) }
    }

    private fun hitText(hit: UsageBossHit): String = "Hit by ${hit.provider} · ${hit.points} Points"

    private fun showHits(hits: List<UsageBossHit>) {
        // Snapshot delivery may repeat hits; IDs prevent replaying their transient animation.
        val now = System.currentTimeMillis()
        val removed = activeHits.filter { active -> hits.none { it.id == active.hit.id } }
        removed.forEach { hitFeedback.remove(it.label) }
        activeHits.removeAll(removed.toSet())
        hits.filter { it.id > lastSeenHitId }.forEach { hit ->
            lastSeenHitId = maxOf(lastSeenHitId, hit.id)
            if (now - hit.observedAt !in 0 until HIT_DISPLAY_MILLIS) return@forEach
            val label = JBLabel().apply {
                alignmentX = LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
                border = JBUI.Borders.emptyTop(3)
                font = font.deriveFont(java.awt.Font.BOLD)
                toolTipText = hitText(hit)
            }
            setWrappedText(label, hitText(hit))
            activeHits.add(ActiveHit(hit, label))
            hitFeedback.add(label)
            if (activeHits.size > 3) hitFeedback.remove(activeHits.removeAt(0).label)
        }
        hitFeedback.revalidate()
        updateHitAnimation()
    }

    private fun updateHitAnimation() {
        if (disposed) return
        val now = System.currentTimeMillis()
        val expired = activeHits.filter { now - it.hit.observedAt >= HIT_DISPLAY_MILLIS }
        expired.forEach { hitFeedback.remove(it.label) }
        if (expired.isNotEmpty()) {
            activeHits.removeAll(expired.toSet())
            hitFeedback.revalidate()
            body.revalidate()
        }
        activeHits.forEach { active ->
            val elapsed = (now - active.hit.observedAt).coerceAtLeast(0)
            val fade = if (elapsed < HIT_DISPLAY_MILLIS - 600) 0.0
                else (elapsed - (HIT_DISPLAY_MILLIS - 600)) / 600.0
            active.label.foreground = blend(providerAccent(active.hit.provider), bossBackground, fade)
        }
        val latest = activeHits.lastOrNull()?.hit
        val age = latest?.let { (now - it.observedAt).coerceAtLeast(0) } ?: HIT_IMPACT_MILLIS
        val impact = (1.0 - age.toDouble() / HIT_IMPACT_MILLIS).coerceIn(0.0, 1.0)
        val color = latest?.let { providerAccent(it.provider) } ?: bossBackground
        bossIcon.impact = impact
        bossIcon.impactColor = color
        bossBattle.background = blend(bossBackground, color, impact * 0.25)
        bossBattle.repaint()
        // Hidden/collapsed panels retain state without running a repaint timer.
        if (activeHits.isNotEmpty() && expanded && isShowing) hitTimer.start() else hitTimer.stop()
    }

    private fun blend(from: Color, to: Color, amount: Double): Color {
        val value = amount.coerceIn(0.0, 1.0)
        fun channel(left: Int, right: Int): Int = (left + (right - left) * value).toInt()
        return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue))
    }

    private fun setWrappedText(label: JBLabel, text: String, availableWidth: Int? = null) {
        // Measure plain text first, then escape each line before passing it to Swing's HTML renderer.
        if (text.isEmpty()) {
            label.text = ""
            return
        }
        val labelWidth = label.width.takeIf { it > 0 } ?: label.parent?.width ?: 0
        val width = (availableWidth ?: labelWidth) - JBUI.scale(6)
        if (width <= 0) {
            label.text = "<html>${StringUtil.escapeXmlEntities(text)}</html>"
            return
        }
        val metrics = label.getFontMetrics(label.font)
        val lines = mutableListOf<String>()
        var line = ""
        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && metrics.stringWidth(candidate) > width) {
                lines += line
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) lines += line
        label.text = lines.joinToString("<br>", "<html>", "</html>") {
            StringUtil.escapeXmlEntities(it)
        }
    }

    private fun partyChip(member: UsagePartyMember): JPanel {
        val accent = providerAccent(member.provider)
        val glowing = resetGlowProvider == member.provider && System.currentTimeMillis() < resetGlowUntil
        val remaining = when {
            member.unlimited -> "∞"
            member.percentLeft != null -> "${member.percentLeft}%"
            else -> "—"
        }
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            isOpaque = true
            background = if (glowing) JBColor(Color(255, 244, 204), Color(91, 74, 38)) else providerChipBackground(member.provider)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(if (glowing) JBColor(Color(222, 167, 42), Color(191, 145, 49)) else JBColor.border()),
                JBUI.Borders.empty(1, 5)
            )
            add(JBLabel("●").apply { foreground = accent })
            add(JBLabel(member.provider).apply { setAllowAutoWrapping(true) })
            add(JBLabel(remaining).apply {
                foreground = member.percentLeft?.let(::usageBarColor) ?: accent
                font = font.deriveFont(java.awt.Font.BOLD)
            })
            toolTipText = if (member.unlimited) "${member.provider}: unlimited reported quota" else "${member.provider}: ${member.percentLeft}% quota remaining"
        }
    }

    private fun formatDuration(hours: Double): String = when {
        hours >= 48 -> "${(hours / 24).toInt()} days"
        hours >= 1 -> "${hours.toInt().coerceAtLeast(1)} hours"
        else -> "${(hours * 60).toInt().coerceAtLeast(1)} minutes"
    }

    private fun bossAccent(variant: Int): Color = when (Math.floorMod(variant, 6)) {
        0 -> JBColor(Color(133, 91, 194), Color(195, 151, 250))
        1 -> JBColor(Color(170, 103, 56), Color(226, 160, 99))
        2 -> JBColor(Color(62, 129, 169), Color(117, 190, 225))
        3 -> JBColor(Color(42, 139, 119), Color(97, 206, 176))
        4 -> JBColor(Color(151, 76, 132), Color(223, 130, 192))
        else -> JBColor(Color(147, 114, 50), Color(219, 184, 94))
    }

    private fun providerAccent(provider: String): Color = when (provider) {
        "Codex" -> JBColor(Color(57, 174, 153), Color(85, 208, 181))
        "JetBrains AI" -> JBColor(Color(157, 119, 220), Color(190, 158, 245))
        "Claude" -> JBColor(Color(187, 93, 49), Color(239, 157, 113))
        "Cursor" -> JBColor(Color(101, 119, 142), Color(167, 185, 209))
        else -> JBColor(Color(82, 151, 230), Color(128, 184, 248))
    }

    private fun providerChipBackground(provider: String): Color = when (provider) {
        "Codex" -> JBColor(Color(235, 248, 244), Color(40, 57, 52))
        "JetBrains AI" -> JBColor(Color(244, 239, 251), Color(55, 47, 67))
        "Claude" -> JBColor(Color(253, 241, 232), Color(69, 49, 39))
        "Cursor" -> JBColor(Color(241, 244, 247), Color(47, 53, 61))
        else -> JBColor(Color(237, 244, 252), Color(40, 51, 65))
    }

    private companion object {
        const val RESET_GLOW_MILLIS = 3_000L
        const val HIT_DISPLAY_MILLIS = 2_500L
        const val HIT_IMPACT_MILLIS = 650L
    }

    private fun bossIconLabelRepaint() {
        bossBattle.repaint()
    }

    override fun dispose() {
        disposed = true
        expiryTimer.stop()
        hitTimer.stop()
        activeHits.clear()
        service.removeListener(listener)
    }

    private class QuotaBossIcon : Icon {
        var variant = 0
        var phase = 0
        var accent: Color = JBColor(Color(133, 91, 194), Color(182, 139, 242))
        var impact = 0.0
        var impactColor: Color = accent

        override fun getIconWidth(): Int = JBUI.scale(24)
        override fun getIconHeight(): Int = JBUI.scale(24)

        override fun paintIcon(component: java.awt.Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val scale = iconWidth / 32.0
                g.translate(x.toDouble(), y.toDouble())
                g.scale(scale, scale)
                if (impact > 0) {
                    g.color = Color(impactColor.red, impactColor.green, impactColor.blue, (impact * 220).toInt())
                    for (ray in 0..7) {
                        val angle = ray * Math.PI / 4
                        g.drawLine((16 + kotlin.math.cos(angle) * 12).toInt(), (16 + kotlin.math.sin(angle) * 12).toInt(),
                            (16 + kotlin.math.cos(angle) * 16).toInt(), (16 + kotlin.math.sin(angle) * 16).toInt())
                    }
                    g.translate(kotlin.math.sin(impact * Math.PI * 6) * impact * 2, 0.0)
                }
                g.color = accent
                when (Math.floorMod(variant, 3)) {
                    0 -> {
                        val ghost = Path2D.Double().apply {
                            moveTo(5.0, 11.0)
                            curveTo(4.0, 5.0, 9.0, 2.0, 15.0, 3.0)
                            lineTo(11.0, 0.5)
                            lineTo(20.0, 4.0)
                            lineTo(28.0, 0.5)
                            lineTo(25.0, 7.0)
                            curveTo(30.0, 12.0, 29.0, 21.0, 28.0, 26.0)
                            lineTo(22.0, 23.0)
                            lineTo(16.0, 29.0)
                            lineTo(10.0, 24.0)
                            lineTo(4.0, 28.0)
                            curveTo(2.0, 22.0, 2.0, 15.0, 5.0, 11.0)
                            closePath()
                        }
                        g.fill(ghost)
                    }
                    1 -> {
                        g.fillRoundRect(3, 4, 26, 25, 7, 7)
                        g.fillRect(6, 1, 5, 6)
                        g.fillRect(21, 1, 5, 6)
                    }
                    else -> {
                        g.fillOval(5, 2, 22, 20)
                        g.fillRoundRect(6, 17, 5, 13, 4, 4)
                        g.fillRoundRect(14, 17, 5, 15, 4, 4)
                        g.fillRoundRect(22, 17, 5, 13, 4, 4)
                    }
                }
                g.color = JBColor.WHITE
                g.fillOval(8, 10, 6, 8)
                g.fillOval(19, 10, 6, 8)
                g.color = JBColor(Color(55, 40, 76), Color(45, 35, 57))
                g.fillOval(10, 13, 3, 4)
                g.fillOval(20, 13, 3, 4)
                if (phase >= 2) {
                    g.color = JBColor(Color(255, 231, 130), Color(255, 223, 114))
                    g.drawLine(15, 18, 12, 22)
                    g.drawLine(12, 22, 17, 25)
                }
            } finally {
                g.dispose()
            }
        }
    }

    private class WrapLayout(alignment: Int, hgap: Int, vgap: Int) : FlowLayout(alignment, hgap, vgap) {
        // FlowLayout's default preferred height ignores wrapping in narrow tool windows.
        override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)

        override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, false)

        private fun layoutSize(target: Container, preferred: Boolean): Dimension = synchronized(target.treeLock) {
            val insets = target.insets
            val availableWidth = (target.width.takeIf { it > 0 } ?: target.parent?.width ?: Int.MAX_VALUE) -
                insets.left - insets.right - hgap * 2
            var rowWidth = 0
            var rowHeight = 0
            var widestRow = 0
            var totalHeight = 0

            target.components.filter { it.isVisible }.forEach { component ->
                val size = if (preferred) component.preferredSize else component.minimumSize
                if (rowWidth > 0 && rowWidth + hgap + size.width > availableWidth) {
                    widestRow = maxOf(widestRow, rowWidth)
                    totalHeight += rowHeight + vgap
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth > 0) rowWidth += hgap
                rowWidth += size.width
                rowHeight = maxOf(rowHeight, size.height)
            }
            if (rowWidth > 0) {
                widestRow = maxOf(widestRow, rowWidth)
                totalHeight += rowHeight
            }

            Dimension(
                widestRow + insets.left + insets.right + hgap * 2,
                totalHeight + insets.top + insets.bottom + vgap * 2
            )
        }
    }
}
