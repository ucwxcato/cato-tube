package org.catosaur.app.library

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class DownloadRegistrationTest {
    private fun newRoot(): Path = Files.createTempDirectory("catotube-download")

    private fun openWritable(root: Path): MusicLibrary =
        assertIs<OpenResult.Opened>(MusicLibrary.open(root)).library

    /** Makes a one-second MP3 with ffmpeg, so ffprobe can read a real duration. */
    private fun makeMp3(file: Path) {
        val process = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
            "-c:a", "libmp3lame", file.toString(),
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0, "ffmpeg must create the test MP3")
    }

    @Test
    fun unreadableDownloadIsNotRegisteredAndStaysStaged() {
        val root = newRoot()
        val library = openWritable(root)
        val staged = Files.createDirectories(root.resolve(".staging").resolve("job")).resolve("broken.mp3")
        Files.writeString(staged, "this is not audio")

        val track = library.addDownloadedTrack(staged, "Broken", "brokenId1", "https://www.youtube.com/watch?v=x", emptyList(), null)

        assertNull(track)
        assertTrue(library.data.tracks.isEmpty())
        assertTrue(Files.exists(staged), "a file ffprobe cannot read is left for the caller to clean up")
        library.close()
    }

    @Test
    fun downloadedTrackMovesIntoSongsAndJoinsThePlaylist() {
        val root = newRoot()
        val library = openWritable(root)
        library.createPlaylist("Mix").get()
        val mixId = library.data.playlists.single().id
        val staged = Files.createDirectories(root.resolve(".staging").resolve("job-1")).resolve("video.mp3")
        makeMp3(staged)

        val track = assertNotNull(
            library.addDownloadedTrack(staged, "Play It Loud", "abcDEF12345", "https://www.youtube.com/watch?v=abcDEF12345", listOf(mixId), null),
        )

        assertEquals("songs/Play It Loud.mp3", track.relativePath)
        assertTrue(Files.exists(root.resolve(track.relativePath)))
        assertTrue(!Files.exists(staged), "the staged file is moved, not copied")
        assertEquals("abcDEF12345", track.youtubeId)
        assertTrue((track.durationMs ?: 0L) > 0L)
        assertEquals(track.id, library.findByYoutubeId("abcDEF12345")?.id)
        assertEquals(listOf(track.id), library.data.playlists.single { it.id == mixId }.trackIds)
        library.close()
    }

    @Test
    fun libraryAndTitleOutsideCp1252StillRegister() {
        val root = Files.createTempDirectory("catotube-\u65e5\u672c-").resolve("\u30e9\u30a4\u30d6\u30e9\u30ea")
        Files.createDirectories(root)
        val library = openWritable(root)
        val staged = Files.createDirectories(root.resolve(".staging").resolve("job-u")).resolve("u.mp3")
        makeMp3(staged)

        val track = assertNotNull(
            library.addDownloadedTrack(staged, "\u6771\u4eac \u30bd\u30f3\u30b0", "uniId1234a", "u", emptyList(), null),
        )

        assertEquals("songs/\u6771\u4eac \u30bd\u30f3\u30b0.mp3", track.relativePath)
        assertTrue((track.durationMs ?: 0L) > 0L)
        library.close()
    }

    @Test
    fun newPlaylistNameCreatesAPlaylistWithTheTrack() {
        val root = newRoot()
        val library = openWritable(root)
        val staged = Files.createDirectories(root.resolve(".staging").resolve("job-2")).resolve("new.mp3")
        makeMp3(staged)

        val track = assertNotNull(
            library.addDownloadedTrack(staged, "Fresh", "freshId1234", "https://www.youtube.com/watch?v=freshId1234", emptyList(), "Brand New"),
        )

        val created = library.data.playlists.single()
        assertEquals("Brand New", created.name)
        assertEquals(listOf(track.id), created.trackIds)
        library.close()
    }

    @Test
    fun sameTitleGetsACollisionSuffixAndNothingIsOverwritten() {
        val root = newRoot()
        val library = openWritable(root)
        val first = Files.createDirectories(root.resolve(".staging").resolve("a")).resolve("one.mp3")
        val second = Files.createDirectories(root.resolve(".staging").resolve("b")).resolve("two.mp3")
        makeMp3(first)
        makeMp3(second)

        val a = assertNotNull(library.addDownloadedTrack(first, "Same", "idAAAAAA1", "u1", emptyList(), null))
        val b = assertNotNull(library.addDownloadedTrack(second, "Same", "idBBBBBB2", "u2", emptyList(), null))

        assertEquals("songs/Same.mp3", a.relativePath)
        assertEquals("songs/Same (2).mp3", b.relativePath)
        library.close()
    }
}
