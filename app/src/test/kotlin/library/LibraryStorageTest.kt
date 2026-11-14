package org.catosaur.app.library

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryStorageTest {
    private fun newRoot(): Path = Files.createTempDirectory("catotube-storage")

    private fun data(vararg titles: String) = LibraryData(
        savedAtEpochMs = 1L,
        tracks = titles.mapIndexed { index, title ->
            Track(
                id = "id-$index",
                title = title,
                artist = "artist $index",
                durationMs = 1_000L * (index + 1),
                relativePath = "songs/$title.mp3",
                dateAddedEpochMs = 100L + index,
            )
        },
        playlists = listOf(Playlist(id = "p1", name = "Focus", trackIds = listOf("id-0"), createdEpochMs = 5L)),
    )

    @Test
    fun saveThenLoadReturnsEqualData() {
        val root = newRoot()
        val storage = LibraryStorage(root)
        val original = data("one", "two")

        storage.save(original)

        val outcome = assertIs<LoadOutcome.Loaded>(storage.load())
        assertEquals(original, outcome.data)
        assertEquals("library.json", outcome.source)
        assertNull(outcome.notice)
    }

    @Test
    fun missingFilesReportMissing() {
        assertEquals(LoadOutcome.Missing, LibraryStorage(newRoot()).load())
    }

    @Test
    fun corruptMainFileFallsBackToBackupAndKeepsTheDamagedCopy() {
        val root = newRoot()
        val storage = LibraryStorage(root)
        val first = data("first")
        val second = data("second")
        storage.save(first)
        storage.save(second)
        Files.writeString(root.resolve("library.json"), "{ this is not json")

        val outcome = assertIs<LoadOutcome.Loaded>(storage.load())

        assertEquals(first, outcome.data)
        assertEquals("library.json.bak", outcome.source)
        assertNotNull(outcome.notice)
        val kept = Files.list(root).use { stream ->
            stream.map { it.fileName.toString() }.filter { it.startsWith("library.json.corrupt-") }.toList()
        }
        assertEquals(1, kept.size, "the damaged file must be kept, not deleted")
    }

    @Test
    fun bothFilesCorruptIsUnreadableAndChangesNothing() {
        val root = newRoot()
        Files.writeString(root.resolve("library.json"), "broken main")
        Files.writeString(root.resolve("library.json.bak"), "broken backup")

        val outcome = LibraryStorage(root).load()

        assertIs<LoadOutcome.Unreadable>(outcome)
        assertEquals("broken main", Files.readString(root.resolve("library.json")))
        assertEquals("broken backup", Files.readString(root.resolve("library.json.bak")))
    }

    @Test
    fun interruptedSaveLeavesThePreviousFileValid() {
        val root = newRoot()
        val storage = LibraryStorage(root)
        val saved = data("kept")
        storage.save(saved)
        // Simulates a crash after the temp file was written but before it replaced library.json.
        Files.writeString(root.resolve("library.json.tmp"), "{ half written")

        val outcome = assertIs<LoadOutcome.Loaded>(storage.load())
        assertEquals(saved, outcome.data)
        assertNull(outcome.notice)

        val next = data("next")
        storage.save(next)
        assertEquals(next, assertIs<LoadOutcome.Loaded>(storage.load()).data)
    }

    @Test
    fun newerSchemaOpensReadOnly() {
        val root = newRoot()
        val storage = LibraryStorage(root)
        storage.save(LibraryData(schemaVersion = LIBRARY_SCHEMA_VERSION + 1, tracks = emptyList()))

        val outcome = assertIs<LoadOutcome.ReadOnly>(storage.load())

        assertTrue(outcome.reason.contains("newer CatoTube"))
    }
}
