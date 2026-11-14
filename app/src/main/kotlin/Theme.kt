package org.catosaur.app

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.LayoutManager
import java.awt.BorderLayout
import java.awt.RadialGradientPaint
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
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.plaf.basic.BasicScrollBarUI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal object Palette {
    val base = Color(11, 13, 19)
    val glowViolet = Color(124, 92, 255)
    val glowCyan = Color(34, 211, 238)
    val surface = Color(20, 23, 32)
    val surfaceRaised = Color(31, 35, 48)
    val surfaceHigh = Color(42, 47, 66)
    val field = Color(14, 17, 25)
    val stroke = Color(42, 47, 62)
    val text = Color(236, 239, 245)
    val muted = Color(142, 150, 170)
    val brandA = Color(99, 102, 241)
    val brandB = Color(168, 85, 247)
    val cyan = Color(34, 211, 238)
    val danger = Color(248, 113, 113)
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

    private val timer: Timer = Timer(16) {
        current += (target - current) * speed
        if (abs(target - current) < 0.002f) {
            current = target
            timer.stop()
        }
        onStep()
    }
}

/** Backdrop with soft colored glows in two corners. */
internal class Backdrop : JPanel(BorderLayout()) {
    init {
        isOpaque = true
        background = Palette.base
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        g2.color = Palette.base
        g2.fillRect(0, 0, width, height)
        val diagonal = max(width, height).toFloat()
        drawGlow(g2, width * 0.95f, -height * 0.05f, diagonal * 0.6f, Palette.glowViolet, 0.20f)
        drawGlow(g2, -width * 0.05f, height * 1.05f, diagonal * 0.6f, Palette.glowCyan, 0.12f)
        g2.dispose()
    }

    private fun drawGlow(g2: Graphics2D, cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
        if (radius <= 0f) return
        g2.paint = RadialGradientPaint(
            cx, cy, radius,
            floatArrayOf(0f, 1f),
            arrayOf(withAlpha(color, alpha), withAlpha(color, 0f)),
        )
        g2.fillRect(0, 0, width, height)
    }
}

/** Rounded surface with a faint top-to-bottom sheen and a hairline border. */
internal class Card(layout: LayoutManager = BorderLayout(), private val arc: Float = 36f) : JPanel(layout) {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        g2.paint = GradientPaint(0f, 0f, mix(Palette.surface, Color.WHITE, 0.025f), 0f, h, Palette.surface)
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = Palette.stroke
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        g2.dispose()
    }
}

/** Text field with a rounded body and an accent border on focus. */
internal class GlowField(initial: String = "") : JTextField(initial) {
    private val focus = Eased(0.25f) { repaint() }

    init {
        isOpaque = false
        font = Fonts.ui(14)
        foreground = Palette.text
        caretColor = Palette.text
        selectionColor = withAlpha(Palette.brandA, 0.45f)
        selectedTextColor = Palette.text
        border = BorderFactory.createEmptyBorder(11, 14, 11, 14)
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
        val glow = focus.current
        val w = width.toFloat()
        val h = height.toFloat()
        val arc = 14f
        g2.color = Palette.field
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = mix(Palette.field, Palette.brandA, 0.06f * glow)
        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
        g2.color = mix(Palette.stroke, Palette.brandA, glow)
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc))
        g2.dispose()
        super.paintComponent(g)
    }
}

/** Pill button. [primary] draws the brand gradient; otherwise a raised ghost style. */
internal class PillButton(
    label: String,
    private val primary: Boolean = false,
    private val hoverTint: Color = Palette.brandA,
) : JButton(label) {
    private val hover = Eased(0.22f) { repaint() }
    private var pressed = false

    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isRolloverEnabled = true
        font = Fonts.ui(13, Font.BOLD)
        foreground = if (primary) Color.WHITE else Palette.text
        border = BorderFactory.createEmptyBorder(10, 22, 10, 22)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hover.target = 1f
            }

            override fun mouseExited(e: MouseEvent) {
                hover.target = 0f
                pressed = false
                repaint()
            }

            override fun mousePressed(e: MouseEvent) {
                pressed = true
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                pressed = false
                repaint()
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val w = width.toFloat()
        val h = height.toFloat()
        val pill = RoundRectangle2D.Float(0f, 0f, w, h, h, h)
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, if (isEnabled) 1f else 0.38f)
        if (primary) {
            val lift = hover.current * 0.12f
            g2.paint = GradientPaint(
                0f, 0f, mix(Palette.brandA, Color.WHITE, lift),
                w, 0f, mix(Palette.brandB, Color.WHITE, lift),
            )
            g2.fill(pill)
            if (pressed) {
                g2.color = Color(0, 0, 0, 70)
                g2.fill(pill)
            }
        } else {
            g2.color = mix(Palette.surfaceRaised, Palette.surfaceHigh, hover.current)
            g2.fill(pill)
            g2.color = mix(Palette.stroke, hoverTint, hover.current)
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h))
        }
        g2.dispose()
        super.paintComponent(g)
    }
}

/** Rounded progress bar with an eased fill, an indeterminate sweep and centered status text. */
internal class GlowProgressBar : JProgressBar(0, 100) {
    private var shown = 0f
    private var sweep = 0f
    private val ticker = Timer(16) { onTick() }

    init {
        isStringPainted = true
        isOpaque = false
        preferredSize = Dimension(200, 28)
        font = Fonts.ui(12, Font.BOLD)
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
        val track = RoundRectangle2D.Float(0f, 0f, w, h, h, h)
        g2.color = Palette.field
        g2.fill(track)
        g2.color = Palette.stroke
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h))

        g2.clip(track)
        if (isIndeterminate) {
            val segment = w * 0.32f
            val x = -segment + (w + segment) * sweep
            g2.paint = GradientPaint(x, 0f, Palette.brandA, x + segment, 0f, Palette.cyan)
            g2.fill(Rectangle2D.Float(x, 0f, segment, h))
        } else if (shown > 0f) {
            val fill = max(w * shown, h)
            g2.paint = GradientPaint(0f, 0f, Palette.brandA, w, 0f, Palette.cyan)
            g2.fill(RoundRectangle2D.Float(0f, 0f, fill, h, h, h))
        }
        g2.clip = null

        val text = string
        if (!text.isNullOrEmpty()) {
            g2.font = font
            val metrics = g2.fontMetrics
            val x = (w - metrics.stringWidth(text)) / 2f
            val y = (h - metrics.height) / 2f + metrics.ascent
            g2.color = Color(0, 0, 0, 120)
            g2.drawString(text, x, y + 1f)
            g2.color = foreground
            g2.drawString(text, x, y)
        }
        g2.dispose()
    }
}

/** Small gradient mark used in the header. */
internal class LogoMark : JComponent() {
    init {
        isOpaque = false
        preferredSize = Dimension(48, 48)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smoothCopy()
        val s = minOf(width, height).toFloat()
        val radius = s * 0.32f
        g2.paint = GradientPaint(0f, 0f, Palette.brandA, s, s, Palette.brandB)
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
        g2.color = withAlpha(Palette.muted, 0.35f)
        g2.fill(
            RoundRectangle2D.Float(
                thumbBounds.x + 2f,
                thumbBounds.y + 2f,
                thumbBounds.width - 4f,
                thumbBounds.height - 4f,
                8f,
                8f,
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
