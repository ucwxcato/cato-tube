package org.catosaur.app.playback

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

internal const val WAVEFORM_SAMPLES = 512

internal const val BASS_FIRST_BAND = 0
internal const val BASS_LAST_BAND = 9
internal const val MID_FIRST_BAND = 10
internal const val MID_LAST_BAND = 21
internal const val TREBLE_FIRST_BAND = 22
internal const val TREBLE_LAST_BAND = 31

/**
 * A read-only-to-consumers frame. The feed reuses two instances; readers should finish with a
 * snapshot before the next frame rather than keeping it as durable state.
 */
internal class AudioFeatures internal constructor() {
    val bands = FloatArray(SPECTRUM_BANDS)
    val rawBands = FloatArray(SPECTRUM_BANDS)
    val waveform = FloatArray(WAVEFORM_SAMPLES)

    var energy: Float = 0f
        private set
    var bass: Float = 0f
        private set
    var mid: Float = 0f
        private set
    var treble: Float = 0f
        private set
    var beat: Float = 0f
        private set
    var frame: Long = 0L
        private set
    var active: Boolean = false
        private set

    internal fun assign(
        sourceBands: FloatArray,
        sourceWaveform: FloatArray,
        energy: Float,
        beat: Float,
        frame: Long,
        active: Boolean,
        sourceRawBands: FloatArray = sourceBands,
    ) {
        if (sourceBands !== bands) System.arraycopy(sourceBands, 0, bands, 0, SPECTRUM_BANDS)
        if (sourceRawBands !== rawBands) System.arraycopy(sourceRawBands, 0, rawBands, 0, SPECTRUM_BANDS)
        if (sourceWaveform !== waveform) System.arraycopy(sourceWaveform, 0, waveform, 0, WAVEFORM_SAMPLES)
        this.energy = energy.coerceIn(0f, 1f)
        this.bass = meanBandEnergy(bands, BASS_FIRST_BAND, BASS_LAST_BAND)
        this.mid = meanBandEnergy(bands, MID_FIRST_BAND, MID_LAST_BAND)
        this.treble = meanBandEnergy(bands, TREBLE_FIRST_BAND, TREBLE_LAST_BAND)
        this.beat = beat.coerceIn(0f, 1f)
        this.frame = frame
        this.active = active
    }

    internal fun copyFrom(source: AudioFeatures) {
        System.arraycopy(source.bands, 0, bands, 0, SPECTRUM_BANDS)
        System.arraycopy(source.rawBands, 0, rawBands, 0, SPECTRUM_BANDS)
        System.arraycopy(source.waveform, 0, waveform, 0, WAVEFORM_SAMPLES)
        energy = source.energy
        bass = source.bass
        mid = source.mid
        treble = source.treble
        beat = source.beat
        frame = source.frame
        active = source.active
    }

    internal fun deactivate() {
        energy = 0f
        bass = 0f
        mid = 0f
        treble = 0f
        beat = 0f
        active = false
    }

    internal fun clear(frame: Long) {
        bands.fill(0f)
        rawBands.fill(0f)
        waveform.fill(0f)
        energy = 0f
        bass = 0f
        mid = 0f
        treble = 0f
        beat = 0f
        this.frame = frame
        active = false
    }
}

/** Publishes frames from the playback thread and counts the consumers that need live analysis. */
internal class AudioFeaturesFeed {
    private val first = AudioFeatures()
    private val second = AudioFeatures()
    private val consumers = AtomicInteger()
    private val smoothedBands = FloatArray(SPECTRUM_BANDS)

    @Volatile
    private var current = first

    private var back = second

    val consumerCount: Int get() = consumers.get()
    val hasConsumers: Boolean get() = consumers.get() > 0

    fun snapshot(): AudioFeatures = current

    /** Attaches one consumer and returns the current frame. Do not retain the frame across updates. */
    fun attach(): AudioFeatures {
        consumers.incrementAndGet()
        return current
    }

    fun detach() {
        while (true) {
            val count = consumers.get()
            check(count > 0) { "No audio feature consumer is attached" }
            if (consumers.compareAndSet(count, count - 1)) return
        }
    }

    /** Called only by the playback thread while at least one consumer is attached. */
    fun publish(bands: FloatArray, energy: Float, waveform: WaveformBuffer, beat: Float) {
        val next = back
        waveform.copyTo(next.waveform)
        for (band in smoothedBands.indices) {
            val target = bands[band]
            val rate = if (target > smoothedBands[band]) BAND_ATTACK else BAND_RELEASE
            smoothedBands[band] += (target - smoothedBands[band]) * rate
        }
        next.assign(
            sourceBands = smoothedBands,
            sourceWaveform = next.waveform,
            energy = energy,
            beat = beat,
            frame = current.frame + 1,
            active = true,
            sourceRawBands = bands,
        )
        publish(next)
    }

    /** Paused playback leaves its last visual data available while consumers ease toward rest. */
    fun pause() {
        val next = back
        next.copyFrom(current)
        next.deactivate()
        publish(next)
    }

    /** Clears feature data when a track stops, ends, or is replaced. */
    fun clear() {
        val previous = current
        val next = back
        smoothedBands.fill(0f)
        next.clear(previous.frame)
        publish(next)
    }

    private fun publish(next: AudioFeatures) {
        val previous = current
        current = next
        back = previous
    }

    private companion object {
        const val BAND_ATTACK = 0.55f
        const val BAND_RELEASE = 0.14f
    }
}

internal fun meanBandEnergy(bands: FloatArray, firstBand: Int, lastBand: Int): Float {
    require(firstBand in bands.indices && lastBand in firstBand until bands.size)
    var total = 0f
    for (band in firstBand..lastBand) total += bands[band]
    return total / (lastBand - firstBand + 1)
}

/** Deterministic preview-only frames. The same elapsed time always produces the same signal. */
internal class SimulatedFeatures {
    private val features = AudioFeatures()
    private val bands = FloatArray(SPECTRUM_BANDS)
    private val waveform = FloatArray(WAVEFORM_SAMPLES)

    fun at(elapsedMs: Long): AudioFeatures {
        val timeMs = elapsedMs.coerceAtLeast(0L)
        val seconds = timeMs / 1000.0
        val beatPhaseMs = timeMs % SIMULATED_BEAT_PERIOD_MS
        val beat = (1f - beatPhaseMs / SIMULATED_BEAT_DECAY_MS.toFloat()).coerceIn(0f, 1f)
        val energy = (0.42 + 0.18 * sin(2.0 * PI * seconds * 0.7)).toFloat().coerceIn(0f, 1f)

        for (band in bands.indices) {
            val position = band.toDouble() / (bands.lastIndex.coerceAtLeast(1))
            val sweep = 0.5 + 0.5 * sin(2.0 * PI * (seconds * 0.22 + position * 1.7))
            val lowAccent = 1.0 - position * 0.55
            bands[band] = (0.08 + 0.46 * sweep * lowAccent).toFloat()
        }
        for (sample in waveform.indices) {
            val position = sample.toDouble() / waveform.size
            waveform[sample] = (energy * sin(2.0 * PI * (position * 5.0 + seconds * 2.0))).toFloat()
        }

        features.assign(
            sourceBands = bands,
            sourceWaveform = waveform,
            energy = energy,
            beat = beat,
            frame = timeMs / SIMULATED_FRAME_MS,
            active = true,
        )
        return features
    }

    private companion object {
        const val SIMULATED_BEAT_PERIOD_MS = 500L
        const val SIMULATED_BEAT_DECAY_MS = 200L
        const val SIMULATED_FRAME_MS = 23L
    }
}
