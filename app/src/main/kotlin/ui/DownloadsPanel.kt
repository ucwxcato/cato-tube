package org.catosaur.app.ui

import org.catosaur.app.Palette
import org.catosaur.app.Card
import org.catosaur.app.FlatButton
import org.catosaur.app.Fonts
import org.catosaur.app.ThemedField
import org.catosaur.app.ThemedProgressBar
import org.catosaur.app.themeScrollBars
import org.catosaur.app.SectionHeading
import org.catosaur.app.download.DEFAULT_DOWNLOAD_DIRECTORY
import org.catosaur.app.download.isYouTubeUrl
import org.catosaur.app.download.ytDlpDownloadCommand
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/** The manual URL download workflow. Behavior is unchanged from the original single-window version. */
internal fun buildDownloadsPanel(frame: JFrame): JComponent {
    var outputDirectory = DEFAULT_DOWNLOAD_DIRECTORY
    val urlField = ThemedField().apply { placeholder = "Paste a YouTube link" }
    val folderField = ThemedField(outputDirectory.toString()).apply {
        isEditable = false
        foreground = Palette.muted
    }
    val log = JTextArea("Only download audio you own or are authorized to use.\n").apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        font = Fonts.mono(13)
        foreground = Palette.text
    }
    val logScroll = JScrollPane(log).apply {
        isOpaque = false
        viewport.isOpaque = false
        border = BorderFactory.createEmptyBorder()
        themeScrollBars()
    }
    val downloadButton = FlatButton("Download MP3", primary = true)
    val folderButton = FlatButton("Browse")
    val progress = ThemedProgressBar().apply { string = "Ready" }
    val activeProcess = AtomicReference<Process?>(null)
    val cancelButton = FlatButton("Cancel", hoverTint = Palette.danger).apply { isEnabled = false }

    val inputCard = Card(GridBagLayout()).apply {
        border = BorderFactory.createEmptyBorder(18, 20, 18, 20)
        val c = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            gridwidth = 2
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 8, 0)
        }
        add(SectionHeading("YouTube link"), c)
        c.gridy = 1
        c.gridwidth = 1
        c.weightx = 1.0
        c.insets = Insets(0, 0, 0, 12)
        add(urlField, c)
        c.gridx = 1
        c.weightx = 0.0
        c.insets = Insets(0, 0, 0, 0)
        add(downloadButton, c)
        c.gridx = 0
        c.gridy = 2
        c.gridwidth = 2
        c.insets = Insets(18, 0, 8, 0)
        add(SectionHeading("Save to"), c)
        c.gridy = 3
        c.gridwidth = 1
        c.weightx = 1.0
        c.insets = Insets(0, 0, 0, 12)
        add(folderField, c)
        c.gridx = 1
        c.weightx = 0.0
        c.insets = Insets(0, 0, 0, 0)
        add(folderButton, c)
    }
    val logCard = Card(BorderLayout(0, 10)).apply {
        border = BorderFactory.createEmptyBorder(16, 20, 14, 12)
        add(SectionHeading("Activity"), BorderLayout.NORTH)
        add(logScroll, BorderLayout.CENTER)
    }
    val progressRow = JPanel(BorderLayout(12, 0)).apply {
        isOpaque = false
        add(progress, BorderLayout.CENTER)
        add(cancelButton, BorderLayout.EAST)
    }

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
            showThemedMessage(frame, "Invalid URL", "Enter a valid youtube.com or youtu.be URL.")
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
                val process = ytDlpDownloadCommand(outputDirectory, url).redirectErrorStream(true).start()
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
                        val percent = Regex("\\[download]\\s+(\\d+(?:\\.\\d+)?)%")
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

    return JPanel(BorderLayout(0, 14)).apply {
        isOpaque = false
        add(inputCard, BorderLayout.NORTH)
        add(logCard, BorderLayout.CENTER)
        add(progressRow, BorderLayout.SOUTH)
    }
}
