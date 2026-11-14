package org.catosaur.app.visual

import org.catosaur.app.playback.AudioFeatures
import org.catosaur.app.playback.SPECTRUM_BANDS
import org.catosaur.app.playback.WAVEFORM_SAMPLES
import org.catosaur.app.withAlpha
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun response(settings: VisualizerSettings): Float =
    ((1f - settings.smoothing) * settings.speed * 0.7f).coerceIn(0.04f, 1f)

private fun level(features: AudioFeatures, band: Int, settings: VisualizerSettings): Float =
    (features.bands[band] * settings.sensitivity * settings.amplitude *
        (if (band < 10) 1f + settings.bassEmphasis else 1f)).coerceIn(0f, 1f)

internal abstract class BandRenderer(override val mode: VisualizerMode) : VisualizerRenderer {
    protected val bands = FloatArray(SPECTRUM_BANDS)
    protected var energy = 0f
    override fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean {
        val active = settings.enabled && features.active
        val rate = response(settings)
        var changed = false
        for (i in bands.indices) {
            val target = if (active) level(features, i, settings) else 0f
            val next = bands[i] + (target - bands[i]) * rate
            if (abs(next - bands[i]) > 0.001f) changed = true
            bands[i] = next
        }
        val targetEnergy = if (active) (features.energy * settings.sensitivity * settings.amplitude).coerceIn(0f, 1f) else 0f
        val nextEnergy = energy + (targetEnergy - energy) * rate
        if (abs(nextEnergy - energy) > 0.001f) changed = true
        energy = nextEnergy
        return changed
    }
    override fun reset() { bands.fill(0f); energy = 0f }
    protected fun bandAt(position: Float): Float {
        val scaled = position.coerceIn(0f, 1f) * bands.lastIndex
        val first = scaled.toInt()
        return bands[first] + (bands[min(first + 1, bands.lastIndex)] - bands[first]) * (scaled - first)
    }
}

internal class CircularSpectrum : BandRenderer(VisualizerMode.CIRCULAR) {
    private var tableCount = 0
    private var cosine = FloatArray(0)
    private var sine = FloatArray(0)
    private fun table(count: Int) {
        if (count == tableCount) return
        tableCount = count
        cosine = FloatArray(count) { cos((2.0 * PI * it / count - PI / 2).toFloat()) }
        sine = FloatArray(count) { sin((2.0 * PI * it / count - PI / 2).toFloat()) }
    }
    override fun render(g: Graphics2D, width: Int, height: Int, features: AudioFeatures, settings: VisualizerSettings) {
        if (!settings.enabled) return
        val count = settings.barCount
        table(count)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.22f
        g.stroke = BasicStroke((2f + settings.barSpacing * 4f).coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (i in 0 until count) {
            val fraction = i.toFloat() / count
            val length = 5f + bandAt(fraction) * radius * 0.85f
            val x = cosine[i]
            val y = sine[i]
            g.color = withAlpha(settings.colorAt(fraction), 0.75f + settings.glow * 0.25f)
            g.draw(Line2D.Float(cx + x * radius, cy + y * radius, cx + x * (radius + length), cy + y * (radius + length)))
        }
    }
}

internal class WaveformRenderer : VisualizerRenderer {
    override val mode = VisualizerMode.WAVEFORM
    private val samples = FloatArray(WAVEFORM_SAMPLES)
    override fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean {
        val rate = response(settings)
        var changed = false
        for (i in samples.indices) {
            val source = if (settings.enabled && features.active) {
                (features.waveform[i] * settings.sensitivity * settings.amplitude *
                    (1f + features.bass * settings.bassEmphasis)).coerceIn(-1f, 1f)
            } else 0f
            val next = samples[i] + (source - samples[i]) * rate
            if (abs(next - samples[i]) > 0.001f) changed = true
            samples[i] = next
        }
        return changed
    }
    override fun render(g: Graphics2D, width: Int, height: Int, features: AudioFeatures, settings: VisualizerSettings) {
        if (!settings.enabled) return
        val center = height / 2f
        val scale = height * 0.38f
        g.stroke = BasicStroke(2f + settings.glow * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (section in 0 until 16) {
            val path = Path2D.Float()
            val first = section * (WAVEFORM_SAMPLES - 1) / 16
            val last = (section + 1) * (WAVEFORM_SAMPLES - 1) / 16
            for (i in first..last) {
                val average = (samples[(i - 1).coerceAtLeast(0)] + samples[i] + samples[(i + 1).coerceAtMost(samples.lastIndex)]) / 3f
                val x = i.toFloat() / samples.lastIndex * width
                val y = center - average * scale
                if (i == first) path.moveTo(x.toDouble(), y.toDouble()) else path.lineTo(x.toDouble(), y.toDouble())
            }
            g.color = settings.colorAt(section / 15f)
            g.draw(path)
        }
    }
    override fun reset() { samples.fill(0f) }
}

internal class ParticleField : VisualizerRenderer {
    override val mode = VisualizerMode.PARTICLES
    private val x = FloatArray(400)
    private val y = FloatArray(400)
    private val life = FloatArray(400)
    private var cursor = 0
    private var tick = 0
    private var lastCount = 0
    private var intensity = 0f
    override fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean {
        if (settings.particleCount != lastCount) {
            reset()
            lastCount = settings.particleCount
        }
        tick++
        var changed = false
        for (i in life.indices) if (life[i] > 0f) {
            life[i] = (life[i] - 0.025f * settings.speed).coerceAtLeast(0f)
            y[i] -= 0.006f * settings.speed
            changed = true
        }
        if (settings.enabled && features.active) {
            val target = features.bass * settings.sensitivity * settings.amplitude * (1f + settings.bassEmphasis)
            intensity += (target - intensity) * response(settings)
            val amount = ((intensity * 10f) +
                features.beat * 12f).toInt().coerceIn(0, 24)
            repeat(amount) {
                val i = cursor++ % settings.particleCount
                x[i] = ((i * 73 + tick * 17) % 997) / 997f
                y[i] = 0.35f + ((i * 37 + tick * 11) % 500) / 1000f
                life[i] = 1f
            }
            changed = changed || amount > 0
        } else {
            intensity *= 0.85f
        }
        return changed
    }
    override fun render(g: Graphics2D, width: Int, height: Int, features: AudioFeatures, settings: VisualizerSettings) {
        if (!settings.enabled) return
        for (i in 0 until settings.particleCount) {
            if (life[i] <= 0f) continue
            val size = settings.particleSize * (0.5f + life[i])
            g.color = withAlpha(settings.colorAt(i.toFloat() / settings.particleCount), life[i] * (0.5f + settings.glow * 0.5f))
            g.fill(Ellipse2D.Float(x[i] * width, y[i] * height, size, size))
        }
    }
    override fun reset() { life.fill(0f); cursor = 0; tick = 0; intensity = 0f }
}

internal class AudioRing : BandRenderer(VisualizerMode.AUDIO_RING) {
    override fun render(g: Graphics2D, width: Int, height: Int, features: AudioFeatures, settings: VisualizerSettings) {
        if (!settings.enabled) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * (0.21f + energy * 0.08f)
        val points = 128
        for (section in 0 until 16) {
            val path = Path2D.Float()
            for (step in 0..8) {
                val fraction = (section * 8 + step).toFloat() / points
                val angle = fraction * 2f * PI.toFloat() - PI.toFloat() / 2f
                val r = radius * (1f + bandAt(fraction) * 0.45f)
                val px = cx + cos(angle) * r
                val py = cy + sin(angle) * r
                if (step == 0) path.moveTo(px.toDouble(), py.toDouble()) else path.lineTo(px.toDouble(), py.toDouble())
            }
            val color = settings.colorAt(section / 15f)
            g.color = withAlpha(color, settings.glow * 0.22f)
            g.stroke = BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(path)
            g.color = color
            g.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(path)
        }
    }
}

internal class MinimalRenderer : VisualizerRenderer {
    override val mode = VisualizerMode.MINIMAL
    private var energy = 0f
    override fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean {
        val target = if (settings.enabled && features.active) {
            (features.energy * settings.sensitivity * settings.amplitude * (1f + features.bass * settings.bassEmphasis)).coerceIn(0f, 1f)
        } else 0f
        val next = energy + (target - energy) * response(settings)
        val changed = abs(next - energy) > 0.001f
        energy = next
        return changed
    }
    override fun render(g: Graphics2D, width: Int, height: Int, features: AudioFeatures, settings: VisualizerSettings) {
        if (!settings.enabled) return
        val y = height * 0.72f
        val start = width * 0.15f
        val end = width * 0.85f
        g.stroke = BasicStroke(1.5f)
        g.color = withAlpha(settings.colorAt(0f), 0.5f)
        g.draw(Line2D.Float(start, y, end, y))
        val x = start + (end - start) * energy
        val size = 6f + settings.glow * 7f
        g.color = settings.colorAt(1f)
        g.fill(Ellipse2D.Float(x - size / 2f, y - size / 2f, size, size))
    }
    override fun reset() { energy = 0f }
}
