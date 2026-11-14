package org.catosaur.app.playback

import org.catosaur.app.library.Track
import kotlin.random.Random

internal enum class RepeatMode { OFF, ALL, ONE }

/**
 * Play order for the player. It holds copies of tracks, not playlist positions, so reordering or
 * clearing the queue never changes a playlist.
 *
 * Two lists make up the order. [queued] holds songs the user added with Play Next or Add to Queue, and
 * the Queue tab shows only that list. [rest] holds what is left of the playlist or library that was
 * started, and it plays after the queued songs.
 *
 * [next] is the manual Next button and always moves on. [onTrackEnded] is the automatic path and
 * honors [RepeatMode.ONE]. [RepeatMode.ALL] replays the tracks given to [start].
 */
internal class PlaybackQueue {
    private val history = ArrayList<Track>()
    private val queued = ArrayList<Track>()
    private val rest = ArrayList<Track>()
    private var source: List<Track> = emptyList()

    /** The track that is playing or was last played, or null when the queue is empty or finished. */
    var now: Track? = null
        private set

    var repeat: RepeatMode = RepeatMode.OFF

    /** Whether new queues are shuffled. Turning it on shuffles what is already queued. */
    var shuffle: Boolean = false
        private set

    /** Everything that plays after the current track, in play order: queued songs first, then the rest. */
    fun upcoming(): List<Track> = queued + rest

    /** Songs the user added, in play order. This is what the Queue tab shows. */
    fun queued(): List<Track> = queued.toList()

    /**
     * Replaces the queue with [tracks], starting at [index], and returns the track to play.
     * Tracks before [index] are not queued again.
     */
    fun start(tracks: List<Track>, index: Int, random: Random = Random.Default): Track? {
        history.clear()
        queued.clear()
        rest.clear()
        source = tracks.toList()
        if (index !in tracks.indices) {
            now = null
            return null
        }
        now = tracks[index]
        rest.addAll(tracks.drop(index + 1))
        if (shuffle) rest.shuffle(random)
        return now
    }

    /** Manual Next: moves to the next track even when repeat is [RepeatMode.ONE]. */
    fun next(random: Random = Random.Default): Track? {
        val current = now ?: return null
        takeFollowing()?.let {
            history.add(current)
            now = it
            return now
        }
        if (repeat == RepeatMode.ALL && source.isNotEmpty()) {
            history.clear()
            rest.addAll(source.drop(1))
            if (shuffle) rest.shuffle(random)
            now = source.first()
            return now
        }
        history.add(current)
        now = null
        return null
    }

    /** Automatic advance when a track ends: repeat-one replays the same track. */
    fun onTrackEnded(random: Random = Random.Default): Track? {
        if (repeat == RepeatMode.ONE) return now
        return next(random)
    }

    /**
     * Manual Previous: returns to the track before the current one. With nothing before it, the
     * current track is returned so the caller can restart it.
     */
    fun previous(): Track? {
        val last = history.removeLastOrNull() ?: return now
        now?.let { rest.add(0, it) }
        now = last
        return now
    }

    /** Puts [track] at the front of the queue. If nothing is playing, [track] becomes the current track. */
    fun playNext(track: Track) {
        if (now == null) now = track else queued.add(0, track)
    }

    /** Adds [track] to the end of the queue. If nothing is playing, [track] becomes the current track. */
    fun addToQueue(track: Track) {
        if (now == null) now = track else queued.add(track)
    }

    /** Plays the queued track at [index] now. Queued tracks ahead of it are dropped; the playlist rest is kept. */
    fun skipTo(index: Int): Track? {
        if (index !in queued.indices) return null
        now?.let { history.add(it) }
        repeat(index) { history.add(queued.removeAt(0)) }
        now = queued.removeAt(0)
        return now
    }

    fun remove(index: Int) {
        if (index in queued.indices) queued.removeAt(index)
    }

    fun move(from: Int, to: Int) {
        if (from !in queued.indices || to !in queued.indices || from == to) return
        queued.add(to, queued.removeAt(from))
    }

    /** Removes every queued song. The rest of the playlist keeps playing. */
    fun clear() {
        queued.clear()
    }

    fun setShuffle(on: Boolean, random: Random = Random.Default) {
        shuffle = on
        if (on) rest.shuffle(random)
    }

    private fun takeFollowing(): Track? = when {
        queued.isNotEmpty() -> queued.removeAt(0)
        rest.isNotEmpty() -> rest.removeAt(0)
        else -> null
    }
}
