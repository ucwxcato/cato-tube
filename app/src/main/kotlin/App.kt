package org.catosaur.app

import java.net.URI
import java.nio.file.Path
import java.nio.file.Files
import javax.swing.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Color
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private var outputDirectory = Path.of("downloads").toAbsolutePath()

fun main() {
    if (System.console() == null) {
        SwingUtilities.invokeLater { showWindow() }
        return
    }

    runCli()
}

private fun runCli() {
    println("YouTube audio downloader for RoboBeat")
    println("Only download audio you own or are authorized to use.\n")

    print("YouTube URL: ")
    val url = readlnOrNull()?.trim().orEmpty()
    if (!isYouTubeUrl(url)) {
        System.err.println("Please enter a valid youtube.com or youtu.be URL.")
        return
    }

    try {
        val process = startDownload(url).inheritIO().start()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            println("\nFinished. MP3 saved in: $outputDirectory")
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

private fun showWindow() {
    val dark = Color(35, 37, 43)
    val darker = Color(26, 28, 33)
    val textColor = Color(232, 234, 238)
    val accent = Color(78, 130, 220)
    UIManager.put("Panel.background", dark)
    UIManager.put("OptionPane.background", dark)
    UIManager.put("OptionPane.messageForeground", textColor)
    UIManager.put("Label.foreground", textColor)
    UIManager.put("TextField.background", darker)
    UIManager.put("TextField.foreground", textColor)
    UIManager.put("TextField.caretForeground", textColor)
    UIManager.put("TextArea.background", darker)
    UIManager.put("TextArea.foreground", textColor)
    UIManager.put("TextArea.caretForeground", textColor)
    UIManager.put("ScrollPane.background", darker)
    UIManager.put("Button.background", Color(55, 59, 68))
    UIManager.put("Button.foreground", textColor)
    UIManager.put("ProgressBar.background", darker)
    UIManager.put("ProgressBar.foreground", accent)

    val frame = JFrame("CatoTube for RoboBeat").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        background = dark
        minimumSize = Dimension(700, 420)
        setSize(780, 480)
        setLocationRelativeTo(null)
    }

    val urlField = JTextField()
    val folderField = JTextField(outputDirectory.toString()).apply { isEditable = false }
    val log = JTextArea("Only download audio you own or are authorized to use.\n")
        .apply { isEditable = false; lineWrap = true; wrapStyleWord = true; background = darker; foreground = textColor }
    val logScroll = JScrollPane(log)
    val downloadButton = JButton("Download MP3").apply { isOpaque = true; background = accent; foreground = Color.WHITE }
    val folderButton = JButton("Browse")
    val progress = JProgressBar(0, 100).apply {
        isStringPainted = true
        string = "Ready"
    }
    val activeProcess = AtomicReference<Process?>(null)
    val cancelButton = JButton("Cancel").apply { isEnabled = false }
    val inputs = JPanel(java.awt.GridBagLayout()).apply {
        border = BorderFactory.createEmptyBorder(12, 12, 8, 12)
        val c = java.awt.GridBagConstraints().apply {
            fill = java.awt.GridBagConstraints.HORIZONTAL
            insets = java.awt.Insets(3, 4, 3, 4)
        }
        c.gridx = 0; c.gridy = 0; c.weightx = 0.0; c.gridheight = 1
        add(JLabel("YouTube URL:"), c)
        c.gridx = 1; c.weightx = 1.0
        add(urlField, c)
        c.gridx = 2; c.weightx = 0.0
        add(downloadButton, c)
        c.gridx = 0; c.gridy = 1; c.gridheight = 1
        add(JLabel("Save to:"), c)
        c.gridx = 1; c.weightx = 1.0
        add(folderField, c)
        c.gridx = 2; c.weightx = 0.0
        add(folderButton, c)
    }
    frame.layout = BorderLayout()
    frame.add(inputs, BorderLayout.NORTH)
    frame.add(logScroll, BorderLayout.CENTER)
    frame.add(JPanel(BorderLayout(8, 0)).apply {
        border = BorderFactory.createEmptyBorder(8, 12, 12, 12)
        add(progress, BorderLayout.CENTER)
        add(cancelButton, BorderLayout.EAST)
    }, BorderLayout.SOUTH)

    cancelButton.addActionListener {
        activeProcess.get()?.let { process ->
            log.append("\nCancel requested…\n")
            process.destroy()
            cancelButton.isEnabled = false
        }
    }

    folderButton.addActionListener {
        val chooser = JFileChooser(outputDirectory.toFile()).apply {
            dialogTitle = "Choose MP3 save folder"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            outputDirectory = chooser.selectedFile.toPath().toAbsolutePath()
            folderField.text = outputDirectory.toString()
        }
    }

    downloadButton.addActionListener {
        val url = urlField.text.trim()
        if (!isYouTubeUrl(url)) {
            JOptionPane.showMessageDialog(frame, "Enter a valid youtube.com or youtu.be URL.", "Invalid URL", JOptionPane.ERROR_MESSAGE)
            return@addActionListener
        }
        downloadButton.isEnabled = false
        progress.isIndeterminate = true
        progress.string = "Connecting…"
        cancelButton.isEnabled = true
        progress.string = "Starting…"
        log.append("\nStarting download...\n")
        Thread {
            try {
                val process = startDownload(url).redirectErrorStream(true).start()
                activeProcess.set(process)
                val lastOutputAt = AtomicLong(System.nanoTime())
                val timedOut = AtomicBoolean(false)
                val watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
                    Thread(task, "download-watchdog").apply { isDaemon = true }
                }
                watchdog.scheduleAtFixedRate({
                    val idleSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastOutputAt.get())
                    if (process.isAlive && idleSeconds >= 15 && idleSeconds < 180) {
                        SwingUtilities.invokeLater {
                            progress.isIndeterminate = true
                            progress.string = "Waiting for YouTube (${idleSeconds}s)…"
                        }
                    }
                    if (process.isAlive && idleSeconds >= 120 && timedOut.compareAndSet(false, true)) {
                        process.destroyForcibly()
                        SwingUtilities.invokeLater {
                            log.append("\nStopped: yt-dlp produced no output for 2 minutes. Check your connection, then try again.\n")
                            progress.isIndeterminate = false
                            progress.string = "Timed out"
                        }
                    }
                }, 5, 5, TimeUnit.SECONDS)
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        lastOutputAt.set(System.nanoTime())
                        val percent = Regex("\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%")
                            .find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                        SwingUtilities.invokeLater {
                            if (percent != null) {
                                progress.isIndeterminate = false
                                progress.value = percent.toInt().coerceIn(0, 100)
                                progress.string = "${"%.1f".format(percent)}%"
                            } else {
                                log.append("$line\n")
                                log.caretPosition = log.document.length
                                progress.isIndeterminate = true
                                progress.string = when {
                                    "Downloading webpage" in line -> "Loading video…"
                                    "Solving JS challenges" in line -> "Solving YouTube challenge…"
                                    "Downloading m3u8 information" in line -> "Reading audio streams…"
                                    "Downloading player" in line -> "Loading player…"
                                    "Downloading .* API JSON".toRegex().containsMatchIn(line) -> "Reading video info…"
                                    "Extracting URL" in line || "Extracting video data" in line -> "Reading video info…"
                                    "Downloading" in line -> "Downloading…"
                                    "Destination:" in line || "ExtractAudio" in line -> "Converting MP3…"
                                    else -> "Working…"
                                }
                            }
                        }
                    }
                }
                val code = process.waitFor()
                watchdog.shutdownNow()
                activeProcess.set(null)
                SwingUtilities.invokeLater {
                    log.append(when {
                        timedOut.get() -> "\nDownload stopped after a 2 minute output timeout.\n"
                        code == 0 -> "\nFinished. MP3 saved to: $outputDirectory\n"
                        code == 1 -> "\nDownload failed. Read the yt-dlp message above; common causes are network errors, an unavailable video, or missing FFmpeg.\n"
                        else -> "\nDownload failed with exit code $code. Read the yt-dlp output above for details.\n"
                    })
                    progress.isIndeterminate = false
                    progress.value = if (code == 0) 100 else 0
                    progress.string = when {
                        timedOut.get() -> "Timed out"
                        code == 0 -> "Complete"
                        else -> "Failed"
                    }
                    downloadButton.isEnabled = true
                    cancelButton.isEnabled = false
                }
            } catch (e: java.io.IOException) {
                SwingUtilities.invokeLater {
                    log.append("Could not start yt-dlp: ${e.message ?: e.javaClass.simpleName}\n")
                    log.append("Install yt-dlp and FFmpeg, then restart CatoTube so it can see the updated PATH.\n")
                    progress.isIndeterminate = false
                    progress.value = 0
                    progress.string = "Failed"
                    downloadButton.isEnabled = true
                    cancelButton.isEnabled = false
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    log.append("Download could not start: ${e.message ?: e.javaClass.simpleName}\n")
                    progress.isIndeterminate = false
                    progress.value = 0
                    progress.string = "Failed"
                    downloadButton.isEnabled = true
                    cancelButton.isEnabled = false
                }
            }
        }.apply { isDaemon = true; start() }
    }
    frame.isVisible = true
}

private fun startDownload(url: String): ProcessBuilder {
    Files.createDirectories(outputDirectory)
    return ProcessBuilder(
    "yt-dlp",
    "--no-playlist",
    "--extract-audio",
    "--format", "bestaudio/best",
    "--audio-format", "mp3",
    "--audio-quality", "0",
    "--newline",
    "--verbose",
    "--socket-timeout", "15",
    "--retries", "1",
    "--extractor-retries", "1",
    "--extractor-args", "youtube:player_client=tv_samsung",
    "--output", outputDirectory.resolve("%(title)s.%(ext)s").toString(),
        url,
    ).directory(File(".").absoluteFile)
}

private fun isYouTubeUrl(value: String): Boolean = try {
    val uri = URI(value)
    val host = uri.host?.lowercase()?.removePrefix("www.")
    uri.scheme in setOf("http", "https") && host in setOf("youtube.com", "m.youtube.com", "youtu.be")
} catch (_: IllegalArgumentException) {
    false
}
