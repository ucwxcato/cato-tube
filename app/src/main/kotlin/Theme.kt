package org.catosaur.app

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.plaf.basic.BasicScrollBarUI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Colors follow the IntelliJ IDEA Darcula theme.
internal object Palette {
    val base = Color(43, 43, 43)            // editor background, window body
    val surface = Color(60, 63, 65)         // tool window panels, cards
    val surfaceRaised = Color(76, 80, 82)   // default button
    val surfaceHigh = Color(89, 91, 93)     // hovered default button
    val field = Color(69, 73, 74)           // text fields, progress track
    val stroke = Color(81, 81, 81)          // borders and separators
    val text = Color(187, 187, 187)         // primary UI text
    val muted = Color(128, 128, 128)        // secondary labels
    val accent = Color(75, 110, 175)        // selection and progress fill
    val focus = Color(53, 146, 196)         // focused field outline
    val primary = Color(54, 88, 128)        // default (primary) button
    val primaryHover = Color(61, 97, 133)
    val primaryPressed = Color(44, 72, 105)
    val selection = Color(33, 66, 131)      // text selection
    val danger = Color(188, 63, 60)
}

internal object Fonts {
    private val families: Set<String> by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    }
    private fun pick(vararg names: String, fallback: String): String =
        names.firstOrNull { it in families } ?: fallback

    private val uiFamily: String by lazy { pick("Segoe UI Variable Text", "Segoe UI", fallback = Font.SANS_SERIF) }
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

internal fun sectionLabel(text: String): JLabel = JLabel(text.uppercase()).apply {
    font = Fonts.ui(11, Font.BOLD)
    foreground = Palette.muted
}

/** Flat window body in the editor background color. */
internal class Backdrop : JPanel(BorderLayout()) {
    init {
        isOpaque = true
        background = Palette.base
    }
}

/** Flat panel with a hairline border, like an IntelliJ tool window. */
internal class Card(layout: LayoutManager = BorderLayout(), private val arc: Float = 8f) : JPanel(layout) {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        g2.color = Palette.surface
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = Palette.stroke
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        g2.dispose()
    }
}

/** Text field with a square-ish body and a blue focus outline, like IntelliJ inputs. */
internal class ThemedField(initial: String = "") : JTextField(initial) {
    private val focus = Eased(0.3f) { repaint() }

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
        val arc = 4f
        g2.color = Palette.field
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = mix(Palette.stroke, Palette.focus, focus.current)
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        g2.dispose()
        super.paintComponent(g)
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
        g2.translate(w * (1f - scale) / 2f, h * (1f - scale) / 2f)
        g2.scale(scale.toDouble(), scale.toDouble())
        val arc = 6f
        val shape = RoundRectangle2D.Float(0f, 0f, w, h, arc, arc)
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, if (isEnabled) 1f else 0.4f)
        if (primary) {
            g2.color = when {
                pressed -> Palette.primaryPressed
                else -> mix(Palette.primary, Palette.primaryHover, hover.current)
            }
            g2.fill(shape)
        } else {
            g2.color = mix(Palette.surfaceRaised, Palette.surfaceHigh, hover.current)
            g2.fill(shape)
            g2.color = mix(Palette.stroke, hoverTint, hover.current)
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        }
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

/** Square app mark for the header, in the default accent blue. */
internal class LogoMark : JComponent() {
    init {
        isOpaque = false
        preferredSize = Dimension(44, 44)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val s = minOf(width, height).toFloat()
        val radius = 6f
        g2.color = Palette.primary
        g2.fill(RoundRectangle2D.Float(0f, 0f, s, s, radius, radius))
        val glyph = "C"
        g2.font = Fonts.ui(22, Font.BOLD)
        val metrics = g2.fontMetrics
        g2.color = Color.WHITE
        g2.drawString(glyph, (s - metrics.stringWidth(glyph)) / 2f, (s - metrics.height) / 2f + metrics.ascent)
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
