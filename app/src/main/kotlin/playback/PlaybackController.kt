package org.catosaur.app.playback

import org.catosaur.app.library.Track
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/** What the player bar shows. Listeners receive a new value on every change. */
internal data class PlayerState(
    val track: Track?,
    val playing: Boolean,
    val positionMs: Long,
    val volume: Float,
    val muted: Boolean,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    /** Loudest recent output, roughly 0 to 1. Zero when paused or stopped. Drives the playing-row pulse. */
    val level: Float = 0f,
)

private const val RESTART_THRESHOLD_MS = 3000L
private val SILENT_SPECTRUM = FloatArray(SPECTRUM_BANDS)

/**
 * Owns playback: the audio engine and the play order. Public methods are synchronized, and
 * [PlaybackListener] callbacks arrive on the playback thread. Both paths share one lock, and the
 * engine's own calls never block, so the two threads cannot deadlock.
 *
 * [resolve] maps a track to its file. It returns null when the library is not open.
 * State and error listeners run on whichever thread raised the change, so the UI must marshal them.
 */
internal class PlaybackController(private val resolve: (Track) -> Path?) : PlaybackListener {
    private val queue = PlaybackQueue()
    private val player = AudioPlayer(this)
    private val stateListeners = CopyOnWriteArrayList<(PlayerState) -> Unit>()
    private val errorListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val queueListeners = CopyOnWriteArrayList<(Track?, List<Track>) -> Unit>()
    private var notifiedNow: Track? = null
    private var notifiedUpcoming: List<Track> = emptyList()

    /** True from play until the track ends, playback fails, or the queue runs out. Pausing keeps it true. */
    private var active = false
    @Volatile private var playing = false
    private var positionMs = 0L
    private var level = 0f
    private var volume = 1f
    private var muted = false

    fun addStateListener(listener: (PlayerState) -> Unit) {
        stateListeners += listener
    }

    fun addErrorListener(listener: (String) -> Unit) {
        errorListeners += listener
    }

    /** Called with the current track and the upcoming list whenever either changes. Runs on the caller's thread. */
    fun addQueueListener(listener: (Track?, List<Track>) -> Unit) {
        queueListeners += listener
    }

    @Synchronized
    fun currentQueue(): Pair<Track?, List<Track>> = queue.now to queue.queued()

    /** Plays [track] next. Starts playback when nothing is queued. */
    @Synchronized
    fun playNextInQueue(track: Track) {
        val wasEmpty = queue.now == null
        queue.playNext(track)
        if (wasEmpty) playCurrent(0L) else publish()
    }

    /** Adds [track] to the end of the queue. Starts playback when nothing is queued. */
    @Synchronized
    fun addToQueue(track: Track) {
        val wasEmpty = queue.now == null
        queue.addToQueue(track)
        if (wasEmpty) playCurrent(0L) else publish()
    }

    /** Plays the upcoming track at [index] now and drops the tracks ahead of it. */
    @Synchronized
    fun playQueueItem(index: Int) {
        if (queue.skipTo(index) == null) return
        playCurrent(0L)
    }

    @Synchronized
    fun removeFromQueue(index: Int) {
        queue.remove(index)
        publish()
    }

    @Synchronized
    fun moveInQueue(from: Int, to: Int) {
        queue.move(from, to)
        publish()
    }

    @Synchronized
    fun clearQueue() {
        queue.clear()
        publish()
    }

    /** Replaces the queue with [tracks], starts the track at [index], and plays it. */
    @Synchronized
    fun playTracks(tracks: List<Track>, index: Int) {
        if (queue.start(tracks, index) == null) return
        playCurrent(0L)
    }

    /** Plays [tracks] in a random order. */
    @Synchronized
    fun playShuffled(tracks: List<Track>) {
        if (queue.start(tracks.shuffled(), 0) == null) return
        playCurrent(0L)
    }

    @Synchronized
    fun togglePlayPause() {
        when {
            queue.now == null -> return
            active && playing -> player.pause()
            active -> player.resume()
            else -> playCurrent(0L)
        }
    }

    /** Manual Next. */
    @Synchronized
    fun next() {
        advance(automatic = false)
    }

    /** Previous restarts the current track when it is past the first few seconds, otherwise goes back one track. */
    @Synchronized
    fun previous() {
        if (positionMs > RESTART_THRESHOLD_MS || queue.previous() == null) {
            if (queue.now != null) playCurrent(0L)
        } else {
            playCurrent(0L)
        }
    }

    /** Seeks the current track to [fraction] of its length. Starts playback if it was paused or ended. */
    @Synchronized
    fun seekTo(fraction: Float) {
        val track = queue.now ?: return
        val duration = track.durationMs ?: return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        if (active) {
            positionMs = target
            resolve(track)?.let { player.play(it, target) }
        } else {
            playCurrent(target)
        }
    }

    @Synchronized
    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        player.setVolume(volume)
        publish()
    }

    @Synchronized
    fun setMuted(value: Boolean) {
        muted = value
        player.setMuted(value)
        publish()
    }

    @Synchronized
    fun toggleShuffle() {
        queue.setShuffle(!queue.shuffle)
        publish()
    }

    @Synchronized
    fun cycleRepeat() {
        queue.repeat = when (queue.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        publish()
    }

    /** Band levels for the visualizer. Zeros unless audio is actually playing, so the bars settle when paused. Safe to call from the EDT. */
    fun spectrum(): FloatArray = if (playing) player.spectrum() else SILENT_SPECTRUM

    /** Attaches one visual or mascot consumer to the shared audio analysis feed. */
    fun attachFeatures(): AudioFeatures = player.attachFeatures()

    /** Detaches one previously attached consumer. */
    fun detachFeatures() = player.detachFeatures()

    /** Latest shared feature frame. Consumers should not retain it across frames. */
    fun audioFeatures(): AudioFeatures = player.audioFeatures()

    /** Stops playback and releases the audio device. Call once, when the window closes. */
    fun close() {
        player.close()
    }

    @Synchronized
    override fun onPosition(positionMs: Long) {
        this.positionMs = positionMs
        level = player.takeLevel()
        publish()
    }

    @Synchronized
    override fun onPlayingChanged(playing: Boolean) {
        this.playing = playing
        if (!playing) level = 0f
        publish()
    }

    @Synchronized
    override fun onEnded() {
        advance(automatic = true)
    }

    @Synchronized
    override fun onError(file: Path, message: String) {
        val title = queue.now?.title ?: file.fileName.toString()
        errorListeners.forEach { it("$title: $message") }
        advance(automatic = false)
    }

    private fun playCurrent(startMs: Long) {
        val track = queue.now ?: return
        val file = resolve(track)
        if (file == null) {
            errorListeners.forEach { it("${track.title}: the library is not open.") }
            advance(automatic = false)
            return
        }
        active = true
        positionMs = startMs
        player.play(file, startMs)
        publish()
    }

    /**
     * Moves to the next track. [automatic] is true when the previous track ended on its own, which
     * honors repeat-one. Errors and manual skips pass false, so a broken file cannot loop forever.
     */
    private fun advance(automatic: Boolean) {
        val next = if (automatic) queue.onTrackEnded() else queue.next()
        if (next != null) {
            playCurrent(0L)
        } else {
            active = false
            playing = false
            positionMs = 0L
            level = 0f
            player.stop()
            publish()
        }
    }

    private fun publish() {
        val state = PlayerState(
            track = queue.now,
            playing = playing,
            positionMs = positionMs,
            volume = volume,
            muted = muted,
            shuffle = queue.shuffle,
            repeat = queue.repeat,
            level = level,
        )
        stateListeners.forEach { it(state) }
        val now = queue.now
        val upcoming = queue.queued()
        if (now != notifiedNow || upcoming != notifiedUpcoming) {
            notifiedNow = now
            notifiedUpcoming = upcoming
            queueListeners.forEach { it(now, upcoming) }
        }
    }
}
