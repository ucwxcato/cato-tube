package org.catosaur.app.visual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import javax.swing.Timer

internal const val VISUALIZER_PREFERENCES_NODE = "org/catosaur/app"
internal const val KEY_VISUALIZER_ENABLED = "visualizerEnabled"
internal const val KEY_VISUALIZER_SETTINGS = "visualizer.settings"
internal const val KEY_VISUALIZER_PREVIOUS = "visualizer.previous"
internal const val VISUALIZER_WRITE_DEBOUNCE_MS = 300

/** Persists visualizer settings without coupling renderers to Preferences or Swing. */
internal class VisualizerStore(
    private val prefs: Preferences = Preferences.userRoot().node(VISUALIZER_PREFERENCES_NODE),
    private val onStatus: (String) -> Unit = {},
) : AutoCloseable {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val writeTimer = Timer(VISUALIZER_WRITE_DEBOUNCE_MS) { flush() }.apply {
        isRepeats = false
    }

    var settings: VisualizerSettings = VisualizerSettings.defaults
        private set

    var previous: VisualizerSettings? = null
        private set

    var statusMessage: String? = null
        private set

    var isReadOnly: Boolean = false
        private set

    val hasPendingSave: Boolean get() = pendingSave

    private var pendingSave = false
    private var editSessionOpen = false

    init {
        load()
    }

    /** Reloads the current and previous slots. Corrupt persisted data is never overwritten here. */
    fun load(): VisualizerSettings {
        writeTimer.stop()
        pendingSave = false
        editSessionOpen = false
        isReadOnly = false
        statusMessage = null

        val enabledPreference = readBoolean(KEY_VISUALIZER_ENABLED)
        val rawSettings = prefs.get(KEY_VISUALIZER_SETTINGS, null)
        settings = if (rawSettings.isNullOrBlank()) {
            VisualizerSettings.defaults.copy(enabled = enabledPreference ?: true)
        } else {
            loadSettings(rawSettings, enabledPreference)
        }

        previous = loadPrevious()
        return settings
    }

    /** Starts a new edit session so the next change becomes the previous-configuration slot. */
    fun beginEditSession() {
        editSessionOpen = false
    }

    /** Applies a change in memory and schedules one debounced write. */
    fun setSettings(value: VisualizerSettings): Boolean {
        if (isReadOnly) return false
        val next = value.clamped()
        if (next == settings) return false
        if (!editSessionOpen) {
            previous = settings
            editSessionOpen = true
        }
        settings = next
        scheduleSave()
        return true
    }

    fun update(transform: (VisualizerSettings) -> VisualizerSettings): Boolean =
        setSettings(transform(settings))

    fun setVisualizerEnabled(enabled: Boolean): Boolean =
        setSettings(settings.copy(enabled = enabled))

    /** Swaps the current settings and the previous slot, then persists both together. */
    fun restorePrevious(): Boolean {
        if (isReadOnly) return false
        val oldPrevious = previous ?: return false
        previous = settings
        settings = oldPrevious.clamped()
        editSessionOpen = true
        scheduleSave()
        return true
    }

    /** Writes a pending change immediately. The UI close handler calls this before shutdown. */
    fun flush() {
        writeTimer.stop()
        if (!pendingSave || isReadOnly) return
        try {
            val current = settings.clamped()
            prefs.put(KEY_VISUALIZER_SETTINGS, json.encodeToString(VisualizerSettings.serializer(), current))
            prefs.putBoolean(KEY_VISUALIZER_ENABLED, current.enabled)
            if (previous == null) {
                prefs.remove(KEY_VISUALIZER_PREVIOUS)
            } else {
                prefs.put(
                    KEY_VISUALIZER_PREVIOUS,
                    json.encodeToString(VisualizerSettings.serializer(), previous!!.clamped()),
                )
            }
            prefs.flush()
            pendingSave = false
            editSessionOpen = false
        } catch (error: BackingStoreException) {
            setStatus("Visualizer settings could not be saved: ${error.message ?: error.javaClass.simpleName}.")
        }
    }

    override fun close() {
        flush()
        writeTimer.stop()
    }

    private fun scheduleSave() {
        pendingSave = true
        writeTimer.restart()
    }

    private fun loadSettings(raw: String, enabledPreference: Boolean?): VisualizerSettings {
        return try {
            val element = json.parseToJsonElement(raw)
            val objectValue = element.jsonObject
            val storedSchema = objectValue["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: VISUALIZER_SCHEMA_VERSION
            if (storedSchema > VISUALIZER_SCHEMA_VERSION) {
                isReadOnly = true
            }
            val decodedRaw = json.decodeFromJsonElement<VisualizerSettings>(element)
            val decoded = decodedRaw.clamped()
            if (storedSchema > VISUALIZER_SCHEMA_VERSION) {
                setStatus("Visualizer settings use a newer schema and are read-only until this app is updated.")
            } else if (decoded != decodedRaw) {
                setStatus("Some visualizer settings were outside their allowed range and were clamped in memory.")
            }
            decoded.copy(enabled = enabledPreference ?: decoded.enabled)
        } catch (error: Exception) {
            val detail = error.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            setStatus("Visualizer settings could not be read$detail. Using defaults in memory.")
            VisualizerSettings.defaults.copy(enabled = enabledPreference ?: true)
        }
    }

    private fun loadPrevious(): VisualizerSettings? {
        val raw = prefs.get(KEY_VISUALIZER_PREVIOUS, null) ?: return null
        return runCatching { json.decodeFromString(VisualizerSettings.serializer(), raw).clamped() }.getOrNull()
    }

    private fun readBoolean(key: String): Boolean? = when (prefs.get(key, null)?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun setStatus(message: String) {
        statusMessage = message
        onStatus(message)
    }
}
