package org.catosaur.app.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Number of frequency bands in the spectrum. */
internal const val SPECTRUM_BANDS = 32

private const val ANALYSIS_FRAMES = 1024
private const val SAMPLE_RATE_HZ = 44100.0
private const val LOWEST_HZ = 40.0
private const val HIGHEST_HZ = 16000.0
private const val FLOOR_DB = -62.0
private const val RANGE_DB = 52.0

/**
 * Splits 16-bit stereo PCM into [SPECTRUM_BANDS] log-spaced bands. The input is mixed to mono, windowed
 * with a Hann window, and measured with one Goertzel filter per band. Each band is returned as 0 to 1 on a
 * dB scale. Not thread-safe; the playback thread owns the instance.
 */
internal class SpectrumAnalyzer {
    private val window = DoubleArray(ANALYSIS_FRAMES) { n ->
        0.5 - 0.5 * cos(2.0 * PI * n / (ANALYSIS_FRAMES - 1))
    }
    private val coefficients = DoubleArray(SPECTRUM_BANDS) { band ->
        val fraction = band.toDouble() / (SPECTRUM_BANDS - 1)
        val freq = LOWEST_HZ * (HIGHEST_HZ / LOWEST_HZ).pow(fraction)
        2.0 * cos(2.0 * PI * freq / SAMPLE_RATE_HZ)
    }
    private val mono = DoubleArray(ANALYSIS_FRAMES)

    /** Analyzes the first [count] bytes of [pcm]. Returns a new array of band levels, each 0 to 1. */
    fun analyze(pcm: ByteArray, count: Int): FloatArray {
        val frames = minOf(ANALYSIS_FRAMES, count / 4)
        for (n in 0 until frames) {
            val i = n * 4
            val left = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toDouble()
            val right = ((pcm[i + 3].toInt() shl 8) or (pcm[i + 2].toInt() and 0xff)).toDouble()
            mono[n] = (left + right) * 0.5 / 32768.0 * window[n]
        }
        for (n in frames until ANALYSIS_FRAMES) mono[n] = 0.0

        val bands = FloatArray(SPECTRUM_BANDS)
        for (band in 0 until SPECTRUM_BANDS) {
            val coeff = coefficients[band]
            var s1 = 0.0
            var s2 = 0.0
            for (n in 0 until ANALYSIS_FRAMES) {
                val s0 = mono[n] + coeff * s1 - s2
                s2 = s1
                s1 = s0
            }
            val power = max(0.0, s1 * s1 + s2 * s2 - coeff * s1 * s2)
            // The Hann window averages to one half, so a sine of amplitude A reads as A * N / 4.
            val amplitude = 4.0 * sqrt(power) / ANALYSIS_FRAMES
            val db = 20.0 * log10(max(amplitude, 1e-9))
            bands[band] = ((db - FLOOR_DB) / RANGE_DB).coerceIn(0.0, 1.0).toFloat()
        }
        return bands
    }
}
