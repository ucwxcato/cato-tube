package org.catosaur.app.playback

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Receives playback events. Methods are called on the playback thread, so UI code must marshal to
 * the EDT before touching Swing.
 */
internal interface PlaybackListener {
    fun onPosition(positionMs: Long)
    fun onPlayingChanged(playing: Boolean)
    fun onEnded()
    fun onError(file: Path, message: String)
}

private const val SAMPLE_RATE = 44100f
private const val CHUNK_BYTES = 4096
private const val LINE_BUFFER_BYTES = 44100
private const val POSITION_INTERVAL_MS = 100L
private const val PAUSE_POLL_MS = 50L

/**
 * Plays one file at a time. ffmpeg decodes the file to 16-bit stereo PCM on stdout, and this class
 * writes that PCM to a [SourceDataLine]. A single thread owns the process and the line; callers send
 * commands through a queue, so play, pause, seek, and stop never race.
 *
 * Seek restarts ffmpeg at the requested position. Volume and mute scale the PCM before it is written.
 */
internal class AudioPlayer(private val listener: PlaybackListener) {
    private val format = AudioFormat(SAMPLE_RATE, 16, 2, true, false)
    private val commands = LinkedBlockingQueue<Command>()
    @Volatile private var volume = 1f
    @Volatile private var muted = false
    @Volatile private var peak = 0f
    private val analyzer = SpectrumAnalyzer()
    @Volatile private var bands = FloatArray(SPECTRUM_BANDS)

    private sealed interface Command {
        data class Play(val file: Path, val startMs: Long) : Command
        data object Pause : Command
        data object Resume : Command
        data object Stop : Command
        data object Shutdown : Command
    }

    private class Session(
        val file: Path,
        val process: Process,
        val line: SourceDataLine,
        val baseMs: Long,
    ) {
        var sawAudio = false
        var lastReportMs = 0L
    }

    private var session: Session? = null
    private var paused = false
    private val thread = Thread(::runLoop, "audio-playback").apply {
        isDaemon = true
        start()
    }

    /** Starts [file] at [startMs]. Seeking is the same call with a new [startMs]. */
    fun play(file: Path, startMs: Long = 0L) {
        commands.put(Command.Play(file, startMs.coerceAtLeast(0L)))
    }

    fun pause() {
        commands.put(Command.Pause)
    }

    fun resume() {
        commands.put(Command.Resume)
    }

    fun stop() {
        commands.put(Command.Stop)
    }

    /** Sets the output level from 0 to 1. Takes effect on the next block of audio. */
    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
    }

    fun setMuted(value: Boolean) {
        muted = value
    }

    /** Stops playback, releases the audio device, and waits briefly for the thread to finish. */
    fun close() {
        commands.put(Command.Shutdown)
        thread.join(CLOSE_TIMEOUT_MS)
    }

    private fun runLoop() {
        val buffer = ByteArray(CHUNK_BYTES)
        while (true) {
            val active = session
            val command = when {
                active == null -> commands.take()
                paused -> commands.poll(PAUSE_POLL_MS, TimeUnit.MILLISECONDS)
                else -> commands.poll()
            }
            if (command != null) {
                if (handle(command)) return
                continue
            }
            val current = session ?: continue
            if (paused) continue

            val read = current.process.inputStream.readNBytes(buffer, 0, buffer.size)
            if (read == 0) {
                finishNaturally(current)
                continue
            }
            current.sawAudio = true
            applyGain(buffer, read)
            measure(buffer, read)
            bands = analyzer.analyze(buffer, read)
            current.line.write(buffer, 0, read)
            reportPosition(current)
        }
    }

    /** Returns true when the loop should exit. */
    private fun handle(command: Command): Boolean {
        when (command) {
            is Command.Play -> startSession(command.file, command.startMs)
            Command.Pause -> {
                val current = session ?: return false
                if (!paused) {
                    paused = true
                    current.line.stop()
                    listener.onPlayingChanged(false)
                }
            }
            Command.Resume -> {
                val current = session ?: return false
                if (paused) {
                    paused = false
                    current.line.start()
                    listener.onPlayingChanged(true)
                }
            }
            Command.Stop -> {
                if (session != null) {
                    endSession()
                    listener.onPlayingChanged(false)
                }
            }
            Command.Shutdown -> {
                endSession()
                return true
            }
        }
        return false
    }

    private fun startSession(file: Path, startMs: Long) {
        endSession()
        if (!Files.isRegularFile(file)) {
            listener.onError(file, "The file is missing.")
            return
        }
        val process = try {
            ProcessBuilder(
                "ffmpeg", "-hide_banner", "-loglevel", "error",
                "-ss", String.format(Locale.ROOT, "%.3f", startMs / 1000.0),
                "-i", file.toString(),
                "-vn", "-f", "s16le", "-ar", "44100", "-ac", "2", "pipe:1",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: IOException) {
            listener.onError(file, "ffmpeg could not start. Check that it is installed and on PATH.")
            return
        }
        val line = try {
            AudioSystem.getSourceDataLine(format).apply {
                open(format, LINE_BUFFER_BYTES)
                start()
            }
        } catch (e: LineUnavailableException) {
            process.destroyForcibly()
            listener.onError(file, "No audio output device is available.")
            return
        }
        session = Session(file, process, line, startMs)
        paused = false
        listener.onPlayingChanged(true)
    }

    /** Ends the current session, if any, without notifying listeners. */
    private fun endSession() {
        val current = session ?: return
        session = null
        paused = false
        bands = FloatArray(SPECTRUM_BANDS)
        current.process.destroyForcibly()
        runCatching { current.process.waitFor(2, TimeUnit.SECONDS) }
        current.line.stop()
        current.line.close()
    }

    private fun finishNaturally(current: Session) {
        if (!current.sawAudio) {
            endSession()
            listener.onPlayingChanged(false)
            listener.onError(current.file, "The file could not be decoded. It may be damaged or not audio.")
            return
        }
        session = null
        paused = false
        bands = FloatArray(SPECTRUM_BANDS)
        current.line.drain()
        current.line.stop()
        current.line.close()
        current.process.destroyForcibly()
        listener.onPlayingChanged(false)
        listener.onEnded()
    }

    private fun reportPosition(current: Session) {
        val now = System.currentTimeMillis()
        if (now - current.lastReportMs < POSITION_INTERVAL_MS) return
        current.lastReportMs = now
        val playedUs = current.line.microsecondPosition.coerceAtLeast(0L)
        listener.onPosition(current.baseMs + playedUs / 1000L)
    }

    /** Returns the loudest block level (roughly 0 to 1) since the last call, then resets it. */
    fun takeLevel(): Float {
        val value = peak
        peak = 0f
        return value
    }

    /** Band levels (0 to 1) of the most recent block, after volume and mute. Zeros when idle. */
    fun spectrum(): FloatArray = bands

    /** Keeps the loudest RMS of the blocks written since the last [takeLevel]. */
    private fun measure(buffer: ByteArray, count: Int) {
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < count) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)).toDouble()
            sum += sample * sample
            samples++
            i += 2
        }
        if (samples == 0) return
        val rms = (sqrt(sum / samples) / 32768.0).toFloat()
        if (rms > peak) peak = rms
    }

    /** Scales little-endian 16-bit samples in [buffer] by the current volume, or silences them when muted. */
    private fun applyGain(buffer: ByteArray, count: Int) {
        val gain = if (muted) 0f else volume
        if (gain >= 0.999f) return
        var i = 0
        while (i + 1 < count) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)
            val scaled = (sample * gain).roundToInt().coerceIn(-32768, 32767)
            buffer[i] = scaled.toByte()
            buffer[i + 1] = (scaled shr 8).toByte()
            i += 2
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 3000L
    }
}
