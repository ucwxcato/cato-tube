package org.catosaur.app.playback

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class SpectrumAnalyzerTest {
    private fun stereoSine(freq: Double, amplitude: Double): ByteArray {
        val frames = 1024
        val bytes = ByteArray(frames * 4)
        for (n in 0 until frames) {
            val value = (sin(2 * PI * freq * n / 44100.0) * amplitude * 32767).toInt()
            for (channel in 0 until 2) {
                val i = n * 4 + channel * 2
                bytes[i] = value.toByte()
                bytes[i + 1] = (value shr 8).toByte()
            }
        }
        return bytes
    }

    @Test
    fun silenceReadsAsZero() {
        val bands = SpectrumAnalyzer().analyze(ByteArray(4096), 4096)
        assertTrue(bands.all { it == 0f }, "silence should give zero in every band")
    }

    @Test
    fun toneLandsInTheBandNearItsFrequency() {
        val bands = SpectrumAnalyzer().analyze(stereoSine(1000.0, 0.5), 4096)
        val peak = bands.indices.maxBy { bands[it] }
        // Band 17 is centred near 1.07 kHz on the 40 Hz to 16 kHz log scale; one band of slack either way.
        assertTrue(peak in 16..18, "1 kHz tone peaked in band $peak")
        assertTrue(bands[peak] > 0.5f, "a 0.5 amplitude tone should read well above the floor")
    }

    @Test
    fun louderToneReadsHigher() {
        val quiet = SpectrumAnalyzer().analyze(stereoSine(1000.0, 0.05), 4096)
        val loud = SpectrumAnalyzer().analyze(stereoSine(1000.0, 0.5), 4096)
        assertTrue(loud.max() > quiet.max(), "louder input should give a higher band level")
    }
}
