package org.catosaur.app.download

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YtDlpCommandsTest {
    @Test
    fun downloadArgumentsMatchTunedConfiguration() {
        val outputDirectory = Files.createTempDirectory("catotube-test").resolve("songs")
        val url = "https://youtu.be/dQw4w9WgXcQ"

        val command = ytDlpDownloadCommand(outputDirectory, url).command()

        val expected = listOf(
            "yt-dlp",
            "--no-playlist",
            "--extract-audio",
            "--format", "bestaudio/best",
            "--audio-format", "mp3",
            "--audio-quality", "0",
            "--newline",
            "--verbose",
            "--socket-timeout", "15",
            "--retries", "1",
            "--extractor-retries", "1",
            "--extractor-args", "youtube:player_client=tv_samsung",
            "--force-ipv4",
            "--output", outputDirectory.resolve("%(title)s.%(ext)s").toString(),
            url,
        )
        assertEquals(expected, command)
        assertTrue("--force-ipv4" in command, "--force-ipv4 must never be removed")
    }

    @Test
    fun creatingTheCommandCreatesTheOutputDirectory() {
        val outputDirectory = Files.createTempDirectory("catotube-test").resolve("nested")

        ytDlpDownloadCommand(outputDirectory, "https://youtu.be/dQw4w9WgXcQ")

        assertTrue(Files.isDirectory(outputDirectory))
    }
}
