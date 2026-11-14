package org.catosaur.app.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeSearchTest {
    @Test
    fun searchCommandKeepsForceIpv4AndTheSpikeFlags() {
        val command = ytSearchCommand("lo fi", 10).command()
        assertTrue("--force-ipv4" in command, "--force-ipv4 must stay on search too")
        assertTrue("--flat-playlist" in command)
        assertTrue("--dump-json" in command)
        assertTrue("ytsearch10:lo fi" in command)
    }

    @Test
    fun parsesFieldsFromAResultLine() {
        val line = """
            {"id":"abc123","title":"Song","channel":"Artist","duration":153,
             "description":"desc","thumbnails":[{"url":"https://i.ytimg.com/vi/abc123/default.jpg"},
             {"url":"https://i.ytimg.com/vi/abc123/hqdefault.jpg"}],"webpage_url":"https://x"}
        """.trimIndent()
        val result = assertIs<SearchResult>(parseSearchLine(line))
        assertEquals("abc123", result.videoId)
        assertEquals("Song", result.title)
        assertEquals("Artist", result.channel)
        assertEquals(153L, result.durationSeconds)
        assertEquals("desc", result.description)
        assertEquals("https://i.ytimg.com/vi/abc123/mqdefault.jpg", result.thumbnailUrl)
        assertEquals("https://www.youtube.com/watch?v=abc123", result.watchUrl)
    }

    @Test
    fun missingDurationAndChannelFallBackWithoutFailing() {
        val line = """{"id":"live1","title":"Stream","uploader":"Host","duration":null}"""
        val result = assertIs<SearchResult>(parseSearchLine(line))
        assertNull(result.durationSeconds)
        assertEquals("Host", result.channel)
        assertEquals("", result.description)
        assertNull(result.thumbnailUrl)
    }

    @Test
    fun rejectsMalformedAndIncompleteLines() {
        assertNull(parseSearchLine("not json"))
        assertNull(parseSearchLine("{}"))
        assertNull(parseSearchLine("""{"id":"x"}"""))
        assertNull(parseSearchLine("""{"id":"","title":"t"}"""))
        assertNull(parseSearchLine("""[1,2,3]"""))
    }

    @Test
    fun thumbnailIsBuiltFromTheVideoIdOnly() {
        assertEquals("https://i.ytimg.com/vi/abc_DEF-12/mqdefault.jpg", thumbnailFor("abc_DEF-12"))
        assertNull(thumbnailFor("../../evil"))
        assertNull(thumbnailFor("a?b=c"))
    }

    @Test
    fun thumbnailAllowlistAcceptsOnlyHttpsYtimgHosts() {
        assertTrue(isAllowedThumbnailUrl("https://i.ytimg.com/vi/a/hq.jpg"))
        assertTrue(isAllowedThumbnailUrl("https://ytimg.com/a.jpg"))
        assertFalse(isAllowedThumbnailUrl("http://i.ytimg.com/a.jpg"))
        assertFalse(isAllowedThumbnailUrl("https://ytimg.com.evil.example/a.jpg"))
        assertFalse(isAllowedThumbnailUrl("not a url"))
    }

    @Test
    fun blankQueryFailsWithoutStartingYtDlp() {
        val search = YouTubeSearch { _, _ -> error("yt-dlp must not start for a blank query") }
        assertIs<SearchOutcome.Failed>(search.run("   "))
    }
}
