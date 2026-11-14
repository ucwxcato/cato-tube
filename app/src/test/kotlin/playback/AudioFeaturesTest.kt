package org.catosaur.app.playback

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioFeaturesTest {
    @Test
    fun feedPublishesPreallocatedFramesOnlyForAttachedConsumers() {
        val feed = AudioFeaturesFeed()
        val bands = FloatArray(SPECTRUM_BANDS) { 0.4f }
        val waveform = WaveformBuffer()
        val pcm = ByteArray(WAVEFORM_SAMPLES * 4)
        waveform.write(pcm, pcm.size)

        assertFalse(feed.hasConsumers)
        feed.attach()
        assertEquals(1, feed.consumerCount)
        assertTrue(feed.hasConsumers)

        feed.publish(bands, 0.6f, waveform, 0.8f)
        val first = feed.snapshot()
        assertTrue(first.active)
        assertEquals(1L, first.frame)
        assertEquals(0.6f, first.energy)
        assertEquals(0.4f * 0.55f, first.bands[0], 0.00001f)
        assertEquals(0.4f, first.rawBands[0], 0.00001f)
        assertEquals(0.8f, first.beat)

        feed.publish(bands, 0.5f, waveform, 0f)
        val second = feed.snapshot()
        assertTrue(second.active)
        assertEquals(2L, second.frame)
        assertTrue(first !== second)

        feed.pause()
        assertFalse(feed.snapshot().active)
        assertEquals(0f, feed.snapshot().energy)
        assertTrue(feed.snapshot().bands.any { it > 0f })
        assertEquals(0.4f, feed.snapshot().rawBands[0], 0.00001f)

        feed.clear()
        assertFalse(feed.snapshot().active)
        assertTrue(feed.snapshot().bands.all { it == 0f })
        assertTrue(feed.snapshot().rawBands.all { it == 0f })
        assertTrue(feed.snapshot().waveform.all { it == 0f })

        feed.detach()
        assertEquals(0, feed.consumerCount)
        assertFalse(feed.hasConsumers)
    }

    @Test
    fun simulatedFeaturesAreDeterministicForAnElapsedTime() {
        val first = SimulatedFeatures().at(1750L)
        val second = SimulatedFeatures().at(1750L)

        assertContentEquals(first.bands, second.bands)
        assertContentEquals(first.waveform, second.waveform)
        assertEquals(first.energy, second.energy)
        assertEquals(first.beat, second.beat)
        assertEquals(first.frame, second.frame)
        assertTrue(first.active)
    }
}
