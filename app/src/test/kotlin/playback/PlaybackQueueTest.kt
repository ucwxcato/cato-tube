package org.catosaur.app.playback

import org.catosaur.app.library.Track
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackQueueTest {
    private fun track(name: String) = Track(id = name, title = name, relativePath = "songs/$name.mp3", dateAddedEpochMs = 0)

    private val a = track("a")
    private val b = track("b")
    private val c = track("c")
    private val d = track("d")

    @Test
    fun nextWalksTheListAndEndsWithoutRepeat() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b, c), 0)
        assertEquals(b.id, queue.next()?.id)
        assertEquals(c.id, queue.next()?.id)
        assertNull(queue.next())
        assertNull(queue.now)
    }

    @Test
    fun startingInTheMiddleDoesNotQueueEarlierTracks() {
        val queue = PlaybackQueue()
        assertEquals(c.id, queue.start(listOf(a, b, c, d), 2)?.id)
        assertEquals(listOf(d.id), queue.upcoming().map { it.id })
    }

    @Test
    fun previousReturnsToTheEarlierTrackAndKeepsTheCurrentOneQueued() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b, c), 0)
        queue.next()
        queue.next()
        assertEquals(b.id, queue.previous()?.id)
        assertEquals(listOf(c.id), queue.upcoming().map { it.id })
    }

    @Test
    fun previousAtTheFirstTrackReturnsItSoItCanRestart() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b), 0)
        assertEquals(a.id, queue.previous()?.id)
    }

    @Test
    fun repeatOneReplaysOnAutomaticAdvanceButManualNextMoves() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b), 0)
        queue.repeat = RepeatMode.ONE
        assertEquals(a.id, queue.onTrackEnded()?.id)
        assertEquals(b.id, queue.next()?.id)
    }

    @Test
    fun repeatAllStartsTheWholeListAgainAfterTheLastTrack() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b), 0)
        queue.repeat = RepeatMode.ALL
        assertEquals(b.id, queue.onTrackEnded()?.id)
        assertEquals(a.id, queue.onTrackEnded()?.id)
    }

    @Test
    fun seededShuffleIsRepeatableAndKeepsEveryTrack() {
        val first = PlaybackQueue()
        first.start(listOf(a, b, c, d), 0)
        first.setShuffle(true, Random(42))
        val second = PlaybackQueue()
        second.start(listOf(a, b, c, d), 0)
        second.setShuffle(true, Random(42))

        assertEquals(first.upcoming().map { it.id }, second.upcoming().map { it.id })
        assertEquals(setOf("b", "c", "d"), first.upcoming().map { it.id }.toSet())
    }

    @Test
    fun playNextAndAddToQueuePlaceTracksAheadOfThePlaylistRest() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b), 0)
        queue.addToQueue(c)
        queue.playNext(d)
        assertEquals(listOf(d.id, c.id), queue.queued().map { it.id })
        assertEquals(listOf(d.id, c.id, b.id), queue.upcoming().map { it.id })
    }

    @Test
    fun reorderingAndRemovingChangeOnlyTheQueue() {
        val source = listOf(a, b, c, d)
        val queue = PlaybackQueue()
        queue.start(source, 0)
        queue.addToQueue(c)
        queue.addToQueue(d)
        queue.move(1, 0)
        queue.remove(1)
        assertEquals(listOf(d.id), queue.queued().map { it.id })
        assertEquals(listOf("a", "b", "c", "d"), source.map { it.id })
    }

    @Test
    fun playlistSongsAreNotInTheQueueUnlessAddedByHand() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b, c), 0)
        assertEquals(emptyList(), queue.queued())
        assertEquals(listOf(b.id, c.id), queue.upcoming().map { it.id })
    }

    @Test
    fun clearRemovesQueuedSongsAndLeavesThePlaylistRunning() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b, c), 0)
        queue.addToQueue(d)
        queue.clear()
        assertEquals(a.id, queue.now?.id)
        assertEquals(emptyList(), queue.queued())
        assertEquals(listOf(b.id, c.id), queue.upcoming().map { it.id })
    }

    @Test
    fun skipToPlaysTheChosenQueuedTrackAndDropsTheOnesAheadOfIt() {
        val queue = PlaybackQueue()
        queue.start(listOf(a), 0)
        queue.addToQueue(b)
        queue.addToQueue(c)
        queue.addToQueue(d)
        assertEquals(c.id, queue.skipTo(1)?.id)
        assertEquals(listOf(d.id), queue.queued().map { it.id })
    }

    @Test
    fun previousAfterSkipGoesToTheSkippedTrack() {
        val queue = PlaybackQueue()
        queue.start(listOf(a), 0)
        queue.addToQueue(b)
        queue.addToQueue(c)
        queue.skipTo(1)
        assertEquals(c.id, queue.now?.id)
        assertEquals(b.id, queue.previous()?.id)
    }

    @Test
    fun skipToOutOfRangeChangesNothing() {
        val queue = PlaybackQueue()
        queue.start(listOf(a, b), 0)
        assertNull(queue.skipTo(5))
        assertEquals(a.id, queue.now?.id)
        assertEquals(listOf(b.id), queue.upcoming().map { it.id })
    }
}
