package org.catosaur.app.visual

/** Modes available to the renderer contract; each mode gets its implementation in a later phase. */
internal enum class VisualizerMode {
    CLASSIC,
    CIRCULAR,
    WAVEFORM,
    PARTICLES,
    AUDIO_RING,
    MINIMAL,
}

/** Minimal renderer selection contract. Phase 3 adds the persisted controls and schema. */
internal data class VisualizerSettings(
    val mode: VisualizerMode = VisualizerMode.CLASSIC,
)
