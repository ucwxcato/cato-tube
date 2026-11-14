package org.catosaur.app.ui

import org.catosaur.app.Card
import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import org.catosaur.app.SegmentedControl
import org.catosaur.app.themeScrollBars
import org.catosaur.app.SmoothTable
import org.catosaur.app.ThemedComboBox
import org.catosaur.app.ThemedField
import org.catosaur.app.ThemedMenuItem
import org.catosaur.app.ThemedMenuSeparator
import org.catosaur.app.ThemedPopupMenu
import org.catosaur.app.library.MusicLibrary
import org.catosaur.app.library.Track
import org.catosaur.app.library.TrackSort
import org.catosaur.app.mix
import org.catosaur.app.paintChip
import org.catosaur.app.sectionLabel
import org.catosaur.app.smoothCopy
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GradientPaint
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.border.MatteBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

private const val RECENT_LIMIT = 10
private val AUDIO_EXTENSIONS = arrayOf("mp3", "wav", "flac", "opus")

/**
 * Home tab: search, sort, import, and the track list. Shows the full library by default; Recent
 * limits the list to the 10 most recently added tracks.
 */
internal class LibraryPanel(
    private val frame: JFrame,
    private val onPlay: (tracks: List<Track>, index: Int) -> Unit = { _, _ -> },
    private val onPlayNext: (Track) -> Unit = {},
    private val onQueue: (Track) -> Unit = {},
    private val onLibraryChanged: (MusicLibrary?) -> Unit = {},
) : JPanel(BorderLayout(0, 12)) {
    private var library: MusicLibrary? = null
    private var showAll = true
    private var sortReversed = false

    private val model = TrackTableModel()
    private val table = SmoothTable(model)

    private val searchField = ThemedField().apply {
        placeholder = "Search songs"
        searchGlyph = true
        toolTipText = "Search by title, artist, or file name"
    }
    private val sortBox = ThemedComboBox(TrackSort.entries.toTypedArray())
    private val importButton = FlatButton("Import...")
    private val rescanButton = FlatButton("Rescan")
    private val scopeControl = SegmentedControl(listOf("Recent", "All")) { index ->
        showAll = index == 1
        refresh()
    }
    private val sectionHeading = SectionHeading("Recently added")
    private val countLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private val summaryLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private val feedbackLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private val bannerText = JLabel().apply {
        font = Fonts.ui(13)
        foreground = Palette.text
    }
    private val bannerAction = FlatButton("Choose folder...")
    private val banner = Card(BorderLayout(12, 0), arc = 12f)

    private val emptyText = JLabel().apply {
        font = Fonts.ui(14)
        foreground = Palette.muted
    }
    private val contentCards = CardLayout()
    private val content = JPanel(contentCards).apply { isOpaque = false }

    private val popup = ThemedPopupMenu()
    private val openLocationItem = menuItem("Open File Location") { selectedTrack()?.let(::openFileLocation) }
    private val editItem = menuItem("Edit Details...") { selectedTrack()?.let(::editDetails) }
    private val addToPlaylistItem = menuItem("Add to Playlist...") { selectedTrack()?.let(::addToPlaylist) }
    private val playNextItem = menuItem("Play Next") { selectedTrack()?.let(onPlayNext) }
    private val queueItem = menuItem("Add to Queue") { selectedTrack()?.let(onQueue) }
    private val removeItem = menuItem("Remove from Library...", tint = Palette.danger) { selectedTrack()?.let(::confirmRemove) }

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 0, 0, 0)

        configureTable()
        configureBanner()
        configureButtons()
        configurePopup()
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refresh()
            override fun removeUpdate(e: DocumentEvent) = refresh()
            override fun changedUpdate(e: DocumentEvent) = refresh()
        })
        sortBox.addActionListener {
            sortReversed = false
            refresh()
        }

        content.add(trackScrollPane(table), CARD_LIST)
        content.add(
            JPanel(GridBagLayout()).apply {
                isOpaque = false
                add(emptyText)
            },
            CARD_EMPTY,
        )

        val toolbar = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            add(searchField, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(sortBox)
                    add(importButton)
                    add(rescanButton)
                },
                BorderLayout.EAST,
            )
        }

        val listHeader = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            add(sectionHeading, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0)).apply {
                    isOpaque = false
                    add(countLabel)
                    add(scopeControl)
                },
                BorderLayout.EAST,
            )
        }
        val listCard = Card(BorderLayout(0, 10), arc = 12f).apply {
            border = BorderFactory.createEmptyBorder(14, 14, 12, 14)
            add(listHeader, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }

        val status = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(summaryLabel, BorderLayout.WEST)
            add(feedbackLabel, BorderLayout.EAST)
        }

        add(
            JPanel(BorderLayout(0, 12)).apply {
                isOpaque = false
                add(banner, BorderLayout.NORTH)
                add(toolbar, BorderLayout.CENTER)
            },
            BorderLayout.NORTH,
        )
        add(listCard, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)

        refresh()
        updateEnabledState()
    }

    /** Connects this panel to [lib] (or to no library). Call on the EDT. */
    fun attach(lib: MusicLibrary?) {
        library = lib
        lib?.addChangeListener { SwingUtilities.invokeLater { refresh() } }
        lib?.addErrorListener { message -> SwingUtilities.invokeLater { showFeedback(message, error = true) } }
        updateBanner()
        updateEnabledState()
        refresh()
        onLibraryChanged(lib)
    }

    /** Shows a short message in the status line. */
    fun showFeedback(message: String, error: Boolean = false) {
        feedbackLabel.text = message
        feedbackLabel.foreground = if (error) Palette.danger else Palette.muted
    }

    /** Waits for library writes to finish. Call once when the window closes. */
    fun shutdown() {
        library?.close()
    }

    /** Marks the row for [trackId] as playing. Pass null when nothing from this panel is playing. */
    fun showNowPlaying(trackId: String?, level: Float) {
        table.setPlaying(trackId, level)
    }

    private fun configureTable() {
        table.rowOfTrack = model::indexOfId
        styleTrackTable(table, model)
        installHeaderSort(table, ::sortByColumn)
        table.apply {
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
                override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 2 || e.button != MouseEvent.BUTTON1) return
                    val row = rowAtPoint(e.point)
                    if (row >= 0) onPlay(this@LibraryPanel.model.snapshot(), row)
                }
            })
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "playSelected")
            actionMap.put("playSelected", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    if (selectedRow >= 0) onPlay(this@LibraryPanel.model.snapshot(), selectedRow)
                }
            })
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "removeSelected")
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("F2"), "editSelected")
            actionMap.put("removeSelected", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    selectedTrack()?.let(::confirmRemove)
                }
            })
            actionMap.put("editSelected", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    selectedTrack()?.let(::editDetails)
                }
            })
        }
    }

    private fun configureBanner() {
        banner.apply {
            border = BorderFactory.createEmptyBorder(10, 14, 10, 14)
            add(bannerText, BorderLayout.CENTER)
            add(bannerAction, BorderLayout.EAST)
        }
    }

    private fun configureButtons() {
        importButton.addActionListener { importFiles() }
        rescanButton.addActionListener { rescan() }
        bannerAction.addActionListener { chooseFolder() }
    }

    private fun configurePopup() {
        popup.add(openLocationItem)
        popup.add(editItem)
        popup.add(addToPlaylistItem)
        popup.add(playNextItem)
        popup.add(queueItem)
        popup.add(ThemedMenuSeparator())
        popup.add(removeItem)
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
        val writable = library?.readOnlyReason == null
        editItem.isEnabled = writable
        addToPlaylistItem.isEnabled = writable
        removeItem.isEnabled = writable
        popup.show(table, e.x, e.y)
    }

    private fun selectedTrack(): Track? = table.selectedRow.takeIf { it >= 0 }?.let { model.trackAt(it) }

    private fun refresh() {
        val lib = library
        val query = searchField.text.trim()
        val searching = query.isNotEmpty()

        scopeControl.isVisible = lib != null && !searching
        scopeControl.selectedIndex = if (showAll) 1 else 0
        sectionHeading.text = when {
            searching -> "SEARCH RESULTS"
            showAll -> "ALL SONGS"
            else -> "RECENTLY ADDED"
        }

        if (lib == null) {
            model.setRows(emptyList(), emptySet())
            emptyText.text = "Choose a library folder to see your songs."
            contentCards.show(content, CARD_EMPTY)
            countLabel.text = ""
            summaryLabel.text = ""
            return
        }

        val sort = sortBox.selectedItem as? TrackSort ?: TrackSort.TITLE
        val tracks = if (searching || showAll) {
            lib.search(query, sort).let { if (sortReversed) it.reversed() else it }
        } else {
            lib.recentlyAdded(RECENT_LIMIT)
        }
        model.setRows(tracks, tracks.filter(lib::isMissing).map { it.id }.toSet())

        val total = lib.data.tracks.size
        if (tracks.isEmpty()) {
            emptyText.text = when {
                searching -> "No songs match \"$query\"."
                total == 0 -> "No songs yet. Import audio files, or download from the Downloads tab."
                else -> "Nothing to show."
            }
            contentCards.show(content, CARD_EMPTY)
        } else {
            contentCards.show(content, CARD_LIST)
        }
        countLabel.text = "${tracks.size} of $total"
        val totalMs = lib.data.tracks.sumOf { it.durationMs ?: 0L }
        summaryLabel.text = "$total ${if (total == 1) "song" else "songs"}" +
            if (totalMs > 0) " - ${formatTotal(totalMs)}" else ""
        sortBox.isEnabled = showAll || searching
    }

    private fun updateBanner() {
        val lib = library
        val message: String? = when {
            lib == null -> "No library folder is selected. Choose one to keep your songs and playlists."
            lib.readOnlyReason != null && lib.notice != null -> "${lib.readOnlyReason} ${lib.notice}"
            lib.readOnlyReason != null -> lib.readOnlyReason
            lib.notice != null -> lib.notice
            else -> null
        }
        bannerText.text = message ?: ""
        bannerAction.isVisible = lib == null
        banner.isVisible = message != null
        revalidate()
        repaint()
    }

    private fun updateEnabledState() {
        val lib = library
        val writable = lib != null && lib.readOnlyReason == null
        importButton.isEnabled = writable
        rescanButton.isEnabled = writable
        searchField.isEnabled = lib != null
    }

    private fun chooseFolder() {
        val chosen = LibrarySetup.chooseAndOpen(frame, null, "Choose library folder") ?: return
        attach(chosen)
        LibrarySetup.offerDownloadsImport(frame, chosen, ::showFeedback)
    }

    private fun importFiles() {
        val lib = library ?: return
        val chooser = JFileChooser().apply {
            dialogTitle = "Import audio"
            isMultiSelectionEnabled = true
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Audio files (${AUDIO_EXTENSIONS.joinToString(", ")})", *AUDIO_EXTENSIONS)
        }
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        val files = chooser.selectedFiles.map { it.toPath() }
        showFeedback("Importing ${files.size} file(s)...")
        lib.importFiles(files) { count ->
            SwingUtilities.invokeLater {
                showFeedback(if (count == 0) "Nothing new was imported." else "Imported $count song(s).")
            }
        }
    }

    private fun rescan() {
        val lib = library ?: return
        showFeedback("Scanning the songs folder...")
        lib.scanForNewFiles { count ->
            SwingUtilities.invokeLater {
                showFeedback(if (count == 0) "No new files found." else "Added $count new file(s).")
            }
        }
    }

    private fun editDetails(track: Track) {
        val lib = library ?: return
        if (lib.readOnlyReason != null) return
        showEditSongDialog(frame, lib, track)
    }

    /**
     * Clicking a column header sorts the list by that column. Clicking the same header again
     * reverses the order. A click in Recent switches to All, because Recent is always newest first.
     */
    private fun sortByColumn(sort: TrackSort) {
        showAll = true
        if (sortBox.selectedItem == sort) {
            sortReversed = !sortReversed
        } else {
            sortReversed = false
            sortBox.selectedItem = sort
        }
        refresh()
    }

    private fun addToPlaylist(track: Track) {
        val lib = library ?: return
        if (lib.readOnlyReason != null) return
        chooseAndAddToPlaylist(frame, lib, track) { message -> showFeedback(message) }
    }

    private fun openFileLocation(track: Track) {
        val lib = library ?: return
        val file = lib.root.resolve(track.relativePath).toAbsolutePath()
        try {
            when {
                Files.exists(file) -> ProcessBuilder("explorer.exe", "/select,$file").start()
                Files.isDirectory(file.parent) -> ProcessBuilder("explorer.exe", file.parent.toString()).start()
                else -> showFeedback("The song folder no longer exists.", error = true)
            }
        } catch (e: IOException) {
            showFeedback("Could not open Explorer: ${e.message ?: e.javaClass.simpleName}", error = true)
        }
    }

    private fun confirmRemove(track: Track) {
        val lib = library ?: return
        if (lib.readOnlyReason != null) return
        val deleteAudio = JCheckBox("Also delete the audio file (cannot be undone)").apply {
            isOpaque = false
            font = Fonts.ui(13)
            foreground = Palette.text
        }
        val message = JPanel(BorderLayout(0, 12)).apply {
            isOpaque = false
            add(
                JLabel(
                    "<html>Remove <b>${escapeHtml(track.title)}</b> from the library?<br>" +
                        "It is also removed from every playlist. Your audio is kept unless you tick the box below.</html>",
                ).apply {
                    font = Fonts.ui(13)
                    foreground = Palette.text
                },
                BorderLayout.CENTER,
            )
            add(deleteAudio, BorderLayout.SOUTH)
        }
        showThemedDialog(frame, "Remove from library", message, confirmText = "Remove") {
            lib.removeTrack(track.id, deleteAudio.isSelected)
        }
    }

    private companion object {
        const val CARD_LIST = "list"
        const val CARD_EMPTY = "empty"
    }
}

internal class TrackTableModel : AbstractTableModel() {
    private var rows: List<Track> = emptyList()
    private var missingIds: Set<String> = emptySet()

    fun setRows(tracks: List<Track>, missing: Set<String>) {
        rows = tracks
        missingIds = missing
        fireTableDataChanged()
    }

    fun trackAt(row: Int): Track? = rows.getOrNull(row)

    /** The row showing [trackId], or -1. Rows are searched in their current (sorted) order. */
    fun indexOfId(trackId: String): Int = rows.indexOfFirst { it.id == trackId }

    /** The rows in their current order, for building a play queue. */
    fun snapshot(): List<Track> = rows

    fun isMissing(row: Int): Boolean = rows.getOrNull(row)?.let { it.id in missingIds } ?: false

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = COLUMNS.size

    override fun getColumnName(column: Int): String = COLUMNS[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val track = rows[rowIndex]
        return when (columnIndex) {
            0 -> track.title
            1 -> track.artist.ifBlank { "-" }
            2 -> formatDuration(track.durationMs)
            else -> DATE_FORMAT.format(Instant.ofEpochMilli(track.dateAddedEpochMs))
        }
    }

    private companion object {
        val COLUMNS = listOf("Title", "Artist", "Duration", "Added")
    }
}

/**
 * Cell renderer for the track list. Cells are transparent so the table's glass highlight shows
 * through. The title column gets a colored initial chip; the color comes from the title so each
 * song keeps the same color across launches.
 */
private class TrackCellRenderer(private val model: TrackTableModel) : DefaultTableCellRenderer() {
    private var chipInitial: String? = null
    private var chipSeed: String = ""

    init {
        font = Fonts.ui(13)
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        isOpaque = false
        val missing = model.isMissing(row)
        val text = value?.toString().orEmpty()
        val track = model.trackAt(row)

        if (column == 0) {
            border = BorderFactory.createEmptyBorder(0, 56, 0, 12)
            this.text = if (missing) "$text  (missing file)" else text
            chipInitial = text.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            chipSeed = track?.title.orEmpty()
            font = Fonts.ui(14, Font.BOLD)
        } else {
            border = BorderFactory.createEmptyBorder(0, 12, 0, 12)
            this.text = text
            chipInitial = null
            font = Fonts.ui(13)
        }
        foreground = when {
            isSelected -> Color.WHITE
            missing && column == 0 -> Palette.danger
            column == 0 -> mix(Palette.text, Color.WHITE, 0.5f)
            column == 1 -> Palette.text
            else -> mix(Palette.muted, Palette.text, 0.35f)
        }
        return this
    }

    override fun paintComponent(g: Graphics) {
        val initial = chipInitial
        if (initial != null) {
            paintChip(g.smoothCopy(), 14f, (height - 28f) / 2f, 28f, initial, chipSeed)
        }
        super.paintComponent(g)
    }
}

private class HeaderRenderer : DefaultTableCellRenderer() {
    init {
        isOpaque = false
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        super.getTableCellRendererComponent(table, value, false, false, row, column)
        text = value?.toString()?.uppercase().orEmpty()
        font = Fonts.ui(11, Font.BOLD)
        foreground = mix(Palette.muted, Palette.text, 0.2f)
        border = BorderFactory.createCompoundBorder(
            MatteBorder(0, 0, 1, 0, Palette.glassEdge),
            BorderFactory.createEmptyBorder(0, if (column == 0) 56 else 12, 0, 12),
        )
        return this
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

/** Sizes, colors, and renderers shared by every track list (Home and playlists). */
/**
 * Edit dialog for a song's title and artist. Shared by Home and Playlists. [onSaved] runs on the
 * EDT after the save is queued.
 */
internal fun showEditSongDialog(owner: JFrame, lib: MusicLibrary, track: Track, onSaved: () -> Unit = {}) {
    val titleField = ThemedField(track.title)
    val artistField = ThemedField(track.artist)
    val form = JPanel(GridLayout(4, 1, 0, 8)).apply {
        isOpaque = false
        add(sectionLabel("Title"))
        add(titleField)
        add(sectionLabel("Artist"))
        add(artistField)
    }
    showThemedDialog(
        owner,
        "Edit song",
        form,
        confirmText = "Save",
        validate = { if (titleField.text.isBlank()) "A song needs a title." else null },
    ) {
        lib.updateTrack(track.id, titleField.text, artistField.text)
        onSaved()
    }
}

/** Makes the column headers of [table] clickable. Each sortable column reports its [TrackSort] to [onSort]. */
internal fun installHeaderSort(table: JTable, onSort: (TrackSort) -> Unit) {
    val header = table.tableHeader
    header.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
    header.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            val column = table.convertColumnIndexToModel(header.columnAtPoint(e.point))
            val sort = when (column) {
                0 -> TrackSort.TITLE
                1 -> TrackSort.ARTIST
                2 -> TrackSort.DURATION
                3 -> TrackSort.DATE_ADDED
                else -> null
            }
            sort?.let(onSort)
        }
    })
}

internal fun styleTrackTable(table: SmoothTable, model: TrackTableModel) {
    table.apply {
        rowHeight = 40
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
        foreground = Palette.text
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        fillsViewportHeight = true
        tableHeader.reorderingAllowed = false
        tableHeader.preferredSize = Dimension(0, 34)
        tableHeader.isOpaque = false
        tableHeader.background = Color(0, 0, 0, 0)
        tableHeader.defaultRenderer = HeaderRenderer()
        setDefaultRenderer(String::class.java, TrackCellRenderer(model))
        columnModel.getColumn(0).preferredWidth = 380
        columnModel.getColumn(1).preferredWidth = 170
        columnModel.getColumn(2).preferredWidth = 120
        columnModel.getColumn(3).preferredWidth = 170
    }
}

/** Scroll pane for a track table, with the header pinned and the glass scroll bar. */
internal fun trackScrollPane(table: JTable): JScrollPane = JScrollPane(table).apply {
    border = BorderFactory.createEmptyBorder()
    setColumnHeaderView(table.tableHeader)
    viewport.isOpaque = false
    columnHeader.isOpaque = false
    columnHeader.background = Color(0, 0, 0, 0)
    isOpaque = false
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    themeScrollBars()
}

internal fun formatDuration(ms: Long?): String {
    if (ms == null) return "-"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

internal fun formatTotal(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
