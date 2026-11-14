package org.catosaur.app.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNamesTest {
    @Test
    fun reservedCharactersBecomeUnderscores() {
        assertEquals("a_b_c_d_e_f_g_h_i_j", safeFileStem("a<b>c:d\"e/f\\g|h?i*j"))
    }

    @Test
    fun controlCharactersBecomeUnderscores() {
        assertEquals("a_b", safeFileStem("a\u0001b"))
    }

    @Test
    fun reservedWindowsNamesAreprefixed() {
        assertEquals("_CON", safeFileStem("CON"))
        assertEquals("_lpt3", safeFileStem("lpt3"))
        assertEquals("CONSOLE", safeFileStem("CONSOLE"))
    }

    @Test
    fun trailingDotsAndSpacesAreRemoved() {
        assertEquals("name", safeFileStem("name...   "))
    }

    @Test
    fun blankTitleUsesFallback() {
        assertEquals("track", safeFileStem("   "))
    }

    @Test
    fun stemIsCappedAt120Characters() {
        assertEquals(120, safeFileStem("x".repeat(300)).length)
    }

    @Test
    fun supportedAudioUsesCaseInsensitiveExtensions() {
        assertTrue(isSupportedAudio("Track.MP3"))
        assertTrue(isSupportedAudio("a.b.opus"))
        assertFalse(isSupportedAudio("notes.txt"))
        assertFalse(isSupportedAudio("noextension"))
    }

    @Test
    fun copyAsNewSongNeverOverwritesAndNumbersCollisions() {
        val dir = Files.createTempDirectory("catotube-copy")
        val source = dir.resolve("Song.mp3")
        Files.writeString(source, "original")
        val songs = dir.resolve("songs")

        val first = copyAsNewSong(source, songs)
        val second = copyAsNewSong(source, songs)
        val third = copyAsNewSong(source, songs)

        assertEquals("Song.mp3", first.fileName.toString())
        assertEquals("Song (2).mp3", second.fileName.toString())
        assertEquals("Song (3).mp3", third.fileName.toString())
        assertEquals("original", Files.readString(source), "the source must stay untouched")
    }
}
