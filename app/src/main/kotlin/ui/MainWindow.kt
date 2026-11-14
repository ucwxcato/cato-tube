package org.catosaur.app.ui

import org.catosaur.app.Backdrop
import org.catosaur.app.Card
import org.catosaur.app.Fonts
import org.catosaur.app.LogoMark
import org.catosaur.app.NativeTitleBar
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private const val WINDOW_TITLE = "CatoTube"

/** Builds the main window, shows it, and then resolves the library folder on the EDT. */
internal fun showMainWindow() {
    val frame = JFrame(WINDOW_TITLE)
    val library = LibraryPanel(frame)
    val nav = SideNav(
        names = listOf("Home", "Discover", "Playlists", "Downloads"),
        contents = listOf(
            library,
            PlaceholderPanel("Discover", "YouTube search is not available yet."),
            PlaceholderPanel("Playlists", "Playlists are not available yet."),
            buildDownloadsPanel(frame),
        ),
    )

    val brand = JPanel(BorderLayout(12, 0)).apply {
        isOpaque = false
        add(LogoMark(), BorderLayout.WEST)
        add(
            JLabel("CatoTube").apply {
                font = Fonts.ui(17, Font.BOLD)
                foreground = Palette.text
            },
            BorderLayout.CENTER,
        )
    }

    val sidebar = Card(BorderLayout(0, 12), arc = 14f).apply {
        border = BorderFactory.createEmptyBorder(18, 14, 16, 14)
        preferredSize = Dimension(228, 0)
        add(brand, BorderLayout.NORTH)
        add(
            JPanel(BorderLayout(0, 8)).apply {
                isOpaque = false
                add(SectionHeading("Menu"), BorderLayout.NORTH)
                add(nav.sidebar, BorderLayout.CENTER)
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel(BorderLayout(0, 18)).apply {
                isOpaque = false
                add(buildAccentPicker(frame), BorderLayout.NORTH)
                add(
                    JLabel("<html><body style='width: 160px'>Only use audio you own or are authorized to use.</body></html>")
                        .apply {
                            font = Fonts.ui(11)
                            foreground = Palette.muted
                        },
                    BorderLayout.SOUTH,
                )
            },
            BorderLayout.SOUTH,
        )
    }

    frame.apply {
        defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        minimumSize = Dimension(900, 600)
        setSize(1100, 750)
        setLocationRelativeTo(null)
        contentPane = Backdrop().apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            add(
                JPanel(BorderLayout(16, 0)).apply {
                    isOpaque = false
                    add(sidebar, BorderLayout.WEST)
                    add(nav.pages, BorderLayout.CENTER)
                },
                BorderLayout.CENTER,
            )
            add(buildPlayerBar(), BorderLayout.SOUTH)
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                library.shutdown()
                dispose()
                exitProcess(0)
            }
        })
        isVisible = true
    }
    NativeTitleBar.apply(WINDOW_TITLE, Palette.surface, Palette.text)
    SwingUtilities.invokeLater {
        val opened = LibrarySetup.resolveStartup(frame)
        library.attach(opened)
        LibrarySetup.offerDownloadsImport(frame, opened, library::showFeedback)
    }
}
