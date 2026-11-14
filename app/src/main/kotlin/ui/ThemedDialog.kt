package org.catosaur.app.ui

import org.catosaur.app.Backdrop
import org.catosaur.app.Card
import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.NativeTitleBar
import org.catosaur.app.Palette
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Modal dialog in the app's glass style with a dark native title bar. [body] is the content card;
 * [validate] returns an error message to keep the dialog open, or null to accept. [onConfirm] runs
 * after the dialog closes with the confirm button.
 */
internal fun showThemedDialog(
    owner: JFrame,
    title: String,
    body: JComponent,
    confirmText: String,
    validate: () -> String? = { null },
    onConfirm: () -> Unit,
) {
    val error = JLabel(" ").apply {
        font = Fonts.ui(12)
        foreground = Palette.danger
    }
    val cancel = FlatButton("Cancel")
    val confirm = FlatButton(confirmText, primary = true)
    val dialog = themedWindow(owner, title, body, error, listOf(cancel, confirm), confirm)

    cancel.addActionListener { dialog.dispose() }
    confirm.addActionListener {
        val problem = validate()
        if (problem != null) {
            error.text = problem
            return@addActionListener
        }
        dialog.dispose()
        onConfirm()
    }
    dialog.isVisible = true
}

/** Message with a single OK button. */
internal fun showThemedMessage(owner: JFrame, title: String, message: String) {
    showThemedChoice(owner, title, message, listOf("OK"))
}

/** Yes/no style prompt. Returns true only when the first button ([yes]) is chosen. */
internal fun showThemedQuestion(owner: JFrame, title: String, message: String, yes: String, no: String): Boolean =
    showThemedChoice(owner, title, message, listOf(yes, no)) == 0

/**
 * Message with one button per entry in [options], in order. Returns the index of the chosen button,
 * or null when the dialog is dismissed with Escape or the title bar close button.
 */
internal fun showThemedChoice(
    owner: JFrame,
    title: String,
    message: String,
    options: List<String>,
    defaultIndex: Int = 0,
): Int? {
    var chosen: Int? = null
    val buttons = options.mapIndexed { index, label -> FlatButton(label, primary = index == defaultIndex) }
    val dialog = themedWindow(owner, title, messageLabel(message), null, buttons, buttons[defaultIndex])
    buttons.forEachIndexed { index, button ->
        button.addActionListener {
            chosen = index
            dialog.dispose()
        }
    }
    dialog.isVisible = true
    return chosen
}

private fun messageLabel(message: String): JComponent = JLabel(
    "<html><body style='width: 360px'>${escapeHtml(message)}</body></html>",
).apply {
    font = Fonts.ui(13)
    foreground = Palette.text
}

internal fun themedWindow(
    owner: JFrame,
    title: String,
    body: JComponent,
    error: JLabel?,
    buttons: List<FlatButton>,
    defaultButton: FlatButton,
): JDialog {
    val dialog = JDialog(owner, title, true)
    val card = Card(BorderLayout(0, 14), arc = 14f, tier = Palette.GLASS_RAISED).apply {
        border = BorderFactory.createEmptyBorder(20, 20, 16, 20)
        add(body, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                error?.let { add(it, BorderLayout.CENTER) }
                add(
                    JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0)).apply {
                        isOpaque = false
                        buttons.forEach { add(it) }
                    },
                    BorderLayout.EAST,
                )
            },
            BorderLayout.SOUTH,
        )
    }

    dialog.apply {
        defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        isResizable = false
        contentPane = Backdrop().apply {
            border = BorderFactory.createEmptyBorder(14, 14, 14, 14)
            add(card, BorderLayout.CENTER)
        }
        rootPane.defaultButton = defaultButton
        rootPane.actionMap.put("dismiss", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                dispose()
            }
        })
        rootPane.inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "dismiss")
        minimumSize = Dimension(420, 0)
        pack()
        setLocationRelativeTo(owner)
    }

    // setVisible in the caller blocks, so the title bar is styled from a queued task.
    SwingUtilities.invokeLater { NativeTitleBar.apply(title, Palette.surface, Palette.text) }
    return dialog
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\n", "<br>")
