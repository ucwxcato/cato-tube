package org.catosaur.app.ui

import org.catosaur.app.AppIcon
import org.catosaur.app.Backdrop
import org.catosaur.app.Card
import org.catosaur.app.Fonts
import org.catosaur.app.LogoMark
import org.catosaur.app.NativeTitleBar
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import org.catosaur.app.download.DownloadManager
import org.catosaur.app.playback.PlaybackController
import org.catosaur.app.library.MusicLibrary
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
    val frame = JFrame(WINDOW_TITLE).apply {
        iconImages = AppIcon.images
    }
    var currentLibrary: MusicLibrary? = null
    val playback = PlaybackController { track -> currentLibrary?.root?.resolve(track.relativePath) }
    val playlists = PlaylistsPanel(
        frame,
        onPlay = playback::playTracks,
        onShuffle = playback::playShuffled,
        onPlayNext = playback::playNextInQueue,
        onQueue = playback::addToQueue,
    )
    val downloads = DownloadManager()
    val discover = DiscoverPanel(frame, downloads)
    downloads.addStatusListener { discover.showStatus(it) }
    val library = LibraryPanel(
        frame,
        onPlay = playback::playTracks,
        onPlayNext = playback::playNextInQueue,
        onQueue = playback::addToQueue,
    ) { lib ->
        currentLibrary = lib
        playlists.attach(lib)
        discover.attach(lib)
        downloads.attach(lib)
    }
    playback.addStateListener { state ->
        SwingUtilities.invokeLater {
            // The current track stays highlighted while paused, and the pulse eases back to rest.
            val trackId = state.track?.id
            library.showNowPlaying(trackId, state.level)
            playlists.showNowPlaying(trackId, state.level)
        }
    }
    val queue = QueuePanel(frame, playback)
    val nav = SideNav(
        names = listOf("Home", "Discover", "Playlists", "Queue", "Downloads"),
        contents = listOf(
            library,
            discover,
            playlists,
            queue,
            buildDownloadsPanel(frame),
        ),
    )
    playback.addQueueListener { now, upcoming ->
        SwingUtilities.invokeLater {
            queue.update(now, upcoming)
            nav.setCount("Queue", upcoming.size)
        }
    }

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
        contentPane = Backdrop(playback).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            add(
                JPanel(BorderLayout(16, 0)).apply {
                    isOpaque = false
                    add(sidebar, BorderLayout.WEST)
                    add(nav.pages, BorderLayout.CENTER)
                },
                BorderLayout.CENTER,
            )
            add(PlayerBar(downloads, playback).view, BorderLayout.SOUTH)
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                playback.close()
                library.shutdown()
                discover.shutdown()
                downloads.shutdown()
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
        ToolSetup.checkInBackground(frame)
    }
}
