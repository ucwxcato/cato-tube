package org.catosaur.app.ui

import org.catosaur.app.Card
import org.catosaur.app.DownloadRing
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.ProgressSlider
import org.catosaur.app.TransportButton
import org.catosaur.app.TransportGlyph
import org.catosaur.app.download.DownloadActivity
import org.catosaur.app.download.DownloadManager
import org.catosaur.app.playback.PlaybackController
import org.catosaur.app.playback.PlayerState
import org.catosaur.app.playback.RepeatMode
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

private const val INFO_WIDTH = 230
private const val NOTICE_MS = 6000

/**
 * Bottom player bar, shown on every tab. Left: the current track. Center: transport controls and
 * the seek slider. Right: mute and volume, then the download ring.
 */
internal class PlayerBar(
    downloads: DownloadManager,
    private val controller: PlaybackController,
) {
    private val titleLabel = JLabel().apply {
        font = Fonts.ui(14)
    }
    private val artistLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private val noticeLabel = JLabel(" ").apply {
        font = Fonts.ui(12)
        foreground = Palette.danger
    }

    private val shuffleButton = TransportButton(TransportGlyph.SHUFFLE, size = 28)
    private val previousButton = TransportButton(TransportGlyph.PREVIOUS)
    private val playButton = TransportButton(TransportGlyph.PLAY, size = 40, primary = true)
    private val nextButton = TransportButton(TransportGlyph.NEXT)
    private val repeatButton = TransportButton(TransportGlyph.REPEAT, size = 28)

    private val seekBar = ProgressSlider()
    private val elapsedLabel = timeLabel()
    private val totalLabel = timeLabel()

    private val muteButton = TransportButton(TransportGlyph.VOLUME, size = 28)
    private val volumeBar = ProgressSlider().apply { preferredSize = Dimension(90, 18) }

    private val ring = DownloadRing()
    private var running: DownloadActivity? = null
    private var muted = false

    private val noticeTimer = Timer(NOTICE_MS) { noticeLabel.text = " " }.apply { isRepeats = false }

    private val card = Card(BorderLayout(16, 0), arc = 10f).apply {
        border = BorderFactory.createEmptyBorder(10, 18, 10, 16)
        preferredSize = Dimension(0, 92)
    }

    /** The bar's component. Add it to the window's south edge. */
    val view: JComponent get() = card

    init {
        val info = JPanel(GridLayout(3, 1, 0, 0)).apply {
            isOpaque = false
            preferredSize = Dimension(INFO_WIDTH, 0)
            add(titleLabel)
            add(artistLabel)
            add(noticeLabel)
        }

        val transport = JPanel(FlowLayout(FlowLayout.CENTER, 10, 0)).apply {
            isOpaque = false
            add(shuffleButton)
            add(previousButton)
            add(playButton)
            add(nextButton)
            add(repeatButton)
        }
        val seekRow = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            add(elapsedLabel, BorderLayout.WEST)
            add(seekBar, BorderLayout.CENTER)
            add(totalLabel, BorderLayout.EAST)
        }
        val center = JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(transport, BorderLayout.CENTER)
            add(seekRow, BorderLayout.SOUTH)
        }

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(muteButton)
            add(volumeBar)
            add(ring)
        }

        card.apply {
            add(info, BorderLayout.WEST)
            add(center, BorderLayout.CENTER)
            add(right, BorderLayout.EAST)
        }

        shuffleButton.onClick = { controller.toggleShuffle() }
        previousButton.onClick = { controller.previous() }
        playButton.onClick = { controller.togglePlayPause() }
        nextButton.onClick = { controller.next() }
        repeatButton.onClick = { controller.cycleRepeat() }
        muteButton.onClick = { controller.setMuted(!muted) }
        seekBar.onChange = { fraction, released -> if (released) controller.seekTo(fraction) }
        volumeBar.onChange = { fraction, _ -> controller.setVolume(fraction) }

        controller.addStateListener { state -> SwingUtilities.invokeLater { render(state) } }
        controller.addErrorListener { message ->
            SwingUtilities.invokeLater {
                noticeLabel.text = fit(message, noticeLabel, INFO_WIDTH)
                noticeTimer.restart()
            }
        }
        render(PlayerState(null, false, 0L, 1f, false, false, RepeatMode.OFF))
        bindRing(downloads)
    }

    private fun render(state: PlayerState) {
        val track = state.track
        titleLabel.text = fit(track?.title ?: "Nothing playing", titleLabel, INFO_WIDTH)
        titleLabel.foreground = if (track == null) Palette.muted else Palette.text
        artistLabel.text = fit(track?.artist.orEmpty(), artistLabel, INFO_WIDTH)

        playButton.glyph = if (state.playing) TransportGlyph.PAUSE else TransportGlyph.PLAY
        playButton.isEnabled = track != null
        previousButton.isEnabled = track != null
        nextButton.isEnabled = track != null

        shuffleButton.active = state.shuffle
        repeatButton.active = state.repeat != RepeatMode.OFF
        repeatButton.glyph = if (state.repeat == RepeatMode.ONE) TransportGlyph.REPEAT_ONE else TransportGlyph.REPEAT

        val duration = track?.durationMs?.takeIf { it > 0 }
        seekBar.isEnabled = duration != null
        elapsedLabel.text = formatDuration(state.positionMs)
        totalLabel.text = formatDuration(duration)
        if (duration != null && !seekBar.dragging) {
            seekBar.fraction = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        } else if (duration == null) {
            seekBar.fraction = 0f
        }

        muted = state.muted
        muteButton.glyph = if (state.muted || state.volume == 0f) TransportGlyph.MUTED else TransportGlyph.VOLUME
        if (!volumeBar.dragging) volumeBar.fraction = state.volume
    }

    private fun bindRing(downloads: DownloadManager) {
        downloads.addActivityListener { activity ->
            running = activity
            if (activity == null) ring.fadeOut() else ring.showActivity(activity.title, activity.fraction)
        }
        ring.onCancel = {
            val job = running
            val owner = SwingUtilities.getWindowAncestor(ring) as? JFrame
            if (job != null && owner != null &&
                showThemedQuestion(owner, "Cancel download", "Cancel \"${job.title}\"? Nothing will be added to your library.", yes = "Cancel download", no = "Keep going")
            ) {
                downloads.cancelCurrent()
            }
        }
    }

    private fun timeLabel(): JLabel = JLabel("0:00").apply {
        font = Fonts.ui(12, Font.PLAIN)
        foreground = Palette.muted
    }
}

/** Shortens [text] with "..." so it fits in [width] pixels at the label's font. */
private fun fit(text: String, label: JLabel, width: Int): String {
    val metrics = label.getFontMetrics(label.font)
    if (metrics.stringWidth(text) <= width) return text
    var end = text.length
    while (end > 0 && metrics.stringWidth(text.substring(0, end) + "...") > width) end--
    return text.substring(0, end) + "..."
}
