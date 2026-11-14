package org.catosaur.app.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class WaveformBufferTest {
    @Test
    fun copiesNewestSamplesInChronologicalOrder() {
        val buffer = WaveformBuffer()
        val pcm = ByteArray(FRAME_COUNT * BYTES_PER_FRAME)
        for (frame in 0 until FRAME_COUNT) {
            val sample = frame - 300
            for (channel in 0..1) {
                val index = frame * BYTES_PER_FRAME + channel * 2
                pcm[index] = sample.toByte()
                pcm[index + 1] = (sample shr 8).toByte()
            }
        }
        buffer.write(pcm, pcm.size)

        val output = FloatArray(WAVEFORM_SAMPLES)
        buffer.copyTo(output)

        assertEquals((FRAME_COUNT - WAVEFORM_SAMPLES - 300) / 32768f, output.first(), 0.00001f)
        assertEquals((FRAME_COUNT - 1 - 300) / 32768f, output.last(), 0.00001f)
    }

    @Test
    fun clearReturnsAnEmptyWaveform() {
        val buffer = WaveformBuffer()
        buffer.write(byteArrayOf(0, 64, 0, 64), 4)
        buffer.clear()

        val output = FloatArray(WAVEFORM_SAMPLES)
        buffer.copyTo(output)

        assertEquals(0f, output.max())
        assertEquals(0f, output.min())
    }

    private companion object {
        const val FRAME_COUNT = 600
        const val BYTES_PER_FRAME = 4
    }
}
