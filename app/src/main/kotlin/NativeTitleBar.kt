package org.catosaur.app

import java.awt.Color
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

// Colors the native Windows title bar through the Desktop Window Manager.
// Windows 10 2004+ and Windows 11 honor dark mode. Windows 11 (build 22000+) also honors
// the caption and text colors. Unsupported attributes fail silently, and other platforms
// are left untouched.
internal object NativeTitleBar {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    fun apply(windowTitle: String, caption: Color, text: Color) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        try {
            Arena.ofConfined().use { arena ->
                val linker = Linker.nativeLinker()
                val user32 = SymbolLookup.libraryLookup("user32.dll", arena)
                val dwmapi = SymbolLookup.libraryLookup("dwmapi.dll", arena)
                val findWindow = linker.downcallHandle(
                    user32.find("FindWindowW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
                val setAttribute = linker.downcallHandle(
                    dwmapi.find("DwmSetWindowAttribute").orElseThrow(),
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                    ),
                )

                val hwnd = findWindow.invokeWithArguments(MemorySegment.NULL, wideString(arena, windowTitle)) as MemorySegment
                if (hwnd.address() == 0L) return

                fun setInt(attribute: Int, value: Int): Int {
                    val cell = arena.allocate(4L, 4L)
                    cell.set(ValueLayout.JAVA_INT, 0L, value)
                    return setAttribute.invokeWithArguments(hwnd, attribute, cell, 4) as Int
                }

                if (setInt(DWMWA_USE_IMMERSIVE_DARK_MODE, 1) != 0) {
                    setInt(DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, 1)
                }
                setInt(DWMWA_CAPTION_COLOR, colorRef(caption))
                setInt(DWMWA_TEXT_COLOR, colorRef(text))
            }
        } catch (e: Throwable) {
            System.err.println("Native title bar theming skipped: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // COLORREF is 0x00BBGGRR.
    private fun colorRef(color: Color): Int =
        (color.blue shl 16) or (color.green shl 8) or color.red

    private fun wideString(arena: Arena, text: String): MemorySegment {
        val segment = arena.allocate(((text.length + 1) * 2).toLong(), 2L)
        text.forEachIndexed { index, char ->
            segment.setAtIndex(ValueLayout.JAVA_CHAR, index.toLong(), char)
        }
        segment.setAtIndex(ValueLayout.JAVA_CHAR, text.length.toLong(), '\u0000')
        return segment
    }
}
