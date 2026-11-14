package org.catosaur.app.visual

import org.catosaur.app.playback.AudioFeatures
import java.awt.Graphics2D

internal interface VisualizerRenderer {
    val mode: VisualizerMode

    /** Advances eased renderer state once per frame. Returns true when a repaint is needed. */
    fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean

    fun render(g: Graphics2D, width: Int, height: Int, features: AudioFeatures, settings: VisualizerSettings)

    fun reset()
}

internal fun rendererFor(mode: VisualizerMode): VisualizerRenderer = when (mode) {
    VisualizerMode.CLASSIC -> ClassicBars()
    VisualizerMode.CIRCULAR -> CircularSpectrum()
    VisualizerMode.WAVEFORM -> WaveformRenderer()
    VisualizerMode.PARTICLES -> ParticleField()
    VisualizerMode.AUDIO_RING -> AudioRing()
    VisualizerMode.MINIMAL -> MinimalRenderer()
}
