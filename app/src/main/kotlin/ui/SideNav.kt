package org.catosaur.app.ui

import org.catosaur.app.Eased
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.mix
import org.catosaur.app.smoothCopy
import org.catosaur.app.withAlpha
import java.awt.BasicStroke
import java.awt.Color
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Vertical navigation. [sidebar] holds the menu items; the selected item is a solid accent pill.
 * [pages] holds the page for each item and crossfades when the selection changes.
 */
internal class SideNav(names: List<String>, contents: List<JComponent>) {
    private val items = names.map { NavItem(it) }
    private val keys = names.indices.map { "page$it" }
    private val cards = CardLayout()
    private var selected = -1

    private val fade = Eased(0.16f) { pages.repaint() }

    val sidebar: JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        items.forEach { add(it) }
        add(Box.createVerticalGlue())
    }

    val pages: JPanel = object : JPanel(cards) {
        init {
            isOpaque = false
        }

        override fun paintChildren(g: Graphics) {
            super.paintChildren(g)
            if (fade.current > 0f) {
                val g2 = g.smoothCopy()
                g2.color = withAlpha(Palette.base, fade.current)
                g2.fillRect(0, 0, width, height)
                g2.dispose()
            }
        }
    }

    init {
        items.forEachIndexed { index, item -> item.onSelect = { select(index) } }
        contents.forEachIndexed { index, component -> pages.add(component, keys[index]) }
        select(0)
    }

    fun select(index: Int) {
        if (index == selected) return
        val previous = selected
        selected = index
        items.forEachIndexed { i, item -> item.selected = i == index }
        cards.show(pages, keys[index])
        if (previous >= 0) {
            fade.snapTo(0.35f)
            fade.target = 0f
        }
    }

    /**
     * Shows [count] in parentheses after the tab named [name] and glows the tab while the count is above zero.
     * Zero hides the count and the glow.
     */
    fun setCount(name: String, count: Int) {
        items.firstOrNull { it.label == name }?.setCount(count)
    }
}

private class NavItem(val label: String) : JComponent() {
    var selected = false
        set(value) {
            field = value
            updateEmphasis()
        }

    var onSelect: () -> Unit = {}

    private var hovered = false
    private var count = 0
    private val emphasis = Eased(0.2f) { repaint() }
    private val glow = Eased(0.3f) { repaint() }

    fun setCount(value: Int) {
        count = value.coerceAtLeast(0)
        glow.target = if (count > 0) 1f else 0f
        repaint()
    }

    init {
        isOpaque = false
        font = Fonts.ui(14)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(200, 42)
        maximumSize = Dimension(Int.MAX_VALUE, 42)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hovered = true
                updateEmphasis()
            }

            override fun mouseExited(e: MouseEvent) {
                hovered = false
                updateEmphasis()
            }

            override fun mouseClicked(e: MouseEvent) {
                onSelect()
            }
        })
    }

    private fun updateEmphasis() {
        emphasis.target = when {
            selected -> 1f
            hovered -> 0.45f
            else -> 0f
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val t = emphasis.current
        if (t > 0f) {
            val pill = RoundRectangle2D.Float(8f, 2f, width - 16f, height - 4f, 10f, 10f)
            g2.color = if (selected) withAlpha(Palette.primary, t) else withAlpha(Color.WHITE, 0.05f + 0.06f * t)
            g2.fill(pill)
        }
        val halo = glow.current
        if (halo > 0f) {
            // Accent halo behind the tab, drawn in layers so the edge fades softly.
            for (layer in 3 downTo 1) {
                g2.color = withAlpha(Palette.focus, 0.10f * halo / layer)
                g2.stroke = BasicStroke(2f * layer)
                g2.draw(RoundRectangle2D.Float(8f, 2f, width - 16f, height - 4f, 10f, 10f))
            }
            g2.color = withAlpha(Palette.focus, 0.14f * halo)
            g2.fill(RoundRectangle2D.Float(8f, 2f, width - 16f, height - 4f, 10f, 10f))
        }
        g2.font = font
        val metrics = g2.fontMetrics
        val baseline = (height + metrics.ascent - metrics.descent) / 2f
        g2.color = mix(Palette.muted, Palette.text, emphasis.current)
        g2.drawString(label, 26f, baseline)
        if (count > 0) {
            val x = 26f + metrics.stringWidth(label) + 6f
            g2.color = withAlpha(Palette.focus, 0.6f + 0.4f * glow.current)
            g2.drawString("($count)", x, baseline)
        }
        g2.dispose()
    }
}
