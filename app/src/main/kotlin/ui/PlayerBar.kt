package org.catosaur.app.ui

import org.catosaur.app.Card
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel

/** Bottom player region. Playback arrives in Phase 4, so this shows only the empty state. */
internal fun buildPlayerBar(): JComponent = Card(BorderLayout(), arc = 10f).apply {
    border = BorderFactory.createEmptyBorder(10, 20, 10, 16)
    preferredSize = Dimension(0, 64)
    add(JLabel("Nothing playing").apply {
        font = Fonts.ui(14)
        foreground = Palette.muted
    }, BorderLayout.WEST)
}
