package org.catosaur.app.download

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class SearchResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationSeconds: Long?,
    val description: String,
    val thumbnailUrl: String?,
    val watchUrl: String,
)

internal sealed interface SearchOutcome {
    data class Found(val results: List<SearchResult>, val skippedLines: Int) : SearchOutcome
    data class Failed(val message: String) : SearchOutcome
    data object Cancelled : SearchOutcome
}

internal const val SEARCH_MAX_RESULTS = 25
internal const val SEARCH_TIMEOUT_SECONDS = 60L

// Flags follow the Phase 0 spike. --force-ipv4 stays on search too (devplan section 1).
internal fun ytSearchCommand(query: String, limit: Int): ProcessBuilder =
    ProcessBuilder(
        "yt-dlp",
        "--force-ipv4",
        "--flat-playlist",
        "--dump-json",
        "--no-warnings",
        "ytsearch$limit:$query",
    )

private val searchJson = Json { ignoreUnknownKeys = true }

/**
 * Parses one line of `yt-dlp --dump-json` output. Returns null for anything that is not a
 * playable-looking result (bad JSON, or no id or title), so the caller can count it as skipped.
 */
internal fun parseSearchLine(line: String): SearchResult? {
    val obj = runCatching { searchJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
    val videoId = obj.text("id")?.takeIf { it.isNotBlank() } ?: return null
    val title = obj.text("title")?.takeIf { it.isNotBlank() } ?: return null
    return SearchResult(
        videoId = videoId,
        title = title,
        channel = obj.text("channel") ?: obj.text("uploader").orEmpty(),
        durationSeconds = (obj["duration"] as? JsonPrimitive)?.doubleOrNull
            ?.takeIf { it >= 0 }
            ?.toLong(),
        description = obj.text("description").orEmpty(),
        thumbnailUrl = thumbnailFor(videoId),
        watchUrl = "https://www.youtube.com/watch?v=$videoId",
    )
}

private val videoIdPattern = Regex("[A-Za-z0-9_-]{6,20}")

/**
 * The 320x180 JPEG for a video. yt-dlp's thumbnail list often points at WebP files, which the
 * JVM cannot decode, so the plain JPEG path is built from the ID instead. Null when the ID is
 * not a plausible YouTube ID.
 */
internal fun thumbnailFor(videoId: String): String? =
    if (videoIdPattern.matches(videoId)) "https://i.ytimg.com/vi/$videoId/mqdefault.jpg" else null

/** Thumbnails load only over HTTPS from ytimg.com or one of its subdomains (devplan section 6). */
internal fun isAllowedThumbnailUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        (host == "ytimg.com" || host.endsWith(".ytimg.com"))
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/**
 * Runs one search. [run] blocks until the search ends, so call it off the EDT. Create a new
 * instance for each search so [cancel] only affects that search.
 */
internal class YouTubeSearch(
    private val command: (query: String, limit: Int) -> ProcessBuilder = ::ytSearchCommand,
) {
    @Volatile private var process: Process? = null
    @Volatile private var cancelled = false

    fun run(
        query: String,
        limit: Int = 10,
        timeoutSeconds: Long = SEARCH_TIMEOUT_SECONDS,
    ): SearchOutcome {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return SearchOutcome.Failed("Type something to search for.")
        if (cancelled) return SearchOutcome.Cancelled

        val started = try {
            command(cleanQuery, limit.coerceIn(1, SEARCH_MAX_RESULTS)).start()
        } catch (e: IOException) {
            return SearchOutcome.Failed("Could not start yt-dlp. Check that it is installed and on PATH.")
        }
        process = started
        if (cancelled) started.destroyForcibly()

        val stderr = StringBuilder()
        val errorReader = Thread {
            runCatching {
                started.errorStream.bufferedReader().useLines { lines -> lines.forEach { stderr.appendLine(it) } }
            }
        }.apply { isDaemon = true; start() }

        val timedOut = AtomicBoolean(false)
        val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "yt-search-watchdog").apply { isDaemon = true }
        }
        watchdog.schedule(
            {
                timedOut.set(true)
                started.destroyForcibly()
            },
            timeoutSeconds,
            TimeUnit.SECONDS,
        )

        val results = mutableListOf<SearchResult>()
        var skipped = 0
        try {
            started.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parsed = parseSearchLine(line)
                    if (parsed != null) results += parsed else skipped++
                }
            }
            val exitCode = started.waitFor()
            errorReader.join(1_000)
            return when {
                cancelled -> SearchOutcome.Cancelled
                timedOut.get() -> SearchOutcome.Failed("Search timed out after $timeoutSeconds seconds.")
                exitCode != 0 -> {
                    val lastError = stderr.lines().lastOrNull { it.isNotBlank() }
                    SearchOutcome.Failed("yt-dlp search failed: ${lastError ?: "exit code $exitCode"}")
                }
                else -> SearchOutcome.Found(results, skipped)
            }
        } catch (e: IOException) {
            return if (cancelled) SearchOutcome.Cancelled else SearchOutcome.Failed("Search failed: ${e.message}")
        } finally {
            watchdog.shutdownNow()
            process = null
        }
    }

    fun cancel() {
        cancelled = true
        process?.destroyForcibly()
    }
}
