package org.catosaur.app.visual

import org.catosaur.app.playback.AudioFeatures
import org.catosaur.app.playback.SPECTRUM_BANDS
import org.catosaur.app.withAlpha
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

internal class ClassicBars : VisualizerRenderer {
    private val shown = FloatArray(SPECTRUM_BANDS)

    override val mode = VisualizerMode.CLASSIC

    override fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean {
        val useBands = settings.mode == mode && settings.enabled && features.active
        val attack = (BASE_ATTACK + (DEFAULT_SMOOTHING - settings.smoothing) * 0.8f).coerceIn(0.08f, 0.95f)
        val release = (BASE_RELEASE + (DEFAULT_SMOOTHING - settings.smoothing) * 0.2f).coerceIn(0.03f, 0.3f)
        val speed = settings.speed.coerceIn(0.25f, 3.0f)
        var moved = false
        for (band in shown.indices) {
            val bassBoost = if (band <= 9) 1f + settings.bassEmphasis else 1f
            val goal = if (useBands) {
                (features.rawBands[band] * settings.sensitivity * settings.amplitude * bassBoost).coerceIn(0f, 1f)
            } else {
                0f
            }
            val rate = (if (goal > shown[band]) attack else release) * speed
            val next = shown[band] + (goal - shown[band]) * rate
            if (abs(next - shown[band]) > CHANGE_THRESHOLD) moved = true
            shown[band] = next
        }
        return moved
    }

    override fun render(
        g: Graphics2D,
        width: Int,
        height: Int,
        features: AudioFeatures,
        settings: VisualizerSettings,
    ) {
        if (settings.mode != mode || !settings.enabled) return

        val w = width.toFloat()
        val h = height.toFloat()
        val barCount = settings.barCount.coerceIn(VisualizerSettingRanges.barCount)
        val slot = w / barCount
        val widthFactor = (settings.barWidth * (1f - (settings.barSpacing - 0.5f) * 0.4f)).coerceIn(0.08f, 0.92f)
        val barWidth = slot * widthFactor
        val maxHeight = h * 0.5f
        val glow = (settings.glow / DEFAULT_GLOW).coerceIn(0f, 1.67f)
        for (bar in 0 until barCount) {
            val position = bar.toFloat() / (barCount - 1).coerceAtLeast(1)
            val primary = settings.colorAt(position)
            val level = interpolatedLevel(position).coerceIn(0f, 1f)
            if (level < MIN_VISIBLE_LEVEL) continue
            val barHeight = maxHeight * level
            val x = slot * bar + (slot - barWidth) / 2f
            val y = h - barHeight
            g.color = withAlpha(primary, ((0.02f + 0.04f * level) * glow).coerceIn(0f, 1f))
            g.fill(RoundRectangle2D.Float(x - 12f, y - 12f, barWidth + 24f, barHeight + 12f, 22f, 22f))
            g.color = withAlpha(primary, ((0.04f + 0.07f * level) * glow).coerceIn(0f, 1f))
            g.fill(RoundRectangle2D.Float(x - 5f, y - 5f, barWidth + 10f, barHeight + 5f, 12f, 12f))
            g.color = withAlpha(primary, ((0.08f + 0.14f * level) * glow).coerceIn(0f, 1f))
            g.fill(RoundRectangle2D.Float(x, y, barWidth, barHeight, 6f, 6f))
        }
    }

    override fun reset() {
        shown.fill(0f)
    }

    private fun interpolatedLevel(position: Float): Float {
        val scaled = position.coerceIn(0f, 1f) * shown.lastIndex
        val lower = floor(scaled).toInt().coerceIn(0, shown.lastIndex)
        val upper = min(lower + 1, shown.lastIndex)
        val fraction = scaled - lower
        return shown[lower] + (shown[upper] - shown[lower]) * fraction
    }

    private companion object {
        const val BASE_ATTACK = 0.55f
        const val BASE_RELEASE = 0.14f
        const val DEFAULT_SMOOTHING = 0.55f
        const val DEFAULT_GLOW = 0.6f
        const val CHANGE_THRESHOLD = 0.001f
        const val MIN_VISIBLE_LEVEL = 0.01f
    }
}
