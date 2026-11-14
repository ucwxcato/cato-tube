package org.catosaur.app

import org.catosaur.app.download.DEFAULT_DOWNLOAD_DIRECTORY
import org.catosaur.app.download.isYouTubeUrl
import org.catosaur.app.download.ytDlpDownloadCommand
import org.catosaur.app.tools.ToolProbe
import org.catosaur.app.tools.ToolStatus
import org.catosaur.app.ui.showMainWindow
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() {
    // This machine's IPv6 route hangs (thumbnail loads timed out). Prefer IPv4 for every JVM socket,
    // the same way yt-dlp is run with --force-ipv4. Must be set before any network class loads.
    System.setProperty("java.net.preferIPv4Stack", "true")
    if (System.console() == null) {
        System.setProperty("awt.useSystemAAFontSettings", "on")
        System.setProperty("swing.aatext", "true")
        SwingUtilities.invokeLater { showWindow() }
        return
    }

    runCli()
}

private fun runCli() {
    println("YouTube audio downloader")
    println("Only download audio you own or are authorized to use.\n")
    printToolStatus()

    print("YouTube URL: ")
    val url = readlnOrNull()?.trim().orEmpty()
    if (!isYouTubeUrl(url)) {
        System.err.println("Please enter a valid youtube.com or youtu.be URL.")
        return
    }

    try {
        val process = ytDlpDownloadCommand(DEFAULT_DOWNLOAD_DIRECTORY, url).inheritIO().start()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            println("\nFinished. MP3 saved in: $DEFAULT_DOWNLOAD_DIRECTORY")
        } else {
            System.err.println("\nyt-dlp exited with code $exitCode. Check that yt-dlp and FFmpeg are installed and on PATH.")
        }
    } catch (_: java.io.IOException) {
        System.err.println("Could not start yt-dlp. Install yt-dlp and FFmpeg, then make sure both are on PATH.")
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        System.err.println("Download interrupted.")
    }
}

private fun printToolStatus() {
    println("External tools:")
    val checks = ToolProbe.checkAll()
    for (check in checks) {
        val state = when (check.status) {
            ToolStatus.Found -> "found (${check.displayVersion})"
            ToolStatus.Missing -> "not found"
            ToolStatus.Failed -> "did not run (${check.detail})"
        }
        println("  ${check.tool.label.padEnd(8)} $state")
    }
    val missing = checks.filter { !it.ok }
    if (missing.isNotEmpty()) {
        println()
        println("Install the missing tools, then reopen CatoTube:")
        missing.map { it.tool.install }.distinct().forEach { println("  $it") }
    }
    println()
}

private fun showWindow() {
    UIManager.put("Panel.background", Palette.base)
    UIManager.put("OptionPane.background", Palette.surface)
    UIManager.put("OptionPane.messageForeground", Palette.text)
    UIManager.put("Label.foreground", Palette.text)
    UIManager.put("Label.font", Fonts.ui(13))
    UIManager.put("Button.background", Palette.surfaceRaised)
    UIManager.put("Button.foreground", Palette.text)
    UIManager.put("Button.font", Fonts.ui(13))
    UIManager.put("ScrollBar.width", 10)

    showMainWindow()
}
