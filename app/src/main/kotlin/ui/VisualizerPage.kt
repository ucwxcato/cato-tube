package org.catosaur.app.ui

import org.catosaur.app.AccentChoice
import org.catosaur.app.Card
import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.Palette
import org.catosaur.app.SectionHeading
import org.catosaur.app.SlimScrollBarUI
import org.catosaur.app.ThemedComboBox
import org.catosaur.app.ThemedSlider
import org.catosaur.app.playback.AudioFeatures
import org.catosaur.app.playback.PlaybackController
import org.catosaur.app.playback.SimulatedFeatures
import org.catosaur.app.sectionLabel
import org.catosaur.app.smoothCopy
import org.catosaur.app.visual.VisualizerMode
import org.catosaur.app.visual.VisualizerRenderer
import org.catosaur.app.visual.VisualizerSettingField
import org.catosaur.app.visual.VisualizerSettings
import org.catosaur.app.visual.VisualizerStore
import org.catosaur.app.visual.VisualizerSettingRanges
import org.catosaur.app.visual.isSettingRelevant
import org.catosaur.app.visual.rendererFor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.RadialGradientPaint
import java.awt.geom.Point2D
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Settings playground. It owns the page controls; renderers only receive the resulting settings. */
internal class VisualizerPage(
    private val store: VisualizerStore,
    playback: PlaybackController,
    private val onSettingsChanged: () -> Unit,
) : JPanel(BorderLayout(0, 12)) {
    private val syncing = falseState()
    private val previewStatus = JLabel().apply {
        font = Fonts.ui(11)
        foreground = Palette.muted
    }
    private val storeStatus = JLabel().apply {
        font = Fonts.ui(11)
        foreground = Palette.muted
        text = store.statusMessage ?: "Changes save automatically."
    }
    private val simulatedCheck = themedCheck("Preview while stopped", selected = false)
    private val enabledCheck = themedCheck("Visualizer enabled", selected = store.settings.enabled)
    private val modeBox = ThemedComboBox(VisualizerMode.entries.toTypedArray())
    private val primaryColorBox = ThemedComboBox(AccentChoice.entries.toTypedArray())
    private val secondaryColorBox = ThemedComboBox(AccentChoice.entries.toTypedArray())

    private val preview = VisualizerPreview(
        playback = playback,
        settings = { store.settings },
        simulateStopped = { simulatedCheck.isSelected },
        onSignal = { previewStatus.text = it },
    )

    private val rows = mutableMapOf<VisualizerSettingField, JComponent>()
    private val checks = mutableMapOf<VisualizerSettingField, JCheckBox>()
    private val sliders = mutableMapOf<VisualizerSettingField, ThemedSlider>()
    private val valueLabels = mutableMapOf<VisualizerSettingField, JLabel>()
    private val valueText = mutableMapOf<VisualizerSettingField, (VisualizerSettings) -> String>()
    private val groups = mutableListOf<Pair<JPanel, List<VisualizerSettingField>>>()
    private val controlCard = Card(BorderLayout(0, 14)).apply {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
    }

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 0, 0, 0)
        modeBox.addActionListener {
            if (syncing.value) return@addActionListener
            val mode = modeBox.selectedItem as? VisualizerMode ?: return@addActionListener
            change { current -> current.copy(mode = mode) }
        }
        enabledCheck.addActionListener {
            if (!syncing.value) change { current -> current.copy(enabled = enabledCheck.isSelected) }
        }
        simulatedCheck.addActionListener { preview.repaint() }
        buildControls()
        add(buildHeader(), BorderLayout.NORTH)
        add(buildPreviewCard(), BorderLayout.CENTER)
        add(buildControlsScroll(), BorderLayout.SOUTH)
        syncControls()
    }

    private fun buildHeader(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        isOpaque = false
        add(
            JLabel("Visualizer").apply {
                font = Fonts.ui(20, Font.BOLD)
                foreground = Palette.text
            },
            BorderLayout.NORTH,
        )
        add(
            JLabel("Tune the background response while a song plays.").apply {
                font = Fonts.ui(12)
                foreground = Palette.muted
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                        isOpaque = false
                        add(JLabel("Mode").apply { foreground = Palette.muted; font = Fonts.ui(12) })
                        modeBox.preferredSize = Dimension(190, 32)
                        add(modeBox)
                        add(enabledCheck)
                        add(simulatedCheck)
                    },
                )
                add(Box.createVerticalStrut(4))
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                        isOpaque = false
                        add(FlatButton("Reset all").apply { addActionListener { resetAll() } })
                        add(FlatButton("Restore previous").apply { addActionListener { restorePrevious() } })
                        add(FlatButton("Randomize").apply { addActionListener { randomize() } })
                    },
                )
                add(Box.createVerticalStrut(2))
                add(storeStatus)
            },
            BorderLayout.SOUTH,
        )
    }

    private fun buildPreviewCard(): JComponent = Card(BorderLayout(0, 8)).apply {
        border = BorderFactory.createEmptyBorder(12, 14, 10, 14)
        add(SectionHeading("Preview"), BorderLayout.NORTH)
        add(preview, BorderLayout.CENTER)
        add(previewStatus, BorderLayout.SOUTH)
    }

    private fun buildControlsScroll(): JComponent = JScrollPane(controlCard).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
        viewport.isOpaque = false
        verticalScrollBar.unitIncrement = 16
        verticalScrollBar.ui = SlimScrollBarUI()
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        preferredSize = Dimension(0, 280)
    }

    private fun buildControls() {
        val colors = group("Colors", listOf(
            VisualizerSettingField.PRIMARY_COLOR,
            VisualizerSettingField.SECONDARY_COLOR,
            VisualizerSettingField.MULTICOLOR,
            VisualizerSettingField.FOLLOW_ACCENT,
        ))
        val motion = group("Motion", listOf(
            VisualizerSettingField.SMOOTHING,
            VisualizerSettingField.SPEED,
            VisualizerSettingField.GLOW,
        ))
        val response = group("Response", listOf(
            VisualizerSettingField.SENSITIVITY,
            VisualizerSettingField.AMPLITUDE,
            VisualizerSettingField.BASS_EMPHASIS,
            VisualizerSettingField.BAR_COUNT,
            VisualizerSettingField.BAR_WIDTH,
            VisualizerSettingField.BAR_SPACING,
            VisualizerSettingField.PARTICLE_COUNT,
            VisualizerSettingField.PARTICLE_SIZE,
        ))
        val background = group("Background", listOf(
            VisualizerSettingField.BACKGROUND_ALPHA,
            VisualizerSettingField.FPS_LIMIT,
        ))
        groups += colors
        groups += motion
        groups += response
        groups += background

        val groupList = listOf(colors, motion, response, background)
        val fields = groupList.flatMap { it.second }.toSet()
        require(fields.size == groupList.sumOf { it.second.size })
        refreshGroups()
    }

    private fun group(title: String, fields: List<VisualizerSettingField>): Pair<JPanel, List<VisualizerSettingField>> {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
        }
        val result = panel to fields
        when (title) {
            "Colors" -> {
                addColorRow(VisualizerSettingField.PRIMARY_COLOR, "Primary", primaryColorBox) { settings, color ->
                    settings.copy(primaryColor = color.rgb and RGB_MASK, followAccent = false)
                }
                addColorRow(VisualizerSettingField.SECONDARY_COLOR, "Secondary", secondaryColorBox) { settings, color ->
                    settings.copy(secondaryColor = color.rgb and RGB_MASK)
                }
                addCheckRow(VisualizerSettingField.MULTICOLOR, "Use both colors", { it.multicolor }) { settings, value ->
                    settings.copy(multicolor = value)
                }
                addCheckRow(VisualizerSettingField.FOLLOW_ACCENT, "Follow the app accent", { it.followAccent }) { settings, value ->
                    settings.copy(followAccent = value)
                }
            }

            "Motion" -> {
                addSliderRow(VisualizerSettingField.SMOOTHING, "Smoothing", VisualizerSettingRanges.smoothing, 0.05f, { it.smoothing }, ::formatFloat) { s, v -> s.copy(smoothing = v) }
                addSliderRow(VisualizerSettingField.SPEED, "Speed", VisualizerSettingRanges.speed, 0.05f, { it.speed }, ::formatFloat) { s, v -> s.copy(speed = v) }
                addSliderRow(VisualizerSettingField.GLOW, "Glow", VisualizerSettingRanges.glow, 0.05f, { it.glow }, ::formatFloat) { s, v -> s.copy(glow = v) }
            }

            "Response" -> {
                addSliderRow(VisualizerSettingField.SENSITIVITY, "Sensitivity", VisualizerSettingRanges.sensitivity, 0.05f, { it.sensitivity }, ::formatFloat) { s, v -> s.copy(sensitivity = v) }
                addSliderRow(VisualizerSettingField.AMPLITUDE, "Amplitude", VisualizerSettingRanges.amplitude, 0.05f, { it.amplitude }, ::formatFloat) { s, v -> s.copy(amplitude = v) }
                addSliderRow(VisualizerSettingField.BASS_EMPHASIS, "Bass emphasis", VisualizerSettingRanges.bassEmphasis, 0.05f, { it.bassEmphasis }, ::formatFloat) { s, v -> s.copy(bassEmphasis = v) }
                addSliderRow(VisualizerSettingField.BAR_COUNT, "Bar count", VisualizerSettingRanges.barCount.first.toFloat()..VisualizerSettingRanges.barCount.last.toFloat(), 1f, { it.barCount.toFloat() }, ::formatInt) { s, v -> s.copy(barCount = v.roundToInt()) }
                addSliderRow(VisualizerSettingField.BAR_WIDTH, "Bar width", VisualizerSettingRanges.barWidth, 0.05f, { it.barWidth }, ::formatFloat) { s, v -> s.copy(barWidth = v) }
                addSliderRow(VisualizerSettingField.BAR_SPACING, "Bar spacing", VisualizerSettingRanges.barSpacing, 0.05f, { it.barSpacing }, ::formatFloat) { s, v -> s.copy(barSpacing = v) }
                addSliderRow(VisualizerSettingField.PARTICLE_COUNT, "Particle count", VisualizerSettingRanges.particleCount.first.toFloat()..VisualizerSettingRanges.particleCount.last.toFloat(), 1f, { it.particleCount.toFloat() }, ::formatInt) { s, v -> s.copy(particleCount = v.roundToInt()) }
                addSliderRow(VisualizerSettingField.PARTICLE_SIZE, "Particle size", VisualizerSettingRanges.particleSize, 0.25f, { it.particleSize }, ::formatFloat) { s, v -> s.copy(particleSize = v) }
            }

            "Background" -> {
                addSliderRow(VisualizerSettingField.BACKGROUND_ALPHA, "Background alpha", VisualizerSettingRanges.backgroundAlpha, 0.05f, { it.backgroundAlpha }, ::formatFloat) { s, v -> s.copy(backgroundAlpha = v) }
                addSliderRow(VisualizerSettingField.FPS_LIMIT, "Preview frame rate", 30f..60f, 30f, { it.fpsLimit.toFloat() }, ::formatFps) { s, v -> s.copy(fpsLimit = v.roundToInt()) }
            }
        }
        return result
    }

    private fun addSliderRow(
        field: VisualizerSettingField,
        label: String,
        range: ClosedFloatingPointRange<Float>,
        step: Float,
        getter: (VisualizerSettings) -> Float,
        formatter: (Float) -> String,
        setter: (VisualizerSettings, Float) -> VisualizerSettings,
    ) {
        val slider = ThemedSlider(range.start, range.endInclusive, getter(store.settings), step)
        val valueLabel = JLabel(formatter(getter(store.settings))).apply {
            font = Fonts.ui(11)
            foreground = Palette.muted
        }
        val row = settingRow(label, valueLabel, slider) {
            change { current -> setter(current, defaultValue(field)) }
        }
        slider.onChange = { value, _ ->
            if (!syncing.value) change { current -> setter(current, value) }
        }
        rows[field] = row
        sliders[field] = slider
        valueLabels[field] = valueLabel
        valueText[field] = { settings -> formatter(getter(settings)) }
    }

    private fun addColorRow(
        field: VisualizerSettingField,
        label: String,
        combo: ThemedComboBox<AccentChoice>,
        setter: (VisualizerSettings, Color) -> VisualizerSettings,
    ) {
        combo.preferredSize = Dimension(150, 32)
        combo.addActionListener {
            if (syncing.value) return@addActionListener
            val choice = combo.selectedItem as? AccentChoice ?: return@addActionListener
            change { current -> setter(current, choice.color) }
        }
        val row = settingRow(label, JLabel().apply { isVisible = false }, combo) {
            val fallback = if (field == VisualizerSettingField.PRIMARY_COLOR) DEFAULT_PRIMARY_COLOR else DEFAULT_SECONDARY_COLOR
            change { current -> current.copy(primaryColor = if (field == VisualizerSettingField.PRIMARY_COLOR) fallback else current.primaryColor, secondaryColor = if (field == VisualizerSettingField.SECONDARY_COLOR) fallback else current.secondaryColor) }
        }
        rows[field] = row
    }

    private fun addCheckRow(
        field: VisualizerSettingField,
        label: String,
        getter: (VisualizerSettings) -> Boolean,
        setter: (VisualizerSettings, Boolean) -> VisualizerSettings,
    ) {
        val check = themedCheck(label, getter(store.settings))
        check.addActionListener { if (!syncing.value) change { current -> setter(current, check.isSelected) } }
        val row = settingRow("", JLabel().apply { isVisible = false }, check) {
            change { current -> setter(current, defaultValue(field) > 0f) }
        }
        rows[field] = row
        checks[field] = check
    }

    private fun settingRow(label: String, value: JLabel, control: JComponent, reset: () -> Unit): JPanel = JPanel(BorderLayout(8, 4)).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 58)
        val top = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel(label).apply { foreground = Palette.text; font = Fonts.ui(12) }, BorderLayout.CENTER)
            add(value, BorderLayout.EAST)
        }
        add(top, BorderLayout.NORTH)
        add(
            JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(control, BorderLayout.CENTER)
                add(FlatButton("Reset").apply { addActionListener { reset() } }, BorderLayout.EAST)
            },
            BorderLayout.CENTER,
        )
    }

    private fun refreshGroups() {
        controlCard.removeAll()
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
        }
        groups.forEachIndexed { index, (panel, fields) ->
            panel.removeAll()
            panel.add(sectionLabel(groupTitle(index)))
            panel.add(Box.createVerticalStrut(8))
            fields.filter { store.settings.mode.isSettingRelevant(it) }.forEach { field ->
                rows[field]?.let { row ->
                    row.isVisible = true
                    setEnabledRecursively(row, !store.isReadOnly)
                    panel.add(row)
                    panel.add(Box.createVerticalStrut(8))
                }
            }
            panel.revalidate()
            panel.repaint()
            body.add(panel)
            if (index < groups.lastIndex) body.add(Box.createVerticalStrut(12))
        }
        controlCard.add(body, BorderLayout.CENTER)
        controlCard.revalidate()
        controlCard.repaint()
    }

    private fun syncControls() {
        syncing.value = true
        val current = store.settings
        modeBox.selectedItem = current.mode
        enabledCheck.isSelected = current.enabled
        primaryColorBox.selectedItem = closestAccent(current.primaryColor)
        secondaryColorBox.selectedItem = closestAccent(current.secondaryColor)
        checks[VisualizerSettingField.FOLLOW_ACCENT]?.isSelected = current.followAccent
        checks[VisualizerSettingField.MULTICOLOR]?.isSelected = current.multicolor
        sliders.forEach { (field, slider) ->
            slider.value = when (field) {
                VisualizerSettingField.SMOOTHING -> current.smoothing
                VisualizerSettingField.SPEED -> current.speed
                VisualizerSettingField.GLOW -> current.glow
                VisualizerSettingField.SENSITIVITY -> current.sensitivity
                VisualizerSettingField.AMPLITUDE -> current.amplitude
                VisualizerSettingField.BASS_EMPHASIS -> current.bassEmphasis
                VisualizerSettingField.BAR_COUNT -> current.barCount.toFloat()
                VisualizerSettingField.BAR_WIDTH -> current.barWidth
                VisualizerSettingField.BAR_SPACING -> current.barSpacing
                VisualizerSettingField.PARTICLE_COUNT -> current.particleCount.toFloat()
                VisualizerSettingField.PARTICLE_SIZE -> current.particleSize
                VisualizerSettingField.BACKGROUND_ALPHA -> current.backgroundAlpha
                VisualizerSettingField.FPS_LIMIT -> current.fpsLimit.toFloat()
                else -> slider.value
            }
            valueLabels[field]?.text = valueText[field]?.invoke(current).orEmpty()
        }
        syncing.value = false
        refreshGroups()
        primaryColorBox.isEnabled = !current.followAccent && !store.isReadOnly
        secondaryColorBox.isEnabled = !store.isReadOnly
        enabledCheck.isEnabled = !store.isReadOnly
        modeBox.isEnabled = !store.isReadOnly
        preview.repaint()
        onSettingsChanged()
    }

    private fun change(transform: (VisualizerSettings) -> VisualizerSettings) {
        if (store.setSettings(transform(store.settings))) {
            storeStatus.text = "Changes save automatically."
            syncControls()
        } else if (store.isReadOnly) {
            storeStatus.text = store.statusMessage.orEmpty()
        }
    }

    private fun setEnabledRecursively(component: JComponent, enabled: Boolean) {
        component.isEnabled = enabled
        component.components.forEach { child ->
            if (child is JComponent) setEnabledRecursively(child, enabled)
        }
    }

    private fun resetAll() {
        change { VisualizerSettings.defaults }
    }

    private fun restorePrevious() {
        if (store.restorePrevious()) {
            storeStatus.text = "Previous settings restored."
            syncControls()
        }
    }

    private fun randomize() {
        val random = Random.Default
        val primary = AccentChoice.entries.random(random).color.rgb and RGB_MASK
        val secondary = AccentChoice.entries.random(random).color.rgb and RGB_MASK
        change { current ->
            current.copy(
                primaryColor = primary,
                secondaryColor = secondary,
                followAccent = false,
                multicolor = true,
                sensitivity = random.nextFloatBetween(0.6f, 2.0f),
                amplitude = random.nextFloatBetween(0.6f, 1.5f),
                smoothing = random.nextFloatBetween(0.3f, 0.85f),
                speed = random.nextFloatBetween(0.6f, 2.0f),
                glow = random.nextFloatBetween(0.2f, 0.9f),
                barCount = random.nextInt(16, 49),
                barWidth = random.nextFloatBetween(0.2f, 0.9f),
                barSpacing = random.nextFloatBetween(0f, 0.8f),
                particleCount = random.nextInt(60, 241),
                particleSize = random.nextFloatBetween(1f, 8f),
                backgroundAlpha = random.nextFloatBetween(0.5f, 1f),
            )
        }
        storeStatus.text = "Randomized visualizer settings."
    }

    private fun defaultValue(field: VisualizerSettingField): Float = when (field) {
        VisualizerSettingField.SMOOTHING -> 0.55f
        VisualizerSettingField.SPEED -> 1.0f
        VisualizerSettingField.GLOW -> 0.6f
        VisualizerSettingField.SENSITIVITY -> 1.0f
        VisualizerSettingField.AMPLITUDE -> 1.0f
        VisualizerSettingField.BASS_EMPHASIS -> 0.0f
        VisualizerSettingField.BAR_COUNT -> 32f
        VisualizerSettingField.BAR_WIDTH -> 0.5f
        VisualizerSettingField.BAR_SPACING -> 0.5f
        VisualizerSettingField.PARTICLE_COUNT -> 160f
        VisualizerSettingField.PARTICLE_SIZE -> 3f
        VisualizerSettingField.BACKGROUND_ALPHA -> 1f
        VisualizerSettingField.FPS_LIMIT -> 30f
        VisualizerSettingField.FOLLOW_ACCENT -> 1f
        VisualizerSettingField.MULTICOLOR -> 0f
        else -> 0f
    }

    private fun groupTitle(index: Int): String = listOf("Colors", "Motion", "Response", "Background")[index]

    private fun closestAccent(rgb: Int): AccentChoice = AccentChoice.entries.minByOrNull { choice ->
        val color = choice.color
        val target = Color(rgb and RGB_MASK)
        val red = color.red - target.red
        val green = color.green - target.green
        val blue = color.blue - target.blue
        red * red + green * green + blue * blue
    } ?: AccentChoice.MOCHA

    private fun themedCheck(text: String, selected: Boolean): JCheckBox = JCheckBox(text, selected).apply {
        isOpaque = false
        font = Fonts.ui(12)
        foreground = Palette.text
        isFocusPainted = false
        toolTipText = text
    }

    private class MutableBoolean(var value: Boolean)

    private fun falseState() = MutableBoolean(false)

    private companion object {
        const val RGB_MASK = 0xFFFFFF
        const val DEFAULT_PRIMARY_COLOR = 0xC49874
        const val DEFAULT_SECONDARY_COLOR = 0x8FA3B8

        fun formatFloat(value: Float): String = String.format(Locale.ROOT, "%.2f", value)
        fun formatInt(value: Float): String = value.roundToInt().toString()
        fun formatFps(value: Float): String = "${value.roundToInt()} fps"
        fun Random.nextFloatBetween(minimum: Float, maximum: Float): Float =
            minimum + nextFloat() * (maximum - minimum)
    }
}

private class VisualizerPreview(
    private val playback: PlaybackController,
    private val settings: () -> VisualizerSettings,
    private val simulateStopped: () -> Boolean,
    private val onSignal: (String) -> Unit,
) : JComponent() {
    private var renderer: VisualizerRenderer = rendererFor(settings().mode)
    private val inactiveFeatures = AudioFeatures()
    private val simulated = SimulatedFeatures()
    private val ticker = Timer(16) { tick() }
    private var attached = false
    private var startedAtNanos = System.nanoTime()
    private var lastMode = settings().mode
    private var lastSignal = ""

    init {
        isOpaque = false
        preferredSize = Dimension(0, 150)
        minimumSize = Dimension(0, 118)
    }

    override fun addNotify() {
        super.addNotify()
        startedAtNanos = System.nanoTime()
        ticker.start()
    }

    override fun removeNotify() {
        ticker.stop()
        detach()
        super.removeNotify()
    }

    private fun tick() {
        if (!isShowing) {
            detach()
            return
        }
        val current = settings()
        ticker.delay = if (current.fpsLimit == 60) PREVIEW_FAST_FRAME_MS else PREVIEW_SLOW_FRAME_MS
        if (current.mode != lastMode) {
            renderer = rendererFor(current.mode)
            lastMode = current.mode
            repaint()
        }
        if (current.enabled && !attached) {
            playback.attachFeatures()
            attached = true
        }
        if (!current.enabled) detach()
        val live = if (attached) playback.audioFeatures() else inactiveFeatures
        val features = if (!live.active && simulateStopped() && current.enabled) {
            simulated.at((System.nanoTime() - startedAtNanos) / 1_000_000L)
        } else {
            live
        }
        signalFor(current, live)
        if (renderer.update(features, current)) repaint()
    }

    private fun signalFor(current: VisualizerSettings, live: AudioFeatures) {
        val next = when {
            !current.enabled -> "Visualizer is off"
            live.active -> "Live audio"
            simulateStopped() -> "Simulated signal"
            else -> "Waiting for playback"
        }
        if (next != lastSignal) {
            lastSignal = next
            onSignal(next)
        }
    }

    private fun detach() {
        if (attached) {
            playback.detachFeatures()
            attached = false
        }
    }

    override fun paintComponent(g: java.awt.Graphics) {
        val current = settings()
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val reach = max(w, h) * 0.9f
        g2.color = org.catosaur.app.withAlpha(Palette.base, current.backgroundAlpha)
        g2.fillRect(0, 0, width, height)
        g2.paint = RadialGradientPaint(
            Point2D.Float(w * 0.08f, 0f),
            reach,
            floatArrayOf(0f, 1f),
            arrayOf(org.catosaur.app.withAlpha(Palette.rose, 0.28f), org.catosaur.app.withAlpha(Palette.rose, 0f)),
        )
        g2.fillRect(0, 0, width, height)
        g2.paint = RadialGradientPaint(
            Point2D.Float(w * 0.95f, h),
            reach,
            floatArrayOf(0f, 1f),
            arrayOf(org.catosaur.app.withAlpha(Palette.glow, 0.12f), org.catosaur.app.withAlpha(Palette.glow, 0f)),
        )
        g2.fillRect(0, 0, width, height)
        renderer.render(g2, width, height, inactiveFeatures, current)
        if (!current.enabled) {
            g2.font = Fonts.ui(13)
            g2.color = Palette.muted
            val message = "Visualizer is off"
            val metrics = g2.fontMetrics
            g2.drawString(message, (w - metrics.stringWidth(message)) / 2f, (h - metrics.height) / 2f + metrics.ascent)
        }
        g2.dispose()
    }

    private companion object {
        const val PREVIEW_FAST_FRAME_MS = 16
        const val PREVIEW_SLOW_FRAME_MS = 33
    }
}
