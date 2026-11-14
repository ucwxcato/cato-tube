package org.catosaur.app.library

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/** Reads audio duration through ffprobe. Returns null when ffprobe is missing or the file cannot be read. */
internal object MediaProbe {
    fun durationMs(file: Path): Long? = try {
        val process = ProcessBuilder(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "csv=p=0",
            file.toString(),
        ).redirectErrorStream(true).start()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else {
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.exitValue() == 0) output.toDoubleOrNull()?.let { (it * 1000).roundToLong() } else null
        }
    } catch (_: IOException) {
        null
    }
}
