package org.catosaur.app.library

import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

internal enum class FolderProblem { Missing, NotDirectory, NotWritable }

/** Remembers the library folder and checks that it can be used. */
internal object LibraryFolder {
    private const val KEY_ROOT = "libraryRoot"
    private const val KEY_MIGRATION_DONE = "schemaMigrationDone"

    private val prefs: Preferences
        get() = Preferences.userRoot().node("org/catosaur/app")

    fun savedRoot(): Path? = prefs.get(KEY_ROOT, null)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }

    fun saveRoot(root: Path) {
        prefs.put(KEY_ROOT, root.toAbsolutePath().toString())
        prefs.flush()
    }

    fun migrationDone(): Boolean = prefs.getBoolean(KEY_MIGRATION_DONE, false)

    fun markMigrationDone() {
        prefs.putBoolean(KEY_MIGRATION_DONE, true)
        prefs.flush()
    }

    fun problem(root: Path): FolderProblem? = when {
        !Files.exists(root) -> FolderProblem.Missing
        !Files.isDirectory(root) -> FolderProblem.NotDirectory
        !isWritable(root) -> FolderProblem.NotWritable
        else -> null
    }

    fun hasLibrary(root: Path): Boolean =
        Files.exists(root.resolve(LibraryStorage.FILE_NAME)) ||
            Files.exists(root.resolve("${LibraryStorage.FILE_NAME}.bak"))

    fun createLayout(root: Path) {
        Files.createDirectories(root.resolve(SONGS_DIR))
        Files.createDirectories(root.resolve("artwork"))
    }

    private fun isWritable(root: Path): Boolean = try {
        val probe = Files.createTempFile(root, ".catotube-", ".probe")
        Files.delete(probe)
        true
    } catch (_: Exception) {
        false
    }
}
