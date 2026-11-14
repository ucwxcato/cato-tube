package org.catosaur.app.library

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MusicLibraryTest {
    private fun newRoot(): Path = Files.createTempDirectory("catotube-library")

    private fun seed(root: Path, tracks: List<Track>, playlists: List<Playlist> = emptyList()) {
        LibraryStorage(root).save(LibraryData(tracks = tracks, playlists = playlists))
    }

    private fun track(id: String, title: String, artist: String, added: Long, file: String) = Track(
        id = id,
        title = title,
        artist = artist,
        durationMs = 60_000L,
        relativePath = "songs/$file",
        dateAddedEpochMs = added,
    )

    private fun openWritable(root: Path): MusicLibrary =
        assertIs<OpenResult.Opened>(MusicLibrary.open(root)).library

    @Test
    fun openingAnEmptyFolderCreatesAnEmptyLibrary() {
        val root = newRoot()

        val library = openWritable(root)

        assertTrue(Files.exists(root.resolve("library.json")))
        assertTrue(library.data.tracks.isEmpty())
        assertEquals(null, library.readOnlyReason)
        library.close()
    }

    @Test
    fun searchMatchesEveryTermAcrossTitleArtistAndFileName() {
        val root = newRoot()
        seed(
            root,
            listOf(
                track("1", "Blue Monday", "New Order", 1, "blue.mp3"),
                track("2", "Heroes", "David Bowie", 2, "heroes.mp3"),
                track("3", "Blue Train", "John Coltrane", 3, "zz.mp3"),
            ),
        )
        val library = openWritable(root)

        assertEquals(listOf("Blue Monday", "Blue Train"), library.search("blue", TrackSort.TITLE).map { it.title })
        assertEquals(listOf("Blue Monday"), library.search("blue order", TrackSort.TITLE).map { it.title })
        assertEquals(listOf("Blue Train"), library.search("zz", TrackSort.TITLE).map { it.title })
        assertEquals(listOf("Blue Train"), library.search("  COLTRANE ", TrackSort.TITLE).map { it.title })
        library.close()
    }

    @Test
    fun sortOrdersByTheChosenField() {
        val root = newRoot()
        seed(
            root,
            listOf(
                track("1", "Blue Monday", "New Order", 10, "a.mp3"),
                track("2", "Heroes", "David Bowie", 30, "b.mp3"),
                track("3", "Blue Train", "John Coltrane", 20, "c.mp3"),
            ),
        )
        val library = openWritable(root)

        assertEquals(listOf("Blue Monday", "Blue Train", "Heroes"), library.search("", TrackSort.TITLE).map { it.title })
        assertEquals(listOf("Heroes", "Blue Train", "Blue Monday"), library.search("", TrackSort.ARTIST).map { it.title })
        assertEquals(listOf("Heroes", "Blue Train", "Blue Monday"), library.search("", TrackSort.DATE_ADDED).map { it.title })
        assertEquals(listOf("Heroes", "Blue Train", "Blue Monday"), library.recentlyAdded(10).map { it.title })
        library.close()
    }

    @Test
    fun recentlyAddedIsLimitedAndNewestFirst() {
        val root = newRoot()
        seed(root, (1..12).map { track("$it", "Song $it", "artist", it.toLong(), "s$it.mp3") })
        val library = openWritable(root)

        val recent = library.recentlyAdded(10)

        assertEquals(10, recent.size)
        assertEquals("Song 12", recent.first().title)
        library.close()
    }

    @Test
    fun missingAudioFileIsFlagged() {
        val root = newRoot()
        seed(root, listOf(track("1", "Gone", "artist", 1, "gone.mp3")))
        val library = openWritable(root)

        assertTrue(library.isMissing(library.data.tracks.single()))
        library.close()
    }

    @Test
    fun removeKeepsAudioUnlessAskedAndDropsPlaylistMemberships() {
        val root = newRoot()
        Files.createDirectories(root.resolve("songs"))
        Files.writeString(root.resolve("songs/keep.mp3"), "audio")
        Files.writeString(root.resolve("songs/drop.mp3"), "audio")
        seed(
            root,
            listOf(
                track("keep", "Keep", "artist", 1, "keep.mp3"),
                track("drop", "Drop", "artist", 2, "drop.mp3"),
            ),
            listOf(Playlist(id = "p", name = "Focus", trackIds = listOf("keep", "drop"), createdEpochMs = 0)),
        )
        val library = openWritable(root)

        library.removeTrack("keep", deleteAudio = false).get(10, TimeUnit.SECONDS)
        library.removeTrack("drop", deleteAudio = true).get(10, TimeUnit.SECONDS)

        assertTrue(library.data.tracks.isEmpty())
        assertEquals(emptyList(), library.data.playlists.single().trackIds)
        assertTrue(Files.exists(root.resolve("songs/keep.mp3")), "audio is kept when deleteAudio is false")
        assertFalse(Files.exists(root.resolve("songs/drop.mp3")), "audio is deleted when deleteAudio is true")
        library.close()
    }

    @Test
    fun importCopiesIntoSongsAndSkipsUnsupportedFiles() {
        val root = newRoot()
        val source = Files.createTempDirectory("catotube-source")
        val mp3 = Files.writeString(source.resolve("Imported.mp3"), "not real audio")
        val text = Files.writeString(source.resolve("notes.txt"), "ignored")
        val library = openWritable(root)
        var added = -1

        library.importFiles(listOf(mp3, text)) { added = it }.get(30, TimeUnit.SECONDS)

        assertEquals(1, added)
        assertEquals("songs/Imported.mp3", library.data.tracks.single().relativePath)
        assertTrue(Files.exists(root.resolve("songs/Imported.mp3")))
        assertTrue(Files.exists(mp3), "the original file is never moved")
        library.close()
    }

    @Test
    fun newerSchemaOpensReadOnlyAndRefusesChanges() {
        val root = newRoot()
        LibraryStorage(root).save(LibraryData(schemaVersion = LIBRARY_SCHEMA_VERSION + 1))
        val library = assertIs<OpenResult.Opened>(MusicLibrary.open(root)).library

        assertNotNull(library.readOnlyReason)
        library.importFiles(emptyList()).get(10, TimeUnit.SECONDS)
        assertEquals(LIBRARY_SCHEMA_VERSION + 1, LibraryStorage(root).load().let {
            (it as LoadOutcome.ReadOnly).data.schemaVersion
        })
        library.close()
    }

    @Test
    fun editingDetailsKeepsFileAndPlaylistMembershipAndRejectsBlankTitles() {
        val root = newRoot()
        Files.createDirectories(root.resolve("songs"))
        Files.writeString(root.resolve("songs/raw-name.mp3"), "audio")
        seed(
            root,
            listOf(track("1", "raw-name", "", 1, "raw-name.mp3")),
            listOf(Playlist(id = "p", name = "Focus", trackIds = listOf("1"), createdEpochMs = 0)),
        )
        val library = openWritable(root)

        library.updateTrack("1", "  Clean Title  ", " Some Artist ").get(10, TimeUnit.SECONDS)
        val edited = library.data.tracks.single()
        assertEquals("Clean Title", edited.title)
        assertEquals("Some Artist", edited.artist)
        assertEquals("songs/raw-name.mp3", edited.relativePath)
        assertEquals(listOf("1"), library.data.playlists.single().trackIds)
        assertTrue(Files.exists(root.resolve("songs/raw-name.mp3")))

        library.updateTrack("1", "   ", "Ignored").get(10, TimeUnit.SECONDS)
        assertEquals("Clean Title", library.data.tracks.single().title)
        library.close()
    }

    @Test
    fun unwritableFolderWithLibraryOpensReadOnly() {
        val root = newRoot()
        seed(root, listOf(track("1", "Song", "artist", 1, "song.mp3")))

        val library = assertIs<OpenResult.Opened>(MusicLibrary.open(root, forceReadOnly = true)).library

        assertNotNull(library.readOnlyReason)
        assertEquals(1, library.data.tracks.size)
        library.close()
    }

    @Test
    fun unwritableFolderWithoutLibraryDoesNotCreateOne() {
        val root = newRoot()

        val result = MusicLibrary.open(root, forceReadOnly = true)

        assertIs<OpenResult.Failed>(result)
        assertFalse(Files.exists(root.resolve("library.json")))
    }

    @Test
    fun playlistNamesAreTrimmedRequiredAndUniqueIgnoringCase() {
        val root = newRoot()
        seed(root, listOf(track("1", "Song", "artist", 1, "song.mp3")))
        val library = openWritable(root)
        val errors = mutableListOf<String>()
        library.addErrorListener { errors += it }

        library.createPlaylist("  Focus  ").get(10, TimeUnit.SECONDS)
        library.createPlaylist("focus").get(10, TimeUnit.SECONDS)
        library.createPlaylist("   ").get(10, TimeUnit.SECONDS)

        assertEquals(listOf("Focus"), library.data.playlists.map { it.name })
        assertEquals(2, errors.size)
        assertEquals("A playlist named \"focus\" already exists.", errors[0])
        assertEquals("A playlist needs a name.", errors[1])
        library.close()
    }

    @Test
    fun renameAllowsKeepingTheSameNameButNotTakingAnotherPlaylistsName() {
        val root = newRoot()
        seed(
            root,
            emptyList(),
            listOf(
                Playlist(id = "a", name = "Focus", createdEpochMs = 0),
                Playlist(id = "b", name = "Gym", createdEpochMs = 0),
            ),
        )
        val library = openWritable(root)
        val errors = mutableListOf<String>()
        library.addErrorListener { errors += it }

        library.renamePlaylist("a", "FOCUS").get(10, TimeUnit.SECONDS)
        library.renamePlaylist("a", "gym").get(10, TimeUnit.SECONDS)
        library.renamePlaylist("b", "Workout").get(10, TimeUnit.SECONDS)

        assertEquals(listOf("FOCUS", "Workout"), library.data.playlists.map { it.name })
        assertEquals(listOf("A playlist named \"gym\" already exists."), errors)
        library.close()
    }

    @Test
    fun trackCanBeInSeveralPlaylistsAndDeletingOneKeepsAudioAndTheOther() {
        val root = newRoot()
        Files.createDirectories(root.resolve("songs"))
        Files.writeString(root.resolve("songs/shared.mp3"), "audio")
        seed(root, listOf(track("1", "Shared", "artist", 1, "shared.mp3")))
        val library = openWritable(root)
        library.createPlaylist("One", listOf("1")).get(10, TimeUnit.SECONDS)
        library.createPlaylist("Two", listOf("1")).get(10, TimeUnit.SECONDS)
        val (one, two) = library.data.playlists

        library.deletePlaylist(one.id).get(10, TimeUnit.SECONDS)

        assertEquals(listOf("Two"), library.data.playlists.map { it.name })
        assertEquals(listOf("1"), library.data.playlists.single().trackIds)
        assertEquals(two.id, library.data.playlists.single().id)
        assertEquals(1, library.data.tracks.size)
        assertTrue(Files.exists(root.resolve("songs/shared.mp3")))
        library.close()
    }

    @Test
    fun addToPlaylistSkipsDuplicatesAndUnknownTracks() {
        val root = newRoot()
        seed(
            root,
            listOf(
                track("1", "One", "artist", 1, "one.mp3"),
                track("2", "Two", "artist", 2, "two.mp3"),
            ),
            listOf(Playlist(id = "p", name = "Mix", trackIds = listOf("1"), createdEpochMs = 0)),
        )
        val library = openWritable(root)
        var added = -1

        library.addToPlaylist("p", listOf("1", "2", "2", "missing")) { added = it }.get(10, TimeUnit.SECONDS)

        assertEquals(1, added)
        assertEquals(listOf("1", "2"), library.data.playlists.single().trackIds)
        library.close()
    }

    @Test
    fun reorderAndRemoveArePersistedAndKeepTheTrackAudio() {
        val root = newRoot()
        Files.createDirectories(root.resolve("songs"))
        Files.writeString(root.resolve("songs/a.mp3"), "audio")
        Files.writeString(root.resolve("songs/b.mp3"), "audio")
        Files.writeString(root.resolve("songs/c.mp3"), "audio")
        seed(
            root,
            listOf(
                track("a", "A", "artist", 1, "a.mp3"),
                track("b", "B", "artist", 2, "b.mp3"),
                track("c", "C", "artist", 3, "c.mp3"),
            ),
            listOf(Playlist(id = "p", name = "Order", trackIds = listOf("a", "b", "c"), createdEpochMs = 0)),
        )
        val library = openWritable(root)

        library.moveInPlaylist("p", "a", -1).get(10, TimeUnit.SECONDS)
        assertEquals(listOf("a", "b", "c"), library.data.playlists.single().trackIds, "moving the first track up does nothing")
        library.moveInPlaylist("p", "c", -1).get(10, TimeUnit.SECONDS)
        assertEquals(listOf("a", "c", "b"), library.data.playlists.single().trackIds)
        library.removeFromPlaylist("p", "c").get(10, TimeUnit.SECONDS)
        library.close()

        val reopened = openWritable(root)

        assertEquals(listOf("a", "b"), reopened.data.playlists.single().trackIds)
        assertEquals(3, reopened.data.tracks.size)
        assertTrue(Files.exists(root.resolve("songs/c.mp3")))
        reopened.close()
    }

    @Test
    fun removingATrackFromTheLibraryRemovesItFromEveryPlaylist() {
        val root = newRoot()
        seed(
            root,
            listOf(track("1", "One", "artist", 1, "gone-one.mp3")),
            listOf(
                Playlist(id = "p", name = "P", trackIds = listOf("1"), createdEpochMs = 0),
                Playlist(id = "q", name = "Q", trackIds = listOf("1"), createdEpochMs = 0),
            ),
        )
        val library = openWritable(root)

        library.removeTrack("1", deleteAudio = false).get(10, TimeUnit.SECONDS)

        assertTrue(library.data.playlists.all { it.trackIds.isEmpty() })
        assertEquals(2, library.data.playlists.size)
        library.close()
    }
}
