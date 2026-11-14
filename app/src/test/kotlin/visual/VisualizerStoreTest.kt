package org.catosaur.app.visual

import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisualizerStoreTest {
    private lateinit var prefs: Preferences

    @BeforeTest
    fun setUp() {
        prefs = Preferences.userRoot().node("org/catosaur/visualizer-test/${UUID.randomUUID()}")
    }

    @AfterTest
    fun tearDown() {
        prefs.removeNode()
    }

    @Test
    fun roundTripWritesSettingsAndEnabledKey() {
        val expected = VisualizerSettings(
            mode = VisualizerMode.PARTICLES,
            primaryColor = 0x123456,
            followAccent = false,
            sensitivity = 1.8f,
            particleCount = 240,
            enabled = false,
        )
        VisualizerStore(prefs).use {
            assertTrue(it.setSettings(expected))
            it.flush()
        }

        val loaded = VisualizerStore(prefs)
        assertEquals(expected, loaded.settings)
        assertFalse(prefs.getBoolean(KEY_VISUALIZER_ENABLED, true))
        loaded.close()
    }

    @Test
    fun clampingKeepsRawJsonUntilTheNextExplicitSave() {
        val json = Json { encodeDefaults = true }
        val raw = json.encodeToString(
            VisualizerSettings.serializer(),
            VisualizerSettings(sensitivity = 99f, barCount = 999),
        )
        prefs.put(KEY_VISUALIZER_SETTINGS, raw)

        val statuses = mutableListOf<String>()
        val store = VisualizerStore(prefs) { statuses += it }
        assertEquals(3.0f, store.settings.sensitivity)
        assertEquals(64, store.settings.barCount)
        assertTrue(statuses.any { it.contains("clamped") })
        assertEquals(raw, prefs.get(KEY_VISUALIZER_SETTINGS, null))
        store.close()
    }

    @Test
    fun unknownFieldsAreIgnored() {
        prefs.put(
            KEY_VISUALIZER_SETTINGS,
            """{"mode":"CLASSIC","futureControl":42,"schemaVersion":1}""",
        )

        val store = VisualizerStore(prefs)
        assertEquals(VisualizerMode.CLASSIC, store.settings.mode)
        assertNull(store.statusMessage)
        store.close()
    }

    @Test
    fun corruptJsonUsesDefaultsWithoutOverwritingThePreference() {
        val raw = "{not-json"
        prefs.put(KEY_VISUALIZER_SETTINGS, raw)
        val statuses = mutableListOf<String>()

        val store = VisualizerStore(prefs) { statuses += it }
        assertEquals(VisualizerSettings.defaults, store.settings)
        assertNotNull(store.statusMessage)
        assertTrue(statuses.any { it.contains("defaults in memory") })
        assertEquals(raw, prefs.get(KEY_VISUALIZER_SETTINGS, null))
        store.close()
    }

    @Test
    fun newerSchemaIsReadOnlyAndPreservesTheStoredValue() {
        val raw = """{"schemaVersion":99,"mode":"PARTICLES","particleCount":300}"""
        prefs.put(KEY_VISUALIZER_SETTINGS, raw)

        val store = VisualizerStore(prefs)
        assertTrue(store.isReadOnly)
        assertNotNull(store.statusMessage)
        assertFalse(store.setVisualizerEnabled(false))
        store.flush()
        assertEquals(raw, prefs.get(KEY_VISUALIZER_SETTINGS, null))
        store.close()
    }

    @Test
    fun previousSlotIsCapturedOnceAndRestoreSwapsBothValues() {
        val first = VisualizerSettings(mode = VisualizerMode.CLASSIC, sensitivity = 0.7f)
        val second = first.copy(mode = VisualizerMode.CIRCULAR, sensitivity = 1.4f)
        val store = VisualizerStore(prefs)
        store.setSettings(first)
        store.flush()
        store.setSettings(second)
        store.update { it.copy(glow = 0.8f) }
        assertEquals(first, store.previous)
        store.flush()

        assertTrue(store.restorePrevious())
        assertEquals(second.copy(glow = 0.8f), store.previous)
        assertEquals(first, store.settings)
        store.flush()
        store.close()

        val reopened = VisualizerStore(prefs)
        assertEquals(first, reopened.settings)
        assertEquals(second.copy(glow = 0.8f), reopened.previous)
        reopened.close()
    }

    @Test
    fun enabledPreferenceIsReadWhenTheSettingsBlobDoesNotContainIt() {
        prefs.putBoolean(KEY_VISUALIZER_ENABLED, false)
        prefs.put(KEY_VISUALIZER_SETTINGS, """{"schemaVersion":1,"mode":"MINIMAL"}""")

        val store = VisualizerStore(prefs)
        assertFalse(store.settings.enabled)
        store.close()
    }
}
