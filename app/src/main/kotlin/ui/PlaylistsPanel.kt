package org.catosaur.app.ui

import org.catosaur.app.Card
import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import org.catosaur.app.SlimScrollBarUI
import org.catosaur.app.SmoothTable
import org.catosaur.app.ThemedComboBox
import org.catosaur.app.ThemedField
import org.catosaur.app.ThemedMenuItem
import org.catosaur.app.ThemedMenuSeparator
import org.catosaur.app.ThemedPopupMenu
import org.catosaur.app.library.MusicLibrary
import org.catosaur.app.library.Playlist
import org.catosaur.app.library.Track
import org.catosaur.app.library.TrackSort
import org.catosaur.app.library.playlistNameProblem
import org.catosaur.app.sectionLabel
import org.catosaur.app.smoothCopy
import org.catosaur.app.withAlpha
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

private const val NEW_PLAYLIST_CHOICE = "New playlist..."

/**
 * Playlists tab: the playlist list on the left and the selected playlist's songs on the right.
 * Songs can be reordered or removed from the playlist; removing a song from a playlist never
 * touches the library or the audio file.
 */
internal class PlaylistsPanel(
    private val frame: JFrame,
    private val onPlay: (tracks: List<Track>, index: Int) -> Unit = { _, _ -> },
    private val onShuffle: (tracks: List<Track>) -> Unit = {},
) : JPanel(BorderLayout(0, 12)) {
    private var library: MusicLibrary? = null
    private var selectedId: String? = null
    private var pendingSelectName: String? = null
    private var reselectTrackId: String? = null
    private var rebuilding = false
    /** Null means the playlist's own order, which is the only order that can be changed with Move up and Move down. */
    private var sortChoice: SortChoice? = null

    private val listModel = DefaultListModel<Playlist>()
    private val list = JList(listModel).apply {
        isOpaque = false
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = PlaylistCell()
    }
    private val playlistCount = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private val newButton = FlatButton("New playlist...", primary = true)

    private val titleLabel = JLabel().apply {
        font = Fonts.ui(20)
        foreground = Palette.text
    }
    private val metaLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private val playButton = FlatButton("Play", primary = true)
    private val shuffleButton = FlatButton("Shuffle")
    private val renameButton = FlatButton("Rename...")
    private val deleteButton = FlatButton("Delete playlist...", hoverTint = Palette.danger)
    private val editButton = FlatButton("Edit details")
    private val upButton = FlatButton("Move up")
    private val downButton = FlatButton("Move down")
    private val removeButton = FlatButton("Remove from playlist", hoverTint = Palette.danger)

    private val model = TrackTableModel()
    private val table = SmoothTable(model)
    private val emptyText = JLabel().apply {
        font = Fonts.ui(14)
        foreground = Palette.muted
    }
    private val contentCards = CardLayout()
    private val content = JPanel(contentCards).apply { isOpaque = false }
    private val feedbackLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }

    private val popup = ThemedPopupMenu()
    private val editItem = menuItem("Edit Details...") { selectedTrack()?.let(::editSelected) }
    private val upItem = menuItem("Move up") { moveSelected(-1) }
    private val downItem = menuItem("Move down") { moveSelected(1) }
    private val removeItem = menuItem("Remove from playlist", Palette.danger) { removeSelected() }

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 0, 0, 0)

        styleTrackTable(table, model)
        table.rowOfTrack = model::indexOfId
        installHeaderSort(table, ::sortByColumn)
        table.selectionModel.addListSelectionListener { if (!it.valueIsAdjusting) updateTrackActions() }
        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2 || e.button != MouseEvent.BUTTON1) return
                val row = table.rowAtPoint(e.point)
                if (row >= 0) onPlay(model.snapshot(), row)
            }
        })
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "playSelected")
        table.actionMap.put("playSelected", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (table.selectedRow >= 0) onPlay(model.snapshot(), table.selectedRow)
            }
        })
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "removeSelected")
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("F2"), "editSelected")
        table.actionMap.put("removeSelected", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = removeSelected()
        })
        table.actionMap.put("editSelected", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                selectedTrack()?.let(::editSelected)
            }
        })
        popup.add(editItem)
        popup.add(ThemedMenuSeparator())
        popup.add(upItem)
        popup.add(downItem)
        popup.add(ThemedMenuSeparator())
        popup.add(removeItem)

        list.addListSelectionListener {
            if (!it.valueIsAdjusting && !rebuilding) {
                selectedId = list.selectedValue?.id
                sortChoice = null
                renderDetail()
            }
        }
        newButton.addActionListener { newPlaylist() }
        playButton.addActionListener { onPlay(model.snapshot(), 0) }
        shuffleButton.addActionListener { onShuffle(model.snapshot()) }
        renameButton.addActionListener { renameSelected() }
        deleteButton.addActionListener { deleteSelected() }
        editButton.addActionListener { selectedTrack()?.let(::editSelected) }
        upButton.addActionListener { moveSelected(-1) }
        downButton.addActionListener { moveSelected(1) }
        removeButton.addActionListener { removeSelected() }

        val listHeader = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            add(SectionHeading("Your playlists"), BorderLayout.CENTER)
            add(playlistCount, BorderLayout.EAST)
        }
        val listCard = Card(BorderLayout(0, 10), arc = 12f).apply {
            border = BorderFactory.createEmptyBorder(14, 14, 12, 14)
            add(listHeader, BorderLayout.NORTH)
            add(
                JScrollPane(list).apply {
                    border = BorderFactory.createEmptyBorder()
                    viewport.isOpaque = false
                    isOpaque = false
                    verticalScrollBar.setUI(SlimScrollBarUI())
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(newButton)
                },
                BorderLayout.SOUTH,
            )
        }

        val header = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(
                JPanel(BorderLayout(0, 2)).apply {
                    isOpaque = false
                    add(titleLabel, BorderLayout.NORTH)
                    add(metaLabel, BorderLayout.SOUTH)
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(playButton)
                    add(shuffleButton)
                    add(renameButton)
                    add(deleteButton)
                },
                BorderLayout.EAST,
            )
        }
        val trackActions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(editButton)
            add(upButton)
            add(downButton)
            add(removeButton)
        }
        content.add(trackScrollPane(table), CARD_LIST)
        content.add(
            JPanel(GridBagLayout()).apply {
                isOpaque = false
                add(emptyText)
            },
            CARD_EMPTY,
        )
        val detailCard = Card(BorderLayout(0, 12), arc = 12f).apply {
            border = BorderFactory.createEmptyBorder(14, 14, 12, 14)
            add(
                JPanel(BorderLayout(0, 12)).apply {
                    isOpaque = false
                    add(header, BorderLayout.NORTH)
                    add(trackActions, BorderLayout.SOUTH)
                },
                BorderLayout.NORTH,
            )
            add(content, BorderLayout.CENTER)
        }

        listCard.preferredSize = Dimension(260, 0)
        add(
            JPanel(BorderLayout(16, 0)).apply {
                isOpaque = false
                add(listCard, BorderLayout.WEST)
                add(detailCard, BorderLayout.CENTER)
            },
            BorderLayout.CENTER,
        )
        add(feedbackLabel, BorderLayout.SOUTH)

        refresh()
    }

    /** Connects this panel to [lib] (or to no library). Call on the EDT. */
    fun attach(lib: MusicLibrary?) {
        library = lib
        lib?.addChangeListener { SwingUtilities.invokeLater { refresh() } }
        lib?.addErrorListener { message -> SwingUtilities.invokeLater { showFeedback(message, error = true) } }
        showFeedback(lib?.readOnlyReason.orEmpty())
        refresh()
    }

    /** Shows a short message in the status line. */
    fun showFeedback(message: String, error: Boolean = false) {
        feedbackLabel.text = message
        feedbackLabel.foreground = if (error) Palette.danger else Palette.muted
    }

    private fun menuItem(label: String, tint: Color = Palette.accent, action: () -> Unit): ThemedMenuItem =
        ThemedMenuItem(label, tint).apply {
            addActionListener { action() }
        }

    private fun maybeShowPopup(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val row = table.rowAtPoint(e.point)
        if (row < 0) return
        table.setRowSelectionInterval(row, row)
        updateTrackActions()
        popup.show(table, e.x, e.y)
    }

    private fun selectedTrack(): Track? = table.selectedRow.takeIf { it >= 0 }?.let { model.trackAt(it) }

    private fun writable(): Boolean = library?.readOnlyReason == null

    /** Marks the row for [trackId] as playing. Pass null when nothing from this panel is playing. */
    fun showNowPlaying(trackId: String?, level: Float) {
        table.setPlaying(trackId, level)
    }

    private fun refresh() {
        val lib = library
        val playlists = lib?.data?.playlists.orEmpty()

        val wanted = pendingSelectName
            ?.let { name -> playlists.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id }
            ?.also { pendingSelectName = null }
            ?: selectedId
        rebuilding = true
        listModel.clear()
        playlists.forEach(listModel::addElement)
        rebuilding = false
        val index = playlists.indexOfFirst { it.id == wanted }
        selectedId = playlists.getOrNull(if (index >= 0) index else 0)?.id
        list.selectedIndex = if (playlists.isEmpty()) -1 else index.coerceAtLeast(0)

        playlistCount.text = if (lib == null) "" else playlists.size.toString()
        renderDetail()
    }

    private fun renderDetail() {
        val lib = library
        val playlist = lib?.data?.playlists?.firstOrNull { it.id == selectedId }
        val canWrite = writable()
        newButton.isEnabled = lib != null && canWrite
        renameButton.isEnabled = playlist != null && canWrite
        deleteButton.isEnabled = playlist != null && canWrite

        if (lib == null || playlist == null) {
            playButton.isEnabled = false
            shuffleButton.isEnabled = false
            titleLabel.text = ""
            metaLabel.text = ""
            model.setRows(emptyList(), emptySet())
            emptyText.text = when {
                lib == null -> "Choose a library folder to see your playlists."
                lib.data.playlists.isEmpty() ->
                    "No playlists yet. Create one, then right-click a song on Home and choose Add to Playlist."
                else -> "Select a playlist."
            }
            contentCards.show(content, CARD_EMPTY)
            updateTrackActions()
            return
        }

        val byId = lib.data.tracks.associateBy { it.id }
        val tracks = playlist.trackIds.mapNotNull { byId[it] }
        val choice = sortChoice
        val shown = if (choice == null) {
            tracks
        } else {
            tracks.sortedWith(lib.comparatorFor(choice.sort)).let { if (choice.reversed) it.reversed() else it }
        }
        model.setRows(shown, shown.filter(lib::isMissing).map { it.id }.toSet())
        playButton.isEnabled = shown.isNotEmpty()
        shuffleButton.isEnabled = shown.isNotEmpty()
        titleLabel.text = playlist.name
        val totalMs = tracks.sumOf { it.durationMs ?: 0L }
        val durationNote = if (totalMs > 0) " - ${formatTotal(totalMs)}" else ""
        val sortNote = choice?.let { " - sorted by ${it.sort}${if (it.reversed) ", reversed" else ""}" }.orEmpty()
        metaLabel.text = songCount(tracks.size) + durationNote + sortNote

        if (tracks.isEmpty()) {
            emptyText.text = "This playlist is empty. Right-click a song on Home and choose Add to Playlist."
            contentCards.show(content, CARD_EMPTY)
        } else {
            contentCards.show(content, CARD_LIST)
        }
        reselectTrackId?.let { id ->
            val row = shown.indexOfFirst { it.id == id }
            if (row >= 0) table.setRowSelectionInterval(row, row)
        }
        reselectTrackId = null
        updateTrackActions()
    }

    private fun updateTrackActions() {
        val row = table.selectedRow
        val active = writable() && row >= 0
        val manualOrder = sortChoice == null
        upButton.isEnabled = active && manualOrder && row > 0
        downButton.isEnabled = active && manualOrder && row < model.rowCount - 1
        removeButton.isEnabled = active
        editButton.isEnabled = active
        upItem.isEnabled = upButton.isEnabled
        downItem.isEnabled = downButton.isEnabled
        removeItem.isEnabled = removeButton.isEnabled
        editItem.isEnabled = active
    }

    private fun newPlaylist() {
        val lib = library ?: return
        if (!writable()) return
        askPlaylistName(frame, "New playlist", "", lib, exceptId = null, confirmText = "Create") { name ->
            pendingSelectName = name
            lib.createPlaylist(name)
        }
    }

    private fun renameSelected() {
        val lib = library ?: return
        val playlist = lib.data.playlists.firstOrNull { it.id == selectedId } ?: return
        if (!writable()) return
        askPlaylistName(frame, "Rename playlist", playlist.name, lib, exceptId = playlist.id, confirmText = "Save") { name ->
            lib.renamePlaylist(playlist.id, name)
        }
    }

    private fun deleteSelected() {
        val lib = library ?: return
        val playlist = lib.data.playlists.firstOrNull { it.id == selectedId } ?: return
        if (!writable()) return
        val confirmed = showThemedQuestion(
            frame,
            "Delete playlist",
            "Delete \"${playlist.name}\"? Its songs stay in your library.",
            yes = "Delete",
            no = "Cancel",
        )
        if (confirmed) lib.deletePlaylist(playlist.id)
    }

    private fun editSelected(track: Track) {
        val lib = library ?: return
        if (!writable()) return
        showEditSongDialog(frame, lib, track) { reselectTrackId = track.id }
    }

    /**
     * Clicking a column header sorts the playlist by that column. A second click reverses it, and a
     * third click returns to the playlist's own order, where Move up and Move down work again.
     */
    private fun sortByColumn(sort: TrackSort) {
        val current = sortChoice
        sortChoice = when {
            current == null || current.sort != sort -> SortChoice(sort, reversed = false)
            !current.reversed -> SortChoice(sort, reversed = true)
            else -> null
        }
        renderDetail()
    }

    private fun moveSelected(step: Int) {
        val lib = library ?: return
        val playlistId = selectedId ?: return
        val track = selectedTrack() ?: return
        if (!writable()) return
        reselectTrackId = track.id
        lib.moveInPlaylist(playlistId, track.id, step)
    }

    private fun removeSelected() {
        val lib = library ?: return
        val playlistId = selectedId ?: return
        val track = selectedTrack() ?: return
        if (!writable()) return
        lib.removeFromPlaylist(playlistId, track.id)
    }

    private companion object {
        const val CARD_LIST = "list"
        const val CARD_EMPTY = "empty"
    }
}

/**
 * Asks for a playlist name and checks it against the library's playlists. [exceptId] is the playlist
 * being renamed, so keeping its own name is allowed.
 */
internal fun askPlaylistName(
    owner: JFrame,
    title: String,
    initial: String,
    lib: MusicLibrary,
    exceptId: String?,
    confirmText: String,
    onConfirm: (String) -> Unit,
) {
    val field = ThemedField(initial).apply { placeholder = "Playlist name" }
    val form = JPanel(GridLayout(2, 1, 0, 8)).apply {
        isOpaque = false
        add(sectionLabel("Name"))
        add(field)
    }
    showThemedDialog(
        owner,
        title,
        form,
        confirmText = confirmText,
        validate = { playlistNameProblem(field.text, lib.data.playlists, exceptId) },
    ) {
        onConfirm(field.text.trim())
    }
}

/**
 * Lets the user pick a playlist (or create one) and adds [track] to it. [onDone] runs on the EDT
 * with a short status message.
 */
internal fun chooseAndAddToPlaylist(owner: JFrame, lib: MusicLibrary, track: Track, onDone: (String) -> Unit) {
    val playlists = lib.data.playlists
    val combo = ThemedComboBox((playlists.map { it.name } + NEW_PLAYLIST_CHOICE).toTypedArray())
    val form = JPanel(GridLayout(2, 1, 0, 8)).apply {
        isOpaque = false
        add(sectionLabel("Playlist"))
        add(combo)
    }
    showThemedDialog(owner, "Add to playlist", form, confirmText = "Add") {
        val index = combo.selectedIndex
        if (index !in playlists.indices) {
            askPlaylistName(owner, "New playlist", "", lib, exceptId = null, confirmText = "Create") { name ->
                lib.createPlaylist(name, listOf(track.id))
                onDone("Added \"${track.title}\" to \"$name\".")
            }
        } else {
            val target = playlists[index]
            lib.addToPlaylist(target.id, listOf(track.id)) { added ->
                SwingUtilities.invokeLater {
                    onDone(
                        if (added == 0) {
                            "\"${track.title}\" is already in \"${target.name}\"."
                        } else {
                            "Added \"${track.title}\" to \"${target.name}\"."
                        },
                    )
                }
            }
        }
    }
}

private fun songCount(count: Int): String = "$count ${if (count == 1) "song" else "songs"}"

private data class SortChoice(val sort: TrackSort, val reversed: Boolean)

/** One playlist row: the name followed by its song count, with a soft accent pill when selected. */
private class PlaylistCell : JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)), ListCellRenderer<Playlist> {
    private val name = JLabel().apply { font = Fonts.ui(14) }
    private val count = JLabel().apply { font = Fonts.ui(12) }
    private var selected = false

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(7, 12, 7, 12)
        add(name)
        add(count)
    }

    override fun getListCellRendererComponent(
        list: JList<out Playlist>?,
        value: Playlist?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        name.text = value?.name.orEmpty()
        count.text = "(${songCount(value?.trackIds?.size ?: 0)})"
        name.foreground = Palette.text
        count.foreground = if (isSelected) Palette.text else Palette.muted
        selected = isSelected
        return this
    }

    override fun paintComponent(g: Graphics) {
        if (selected) {
            val g2 = g.smoothCopy()
            g2.color = withAlpha(Palette.accent, 0.22f)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 10f, 10f))
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
