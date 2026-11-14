package org.catosaur.app.ui

import org.catosaur.app.Card
import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import org.catosaur.app.SmoothTable
import org.catosaur.app.library.Track
import org.catosaur.app.playback.PlaybackController
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

private const val CARD_LIST = "list"
private const val CARD_EMPTY = "empty"

/**
 * The Queue page: the song that is playing and the songs that play next. The queue holds copies of
 * tracks, so removing or reordering here never changes a playlist or the library.
 */
internal class QueuePanel(
    private val frame: JFrame,
    private val playback: PlaybackController,
) : JPanel(BorderLayout(0, 14)) {
    private val model = TrackTableModel()
    private val table = SmoothTable(model)
    /** Row to select after the next [update], used so Move up and Move down keep the song selected. */
    private var pendingSelect = -1

    private val nowTitle = JLabel().apply {
        font = Fonts.ui(18)
        foreground = Palette.text
    }
    private val nowArtist = JLabel().apply {
        font = Fonts.ui(13)
        foreground = Palette.muted
    }
    private val countLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private val emptyText = JLabel(
        "<html><body style='width: 380px'>Nothing is queued. Right-click a song on Home or Playlists " +
            "to play it next or add it to the queue.</body></html>",
    ).apply {
        font = Fonts.ui(14)
        foreground = Palette.muted
    }
    private val contentCards = CardLayout()
    private val content = JPanel(contentCards).apply { isOpaque = false }

    private val upButton = FlatButton("Move up")
    private val downButton = FlatButton("Move down")
    private val removeButton = FlatButton("Remove", hoverTint = Palette.danger)
    private val clearButton = FlatButton("Clear queue", hoverTint = Palette.danger)

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 0, 0, 0)

        styleTrackTable(table, model)
        table.selectionModel.addListSelectionListener { if (!it.valueIsAdjusting) updateButtons() }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) playSelected()
            }
        })
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) playSelected()
            }
        })
        upButton.addActionListener { moveSelected(-1) }
        downButton.addActionListener { moveSelected(1) }
        removeButton.addActionListener { removeSelected() }
        clearButton.addActionListener { confirmClear() }

        val nowCard = Card(BorderLayout(0, 8), arc = 14f).apply {
            border = BorderFactory.createEmptyBorder(16, 18, 16, 18)
            add(SectionHeading("Now playing"), BorderLayout.NORTH)
            add(
                JPanel(BorderLayout(0, 2)).apply {
                    isOpaque = false
                    add(nowTitle, BorderLayout.NORTH)
                    add(nowArtist, BorderLayout.SOUTH)
                },
                BorderLayout.CENTER,
            )
        }

        content.add(trackScrollPane(table), CARD_LIST)
        content.add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
                add(emptyText, BorderLayout.NORTH)
            },
            CARD_EMPTY,
        )

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(SectionHeading("Up next"), BorderLayout.WEST)
            add(countLabel, BorderLayout.EAST)
        }
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(upButton)
            add(downButton)
            add(removeButton)
            add(clearButton)
        }
        val queueCard = Card(BorderLayout(0, 12), arc = 14f).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }

        add(nowCard, BorderLayout.NORTH)
        add(queueCard, BorderLayout.CENTER)

        val (now, upcoming) = playback.currentQueue()
        update(now, upcoming)
    }

    /** Shows the current track and the upcoming list. Call on the EDT whenever the queue changes. */
    fun update(now: Track?, upcoming: List<Track>) {
        nowTitle.text = now?.title ?: "Nothing playing"
        nowArtist.text = now?.artist.orEmpty()
        model.setRows(upcoming, emptySet())
        countLabel.text = if (upcoming.isEmpty()) "" else "${upcoming.size} queued"
        contentCards.show(content, if (upcoming.isEmpty()) CARD_EMPTY else CARD_LIST)
        if (pendingSelect in upcoming.indices) table.setRowSelectionInterval(pendingSelect, pendingSelect)
        pendingSelect = -1
        updateButtons()
    }

    private fun updateButtons() {
        val row = table.selectedRow
        val count = model.rowCount
        upButton.isEnabled = row > 0
        downButton.isEnabled = row >= 0 && row < count - 1
        removeButton.isEnabled = row >= 0
        clearButton.isEnabled = count > 0
    }

    private fun playSelected() {
        val row = table.selectedRow
        if (row >= 0) playback.playQueueItem(row)
    }

    private fun moveSelected(delta: Int) {
        val row = table.selectedRow
        val target = row + delta
        if (row < 0 || target !in 0 until model.rowCount) return
        pendingSelect = target
        playback.moveInQueue(row, target)
    }

    private fun removeSelected() {
        val row = table.selectedRow
        if (row < 0) return
        // The song after the removed one slides into this row, so keep the selection here.
        pendingSelect = row
        playback.removeFromQueue(row)
    }

    private fun confirmClear() {
        val clear = showThemedQuestion(
            frame,
            "Clear queue",
            "Remove every song from the queue? The song that is playing keeps playing.",
            yes = "Clear",
            no = "Keep",
        )
        if (clear) playback.clearQueue()
    }
}
