package org.catosaur.app.library

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

internal const val SONGS_DIR = "songs"

private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "flac", "opus")

private val RESERVED_WINDOWS_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
    (1..9).flatMap { listOf("COM$it", "LPT$it") }

internal fun isSupportedAudio(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

/**
 * Turns a title into a filename stem that is safe on Windows: reserved characters and
 * control characters become underscores, trailing dots and spaces are removed, reserved
 * device names are prefixed, and the result is capped at 120 characters.
 */
internal fun safeFileStem(title: String, fallback: String = "track"): String {
    val replaced = title.map { ch ->
        if (ch in "<>:\"/\\|?*" || ch.code < 32) '_' else ch
    }.joinToString("")
    val stem = replaced.trim().trimEnd('.', ' ').take(120).ifBlank { fallback }
    return if (stem.uppercase() in RESERVED_WINDOWS_NAMES) "_$stem" else stem
}

/**
 * Moves a finished download into [songsDirectory] as "<safe title>.mp3". Collisions get " (2)",
 * " (3)", and so on. Existing files are never overwritten.
 */
internal fun moveAsNewSong(source: Path, songsDirectory: Path, title: String): Path {
    Files.createDirectories(songsDirectory)
    val stem = safeFileStem(title)
    var index = 1
    while (true) {
        val name = if (index == 1) "$stem.mp3" else "$stem ($index).mp3"
        val target = songsDirectory.resolve(name)
        try {
            Files.move(source, target)
            return target
        } catch (_: FileAlreadyExistsException) {
            index++
        }
    }
}

/**
 * Copies [source] into [songsDirectory] under a name that does not exist yet. Existing files
 * are never overwritten: collisions get " (2)", " (3)", and so on. A partial copy is removed
 * if the copy fails.
 */
internal fun copyAsNewSong(source: Path, songsDirectory: Path): Path {
    Files.createDirectories(songsDirectory)
    val stem = safeFileStem(source.fileName.toString().substringBeforeLast('.'))
    val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
    var index = 1
    while (true) {
        val name = if (index == 1) "$stem.$extension" else "$stem ($index).$extension"
        val target = songsDirectory.resolve(name)
        try {
            Files.copy(source, target)
            return target
        } catch (_: FileAlreadyExistsException) {
            index++
        } catch (e: Exception) {
            Files.deleteIfExists(target)
            throw e
        }
    }
}
