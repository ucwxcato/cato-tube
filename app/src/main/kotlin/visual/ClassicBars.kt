package org.catosaur.app.visual

import org.catosaur.app.Palette
import org.catosaur.app.playback.AudioFeatures
import org.catosaur.app.playback.SPECTRUM_BANDS
import org.catosaur.app.withAlpha
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.abs

internal class ClassicBars : VisualizerRenderer {
    private val shown = FloatArray(SPECTRUM_BANDS)

    override val mode = VisualizerMode.CLASSIC

    override fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean {
        val useBands = settings.mode == mode && features.active
        var moved = false
        for (band in shown.indices) {
            val goal = if (useBands) features.rawBands[band] else 0f
            val rate = if (goal > shown[band]) BARS_ATTACK else BARS_RELEASE
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
        if (settings.mode != mode) return

        val w = width.toFloat()
        val h = height.toFloat()
        val slot = w / SPECTRUM_BANDS
        val barWidth = slot * 0.5f
        val maxHeight = h * 0.5f
        for (band in 0 until SPECTRUM_BANDS) {
            val level = shown[band].coerceIn(0f, 1f)
            if (level < MIN_VISIBLE_LEVEL) continue
            val barHeight = maxHeight * level
            val x = slot * band + (slot - barWidth) / 2f
            val y = h - barHeight
            g.color = withAlpha(Palette.focus, 0.02f + 0.04f * level)
            g.fill(RoundRectangle2D.Float(x - 12f, y - 12f, barWidth + 24f, barHeight + 12f, 22f, 22f))
            g.color = withAlpha(Palette.focus, 0.04f + 0.07f * level)
            g.fill(RoundRectangle2D.Float(x - 5f, y - 5f, barWidth + 10f, barHeight + 5f, 12f, 12f))
            g.color = withAlpha(Palette.focus, 0.08f + 0.14f * level)
            g.fill(RoundRectangle2D.Float(x, y, barWidth, barHeight, 6f, 6f))
        }
    }

    override fun reset() {
        shown.fill(0f)
    }

    private companion object {
        const val BARS_ATTACK = 0.55f
        const val BARS_RELEASE = 0.14f
        const val CHANGE_THRESHOLD = 0.001f
        const val MIN_VISIBLE_LEVEL = 0.01f
    }
}
