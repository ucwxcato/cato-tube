package org.catosaur.app.download

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YouTubeUrlTest {
    @Test
    fun acceptsSingleVideoLinksOnYouTubeHosts() {
        assertTrue(isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(isYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(isYouTubeUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(isYouTubeUrl("http://youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun rejectsOtherHostsAndSchemes() {
        assertFalse(isYouTubeUrl("ftp://youtube.com/watch?v=abc"))
        assertFalse(isYouTubeUrl("https://example.com/watch?v=abc"))
        assertFalse(isYouTubeUrl("https://youtube.com.evil.example/watch?v=abc"))
        assertFalse(isYouTubeUrl("https://evilyoutube.com/watch?v=abc"))
        assertFalse(isYouTubeUrl("not a url"))
        assertFalse(isYouTubeUrl(""))
    }
}
