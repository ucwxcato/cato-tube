package org.catosaur.app.visual

import org.catosaur.app.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VisualizerSettingsTest {
    @Test
    fun defaultsAndRangesCoverEveryPersistedControl() {
        val defaults = VisualizerSettings.defaults

        assertEquals(1, defaults.schemaVersion)
        assertEquals(VisualizerMode.CLASSIC, defaults.mode)
        assertTrue(defaults.followAccent)
        assertTrue(defaults.enabled)
        assertEquals(32, defaults.barCount)
        assertEquals(160, defaults.particleCount)
        assertEquals(30, defaults.fpsLimit)
        assertEquals(VisualizerSettingRanges.sensitivity, 0.25f..3.0f)
        assertEquals(VisualizerSettingRanges.particleCount, 20..400)
    }

    @Test
    fun clampedValuesStayInsideThePublishedRanges() {
        val clamped = VisualizerSettings(
            primaryColor = -1,
            secondaryColor = 0x1FFFFFF,
            sensitivity = -4f,
            amplitude = 8f,
            smoothing = Float.NaN,
            speed = 8f,
            glow = -1f,
            barCount = 100,
            barWidth = 0f,
            barSpacing = 2f,
            particleCount = 1,
            particleSize = 20f,
            backgroundAlpha = -1f,
            bassEmphasis = 2f,
            fpsLimit = 45,
        ).clamped()

        assertEquals(0, clamped.primaryColor)
        assertEquals(0xFFFFFF, clamped.secondaryColor)
        assertEquals(0.25f, clamped.sensitivity)
        assertEquals(2.0f, clamped.amplitude)
        assertEquals(0.55f, clamped.smoothing)
        assertEquals(3.0f, clamped.speed)
        assertEquals(0.0f, clamped.glow)
        assertEquals(64, clamped.barCount)
        assertEquals(0.2f, clamped.barWidth)
        assertEquals(0.8f, clamped.barSpacing)
        assertEquals(20, clamped.particleCount)
        assertEquals(8.0f, clamped.particleSize)
        assertEquals(0.0f, clamped.backgroundAlpha)
        assertEquals(1.0f, clamped.bassEmphasis)
        assertEquals(30, clamped.fpsLimit)
    }

    @Test
    fun modeRelevanceDoesNotExposeControlsForOtherModes() {
        assertTrue(VisualizerMode.CLASSIC.isSettingRelevant(VisualizerSettingField.BAR_COUNT))
        assertTrue(VisualizerMode.CIRCULAR.isSettingRelevant(VisualizerSettingField.BAR_COUNT))
        assertFalse(VisualizerMode.WAVEFORM.isSettingRelevant(VisualizerSettingField.BAR_COUNT))
        assertFalse(VisualizerMode.WAVEFORM.isSettingRelevant(VisualizerSettingField.PARTICLE_COUNT))
        assertTrue(VisualizerMode.PARTICLES.isSettingRelevant(VisualizerSettingField.PARTICLE_COUNT))
        assertTrue(VisualizerMode.AUDIO_RING.isSettingRelevant(VisualizerSettingField.SECONDARY_COLOR))
    }

    @Test
    fun followAccentResolvesAtPaintTime() {
        val settings = VisualizerSettings(followAccent = true, primaryColor = 0x010203)
        val before = settings.resolvedPrimaryColor()
        try {
            Palette.applyAccent(java.awt.Color(0x33, 0x66, 0x99))
            val after = settings.resolvedPrimaryColor()

            assertNotEquals(before, after)
            assertEquals(Palette.focus, after)
        } finally {
            Palette.applyAccent(java.awt.Color(128, 92, 66))
        }
    }
}
