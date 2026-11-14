package org.catosaur.app.library

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal enum class TrackSort(private val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration"),
    DATE_ADDED("Date added");

    override fun toString(): String = label
}

internal sealed interface OpenResult {
    class Opened(val library: MusicLibrary) : OpenResult
    data object NoLibrary : OpenResult
    class Failed(val message: String) : OpenResult
}

/**
 * Returns the reason [name] cannot be used as a playlist name, or null when it is fine. The name
 * is trimmed first. Names must be non-blank and unique ignoring case. [exceptId] is the playlist
 * being renamed, so it does not clash with its own current name.
 */
internal fun playlistNameProblem(name: String, playlists: List<Playlist>, exceptId: String?): String? {
    val clean = name.trim()
    if (clean.isEmpty()) return "A playlist needs a name."
    val clash = playlists.any { it.id != exceptId && it.name.equals(clean, ignoreCase = true) }
    return if (clash) "A playlist named \"$clean\" already exists." else null
}

/**
 * In-memory view of library.json plus the operations that change it.
 *
 * Every mutation runs on one background worker thread, saves to disk before it is published,
 * and then notifies change listeners on that worker. Swing listeners must move back to the
 * EDT themselves. Reads ([search], [recentlyAdded], [data]) use the last published snapshot
 * and never block on the worker.
 */
internal class MusicLibrary private constructor(
    val root: Path,
    private val storage: LibraryStorage,
    initial: LibraryData,
    /** Non-null when the library cannot be changed. The string is shown to the user. */
    val readOnlyReason: String?,
    /** Non-null when the library was recovered from a backup or otherwise needs attention. */
    val notice: String?,
) {
    private class Indexed(val track: Track, val haystack: String)

    private class Snapshot(val data: LibraryData, val indexed: List<Indexed>, val missingIds: Set<String>)

    @Volatile
    private var state: Snapshot = snapshotOf(initial)

    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "library-worker").apply { isDaemon = true }
    }
    private val changeListeners = CopyOnWriteArrayList<(LibraryData) -> Unit>()
    private val errorListeners = CopyOnWriteArrayList<(String) -> Unit>()

    val data: LibraryData
        get() = state.data

    fun isMissing(track: Track): Boolean = track.id in state.missingIds

    fun addChangeListener(listener: (LibraryData) -> Unit) {
        changeListeners += listener
    }

    fun addErrorListener(listener: (String) -> Unit) {
        errorListeners += listener
    }

    /** Case-insensitive search. Every whitespace-separated term must match the title, artist, or file name. */
    fun search(query: String, sort: TrackSort): List<Track> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return state.indexed
            .filter { entry -> terms.all { entry.haystack.contains(it) } }
            .map { it.track }
            .sortedWith(comparatorFor(sort))
    }

    fun recentlyAdded(limit: Int): List<Track> =
        state.data.tracks.sortedByDescending { it.dateAddedEpochMs }.take(limit)

    /** [onFinished] runs on the worker with the number of tracks added. */
    fun importFiles(files: List<Path>, onFinished: (Int) -> Unit = {}): Future<*> = runOnWorker("Import") {
        val songs = root.resolve(SONGS_DIR)
        val added = mutableListOf<Track>()
        for (source in files) {
            try {
                if (!isSupportedAudio(source.fileName.toString())) {
                    reportError("Skipped ${source.fileName}: unsupported format.")
                    continue
                }
                val alreadyInSongs = source.toAbsolutePath().normalize().parent == songs.toAbsolutePath().normalize()
                val target = if (alreadyInSongs) source else copyAsNewSong(source, songs)
                val relative = relativeSongPath(target)
                if (added.any { it.relativePath == relative } || state.data.tracks.any { it.relativePath == relative }) {
                    continue
                }
                added += newTrack(target, relative, System.currentTimeMillis())
            } catch (e: IOException) {
                reportError("Could not import ${source.fileName}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        if (added.isNotEmpty()) {
            commit("Import") { it.copy(tracks = it.tracks + added) }
        }
        onFinished(added.size)
    }

    /** Adds audio files found in songs/ that the library does not reference yet. [onFinished] gets the count added. */
    fun scanForNewFiles(onFinished: (Int) -> Unit = {}): Future<*> = runOnWorker("Rescan") {
        val songs = root.resolve(SONGS_DIR)
        if (!Files.isDirectory(songs)) {
            onFinished(0)
            return@runOnWorker
        }
        val known = state.data.tracks.map { it.relativePath }.toSet()
        val files = Files.list(songs).use { stream ->
            stream.filter { Files.isRegularFile(it) && isSupportedAudio(it.fileName.toString()) }.toList()
        }.sortedBy { it.fileName.toString() }
        val added = files
            .filter { relativeSongPath(it) !in known }
            .map { file -> newTrack(file, relativeSongPath(file), Files.getLastModifiedTime(file).toMillis()) }
        if (added.isNotEmpty()) {
            commit("Rescan") { it.copy(tracks = it.tracks + added) }
        }
        onFinished(added.size)
    }

    /**
     * Removes the track and its playlist memberships. The audio file is deleted only when
     * [deleteAudio] is true, and only after the metadata change has been saved.
     */
    fun removeTrack(trackId: String, deleteAudio: Boolean): Future<*> = runOnWorker("Remove") {
        val track = state.data.tracks.firstOrNull { it.id == trackId } ?: return@runOnWorker
        val saved = commit("Remove") { data ->
            data.copy(
                tracks = data.tracks.filter { it.id != trackId },
                playlists = data.playlists.map { playlist ->
                    playlist.copy(trackIds = playlist.trackIds.filter { it != trackId })
                },
            )
        }
        if (saved && deleteAudio) {
            Files.deleteIfExists(root.resolve(track.relativePath))
        }
    }

    /**
     * Changes the display title and artist. The audio file keeps its name, so the track's path and
     * playlist memberships are unaffected. A blank title is rejected; a blank artist clears the field.
     */
    fun updateTrack(trackId: String, title: String, artist: String): Future<*> = runOnWorker("Edit") {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            reportError("A song needs a title.")
            return@runOnWorker
        }
        if (state.data.tracks.none { it.id == trackId }) return@runOnWorker
        commit("Edit") { data ->
            data.copy(
                tracks = data.tracks.map { track ->
                    if (track.id == trackId) track.copy(title = cleanTitle, artist = artist.trim()) else track
                },
            )
        }
    }

    /**
     * Creates a playlist. Tracks in [trackIds] that are not in the library are ignored. Names are
     * trimmed and must be unique ignoring case; see [playlistNameProblem].
     */
    fun createPlaylist(name: String, trackIds: List<String> = emptyList()): Future<*> = runOnWorker("Create playlist") {
        val cleanName = validatedPlaylistName(name, exceptId = null) ?: return@runOnWorker
        val known = state.data.tracks.map { it.id }.toSet()
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            trackIds = trackIds.filter { it in known }.distinct(),
            createdEpochMs = System.currentTimeMillis(),
        )
        commit("Create playlist") { it.copy(playlists = it.playlists + playlist) }
    }

    fun renamePlaylist(playlistId: String, name: String): Future<*> = runOnWorker("Rename playlist") {
        val cleanName = validatedPlaylistName(name, exceptId = playlistId) ?: return@runOnWorker
        changePlaylist("Rename playlist", playlistId) { it.copy(name = cleanName) }
    }

    /** Deletes the playlist only. Its tracks stay in the library and keep their audio files. */
    fun deletePlaylist(playlistId: String): Future<*> = runOnWorker("Delete playlist") {
        if (state.data.playlists.none { it.id == playlistId }) return@runOnWorker
        commit("Delete playlist") { data -> data.copy(playlists = data.playlists.filter { it.id != playlistId }) }
    }

    /**
     * Appends [trackIds] to the playlist in the given order, skipping tracks already in it and
     * tracks that are not in the library. [onFinished] runs on the worker with the number added.
     */
    fun addToPlaylist(playlistId: String, trackIds: List<String>, onFinished: (Int) -> Unit = {}): Future<*> =
        runOnWorker("Add to playlist") {
            val playlist = state.data.playlists.firstOrNull { it.id == playlistId }
            if (playlist == null) {
                onFinished(0)
            } else {
                val known = state.data.tracks.map { it.id }.toSet()
                val already = playlist.trackIds.toSet()
                val toAdd = trackIds.filter { it in known && it !in already }.distinct()
                if (toAdd.isNotEmpty()) {
                    changePlaylist("Add to playlist", playlistId) { it.copy(trackIds = it.trackIds + toAdd) }
                }
                onFinished(toAdd.size)
            }
        }

    /** Takes the track out of the playlist. The track and its audio file are untouched. */
    fun removeFromPlaylist(playlistId: String, trackId: String): Future<*> = runOnWorker("Remove from playlist") {
        changePlaylist("Remove from playlist", playlistId) { playlist ->
            playlist.copy(trackIds = playlist.trackIds.filter { it != trackId })
        }
    }

    /** Moves the track one position earlier ([step] = -1) or later ([step] = 1) in the playlist. */
    fun moveInPlaylist(playlistId: String, trackId: String, step: Int): Future<*> = runOnWorker("Reorder playlist") {
        changePlaylist("Reorder playlist", playlistId) { playlist ->
            val from = playlist.trackIds.indexOf(trackId)
            val to = from + step
            if (from < 0 || to !in playlist.trackIds.indices) {
                playlist
            } else {
                val ids = playlist.trackIds.toMutableList()
                val moved = ids.removeAt(from)
                ids.add(to, moved)
                playlist.copy(trackIds = ids)
            }
        }
    }

    /** Waits for queued work to finish, then stops the worker. Call once, when the window closes. */
    fun close() {
        worker.shutdown()
        worker.awaitTermination(10, TimeUnit.SECONDS)
    }

    private fun runOnWorker(description: String, action: () -> Unit): Future<*> = worker.submit(Runnable {
        try {
            if (readOnlyReason != null) {
                reportError("$description is unavailable: the library is read-only.")
                return@Runnable
            }
            action()
        } catch (e: Exception) {
            reportError("$description failed: ${e.message ?: e.javaClass.simpleName}")
        }
    })

    /** Runs on the worker. Returns false and reports an error when the save fails. */
    private fun commit(description: String, change: (LibraryData) -> LibraryData): Boolean {
        val next = change(state.data).copy(savedAtEpochMs = System.currentTimeMillis())
        try {
            storage.save(next)
        } catch (e: IOException) {
            reportError("$description was not saved: ${e.message ?: e.javaClass.simpleName}")
            return false
        }
        state = snapshotOf(next)
        changeListeners.forEach { it(next) }
        return true
    }

    private fun newTrack(file: Path, relative: String, dateAddedMs: Long): Track = Track(
        id = UUID.randomUUID().toString(),
        title = file.fileName.toString().substringBeforeLast('.').ifBlank { "Untitled" },
        relativePath = relative,
        dateAddedEpochMs = dateAddedMs,
        durationMs = MediaProbe.durationMs(file),
    )

    private fun relativeSongPath(file: Path): String = "$SONGS_DIR/${file.fileName}"

    private fun reportError(message: String) {
        errorListeners.forEach { it(message) }
    }

    /** Runs on the worker. Returns the trimmed name, or reports the problem and returns null. */
    private fun validatedPlaylistName(name: String, exceptId: String?): String? {
        val problem = playlistNameProblem(name, state.data.playlists, exceptId)
        if (problem != null) {
            reportError(problem)
            return null
        }
        return name.trim()
    }

    /** Runs on the worker. Does nothing when the playlist no longer exists. */
    private fun changePlaylist(description: String, playlistId: String, transform: (Playlist) -> Playlist) {
        if (state.data.playlists.none { it.id == playlistId }) return
        commit(description) { data ->
            data.copy(playlists = data.playlists.map { if (it.id == playlistId) transform(it) else it })
        }
    }

    private fun snapshotOf(data: LibraryData): Snapshot = Snapshot(
        data = data,
        indexed = data.tracks.map { track ->
            val haystack = listOf(track.title, track.artist, track.relativePath.substringAfterLast('/'))
                .joinToString(" ")
                .lowercase()
            Indexed(track, haystack)
        },
        missingIds = data.tracks
            .filter { !Files.exists(root.resolve(it.relativePath)) }
            .map { it.id }
            .toSet(),
    )

    private fun comparatorFor(sort: TrackSort): Comparator<Track> = when (sort) {
        TrackSort.TITLE -> compareBy { it.title.lowercase() }
        TrackSort.ARTIST -> compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        TrackSort.DURATION -> compareBy<Track> { it.durationMs ?: 0L }.thenBy { it.title.lowercase() }
        TrackSort.DATE_ADDED -> compareByDescending<Track> { it.dateAddedEpochMs }.thenBy { it.title.lowercase() }
    }

    companion object {
        /**
         * Opens the library in [root]. A missing library is created empty, unless [forceReadOnly]
         * is set, in which case there is nothing to open.
         */
        fun open(root: Path, forceReadOnly: Boolean = false): OpenResult {
            val storage = LibraryStorage(root)
            return try {
                when (val outcome = storage.load()) {
                    LoadOutcome.Missing -> {
                        if (forceReadOnly) {
                            OpenResult.Failed("The folder is not writable, so a new library cannot be created there.")
                        } else {
                            val empty = LibraryData()
                            storage.save(empty)
                            OpenResult.Opened(MusicLibrary(root, storage, empty, readOnlyReason = null, notice = null))
                        }
                    }
                    is LoadOutcome.Loaded -> OpenResult.Opened(
                        MusicLibrary(
                            root,
                            storage,
                            outcome.data,
                            readOnlyReason = if (forceReadOnly) "The folder is not writable. Changes are disabled." else null,
                            notice = outcome.notice,
                        ),
                    )
                    is LoadOutcome.ReadOnly -> OpenResult.Opened(
                        MusicLibrary(root, storage, outcome.data, readOnlyReason = outcome.reason, notice = null),
                    )
                    is LoadOutcome.Unreadable -> OpenResult.Failed(outcome.message)
                }
            } catch (e: IOException) {
                OpenResult.Failed("Could not open the library: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
