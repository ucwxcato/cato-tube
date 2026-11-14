package org.catosaur.app.library

import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal sealed interface LoadOutcome {
    /** [source] names the file the data came from. [notice] explains any recovery that happened. */
    data class Loaded(val data: LibraryData, val source: String, val notice: String?) : LoadOutcome

    /** The data was written by a newer CatoTube. It may be shown but must not be saved. */
    data class ReadOnly(val data: LibraryData, val reason: String) : LoadOutcome

    data object Missing : LoadOutcome

    data class Unreadable(val message: String) : LoadOutcome
}

/**
 * Reads and writes library.json with a backup and an atomic replace.
 *
 * Save order: write library.json.tmp and flush it, copy the current library.json to
 * library.json.bak, then move the temp file over library.json. A crash at any step leaves
 * either the old or the new file intact.
 */
internal class LibraryStorage(private val root: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mainFile = root.resolve(FILE_NAME)
    private val backupFile = root.resolve("$FILE_NAME.bak")
    private val tempFile = root.resolve("$FILE_NAME.tmp")

    fun load(): LoadOutcome {
        val mainExists = Files.exists(mainFile)
        val backupExists = Files.exists(backupFile)
        if (!mainExists && !backupExists) return LoadOutcome.Missing

        if (mainExists) {
            val main = parse(mainFile)
            if (main.isSuccess) return classify(main.getOrThrow(), FILE_NAME, notice = null)

            val mainError = main.exceptionOrNull()?.message ?: "unknown error"
            if (!backupExists) {
                return LoadOutcome.Unreadable("library.json could not be read ($mainError), and no backup exists. Nothing was changed.")
            }
            val backup = parse(backupFile)
            if (backup.isFailure) {
                return LoadOutcome.Unreadable("library.json ($mainError) and library.json.bak could not be read. Nothing was changed.")
            }
            val kept = "library.json.corrupt-${System.currentTimeMillis()}"
            Files.move(mainFile, root.resolve(kept))
            return classify(
                backup.getOrThrow(),
                "$FILE_NAME.bak",
                notice = "library.json could not be read ($mainError). The last backup was loaded and the damaged file was kept as $kept.",
            )
        }

        // Only the backup exists, so the main file was removed. Recover from the backup.
        val backup = parse(backupFile)
        if (backup.isFailure) {
            return LoadOutcome.Unreadable("library.json.bak could not be read (${backup.exceptionOrNull()?.message}). Nothing was changed.")
        }
        return classify(backup.getOrThrow(), "$FILE_NAME.bak", notice = "library.json was missing. The last backup was loaded.")
    }

    fun save(data: LibraryData) {
        Files.createDirectories(root)
        val text = json.encodeToString(LibraryData.serializer(), data)
        FileChannel.open(
            tempFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        ).use { channel ->
            channel.write(ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8)))
            channel.force(true)
        }
        if (Files.exists(mainFile)) {
            Files.copy(mainFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.move(tempFile, mainFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile, mainFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parse(file: Path): Result<LibraryData> = runCatching {
        json.decodeFromString(LibraryData.serializer(), Files.readString(file, StandardCharsets.UTF_8))
    }

    private fun classify(data: LibraryData, source: String, notice: String?): LoadOutcome =
        if (data.schemaVersion > LIBRARY_SCHEMA_VERSION) {
            LoadOutcome.ReadOnly(
                data,
                "This library was written by a newer CatoTube (schema ${data.schemaVersion}). It is shown read-only.",
            )
        } else {
            LoadOutcome.Loaded(data, source, notice)
        }

    companion object {
        const val FILE_NAME = "library.json"
    }
}
