package io.github.larsnoerber.agentsusage.ui.components

import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil
import javax.swing.Icon

/** Loads the clean vector AgentMeter mark at the requested display size. */
internal class AgentMeterLogo(size: Int) : Icon {
    private val icon = IconLoader.getIcon("/icons/agentmeter-logo.svg", AgentMeterLogo::class.java).let {
        IconUtil.scale(it, null, size.toFloat() / it.iconWidth)
    }

    override fun getIconWidth(): Int = icon.iconWidth
    override fun getIconHeight(): Int = icon.iconHeight
    override fun paintIcon(component: java.awt.Component?, graphics: java.awt.Graphics, x: Int, y: Int) {
        icon.paintIcon(component, graphics, x, y)
    }
}
