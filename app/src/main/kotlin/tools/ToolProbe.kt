package org.catosaur.app.tools

import java.io.IOException
import java.util.concurrent.TimeUnit

/** An external command CatoTube runs. [install] is the WinGet line shown when the tool is missing. */
internal enum class Tool(
    val label: String,
    val command: String,
    val versionArgs: List<String>,
    val purpose: String,
    val install: String,
) {
    YT_DLP(
        label = "yt-dlp",
        command = "yt-dlp",
        versionArgs = listOf("--version"),
        purpose = "YouTube search and downloads",
        install = "winget install --source winget --id yt-dlp.yt-dlp",
    ),
    FFMPEG(
        label = "FFmpeg",
        command = "ffmpeg",
        versionArgs = listOf("-version"),
        purpose = "MP3 conversion and playback",
        install = "winget install --source winget --id Gyan.FFmpeg",
    ),
    FFPROBE(
        label = "ffprobe",
        command = "ffprobe",
        versionArgs = listOf("-version"),
        purpose = "duration and metadata",
        install = "winget install --source winget --id Gyan.FFmpeg",
    ),
    DENO(
        label = "Deno",
        command = "deno",
        versionArgs = listOf("--version"),
        purpose = "yt-dlp JavaScript challenge solver",
        install = "winget install --source winget --id DenoLand.Deno",
    ),
}

internal enum class ToolStatus { Found, Missing, Failed }

/** Outcome of probing one tool. [version] is the first output line when found; [detail] explains a failure. */
internal data class ToolCheck(
    val tool: Tool,
    val status: ToolStatus,
    val version: String? = null,
    val detail: String? = null,
) {
    val ok: Boolean get() = status == ToolStatus.Found

    /** Version line trimmed to fit a dialog row. */
    val displayVersion: String?
        get() = version?.let { if (it.length > 72) it.take(69) + "..." else it }
}

/** Probes the external tools. A check never throws and never blocks longer than its timeout. */
internal object ToolProbe {
    /** Runs every tool's version command. */
    fun checkAll(timeoutSeconds: Long = 10): List<ToolCheck> = Tool.entries.map { check(it, timeoutSeconds) }

    /** Runs one tool's version command. */
    fun check(tool: Tool, timeoutSeconds: Long = 10): ToolCheck =
        run(tool, listOf(tool.command) + tool.versionArgs, timeoutSeconds)

    /** Runs [command] and classifies the result. Tests call this with fake commands. */
    fun run(tool: Tool, command: List<String>, timeoutSeconds: Long): ToolCheck {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (_: IOException) {
            return ToolCheck(tool, ToolStatus.Missing)
        }
        return try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                ToolCheck(tool, ToolStatus.Failed, detail = "no response within ${timeoutSeconds}s")
            } else {
                val output = process.inputStream.bufferedReader().readText()
                val firstLine = output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                if (process.exitValue() == 0) {
                    ToolCheck(tool, ToolStatus.Found, version = firstLine ?: "unknown version")
                } else {
                    ToolCheck(tool, ToolStatus.Failed, detail = firstLine ?: "exit code ${process.exitValue()}")
                }
            }
        } catch (_: IOException) {
            ToolCheck(tool, ToolStatus.Failed, detail = "could not read output")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            ToolCheck(tool, ToolStatus.Failed, detail = "interrupted")
        }
    }
}
