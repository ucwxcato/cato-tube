package org.catosaur.app.visual

import kotlinx.serialization.Serializable
import org.catosaur.app.Palette
import java.awt.Color

internal const val VISUALIZER_SCHEMA_VERSION = 1
internal const val DEFAULT_PRIMARY_COLOR_RGB = 0xC49874
internal const val DEFAULT_SECONDARY_COLOR_RGB = 0x8FA3B8

/** Modes available to the renderer contract; each mode gets its implementation in a later phase. */
internal enum class VisualizerMode {
    CLASSIC,
    CIRCULAR,
    WAVEFORM,
    PARTICLES,
    AUDIO_RING,
    MINIMAL,
}

/** Settings that are persisted for the visualizer and shared by the preview and the Backdrop. */
@Serializable
internal data class VisualizerSettings(
    val mode: VisualizerMode = VisualizerMode.CLASSIC,
    val primaryColor: Int = DEFAULT_PRIMARY_COLOR_RGB,
    val secondaryColor: Int = DEFAULT_SECONDARY_COLOR_RGB,
    val followAccent: Boolean = true,
    val sensitivity: Float = 1.0f,
    val amplitude: Float = 1.0f,
    val smoothing: Float = 0.55f,
    val speed: Float = 1.0f,
    val glow: Float = 0.6f,
    val barCount: Int = 32,
    val barWidth: Float = 0.5f,
    val barSpacing: Float = 0.5f,
    val particleCount: Int = 160,
    val particleSize: Float = 3.0f,
    val backgroundAlpha: Float = 1.0f,
    val bassEmphasis: Float = 0.0f,
    val fpsLimit: Int = 30,
    val enabled: Boolean = true,
    val schemaVersion: Int = VISUALIZER_SCHEMA_VERSION,
) {
    /** Returns a safe in-memory copy without changing the stored JSON until the next save. */
    internal fun clamped(): VisualizerSettings = copy(
        primaryColor = primaryColor.coerceIn(0, RGB_MAX),
        secondaryColor = secondaryColor.coerceIn(0, RGB_MAX),
        sensitivity = sensitivity.clamp(VisualizerSettingRanges.sensitivity, 1.0f),
        amplitude = amplitude.clamp(VisualizerSettingRanges.amplitude, 1.0f),
        smoothing = smoothing.clamp(VisualizerSettingRanges.smoothing, 0.55f),
        speed = speed.clamp(VisualizerSettingRanges.speed, 1.0f),
        glow = glow.clamp(VisualizerSettingRanges.glow, 0.6f),
        barCount = barCount.coerceIn(VisualizerSettingRanges.barCount),
        barWidth = barWidth.clamp(VisualizerSettingRanges.barWidth, 0.5f),
        barSpacing = barSpacing.clamp(VisualizerSettingRanges.barSpacing, 0.5f),
        particleCount = particleCount.coerceIn(VisualizerSettingRanges.particleCount),
        particleSize = particleSize.clamp(VisualizerSettingRanges.particleSize, 3.0f),
        backgroundAlpha = backgroundAlpha.clamp(VisualizerSettingRanges.backgroundAlpha, 1.0f),
        bassEmphasis = bassEmphasis.clamp(VisualizerSettingRanges.bassEmphasis, 0.0f),
        fpsLimit = if (fpsLimit == 60) 60 else 30,
        schemaVersion = VISUALIZER_SCHEMA_VERSION,
    )

    /** Reads the accent at paint time when follow-accent is enabled. */
    internal fun resolvedPrimaryColor(): Color =
        if (followAccent) Palette.focus else Color(primaryColor and RGB_MAX)

    internal fun resolvedSecondaryColor(): Color = Color(secondaryColor and RGB_MAX)

    internal companion object {
        val defaults: VisualizerSettings get() = VisualizerSettings()

        internal fun forMode(mode: VisualizerMode): VisualizerSettings = defaults.copy(mode = mode)
    }
}

internal enum class VisualizerSettingField {
    MODE,
    PRIMARY_COLOR,
    SECONDARY_COLOR,
    FOLLOW_ACCENT,
    SENSITIVITY,
    AMPLITUDE,
    SMOOTHING,
    SPEED,
    GLOW,
    BAR_COUNT,
    BAR_WIDTH,
    BAR_SPACING,
    PARTICLE_COUNT,
    PARTICLE_SIZE,
    BACKGROUND_ALPHA,
    BASS_EMPHASIS,
    FPS_LIMIT,
    ENABLED,
}

internal fun relevantVisualizerFields(mode: VisualizerMode): Set<VisualizerSettingField> = when (mode) {
    VisualizerMode.CLASSIC -> setOf(
        VisualizerSettingField.MODE,
        VisualizerSettingField.PRIMARY_COLOR,
        VisualizerSettingField.FOLLOW_ACCENT,
        VisualizerSettingField.SENSITIVITY,
        VisualizerSettingField.AMPLITUDE,
        VisualizerSettingField.SMOOTHING,
        VisualizerSettingField.SPEED,
        VisualizerSettingField.GLOW,
        VisualizerSettingField.BAR_COUNT,
        VisualizerSettingField.BAR_WIDTH,
        VisualizerSettingField.BAR_SPACING,
        VisualizerSettingField.BACKGROUND_ALPHA,
        VisualizerSettingField.BASS_EMPHASIS,
        VisualizerSettingField.FPS_LIMIT,
        VisualizerSettingField.ENABLED,
    )

    VisualizerMode.CIRCULAR -> relevantVisualizerFields(VisualizerMode.CLASSIC) -
        setOf(VisualizerSettingField.BAR_WIDTH)

    VisualizerMode.WAVEFORM -> setOf(
        VisualizerSettingField.MODE,
        VisualizerSettingField.PRIMARY_COLOR,
        VisualizerSettingField.FOLLOW_ACCENT,
        VisualizerSettingField.SENSITIVITY,
        VisualizerSettingField.AMPLITUDE,
        VisualizerSettingField.SMOOTHING,
        VisualizerSettingField.SPEED,
        VisualizerSettingField.GLOW,
        VisualizerSettingField.BACKGROUND_ALPHA,
        VisualizerSettingField.BASS_EMPHASIS,
        VisualizerSettingField.FPS_LIMIT,
        VisualizerSettingField.ENABLED,
    )

    VisualizerMode.PARTICLES -> setOf(
        VisualizerSettingField.MODE,
        VisualizerSettingField.PRIMARY_COLOR,
        VisualizerSettingField.SECONDARY_COLOR,
        VisualizerSettingField.FOLLOW_ACCENT,
        VisualizerSettingField.SENSITIVITY,
        VisualizerSettingField.AMPLITUDE,
        VisualizerSettingField.SMOOTHING,
        VisualizerSettingField.SPEED,
        VisualizerSettingField.GLOW,
        VisualizerSettingField.PARTICLE_COUNT,
        VisualizerSettingField.PARTICLE_SIZE,
        VisualizerSettingField.BACKGROUND_ALPHA,
        VisualizerSettingField.BASS_EMPHASIS,
        VisualizerSettingField.FPS_LIMIT,
        VisualizerSettingField.ENABLED,
    )

    VisualizerMode.AUDIO_RING -> setOf(
        VisualizerSettingField.MODE,
        VisualizerSettingField.PRIMARY_COLOR,
        VisualizerSettingField.SECONDARY_COLOR,
        VisualizerSettingField.FOLLOW_ACCENT,
        VisualizerSettingField.SENSITIVITY,
        VisualizerSettingField.AMPLITUDE,
        VisualizerSettingField.SMOOTHING,
        VisualizerSettingField.SPEED,
        VisualizerSettingField.GLOW,
        VisualizerSettingField.BACKGROUND_ALPHA,
        VisualizerSettingField.BASS_EMPHASIS,
        VisualizerSettingField.FPS_LIMIT,
        VisualizerSettingField.ENABLED,
    )

    VisualizerMode.MINIMAL -> setOf(
        VisualizerSettingField.MODE,
        VisualizerSettingField.PRIMARY_COLOR,
        VisualizerSettingField.FOLLOW_ACCENT,
        VisualizerSettingField.SENSITIVITY,
        VisualizerSettingField.AMPLITUDE,
        VisualizerSettingField.SMOOTHING,
        VisualizerSettingField.SPEED,
        VisualizerSettingField.GLOW,
        VisualizerSettingField.BACKGROUND_ALPHA,
        VisualizerSettingField.BASS_EMPHASIS,
        VisualizerSettingField.FPS_LIMIT,
        VisualizerSettingField.ENABLED,
    )
}

internal fun VisualizerMode.relevantFields(): Set<VisualizerSettingField> = relevantVisualizerFields(this)

internal fun VisualizerMode.isSettingRelevant(field: VisualizerSettingField): Boolean = field in relevantFields()

internal object VisualizerSettingRanges {
    val sensitivity = 0.25f..3.0f
    val amplitude = 0.25f..2.0f
    val smoothing = 0.0f..0.95f
    val speed = 0.25f..3.0f
    val glow = 0.0f..1.0f
    val barCount = 16..64
    val barWidth = 0.2f..0.9f
    val barSpacing = 0.0f..0.8f
    val particleCount = 20..400
    val particleSize = 1.0f..8.0f
    val backgroundAlpha = 0.0f..1.0f
    val bassEmphasis = 0.0f..1.0f
}

private const val RGB_MAX = 0xFFFFFF

private fun Float.clamp(range: ClosedFloatingPointRange<Float>, fallback: Float): Float =
    if (isFinite()) coerceIn(range) else fallback
