package org.catosaur.app.download

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Folder used by the manual download flow until downloads are staged into the library (Phase 3). */
internal val DEFAULT_DOWNLOAD_DIRECTORY: Path = Path.of("downloads").toAbsolutePath()

// Do not change these arguments without reading the tuning notes in the repo docs. --force-ipv4 and the
// socket/retry/player_client values were tuned on purpose.
internal fun ytDlpDownloadCommand(outputDirectory: Path, url: String): ProcessBuilder {
    Files.createDirectories(outputDirectory)
    return ProcessBuilder(
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
    ).directory(File(".").absoluteFile)
}
