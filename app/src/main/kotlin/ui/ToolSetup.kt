package org.catosaur.app.ui

import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.ThemedField
import org.catosaur.app.smoothCopy
import org.catosaur.app.tools.ToolCheck
import org.catosaur.app.tools.ToolProbe
import org.catosaur.app.tools.ToolStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.geom.Ellipse2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Startup check for the external tools. The probe runs off the EDT, and the dialog appears only on problems. */
internal object ToolSetup {
    fun checkInBackground(frame: JFrame) {
        Thread({
            val checks = ToolProbe.checkAll()
            if (checks.any { !it.ok }) SwingUtilities.invokeLater { show(frame, checks) }
        }, "tool-check").start()
    }

    private fun show(frame: JFrame, initial: List<ToolCheck>) {
        val rows = JPanel(GridLayout(0, 1, 0, 14)).apply { isOpaque = false }
        val summary = JLabel().apply {
            font = Fonts.ui(12)
            foreground = Palette.muted
        }

        fun fill(checks: List<ToolCheck>) {
            rows.removeAll()
            checks.forEach { rows.add(toolRow(it)) }
            summary.text = summaryText(checks)
            rows.revalidate()
            rows.repaint()
        }
        fill(initial)

        val body = JPanel(BorderLayout(0, 14)).apply {
            isOpaque = false
            add(intro(), BorderLayout.NORTH)
            add(rows, BorderLayout.CENTER)
            add(summary, BorderLayout.SOUTH)
        }

        val recheck = FlatButton("Re-check")
        val keepGoing = FlatButton("Continue", primary = true)
        val dialog = themedWindow(frame, "External tools", body, null, listOf(recheck, keepGoing), keepGoing)
        recheck.addActionListener {
            recheck.isEnabled = false
            Thread({
                val fresh = ToolProbe.checkAll()
                SwingUtilities.invokeLater {
                    if (!dialog.isDisplayable) return@invokeLater
                    fill(fresh)
                    recheck.isEnabled = true
                    dialog.pack()
                }
            }, "tool-recheck").start()
        }
        dialog.isVisible = true
    }

    private fun intro(): JComponent = JLabel(
        "<html><body style='width: 400px'>CatoTube uses external tools for YouTube downloads and " +
            "audio playback. Install what is missing, then choose Re-check. After a fresh install, " +
            "close and reopen CatoTube instead: a running app keeps its old PATH.</body></html>",
    ).apply {
        font = Fonts.ui(13)
        foreground = Palette.text
    }

    private fun toolRow(check: ToolCheck): JComponent {
        val head = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel(check.tool.label).apply {
                font = Fonts.ui(13, Font.BOLD)
                foreground = Palette.text
            })
            add(JLabel(stateText(check)).apply {
                font = Fonts.ui(12)
                foreground = if (check.ok) Palette.muted else Palette.danger
            })
        }
        val column = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(head)
            if (!check.ok) {
                add(Box.createVerticalStrut(4))
                add(JLabel(check.tool.purpose).apply {
                    font = Fonts.ui(12)
                    foreground = Palette.muted
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                add(Box.createVerticalStrut(6))
                add(commandRow(check).apply { alignmentX = Component.LEFT_ALIGNMENT })
            }
        }
        return JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(StatusDot(check.ok), BorderLayout.WEST)
            add(column, BorderLayout.CENTER)
        }
    }

    private fun commandRow(check: ToolCheck): JComponent {
        val field = ThemedField(check.tool.install).apply {
            isEditable = false
            font = Fonts.mono(12)
        }
        val copy = FlatButton("Copy")
        copy.addActionListener { copyToClipboard(check.tool.install) }
        return JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(field, BorderLayout.CENTER)
            add(copy, BorderLayout.EAST)
        }
    }

    private fun stateText(check: ToolCheck): String = when (check.status) {
        ToolStatus.Found -> "found: ${check.displayVersion}"
        ToolStatus.Missing -> "not found on PATH"
        ToolStatus.Failed -> "did not run: ${check.detail}"
    }

    private fun summaryText(checks: List<ToolCheck>): String {
        val missing = checks.count { !it.ok }
        return when {
            missing == 0 -> "All tools are ready."
            missing == 1 -> "1 of ${checks.size} tools needs attention."
            else -> "$missing of ${checks.size} tools need attention."
        }
    }

    private fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

/** State dot: success green when [ok], danger red otherwise. */
private class StatusDot(private val ok: Boolean) : JComponent() {
    init {
        isOpaque = false
        preferredSize = Dimension(12, 12)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        g2.color = if (ok) Palette.success else Palette.danger
        g2.fill(Ellipse2D.Float(0f, 5f, 12f, 12f))
        g2.dispose()
    }
}
