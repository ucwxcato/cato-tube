package org.catosaur.app.visual

import org.catosaur.app.playback.SimulatedFeatures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class RemainingRenderersTest {
    @Test
    fun everyModeDrawsAndSurvivesPauseAndResize() {
        val source = SimulatedFeatures()
        for (mode in VisualizerMode.entries) {
            val renderer = rendererFor(mode)
            val settings = VisualizerSettings(mode = mode, multicolor = true, barCount = 48)
            assertEquals(mode, renderer.mode)
            repeat(8) { assertTrue(renderer.update(source.at(it * 33L), settings)) }
            for ((width, height) in listOf(400 to 180, 900 to 600)) {
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                renderer.render(graphics, width, height, source.at(264), settings)
                graphics.dispose()
                assertTrue((0 until height).any { y -> (0 until width).any { x -> image.getRGB(x, y) != 0 } }, "$mode drew nothing")
            }
            renderer.update(org.catosaur.app.playback.AudioFeatures(), settings)
            renderer.reset()
        }
    }

    @Test
    fun secondColorIsAvailableToEveryMode() {
        val settings = VisualizerSettings(primaryColor = 0x112233, secondaryColor = 0xDDEEFF, followAccent = false, multicolor = true)
        assertEquals(0x112233, settings.colorAt(0f).rgb and 0xFFFFFF)
        assertEquals(0xDDEEFF, settings.colorAt(1f).rgb and 0xFFFFFF)
        assertTrue(VisualizerMode.entries.all { it.isSettingRelevant(VisualizerSettingField.MULTICOLOR) && it.isSettingRelevant(VisualizerSettingField.SECONDARY_COLOR) })
        assertEquals(settings, kotlinx.serialization.json.Json.decodeFromString(VisualizerSettings.serializer(),
            kotlinx.serialization.json.Json.encodeToString(VisualizerSettings.serializer(), settings)))
    }
}
