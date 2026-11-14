package org.catosaur.app.ui

import org.catosaur.app.Card
import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import org.catosaur.app.themeScrollBars
import org.catosaur.app.ThemedComboBox
import org.catosaur.app.ThemedField
import org.catosaur.app.ThemedMenuItem
import org.catosaur.app.ThemedPopupMenu
import org.catosaur.app.SegmentedControl
import org.catosaur.app.download.DownloadManager
import org.catosaur.app.recommendation.KeyProvider
import org.catosaur.app.recommendation.RecommendationMode
import org.catosaur.app.recommendation.RecommendationProvider
import org.catosaur.app.recommendation.RecommendationSettings
import org.catosaur.app.download.DownloadRequest
import org.catosaur.app.download.SearchOutcome
import org.catosaur.app.download.SearchResult
import org.catosaur.app.download.YouTubeSearch
import org.catosaur.app.library.MusicLibrary
import org.catosaur.app.sectionLabel
import org.catosaur.app.smoothCopy
import org.catosaur.app.withAlpha
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

private const val RESULT_LIMIT = 10
private const val CARD_RESULTS = "results"
private const val CARD_MESSAGE = "message"
private const val NEW_PLAYLIST_CHOICE = "New playlist..."
private const val VIEW_SEARCH = "search"
private const val VIEW_RECOMMENDATIONS = "recommendations"

/**
 * Discover tab: searches YouTube through yt-dlp and lists the results, and offers optional Last.fm
 * suggestions (For You and Explore). Searching never downloads anything. A download starts only when the
 * user picks it from a result's right-click menu, and a suggestion only fills the search field.
 */
internal class DiscoverPanel(
    private val frame: JFrame,
    private val downloads: DownloadManager,
    private val provider: RecommendationProvider,
    private val settings: RecommendationSettings,
    private val keys: KeyProvider,
) : JPanel(BorderLayout(0, 12)) {
    private var activeSearch: YouTubeSearch? = null
    private var searchGeneration = 0
    private var lastQuery = ""
    private var library: MusicLibrary? = null

    private val viewCards = CardLayout()
    private val views = JPanel(viewCards).apply { isOpaque = false }
    private val modeControl = SegmentedControl(listOf("Search", "For You", "Explore")) { index -> showView(index) }
    private val recommendations = RecommendationsView(frame, provider, settings, keys) { query -> searchFor(query) }

    private val popup = ThemedPopupMenu()
    private val downloadItem = ThemedMenuItem("Download").apply {
        addActionListener { downloadSelected(withPlaylist = false) }
    }
    private val downloadToPlaylistItem = ThemedMenuItem("Download and add to playlist...").apply {
        addActionListener { downloadSelected(withPlaylist = true) }
    }

    private val queryField = ThemedField().apply {
        placeholder = "Search YouTube"
        searchGlyph = true
        toolTipText = "Type a title, artist, or topic, then press Enter"
    }
    private val searchButton = FlatButton("Search", primary = true)

    private val resultsModel = DefaultListModel<SearchResult>()
    private val resultList = JList(resultsModel).apply {
        isOpaque = false
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = ResultCell()
    }
    private val messageText = JLabel().apply {
        font = Fonts.ui(14)
        foreground = Palette.muted
    }
    private val contentCards = CardLayout()
    private val content = JPanel(contentCards).apply { isOpaque = false }

    private val sectionHeading = SectionHeading("Results")
    private val statusLabel = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 0, 0, 0)

        queryField.addActionListener { startSearch() }
        searchButton.addActionListener { startSearch() }
        resultList.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                val row = plusRowAt(e.point) ?: return
                resultList.selectedIndex = row
                val button = plusBounds(resultList.getCellBounds(row, row))
                popup.show(resultList, button.x, button.y + button.height)
            }
        })
        resultList.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                resultList.cursor = if (plusRowAt(e.point) != null) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }
        })
        popup.add(downloadItem)
        popup.add(downloadToPlaylistItem)

        content.add(
            JScrollPane(resultList).apply {
                border = BorderFactory.createEmptyBorder()
                viewport.isOpaque = false
                isOpaque = false
                themeScrollBars()
            },
            CARD_RESULTS,
        )
        content.add(
            JPanel(GridBagLayout()).apply {
                isOpaque = false
                add(messageText)
            },
            CARD_MESSAGE,
        )

        val toolbar = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            add(queryField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
        }
        val resultsCard = Card(BorderLayout(0, 10), arc = 12f).apply {
            border = BorderFactory.createEmptyBorder(14, 14, 12, 14)
            add(sectionHeading, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }

        val searchView = JPanel(BorderLayout(0, 12)).apply {
            isOpaque = false
            add(toolbar, BorderLayout.NORTH)
            add(resultsCard, BorderLayout.CENTER)
        }
        val recommendationsCard = Card(BorderLayout(), arc = 12f).apply {
            border = BorderFactory.createEmptyBorder(14, 14, 12, 14)
            add(recommendations, BorderLayout.CENTER)
        }
        views.add(searchView, VIEW_SEARCH)
        views.add(recommendationsCard, VIEW_RECOMMENDATIONS)
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(modeControl)
            },
            BorderLayout.NORTH,
        )
        add(views, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(statusLabel, BorderLayout.WEST)
            },
            BorderLayout.SOUTH,
        )

        showMessage("Search YouTube to see results here.", error = false)
    }

    /** Cancels any search or recommendation refresh still running. Call once when the window closes. */
    fun shutdown() {
        activeSearch?.cancel()
        activeSearch = null
        provider.shutdown()
    }

    /** Fills the search field with [query] and runs the same search as the Search button. Never downloads. */
    internal fun searchFor(query: String) {
        modeControl.selectedIndex = 0
        showView(0)
        queryField.text = query
        startSearch()
    }

    /** Switches between Search (0), For You (1), and Explore (2). Switching never starts a recommendation request. */
    private fun showView(index: Int) {
        val searchView = index == 0
        viewCards.show(views, if (searchView) VIEW_SEARCH else VIEW_RECOMMENDATIONS)
        if (!searchView) {
            recommendations.showMode(if (index == 1) RecommendationMode.FOR_YOU else RecommendationMode.EXPLORE)
        }
    }

    /** Connects the panel to [lib], or to no library. Call on the EDT. */
    fun attach(lib: MusicLibrary?) {
        library = lib
    }

    /** Shows a download status line. Call on the EDT. */
    fun showStatus(message: String) {
        statusLabel.foreground = Palette.muted
        statusLabel.text = message
    }

    private fun maybeShowPopup(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val row = resultList.locationToIndex(e.point)
        if (row < 0 || resultList.getCellBounds(row, row)?.contains(e.point) != true) return
        resultList.selectedIndex = row
        popup.show(resultList, e.x, e.y)
    }

    /** Row index whose plus button is under [point], or null. */
    private fun plusRowAt(point: Point): Int? {
        val row = resultList.locationToIndex(point)
        if (row < 0) return null
        val cell = resultList.getCellBounds(row, row) ?: return null
        return if (cell.contains(point) && plusBounds(cell).contains(point)) row else null
    }

    private fun downloadSelected(withPlaylist: Boolean) {
        val result = resultList.selectedValue ?: return
        if (!withPlaylist) {
            downloads.submit(requestFor(result))
            return
        }
        val lib = library
        if (lib == null || lib.readOnlyReason != null) {
            showStatus("Open a library before downloading.")
            return
        }
        chooseDownloadPlaylist(frame, lib) { playlistIds, newName ->
            downloads.submit(requestFor(result, playlistIds, newName))
        }
    }

    private fun startSearch() {
        val query = queryField.text.trim()
        activeSearch?.cancel()
        activeSearch = null
        val generation = ++searchGeneration
        if (query.isEmpty()) {
            showMessage("Type something to search for.", error = true)
            return
        }

        lastQuery = query
        statusLabel.text = ""
        showMessage("Searching for \"$query\"...", error = false)

        val search = YouTubeSearch()
        activeSearch = search
        Thread({
            val outcome = search.run(query, RESULT_LIMIT)
            SwingUtilities.invokeLater {
                if (generation == searchGeneration) {
                    activeSearch = null
                    showOutcome(outcome)
                }
            }
        }, "yt-search").apply { isDaemon = true; start() }
    }

    private fun showOutcome(outcome: SearchOutcome) {
        when (outcome) {
            is SearchOutcome.Found -> {
                resultsModel.clear()
                outcome.results.forEach(resultsModel::addElement)
                if (outcome.results.isEmpty()) {
                    showMessage("No results for \"$lastQuery\".", error = false)
                    return
                }
                contentCards.show(content, CARD_RESULTS)
                statusLabel.foreground = Palette.muted
                val skipped = if (outcome.skippedLines > 0) " (${outcome.skippedLines} unreadable lines skipped)" else ""
                statusLabel.text = "${outcome.results.size} results for \"$lastQuery\"$skipped"
            }
            is SearchOutcome.Failed -> {
                resultsModel.clear()
                showMessage(outcome.message, error = true)
            }
            SearchOutcome.Cancelled -> Unit
        }
    }

    private fun showMessage(message: String, error: Boolean) {
        messageText.text = message
        messageText.foreground = if (error) Palette.danger else Palette.muted
        contentCards.show(content, CARD_MESSAGE)
        if (error) statusLabel.text = ""
    }
}

/**
 * One search result row: thumbnail on the left, then the title over channel and duration. The
 * row gets a soft accent pill when selected.
 */
private class ResultCell : JPanel(BorderLayout(0, 2)), ListCellRenderer<SearchResult> {
    private val title = JLabel().apply { font = Fonts.ui(14) }
    private val detail = JLabel().apply {
        font = Fonts.ui(12)
        foreground = Palette.muted
    }
    private var selected = false
    private var thumbnailUrl: String? = null

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(9, THUMB_LEFT + THUMB_WIDTH + THUMB_GAP, 9, PLUS_RIGHT + PLUS_SIZE + 10)
        add(title, BorderLayout.NORTH)
        add(detail, BorderLayout.SOUTH)
    }

    override fun getListCellRendererComponent(
        list: JList<out SearchResult>?,
        value: SearchResult?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        title.text = value?.title.orEmpty()
        title.foreground = Palette.text
        detail.text = listOfNotNull(
            value?.channel?.takeIf { it.isNotBlank() },
            value?.durationSeconds?.let(::formatSearchDuration) ?: "duration unknown",
        ).joinToString("  |  ")
        selected = isSelected
        thumbnailUrl = value?.thumbnailUrl
        thumbnailUrl?.let { url -> ThumbnailCache.request(url) { list?.repaint() } }
        return this
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        if (selected) {
            g2.color = withAlpha(Palette.accent, 0.22f)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 10f, 10f))
        }
        paintThumbnail(g2)
        paintPlus(g2)
        g2.dispose()
        super.paintComponent(g)
    }

    private fun paintPlus(g2: Graphics2D) {
        val button = plusBounds(Rectangle(0, 0, width, height))
        g2.color = Palette.field
        g2.fill(Ellipse2D.Float(button.x.toFloat(), button.y.toFloat(), button.width.toFloat(), button.height.toFloat()))
        val cx = button.x + button.width / 2f
        val cy = button.y + button.height / 2f
        val arm = 6f
        g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = withAlpha(Palette.text, 0.85f)
        g2.draw(Line2D.Float(cx - arm, cy, cx + arm, cy))
        g2.draw(Line2D.Float(cx, cy - arm, cx, cy + arm))
    }

    private fun paintThumbnail(g2: Graphics2D) {
        val x = THUMB_LEFT
        val y = (height - THUMB_HEIGHT) / 2
        val frame = RoundRectangle2D.Float(
            x.toFloat(), y.toFloat(), THUMB_WIDTH.toFloat(), THUMB_HEIGHT.toFloat(), 8f, 8f,
        )
        g2.color = Palette.field
        g2.fill(frame)

        val image = thumbnailUrl?.let(ThumbnailCache::cached) ?: return
        // Fit the whole image inside the frame (letterboxed), so nothing is stretched or cropped.
        val scale = minOf(THUMB_WIDTH.toDouble() / image.width, THUMB_HEIGHT.toDouble() / image.height)
        val drawWidth = (image.width * scale).toInt()
        val drawHeight = (image.height * scale).toInt()
        val clip = g2.clip
        g2.clip(frame)
        g2.drawImage(
            image,
            x + (THUMB_WIDTH - drawWidth) / 2,
            y + (THUMB_HEIGHT - drawHeight) / 2,
            drawWidth,
            drawHeight,
            null,
        )
        g2.clip = clip
    }
}

private const val THUMB_LEFT = 12
private const val THUMB_WIDTH = 96
private const val THUMB_HEIGHT = 54
private const val THUMB_GAP = 12
private const val PLUS_SIZE = 26
private const val PLUS_RIGHT = 14

/** Square hit area of a row's plus button, centered vertically at the right edge of [cell]. */
private fun plusBounds(cell: Rectangle): Rectangle = Rectangle(
    cell.x + cell.width - PLUS_RIGHT - PLUS_SIZE,
    cell.y + (cell.height - PLUS_SIZE) / 2,
    PLUS_SIZE,
    PLUS_SIZE,
)

private fun formatSearchDuration(seconds: Long): String = formatDuration(seconds * 1000)

private fun requestFor(
    result: SearchResult,
    playlistIds: List<String> = emptyList(),
    newPlaylistName: String? = null,
): DownloadRequest = DownloadRequest(
    youtubeId = result.videoId,
    title = result.title,
    watchUrl = result.watchUrl,
    playlistIds = playlistIds,
    newPlaylistName = newPlaylistName,
)

/**
 * Asks which playlist a download should join (or a new one to create), then calls [onChosen] with
 * the existing playlist IDs and the new playlist name, if any.
 */
private fun chooseDownloadPlaylist(
    owner: JFrame,
    lib: MusicLibrary,
    onChosen: (playlistIds: List<String>, newPlaylistName: String?) -> Unit,
) {
    val playlists = lib.data.playlists
    val combo = ThemedComboBox((playlists.map { it.name } + NEW_PLAYLIST_CHOICE).toTypedArray())
    val form = JPanel(GridLayout(2, 1, 0, 8)).apply {
        isOpaque = false
        add(sectionLabel("Playlist"))
        add(combo)
    }
    showThemedDialog(owner, "Download to playlist", form, confirmText = "Download") {
        val index = combo.selectedIndex
        if (index in playlists.indices) {
            onChosen(listOf(playlists[index].id), null)
        } else {
            askPlaylistName(owner, "New playlist", "", lib, exceptId = null, confirmText = "Create") { name ->
                onChosen(emptyList(), name)
            }
        }
    }
}
