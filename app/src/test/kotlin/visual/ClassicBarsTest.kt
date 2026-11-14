package org.catosaur.app.visual

import org.catosaur.app.playback.AudioFeatures
import org.catosaur.app.playback.SPECTRUM_BANDS
import org.catosaur.app.playback.WAVEFORM_SAMPLES
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassicBarsTest {
    @Test
    fun easingUsesRawBandsAndSettlesToRest() {
        val rawBands = FloatArray(SPECTRUM_BANDS) { 0.8f }
        val smoothedBands = FloatArray(SPECTRUM_BANDS) { 0.22f }
        val features = AudioFeatures().apply {
            assign(
                sourceBands = smoothedBands,
                sourceWaveform = FloatArray(WAVEFORM_SAMPLES),
                energy = 0.4f,
                beat = 0f,
                frame = 1L,
                active = true,
                sourceRawBands = rawBands,
            )
        }
        val renderer = ClassicBars()
        val settings = VisualizerSettings()

        repeat(8) { assertTrue(renderer.update(features, settings)) }
        assertFalse(renderer.update(features, settings))

        val inactiveFeatures = AudioFeatures()
        repeat(40) { renderer.update(inactiveFeatures, settings) }
        assertFalse(renderer.update(inactiveFeatures, settings))
    }

    @Test
    fun barsEaseOutWhenAnotherModeIsSelected() {
        val bands = FloatArray(SPECTRUM_BANDS) { 0.8f }
        val features = AudioFeatures().apply {
            assign(
                sourceBands = bands,
                sourceWaveform = FloatArray(WAVEFORM_SAMPLES),
                energy = 0.4f,
                beat = 0f,
                frame = 1L,
                active = true,
            )
        }
        val renderer = ClassicBars()

        assertTrue(renderer.update(features, VisualizerSettings()))
        repeat(40) { renderer.update(features, VisualizerSettings(mode = VisualizerMode.MINIMAL)) }
        assertFalse(renderer.update(features, VisualizerSettings(mode = VisualizerMode.MINIMAL)))
    }
}
