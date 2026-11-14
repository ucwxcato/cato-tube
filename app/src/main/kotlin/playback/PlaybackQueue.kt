package org.catosaur.app.playback

import org.catosaur.app.library.Track
import kotlin.random.Random

internal enum class RepeatMode { OFF, ALL, ONE }

/**
 * Play order for the player. It holds copies of tracks, not playlist positions, so reordering or
 * clearing the queue never changes a playlist.
 *
 * [next] is the manual Next button and always moves on. [onTrackEnded] is the automatic path and
 * honors [RepeatMode.ONE]. [RepeatMode.ALL] replays the tracks given to [start].
 */
internal class PlaybackQueue {
    private val history = ArrayList<Track>()
    private val upcoming = ArrayList<Track>()
    private var source: List<Track> = emptyList()

    /** The track that is playing or was last played, or null when the queue is empty or finished. */
    var now: Track? = null
        private set

    var repeat: RepeatMode = RepeatMode.OFF

    /** Whether new queues are shuffled. Turning it on shuffles what is already queued. */
    var shuffle: Boolean = false
        private set

    fun upcoming(): List<Track> = upcoming.toList()

    /**
     * Replaces the queue with [tracks], starting at [index], and returns the track to play.
     * Tracks before [index] are not queued again.
     */
    fun start(tracks: List<Track>, index: Int, random: Random = Random.Default): Track? {
        history.clear()
        upcoming.clear()
        source = tracks.toList()
        if (index !in tracks.indices) {
            now = null
            return null
        }
        now = tracks[index]
        upcoming.addAll(tracks.drop(index + 1))
        if (shuffle) upcoming.shuffle(random)
        return now
    }

    /** Manual Next: moves to the next track even when repeat is [RepeatMode.ONE]. */
    fun next(random: Random = Random.Default): Track? {
        val current = now ?: return null
        if (upcoming.isNotEmpty()) {
            history.add(current)
            now = upcoming.removeAt(0)
            return now
        }
        if (repeat == RepeatMode.ALL && source.isNotEmpty()) {
            history.clear()
            upcoming.addAll(source.drop(1))
            if (shuffle) upcoming.shuffle(random)
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
        now?.let { upcoming.add(0, it) }
        now = last
        return now
    }

    /** Puts [track] at the front of the upcoming list. If nothing is playing, [track] becomes the current track. */
    fun playNext(track: Track) {
        if (now == null) now = track else upcoming.add(0, track)
    }

    /** Adds [track] to the end of the upcoming list. If nothing is playing, [track] becomes the current track. */
    fun addToQueue(track: Track) {
        if (now == null) now = track else upcoming.add(track)
    }

    fun remove(index: Int) {
        if (index in upcoming.indices) upcoming.removeAt(index)
    }

    fun move(from: Int, to: Int) {
        if (from !in upcoming.indices || to !in upcoming.indices || from == to) return
        upcoming.add(to, upcoming.removeAt(from))
    }

    fun clear() {
        upcoming.clear()
    }

    fun setShuffle(on: Boolean, random: Random = Random.Default) {
        shuffle = on
        if (on) upcoming.shuffle(random)
    }
}
