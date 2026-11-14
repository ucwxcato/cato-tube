package org.catosaur.app.playback

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
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
        val output = FloatArray(SPECTRUM_BANDS) { 1f }
        val bands = SpectrumAnalyzer().analyze(ByteArray(4096), 4096, output)
        assertSame(output, bands, "analysis should write into the provided array")
        assertTrue(bands.all { it == 0f }, "silence should give zero in every band")
    }

    @Test
    fun toneLandsInTheBandNearItsFrequency() {
        val bands = SpectrumAnalyzer().analyze(stereoSine(1000.0, 0.5), 4096, FloatArray(SPECTRUM_BANDS))
        val peak = bands.indices.maxBy { bands[it] }
        // Band 17 is centred near 1.07 kHz on the 40 Hz to 16 kHz log scale; one band of slack either way.
        assertTrue(peak in 16..18, "1 kHz tone peaked in band $peak")
        assertTrue(bands[peak] > 0.5f, "a 0.5 amplitude tone should read well above the floor")
    }

    @Test
    fun louderToneReadsHigher() {
        val quiet = SpectrumAnalyzer().analyze(stereoSine(1000.0, 0.05), 4096, FloatArray(SPECTRUM_BANDS))
        val loud = SpectrumAnalyzer().analyze(stereoSine(1000.0, 0.5), 4096, FloatArray(SPECTRUM_BANDS))
        assertTrue(loud.max() > quiet.max(), "louder input should give a higher band level")
    }

    @Test
    fun oneHundredHertzToneLandsInBassGroup() {
        val bands = SpectrumAnalyzer().analyze(stereoSine(100.0, 0.5), 4096, FloatArray(SPECTRUM_BANDS))
        val peak = bands.indices.maxBy { bands[it] }

        assertTrue(peak in BASS_FIRST_BAND..BASS_LAST_BAND, "100 Hz tone peaked in band $peak")
        assertTrue(meanBandEnergy(bands, BASS_FIRST_BAND, BASS_LAST_BAND) > meanBandEnergy(bands, MID_FIRST_BAND, MID_LAST_BAND))
    }

    @Test
    fun fourKilohertzToneLandsInTrebleGroup() {
        val bands = SpectrumAnalyzer().analyze(stereoSine(4000.0, 0.5), 4096, FloatArray(SPECTRUM_BANDS))
        val peak = bands.indices.maxBy { bands[it] }

        assertTrue(peak in TREBLE_FIRST_BAND..TREBLE_LAST_BAND, "4 kHz tone peaked in band $peak")
        assertTrue(meanBandEnergy(bands, TREBLE_FIRST_BAND, TREBLE_LAST_BAND) > meanBandEnergy(bands, BASS_FIRST_BAND, BASS_LAST_BAND))
    }

    @Test
    fun featureBandGroupBoundsStayPinned() {
        assertEquals(0..9, BASS_FIRST_BAND..BASS_LAST_BAND)
        assertEquals(10..21, MID_FIRST_BAND..MID_LAST_BAND)
        assertEquals(22..31, TREBLE_FIRST_BAND..TREBLE_LAST_BAND)
    }
}
