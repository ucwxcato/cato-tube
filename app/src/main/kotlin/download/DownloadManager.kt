package org.catosaur.app.download

import org.catosaur.app.library.MusicLibrary
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

/** The job that is running now. [fraction] is 0..1 while yt-dlp reports a percent, or null when it is busy without one. */
internal data class DownloadActivity(val title: String, val fraction: Float?)

/** What a download should do once the MP3 is in the library. */
internal data class DownloadRequest(
    val youtubeId: String,
    val title: String,
    val watchUrl: String,
    /** Existing playlists to add the track to. */
    val playlistIds: List<String> = emptyList(),
    /** A new playlist to create and add the track to, or null. */
    val newPlaylistName: String? = null,
)

private const val NO_OUTPUT_TIMEOUT_SECONDS = 120L
private const val STAGING_DIR = ".staging"

/**
 * Runs downloads one at a time (devplan: sequential downloads only). Each download goes to
 * `<library>/.staging/<job>/`; a finished MP3 is registered in the library, and the staging folder
 * is removed whether the job succeeds, fails, or is cancelled.
 *
 * Status messages are delivered to listeners on the EDT.
 */
internal class DownloadManager {
    @Volatile private var library: MusicLibrary? = null
    @Volatile private var activeProcess: Process? = null
    @Volatile private var cancelRequested = false
    private val statusListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val activityListeners = CopyOnWriteArrayList<(DownloadActivity?) -> Unit>()
    private val logListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val countListeners = CopyOnWriteArrayList<(Int) -> Unit>()
    private val unfinishedJobs = AtomicInteger(0)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "download-worker").apply { isDaemon = true }
    }

    fun addStatusListener(listener: (String) -> Unit) {
        statusListeners += listener
    }

    /** Receives every status message and every yt-dlp output line, in order. Called on the EDT. */
    fun addLogListener(listener: (String) -> Unit) {
        logListeners += listener
    }

    /** Receives how many downloads are queued or running, each time that number changes. Called on the EDT. */
    fun addCountListener(listener: (Int) -> Unit) {
        countListeners += listener
    }

    /** Receives the running job's progress, and null once no job is left. Called on the EDT. */
    fun addActivityListener(listener: (DownloadActivity?) -> Unit) {
        activityListeners += listener
    }

    /** Sets the library that finished downloads are added to. Null means no library is open. */
    fun attach(lib: MusicLibrary?) {
        library = lib
    }

    /**
     * Queues a download. Call from the EDT. A video already in the library is not downloaded again;
     * the existing track is added to the requested playlists instead.
     */
    fun submit(request: DownloadRequest) {
        val lib = library
        if (lib == null) {
            status("Open a library before downloading.")
            return
        }
        if (lib.readOnlyReason != null) {
            status("The library is read-only, so nothing was downloaded.")
            return
        }
        val existing = lib.findByYoutubeId(request.youtubeId)
        if (existing != null) {
            lib.addTrackToPlaylists(existing.id, request.playlistIds, request.newPlaylistName)
            status("\"${existing.title}\" is already in your library.")
            return
        }
        status("Queued \"${request.title}\".")
        unfinishedJobs.incrementAndGet()
        countChanged()
        worker.submit { runJob(lib, request) }
    }

    /** Stops the running download, if any. Its staging folder is removed when it ends. */
    fun cancelCurrent() {
        cancelRequested = true
        activeProcess?.destroyForcibly()
    }

    /** Cancels the running download and stops accepting new work. Call once, when the window closes. */
    fun shutdown() {
        cancelCurrent()
        worker.shutdownNow()
    }

    private fun runJob(lib: MusicLibrary, request: DownloadRequest) {
        cancelRequested = false
        val staging = lib.root.resolve(STAGING_DIR).resolve(UUID.randomUUID().toString())
        activity(DownloadActivity(request.title, null))
        try {
            Files.createDirectories(staging)
            status("Downloading \"${request.title}\"...")
            val code = runYtDlp(staging, request.watchUrl, request.title)
            when {
                cancelRequested -> status("Cancelled \"${request.title}\". Nothing was added.")
                code == TIMED_OUT -> status("Stopped \"${request.title}\": no output for 2 minutes. Nothing was added.")
                code != 0 -> status("Download failed for \"${request.title}\" (yt-dlp exit $code). Nothing was added.")
                else -> registerFinished(lib, staging, request)
            }
        } catch (e: IOException) {
            status("Download could not start: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            deleteStaging(staging)
            if (unfinishedJobs.decrementAndGet() == 0) activity(null)
            countChanged()
        }
    }

    /** Runs yt-dlp into [staging] and returns its exit code, or [TIMED_OUT]. */
    private fun runYtDlp(staging: Path, watchUrl: String, title: String): Int {
        val process = ytDlpDownloadCommand(staging, watchUrl).redirectErrorStream(true).start()
        activeProcess = process
        val lastOutputAt = AtomicLong(System.nanoTime())
        val timedOut = AtomicBoolean(false)
        val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "download-watchdog").apply { isDaemon = true }
        }
        watchdog.scheduleAtFixedRate({
            val idleSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastOutputAt.get())
            if (process.isAlive && idleSeconds >= NO_OUTPUT_TIMEOUT_SECONDS && timedOut.compareAndSet(false, true)) {
                process.destroyForcibly()
            }
        }, 5, 5, TimeUnit.SECONDS)
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    lastOutputAt.set(System.nanoTime())
                    emitLog(line)
                    val percent = PROGRESS_LINE.find(line)?.groupValues?.get(1)?.toFloatOrNull()
                    when {
                        percent != null -> activity(DownloadActivity(title, (percent / 100f).coerceIn(0f, 1f)))
                        line.startsWith("[ExtractAudio]") -> activity(DownloadActivity(title, null))
                    }
                }
            }
            val code = process.waitFor()
            return if (timedOut.get()) TIMED_OUT else code
        } finally {
            watchdog.shutdownNow()
            activeProcess = null
        }
    }

    /** Moves the finished MP3 from [staging] into the library. */
    private fun registerFinished(lib: MusicLibrary, staging: Path, request: DownloadRequest) {
        val mp3 = Files.list(staging).use { files ->
            files.filter { it.fileName.toString().endsWith(".mp3", ignoreCase = true) }.findFirst().orElse(null)
        }
        if (mp3 == null) {
            status("Download finished but no MP3 was produced for \"${request.title}\".")
            return
        }
        status("Checking \"${request.title}\"...")
        val track = lib.addDownloadedTrack(
            stagedFile = mp3,
            title = request.title,
            youtubeId = request.youtubeId,
            sourceUrl = request.watchUrl,
            playlistIds = request.playlistIds,
            newPlaylistName = request.newPlaylistName,
        )
        if (track != null) {
            status("Added \"${track.title}\" to your library.")
        }
    }

    private fun deleteStaging(staging: Path) {
        runCatching {
            if (Files.exists(staging)) {
                Files.walk(staging).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    private fun status(message: String) {
        SwingUtilities.invokeLater {
            statusListeners.forEach { it(message) }
            logListeners.forEach { it(message) }
        }
    }

    private fun emitLog(line: String) {
        SwingUtilities.invokeLater {
            logListeners.forEach { it(line) }
        }
    }

    private fun countChanged() {
        val count = unfinishedJobs.get()
        SwingUtilities.invokeLater {
            countListeners.forEach { it(count) }
        }
    }

    private fun activity(value: DownloadActivity?) {
        SwingUtilities.invokeLater {
            activityListeners.forEach { it(value) }
        }
    }

    private companion object {
        const val TIMED_OUT = -2
        val PROGRESS_LINE = Regex("""\[download\]\s+(\d+(?:\.\d+)?)%""")
    }
}
