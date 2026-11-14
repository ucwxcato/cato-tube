package org.catosaur.app.ui

import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

/** Centered message for a tab whose feature is not built yet. */
internal class PlaceholderPanel(title: String, message: String) : JPanel(GridBagLayout()) {
    init {
        isOpaque = false
        val text = JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(JLabel(title).apply {
                font = Fonts.ui(18, Font.BOLD)
                foreground = Palette.text
            }, BorderLayout.NORTH)
            add(JLabel(message).apply {
                font = Fonts.ui(13)
                foreground = Palette.muted
            }, BorderLayout.CENTER)
        }
        add(text, GridBagConstraints())
    }
}
