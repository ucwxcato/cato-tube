package org.catosaur.app

import org.catosaur.app.playback.SPECTRUM_BANDS
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.LayoutManager
import java.awt.LinearGradientPaint
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Arc2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.BasicComboBoxUI
import javax.swing.plaf.basic.BasicComboPopup
import javax.swing.plaf.basic.BasicScrollBarUI
import javax.swing.plaf.basic.ComboPopup
import javax.swing.table.TableModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Dark glass palette: near-black base, translucent white glass layers, warm off-white text, mocha accent.
internal object Palette {
    /** White glass layer opacities, lightest to strongest. Cards use [GLASS_CARD]; dialogs use [GLASS_RAISED]. */
    const val GLASS_BASE = 0.028f
    const val GLASS_CARD = 0.056f
    const val GLASS_RAISED = 0.104f
    const val GLASS_TOP = 0.179f

    val base = Color(20, 21, 25)            // window body
    val surface = Color(34, 36, 41)         // panels, title bars
    val surfaceRaised = Color(50, 53, 60)   // default button
    val surfaceHigh = Color(62, 66, 74)     // hovered default button
    val field = Color(44, 47, 54)           // progress track
    val stroke = Color(70, 74, 82)          // borders and separators
    val text = Color(204, 198, 190)         // primary UI text, warm off-white (not pure white)
    val muted = Color(134, 128, 122)        // secondary labels, warm grey
    val danger = Color(214, 84, 80)
    val rose = Color(120, 72, 56)           // warm light, top left
    val violet = Color(120, 90, 170)
    val glassSheen = Color(255, 255, 255, 18)
    val glassEdge = Color(255, 255, 255, 40)

    // Accent family. Changed only through applyAccent(); the initial values are the mocha defaults.
    var accent = Color(128, 92, 66)         // selection and progress fill
        private set
    var focus = Color(196, 152, 116)        // focused outline, active nav pill, latte highlight
        private set
    var primary = Color(104, 72, 50)        // default (primary) button
        private set
    var primaryHover = Color(122, 86, 60)
        private set
    var primaryPressed = Color(86, 58, 40)
        private set
    var selection = Color(96, 68, 50)       // text selection
        private set
    var glow = Color(140, 100, 70)          // warm light, bottom right
        private set

    /**
     * Rebuilds the accent family from one base color. Hue and saturation carry through to every
     * shade; only brightness moves, so each choice keeps the same feel.
     */
    fun applyAccent(base: Color) {
        val hsb = Color.RGBtoHSB(base.red, base.green, base.blue, null)
        val hue = hsb[0]
        val sat = hsb[1]
        val bright = hsb[2]
        fun tone(saturation: Float, brightness: Float): Color =
            Color.getHSBColor(hue, saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))
        accent = base
        focus = tone(sat * 0.8f, bright + (1f - bright) * 0.5f)
        primary = tone(sat, bright * 0.82f)
        primaryHover = tone(sat, bright * 0.95f)
        primaryPressed = tone(sat, bright * 0.67f)
        selection = tone(sat * 0.9f, bright * 0.75f)
        glow = tone(sat, bright * 1.1f)
    }
}

/** Accent colors offered in the sidebar. Mocha is the default; the rest come from the FLB accent set. */
internal enum class AccentChoice(private val label: String, val color: Color) {
    MOCHA("Mocha", Color(128, 92, 66)),
    BLUE("Blue", Color(0x00, 0x66, 0xff)),
    PERIWINKLE("Periwinkle", Color(0x7a, 0x86, 0xcb)),
    ORCHID("Orchid", Color(0xba, 0x68, 0xc6)),
    PEACH("Peach", Color(0xfd, 0x8b, 0x64)),
    SAGE("Sage", Color(0xac, 0xd5, 0x80)),
    GOLD("Gold", Color(0xfc, 0xd4, 0x50)),
    TEAL("Teal", Color(0x4d, 0xb6, 0xac)),
    PINK("Pink", Color(0xee, 0x63, 0x90)),
    CORAL("Coral", Color(0xe5, 0x73, 0x75)),
    SALMON("Salmon", Color(0xff, 0x8a, 0x66)),
    ;

    override fun toString(): String = label
}

internal object Fonts {
    private val families: Set<String> by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    }
    private fun pick(vararg names: String, fallback: String): String =
        names.firstOrNull { it in families } ?: fallback

    // Bahnschrift ships with Windows 10 and 11. Its DIN-derived forms suit a library and downloader tool.
    private val uiFamily: String by lazy { pick("Bahnschrift", "Segoe UI Variable Text", "Segoe UI", fallback = Font.SANS_SERIF) }
    private val monoFamily: String by lazy { pick("Cascadia Mono", "Consolas", fallback = Font.MONOSPACED) }

    fun ui(size: Int, style: Int = Font.PLAIN): Font = Font(uiFamily, style, size)
    fun mono(size: Int, style: Int = Font.PLAIN): Font = Font(monoFamily, style, size)
}

internal fun mix(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    fun channel(x: Int, y: Int) = (x + (y - x) * k).roundToInt()
    return Color(
        channel(a.red, b.red),
        channel(a.green, b.green),
        channel(a.blue, b.blue),
        channel(a.alpha, b.alpha),
    )
}

internal fun withAlpha(color: Color, alpha: Float): Color =
    Color(color.red, color.green, color.blue, (alpha.coerceIn(0f, 1f) * 255).roundToInt())

/**
 * Fills [shape] with a near-opaque [Palette.base] layer. Translucent fills on top keep their look, but
 * the bars behind them are hidden, so the backdrop reads as background only.
 */
internal fun paintBacking(g2: Graphics2D, shape: Shape) {
    g2.color = withAlpha(Palette.base, BACKING_ALPHA)
    g2.fill(shape)
}

private const val BACKING_ALPHA = 0.9f

internal fun Graphics.smoothCopy(): Graphics2D = (create() as Graphics2D).apply {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
}

/** Smoothly moves a value from its current state toward [target] on each Swing tick. */
internal class Eased(private val speed: Float, private val onStep: () -> Unit) {
    var current = 0f
        private set

    var target = 0f
        set(value) {
            field = value
            if (!timer.isRunning) timer.start()
        }

    /** Jumps to [value] without animating. */
    fun snapTo(value: Float) {
        timer.stop()
        current = value
        target = value
    }

    private val timer: Timer = Timer(16) {
        current += (target - current) * speed
        if (abs(target - current) < 0.002f) {
            current = target
            timer.stop()
        }
        onStep()
    }
}

/**
 * Small progress ring for background downloads. It fades in and out and takes no input, so it
 * never blocks the page under it. Without a percent it spins; with one, the arc fills clockwise.
 * Give it a fixed slot in a layout so showing it does not shift anything.
 */
internal class DownloadRing : JComponent() {
    /** Runs when the user clicks the ring while a download is running. */
    var onCancel: (() -> Unit)? = null

    private var indeterminate = true
    private var percent: Int? = null
    private var spinDegrees = 0.0
    private val fill = Eased(0.2f) { repaint() }
    private val visibility = Eased(0.12f) { repaint() }
    private val spin: Timer = Timer(16) {
        spinDegrees = (spinDegrees + 5.0) % 360.0
        if (visibility.current <= 0f && visibility.target == 0f) {
            spin.stop()
        } else {
            repaint()
        }
    }

    init {
        isOpaque = false
        preferredSize = Dimension(RING_SLOT, RING_SLOT)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (visibility.target == 1f) onCancel?.invoke()
            }
        })
    }

    /** Shows the ring for [title] at [fraction] (0..1), or spins it when [fraction] is null. */
    fun showActivity(title: String, fraction: Float?) {
        indeterminate = fraction == null
        percent = fraction?.let { (it.coerceIn(0f, 1f) * 100).roundToInt() }
        if (fraction != null) fill.target = fraction.coerceIn(0f, 1f)
        toolTipText = if (percent == null) {
            "Downloading $title. Click to cancel."
        } else {
            "Downloading $title ($percent%). Click to cancel."
        }
        visibility.target = 1f
        if (!spin.isRunning) spin.start()
    }

    /** Fades the ring out. It stops spinning once it is fully hidden. */
    fun fadeOut() {
        visibility.target = 0f
        toolTipText = null
    }

    override fun paintComponent(g: Graphics) {
        val alpha = visibility.current
        if (alpha <= 0.001f) return
        val g2 = g.smoothCopy()
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
        val inset = RING_STROKE / 2f + 2f
        val diameter = RING_SLOT - inset * 2
        val x = (width - RING_SLOT) / 2f + inset
        val y = (height - RING_SLOT) / 2f + inset
        g2.stroke = BasicStroke(RING_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = Palette.field
        g2.draw(Ellipse2D.Float(x, y, diameter, diameter))
        g2.color = Palette.focus
        if (indeterminate) {
            g2.draw(Arc2D.Float(x, y, diameter, diameter, (90.0 - spinDegrees).toFloat(), -100f, Arc2D.OPEN))
        } else {
            val sweep = -360f * fill.current
            if (sweep < -0.5f) {
                g2.draw(Arc2D.Float(x, y, diameter, diameter, 90f, sweep, Arc2D.OPEN))
            }
        }
        percent?.takeUnless { indeterminate }?.let { value ->
            g2.font = Fonts.ui(10)
            g2.color = Palette.text
            val label = "$value%"
            val metrics = g2.fontMetrics
            g2.drawString(
                label,
                width / 2f - metrics.stringWidth(label) / 2f,
                height / 2f + (metrics.ascent - metrics.descent) / 2f,
            )
        }
        g2.dispose()
    }

    private companion object {
        const val RING_SLOT = 38
        const val RING_STROKE = 3f
    }
}

internal enum class TransportGlyph { PREVIOUS, PLAY, PAUSE, NEXT, SHUFFLE, REPEAT, REPEAT_ONE, VOLUME, MUTED }

/**
 * Round icon button for the player bar. The glyph is drawn with shapes, not an icon font. [active]
 * tints it with the focus color (shuffle and repeat on). [primary] draws the filled play button.
 */
internal class TransportButton(
    var glyph: TransportGlyph,
    private val size: Int = 30,
    private val primary: Boolean = false,
) : JComponent() {
    var active: Boolean = false
        set(value) {
            field = value
            repaint()
        }

    /** Runs on a click with the button enabled. */
    var onClick: (() -> Unit)? = null

    private val hover = Eased(0.25f) { repaint() }

    init {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(size, size)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hover.target = 1f
            }

            override fun mouseExited(e: MouseEvent) {
                hover.target = 0f
            }

            override fun mouseClicked(e: MouseEvent) {
                if (isEnabled && e.button == MouseEvent.BUTTON1) onClick?.invoke()
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val radius = size / 2f
        val cx = width / 2f
        val cy = height / 2f
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, if (isEnabled) 1f else 0.35f)
        val disc = Ellipse2D.Float(cx - radius, cy - radius, size.toFloat(), size.toFloat())
        val ink = when {
            primary -> Color.WHITE
            active -> Palette.focus
            else -> mix(Palette.muted, Palette.text, hover.current)
        }
        if (primary) {
            val top = mix(Palette.primaryHover, Color.WHITE, 0.10f)
            g2.paint = GradientPaint(0f, cy - radius, top, 0f, cy + radius, Palette.primary)
            g2.fill(disc)
        } else if (hover.current > 0f) {
            g2.color = withAlpha(Color.WHITE, 0.06f * hover.current)
            g2.fill(disc)
        }
        g2.color = ink
        drawGlyph(g2, glyph, cx - size * 0.21f, cy - size * 0.21f, size * 0.42f)
        g2.dispose()
    }

    private companion object {
        fun drawGlyph(g2: Graphics2D, glyph: TransportGlyph, x0: Float, y0: Float, u: Float) {
            g2.stroke = BasicStroke(maxOf(1.5f, u * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            fun at(x: Float, y: Float) = Point2D.Float(x0 + x * u, y0 + y * u)
            fun polygon(vararg points: Float) {
                val path = Path2D.Float()
                path.moveTo(x0 + points[0] * u, y0 + points[1] * u)
                var i = 2
                while (i < points.size) {
                    path.lineTo(x0 + points[i] * u, y0 + points[i + 1] * u)
                    i += 2
                }
                path.closePath()
                g2.fill(path)
            }
            fun polyline(vararg points: Float) {
                val path = Path2D.Float()
                path.moveTo(x0 + points[0] * u, y0 + points[1] * u)
                var i = 2
                while (i < points.size) {
                    path.lineTo(x0 + points[i] * u, y0 + points[i + 1] * u)
                    i += 2
                }
                g2.draw(path)
            }
            fun bar(x: Float, y: Float, w: Float, h: Float) {
                g2.fill(RoundRectangle2D.Float(x0 + x * u, y0 + y * u, w * u, h * u, w * u / 2, w * u / 2))
            }
            fun speaker() {
                polygon(0.12f, 0.38f, 0.30f, 0.38f, 0.50f, 0.22f, 0.50f, 0.78f, 0.30f, 0.62f, 0.12f, 0.62f)
            }
            when (glyph) {
                TransportGlyph.PLAY -> polygon(0.30f, 0.20f, 0.80f, 0.50f, 0.30f, 0.80f)
                TransportGlyph.PAUSE -> {
                    bar(0.28f, 0.20f, 0.16f, 0.60f)
                    bar(0.56f, 0.20f, 0.16f, 0.60f)
                }
                TransportGlyph.PREVIOUS -> {
                    bar(0.16f, 0.22f, 0.08f, 0.56f)
                    polygon(0.84f, 0.22f, 0.84f, 0.78f, 0.26f, 0.50f)
                }
                TransportGlyph.NEXT -> {
                    bar(0.76f, 0.22f, 0.08f, 0.56f)
                    polygon(0.16f, 0.22f, 0.16f, 0.78f, 0.74f, 0.50f)
                }
                TransportGlyph.SHUFFLE -> {
                    polyline(0.10f, 0.70f, 0.35f, 0.70f, 0.65f, 0.30f, 0.90f, 0.30f)
                    polyline(0.10f, 0.30f, 0.35f, 0.30f, 0.65f, 0.70f, 0.90f, 0.70f)
                    polyline(0.80f, 0.20f, 0.90f, 0.30f, 0.80f, 0.40f)
                    polyline(0.80f, 0.60f, 0.90f, 0.70f, 0.80f, 0.80f)
                }
                TransportGlyph.REPEAT, TransportGlyph.REPEAT_ONE -> {
                    g2.draw(RoundRectangle2D.Float(x0 + 0.16f * u, y0 + 0.28f * u, 0.68f * u, 0.44f * u, 0.22f * u, 0.22f * u))
                    polygon(0.70f, 0.14f, 0.86f, 0.24f, 0.70f, 0.34f)
                    if (glyph == TransportGlyph.REPEAT_ONE) {
                        polyline(0.46f, 0.42f, 0.52f, 0.38f, 0.52f, 0.62f)
                    }
                }
                TransportGlyph.VOLUME -> {
                    speaker()
                    g2.draw(Arc2D.Float(x0 + 0.44f * u, y0 + 0.30f * u, 0.40f * u, 0.40f * u, -45f, 90f, Arc2D.OPEN))
                }
                TransportGlyph.MUTED -> {
                    speaker()
                    polyline(0.64f, 0.36f, 0.88f, 0.64f)
                    polyline(0.88f, 0.36f, 0.64f, 0.64f)
                }
            }
        }
    }
}

/**
 * Thin horizontal slider with an accent fill and a small knob. Drag updates are shown at once.
 * [onChange] runs on every user change with a flag that is true when the mouse is released, so a
 * seek can wait for the release. While the user drags, outside updates to [fraction] are ignored.
 */
internal class ProgressSlider : JComponent() {
    var fraction: Float = 0f
        set(value) {
            if (dragging) return
            field = value.coerceIn(0f, 1f)
            repaint()
        }

    var onChange: ((fraction: Float, released: Boolean) -> Unit)? = null

    /** True while the mouse is held down on the slider. */
    var dragging: Boolean = false
        private set

    init {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(160, 18)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!isEnabled || e.button != MouseEvent.BUTTON1) return
                dragging = true
                setFromX(e.x, released = false)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (!dragging) return
                dragging = false
                setFromX(e.x, released = true)
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (dragging) setFromX(e.x, released = false)
            }
        })
    }

    private fun setFromX(x: Int, released: Boolean) {
        val usable = (width - KNOB_RADIUS * 2).coerceAtLeast(1)
        val value = ((x - KNOB_RADIUS).toFloat() / usable).coerceIn(0f, 1f)
        fraction = value
        onChange?.invoke(value, released)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, if (isEnabled) 1f else 0.35f)
        val left = KNOB_RADIUS.toFloat()
        val usable = width - KNOB_RADIUS * 2f
        val cy = height / 2f
        val trackHeight = 4f
        g2.color = Palette.field
        g2.fill(RoundRectangle2D.Float(left, cy - trackHeight / 2, usable, trackHeight, trackHeight, trackHeight))
        val filled = usable * fraction
        g2.color = Palette.accent
        g2.fill(RoundRectangle2D.Float(left, cy - trackHeight / 2, filled, trackHeight, trackHeight, trackHeight))
        g2.color = Palette.text
        g2.fill(Ellipse2D.Float(left + filled - KNOB_RADIUS, cy - KNOB_RADIUS, KNOB_RADIUS * 2f, KNOB_RADIUS * 2f))
        g2.dispose()
    }

    private companion object {
        const val KNOB_RADIUS = 5
    }
}

internal fun sectionLabel(text: String): JLabel = JLabel(text.uppercase()).apply {
    font = Fonts.ui(11, Font.BOLD)
    foreground = Palette.muted
}

/**
 * Uppercase heading with a thin accent line that fades out to the right of the text. Use it where
 * a heading sits in a row that has room for the line.
 */
internal class SectionHeading(initial: String) : JComponent() {
    var text: String = initial.uppercase()
        set(value) {
            field = value.uppercase()
            revalidate()
            repaint()
        }

    init {
        isOpaque = false
        font = Fonts.ui(11, Font.BOLD)
        foreground = Palette.muted
    }

    override fun getPreferredSize(): Dimension {
        val metrics = getFontMetrics(font)
        return Dimension(metrics.stringWidth(text) + LINE_GAP * 2, metrics.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        g2.font = font
        val metrics = g2.fontMetrics
        val textWidth = metrics.stringWidth(text)
        g2.color = foreground
        g2.drawString(text, 0f, (height + metrics.ascent - metrics.descent) / 2f)
        val start = textWidth + LINE_GAP.toFloat()
        if (start < width) {
            val y = height / 2f + 0.5f
            g2.paint = LinearGradientPaint(
                Point2D.Float(start, y),
                Point2D.Float(width.toFloat(), y),
                floatArrayOf(0f, 0.6f, 1f),
                arrayOf(withAlpha(Palette.accent, 0.9f), withAlpha(Palette.accent, 0.35f), withAlpha(Palette.accent, 0f)),
            )
            g2.stroke = BasicStroke(1f)
            g2.draw(Line2D.Float(start, y, width.toFloat(), y))
        }
        g2.dispose()
    }

    private companion object {
        const val LINE_GAP = 12
    }
}

/**
 * Window body: the editor background with two soft colored glows and a live spectrum. [spectrum]
 * supplies band levels (0 to 1) each frame; the bars rise from the bottom edge and ease toward it, so
 * they settle flat when playback stops. Translucent [Card]s sit on top, so the bars show through the glass.
 */
internal class Backdrop(
    private val spectrum: () -> FloatArray = { FloatArray(SPECTRUM_BANDS) },
) : JPanel(BorderLayout()) {
    private val shown = FloatArray(SPECTRUM_BANDS)
    private val ticker = Timer(BARS_FRAME_MS) { tickBars() }

    init {
        isOpaque = true
        background = Palette.base
        ticker.start()
    }

    private fun tickBars() {
        val target = spectrum()
        var moved = false
        for (band in shown.indices) {
            val goal = target[band]
            val rate = if (goal > shown[band]) BARS_ATTACK else BARS_RELEASE
            val next = shown[band] + (goal - shown[band]) * rate
            if (abs(next - shown[band]) > 0.001f) moved = true
            shown[band] = next
        }
        if (moved) repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val reach = max(w, h) * 0.7f
        g2.color = Palette.base
        g2.fillRect(0, 0, width, height)
        g2.paint = RadialGradientPaint(
            Point2D.Float(w * 0.08f, h * 0.0f),
            reach,
            floatArrayOf(0f, 1f),
            arrayOf(withAlpha(Palette.rose, 0.34f), withAlpha(Palette.rose, 0f)),
        )
        g2.fillRect(0, 0, width, height)
        g2.paint = RadialGradientPaint(
            Point2D.Float(w * 0.95f, h * 1.0f),
            reach,
            floatArrayOf(0f, 1f),
            arrayOf(withAlpha(Palette.glow, 0.13f), withAlpha(Palette.glow, 0f)),
        )
        g2.fillRect(0, 0, width, height)
        paintBars(g2, w, h)
        g2.dispose()
    }

    /** One accent color for every bar. Each bar has a wide faint halo and a brighter core. */
    private fun paintBars(g2: Graphics2D, w: Float, h: Float) {
        val slot = w / SPECTRUM_BANDS
        val barWidth = slot * 0.5f
        val maxHeight = h * 0.5f
        for (band in 0 until SPECTRUM_BANDS) {
            val level = shown[band].coerceIn(0f, 1f)
            if (level < 0.01f) continue
            val barHeight = maxHeight * level
            val x = slot * band + (slot - barWidth) / 2f
            val y = h - barHeight
            g2.color = withAlpha(Palette.focus, 0.02f + 0.04f * level)
            g2.fill(RoundRectangle2D.Float(x - 12f, y - 12f, barWidth + 24f, barHeight + 12f, 22f, 22f))
            g2.color = withAlpha(Palette.focus, 0.04f + 0.07f * level)
            g2.fill(RoundRectangle2D.Float(x - 5f, y - 5f, barWidth + 10f, barHeight + 5f, 12f, 12f))
            g2.color = withAlpha(Palette.focus, 0.08f + 0.14f * level)
            g2.fill(RoundRectangle2D.Float(x, y, barWidth, barHeight, 6f, 6f))
        }
    }
}

private const val BARS_FRAME_MS = 33
private const val BARS_ATTACK = 0.55f
private const val BARS_RELEASE = 0.14f

/**
 * Frosted-glass panel: a white translucent layer over whatever is behind it, a sheen band at the
 * top, and a bright top edge that fades into the normal hairline at the bottom. [tier] picks the
 * layer strength from [Palette.GLASS_CARD] or [Palette.GLASS_RAISED].
 */
internal class Card(
    layout: LayoutManager = BorderLayout(),
    private val arc: Float = 14f,
    private val tier: Float = Palette.GLASS_CARD,
) : JPanel(layout) {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val body = RoundRectangle2D.Float(0f, 0f, w, h, arc, arc)
        paintBacking(g2, body)
        g2.color = withAlpha(Color.WHITE, tier)
        g2.fill(body)
        g2.clip(body)
        g2.paint = GradientPaint(0f, 0f, Palette.glassSheen, 0f, h * 0.24f, withAlpha(Color.WHITE, 0f))
        g2.fill(Rectangle2D.Float(0f, 0f, w, h * 0.24f))
        g2.clip = null
        g2.paint = GradientPaint(0f, 0f, Palette.glassEdge, 0f, h, Palette.stroke)
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        g2.dispose()
    }
}

/**
 * Text field in the glass style: a black translucent fill with no resting outline. Focus shows an
 * accent ring.
 */
internal class ThemedField(initial: String = "") : JTextField(initial) {
    private val focus = Eased(0.3f) { repaint() }

    /** Shown in muted text while the field is empty. */
    var placeholder: String = ""

    /** Draws a magnifier at the left and reserves room for it. Use on search fields. */
    var searchGlyph: Boolean = false
        set(value) {
            field = value
            border = BorderFactory.createEmptyBorder(9, if (value) 40 else 12, 9, 12)
        }

    init {
        isOpaque = false
        font = Fonts.ui(14)
        foreground = Palette.text
        caretColor = Palette.text
        selectionColor = Palette.selection
        selectedTextColor = Palette.text
        border = BorderFactory.createEmptyBorder(9, 12, 9, 12)
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                focus.target = 1f
            }

            override fun focusLost(e: FocusEvent) {
                focus.target = 0f
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val arc = 10f
        paintBacking(g2, RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = withAlpha(Color.BLACK, 0.16f)
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        if (focus.current > 0f) {
            g2.color = withAlpha(Palette.focus, 0.18f * focus.current)
            g2.stroke = BasicStroke(3f)
            g2.draw(RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, arc, arc))
            g2.color = withAlpha(Palette.focus, focus.current)
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        }
        g2.dispose()
        super.paintComponent(g)
        if (text.isEmpty() && placeholder.isNotEmpty()) {
            val hint = g.smoothCopy()
            hint.font = font
            hint.color = Palette.muted
            val metrics = hint.fontMetrics
            hint.drawString(placeholder, insets.left.toFloat(), (height + metrics.ascent - metrics.descent) / 2f)
            hint.dispose()
        }
        if (searchGlyph) {
            val glyph = g.smoothCopy()
            glyph.color = mix(Palette.muted, Palette.text, focus.current)
            glyph.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val cx = 22f
            val cy = height / 2f - 1f
            glyph.draw(Ellipse2D.Float(cx - 5f, cy - 5f, 9f, 9f))
            glyph.draw(Line2D.Float(cx + 4.5f, cy + 4.5f, cx + 8f, cy + 8f))
            glyph.dispose()
        }
    }
}

/**
 * Button in IntelliJ style. [primary] uses the default-button blue; otherwise a neutral
 * raised button with a border that picks up [hoverTint] on hover.
 */
internal class FlatButton(
    label: String,
    private val primary: Boolean = false,
    private val hoverTint: Color = Palette.focus,
) : JButton(label) {
    private val hover = Eased(0.25f) { repaint() }
    private val press = Eased(0.45f) { repaint() }
    private var pressed = false

    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isRolloverEnabled = true
        font = Fonts.ui(13)
        foreground = if (primary) Color.WHITE else Palette.text
        border = BorderFactory.createEmptyBorder(7, 16, 7, 16)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hover.target = 1f
            }

            override fun mouseExited(e: MouseEvent) {
                hover.target = 0f
                setPressed(false)
            }

            override fun mousePressed(e: MouseEvent) {
                setPressed(true)
            }

            override fun mouseReleased(e: MouseEvent) {
                setPressed(false)
            }
        })
    }

    private fun setPressed(value: Boolean) {
        pressed = value
        press.target = if (value) 1f else 0f
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = 1f - PRESS_SCALE * press.current
        g2.translate((w * (1f - scale) / 2f).toDouble(), (h * (1f - scale) / 2f).toDouble())
        g2.scale(scale.toDouble(), scale.toDouble())
        val arc = 6f
        val shape = RoundRectangle2D.Float(0f, 0f, w, h, arc, arc)
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, if (isEnabled) 1f else 0.4f)
        val base = if (primary) {
            if (pressed) Palette.primaryPressed else mix(Palette.primary, Palette.primaryHover, hover.current)
        } else {
            mix(Palette.surfaceRaised, Palette.surfaceHigh, hover.current)
        }
        // Lit from above: a lighter top fading into the base color, like a glossy glass key.
        g2.paint = GradientPaint(0f, 0f, mix(base, Color.WHITE, 0.10f), 0f, h, mix(base, Color.BLACK, 0.05f))
        g2.fill(shape)
        g2.color = if (primary) {
            withAlpha(Color.WHITE, 0.16f)
        } else {
            mix(Palette.stroke, hoverTint, hover.current)
        }
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        super.paintComponent(g2)
        g2.dispose()
    }

    private companion object {
        const val PRESS_SCALE = 0.03f
    }
}

/** Progress bar with an eased solid fill, an indeterminate sweep and centered status text. */
internal class ThemedProgressBar : JProgressBar(0, 100) {
    private var shown = 0f
    private var sweep = 0f
    private val ticker = Timer(16) { onTick() }

    init {
        isStringPainted = true
        isOpaque = false
        preferredSize = Dimension(200, 26)
        font = Fonts.ui(12)
        foreground = Color.WHITE
        ticker.start()
    }

    private fun onTick() {
        val target = if (isIndeterminate) 0f else (value - minimum).toFloat() / (maximum - minimum).coerceAtLeast(1)
        val before = shown
        shown += (target - shown) * 0.14f
        if (abs(target - shown) < 0.0005f) shown = target
        sweep = (sweep + 0.011f) % 1f
        if (isIndeterminate || shown != before) repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val arc = 4f
        val track = RoundRectangle2D.Float(0f, 0f, w, h, arc, arc)
        g2.color = Palette.field
        g2.fill(track)
        g2.color = Palette.stroke
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))

        g2.clip(track)
        g2.color = Palette.accent
        if (isIndeterminate) {
            val segment = w * 0.25f
            val x = -segment + (w + segment) * sweep
            g2.fill(Rectangle2D.Float(x, 0f, segment, h))
        } else if (shown > 0f) {
            g2.fill(Rectangle2D.Float(0f, 0f, max(w * shown, 1f), h))
        }
        g2.clip = null

        val text = string
        if (!text.isNullOrEmpty()) {
            g2.font = font
            val metrics = g2.fontMetrics
            val x = (w - metrics.stringWidth(text)) / 2f
            val y = (h - metrics.height) / 2f + metrics.ascent
            g2.color = Color(0, 0, 0, 110)
            g2.drawString(text, x, y + 1f)
            g2.color = foreground
            g2.drawString(text, x, y)
        }
        g2.dispose()
    }
}

/** The app icon, bundled as `app-icon.png`. Used in the header and as the window icon (taskbar). */
internal object AppIcon {
    /** Icon sizes for the window. Java picks the closest one for the taskbar and title bar. */
    val images: List<BufferedImage> by lazy {
        listOfNotNull(
            loadResource("/app-icon.png"),
        )
    }

    private fun loadResource(path: String): BufferedImage? =
        AppIcon::class.java.getResourceAsStream(path)?.use { ImageIO.read(it) }
}

/** Header mark: the app icon drawn at the component's size with smooth scaling. */
internal class LogoMark : JComponent() {
    init {
        isOpaque = false
        preferredSize = Dimension(44, 44)
    }

    override fun paintComponent(g: Graphics) {
        val image = AppIcon.images.firstOrNull() ?: return
        val g2 = g.smoothCopy()
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val s = minOf(width, height)
        g2.drawImage(image, 0, 0, s, s, null)
        g2.dispose()
    }
}


/** Output RMS is usually 0.1 to 0.3, so the playing-row pulse scales it up to use the full range. */
private const val PLAYING_LEVEL_GAIN = 3f

/**
 * Table whose hover and selection highlights are rounded pills that glide between rows.
 * Cells must be transparent (isOpaque = false) so the highlights show through.
 */
internal class SmoothTable(model: TableModel) : JTable(model) {
    private val hoverY = Eased(0.32f) { repaint() }
    private val hoverAlpha = Eased(0.18f) { repaint() }
    private val selectY = Eased(0.32f) { repaint() }
    private val selectAlpha = Eased(0.18f) { repaint() }
    private var hoverRow = -1
    private val playingAlpha = Eased(0.25f) { repaint() }
    private val playingPulse = Eased(0.12f) { repaint() }
    private var playingId: String? = null

    /** Maps a track id to its row, or -1 when the row is not shown. Set by the panel that owns the model. */
    var rowOfTrack: (String) -> Int = { -1 }

    /**
     * Marks [trackId] as the playing row. [level] (0 to 1) drives a soft pulse. Pass null when nothing
     * is playing or the track is not in this table. Selection is drawn separately.
     */
    fun setPlaying(trackId: String?, level: Float) {
        playingId = trackId
        playingAlpha.target = if (trackId != null) 1f else 0f
        playingPulse.target = (level * PLAYING_LEVEL_GAIN).coerceIn(0f, 1f)
    }

    init {
        isOpaque = false
        selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) moveHighlight(selectedRow, selectY, selectAlpha)
        }
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val row = rowAtPoint(e.point)
                if (row != hoverRow) {
                    hoverRow = row
                    moveHighlight(row, hoverY, hoverAlpha)
                }
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                hoverRow = -1
                moveHighlight(-1, hoverY, hoverAlpha)
            }
        })
    }

    private fun moveHighlight(row: Int, position: Eased, alpha: Eased) {
        if (row < 0) {
            alpha.target = 0f
            return
        }
        val y = row * rowHeight.toFloat()
        // Fade in at the new row without sliding in from wherever the highlight was last.
        if (alpha.current < 0.02f) position.snapTo(y) else position.target = y
        alpha.target = 1f
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val inset = 4f
        val pillHeight = rowHeight - 4f
        val pillWidth = width - inset * 2f
        if (hoverAlpha.current > 0f) {
            val pill = RoundRectangle2D.Float(inset, hoverY.current + 2f, pillWidth, pillHeight, 8f, 8f)
            g2.color = withAlpha(Palette.text, 0.07f * hoverAlpha.current)
            g2.fill(pill)
            g2.color = withAlpha(Palette.glassEdge, 0.20f * hoverAlpha.current)
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(inset + 0.5f, hoverY.current + 2.5f, pillWidth - 1f, pillHeight - 1f, 8f, 8f))
        }
        val playingRow = playingId?.let(rowOfTrack) ?: -1
        if (playingAlpha.current > 0.01f && playingRow >= 0) {
            val top = playingRow * rowHeight + 2f
            val fade = playingAlpha.current
            // The pulse lifts the halo and fill; the left bar stays put so the row reads as "playing" while paused.
            val pulse = 0.25f + 0.75f * playingPulse.current
            val strength = fade * pulse
            for (layer in 3 downTo 1) {
                g2.color = withAlpha(Palette.focus, 0.20f * strength / layer)
                g2.stroke = BasicStroke(2f * layer)
                g2.draw(RoundRectangle2D.Float(inset, top, pillWidth, pillHeight, 10f, 10f))
            }
            g2.color = withAlpha(Palette.focus, 0.16f * strength)
            g2.fill(RoundRectangle2D.Float(inset, top, pillWidth, pillHeight, 8f, 8f))
            g2.color = withAlpha(Palette.focus, 0.85f * fade)
            g2.fill(RoundRectangle2D.Float(inset + 2f, top + 7f, 3f, pillHeight - 14f, 2f, 2f))
        }
        if (selectAlpha.current > 0f) {
            val top = selectY.current + 2f
            val pill = RoundRectangle2D.Float(inset, top, pillWidth, pillHeight, 8f, 8f)
            // Soft glow first, then the accent fill, so the selected row lifts off the glass.
            g2.color = withAlpha(Palette.focus, 0.18f * selectAlpha.current)
            g2.stroke = BasicStroke(4f)
            g2.draw(RoundRectangle2D.Float(inset - 1f, top - 1f, pillWidth + 2f, pillHeight + 2f, 10f, 10f))
            g2.paint = GradientPaint(
                0f, top, withAlpha(mix(Palette.accent, Color.WHITE, 0.10f), selectAlpha.current),
                0f, top + pillHeight, withAlpha(Palette.accent, selectAlpha.current),
            )
            g2.fill(pill)
        }
        g2.dispose()
        super.paintComponent(g)
    }
}

/**
 * Two or more options in a dark rounded track. The accent pill glides to the chosen segment.
 * [onChange] runs only for clicks, not for [selectedIndex] assignments.
 */
internal class SegmentedControl(
    private val labels: List<String>,
    private val onChange: (Int) -> Unit,
) : JComponent() {
    private val position = Eased(0.2f) { repaint() }

    var selectedIndex: Int = 0
        set(value) {
            if (value == field) return
            field = value
            position.target = value.toFloat()
        }

    init {
        isOpaque = false
        font = Fonts.ui(12)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(160, 30)
        maximumSize = Dimension(Int.MAX_VALUE, 30)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = (e.x * labels.size / width.coerceAtLeast(1)).coerceIn(0, labels.size - 1)
                if (index != selectedIndex) {
                    selectedIndex = index
                    onChange(index)
                }
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val segment = width / labels.size.toFloat()
        val pad = 3f
        paintBacking(g2, RoundRectangle2D.Float(0f, 0f, width - 1f, height - 1f, 10f, 10f))
        g2.color = withAlpha(Color.BLACK, 0.16f)
        g2.fill(RoundRectangle2D.Float(0f, 0f, width - 1f, height - 1f, 10f, 10f))
        g2.color = Palette.primary
        g2.fill(RoundRectangle2D.Float(
            position.current * segment + pad,
            pad,
            segment - pad * 2f,
            height - pad * 2f - 1f,
            8f,
            8f,
        ))
        g2.font = font
        val metrics = g2.fontMetrics
        labels.forEachIndexed { index, label ->
            val closeness = (1f - kotlin.math.abs(position.current - index)).coerceIn(0f, 1f)
            g2.color = mix(Palette.muted, Palette.text, closeness)
            val x = index * segment + (segment - metrics.stringWidth(label)) / 2f
            g2.drawString(label, x, (height + metrics.ascent - metrics.descent) / 2f)
        }
        g2.dispose()
    }
}

/** Dropdown with a flat rounded field, a drawn chevron, and a glass popup list. */
internal class ThemedComboBox<T>(items: Array<T>) : JComboBox<T>(items) {
    init {
        isOpaque = false
        font = Fonts.ui(13)
        foreground = Palette.text
        border = BorderFactory.createEmptyBorder(6, 12, 6, 6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        renderer = ThemedCellRenderer()
        addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) = repaint()
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = repaint()
            override fun popupMenuCanceled(e: PopupMenuEvent) = repaint()
        })
    }

    override fun updateUI() {
        setUI(ThemedComboUI())
    }
}

private class ThemedCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        val inPopup = index >= 0
        font = Fonts.ui(13)
        border = BorderFactory.createEmptyBorder(7, 12, 7, 12)
        isOpaque = inPopup
        background = if (isSelected) mix(Palette.surface, Palette.accent, 0.55f) else Palette.surface
        foreground = if (isSelected) Color.WHITE else Palette.text
        return this
    }
}

private class ThemedComboUI : BasicComboBoxUI() {
    override fun createArrowButton(): JButton = object : JButton() {
        init {
            isOpaque = false
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            preferredSize = Dimension(22, 22)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.smoothCopy()
            val cx = width / 2f
            val cy = height / 2f
            g2.color = if (comboBox?.isPopupVisible == true) Palette.focus else Palette.muted
            g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.draw(java.awt.geom.Line2D.Float(cx - 4f, cy - 1.5f, cx, cy + 2.5f))
            g2.draw(java.awt.geom.Line2D.Float(cx, cy + 2.5f, cx + 4f, cy - 1.5f))
            g2.dispose()
        }
    }

    override fun paint(g: Graphics, c: JComponent) {
        val g2 = g.smoothCopy()
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val arc = 10f
        paintBacking(g2, RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = withAlpha(Color.BLACK, 0.16f)
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        if (comboBox.isPopupVisible) {
            g2.color = withAlpha(Palette.focus, 0.18f)
            g2.stroke = BasicStroke(3f)
            g2.draw(RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, arc, arc))
            g2.color = withAlpha(Palette.focus, 1f)
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        }
        g2.dispose()
        super.paint(g, c)
    }

    override fun paintCurrentValueBackground(g: Graphics, bounds: Rectangle, hasFocus: Boolean) {}

    /** Draws the selected value here rather than through the default renderer, so it always uses [Palette.text] and smooth text. */
    override fun paintCurrentValue(g: Graphics, bounds: Rectangle, hasFocus: Boolean) {
        val value = comboBox.selectedItem ?: return
        val g2 = g.smoothCopy()
        g2.font = Fonts.ui(13)
        g2.color = Palette.text
        val metrics = g2.fontMetrics
        val y = bounds.y + (bounds.height - metrics.height) / 2f + metrics.ascent
        g2.drawString(value.toString(), bounds.x.toFloat(), y)
        g2.dispose()
    }

    override fun createPopup(): ComboPopup = object : BasicComboPopup(comboBox) {
        init {
            background = Palette.surface
            border = BorderFactory.createLineBorder(Palette.stroke)
            list.background = Palette.surface
        }
    }
}

/**
 * Colored rounded chip with a centered initial. The color comes from [seed] (the song title), so
 * each song keeps the same color across launches.
 */
internal fun paintChip(g2: Graphics2D, x: Float, y: Float, size: Float, initial: String, seed: String) {
    val color = chipColorFor(seed)
    g2.paint = GradientPaint(x, y, mix(color, Color.WHITE, 0.18f), x, y + size, color)
    g2.fill(RoundRectangle2D.Float(x, y, size, size, size * 0.36f, size * 0.36f))
    g2.font = Fonts.ui((size * 0.46f).roundToInt(), Font.BOLD)
    g2.color = Color.WHITE
    val metrics = g2.fontMetrics
    g2.drawString(initial, x + (size - metrics.stringWidth(initial)) / 2f, y + (size + metrics.ascent - metrics.descent) / 2f)
}

internal fun chipColorFor(seed: String): Color {
    val hue = (seed.lowercase().hashCode() and 0x7fffffff) % 360 / 360f
    return Color.getHSBColor(hue, 0.52f, 0.66f)
}

/** Glass popup for right-click menus. Paints its own rounded body and hairline edge. */
internal class ThemedPopupMenu : JPopupMenu() {
    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val arc = 12f
        g2.color = mix(Palette.base, Color.WHITE, Palette.GLASS_RAISED)
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = Palette.stroke
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        g2.dispose()
        super.paintComponent(g)
    }
}

/** Menu row with a rounded highlight when armed. [tint] colors the highlight; use [Palette.danger] for destructive actions. */
internal class ThemedMenuItem(label: String, private val tint: Color = Palette.accent) : JMenuItem(label) {
    init {
        isOpaque = false
        font = Fonts.ui(13)
        foreground = Palette.text
        border = BorderFactory.createEmptyBorder(8, 14, 8, 14)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        model.addChangeListener { repaint() }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        if (model.isArmed && isEnabled) {
            g2.color = withAlpha(tint, 0.38f)
            g2.fill(RoundRectangle2D.Float(0f, 1f, w, h - 2f, 8f, 8f))
        }
        g2.font = font
        g2.color = if (isEnabled) foreground else Palette.muted
        val metrics = g2.fontMetrics
        g2.drawString(text, insets.left.toFloat(), (h + metrics.ascent - metrics.descent) / 2f)
        g2.dispose()
    }
}

/** Hairline divider between groups of [ThemedMenuItem]s. */
internal class ThemedMenuSeparator : JComponent() {
    init {
        isOpaque = false
        preferredSize = Dimension(0, 9)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val y = height / 2f + 0.5f
        g2.color = withAlpha(Palette.glassEdge, 0.5f)
        g2.stroke = BasicStroke(1f)
        g2.draw(Line2D.Float(10f, y, width - 10f, y))
        g2.dispose()
    }
}

/** Thin rounded scrollbar without arrow buttons. */
internal class SlimScrollBarUI : BasicScrollBarUI() {
    override fun createDecreaseButton(orientation: Int): JButton = zeroSizeButton()
    override fun createIncreaseButton(orientation: Int): JButton = zeroSizeButton()

    override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {}

    override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
        val g2 = g.smoothCopy()
        g2.color = withAlpha(Palette.muted, 0.45f)
        g2.fill(
            RoundRectangle2D.Float(
                thumbBounds.x + 2f,
                thumbBounds.y + 2f,
                thumbBounds.width - 4f,
                thumbBounds.height - 4f,
                6f,
                6f,
            ),
        )
        g2.dispose()
    }

    private fun zeroSizeButton(): JButton = JButton().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        maximumSize = Dimension(0, 0)
    }
}
