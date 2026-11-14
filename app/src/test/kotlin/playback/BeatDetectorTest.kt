package org.catosaur.app.playback

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatDetectorTest {
    @Test
    fun detectsEachClickInA120BpmTrainWithinOneFrame() {
        val detector = BeatDetector()
        val frameMs = 23L
        var timeMs = 0L
        repeat(44) {
            detector.process(0.08f, timeMs)
            timeMs += frameMs
        }

        val detectedTimes = mutableListOf<Long>()
        val framesPerBeat = 22
        repeat(framesPerBeat * 8) { frame ->
            val bass = if (frame % framesPerBeat == 0) 0.9f else 0.08f
            if (detector.process(bass, timeMs) == 1f) detectedTimes += timeMs
            timeMs += frameMs
        }

        assertEquals(8, detectedTimes.size, "each click should produce one beat")
        detectedTimes.forEachIndexed { index, detected ->
            val expected = 44L * frameMs + index * framesPerBeat * frameMs
            assertTrue(abs(detected - expected) <= frameMs, "click $index was detected at $detected ms, expected near $expected ms")
        }
    }

    @Test
    fun steadyToneDoesNotProduceRepeatingBeatsAfterWarmup() {
        val detector = BeatDetector()
        var timeMs = 0L
        repeat(100) {
            detector.process(0.35f, timeMs)
            timeMs += 23L
        }

        val pulses = (0 until 500).map {
            detector.process(0.35f, timeMs).also { timeMs += 23L }
        }

        assertTrue(pulses.none { it == 1f }, "steady bass should not retrigger the detector")
    }

    @Test
    fun syntheticWhiteNoiseStaysBelowFalseTriggerLimitForSixtySeconds() {
        val analyzer = SpectrumAnalyzer()
        val detector = BeatDetector()
        val random = kotlin.random.Random(1102)
        val pcm = ByteArray(4096)
        val bands = FloatArray(SPECTRUM_BANDS)
        var timeMs = 0L
        var falseTriggers = 0
        val warmupFrames = framesForSeconds(1)
        val frames = warmupFrames + framesForSeconds(60)

        repeat(frames) { frame ->
            fillWhiteNoise(pcm, random)
            analyzer.analyze(pcm, pcm.size, bands)
            val bass = meanBandEnergy(bands, BASS_FIRST_BAND, BASS_LAST_BAND)
            val pulse = detector.process(bass, timeMs)
            if (frame >= warmupFrames && pulse == 1f) falseTriggers++
            timeMs = (frame + 1L) * ANALYSIS_FRAMES * 1000L / SAMPLE_RATE_HZ
        }

        println("Synthetic white-noise false triggers in 60 s: $falseTriggers")
        assertTrue(falseTriggers < MAX_FALSE_TRIGGERS_IN_60_SECONDS, "$falseTriggers false triggers exceeded the limit")
    }

    private fun fillWhiteNoise(pcm: ByteArray, random: kotlin.random.Random) {
        for (frame in 0 until pcm.size / BYTES_PER_FRAME) {
            val sample = random.nextInt(-8192, 8192)
            for (channel in 0..1) {
                val index = frame * BYTES_PER_FRAME + channel * 2
                pcm[index] = sample.toByte()
                pcm[index + 1] = (sample shr 8).toByte()
            }
        }
    }

    private fun framesForSeconds(seconds: Int): Int =
        ((seconds * SAMPLE_RATE_HZ + ANALYSIS_FRAMES - 1) / ANALYSIS_FRAMES).toInt()

    private companion object {
        const val ANALYSIS_FRAMES = 1024L
        const val SAMPLE_RATE_HZ = 44100L
        const val BYTES_PER_FRAME = 4
        const val MAX_FALSE_TRIGGERS_IN_60_SECONDS = 2
    }
}
