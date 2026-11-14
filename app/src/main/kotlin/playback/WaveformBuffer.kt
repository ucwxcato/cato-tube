package org.catosaur.app.playback

/** Playback-thread ring buffer containing the most recent mono samples. */
internal class WaveformBuffer {
    private val samples = FloatArray(WAVEFORM_SAMPLES)
    private var writeIndex = 0
    private var count = 0

    /** Writes the newest mono frames from little-endian 16-bit stereo PCM. */
    fun write(pcm: ByteArray, byteCount: Int) {
        require(byteCount in 0..pcm.size) { "byteCount must be within the PCM buffer" }
        val frames = byteCount / BYTES_PER_FRAME
        val firstFrame = (frames - WAVEFORM_SAMPLES).coerceAtLeast(0)
        for (frame in firstFrame until frames) {
            val index = frame * BYTES_PER_FRAME
            val left = readSample(pcm, index)
            val right = readSample(pcm, index + 2)
            samples[writeIndex] = ((left + right) * 0.5f / SAMPLE_SCALE).coerceIn(-1f, 1f)
            writeIndex = (writeIndex + 1) % WAVEFORM_SAMPLES
            count = (count + 1).coerceAtMost(WAVEFORM_SAMPLES)
        }
    }

    /** Copies samples in chronological order, right-aligning a partially-filled buffer. */
    fun copyTo(output: FloatArray) {
        require(output.size == WAVEFORM_SAMPLES) { "output must contain $WAVEFORM_SAMPLES samples" }
        output.fill(0f)
        val copied = minOf(count, output.size)
        var sourceIndex = (writeIndex - copied + WAVEFORM_SAMPLES) % WAVEFORM_SAMPLES
        val outputStart = output.size - copied
        for (index in 0 until copied) {
            output[outputStart + index] = samples[sourceIndex]
            sourceIndex = (sourceIndex + 1) % WAVEFORM_SAMPLES
        }
    }

    fun clear() {
        samples.fill(0f)
        writeIndex = 0
        count = 0
    }

    private fun readSample(pcm: ByteArray, index: Int): Float {
        val value = (pcm[index + 1].toInt() shl 8) or (pcm[index].toInt() and 0xff)
        return value.toShort().toFloat()
    }

    private companion object {
        const val BYTES_PER_FRAME = 4
        const val SAMPLE_SCALE = 32768f
    }
}
