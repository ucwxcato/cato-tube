package org.catosaur.app.ui

import org.catosaur.app.AccentChoice
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import org.catosaur.app.ThemedComboBox
import java.awt.BorderLayout
import java.util.prefs.Preferences
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

private const val KEY_ACCENT = "accent"

/**
 * Sidebar control that picks the accent color. The choice is applied at once and remembered in the
 * same preferences node as the library folder.
 */
internal fun buildAccentPicker(frame: JFrame): JComponent {
    val saved = savedAccent()
    Palette.applyAccent(saved.color)
    val picker = ThemedComboBox(AccentChoice.entries.toTypedArray()).apply {
        selectedItem = saved
        addActionListener {
            val choice = selectedItem as? AccentChoice ?: return@addActionListener
            Palette.applyAccent(choice.color)
            savedPrefs().put(KEY_ACCENT, choice.name)
            frame.repaint()
        }
    }
    return JPanel(BorderLayout(0, 8)).apply {
        isOpaque = false
        add(SectionHeading("Accent"), BorderLayout.NORTH)
        add(picker, BorderLayout.CENTER)
    }
}

private fun savedAccent(): AccentChoice {
    val name = savedPrefs().get(KEY_ACCENT, AccentChoice.MOCHA.name)
    return AccentChoice.entries.firstOrNull { it.name == name } ?: AccentChoice.MOCHA
}

private fun savedPrefs(): Preferences = Preferences.userRoot().node("org/catosaur/app")
