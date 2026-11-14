package org.catosaur.app.ui

import org.catosaur.app.download.DEFAULT_DOWNLOAD_DIRECTORY
import org.catosaur.app.library.FolderProblem
import org.catosaur.app.library.LibraryFolder
import org.catosaur.app.library.MusicLibrary
import org.catosaur.app.library.OpenResult
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JFrame

/** Startup and folder-selection flows. Every method runs on the EDT and may show modal dialogs. */
internal object LibrarySetup {
    private val home: Path = Path.of(System.getProperty("user.home"))

    /** Resolves the saved library folder at startup. Returns null when the user continues without a library. */
    fun resolveStartup(frame: JFrame): MusicLibrary? {
        val root = LibraryFolder.savedRoot() ?: return firstRun(frame)
        while (true) {
            val problem = LibraryFolder.problem(root)
            if (problem == null || (problem == FolderProblem.NotWritable && LibraryFolder.hasLibrary(root))) {
                return openFolder(frame, root)
            }
            val choice = showThemedChoice(
                frame,
                "Library folder unavailable",
                unavailableMessage(root, problem),
                listOf("Relocate...", "Open another...", "Retry", "Continue without library"),
                defaultIndex = 2,
            )
            when (choice) {
                0 -> chooseAndOpen(frame, root.parent ?: home, "Find the moved library folder")?.let { return it }
                1 -> chooseAndOpen(frame, root.parent ?: home, "Open another library folder")?.let { return it }
                2 -> Unit
                else -> return null
            }
        }
    }

    /** Lets the user pick a folder and opens it as the library. Returns null on cancel or failure. */
    fun chooseAndOpen(frame: JFrame, start: Path?, title: String): MusicLibrary? {
        val chooser = JFileChooser((start ?: home).toFile()).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return null
        return openFolder(frame, chooser.selectedFile.toPath().toAbsolutePath())
    }

    /**
     * Opens [root] as the library. An existing library opens as usual. An empty folder needs the
     * user's confirmation before the library files are created. A read-only folder with a library
     * opens in read-only mode.
     */
    fun openFolder(frame: JFrame, root: Path): MusicLibrary? {
        val problem = LibraryFolder.problem(root)
        val hasLibrary = LibraryFolder.hasLibrary(root)
        if (problem == FolderProblem.NotWritable && !hasLibrary) {
            showError(frame, "This folder is not writable and does not contain a library:\n$root\n\nChoose another folder.")
            return null
        }
        if (problem == FolderProblem.Missing || problem == FolderProblem.NotDirectory) {
            showError(frame, "This is not an existing folder:\n$root")
            return null
        }
        if (problem == null && !hasLibrary) {
            val confirmed = showThemedQuestion(
                frame,
                "Create library",
                "Create a CatoTube library in this folder?\n\n$root\n\n" +
                    "This adds the songs and artwork folders and library.json. Existing files are not changed.",
                yes = "Create library",
                no = "Cancel",
            )
            if (!confirmed) return null
            LibraryFolder.createLayout(root)
        }
        return when (val result = MusicLibrary.open(root, forceReadOnly = problem == FolderProblem.NotWritable)) {
            is OpenResult.Opened -> {
                LibraryFolder.saveRoot(root)
                result.library
            }
            is OpenResult.Failed -> {
                showError(frame, result.message)
                null
            }
            OpenResult.NoLibrary -> null
        }
    }

    /**
     * One-time offer to copy MP3s from the old downloads folder into the library. Files are copied,
     * never moved. The offer is recorded as done once the user answers.
     */
    fun offerDownloadsImport(frame: JFrame, library: MusicLibrary?, status: (String) -> Unit) {
        if (library == null || library.readOnlyReason != null || LibraryFolder.migrationDone()) return
        val files = existingDownloads()
        if (files.isEmpty()) return
        val answer = showThemedQuestion(
            frame,
            "Import earlier downloads",
            "Found ${files.size} MP3 file(s) in:\n$DEFAULT_DOWNLOAD_DIRECTORY\n\n" +
                "Import them into the library? They are copied into the library's songs folder. " +
                "The originals stay where they are.",
            yes = "Import",
            no = "Not now",
        )
        LibraryFolder.markMigrationDone()
        if (answer) {
            status("Importing ${files.size} file(s)...")
            library.importFiles(files) { count ->
                javax.swing.SwingUtilities.invokeLater {
                    status(if (count == 0) "Nothing new was imported." else "Imported $count song(s) from downloads.")
                }
            }
        }
    }

    private fun firstRun(frame: JFrame): MusicLibrary? {
        showThemedMessage(
            frame,
            "Set up your library",
            "Choose a folder for your CatoTube library. Songs are stored in a songs folder inside it, " +
                "and the library index is kept in library.json.",
        )
        return chooseAndOpen(frame, null, "Choose library folder")
    }

    private fun unavailableMessage(root: Path, problem: FolderProblem?): String {
        val reason = when (problem) {
            FolderProblem.Missing -> "The library folder is missing:"
            FolderProblem.NotDirectory -> "The saved library location is not a folder:"
            FolderProblem.NotWritable -> "The library folder is not writable and has no library:"
            null -> "The library folder is unavailable:"
        }
        return "$reason\n$root\n\nNothing was changed. Relocate it, open another library, or retry after reconnecting the drive."
    }

    private fun existingDownloads(): List<Path> {
        val directory = DEFAULT_DOWNLOAD_DIRECTORY
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mp3", ignoreCase = true) }
                .sorted()
                .toList()
        }
    }

    private fun showError(frame: JFrame, message: String) {
        showThemedMessage(frame, "Library", message)
    }
}
